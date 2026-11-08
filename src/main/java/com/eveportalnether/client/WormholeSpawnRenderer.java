package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.SpawnAnim;
import com.eveportalnether.world.WormholeStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Approved spawn v4: short visual lightning, centre seed, sequential UL→UR→LR→LL
 * points with stretching edges, then grow to the pair's actual inner size and fill.
 * Drawn from the stand tile so every client sees the same one-shot.
 */
final class WormholeSpawnRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_outline.png");
    private static final float POINT = 0.07F;
    private static final float LINE = 0.03F;
    private static final int[] WHITE = WormholeStyle.WHITE;

    private WormholeSpawnRenderer() {
    }

    static void render(PoseStack.Pose pose, MultiBufferSource buffers, Direction.Axis axis,
                       BlockPos stand, BlockPos origin, int width, int height, float elapsed, int portalRgb) {
        SpawnAnim.Frame frame = SpawnAnim.frame(width, height, elapsed);
        float originAcross = axis == Direction.Axis.X ? origin.getZ() : origin.getX();
        float standAcross = axis == Direction.Axis.X ? stand.getZ() : stand.getX();
        float da = originAcross - standAcross;
        float du = origin.getY() - stand.getY();

        VertexConsumer cutout = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        if (frame.lightning() > 0.04F) {
            lightning(cutout, pose, axis, da + frame.cx(), du + frame.cy(),
                    Math.max(width, height) * 0.55F, frame.lightning(), width + height);
        }
        if (frame.lineSeedToUl()) {
            line(cutout, pose, axis, da + frame.cx(), du + frame.cy(), da + frame.ulx(), du + frame.uly());
        }
        if (frame.lineTop()) {
            line(cutout, pose, axis, da + frame.ulx(), du + frame.uly(), da + frame.urx(), du + frame.ury());
        }
        if (frame.lineRight()) {
            line(cutout, pose, axis, da + frame.urx(), du + frame.ury(), da + frame.lrx(), du + frame.lry());
        }
        if (frame.lineBottom()) {
            line(cutout, pose, axis, da + frame.lrx(), du + frame.lry(), da + frame.llx(), du + frame.lly());
        }
        if (frame.lineLeft()) {
            line(cutout, pose, axis, da + frame.llx(), du + frame.lly(), da + frame.ulx(), du + frame.uly());
        }
        if (frame.seedVisible()) {
            point(cutout, pose, axis, da + frame.seedX(), du + frame.seedY(), POINT * (frame.seedAtCentre() ? 1.15F : 1.0F));
        }
        if (frame.ulVisible()) {
            point(cutout, pose, axis, da + frame.ulx(), du + frame.uly(), POINT);
        }
        if (frame.urVisible()) {
            point(cutout, pose, axis, da + frame.urx(), du + frame.ury(), POINT);
        }
        if (frame.lrVisible()) {
            point(cutout, pose, axis, da + frame.lrx(), du + frame.lry(), POINT);
        }
        if (frame.llVisible()) {
            point(cutout, pose, axis, da + frame.llx(), du + frame.lly(), POINT);
        }
        if (frame.vortex() > 0.001F) {
            fill(cutout, pose, axis, da, du, frame, portalRgb, elapsed);
        }
    }

    private static void lightning(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                                  float cx, float cy, float length, float strength, int seed) {
        int segments = 6;
        float prevA = cx;
        float prevU = cy + length;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float jag = ((seed * 1103515245 + i * 9973) >> 16 & 255) / 255.0F - 0.5F;
            float a = cx + jag * 0.55F * (1.0F - t);
            float u = cy + length * (1.0F - t);
            line(consumer, pose, axis, prevA, prevU, a, u, LINE * (0.7F + 0.8F * strength));
            if (i == 3) {
                float forkA = a + 0.45F;
                float forkU = u - 0.35F;
                line(consumer, pose, axis, a, u, forkA, forkU, LINE * 0.55F * strength);
            }
            prevA = a;
            prevU = u;
        }
    }

    private static void fill(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float da, float du, SpawnAnim.Frame frame, int portalRgb, float elapsed) {
        float minA = Math.min(frame.ulx(), frame.llx());
        float maxA = Math.max(frame.urx(), frame.lrx());
        float minU = Math.min(frame.lly(), frame.lry());
        float maxU = Math.max(frame.uly(), frame.ury());
        float cx = (minA + maxA) * 0.5F;
        float cy = (minU + maxU) * 0.5F;
        float maxR = SpawnAnim.lerp(0.05F, Mth.sqrt((maxA - minA) * (maxA - minA) + (maxU - minU) * (maxU - minU)) * 0.5F, frame.vortex());
        int steps = 8;
        float stepA = (maxA - minA) / steps;
        float stepU = (maxU - minU) / steps;
        int[] rgb = new int[3];
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < steps; j++) {
                float a0 = minA + i * stepA;
                float u0 = minU + j * stepU;
                float a1 = a0 + stepA;
                float u1 = u0 + stepU;
                float mx = (a0 + a1) * 0.5F - cx;
                float my = (u0 + u1) * 0.5F - cy;
                if (mx * mx + my * my > maxR * maxR) {
                    continue;
                }
                WormholeStyle.vortexRgb(portalRgb, mx, my, elapsed, 4, 5, rgb);
                quad(consumer, pose, axis, da + a0, du + u0, da + a1, du + u1, rgb);
            }
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float a0, float u0, float a1, float u1) {
        line(consumer, pose, axis, a0, u0, a1, u1, LINE);
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float a0, float u0, float a1, float u1, float thickness) {
        float dx = a1 - a0;
        float dy = u1 - u0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) {
            return;
        }
        float nx = -dy / len * thickness * 0.5F;
        float ny = dx / len * thickness * 0.5F;
        strip(consumer, pose, axis, a0 + nx, u0 + ny, a1 + nx, u1 + ny, a1 - nx, u1 - ny, a0 - nx, u0 - ny);
    }

    private static void point(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                              float a, float u, float size) {
        float h = size * 0.5F;
        strip(consumer, pose, axis, a - h, u - h, a + h, u - h, a + h, u + h, a - h, u + h);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float a0, float u0, float a1, float u1, int[] rgb) {
        float d0 = 0.5F - WormholeStyle.DEPTH;
        float d1 = 0.5F + WormholeStyle.DEPTH;
        vert(consumer, pose, axis, a0, u0, d0, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a1, u0, d0, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a1, u1, d0, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a0, u1, d0, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a0, u0, d1, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a0, u1, d1, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a1, u1, d1, rgb[0], rgb[1], rgb[2], 255);
        vert(consumer, pose, axis, a1, u0, d1, rgb[0], rgb[1], rgb[2], 255);
    }

    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                              float a0, float u0, float a1, float u1, float a2, float u2, float a3, float u3) {
        float d0 = 0.5F - WormholeStyle.DEPTH;
        float d1 = 0.5F + WormholeStyle.DEPTH;
        vert(consumer, pose, axis, a0, u0, d0, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a1, u1, d0, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a2, u2, d0, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a3, u3, d0, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a0, u0, d1, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a3, u3, d1, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a2, u2, d1, WHITE[0], WHITE[1], WHITE[2], 255);
        vert(consumer, pose, axis, a1, u1, d1, WHITE[0], WHITE[1], WHITE[2], 255);
    }

    private static void vert(VertexConsumer consumer, PoseStack.Pose pose, Direction.Axis axis,
                             float across, float up, float depth, int r, int g, int b, int a) {
        float x;
        float y = up;
        float z;
        float nx;
        float nz;
        if (axis == Direction.Axis.X) {
            x = depth;
            z = across;
            nx = 1.0F;
            nz = 0.0F;
        } else {
            x = across;
            z = depth;
            nx = 0.0F;
            nz = 1.0F;
        }
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(WormholeStyle.FULL_BRIGHT)
                .setNormal(pose, nx, 0.0F, nz);
    }
}
