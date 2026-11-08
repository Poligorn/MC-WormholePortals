package com.eveportalnether.scan;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Side-agnostic iris clock. Opaque until the destination is ready. Ready-time
 * (not wall time) drives the 2s ease: an unready dest covers with the vortex
 * without skipping the rest of the open, so dest cannot pop in at iris=1.
 * A flicker after the ease has started covers without rewinding or jumping.
 */
public final class ScanRevealIris {
    private float openAge = -1.0F;
    private float lastNow = Float.NaN;
    private long clockStart = -1L;
    private boolean wasReady;

    public void reset() {
        openAge = -1.0F;
        lastNow = Float.NaN;
        clockStart = -1L;
        wasReady = false;
    }

    /** First dest-ready sample, or {@code -1} if the 2s open has not started. */
    public long clockStart() {
        return clockStart;
    }

    public float openAge() {
        return openAge;
    }

    /**
     * 0 = fully opaque vortex, 1 = skip vortex.
     * {@code expireAt} is exclusive, same units as {@code now}.
     */
    public float sample(boolean revealing, boolean destinationReady, float now, long expireAt) {
        if (!revealing || now >= expireAt) {
            reset();
            lastNow = now;
            return 0.0F;
        }
        if (!destinationReady) {
            wasReady = false;
            lastNow = now;
            return 0.0F;
        }
        if (openAge < 0.0F) {
            openAge = 0.0F;
            clockStart = (long) Math.floor(now);
        } else if (wasReady && !Float.isNaN(lastNow)) {
            openAge += Math.max(0.0F, now - lastNow);
        }
        wasReady = true;
        lastNow = now;

        int trans = ScanReveal.TRANSITION_TICKS;
        float remaining = expireAt - now;
        if (remaining <= trans) {
            return ScanReveal.ease(remaining / trans);
        }
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
