package com.eveportalnether.compat.immersiveportals;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated Immersive Portals 6.0.7 adapter. Loaded only via {@link ImmersivePortalsHook}
 * after {@code ModList.isLoaded("immersive_portals_core")}. Do not annotate with
 * {@code @EventBusSubscriber}: that would class-load this file on every boot.
 * <p>
 * Public API used (Immersive Portals 6.0.7 NeoForge sources):
 * {@link Portal#ENTITY_TYPE}, {@link Portal#setOriginPos}, {@link Portal#setDestinationDimension},
 * {@link Portal#setDestination}, {@link Portal#setOrientationAndSize}, {@link Portal#setTeleportable},
 * {@link Portal#setInteractable}, {@link Portal#setCrossPortalCollisionEnabled},
 * {@link Portal#specificPlayerId}, {@link Portal#broadcastToPlayer}, {@link Portal#portalTag},
 * {@link Portal#reloadAndSyncToClientNextTick}, {@link PortalAPI#createFlippedPortal},
 * {@link PortalAPI#addChunkLoaderForPlayer}, {@link PortalAPI#removeChunkLoaderForPlayer},
 * {@link ChunkLoader}.
 * <p>
 * {@code specificPlayerId} is not teleport-only: {@code broadcastToPlayer} returns true only for
 * that UUID, which is the entity-tracking gate. Other players never receive the portal entity.
 * {@code teleportable=false} also makes {@code canCollideWithEntity} false, so Wormholes remains
 * the only teleport owner.
 * <p>
 * No non-public IP coupling. AeroPortals transfer internals are not referenced.
 */
public final class ImmersivePortalsCompat {
    static final String PORTAL_TAG = "eveportalnether:scan_reveal";

    private static final Map<UUID, Spawned> SPAWNED = new ConcurrentHashMap<>();

    private ImmersivePortalsCompat() {
    }

    static void register() {
        NeoForge.EVENT_BUS.register(ImmersivePortalsCompat.class);
        EvePortalNether.LOGGER.info("Registered optional Immersive Portals 6.0.7 through-view adapter");
    }

    static boolean spawnReveal(ServerPlayer player, PortalPair pair) {
        despawnReveal(player.server, player.getUUID());
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        ServerLevel nether = player.server.getLevel(Level.NETHER);
        if (overworld == null || nether == null) {
            return false;
        }

        int innerW = Math.max(1, pair.getWidth() - 2);
        int innerH = Math.max(1, pair.getHeight() - 2);
        Vec3 axisW = pair.getAxis() == Direction.Axis.X ? new Vec3(0.0, 0.0, 1.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 axisH = new Vec3(0.0, 1.0, 0.0);
        Vec3 overworldCenter = PortalShape.windowCenter(pair.getOverworldOrigin(), pair.getAxis(), pair.getWidth(), pair.getHeight());
        Vec3 netherCenter = PortalShape.windowCenter(pair.getNetherOrigin(), pair.getAxis(), pair.getWidth(), pair.getHeight());

        Spawned spawned = new Spawned();
        spawned.playerHint = player.getUUID();
        // Register before addFreshEntity so EntityJoinLevelEvent does not treat these as orphans.
        SPAWNED.put(player.getUUID(), spawned);
        try {
            spawnFaced(overworld, overworldCenter, Level.NETHER, netherCenter, axisW, axisH, innerW, innerH, player, spawned);
            spawnFaced(nether, netherCenter, Level.OVERWORLD, overworldCenter, axisW, axisH, innerW, innerH, player, spawned);
            addLoader(player, Level.NETHER, pair.getNetherOrigin(), innerW, innerH, spawned);
            addLoader(player, Level.OVERWORLD, pair.getOverworldOrigin(), innerW, innerH, spawned);
        } catch (RuntimeException exception) {
            EvePortalNether.LOGGER.warn("Immersive Portals reveal spawn failed; leaving vortex in place", exception);
            despawnReveal(player.server, player.getUUID());
            return false;
        }
        if (spawned.entities.isEmpty()) {
            despawnReveal(player.server, player.getUUID());
            return false;
        }
        return true;
    }

    static void despawnReveal(MinecraftServer server, UUID playerId) {
        Spawned spawned = SPAWNED.remove(playerId);
        if (spawned == null) {
            return;
        }
        discardSpawned(server, spawned);
    }

    /**
     * Drop tagged reveal portals that were saved from a previous session (crash / autosave).
     * UUIDs are registered on {@link Spawned} before {@code addFreshEntity}, so live spawns pass.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Portal portal)) {
            return;
        }
        if (!PORTAL_TAG.equals(portal.portalTag)) {
            return;
        }
        if (isTracked(portal.getUUID())) {
            return;
        }
        event.setCanceled(true);
        portal.discard();
    }

    private static boolean isTracked(UUID entityId) {
        for (Spawned spawned : SPAWNED.values()) {
            for (TrackedEntity tracked : spawned.entities) {
                if (tracked.id().equals(entityId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void spawnFaced(ServerLevel level, Vec3 origin, ResourceKey<Level> destDim, Vec3 dest,
                                   Vec3 axisW, Vec3 axisH, double width, double height,
                                   ServerPlayer player, Spawned spawned) {
        Portal portal = createPortal(level, origin, destDim, dest, axisW, axisH, width, height, player);
        spawned.entities.add(new TrackedEntity(portal.getUUID(), level.dimension()));
        level.addFreshEntity(portal);
        portal.reloadAndSyncToClientNextTick();

        Portal flipped = PortalAPI.createFlippedPortal(portal);
        configure(flipped, player);
        spawned.entities.add(new TrackedEntity(flipped.getUUID(), level.dimension()));
        level.addFreshEntity(flipped);
        flipped.reloadAndSyncToClientNextTick();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Portal createPortal(ServerLevel level, Vec3 origin, ResourceKey<Level> destDim, Vec3 dest,
                                       Vec3 axisW, Vec3 axisH, double width, double height, ServerPlayer player) {
        Entity created = Portal.ENTITY_TYPE.create(level);
        if (!(created instanceof Portal portal)) {
            throw new IllegalStateException("Immersive Portals entity type did not construct a Portal");
        }
        portal.setOriginPos(origin);
        portal.setDestinationDimension(destDim);
        portal.setDestination(dest);
        portal.setOrientationAndSize(axisW, axisH, width, height);
        configure(portal, player);
        return portal;
    }

    private static void configure(Portal portal, ServerPlayer player) {
        portal.setTeleportable(false);
        portal.setInteractable(false);
        portal.setCrossPortalCollisionEnabled(false);
        portal.specificPlayerId = player.getUUID();
        portal.portalTag = PORTAL_TAG;
    }

    private static void addLoader(ServerPlayer player, ResourceKey<Level> dimension, BlockPos origin,
                                  int innerW, int innerH, Spawned spawned) {
        ChunkPos chunk = new ChunkPos(origin);
        int radius = Mth.clamp(Math.max(innerW, innerH) / 16 + 1, 1, 4);
        ChunkLoader loader = new ChunkLoader(dimension, chunk.x, chunk.z, radius);
        PortalAPI.addChunkLoaderForPlayer(player, loader);
        spawned.loaders.add(new TrackedLoader(player.getUUID(), loader));
    }

    private static void discardSpawned(MinecraftServer server, Spawned spawned) {
        ServerPlayer player = server.getPlayerList().getPlayer(spawned.playerHint);
        for (TrackedLoader tracked : spawned.loaders) {
            ServerPlayer owner = player != null ? player : server.getPlayerList().getPlayer(tracked.playerId());
            if (owner != null) {
                try {
                    PortalAPI.removeChunkLoaderForPlayer(owner, tracked.loader());
                } catch (RuntimeException exception) {
                    EvePortalNether.LOGGER.debug("Reveal chunk-loader remove skipped: {}", exception.toString());
                }
            }
        }
        spawned.loaders.clear();

        Iterator<TrackedEntity> iterator = spawned.entities.iterator();
        while (iterator.hasNext()) {
            TrackedEntity tracked = iterator.next();
            iterator.remove();
            ServerLevel level = server.getLevel(tracked.dimension());
            if (level == null) {
                continue;
            }
            Entity entity = level.getEntity(tracked.id());
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static final class Spawned {
        final List<TrackedEntity> entities = new ArrayList<>();
        final List<TrackedLoader> loaders = new ArrayList<>();
        UUID playerHint;
    }

    private record TrackedEntity(UUID id, ResourceKey<Level> dimension) {
    }

    private record TrackedLoader(UUID playerId, ChunkLoader loader) {
    }
}
