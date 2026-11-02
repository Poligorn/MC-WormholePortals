package com.eveportalnether.compat.aeroportals;

import com.breakinblocks.aeroportals.api.AeroPortalsApi;
import com.breakinblocks.aeroportals.api.SubLevelPreTransferEvent;
import com.breakinblocks.aeroportals.api.SubLevelTransferEvent;
import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated AeroPortals integration. Loaded only via {@link AeroPortalsHook} after
 * {@code ModList.isLoaded("aeroportals")} (and Sable) succeed. Do not annotate with
 * {@code @EventBusSubscriber}: that would class-load this file on every boot.
 */
public final class AeroPortalsCompat {
    private static final Map<UUID, UUID> PRIMARY_SUB_TO_PAIR = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CHAIN_SUB_TO_PAIR = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING_CONSUME = new ConcurrentHashMap<>();
    private static final Map<UUID, ReturnLock> RETURN_LOCKS = new ConcurrentHashMap<>();

    private AeroPortalsCompat() {
    }

    static void register() {
        AeroPortalsApi.registerPortal(new WormholeAeroPortalType());
        NeoForge.EVENT_BUS.register(AeroPortalsCompat.class);
        EvePortalNether.LOGGER.info("Registered optional AeroPortals adapter ({})", WormholeAeroPortalType.ID);
    }

    static void rememberPrimary(UUID subId, UUID pairId) {
        PRIMARY_SUB_TO_PAIR.put(subId, pairId);
    }

    static boolean isReturnLocked(UUID subId, UUID pairId, long gameTime) {
        pruneLocks(gameTime);
        ReturnLock lock = RETURN_LOCKS.get(subId);
        return lock != null && lock.pairId.equals(pairId);
    }

    static boolean shouldSkipPlayerTravel(Player player) {
        return player.isPassenger();
    }

    @SubscribeEvent
    public static void onPreTransfer(SubLevelPreTransferEvent event) {
        if (!WormholeAeroPortalType.LABEL.equals(event.label())) {
            return;
        }
        UUID primary = WormholeAeroPortalType.uuidOf(event.sub());
        UUID pairId = primary == null ? null : PRIMARY_SUB_TO_PAIR.get(primary);
        if (pairId == null) {
            return;
        }
        PortalPair pair = findPair(event.srcLevel(), pairId);
        if (pair == null || pair.isForming() || pair.isExpired()) {
            PRIMARY_SUB_TO_PAIR.remove(primary);
            event.cancel("wormhole is not ready");
            return;
        }
        PENDING_CONSUME.put(pairId, Boolean.TRUE);
        for (Object member : event.chain()) {
            if (member instanceof ServerSubLevel sub) {
                UUID id = WormholeAeroPortalType.uuidOf(sub);
                if (id != null) {
                    CHAIN_SUB_TO_PAIR.put(id, pairId);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTransfer(SubLevelTransferEvent event) {
        UUID subId = event.subUuid();
        UUID pairId = CHAIN_SUB_TO_PAIR.remove(subId);
        if (pairId == null) {
            pairId = PRIMARY_SUB_TO_PAIR.remove(subId);
        }
        if (pairId == null) {
            return;
        }

        ServerLevel dst = event.dstLevel();
        long expire = dst.getGameTime() + Math.max(0, Config.RETURN_LOCK_TICKS.get());
        RETURN_LOCKS.put(subId, new ReturnLock(pairId, expire));
        pruneLocks(dst.getGameTime());

        if (PENDING_CONSUME.remove(pairId) == null) {
            return;
        }

        MinecraftServer server = dst.getServer();
        PortalManager manager = PortalManager.get(dst);
        PortalPair pair = findPair(dst, pairId);
        if (pair == null) {
            return;
        }

        pair.consumeJump();
        manager.setDirty();
        if (pair.isExpired()) {
            // Quiet close: the crew is already at the destination portal, so an implosion
            // would kill them. Walking players still implode via WormholeBlock.entityInside.
            manager.collapse(server, pair);
        } else {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (overworld != null) {
                PortalManager.syncDecay(overworld, pair);
            }
            if (nether != null) {
                PortalManager.syncDecay(nether, pair);
            }
        }
    }

    private static PortalPair findPair(ServerLevel level, UUID pairId) {
        for (PortalPair pair : PortalManager.get(level).getPairs()) {
            if (pair.getId().equals(pairId)) {
                return pair;
            }
        }
        return null;
    }

    private static void pruneLocks(long gameTime) {
        if (gameTime != Long.MIN_VALUE) {
            RETURN_LOCKS.entrySet().removeIf(entry -> entry.getValue().expireTick <= gameTime);
        }
    }

    private record ReturnLock(UUID pairId, long expireTick) {
    }
}
