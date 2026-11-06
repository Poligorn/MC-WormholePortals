package com.eveportalnether.scan;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Side-agnostic iris clock. Opaque until the destination is ready; once the clock
 * starts, a one-tick dest flicker must not rewind the 2s ease.
 */
public final class ScanRevealIris {
    private long clockStart = -1L;

    public void reset() {
        clockStart = -1L;
    }

    public long clockStart() {
        return clockStart;
    }

    /**
     * 0 = fully opaque vortex, 1 = skip vortex.
     * {@code expireAt} is exclusive, same units as {@code now}.
     */
    public float sample(boolean revealing, boolean destinationReady, long now, long expireAt) {
        if (!revealing || now >= expireAt) {
            clockStart = -1L;
            return 0.0F;
        }
        if (!destinationReady) {
            return 0.0F;
        }
        if (clockStart < 0L) {
            clockStart = now;
        }
        int trans = ScanReveal.TRANSITION_TICKS;
        long remaining = expireAt - now;
        if (remaining <= trans) {
            return ScanReveal.ease(remaining / (float) trans);
        }
        long openAge = now - clockStart;
        if (openAge < trans) {
            return ScanReveal.ease(openAge / (float) trans);
        }
        return 1.0F;
    }

    /**
     * Draw the local vortex only in the viewer's own dimension. When Immersive Portals
     * renders the destination world, {@code rendered} is that other dimension — an
     * opaque dest-side vortex would fill the through-view and look like no reveal.
     */
    public static boolean drawVortexForDimension(ResourceKey<Level> rendered, ResourceKey<Level> viewer) {
        return viewer == null || rendered.equals(viewer);
    }
}
