package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.world.WormholeStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Window interior: fully opaque coloured vortex (cutout + depth write) so the same-world
 * terrain cannot show through. After a successful scan the scanning client skips that
 * interior so Immersive Portals can show the destination; other clients keep the vortex.
 * <p>
 * Border: a thin light-gray luminous line in the invisible frame ring, plus a soft outer
 * halo. The solid line uses {@code entityCutoutNoCull} ({@code COLOR_DEPTH_WRITE}) so
 * clouds cannot punch through those pixels. The halo is emissive and sits outside the
 * solid line — not a second slab, not always-on-top.
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
        boolean border = isBorder(level, pos, axis);
        PoseStack.Pose pose = poseStack.last();
        boolean revealing = ScanRevealClient.isRevealing(level, pos);

        if (!revealing) {
            Bounds bounds = portalBounds(level, pos, axis);
            int portalRgb = state.getValue(WormholeBlock.COLOR).tint(state.getValue(WormholeBlock.DECAY));
            float time = level.getGameTime() + partialTick;
            VertexConsumer vortex = buffers.getBuffer(RenderType.entityCutoutNoCull(OUTLINE_TEXTURE));
            swirlFace(vortex, pose, axis, pos, bounds, time, 0.5F, portalRgb);
        }

        if (border) {
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
                                  Bounds bounds, float time, float depth, int portalRgb) {
        float o = WormholeStyle.OVERLAP;
        swirlVertex(consumer, pose, axis, pos, bounds, -o, -o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, 1.0F + o, -o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, 1.0F + o, 1.0F + o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, -o, 1.0F + o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, -o, -o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, -o, 1.0F + o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, 1.0F + o, 1.0F + o, depth, time, portalRgb);
        swirlVertex(consumer, pose, axis, pos, bounds, 1.0F + o, -o, depth, time, portalRgb);
    }

    private static void swirlVertex(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis, BlockPos pos,
                                    Bounds bounds, float localAcross, float localUp, float depth, float time, int portalRgb) {
        float across = (axis == Direction.Axis.X ? pos.getZ() : pos.getX()) + localAcross;
        float up = pos.getY() + localUp;
        float centerAcross = (bounds.minAcross + bounds.maxAcross + 1) * 0.5F;
        float centerUp = (bounds.minY + bounds.maxY + 1) * 0.5F;
        WormholeStyle.vortexRgb(portalRgb, across - centerAcross, up - centerUp, time, RGB);
        if (axis == Direction.Axis.X) {
            vertex(consumer, pose, depth, localUp, localAcross, 0.0F, 0.0F, RGB[0], RGB[1], RGB[2], WormholeStyle.VORTEX_ALPHA, 1, 0, 0);
        } else {
            vertex(consumer, pose, localAcross, localUp, depth, 0.0F, 0.0F, RGB[0], RGB[1], RGB[2], WormholeStyle.VORTEX_ALPHA, 0, 0, 1);
        }
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
     * One thin box. Front faces are light gray; the face toward the window is the darker
     * sidewall so the aperture reads slightly recessed. Halo uses the same topology with
     * vertex alpha {@link WormholeStyle#HALO_ALPHA}.
     */
    private static void rim(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                            float a0, float u0, float a1, float u1, boolean innerHigh, boolean vertical,
                            float depth, boolean halo) {
        float d0 = 0.5F - depth;
        float d1 = 0.5F + depth;
        int alpha = halo ? WormholeStyle.HALO_ALPHA : 255;
        int[] front = halo ? WormholeStyle.GRAY_HI : WormholeStyle.GRAY;
        int[] hi = halo ? WormholeStyle.GRAY_HI : WormholeStyle.GRAY_HI;
        int[] wall = halo ? WormholeStyle.GRAY : WormholeStyle.GRAY_WALL;
        int[] outer = halo ? WormholeStyle.GRAY : WormholeStyle.GRAY_OUTER;
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
