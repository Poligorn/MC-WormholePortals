package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.scan.ScanRevealIris;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.WormholeStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Window interior: fully opaque portal-tinted pixel vortex (cutout + depth write) so the
 * same-world terrain cannot show through. After a successful scan the scanning client
 * irises the vortex over 2s only when a tagged IP portal is present <em>and</em> the dest
 * view is compiled; other clients keep the vortex. A missing destination never punches a
 * hole. Dest-side BER is skipped only for this revealing window so the dest vortex cannot
 * fill the through-view; unrelated nested wormholes keep theirs. IP composites dest after
 * this BER, so {@link #renderRevealOverlay} redraws the iris on {@code AFTER_LEVEL}.
 * <p>
 * Border: a thin fullbright white line (~0.03 block) in the invisible frame ring, plus a
 * short outer halo. The solid line uses {@code entityCutoutNoCull} so clouds cannot punch
 * through. Packed light is {@link WormholeStyle#FULL_BRIGHT} — an overlay, not block
 * lighting and not bloom. The halo is emissive and sits outside the solid line.
 */
public class WormholePulseRenderer implements BlockEntityRenderer<WormholeBlockEntity> {
    private static final ResourceLocation OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_outline.png");
    private static Bounds cache;
    private static final int[] RGB = new int[3];

    public WormholePulseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(WormholeBlockEntity entity) {
        return new AABB(entity.getBlockPos()).inflate(0.5);
    }

    @Override
    public void render(WormholeBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof WormholeBlock) || state.getValue(WormholeBlock.FRAME) || entity.getLevel() == null) {
            return;
        }
        Level level = entity.getLevel();
        BlockPos pos = entity.getBlockPos();
        Direction.Axis axis = state.getValue(WormholeBlock.AXIS);
        if (!shouldDrawVortex(level, pos)) {
            return;
        }
        float iris = ScanRevealClient.irisProgress(level, pos, partialTick);
        renderWindow(level, pos, state, axis, poseStack.last(), buffers, iris, iris < 0.999F, true, partialTick);
    }

    /**
     * Drawn after Immersive Portals composites dest into the portal quad. Covers an unready
     * dest with the opaque vortex, then irises the hole over 2s. Rim is redrawn on top so
     * dest cannot overdraw the white frame.
     */
    static void renderRevealOverlay(RenderLevelStageEvent event, Level level, BlockPos origin,
                                    Direction.Axis axis, int width, int height, float iris) {
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(event.getModelViewMatrix());
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean drawVortex = iris < 0.999F;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (int across = 1; across < width - 1; across++) {
            for (int up = 1; up < height - 1; up++) {
                BlockPos pos = PortalShape.offset(origin, across, up, axis);
                if (frustum != null && !frustum.isVisible(new AABB(pos))) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof WormholeBlock) || state.getValue(WormholeBlock.FRAME)) {
                    continue;
                }
                boolean border = isBorder(level, pos, axis);
                if (!drawVortex && !border) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                renderWindow(level, pos, state, axis, poseStack.last(), buffers, iris, drawVortex, true, partial);
                poseStack.popPose();
            }
        }
        buffers.endBatch();
    }

    private static void renderWindow(Level level, BlockPos pos, BlockState state, Direction.Axis axis,
                                     PoseStack.Pose pose, MultiBufferSource buffers, float iris,
                                     boolean drawVortex, boolean drawRim, float partialTick) {
        if (drawVortex) {
            Bounds bounds = portalBounds(level, pos, axis);
            int portalRgb = state.getValue(WormholeBlock.COLOR).tint(state.getValue(WormholeBlock.DECAY));
            float time = level.getGameTime() + partialTick;
            VertexConsumer vortex = buffers.getBuffer(RenderType.entityCutoutNoCull(OUTLINE_TEXTURE));
            swirlFace(vortex, pose, axis, pos, bounds, time, 0.5F, portalRgb, iris);
        }
        if (drawRim && isBorder(level, pos, axis)) {
            boolean top = !isWindow(level, pos.above());
            boolean bottom = !isWindow(level, pos.below());
            boolean minAcross = !isWindow(level, across(pos, axis, -1));
            boolean maxAcross = !isWindow(level, across(pos, axis, 1));
            VertexConsumer halo = buffers.getBuffer(RenderType.entityTranslucentEmissive(OUTLINE_TEXTURE, false));
            outline(halo, pose, axis, top, bottom, minAcross, maxAcross, true);
            VertexConsumer rim = buffers.getBuffer(RenderType.entityCutoutNoCull(OUTLINE_TEXTURE));
            outline(rim, pose, axis, top, bottom, minAcross, maxAcross, false);
        }
    }

    private static void outline(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                                boolean top, boolean bottom, boolean minAcross, boolean maxAcross, boolean halo) {
        float depth = halo ? WormholeStyle.HALO_DEPTH : WormholeStyle.DEPTH;
        float inner = halo ? WormholeStyle.RIM : -WormholeStyle.INNER;
        float outer = halo ? WormholeStyle.HALO : WormholeStyle.RIM;
        float a0 = minAcross ? -outer : 0.0F;
        float a1 = maxAcross ? 1.0F + outer : 1.0F;
        if (top) {
            rim(consumer, pose, axis, a0, 1.0F + inner, a1, 1.0F + outer, false, false, depth, halo);
        }
        if (bottom) {
            rim(consumer, pose, axis, a0, -outer, a1, halo ? -WormholeStyle.RIM : WormholeStyle.INNER, true, false, depth, halo);
        }
        float vu0 = bottom ? (halo ? WormholeStyle.RIM : WormholeStyle.INNER) : 0.0F;
        float vu1 = top ? (halo ? 1.0F - WormholeStyle.RIM : 1.0F - WormholeStyle.INNER) : 1.0F;
        if (minAcross) {
            rim(consumer, pose, axis, -outer, vu0, halo ? -WormholeStyle.RIM : WormholeStyle.INNER, vu1, true, true, depth, halo);
        }
        if (maxAcross) {
            rim(consumer, pose, axis, halo ? 1.0F + WormholeStyle.RIM : 1.0F - WormholeStyle.INNER, vu0, 1.0F + outer, vu1, false, true, depth, halo);
        }
    }

    private static Bounds portalBounds(Level level, BlockPos pos, Direction.Axis axis) {
        int plane = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
        int across = axis == Direction.Axis.X ? pos.getZ() : pos.getX();
        Bounds cached = cache;
        if (cached != null && cached.tick == level.getGameTime() && cached.axis == axis && cached.plane == plane
                && across >= cached.minAcross && across <= cached.maxAcross
                && pos.getY() >= cached.minY && pos.getY() <= cached.maxY) {
            return cached;
        }
        BlockPos cursor = pos;
        while (isWindow(level, cursor.below())) {
            cursor = cursor.below();
        }
        while (isWindow(level, across(cursor, axis, -1))) {
            cursor = across(cursor, axis, -1);
        }
        int minAcross = axis == Direction.Axis.X ? cursor.getZ() : cursor.getX();
        int minY = cursor.getY();
        int width = 1;
        int height = 1;
        while (isWindow(level, across(cursor, axis, width))) {
            width++;
        }
        while (isWindow(level, cursor.above(height))) {
            height++;
        }
        Bounds bounds = new Bounds(level.getGameTime(), axis, plane, minAcross, minAcross + width - 1, minY, minY + height - 1);
        cache = bounds;
        return bounds;
    }

    private static void swirlFace(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis, BlockPos pos,
                                  Bounds bounds, float time, float depth, int portalRgb, float iris) {
        int width = bounds.maxAcross - bounds.minAcross + 1;
        int height = bounds.maxY - bounds.minY + 1;
        int sx = WormholeStyle.vortexSubdiv(width);
        int sy = WormholeStyle.vortexSubdiv(height);
        float hole = iris <= 0.0F ? -1.0F : iris * (WormholeStyle.vortexMaxRadius(width, height) + 0.08F);
        float centerAcross = (bounds.minAcross + bounds.maxAcross + 1) * 0.5F;
        float centerUp = (bounds.minY + bounds.maxY + 1) * 0.5F;
        float stepX = 1.0F / sx;
        float stepY = 1.0F / sy;
        float o = WormholeStyle.OVERLAP;
        int tileAcross = axis == Direction.Axis.X ? pos.getZ() : pos.getX();
        for (int i = 0; i < sx; i++) {
            for (int j = 0; j < sy; j++) {
                float midX = (i + 0.5F) * stepX;
                float midY = (j + 0.5F) * stepY;
                float dx = tileAcross + midX - centerAcross;
                float dy = pos.getY() + midY - centerUp;
                if (hole >= 0.0F && WormholeStyle.vortexRadius(dx, dy) < hole) {
                    continue;
                }
                WormholeStyle.vortexRgb(portalRgb, dx, dy, time, width, height, RGB);
                float x0 = i == 0 ? -o : i * stepX;
                float y0 = j == 0 ? -o : j * stepY;
                float x1 = i == sx - 1 ? 1.0F + o : (i + 1) * stepX;
                float y1 = j == sy - 1 ? 1.0F + o : (j + 1) * stepY;
                swirlQuad(consumer, pose, axis, x0, y0, x1, y1, depth);
            }
        }
    }

    private static void swirlQuad(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                                  float a0, float u0, float a1, float u1, float depth) {
        int r = RGB[0];
        int g = RGB[1];
        int b = RGB[2];
        int a = WormholeStyle.VORTEX_ALPHA;
        if (axis == Direction.Axis.X) {
            vertex(consumer, pose, depth, u0, a0, 0.0F, 0.0F, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, u0, a1, 1.0F, 0.0F, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, u1, a1, 1.0F, 1.0F, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, u1, a0, 0.0F, 1.0F, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, u0, a0, 0.0F, 0.0F, r, g, b, a, -1, 0, 0);
            vertex(consumer, pose, depth, u1, a0, 0.0F, 1.0F, r, g, b, a, -1, 0, 0);
            vertex(consumer, pose, depth, u1, a1, 1.0F, 1.0F, r, g, b, a, -1, 0, 0);
            vertex(consumer, pose, depth, u0, a1, 1.0F, 0.0F, r, g, b, a, -1, 0, 0);
        } else {
            vertex(consumer, pose, a0, u0, depth, 0.0F, 0.0F, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, a1, u0, depth, 1.0F, 0.0F, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, a1, u1, depth, 1.0F, 1.0F, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, a0, u1, depth, 0.0F, 1.0F, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, a0, u0, depth, 0.0F, 0.0F, r, g, b, a, 0, 0, -1);
            vertex(consumer, pose, a0, u1, depth, 0.0F, 1.0F, r, g, b, a, 0, 0, -1);
            vertex(consumer, pose, a1, u1, depth, 1.0F, 1.0F, r, g, b, a, 0, 0, -1);
            vertex(consumer, pose, a1, u0, depth, 1.0F, 0.0F, r, g, b, a, 0, 0, -1);
        }
    }

    /**
     * Immersive Portals keeps the player in their origin dimension while rendering
     * the destination. Skip dest-side vortex only for this revealing window.
     */
    static boolean shouldDrawVortex(Level rendered, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> viewer = minecraft.player == null ? null : minecraft.player.level().dimension();
        boolean revealing = ScanRevealClient.isRevealing(rendered, pos);
        return ScanRevealIris.drawVortexForDimension(rendered.dimension(), viewer, revealing);
    }

    private static boolean isBorder(Level level, BlockPos pos, Direction.Axis axis) {
        return !isWindow(level, pos.above())
                || !isWindow(level, pos.below())
                || !isWindow(level, across(pos, axis, -1))
                || !isWindow(level, across(pos, axis, 1));
    }

    private static boolean isWindow(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.WORMHOLE.get()) && !state.getValue(WormholeBlock.FRAME);
    }

    private static BlockPos across(BlockPos pos, Direction.Axis axis, int steps) {
        return axis == Direction.Axis.X ? pos.offset(0, 0, steps) : pos.offset(steps, 0, 0);
    }

    /**
     * One thin box. Front faces are fullbright white; the face toward the window is slightly
     * dimmer so the aperture reads recessed. Halo uses the same topology with vertex alpha
     * {@link WormholeStyle#HALO_ALPHA}. Packed light is always fullbright.
     */
    private static void rim(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                            float a0, float u0, float a1, float u1, boolean innerHigh, boolean vertical,
                            float depth, boolean halo) {
        float d0 = 0.5F - depth;
        float d1 = 0.5F + depth;
        int alpha = halo ? WormholeStyle.HALO_ALPHA : 255;
        int[] front = halo ? WormholeStyle.WHITE_HI : WormholeStyle.WHITE;
        int[] hi = halo ? WormholeStyle.WHITE_HI : WormholeStyle.WHITE_HI;
        int[] wall = halo ? WormholeStyle.WHITE : WormholeStyle.WHITE_WALL;
        int[] outer = halo ? WormholeStyle.WHITE : WormholeStyle.WHITE_OUTER;
        int[] u0c = vertical ? outer : (innerHigh ? outer : wall);
        int[] u1c = vertical ? outer : (innerHigh ? wall : outer);
        int[] a0c = vertical ? (innerHigh ? outer : wall) : outer;
        int[] a1c = vertical ? (innerHigh ? wall : outer) : outer;
        quad(consumer, pose, axis, a0, u0, d0, a1, u0, d0, a1, u1, d0, a0, u1, d0, front, alpha, 0, 0, -1);
        quad(consumer, pose, axis, a0, u0, d1, a0, u1, d1, a1, u1, d1, a1, u0, d1, hi, alpha, 0, 0, 1);
        quad(consumer, pose, axis, a0, u0, d0, a0, u0, d1, a1, u0, d1, a1, u0, d0, u0c, alpha, 0, -1, 0);
        quad(consumer, pose, axis, a0, u1, d0, a1, u1, d0, a1, u1, d1, a0, u1, d1, u1c, alpha, 0, 1, 0);
        quad(consumer, pose, axis, a0, u0, d0, a0, u1, d0, a0, u1, d1, a0, u0, d1, a0c, alpha, -1, 0, 0);
        quad(consumer, pose, axis, a1, u0, d0, a1, u0, d1, a1, u1, d1, a1, u1, d0, a1c, alpha, 1, 0, 0);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float a0, float u0, float d0, float a1, float u1, float d1,
                             float a2, float u2, float d2, float a3, float u3, float d3,
                             int[] rgb, int alpha, float nx, float ny, float nz) {
        vertexAU(consumer, pose, axis, a0, u0, d0, 0.0F, 1.0F, rgb[0], rgb[1], rgb[2], alpha, nx, ny, nz);
        vertexAU(consumer, pose, axis, a1, u1, d1, 1.0F, 1.0F, rgb[0], rgb[1], rgb[2], alpha, nx, ny, nz);
        vertexAU(consumer, pose, axis, a2, u2, d2, 1.0F, 0.0F, rgb[0], rgb[1], rgb[2], alpha, nx, ny, nz);
        vertexAU(consumer, pose, axis, a3, u3, d3, 0.0F, 0.0F, rgb[0], rgb[1], rgb[2], alpha, nx, ny, nz);
    }

    private static void vertexAU(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                                 float across, float up, float depth, float u, float v, int r, int g, int b, int a,
                                 float nx, float ny, float nz) {
        if (axis == Direction.Axis.X) {
            vertex(consumer, pose, depth, up, across, u, v, r, g, b, a, nz, ny, nx);
        } else {
            vertex(consumer, pose, across, up, depth, u, v, r, g, b, a, nx, ny, nz);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(WormholeStyle.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    private record Bounds(long tick, Direction.Axis axis, int plane, int minAcross, int maxAcross, int minY, int maxY) {
    }
}
