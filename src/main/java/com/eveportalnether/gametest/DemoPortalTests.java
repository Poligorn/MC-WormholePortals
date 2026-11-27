package com.eveportalnether.gametest;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalTier;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(EvePortalNether.MODID)
@PrefixGameTestTemplate(false)
public final class DemoPortalTests {
    private DemoPortalTests() {
    }

    @GameTest(template = "empty")
    public static void demoFlagPersistsInNbt(GameTestHelper helper) {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID owner = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PortalPair pair = new PortalPair(id, new BlockPos(8, 64, 12), BlockPos.ZERO,
                Direction.Axis.Z, 4, 5, PortalTier.UNSTABLE, PortalPair.DEMO_LIFETIME_TICKS, 8);
        pair.setDemo(true);
        pair.setOwnerId(owner);
        pair.setDemoDimension(Level.OVERWORLD);
        pair.setCode(42);

        CompoundTag tag = pair.save();
        if (!tag.getBoolean("Demo")) {
            helper.fail("Demo flag missing from NBT");
            return;
        }
        if (!owner.equals(tag.getUUID("Owner"))) {
            helper.fail("Owner UUID missing from NBT");
            return;
        }

        PortalPair loaded = PortalPair.load(tag);
        if (!loaded.isDemo()) {
            helper.fail("Loaded pair lost the demo flag");
            return;
        }
        if (!owner.equals(loaded.getOwnerId())) {
            helper.fail("Loaded pair lost the owner");
            return;
        }
        if (loaded.getDemoDimension() != Level.OVERWORLD) {
            helper.fail("Loaded pair lost the demo dimension");
            return;
        }
        if (loaded.existsIn(Level.NETHER) || !loaded.existsIn(Level.OVERWORLD)) {
            helper.fail("Demo pair must exist only in its placement dimension");
            return;
        }
        if (loaded.contains(Level.NETHER, loaded.getOverworldOrigin())) {
            helper.fail("Demo pair must not contain Nether positions");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanStartsShortWarningWindow(GameTestHelper helper) {
        PortalPair pair = new PortalPair(UUID.randomUUID(), new BlockPos(0, 64, 0), BlockPos.ZERO,
                Direction.Axis.Z, 4, 5, PortalTier.UNSTABLE, PortalPair.DEMO_LIFETIME_TICKS, 8);
        pair.setDemo(true);
        pair.setFormingTicks(40);
        pair.beginDemoCollapse();
        if (pair.isForming()) {
            helper.fail("beginDemoCollapse should end forming");
            return;
        }
        if (pair.getTicksRemaining() != PortalPair.DEMO_WARNING_TICKS) {
            helper.fail("expected " + PortalPair.DEMO_WARNING_TICKS + " warning ticks, got " + pair.getTicksRemaining());
            return;
        }
        if (!pair.isWarning() || pair.getDecay() != PortalPair.DECAY_DYING) {
            helper.fail("demo should immediately enter the dying warning window");
            return;
        }
        if (pair.getWarningWindow() != PortalPair.DEMO_WARNING_TICKS) {
            helper.fail("demo warning window should be 100 ticks");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyNbtIsNotDemo(GameTestHelper helper) {
        PortalPair pair = new PortalPair(UUID.randomUUID(), new BlockPos(1, 70, 1), new BlockPos(1, 70, 1),
                Direction.Axis.X, 8, 10, PortalTier.STANDARD, 20 * 60 * 30, 6);
        CompoundTag tag = pair.save();
        if (tag.getBoolean("Demo") || tag.hasUUID("Owner")) {
            helper.fail("natural portals must not write demo NBT");
            return;
        }
        PortalPair loaded = PortalPair.load(tag);
        if (loaded.isDemo() || loaded.getOwnerId() != null) {
            helper.fail("legacy NBT must load as a normal linked pair");
            return;
        }
        if (!loaded.existsIn(Level.OVERWORLD) || !loaded.existsIn(Level.NETHER)) {
            helper.fail("natural pairs exist on both sides");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void demoUsesSmallUnstableFrame(GameTestHelper helper) {
        int width = PortalShape.minWidth(PortalTier.UNSTABLE);
        int height = PortalShape.minHeight(PortalTier.UNSTABLE);
        if (width > 4 || height > 5) {
            helper.fail("training portal should use the smallest unstable frame, got " + width + "x" + height);
            return;
        }
        if (PortalPair.DEMO_LIFETIME_TICKS != 20 * 90) {
            helper.fail("demo lifetime should be 90 seconds");
            return;
        }
        if (PortalPair.DEMO_MIN_HEALTH != 2.0F) {
            helper.fail("demo implosion must leave at least one heart");
            return;
        }
        helper.succeed();
    }
}
