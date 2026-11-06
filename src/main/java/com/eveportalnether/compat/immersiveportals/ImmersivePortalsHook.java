package com.eveportalnether.compat.immersiveportals;

import com.eveportalnether.world.data.PortalPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Safe entry point from the rest of the mod. Does not mention Immersive Portals types,
 * so {@code EvePortalNether} can load when that mod is absent.
 * {@link ImmersivePortalsCompat} is only class-loaded after {@code ModList.isLoaded} succeeds.
 */
public final class ImmersivePortalsHook {
    public static final String MOD_ID = "immersive_portals_core";

    private ImmersivePortalsHook() {
    }

    public static boolean available() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void register() {
        if (available()) {
            ImmersivePortalsCompat.register();
        }
    }

    public static boolean spawnReveal(ServerPlayer player, PortalPair pair) {
        return available() && ImmersivePortalsCompat.spawnReveal(player, pair);
    }

    public static void despawnReveal(MinecraftServer server, UUID playerId) {
        if (available()) {
            ImmersivePortalsCompat.despawnReveal(server, playerId);
        }
    }

    /**
     * Client/server: a tagged reveal portal is actually in the window.
     * The iris must not punch a hole until this is true, or the local world would show.
     */
    public static boolean hasRevealPortal(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return available() && ImmersivePortalsCompat.hasTaggedPortalInWindow(level, origin, axis, width, height);
    }

    /**
     * Client: tagged portal exists, is synced, and dest-side sections are compiled.
     * Opening the iris on entity presence alone exposes an unmeshed dest.
     */
    public static boolean isDestinationViewReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return available() && ImmersivePortalsCompat.isDestinationViewReady(level, origin, axis, width, height);
    }

    /** Client: Immersive Portals is currently rendering a nested portal world. */
    public static boolean isNestedPortalRender() {
        return available() && ImmersivePortalsCompat.isNestedPortalRender();
    }
}
