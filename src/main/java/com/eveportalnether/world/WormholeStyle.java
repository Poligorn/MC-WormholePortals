package com.eveportalnether.world;

import net.minecraft.util.Mth;

/**
 * Shared wormhole look: thin light-gray luminous rim and a fully opaque coloured vortex.
 * World BER and the detector preview both read these values so they cannot drift.
 */
public final class WormholeStyle {
    /**
     * Visible in-plane width of the solid rim, into the invisible frame ring.
     * 0.33 was a fat slab in-game; 0.06 reads as a thin glowing line.
     */
    public static final float RIM = 0.06F;
    /** Extra outer glow beyond {@link #RIM}; emissive, not a second solid ring. */
    public static final float HALO = 0.11F;
    /** Modest recess along the portal normal from the plane at 0.5. */
    public static final float DEPTH = 0.08F;
    public static final float HALO_DEPTH = 0.10F;
    /** Overlap onto the window so the rim meets the opaque vortex with no crack. */
    public static final float INNER = 0.02F;
    /** Overlap between adjacent vortex tiles. */
    public static final float OVERLAP = 0.02F;
    public static final int VORTEX_ALPHA = 255;
    public static final int HALO_ALPHA = 110;
    public static final int FULL_BRIGHT = 0xF000F0;

    /** #C9CBD1 front of the rim. */
    public static final int[] GRAY = {201, 203, 209};
    public static final int[] GRAY_HI = {226, 228, 232};
    public static final int[] GRAY_WALL = {88, 90, 96};
    public static final int[] GRAY_OUTER = {168, 170, 176};

    private WormholeStyle() {
    }

    public static int grayRgb() {
        return rgb(GRAY);
    }

    public static int grayHiRgb() {
        return rgb(GRAY_HI);
    }

    public static int rgb(int[] channels) {
        return channels[0] << 16 | channels[1] << 8 | channels[2];
    }

    /**
     * Opaque vortex colour: portal-tinted spiral that stays readable at night.
     * Alpha is always {@link #VORTEX_ALPHA}; darkness is RGB only, never holes.
     */
    public static int vortexRgb(int portalRgb, float dx, float dy, float time) {
        float dist = Mth.sqrt(dx * dx + dy * dy);
        float ang = (float) Math.atan2(dy, dx) + time * 0.14F;
        float arm = 0.5F + 0.5F * Mth.sin(ang * 4.0F - dist * 1.8F + time * 0.35F);
        float radial = Mth.clamp(dist / 3.2F, 0.0F, 1.0F);
        float shade = 0.50F + 0.50F * (0.40F + 0.60F * radial) * (0.55F + 0.45F * arm);
        int r = portalRgb >> 16 & 0xFF;
        int g = portalRgb >> 8 & 0xFF;
        int b = portalRgb & 0xFF;
        return clamp((int) (r * shade * 1.35F)) << 16
                | clamp((int) (g * shade * 1.25F)) << 8
                | clamp((int) (b * shade * 1.20F));
    }

    public static void vortexRgb(int portalRgb, float dx, float dy, float time, int[] out) {
        int rgb = vortexRgb(portalRgb, dx, dy, time);
        out[0] = rgb >> 16 & 0xFF;
        out[1] = rgb >> 8 & 0xFF;
        out[2] = rgb & 0xFF;
    }

    private static int clamp(int value) {
        return Mth.clamp(value, 0, 255);
    }
}
