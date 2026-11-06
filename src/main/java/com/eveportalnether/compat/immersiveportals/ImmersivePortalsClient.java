package com.eveportalnether.compat.immersiveportals;

import com.eveportalnether.world.PortalShape;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * Client-only Immersive Portals lookups. Must not be referenced from
 * {@link ImmersivePortalsCompat} or other common types — those load on dedicated servers
 * that have no {@code net.minecraft.client} classes.
 */
public final class ImmersivePortalsClient {
    private ImmersivePortalsClient() {
    }

    public static boolean isNestedPortalRender() {
        return PortalRendering.isRendering();
    }

    public static boolean hasRevealPortal(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return findRevealPortal(level, origin, axis, width, height) != null;
    }

    public static boolean isDestinationViewReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return destinationReady(level, origin, axis, width, height, true);
    }

    public static boolean isDestinationCenterReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return destinationReady(level, origin, axis, width, height, false);
    }

    private static boolean destinationReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height,
                                           boolean requireExtents) {
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
            if (!sectionReady(destWorld, renderer, dest)) {
                return false;
            }
            if (!requireExtents) {
                return true;
            }
            double hw = portal.getWidth() * 0.5;
            double hh = portal.getHeight() * 0.5;
            return sectionReady(destWorld, renderer, dest.add(axisW.scale(hw)))
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
        if (level instanceof ClientLevel clientLevel) {
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
        if (!ImmersivePortalsCompat.PORTAL_TAG.equals(portal.portalTag)) {
            return false;
        }
        if (PortalShape.contains(origin, axis, width, height, BlockPos.containing(portal.position()))) {
            return true;
        }
        AABB portalBox = portal.getBoundingBox();
        return portalBox != null && box.intersects(portalBox);
    }
}
