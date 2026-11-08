package com.eveportalnether.world;

/**
 * Approved spawn v4 timeline, in ticks at 20 TPS. One-shot: elapsed is derived from
 * remaining forming ticks, so a chunk reload / late join continues instead of replaying.
 * <p>
 * Lightning (visual-only) → seed at centre → corners fly out UL, UR, LR, LL in order
 * with stretching edges. LL <em>is</em> the seed leaving the centre. Then the small
 * frame grows to the pair's actual outer size and the vortex fills.
 */
public final class SpawnAnim {
    public static final int LIGHTNING_TICKS = 6;
    public static final int SEED_TICKS = 14;
    public static final int CORNER_TICKS = 20;
    public static final int GROW_TICKS = 40;
    public static final int FILL_TICKS = 10;
    public static final int ASSEMBLY_START = LIGHTNING_TICKS + SEED_TICKS;
    public static final int GROW_START = ASSEMBLY_START + CORNER_TICKS * 4;
    public static final int FILL_START = GROW_START + GROW_TICKS;
    public static final int DURATION_TICKS = FILL_START + FILL_TICKS;
    /** Half-size of the assembled rectangle before it grows, in blocks. */
    public static final float SMALL_HALF = 0.45F;

    public enum Phase {
        LIGHTNING, SEED, UL, UR, LR, LL, GROW, FILL, DONE
    }

    private SpawnAnim() {
    }

    /**
     * Forming length used at spawn. {@code configTicks == 0} honours an explicit skip.
     * Otherwise the visual is never shorter than {@link #DURATION_TICKS}, so the player
     * cannot walk through a half-built frame.
     */
    public static int formingDuration(int configTicks) {
        if (configTicks <= 0) {
            return 0;
        }
        return Math.max(configTicks, DURATION_TICKS);
    }

    public static int elapsed(int formingTotal, int formingRemaining) {
        if (formingTotal <= 0) {
            return DURATION_TICKS;
        }
        return Math.max(0, formingTotal - Math.max(0, formingRemaining));
    }

    /**
     * Saved portals with no forming data must not play the spawn. A live spawn that
     * still has remaining ticks continues from {@link #elapsed}.
     */
    public static boolean plays(int formingTotal, int formingRemaining) {
        return formingTotal > 0 && formingRemaining > 0;
    }

    public static Phase phase(float elapsed) {
        if (elapsed < 0.0F) {
            return Phase.DONE;
        }
        if (elapsed < LIGHTNING_TICKS) {
            return Phase.LIGHTNING;
        }
        if (elapsed < ASSEMBLY_START) {
            return Phase.SEED;
        }
        float assembly = elapsed - ASSEMBLY_START;
        if (assembly < CORNER_TICKS) {
            return Phase.UL;
        }
        if (assembly < CORNER_TICKS * 2) {
            return Phase.UR;
        }
        if (assembly < CORNER_TICKS * 3) {
            return Phase.LR;
        }
        if (assembly < CORNER_TICKS * 4) {
            return Phase.LL;
        }
        if (elapsed < FILL_START) {
            return Phase.GROW;
        }
        if (elapsed < DURATION_TICKS) {
            return Phase.FILL;
        }
        return Phase.DONE;
    }

    public static float cornerT(float elapsed, Phase phase) {
        int index = switch (phase) {
            case UL -> 0;
            case UR -> 1;
            case LR -> 2;
            case LL -> 3;
            default -> -1;
        };
        if (index < 0) {
            return 1.0F;
        }
        float local = elapsed - ASSEMBLY_START - index * CORNER_TICKS;
        return clamp01(local / CORNER_TICKS);
    }

    public static float growT(float elapsed) {
        if (elapsed < GROW_START) {
            return 0.0F;
        }
        return clamp01((elapsed - GROW_START) / GROW_TICKS);
    }

    public static float vortexT(float elapsed) {
        if (elapsed < FILL_START) {
            return 0.0F;
        }
        return clamp01((elapsed - FILL_START) / FILL_TICKS);
    }

    public static float lightningT(float elapsed) {
        if (elapsed >= LIGHTNING_TICKS) {
            return 0.0F;
        }
        float t = elapsed / LIGHTNING_TICKS;
        return t < 0.35F ? t / 0.35F : 1.0F - (t - 0.35F) / 0.65F;
    }

    /**
     * Frame corners in across/up block units from the structure origin. Small assembled
     * size first, then lerp to the real inner window of {@code width}×{@code height}.
     */
    public static Frame frame(int width, int height, float elapsed) {
        float cx = width * 0.5F;
        float cy = height * 0.5F;
        float small = SMALL_HALF;
        float ulx = cx - small;
        float uly = cy + small;
        float urx = cx + small;
        float ury = cy + small;
        float lrx = cx + small;
        float lry = cy - small;
        float llx = cx - small;
        float lly = cy - small;

        float fullUlx = 1.0F;
        float fullUly = height - 1.0F;
        float fullUrx = width - 1.0F;
        float fullUry = height - 1.0F;
        float fullLrx = width - 1.0F;
        float fullLry = 1.0F;
        float fullLlx = 1.0F;
        float fullLly = 1.0F;

        Phase phase = phase(elapsed);
        float ulT = 0.0F;
        float urT = 0.0F;
        float lrT = 0.0F;
        float llT = 0.0F;
        if (phase.ordinal() > Phase.UL.ordinal()) {
            ulT = 1.0F;
        } else if (phase == Phase.UL) {
            ulT = cornerT(elapsed, Phase.UL);
        }
        if (phase.ordinal() > Phase.UR.ordinal()) {
            urT = 1.0F;
        } else if (phase == Phase.UR) {
            urT = cornerT(elapsed, Phase.UR);
        }
        if (phase.ordinal() > Phase.LR.ordinal()) {
            lrT = 1.0F;
        } else if (phase == Phase.LR) {
            lrT = cornerT(elapsed, Phase.LR);
        }
        if (phase.ordinal() > Phase.LL.ordinal()) {
            llT = 1.0F;
        } else if (phase == Phase.LL) {
            llT = cornerT(elapsed, Phase.LL);
        }

        float g = ScanRevealEase.smooth(growT(elapsed));
        ulx = lerp(cx, lerp(ulx, fullUlx, g), ulT);
        uly = lerp(cy, lerp(uly, fullUly, g), ulT);
        urx = lerp(cx, lerp(urx, fullUrx, g), urT);
        ury = lerp(cy, lerp(ury, fullUry, g), urT);
        lrx = lerp(cx, lerp(lrx, fullLrx, g), lrT);
        lry = lerp(cy, lerp(lry, fullLry, g), lrT);
        llx = lerp(cx, lerp(llx, fullLlx, g), llT);
        lly = lerp(cy, lerp(lly, fullLly, g), llT);

        boolean seedAtCentre = phase.ordinal() <= Phase.LR.ordinal();
        float seedX = seedAtCentre ? cx : llx;
        float seedY = seedAtCentre ? cy : lly;
        boolean seedVisible = phase == Phase.SEED
                || phase == Phase.UL
                || phase == Phase.UR
                || phase == Phase.LR
                || phase == Phase.LL
                || (phase == Phase.LIGHTNING && lightningT(elapsed) >= 0.4F);
        boolean ulVisible = phase.ordinal() >= Phase.UL.ordinal();
        boolean urVisible = phase.ordinal() >= Phase.UR.ordinal();
        boolean lrVisible = phase.ordinal() >= Phase.LR.ordinal();
        boolean llVisible = phase.ordinal() > Phase.LL.ordinal();

        boolean lineTop = phase.ordinal() >= Phase.UR.ordinal();
        boolean lineRight = phase.ordinal() >= Phase.LR.ordinal();
        boolean lineBottom = phase.ordinal() >= Phase.LL.ordinal();
        boolean lineLeft = phase.ordinal() >= Phase.LL.ordinal();
        boolean lineSeedToUl = phase == Phase.UL;

        return new Frame(phase, cx, cy, seedX, seedY, seedVisible, seedAtCentre,
                ulx, uly, ulVisible, urx, ury, urVisible, lrx, lry, lrVisible, llx, lly, llVisible,
                lineSeedToUl, lineTop, lineRight, lineBottom, lineLeft,
                lightningT(elapsed), vortexT(elapsed), g);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    public static float clamp01(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, t);
    }

    public record Frame(Phase phase, float cx, float cy, float seedX, float seedY, boolean seedVisible, boolean seedAtCentre,
                        float ulx, float uly, boolean ulVisible, float urx, float ury, boolean urVisible,
                        float lrx, float lry, boolean lrVisible, float llx, float lly, boolean llVisible,
                        boolean lineSeedToUl, boolean lineTop, boolean lineRight, boolean lineBottom, boolean lineLeft,
                        float lightning, float vortex, float grow) {
    }

    /**
     * Local ease so {@link SpawnAnim} does not depend on the scan iris class.
     */
    static final class ScanRevealEase {
        private ScanRevealEase() {
        }

        static float smooth(float t) {
            t = SpawnAnim.clamp01(t);
            return t * t * (3.0F - 2.0F * t);
        }
    }
}
