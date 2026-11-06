package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Window interior: a procedural vortex on the true portal plane. After a successful scan
 * the scanning client skips that interior so Immersive Portals can show the destination;
 * other clients keep the vortex. Border: thin light-gray rim with 0.25-block depth.
 * <p>
 * {@code entityTranslucentEmissive} uses {@code COLOR_WRITE} only. Clouds in 1.21.1 are
 * drawn after block entities and depth-test against the main buffer (Fast/Fancy), so a
 * colour-only rim lets them show through. The rim uses {@code entityCutoutNoCull} instead
 * ({@code COLOR_DEPTH_WRITE}, no transparency) so it occludes clouds behind those pixels
 * while still losing to closer terrain. The rest of the 1-block collision ring stays
 * invisible — no full-block depth wall.
 */
public class WormholePulseRenderer implements BlockEntityRenderer<WormholeBlockEntity> {
    private static final ResourceLocation PULSE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_pulse.png");
    private static final ResourceLocation OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_outline.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float[] VORTEX_DEPTHS = {0.44F, 0.50F, 0.56F};
    private static Bounds cache;
    /** Visible in-plane width into the frame (~1/3 of the old 1-block ring). */
    private static final float RIM = 0.33F;
    /** Projection toward each viewing side from the true plane at 0.5. */
    private static final float DEPTH = 0.25F;
    /** Tiny overlap onto the window so the interior model cannot leave a hairline gap. */
    private static final float INNER = 0.02F;
    /** #C9CBD1 front of the rim. */
    private static final int[] GRAY = {201, 203, 209};
    private static final int[] GRAY_HI = {226, 228, 232};
    private static final int[] GRAY_WALL = {88, 90, 96};
    private static final int[] GRAY_OUTER = {168, 170, 176};

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
        boolean lod = shouldSkipLod(pos);
        if (lod && !border) {
            return;
        }

        int decay = state.getValue(WormholeBlock.DECAY);
        float time = level.getGameTime() + partialTick;
        PoseStack.Pose pose = poseStack.last();
        boolean revealing = ScanRevealClient.isRevealing(level, pos);

        if (!lod && !revealing) {
            Bounds bounds = portalBounds(level, pos, axis);
            int rgb = state.getValue(WormholeBlock.COLOR).tint(decay);
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(PULSE_TEXTURE, false));
            for (int i = 0; i < VORTEX_DEPTHS.length; i++) {
                float layer = 0.55F - i * 0.12F;
                swirlFace(consumer, pose, axis, pos, bounds, time + i * 0.7F, VORTEX_DEPTHS[i], r, g, b, layer);
            }
        }

        if (border) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(OUTLINE_TEXTURE));
            boolean top = !isWindow(level, pos.above());
            boolean bottom = !isWindow(level, pos.below());
            boolean minAcross = !isWindow(level, across(pos, axis, -1));
            boolean maxAcross = !isWindow(level, across(pos, axis, 1));
            float a0 = minAcross ? -RIM : 0.0F;
            float a1 = maxAcross ? 1.0F + RIM : 1.0F;
            if (top) {
                // Inner sidewall is the low-U face (toward the window).
                rim(consumer, pose, axis, a0, 1.0F - INNER, a1, 1.0F + RIM, false);
            }
            if (bottom) {
                rim(consumer, pose, axis, a0, -RIM, a1, INNER, true);
            }
            float vu0 = bottom ? INNER : 0.0F;
            float vu1 = top ? 1.0F - INNER : 1.0F;
            if (minAcross) {
                rim(consumer, pose, axis, -RIM, vu0, INNER, vu1, true, true);
            }
            if (maxAcross) {
                rim(consumer, pose, axis, 1.0F - INNER, vu0, 1.0F + RIM, vu1, false, true);
            }
        }
    }

    /**
     * Drop every other (then three of four) interior window quads as the camera pulls
     * away so a 50×50 portal does not submit thousands of translucent faces.
     * Perimeter outline is never lod-skipped, or the rectangle would break up.
     */
    private static boolean shouldSkipLod(BlockPos pos) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSqr = camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        int parity = pos.getX() + pos.getY() + pos.getZ();
        if (distSqr > 64 * 64 && (parity & 3) != 0) {
            return true;
        }
        return distSqr > 32 * 32 && (parity & 1) != 0;
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
                                  Bounds bounds, float time, float depth, int r, int g, int b, float layer) {
        swirlVertex(consumer, pose, axis, pos, bounds, 0.0F, 0.0F, depth, time, r, g, b, layer);
        swirlVertex(consumer, pose, axis, pos, bounds, 1.0F, 0.0F, depth, time, r, g, b, layer);
        swirlVertex(consumer, pose, axis, pos, bounds, 1.0F, 1.0F, depth, time, r, g, b, layer);
        swirlVertex(consumer, pose, axis, pos, bounds, 0.0F, 1.0F, depth, time, r, g, b, layer);
    }

    private static void swirlVertex(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis, BlockPos pos,
                                    Bounds bounds, float localAcross, float localUp, float depth, float time,
                                    int r, int g, int b, float layer) {
        float across = (axis == Direction.Axis.X ? pos.getZ() : pos.getX()) + localAcross;
        float up = pos.getY() + localUp;
        float centerAcross = (bounds.minAcross + bounds.maxAcross + 1) * 0.5F;
        float centerUp = (bounds.minY + bounds.maxY + 1) * 0.5F;
        float dx = across - centerAcross;
        float dy = up - centerUp;
        float dist = Mth.sqrt(dx * dx + dy * dy);
        float ang = (float) Math.atan2(dy, dx) + time * 0.14F;
        float u = 0.5F + 0.45F * Mth.cos(ang) * (0.15F + dist * 0.12F);
        float v = 0.5F + 0.45F * Mth.sin(ang) * (0.15F + dist * 0.12F);
        float funnel = Mth.clamp(dist / 3.5F, 0.0F, 1.0F);
        float shade = 0.12F + 0.88F * funnel;
        int vr = (int) (r * shade * (0.35F + 0.65F * layer));
        int vg = (int) (g * shade * (0.28F + 0.55F * layer));
        int vb = (int) (Math.min(255, b * 1.15F) * (0.22F + 0.55F * funnel) * layer);
        int a = (int) ((0.55F + 0.35F * funnel) * layer * 255.0F);
        if (axis == Direction.Axis.X) {
            vertex(consumer, pose, depth, localUp, localAcross, u, v, vr, vg, vb, a, 1, 0, 0);
        } else {
            vertex(consumer, pose, localAcross, localUp, depth, u, v, vr, vg, vb, a, 0, 0, 1);
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

    private static void rim(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                            float a0, float u0, float a1, float u1, boolean innerHighU) {
        rim(consumer, pose, axis, a0, u0, a1, u1, innerHighU, false);
    }

    /**
     * One box. Front faces (along the portal normal) are light gray; the face toward the
     * window is the darker sidewall. {@code vertical} swaps which pair of faces is inner.
     */
    private static void rim(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                            float a0, float u0, float a1, float u1, boolean innerHigh, boolean vertical) {
        float d0 = 0.5F - DEPTH;
        float d1 = 0.5F + DEPTH;
        int[] u0c = vertical ? GRAY_OUTER : (innerHigh ? GRAY_OUTER : GRAY_WALL);
        int[] u1c = vertical ? GRAY_OUTER : (innerHigh ? GRAY_WALL : GRAY_OUTER);
        int[] a0c = vertical ? (innerHigh ? GRAY_OUTER : GRAY_WALL) : GRAY_OUTER;
        int[] a1c = vertical ? (innerHigh ? GRAY_WALL : GRAY_OUTER) : GRAY_OUTER;
        quad(consumer, pose, axis, a0, u0, d0, a1, u0, d0, a1, u1, d0, a0, u1, d0, GRAY, 0, 0, -1);
        quad(consumer, pose, axis, a0, u0, d1, a0, u1, d1, a1, u1, d1, a1, u0, d1, GRAY_HI, 0, 0, 1);
        quad(consumer, pose, axis, a0, u0, d0, a0, u0, d1, a1, u0, d1, a1, u0, d0, u0c, 0, -1, 0);
        quad(consumer, pose, axis, a0, u1, d0, a1, u1, d0, a1, u1, d1, a0, u1, d1, u1c, 0, 1, 0);
        quad(consumer, pose, axis, a0, u0, d0, a0, u1, d0, a0, u1, d1, a0, u0, d1, a0c, -1, 0, 0);
        quad(consumer, pose, axis, a1, u0, d0, a1, u0, d1, a1, u1, d1, a1, u1, d0, a1c, 1, 0, 0);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float a0, float u0, float d0, float a1, float u1, float d1,
                             float a2, float u2, float d2, float a3, float u3, float d3,
                             int[] rgb, float nx, float ny, float nz) {
        vertexAU(consumer, pose, axis, a0, u0, d0, 0.0F, 1.0F, rgb[0], rgb[1], rgb[2], 255, nx, ny, nz);
        vertexAU(consumer, pose, axis, a1, u1, d1, 1.0F, 1.0F, rgb[0], rgb[1], rgb[2], 255, nx, ny, nz);
        vertexAU(consumer, pose, axis, a2, u2, d2, 1.0F, 0.0F, rgb[0], rgb[1], rgb[2], 255, nx, ny, nz);
        vertexAU(consumer, pose, axis, a3, u3, d3, 0.0F, 0.0F, rgb[0], rgb[1], rgb[2], 255, nx, ny, nz);
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
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    private record Bounds(long tick, Direction.Axis axis, int plane, int minAcross, int maxAcross, int minY, int maxY) {
    }
}
