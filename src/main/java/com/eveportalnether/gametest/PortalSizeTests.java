package com.eveportalnether.gametest;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.scan.ScanReveal;
import com.eveportalnether.scan.ScanRevealIris;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalTier;
import com.eveportalnether.world.WormholeStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
        assertEq(helper, ScanReveal.TRANSITION_TICKS, 40, "2s iris ticks");
        if (Math.abs(ScanReveal.ease(0.0F)) > 0.001F || Math.abs(ScanReveal.ease(1.0F) - 1.0F) > 0.001F) {
            helper.fail("ease must map 0→0 and 1→1");
            return;
        }
        float mid = ScanReveal.ease(0.5F);
        if (mid < 0.49F || mid > 0.51F) {
            helper.fail("ease-in-out midpoint should be ~0.5, got " + mid);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanRevealIrisDoesNotRewindOnDestFlicker(GameTestHelper helper) {
        ScanRevealIris iris = new ScanRevealIris();
        long expire = ScanReveal.DURATION_TICKS;
        if (iris.sample(true, false, 0L, expire) != 0.0F) {
            helper.fail("iris stays closed until destination is ready");
            return;
        }
        for (int tick = 1; tick < ScanReveal.TRANSITION_TICKS; tick++) {
            if (iris.sample(true, false, tick, expire) != 0.0F || iris.clockStart() != -1L) {
                helper.fail("iris must not start the 2s clock while dest chunks are unready");
                return;
            }
        }
        if (iris.sample(true, true, 1L, expire) != 0.0F || iris.clockStart() != 1L) {
            helper.fail("iris clock must start at the first ready tick (ease 0 at age 0)");
            return;
        }
        if (iris.sample(true, false, 2L, expire) != 0.0F) {
            helper.fail("missing destination must cover with vortex");
            return;
        }
        float resumed = iris.sample(true, true, 21L, expire);
        float fresh = new ScanRevealIris().sample(true, true, 21L, expire);
        if (resumed <= fresh + 0.05F) {
            helper.fail("dest flicker must not rewind the 2s ease; resumed=" + resumed + " fresh=" + fresh);
            return;
        }
        if (iris.sample(false, true, 22L, expire) != 0.0F) {
            helper.fail("non-scanner / other portal stays opaque");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void destDimensionVortexWouldOccludeThroughView(GameTestHelper helper) {
        if (!ScanRevealIris.drawVortexForDimension(Level.OVERWORLD, Level.OVERWORLD, true)) {
            helper.fail("home overworld must still draw the vortex");
            return;
        }
        if (ScanRevealIris.drawVortexForDimension(Level.NETHER, Level.OVERWORLD, true)) {
            helper.fail("nether rendered for this revealing window must not draw the dest vortex");
            return;
        }
        if (!ScanRevealIris.drawVortexForDimension(Level.NETHER, Level.OVERWORLD, false)) {
            helper.fail("unrelated nether wormhole in a nested view must keep its vortex");
            return;
        }
        if (ScanRevealIris.drawVortexForDimension(Level.OVERWORLD, Level.NETHER, true)) {
            helper.fail("overworld rendered for this revealing window must not draw the dest vortex");
            return;
        }
        if (!ScanRevealIris.drawVortexForDimension(Level.OVERWORLD, Level.NETHER, false)) {
            helper.fail("unrelated overworld wormhole in a nested view must keep its vortex");
            return;
        }
        if (!ScanRevealIris.drawVortexForDimension(Level.NETHER, null, true)) {
            helper.fail("no viewer (menu) keeps the vortex");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void destApertureTucksUnderRim(GameTestHelper helper) {
        if (WormholeStyle.APERTURE_FILL < WormholeStyle.RIM * 2.0F) {
            helper.fail("IP dest quad extra " + WormholeStyle.APERTURE_FILL
                    + " must reach under both sides of the " + WormholeStyle.RIM + " rim");
            return;
        }
        int inner = 5;
        float aperture = inner + WormholeStyle.APERTURE_FILL;
        if (aperture <= inner) {
            helper.fail("aperture must be larger than the inner window so dest fills to the rim");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void windowBoxCoversBothAxes(GameTestHelper helper) {
        BlockPos origin = new BlockPos(50, 72, 18);
        AABB zBox = PortalShape.windowBox(origin, Direction.Axis.Z, 7, 8);
        if (!zBox.intersects(new AABB(origin.offset(3, 4, 0)))) {
            helper.fail("Z window box must cover the interior");
            return;
        }
        if (zBox.intersects(new AABB(origin.offset(0, 0, 2)))) {
            helper.fail("Z window box must stay on the portal plane");
            return;
        }
        AABB xBox = PortalShape.windowBox(origin, Direction.Axis.X, 7, 8);
        if (!xBox.intersects(new AABB(origin.offset(0, 4, 3)))) {
            helper.fail("X window box must cover the interior");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vortexIsOpaqueAndRimIsThin(GameTestHelper helper) {
        if (WormholeStyle.RIM < 0.02F || WormholeStyle.RIM > 0.04F) {
            helper.fail("solid rim width " + WormholeStyle.RIM + " should be a thin ~0.03 line");
            return;
        }
        if (WormholeStyle.HALO <= WormholeStyle.RIM || WormholeStyle.HALO > 0.09F) {
            helper.fail("halo " + WormholeStyle.HALO + " should be a short outside glow, not a slab");
            return;
        }
        if (WormholeStyle.DEPTH > 0.12F) {
            helper.fail("rim depth " + WormholeStyle.DEPTH + " is a slab, not a modest recess");
            return;
        }
        assertEq(helper, WormholeStyle.VORTEX_ALPHA, 255, "vortex vertex alpha");
        assertEq(helper, WormholeStyle.WHITE[0], 255, "white rim R");
        assertEq(helper, WormholeStyle.WHITE[1], 255, "white rim G");
        assertEq(helper, WormholeStyle.WHITE[2], 255, "white rim B");
        assertEq(helper, WormholeStyle.FULL_BRIGHT, 0xF000F0, "rim packed light");
        int dark = WormholeStyle.vortexRgb(0x3366CC, 0.0F, 0.0F, 0.0F, 2, 3);
        int r = dark >> 16 & 0xFF;
        int g = dark >> 8 & 0xFF;
        int b = dark & 0xFF;
        if (r + g + b < 40) {
            helper.fail("vortex centre is black, not a shaded portal tint: " + r + "," + g + "," + b);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vortexIsPixelFunnelNotFlatFill(GameTestHelper helper) {
        int tint = 0xFF8A2B;
        int a = WormholeStyle.vortexRgb(tint, 0.01F, 0.01F, 0.4F, 2, 3);
        int b = WormholeStyle.vortexRgb(tint, 0.04F, 0.02F, 0.4F, 2, 3);
        if (a != b) {
            helper.fail("samples in the same texel must match for pixel-art snapping");
            return;
        }
        int centre = WormholeStyle.vortexRgb(tint, 0.0F, 0.0F, 0.4F, 2, 3);
        int strand = WormholeStyle.vortexRgb(tint, 0.6F, 0.15F, 0.4F, 2, 3);
        if (centre == strand) {
            helper.fail("funnel centre and a mid strand should differ (not a uniform fill)");
            return;
        }
        int cr = centre >> 16 & 0xFF;
        int cg = centre >> 8 & 0xFF;
        int sr = strand >> 16 & 0xFF;
        int sg = strand >> 8 & 0xFF;
        if (cr <= cg + 8 || sr <= sg + 8) {
            helper.fail("orange portal vortex must stay warm-hued, not black/blue");
            return;
        }
        if (Math.abs(sr - 255) < 2 && Math.abs(sg - 214) < 20) {
            helper.fail("strand is yellow-white highlight, not portal ochre");
            return;
        }
        int blue = WormholeStyle.vortexRgb(0x3B7BFF, 0.5F, 0.0F, 0.4F, 2, 3);
        if ((blue & 0xFF) < (blue >> 16 & 0xFF)) {
            helper.fail("blue portal vortex must stay blue-tinted, not forced gold");
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
