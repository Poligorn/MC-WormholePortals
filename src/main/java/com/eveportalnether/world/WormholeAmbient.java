package com.eveportalnether.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Ambient drone for a live wormhole. One looping host per window (min-across,
 * min-Y interior). The audible position is the window centre, not that tile.
 * <p>
 * Alpha.2 still sounded "only nearby" for two stacked reasons, not a missing
 * config number:
 * <ol>
 *   <li>{@code SoundEngine} LINEAR uses {@code max(volume,1)*Sound.attenuation}
 *       as the OpenAL max distance. Quadratic {@link #loopGain} then multiplied
 *       that again, so 100 blocks was ~0.015 even with a 256 file range.</li>
 *   <li>The gain curve itself was {@code (1-t)²} from 8→128, already ~0.05 at
 *       100 blocks before OpenAL. Search at 100–150 needs a linear fade that
 *       actually reaches the configured zero, not a volume bump.</li>
 * </ol>
 * The instance now uses {@code Attenuation.NONE} with {@code relative=false}:
 * OpenAL pans in the world, distance is only {@link #loopGain}. Block-entity
 * ticks (hence starting the loop) still require the chunk to be loaded;
 * that is a chunk-load limit, not a substitute for hear distance.
 */
public final class WormholeAmbient {
    /** Loop amplitude at the aperture; OpenAL still pans by world position. */
    public static final float LOOP_VOLUME = 0.45F;
    /** After this many ticks with only the dest center compiled, iris may open (avoid stuck opaque). */
    public static final int DEST_CENTER_FALLBACK_TICKS = 20;
    public static final int HEAR_DISTANCE_DEFAULT = 128;
    public static final int HEAR_DISTANCE_MIN = 100;
    public static final int HEAR_DISTANCE_MAX = 150;
    /** Full volume inside this radius; linear falloff from here to hear distance. */
    public static final float NEAR_FULL_BLOCKS = 8.0F;
    /**
     * {@code sounds.json} / SoundEvent range for {@code portal.hum}. Kept above
     * {@link #HEAR_DISTANCE_MAX} so a LINEAR fallback cannot clamp at vanilla 16.
     * The live loop does not use LINEAR.
     */
    public static final int SOUND_ATTENUATION_DISTANCE = 256;

    private WormholeAmbient() {
    }

    /**
     * 1 at the aperture through {@link #NEAR_FULL_BLOCKS}, 0 at {@code hearDistance}.
     * Linear so 100 blocks is still in the search band; quadratic from alpha.2
     * was already inaudible there.
     */
    public static float loopGain(double distance, int hearDistance) {
        if (distance < 0.0 || hearDistance <= 0 || distance >= hearDistance) {
            return 0.0F;
        }
        if (distance <= NEAR_FULL_BLOCKS) {
            return 1.0F;
        }
        float span = hearDistance - NEAR_FULL_BLOCKS;
        if (span <= 0.0F) {
            return 1.0F;
        }
        float t = (float) ((distance - NEAR_FULL_BLOCKS) / span);
        float gain = 1.0F - t;
        return gain < 0.0F ? 0.0F : Math.min(1.0F, gain);
    }

    public static int clampHearDistance(int configured) {
        if (configured < HEAR_DISTANCE_MIN) {
            return HEAR_DISTANCE_MIN;
        }
        if (configured > HEAR_DISTANCE_MAX) {
            return HEAR_DISTANCE_MAX;
        }
        return configured;
    }

    /**
     * Hear-distance used for gain. Render distance is <em>not</em> mixed in:
     * shrinking the curve to {@code rd*16} made a 128 config silent at 96 when
     * the client was on RD 6, which is not a mod fix.
     */
    public static int hearDistance(int configured) {
        return clampHearDistance(configured);
    }

    /**
     * How far client chunks (and therefore BE ticks that <em>start</em> the loop)
     * extend. Documented limit only — do not use as the attenuation length.
     */
    public static int loadedChunkBlocks(int renderDistanceChunks) {
        return Math.max(0, renderDistanceChunks) * 16;
    }

    /**
     * Extra OpenAL LINEAR scale on top of {@link #loopGain}. {@code false} is the
     * live path: the engine must not fade a second time.
     */
    public static float openAlLinearScale(double distance, float instanceVolume, int fileAttenuation,
                                          boolean linear) {
        if (!linear) {
            return 1.0F;
        }
        float max = Math.max(instanceVolume, 1.0F) * Math.max(1, fileAttenuation);
        if (distance >= max || max <= 0.0F) {
            return 0.0F;
        }
        return (float) (1.0 - distance / max);
    }

    public static boolean usesOpenAlLinear() {
        return false;
    }

    public static boolean sameDimension(ResourceKey<Level> host, ResourceKey<Level> viewer) {
        return host != null && host.equals(viewer);
    }

    /**
     * Exactly one interior tile per window: the min-across, min-Y interior cell.
     * Neighbours toward -Y / -across are frame or outside. That tile owns the
     * loop; the sample position is still the window centre.
     */
    public static boolean isLoopHost(boolean windowBelow, boolean windowMinusAcross) {
        return !windowBelow && !windowMinusAcross;
    }
}
