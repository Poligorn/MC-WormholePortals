package com.eveportalnether.world;

/**
 * Ambient drone for a live wormhole. The 0.6 particle budget skip also starved
 * {@code PORTAL_AMBIENT} one-shots on anything larger than a vanilla-sized window,
 * which is why the hum disappeared. One looping host per window restores it.
 */
public final class WormholeAmbient {
    /** Linear-attenuated loop volume; matches the old one-shot amplitude. */
    public static final float LOOP_VOLUME = 0.45F;
    /** After this many ticks with only the dest center compiled, iris may open (avoid stuck opaque). */
    public static final int DEST_CENTER_FALLBACK_TICKS = 100;

    private WormholeAmbient() {
    }

    /**
     * Exactly one interior tile per window: the min-across, min-Y interior cell.
     * Neighbours toward -Y / -across are frame or outside.
     */
    public static boolean isLoopHost(boolean windowBelow, boolean windowMinusAcross) {
        return !windowBelow && !windowMinusAcross;
    }
}
