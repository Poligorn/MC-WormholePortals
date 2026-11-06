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

/**
 * Window interior: emissive pulse over each window block (unchanged).
 * Perimeter: a single thin fullbright rectangle in the portal's assigned colour,
 * plus a same-colour alpha halo. No white/gold contour and no second nested frame.
 * Shaderless — fullbright quads, not bloom or coloured block light.
 */
public class WormholePulseRenderer implements BlockEntityRenderer<WormholeBlockEntity> {
    private static final ResourceLocation PULSE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_pulse.png");
    private static final ResourceLocation OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_outline.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float TILES = 4.0F;
    private static final float LINE = 0.04F;
    private static final float HALO = 0.14F;
    private static final float[] PULSE_DEPTHS = {0.47F, 0.53F};
    private static final float[] OUTLINE_DEPTHS = {0.44F, 0.56F};

    public WormholePulseRenderer(BlockEntityRendererProvider.Context context) {
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
        int across = axis == Direction.Axis.X ? pos.getZ() : pos.getX();
        float pulse = alpha(decay, time, pos.getY(), across);
        PoseStack.Pose pose = poseStack.last();

        if (!lod && pulse > 0.01F) {
            int rgb = state.getValue(WormholeBlock.COLOR).tint(decay);
            float u0 = Math.floorMod(across, (int) TILES) / TILES;
            float v0 = Math.floorMod(-pos.getY(), (int) TILES) / TILES;
            float u1 = u0 + 1.0F / TILES;
            float v1 = v0 + 1.0F / TILES;
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            int a = (int) (pulse * 255.0F);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(PULSE_TEXTURE, false));
            for (float depth : PULSE_DEPTHS) {
                fillFace(consumer, pose, axis, 0.0F, 0.0F, 1.0F, 1.0F, depth, u0, v0, u1, v1, r, g, b, a);
            }
        }

        if (border) {
            int rgb = state.getValue(WormholeBlock.COLOR).rgb();
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            int haloA = (int) (Mth.clamp(pulse * 0.22F, 0.04F, 0.28F) * 255.0F);
            int lineA = (int) (Mth.clamp(pulse * 1.15F, 0.35F, 0.95F) * 255.0F);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(OUTLINE_TEXTURE, false));
            boolean top = !isWindow(level, pos.above());
            boolean bottom = !isWindow(level, pos.below());
            boolean minAcross = !isWindow(level, across(pos, axis, -1));
            boolean maxAcross = !isWindow(level, across(pos, axis, 1));
            for (float depth : OUTLINE_DEPTHS) {
                if (top) {
                    strip(consumer, pose, axis, 0.0F, 1.0F - HALO, 1.0F, 1.0F, depth, r, g, b, haloA);
                    strip(consumer, pose, axis, 0.0F, 1.0F - LINE, 1.0F, 1.0F, depth, r, g, b, lineA);
                }
                if (bottom) {
                    strip(consumer, pose, axis, 0.0F, 0.0F, 1.0F, HALO, depth, r, g, b, haloA);
                    strip(consumer, pose, axis, 0.0F, 0.0F, 1.0F, LINE, depth, r, g, b, lineA);
                }
                if (minAcross) {
                    strip(consumer, pose, axis, 0.0F, 0.0F, HALO, 1.0F, depth, r, g, b, haloA);
                    strip(consumer, pose, axis, 0.0F, 0.0F, LINE, 1.0F, depth, r, g, b, lineA);
                }
                if (maxAcross) {
                    strip(consumer, pose, axis, 1.0F - HALO, 0.0F, 1.0F, 1.0F, depth, r, g, b, haloA);
                    strip(consumer, pose, axis, 1.0F - LINE, 0.0F, 1.0F, 1.0F, depth, r, g, b, lineA);
                }
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

    private static float alpha(int decay, float time, int y, int across) {
        if (decay >= 3) {
            float phase = (time + across * 0.7F) % 7.0F;
            return phase < 2.0F ? 0.75F : 0.06F;
        }
        float wave = 0.5F + 0.5F * Mth.sin(time * 0.16F - y * 0.65F - across * 0.25F);
        float base = switch (decay) {
            case 0 -> 0.55F;
            case 1 -> 0.4F;
            default -> 0.28F;
        };
        float alpha = base * (0.35F + 0.65F * wave);
        if (decay == 2 && Mth.sin(time * 1.7F + across * 3.1F + y * 1.3F) > 0.92F) {
            alpha *= 0.2F;
        }
        return alpha;
    }

    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                              float u0, float v0, float u1, float v1, float depth, int r, int g, int b, int a) {
        fillFace(consumer, pose, axis, u0, v0, u1, v1, depth, 0.0F, 0.0F, 1.0F, 1.0F, r, g, b, a);
    }

    private static void fillFace(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                                 float x0, float y0, float x1, float y1, float depth,
                                 float u0, float v0, float u1, float v1, int r, int g, int b, int a) {
        if (axis == Direction.Axis.X) {
            vertex(consumer, pose, depth, y0, x0, u0, v1, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, y0, x1, u1, v1, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, y1, x1, u1, v0, r, g, b, a, 1, 0, 0);
            vertex(consumer, pose, depth, y1, x0, u0, v0, r, g, b, a, 1, 0, 0);
        } else {
            vertex(consumer, pose, x0, y0, depth, u0, v1, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, x1, y0, depth, u1, v1, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, x1, y1, depth, u1, v0, r, g, b, a, 0, 0, 1);
            vertex(consumer, pose, x0, y1, depth, u0, v0, r, g, b, a, 0, 0, 1);
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
}
