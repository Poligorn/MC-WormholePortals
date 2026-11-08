package com.eveportalnether.world;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalShape {
    private static final int LANDING_POCKET_WIDTH = 5;
    private static final int LANDING_POCKET_HEIGHT = 4;
    private static final Set<String> SQUEEZE_WARNED = ConcurrentHashMap.newKeySet();

    private PortalShape() {
    }

    /**
     * Class range after intersecting optional global clamps. A stale pre-0.6
     * {@code maxPortalWidth=7} (or any global that empties/collapses a class that
     * actually has a span) is <em>not</em> applied: new portals keep the class
     * random, saved geometry is untouched, and a one-shot warn is logged.
     */
    public record OuterRange(int min, int max, boolean classFallback, String note) {
        public int span() {
            return Math.max(1, max - min + 1);
        }

        public int roll(RandomSource random) {
            return min + random.nextInt(span());
        }
    }

    public static int rollWidth(PortalTier tier, RandomSource random) {
        return widthRange(tier).roll(random);
    }

    public static int rollHeight(PortalTier tier, RandomSource random) {
        return heightRange(tier).roll(random);
    }

    public static OuterRange widthRange(PortalTier tier) {
        int classMin = Math.min(tier.spec().minWidth.get(), tier.spec().maxWidth.get());
        int classMax = Math.max(tier.spec().minWidth.get(), tier.spec().maxWidth.get());
        return resolveOuterRange(tier.getSerializedName(), "width", classMin, classMax,
                Config.MIN_PORTAL_WIDTH.get(), Config.MAX_PORTAL_WIDTH.get(), 3, Config.PORTAL_OUTER_SIZE_CAP);
    }

    public static OuterRange heightRange(PortalTier tier) {
        int classMin = Math.min(tier.spec().minHeight.get(), tier.spec().maxHeight.get());
        int classMax = Math.max(tier.spec().minHeight.get(), tier.spec().maxHeight.get());
        return resolveOuterRange(tier.getSerializedName(), "height", classMin, classMax,
                Config.MIN_PORTAL_HEIGHT.get(), Config.MAX_PORTAL_HEIGHT.get(), 4, Config.PORTAL_OUTER_SIZE_CAP);
    }

    public static int minWidth(PortalTier tier) {
        return widthRange(tier).min();
    }

    public static int maxWidth(PortalTier tier) {
        return widthRange(tier).max();
    }

    public static int minHeight(PortalTier tier) {
        return heightRange(tier).min();
    }

    public static int maxHeight(PortalTier tier) {
        return heightRange(tier).max();
    }

    public static OuterRange resolveOuterRange(String className, String axis, int classMin, int classMax,
                                              int globalMin, int globalMax, int floor, int cap) {
        int cMin = Mth.clamp(Math.min(classMin, classMax), floor, cap);
        int cMax = Mth.clamp(Math.max(classMin, classMax), floor, cap);
        int gMin = Mth.clamp(Math.min(globalMin, globalMax), floor, cap);
        int gMax = Mth.clamp(Math.max(globalMin, globalMax), floor, cap);
        int lo = Math.max(cMin, gMin);
        int hi = Math.min(cMax, gMax);
        boolean classHasSpan = cMax > cMin;
        if (lo > hi || (lo == hi && classHasSpan)) {
            String note = "spawn." + (axis.equals("width") ? "maxPortalWidth/minPortalWidth" : "maxPortalHeight/minPortalHeight")
                    + "=" + gMin + "–" + gMax + " collapses " + className + " " + axis + " " + cMin + "–" + cMax
                    + "; using class range for new portals (saved sizes unchanged)";
            warnSqueeze(className + ":" + axis, note);
            return new OuterRange(cMin, cMax, true, note);
        }
        return new OuterRange(lo, hi, false, "");
    }

    private static void warnSqueeze(String key, String note) {
        if (SQUEEZE_WARNED.add(key)) {
            EvePortalNether.LOGGER.warn(note);
        }
    }

    public static BlockPos offset(BlockPos origin, int across, int up, Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? origin.offset(0, up, across)
                : origin.offset(across, up, 0);
    }

    public static boolean isFrame(int across, int up, int width, int height) {
        return across == 0 || across == width - 1 || up == 0 || up == height - 1;
    }

    public static BlockPos standPos(BlockPos origin, Direction.Axis axis, int width, int height) {
        return offset(origin, width / 2, 1, axis);
    }

    public static boolean contains(BlockPos origin, Direction.Axis axis, int width, int height, BlockPos pos) {
        int up = pos.getY() - origin.getY();
        if (up < 0 || up >= height) {
            return false;
        }
        if (axis == Direction.Axis.X) {
            return pos.getX() == origin.getX()
                    && pos.getZ() >= origin.getZ()
                    && pos.getZ() < origin.getZ() + width;
        }
        return pos.getZ() == origin.getZ()
                && pos.getX() >= origin.getX()
                && pos.getX() < origin.getX() + width;
    }

    public static Vec3 windowCenter(BlockPos origin, Direction.Axis axis, int width, int height) {
        Vec3 corner = Vec3.atLowerCornerOf(origin);
        double across = width / 2.0;
        double up = height / 2.0;
        return axis == Direction.Axis.X ? corner.add(0.5, up, across) : corner.add(across, up, 0.5);
    }

    /** Inclusive block rectangle of the portal, slightly inflated for entity queries. */
    public static AABB windowBox(BlockPos origin, Direction.Axis axis, int width, int height) {
        BlockPos far = offset(origin, Math.max(0, width - 1), Math.max(0, height - 1), axis);
        return new AABB(origin).minmax(new AABB(far)).inflate(0.25);
    }

    public static boolean isVolumeReplaceable(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        for (int across = 0; across < width; across++) {
            for (int up = 0; up < height; up++) {
                if (!isReplaceable(level.getBlockState(offset(origin, across, up, axis)))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hasSolidFooting(Level level, BlockPos origin, Direction.Axis axis, int width) {
        for (int across = 0; across < width; across++) {
            BlockPos below = offset(origin, across, 0, axis).below();
            BlockState state = level.getBlockState(below);
            if (!state.isSolidRender(level, below) || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isReplaceable(BlockState state) {
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    /**
     * Clears a short air pocket on both faces of the inner window so a player
     * is not entombed in netherrack and can mine blocks beyond the portal.
     * Limited to a vanilla-sized patch around the stand column so a 50-block
     * portal does not flatten thousands of blocks on every crossing.
     */
    public static void carveLandingPocket(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        Direction.Axis normal = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        int innerW = Math.max(0, width - 2);
        int innerH = Math.max(0, height - 2);
        int pocketW = Math.min(innerW, LANDING_POCKET_WIDTH);
        int pocketH = Math.min(innerH, LANDING_POCKET_HEIGHT);
        int startAcross = 1 + Math.max(0, (innerW - pocketW) / 2);
        int endAcross = startAcross + pocketW;
        int startUp = 1;
        int endUp = 1 + pocketH;

        for (int side : new int[] {-2, -1, 1, 2}) {
            for (int across = startAcross; across < endAcross; across++) {
                for (int up = startUp; up < endUp; up++) {
                    BlockPos onPortal = offset(origin, across, up, axis);
                    BlockPos pocket = onPortal.relative(Direction.fromAxisAndDirection(normal, side > 0
                            ? Direction.AxisDirection.POSITIVE
                            : Direction.AxisDirection.NEGATIVE), Math.abs(side));
                    tryCarve(level, pocket);
                }
            }
        }

        // Stable floor just in front of the window, both sides.
        for (int side : new int[] {-1, 1}) {
            for (int across = startAcross; across < endAcross; across++) {
                BlockPos window = offset(origin, across, 1, axis);
                BlockPos floor = window.relative(Direction.fromAxisAndDirection(normal, side > 0
                        ? Direction.AxisDirection.POSITIVE
                        : Direction.AxisDirection.NEGATIVE)).below();
                BlockState floorState = level.getBlockState(floor);
                if (!floorState.isSolidRender(level, floor) || !floorState.getFluidState().isEmpty()) {
                    if (canCarve(level, floor, floorState) || floorState.getFluidState().is(Fluids.LAVA)
                            || floorState.isAir()) {
                        boolean nether = level.dimension() == Level.NETHER;
                        level.setBlock(floor, (nether ? Blocks.NETHERRACK : Blocks.COBBLESTONE).defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void tryCarve(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!canCarve(level, pos, state) && state.getFluidState().isEmpty()) {
            return;
        }
        if (state.getBlock() instanceof WormholeBlock) {
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static boolean canCarve(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof WormholeBlock) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        return !state.isAir();
    }
}
