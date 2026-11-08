package com.eveportalnether.scan;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.compat.immersiveportals.ImmersivePortalsHook;
import com.eveportalnether.network.ModNetwork;
import com.eveportalnether.network.ScanRevealPayload;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player through-view window after a successful detector scan. Duration is the
 * live destination view <em>after</em> decrypt completes, not the decrypt itself.
 * One session per player; a later successful scan replaces the previous one.
 * <p>
 * Dest chunks are preloaded when decrypt <em>starts</em> so they can be compiled by
 * COMPLETE. {@link #start} must not despawn a preload for the same pair — that was
 * wiping the dest renderer and leaving a fully opaque vortex after ГОТОВО.
 */
public final class ScanReveal {
    /** 60 seconds at 20 TPS, including the 2s open and 2s close irises. */
    public static final int DURATION_TICKS = 1200;
    /** 2.0 s ease-in-out at 20 TPS. */
    public static final int TRANSITION_TICKS = 40;

    public static float ease(float t) {
        t = t < 0.0F ? 0.0F : Math.min(1.0F, t);
        return t * t * (3.0F - 2.0F * t);
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private ScanReveal() {
    }

    /**
     * Keep an existing IP spawn when the new scan is the same pair. Despawn+respawn
     * on COMPLETE dropped compiled dest sections.
     */
    public static boolean shouldKeepSpawn(UUID existingPair, UUID incomingPair, boolean revealing) {
        return existingPair != null && existingPair.equals(incomingPair) && revealing;
    }

    public static void preload(ServerPlayer player, PortalPair pair) {
        if (!ImmersivePortalsHook.available()) {
            return;
        }
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && shouldKeepSpawn(existing.pairId, pair.getId(), true)) {
            SESSIONS.put(player.getUUID(), new Session(pair.getId(), Long.MAX_VALUE / 4, true));
            return;
        }
        stop(player.server, player.getUUID());
        if (!ImmersivePortalsHook.spawnReveal(player, pair)) {
            EvePortalNether.LOGGER.warn("Scan reveal preload failed for {} at {}; dest will compile after COMPLETE if at all",
                    player.getGameProfile().getName(), pair.getOverworldOrigin().toShortString());
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(pair.getId(), Long.MAX_VALUE / 4, true));
        EvePortalNether.LOGGER.info("Scan reveal preload for {} at {}",
                player.getGameProfile().getName(), pair.getOverworldOrigin().toShortString());
    }

    public static void start(ServerPlayer player, PortalPair pair) {
        if (!ImmersivePortalsHook.available()) {
            return;
        }
        Session existing = SESSIONS.get(player.getUUID());
        boolean keep = existing != null && shouldKeepSpawn(existing.pairId, pair.getId(), true);
        if (!keep) {
            stop(player.server, player.getUUID());
            if (!ImmersivePortalsHook.spawnReveal(player, pair)) {
                EvePortalNether.LOGGER.warn("Scan reveal spawn failed for {} at {}; vortex stays opaque",
                        player.getGameProfile().getName(), pair.getOverworldOrigin().toShortString());
                return;
            }
        }
        long expireAt = player.server.overworld().getGameTime() + DURATION_TICKS;
        SESSIONS.put(player.getUUID(), new Session(pair.getId(), expireAt, false));
        ModNetwork.sendReveal(player, ScanRevealPayload.of(pair, DURATION_TICKS));
        EvePortalNether.LOGGER.info("Scan reveal started for {} ({} {}x{}, {} ticks, keepSpawn={})",
                player.getGameProfile().getName(), pair.getSignature(), pair.getWidth(), pair.getHeight(),
                DURATION_TICKS, keep);
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (!entry.getValue().preload && now >= entry.getValue().expireAt) {
                expired.add(entry.getKey());
            }
        }
        for (UUID playerId : expired) {
            stop(server, playerId);
        }
    }

    public static void stopPair(MinecraftServer server, UUID pairId) {
        List<UUID> matching = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (entry.getValue().pairId.equals(pairId)) {
                matching.add(entry.getKey());
            }
        }
        for (UUID playerId : matching) {
            stop(server, playerId);
        }
    }

    public static void stopAll(MinecraftServer server) {
        List<UUID> ids = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : ids) {
            stop(server, playerId);
        }
    }

    public static void stop(MinecraftServer server, UUID playerId) {
        Session removed = SESSIONS.remove(playerId);
        ImmersivePortalsHook.despawnReveal(server, playerId);
        if (removed == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null && !removed.preload) {
            ModNetwork.sendReveal(player, ScanRevealPayload.clear());
        }
    }

    private record Session(UUID pairId, long expireAt, boolean preload) {
    }
}
