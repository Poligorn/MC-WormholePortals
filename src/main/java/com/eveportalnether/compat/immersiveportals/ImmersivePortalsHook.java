package com.eveportalnether.compat.immersiveportals;

import com.eveportalnether.world.data.PortalPair;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
}
