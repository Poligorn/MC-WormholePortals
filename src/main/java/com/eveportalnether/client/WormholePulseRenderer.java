package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emissive glow layered over each window block. Brightness travels up the portal as a wave, so the whole
 * window breathes in one rhythm; a dying portal strobes instead.
 */
public class WormholePulseRenderer implements BlockEntityRenderer<WormholeBlockEntity> {
    private static final ResourceLocation PULSE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_pulse.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float TILES = 4.0F;

    public WormholePulseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WormholeBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof WormholeBlock) || state.getValue(WormholeBlock.FRAME) || entity.getLevel() == null) {
            return;
        }
        BlockPos pos = entity.getBlockPos();
        Direction.Axis axis = state.getValue(WormholeBlock.AXIS);
        if (shouldSkipLod(pos)) {
            return;
        }
        int decay = state.getValue(WormholeBlock.DECAY);
        int rgb = state.getValue(WormholeBlock.COLOR).tint(decay);
        float time = entity.getLevel().getGameTime() + partialTick;
        int across = axis == Direction.Axis.X ? pos.getZ() : pos.getX();

        float alpha = alpha(decay, time, pos.getY(), across);
        if (alpha <= 0.01F) {
            return;
        }

        float u0 = Math.floorMod(across, (int) TILES) / TILES;
        float v0 = Math.floorMod(-pos.getY(), (int) TILES) / TILES;
        float u1 = u0 + 1.0F / TILES;
        float v1 = v0 + 1.0F / TILES;

        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int a = (int) (alpha * 255.0F);

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(PULSE_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (float depth : new float[]{0.47F, 0.53F}) {
            if (axis == Direction.Axis.X) {
                vertex(consumer, pose, depth, 0, 0, u0, v1, r, g, b, a, 1, 0, 0);
                vertex(consumer, pose, depth, 0, 1, u1, v1, r, g, b, a, 1, 0, 0);
                vertex(consumer, pose, depth, 1, 1, u1, v0, r, g, b, a, 1, 0, 0);
                vertex(consumer, pose, depth, 1, 0, u0, v0, r, g, b, a, 1, 0, 0);
            } else {
                vertex(consumer, pose, 0, 0, depth, u0, v1, r, g, b, a, 0, 0, 1);
                vertex(consumer, pose, 1, 0, depth, u1, v1, r, g, b, a, 0, 0, 1);
                vertex(consumer, pose, 1, 1, depth, u1, v0, r, g, b, a, 0, 0, 1);
                vertex(consumer, pose, 0, 1, depth, u0, v0, r, g, b, a, 0, 0, 1);
            }
        }
    }

    /**
     * Drop every other (then three of four) window quads as the camera pulls
     * away so a 50×50 portal does not submit thousands of translucent faces.
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

    private static float alpha(int decay, float time, int y, int across) {
        if (decay >= 3) {
            // Strobe: long dark gaps broken by hard flashes.
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
