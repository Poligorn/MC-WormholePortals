package com.eveportalnether.scan;

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

    public static void start(ServerPlayer player, PortalPair pair) {
        stop(player.server, player.getUUID());
        if (!ImmersivePortalsHook.spawnReveal(player, pair)) {
            return;
        }
        long expireAt = player.server.overworld().getGameTime() + DURATION_TICKS;
        SESSIONS.put(player.getUUID(), new Session(pair.getId(), expireAt));
        ModNetwork.sendReveal(player, ScanRevealPayload.of(pair, DURATION_TICKS));
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (now >= entry.getValue().expireAt) {
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
        if (player != null) {
            ModNetwork.sendReveal(player, ScanRevealPayload.clear());
        }
    }

    private record Session(UUID pairId, long expireAt) {
    }
}
