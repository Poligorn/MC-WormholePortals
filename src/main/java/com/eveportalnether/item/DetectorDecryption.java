package com.eveportalnether.item;

import com.eveportalnether.Config;
import com.eveportalnether.advancement.WormholeEvents;
import com.eveportalnether.world.PortalTier;
import com.eveportalnether.network.ModNetwork;
import com.eveportalnether.network.PortalScanPayload;
import com.eveportalnether.registry.ModItems;
import com.eveportalnether.registry.ModSounds;
import com.eveportalnether.scan.ScanReveal;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side detector readouts: the portal signature is revealed character by character
 * over a short window, during which the player must stay put and keep the detector in hand.
 */
public final class DetectorDecryption {
    private static final double MAX_DISTANCE_SQ = 7.0 * 7.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DetectorDecryption() {
    }

    private static final class Session {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final UUID pairId;
        final int duration;
        final String[] fields;
        final List<Integer> order;
        int elapsed;
        int revealed;

        Session(ResourceKey<Level> dimension, BlockPos pos, PortalPair pair, int duration, RandomSource random) {
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.pairId = pair.getId();
            this.duration = duration;
            this.fields = new String[]{pair.getSignature(), formatTime(pair.getTicksRemaining()), String.valueOf(pair.getJumpsRemaining())};
            List<Integer> indices = new ArrayList<>();
            int total = 0;
            for (String field : fields) {
                total += field.length();
            }
            for (int i = 0; i < total; i++) {
                indices.add(i);
            }
            Collections.shuffle(indices, new java.util.Random(random.nextLong()));
            this.order = indices;
        }
    }

    public static boolean isDecrypting(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void start(ServerPlayer player, ServerLevel level, BlockPos pos, PortalPair pair) {
        int duration = Config.DETECTOR_DECRYPT_TICKS.get();
        SESSIONS.put(player.getUUID(), new Session(level.dimension(), pos, pair, duration, level.getRandom()));
        ModNetwork.sendScan(player, PortalScanPayload.of(pair, duration));
        level.playSound(null, pos, ModSounds.DETECTOR_USE.get(), SoundSource.PLAYERS, 0.5F, 0.7F);
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            PortalPair pair = findPair(PortalManager.get(level), session.pairId);
            InteractionHand hand = detectorHand(player);
            if (pair == null || hand == null || level.dimension() != session.dimension
                    || player.distanceToSqr(session.pos.getCenter()) > MAX_DISTANCE_SQ) {
                it.remove();
                ModNetwork.sendScanLost(player);
                player.displayClientMessage(Component.translatable("eveportalnether.detector.lost").withStyle(ChatFormatting.RED), true);
                level.playSound(null, player.blockPosition(), ModSounds.RADAR_EMPTY.get(), SoundSource.PLAYERS, 0.5F, 1.3F);
                continue;
            }

            session.elapsed++;
            if (session.elapsed % 4 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.4, 0.5, 0.4, 0.4);
                level.sendParticles(ParticleTypes.PORTAL, session.pos.getX() + 0.5, session.pos.getY() + 0.5, session.pos.getZ() + 0.5,
                        0, player.getX() - session.pos.getX() - 0.5, player.getEyeY() - session.pos.getY() - 0.5, player.getZ() - session.pos.getZ() - 0.5, 1.0);
            }
            int total = session.order.size();
            int revealed = Math.min(total, session.elapsed * total / Math.max(1, session.duration));
            if (revealed > session.revealed) {
                session.revealed = revealed;
                level.playSound(null, player.blockPosition(), ModSounds.DETECTOR_CLICK.get(), SoundSource.PLAYERS,
                        0.4F, 1.4F + level.getRandom().nextFloat() * 0.4F);
            }

            if (session.elapsed >= session.duration) {
                it.remove();
                level.playSound(null, player.blockPosition(), ModSounds.DETECTOR_USE.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
                ItemStack detector = player.getItemInHand(hand);
                detector.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                WormholeEvents.fire(player, WormholeEvents.DECRYPT);
                if (pair.getTier() == PortalTier.MASSIVE) {
                    WormholeEvents.fire(player, WormholeEvents.DECRYPT_MASSIVE);
                }
                ScanReveal.start(player, pair);
            }
        }
    }

    @Nullable
    private static PortalPair findPair(PortalManager manager, UUID id) {
        for (PortalPair pair : manager.getPairs()) {
            if (pair.getId().equals(id)) {
                return pair;
            }
        }
        return null;
    }

    @Nullable
    private static InteractionHand detectorHand(ServerPlayer player) {
        if (player.getMainHandItem().is(ModItems.PORTAL_DETECTOR.get())) {
            return InteractionHand.MAIN_HAND;
        }
        return player.getOffhandItem().is(ModItems.PORTAL_DETECTOR.get()) ? InteractionHand.OFF_HAND : null;
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
