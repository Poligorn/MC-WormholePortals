package com.eveportalnether.compat.immersiveportals;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.WormholeStyle;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

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

    private static final ThreadLocal<Boolean> SPAWNING = ThreadLocal.withInitial(() -> Boolean.FALSE);
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
        double apertureW = innerW + WormholeStyle.APERTURE_FILL;
        double apertureH = innerH + WormholeStyle.APERTURE_FILL;
        Vec3 axisW = pair.getAxis() == Direction.Axis.X ? new Vec3(0.0, 0.0, 1.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 axisH = new Vec3(0.0, 1.0, 0.0);
        Vec3 overworldCenter = PortalShape.windowCenter(pair.getOverworldOrigin(), pair.getAxis(), pair.getWidth(), pair.getHeight());
        Vec3 netherCenter = PortalShape.windowCenter(pair.getNetherOrigin(), pair.getAxis(), pair.getWidth(), pair.getHeight());

        Spawned spawned = new Spawned();
        spawned.playerHint = player.getUUID();
        // Register before addFreshEntity so EntityJoinLevelEvent does not treat these as orphans.
        SPAWNED.put(player.getUUID(), spawned);
        SPAWNING.set(Boolean.TRUE);
        try {
            spawnFaced(overworld, overworldCenter, Level.NETHER, netherCenter, axisW, axisH, apertureW, apertureH, player, spawned);
            spawnFaced(nether, netherCenter, Level.OVERWORLD, overworldCenter, axisW, axisH, apertureW, apertureH, player, spawned);
            addLoader(player, Level.NETHER, pair.getNetherOrigin(), innerW, innerH, spawned);
            addLoader(player, Level.OVERWORLD, pair.getOverworldOrigin(), innerW, innerH, spawned);
        } catch (RuntimeException exception) {
            EvePortalNether.LOGGER.warn("Immersive Portals reveal spawn failed; leaving vortex in place", exception);
            despawnReveal(player.server, player.getUUID());
            return false;
        } finally {
            SPAWNING.set(Boolean.FALSE);
        }
        if (spawned.entities.isEmpty()) {
            despawnReveal(player.server, player.getUUID());
            return false;
        }
        EvePortalNether.LOGGER.info("Spawned {} Immersive Portals reveal entities for {} at {}",
                spawned.entities.size(), player.getGameProfile().getName(), pair.getOverworldOrigin().toShortString());
        return true;
    }

    static boolean hasTaggedPortalInWindow(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return findRevealPortal(level, origin, axis, width, height) != null;
    }

    static boolean isNestedPortalRender() {
        return PortalRendering.isRendering();
    }

    /**
     * Entity present, orientation synced, dest client world exists, and dest sections
     * at the portal center and four extent points are compiled (not {@code Null RenderChunk}).
     */
    static boolean isDestinationViewReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        if (level == null || !level.isClientSide()) {
            return false;
        }
        Portal portal = findRevealPortal(level, origin, axis, width, height);
        if (portal == null || portal.isRemoved()) {
            return false;
        }
        Vec3 dest = portal.getDestPos();
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        ResourceKey<Level> destDim = portal.getDestDim();
        if (dest == null || destDim == null || axisW == null || axisH == null) {
            return false;
        }
        if (portal.getWidth() < 0.5 || portal.getHeight() < 0.5) {
            return false;
        }
        try {
            ClientLevel destWorld = ClientWorldLoader.getOptionalWorld(destDim);
            if (destWorld == null) {
                return false;
            }
            LevelRenderer renderer = ClientWorldLoader.WORLD_RENDERER_MAP.get(destDim);
            if (renderer == null) {
                return false;
            }
            double hw = portal.getWidth() * 0.5;
            double hh = portal.getHeight() * 0.5;
            return sectionReady(destWorld, renderer, dest)
                    && sectionReady(destWorld, renderer, dest.add(axisW.scale(hw)))
                    && sectionReady(destWorld, renderer, dest.add(axisW.scale(-hw)))
                    && sectionReady(destWorld, renderer, dest.add(axisH.scale(hh)))
                    && sectionReady(destWorld, renderer, dest.add(axisH.scale(-hh)));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean sectionReady(ClientLevel destWorld, LevelRenderer renderer, Vec3 pos) {
        BlockPos block = BlockPos.containing(pos);
        if (!destWorld.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        return renderer.isSectionCompiled(block);
    }

    private static Portal findRevealPortal(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        if (level == null || origin == null || axis == null || width <= 0 || height <= 0) {
            return null;
        }
        AABB box = PortalShape.windowBox(origin, axis, width, height).inflate(1.0);
        for (Portal portal : level.getEntitiesOfClass(Portal.class, box)) {
            if (isRevealPortal(portal, origin, axis, width, height, box)) {
                return portal;
            }
        }
        // IP portals can have a degenerate bounding box until PortalSyncPacket applies
        // (NULL_BOX at 0,0,0). Spatial queries miss those; walk rendered entities by position.
        if (level.isClientSide() && level instanceof ClientLevel clientLevel) {
            for (Entity entity : clientLevel.entitiesForRendering()) {
                if (entity instanceof Portal portal && isRevealPortal(portal, origin, axis, width, height, box)) {
                    return portal;
                }
            }
        }
        return null;
    }

    private static boolean isRevealPortal(Portal portal, BlockPos origin, Direction.Axis axis,
                                          int width, int height, AABB box) {
        if (!PORTAL_TAG.equals(portal.portalTag)) {
            return false;
        }
        if (PortalShape.contains(origin, axis, width, height, BlockPos.containing(portal.position()))) {
            return true;
        }
        AABB portalBox = portal.getBoundingBox();
        return portalBox != null && box.intersects(portalBox);
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
        if (Boolean.TRUE.equals(SPAWNING.get()) || isTracked(portal.getUUID())) {
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
