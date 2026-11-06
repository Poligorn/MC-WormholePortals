package com.eveportalnether.gametest;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.scan.ScanReveal;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalTier;
import com.eveportalnether.world.WormholeStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
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

    @GameTest(template = "empty")
    public static void frameIsInvisibleButSolid(GameTestHelper helper) {
        WormholeBlock block = (WormholeBlock) ModBlocks.WORMHOLE.get();
        BlockState frame = block.defaultBlockState().setValue(WormholeBlock.FRAME, true);
        BlockState window = block.defaultBlockState().setValue(WormholeBlock.FRAME, false);
        if (frame.getRenderShape() != RenderShape.INVISIBLE) {
            helper.fail("frame should not draw a solid model");
            return;
        }
        if (window.getRenderShape() != RenderShape.INVISIBLE) {
            helper.fail("window interior is BER vortex / through-view, not a block model");
            return;
        }
        if (frame.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty()) {
            helper.fail("frame collision must remain");
            return;
        }
        if (!window.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty()) {
            helper.fail("window collision must stay empty");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanRevealLastsSixtySeconds(GameTestHelper helper) {
        assertEq(helper, ScanReveal.DURATION_TICKS, 1200, "scan reveal duration ticks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vortexIsOpaqueAndRimIsThin(GameTestHelper helper) {
        if (WormholeStyle.RIM < 0.04F || WormholeStyle.RIM > 0.08F) {
            helper.fail("solid rim width " + WormholeStyle.RIM + " should be a thin 0.04–0.08 line");
            return;
        }
        if (WormholeStyle.DEPTH > 0.12F) {
            helper.fail("rim depth " + WormholeStyle.DEPTH + " is a slab, not a modest recess");
            return;
        }
        assertEq(helper, WormholeStyle.VORTEX_ALPHA, 255, "vortex vertex alpha");
        int dark = WormholeStyle.vortexRgb(0x3366CC, 0.0F, 0.0F, 0.0F);
        int r = dark >> 16 & 0xFF;
        int g = dark >> 8 & 0xFF;
        int b = dark & 0xFF;
        if (r + g + b < 80) {
            helper.fail("vortex centre too dark to read at night: " + r + "," + g + "," + b);
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
