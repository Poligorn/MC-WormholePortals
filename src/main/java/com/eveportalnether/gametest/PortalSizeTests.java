package com.eveportalnether.gametest;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EvePortalNether.MODID)
@PrefixGameTestTemplate(false)
public final class PortalSizeTests {
    private PortalSizeTests() {
    }

    @GameTest(template = "empty")
    public static void massiveOuterLimitIs50(GameTestHelper helper) {
        assertEq(helper, Config.PORTAL_OUTER_SIZE_CAP, 50, "PORTAL_OUTER_SIZE_CAP");
        assertEq(helper, PortalShape.maxWidth(PortalTier.MASSIVE), 50, "massive max width");
        assertEq(helper, PortalShape.maxHeight(PortalTier.MASSIVE), 50, "massive max height");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void classesScaleWithoutOverlap(GameTestHelper helper) {
        int unstableW = PortalShape.maxWidth(PortalTier.UNSTABLE);
        int standardW = PortalShape.maxWidth(PortalTier.STANDARD);
        int massiveW = PortalShape.maxWidth(PortalTier.MASSIVE);
        int unstableH = PortalShape.maxHeight(PortalTier.UNSTABLE);
        int standardH = PortalShape.maxHeight(PortalTier.STANDARD);
        int massiveH = PortalShape.maxHeight(PortalTier.MASSIVE);

        if (unstableW >= standardW) {
            helper.fail("unstable max width " + unstableW + " should be < standard " + standardW);
            return;
        }
        if (standardW >= massiveW) {
            helper.fail("standard max width " + standardW + " should be < massive " + massiveW);
            return;
        }
        if (unstableH >= standardH) {
            helper.fail("unstable max height " + unstableH + " should be < standard " + standardH);
            return;
        }
        if (standardH >= massiveH) {
            helper.fail("standard max height " + standardH + " should be < massive " + massiveH);
            return;
        }
        if (PortalShape.minWidth(PortalTier.UNSTABLE) < 3) {
            helper.fail("unstable min width must stay at least 3");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void containsIsO1ForMassiveFrame(GameTestHelper helper) {
        BlockPos origin = new BlockPos(10, 40, -4);
        int width = 50;
        int height = 50;
        Direction.Axis axis = Direction.Axis.Z;

        if (!PortalShape.contains(origin, axis, width, height, origin)) {
            helper.fail("origin should be inside");
            return;
        }
        if (!PortalShape.contains(origin, axis, width, height, origin.offset(49, 49, 0))) {
            helper.fail("opposite corner should be inside");
            return;
        }
        if (PortalShape.contains(origin, axis, width, height, origin.offset(50, 0, 0))) {
            helper.fail("x = width should be outside");
            return;
        }
        if (PortalShape.contains(origin, axis, width, height, origin.offset(0, 50, 0))) {
            helper.fail("y = height should be outside");
            return;
        }
        if (PortalShape.contains(origin, axis, width, height, origin.offset(0, 0, 1))) {
            helper.fail("one block off the plane should be outside");
            return;
        }

        BlockPos xOrigin = new BlockPos(-8, 12, 3);
        if (!PortalShape.contains(xOrigin, Direction.Axis.X, width, height, xOrigin.offset(0, 25, 25))) {
            helper.fail("axis X interior should be inside");
            return;
        }
        if (PortalShape.contains(xOrigin, Direction.Axis.X, width, height, xOrigin.offset(1, 0, 0))) {
            helper.fail("axis X off-plane should be outside");
            return;
        }

        // Legacy 4×5 still matches the same rectangle as before.
        BlockPos small = new BlockPos(0, 64, 0);
        if (!PortalShape.contains(small, Direction.Axis.Z, 4, 5, small.offset(2, 1, 0))) {
            helper.fail("legacy 4x5 stand column should still be inside");
            return;
        }
        helper.succeed();
    }

    private static void assertEq(GameTestHelper helper, int actual, int expected, String label) {
        if (actual != expected) {
            helper.fail(label + ": expected " + expected + " but got " + actual);
        }
    }
}
