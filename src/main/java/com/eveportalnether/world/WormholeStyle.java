package com.eveportalnether.world;

import net.minecraft.util.Mth;

/**
 * Shared wormhole look: a thin fullbright white rim and an opaque portal-tinted pixel vortex.
 * World BER and the detector preview both read these values so they cannot drift.
 */
public final class WormholeStyle {
    /**
     * Visible in-plane width of the solid rim, into the invisible frame ring.
     * Approved concept: ~0.03 block — a thin line, not a slab.
     */
    public static final float RIM = 0.03F;
    /** Extra outer glow beyond {@link #RIM}; emissive overlay only, outside the solid line. */
    public static final float HALO = 0.05F;
    /** Modest recess along the portal normal from the plane at 0.5. */
    public static final float DEPTH = 0.08F;
    public static final float HALO_DEPTH = 0.10F;
    /** Overlap onto the window so the rim meets the opaque vortex with no crack. */
    public static final float INNER = 0.02F;
    /**
     * Extra Immersive Portals quad size (both axes) so the live destination tucks under
     * the 0.03 rim instead of leaving a dark inset inside the white frame.
     */
    public static final float APERTURE_FILL = 0.08F;
    /** Overlap between adjacent vortex tiles. */
    public static final float OVERLAP = 0.02F;
    public static final int VORTEX_ALPHA = 255;
    public static final int HALO_ALPHA = 90;
    /** Packed light so the rim does not pick up world/block light. Not block lighting, not bloom. */
    public static final int FULL_BRIGHT = 0xF000F0;

    public static final int[] WHITE = {255, 255, 255};
    public static final int[] WHITE_HI = {255, 255, 255};
    /** Slightly dimmer inner wall so the geometric recess still reads. */
    public static final int[] WHITE_WALL = {228, 230, 235};
    public static final int[] WHITE_OUTER = {255, 255, 255};

    /** Aim for about this many texels across a typical window so the funnel stays pixel-art. */
    public static final int VORTEX_TEXEL_TARGET = 48;
    public static final int VORTEX_SUBDIV_MAX = 16;

    private WormholeStyle() {
    }

    public static int whiteRgb() {
        return rgb(WHITE);
    }

    public static int rgb(int[] channels) {
        return channels[0] << 16 | channels[1] << 8 | channels[2];
    }

    public static int vortexSubdiv(int spanBlocks) {
        return Mth.clamp(Math.round(VORTEX_TEXEL_TARGET / (float) Math.max(1, spanBlocks)), 1, VORTEX_SUBDIV_MAX);
    }

    public static float vortexMaxRadius(int widthBlocks, int heightBlocks) {
        float hx = widthBlocks * 0.5F;
        float hy = heightBlocks * 0.5F;
        return Mth.sqrt(hx * hx + hy * hy);
    }

    public static float vortexRadius(float dx, float dy) {
        return Mth.sqrt(dx * dx + dy * dy);
    }

    /**
     * Opaque vortex colour: portal-class tint, slightly darker same-hue lanes, quantized
     * inward threads. Darkness is RGB only — alpha is always {@link #VORTEX_ALPHA}.
     */
    public static int vortexRgb(int portalRgb, float dx, float dy, float time) {
        return vortexRgb(portalRgb, dx, dy, time, 4, 5);
    }

    public static int vortexRgb(int portalRgb, float dx, float dy, float time, int widthBlocks, int heightBlocks) {
        int sw = vortexSubdiv(widthBlocks);
        int sh = vortexSubdiv(heightBlocks);
        float sx = (Mth.floor(dx * sw) + 0.5F) / sw;
        float sy = (Mth.floor(dy * sh) + 0.5F) / sh;
        float dist = Mth.sqrt(sx * sx + sy * sy);
        float halfShort = Math.min(widthBlocks, heightBlocks) * 0.5F;
        float r = halfShort <= 0.001F ? dist : dist / halfShort;
        float ang = (float) Math.atan2(sy, sx);
        float phase = time * 0.08F;
        float arms = 0.55F + 0.45F * Mth.sin(3.0F * ang - 6.5F * r + phase);
        float threads = 0.5F + 0.5F * Mth.sin(9.0F * ang - 14.0F * r + phase * 1.6F);
        float mix = Mth.clamp(0.62F * arms + 0.38F * threads, 0.0F, 1.0F);
        mix = Math.round(mix * 10.0F) / 10.0F;
        float recess = Mth.clamp((r - 0.02F) / 1.15F, 0.0F, 1.0F);
        recess = (float) Math.pow(recess, 1.35F);
        recess = Math.round(recess * 12.0F) / 12.0F;
        float core = 1.0F - (float) Math.exp(-r * r * 7.5F);

        int pr = portalRgb >> 16 & 0xFF;
        int pg = portalRgb >> 8 & 0xFF;
        int pb = portalRgb & 0xFF;
        float luma = (pr + pg + pb) / 3.0F;
        float boost = luma < 48.0F ? 48.0F / Math.max(1.0F, luma) : 1.0F;
        float br = pr * boost;
        float bg = pg * boost;
        float bb = pb * boost;
        float dim = 0.64F;
        float wr = br * dim * (1.0F - mix) + br * mix;
        float wg = bg * dim * (1.0F - mix) + bg * mix;
        float wb = bb * dim * (1.0F - mix) + bb * mix;
        float shade = (0.58F + 0.42F * recess) * (0.62F + 0.38F * core);
        wr *= shade;
        wg *= shade;
        wb *= shade;
        float well = (float) Math.pow(Mth.clamp(1.0F - r / 0.22F, 0.0F, 1.0F), 1.55F);
        wr = wr * (1.0F - 0.38F * well) + br * 0.52F * 0.38F * well;
        wg = wg * (1.0F - 0.38F * well) + bg * 0.52F * 0.38F * well;
        wb = wb * (1.0F - 0.38F * well) + bb * 0.52F * 0.38F * well;
        if (mix > 0.74F && r > 0.20F && r < 0.90F) {
            wr = wr * 0.78F + br * 1.08F * 0.22F;
            wg = wg * 0.78F + bg * 1.08F * 0.22F;
            wb = wb * 0.78F + bb * 1.08F * 0.22F;
        }
        return quantize(wr) << 16 | quantize(wg) << 8 | quantize(wb);
    }

    public static void vortexRgb(int portalRgb, float dx, float dy, float time, int widthBlocks, int heightBlocks, int[] out) {
        int rgb = vortexRgb(portalRgb, dx, dy, time, widthBlocks, heightBlocks);
        out[0] = rgb >> 16 & 0xFF;
        out[1] = rgb >> 8 & 0xFF;
        out[2] = rgb & 0xFF;
    }

    public static void vortexRgb(int portalRgb, float dx, float dy, float time, int[] out) {
        vortexRgb(portalRgb, dx, dy, time, 4, 5, out);
    }

    private static int quantize(float value) {
        return Mth.clamp(Math.round(value / 8.0F) * 8, 0, 255);
    }
}
