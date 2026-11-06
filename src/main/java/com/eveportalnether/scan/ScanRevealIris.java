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
    public float sample(boolean revealing, boolean destinationReady, float now, long expireAt) {
        if (!revealing || now >= expireAt) {
            clockStart = -1L;
            return 0.0F;
        }
        if (!destinationReady) {
            return 0.0F;
        }
        if (clockStart < 0L) {
            clockStart = (long) now;
        }
        int trans = ScanReveal.TRANSITION_TICKS;
        float remaining = expireAt - now;
        if (remaining <= trans) {
            return ScanReveal.ease(remaining / trans);
        }
        float openAge = now - clockStart;
        if (openAge < trans) {
            return ScanReveal.ease(openAge / trans);
        }
        return 1.0F;
    }

    /**
     * Skip the dest-side vortex only for <em>this</em> revealing window while Immersive
     * Portals is rendering the other dimension. Unrelated wormholes in that nested view
     * keep their vortex so we do not blanket-hide portals.
     */
    public static boolean drawVortexForDimension(ResourceKey<Level> rendered, ResourceKey<Level> viewer,
                                                 boolean thisWindowRevealing) {
        if (viewer == null || rendered.equals(viewer)) {
            return true;
        }
        return !thisWindowRevealing;
    }
}
