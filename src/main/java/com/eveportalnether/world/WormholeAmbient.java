package com.eveportalnether.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Ambient drone for a live wormhole. The 0.6 particle budget skip also starved
 * {@code PORTAL_AMBIENT} one-shots on anything larger than a vanilla-sized window,
 * which is why the hum disappeared. One looping host per window restores it.
 * <p>
 * Vanilla {@code SoundEngine} feeds OpenAL {@code AL_LINEAR_DISTANCE} with
 * {@code max(volume, 1) * Sound.getAttenuationDistance()}. The vanilla
 * {@code block.portal.ambient} file defaults to 16, so gain is 0 at 16 blocks.
 * {@code portal.hum} is a raw file sound (not that event) so the 16-block value
 * is not inherited. Falloff is applied in {@link #loopGain}; OpenAL LINEAR is
 * given a 256-block cap so it cannot silence the loop inside the configured
 * 100–150 hear range. Spatial panning stays on ({@code relative=false}).
 */
public final class WormholeAmbient {
    /** Loop amplitude at the aperture; OpenAL still pans by world position. */
    public static final float LOOP_VOLUME = 0.45F;
    /** After this many ticks with only the dest center compiled, iris may open (avoid stuck opaque). */
    public static final int DEST_CENTER_FALLBACK_TICKS = 100;
    public static final int HEAR_DISTANCE_DEFAULT = 128;
    public static final int HEAR_DISTANCE_MIN = 100;
    public static final int HEAR_DISTANCE_MAX = 150;
    /** Full volume inside this radius; quadratic falloff from here to hear distance. */
    public static final float NEAR_FULL_BLOCKS = 8.0F;
    /**
     * {@code sounds.json} {@code attenuation_distance} for {@code portal.hum}.
     * Must stay above {@link #HEAR_DISTANCE_MAX} so vanilla LINEAR cannot clamp
     * before {@link #loopGain} reaches 0.
     */
    public static final int SOUND_ATTENUATION_DISTANCE = 256;

    private WormholeAmbient() {
    }

    /**
     * 1 at the aperture through {@link #NEAR_FULL_BLOCKS}, 0 at {@code hearDistance}.
     * Quadratic so the last stretch is a smooth fade rather than a wall.
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
        float gain = (1.0F - t) * (1.0F - t);
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
     * Block entities only tick in loaded chunks, so the hear range cannot exceed
     * the client's render distance even if the config asks for 150.
     */
    public static int effectiveHearDistance(int configured, int renderDistanceChunks) {
        int hear = clampHearDistance(configured);
        int loaded = Math.max(0, renderDistanceChunks) * 16;
        return Math.min(hear, loaded);
    }

    public static boolean sameDimension(ResourceKey<Level> host, ResourceKey<Level> viewer) {
        return host != null && host.equals(viewer);
    }

    /**
     * Exactly one interior tile per window: the min-across, min-Y interior cell.
     * Neighbours toward -Y / -across are frame or outside.
     */
    public static boolean isLoopHost(boolean windowBelow, boolean windowMinusAcross) {
        return !windowBelow && !windowMinusAcross;
    }
}
