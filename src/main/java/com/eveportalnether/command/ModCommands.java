package com.eveportalnether.command;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalSpawner;
import com.eveportalnether.world.PortalTier;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = EvePortalNether.MODID)
public class ModCommands {
    private static final String[] STAGES = {"fresh", "worn", "critical", "dying"};

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn")
                .executes(context -> spawnPortalCommand(context.getSource(), PortalTier.roll(context.getSource().getLevel().getRandom())));
        for (PortalTier tier : PortalTier.values()) {
            spawn.then(Commands.literal(tier.getSerializedName())
                    .executes(context -> spawnPortalCommand(context.getSource(), tier)));
        }

        LiteralArgumentBuilder<CommandSourceStack> stage = Commands.literal("stage");
        for (int i = 0; i < STAGES.length; i++) {
            int target = i;
            stage.then(Commands.literal(STAGES[i]).executes(context -> stageCommand(context.getSource(), target)));
        }

        dispatcher.register(Commands.literal("eveportal")
                .requires(source -> source.hasPermission(2))
                .then(spawn)
                .then(stage)
                .then(Commands.literal("skip")
                        .executes(context -> skipCommand(context.getSource())))
                .then(Commands.literal("collapse")
                        .executes(context -> stageCommand(context.getSource(), PortalPair.DECAY_DYING)))
        );
    }

    private static int spawnPortalCommand(CommandSourceStack source, PortalTier tier) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Command must be executed by a player."));
            return 0;
        }

        PortalPair pair = PortalSpawner.forceSpawnLooking(player, tier);
        if (pair == null) {
            source.sendFailure(Component.literal("Could not spawn a linked portal pair."));
            return 0;
        }

        PortalShape.OuterRange wr = PortalShape.widthRange(tier);
        PortalShape.OuterRange hr = PortalShape.heightRange(tier);
        source.sendSuccess(() -> Component.literal("Portal " + pair.getSignature() + " (" + tier.getSerializedName() + ", "
                + pair.getWidth() + "x" + pair.getHeight()
                + " of class " + wr.min() + "–" + wr.max() + "×" + hr.min() + "–" + hr.max()
                + (wr.classFallback() || hr.classFallback()
                ? "; global spawn min/max was ignored so this class can still randomize"
                : "")
                + ", " + pair.getColor().getSerializedName() + "). Overworld "
                + pair.getOverworldOrigin().toShortString()
                + " / Nether "
                + pair.getNetherOrigin().toShortString()
                + " | " + pair.getTicksRemaining() + " ticks, "
                + pair.getJumpsRemaining() + " entries"), true);
        return 1;
    }

    private static int skipCommand(CommandSourceStack source) {
        PortalPair pair = nearest(source);
        if (pair == null) {
            return 0;
        }
        int next = pair.isForming() ? PortalPair.DECAY_FRESH : Math.min(PortalPair.DECAY_DYING, pair.getDecay() + 1);
        return stageCommand(source, next);
    }

    private static int stageCommand(CommandSourceStack source, int stage) {
        PortalPair pair = nearest(source);
        if (pair == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        PortalManager manager = PortalManager.get(level);
        int warning = Config.WARNING_DURATION_TICKS.get();
        int initial = pair.getInitialTicks();

        if (pair.isForming()) {
            // One tick left, so the next manager tick plays the full opening sequence.
            pair.setFormingTicks(stage == PortalPair.DECAY_FRESH ? 1 : 0);
        }
        pair.setJumpsRemaining(Math.max(2, pair.getInitialJumps()));
        switch (stage) {
            case PortalPair.DECAY_FRESH -> pair.setTicksRemaining(initial);
            case PortalPair.DECAY_WORN -> pair.setTicksRemaining(Math.max(warning + 20, (int) (initial * 0.4)));
            case PortalPair.DECAY_CRITICAL -> pair.setTicksRemaining(Math.max(warning + 20, (int) (initial * 0.12)));
            default -> pair.setTicksRemaining(warning + 1);
        }
        manager.setDirty();
        for (ServerLevel side : new ServerLevel[]{level.getServer().getLevel(Level.OVERWORLD), level.getServer().getLevel(Level.NETHER)}) {
            if (side != null) {
                PortalManager.syncDecay(side, pair);
            }
        }

        source.sendSuccess(() -> Component.literal("Portal " + pair.getSignature() + " -> " + STAGES[stage]
                + " (" + pair.getTicksRemaining() + " ticks, " + pair.getJumpsRemaining() + " entries)"), true);
        return 1;
    }

    private static PortalPair nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        PortalPair pair = PortalManager.get(level).findNearest(level.dimension(), BlockPos.containing(source.getPosition()));
        if (pair == null) {
            source.sendFailure(Component.literal("No active portal pairs."));
        }
        return pair;
    }
}
