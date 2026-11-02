package com.eveportalnether.compat.aeroportals;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Safe entry point from the rest of the mod. Does not mention AeroPortals or Sable
 * types, so {@code EvePortalNether} can load when those mods are absent.
 * {@link AeroPortalsCompat} is only class-loaded after both mods are confirmed present.
 */
public final class AeroPortalsHook {
    public static final String AEROPORTALS_MOD_ID = "aeroportals";
    public static final String SABLE_MOD_ID = "sable";

    private AeroPortalsHook() {
    }

    public static boolean available() {
        return ModList.get().isLoaded(AEROPORTALS_MOD_ID) && ModList.get().isLoaded(SABLE_MOD_ID);
    }

    public static void register() {
        if (available()) {
            AeroPortalsCompat.register();
        }
    }

    /**
     * Players riding (or otherwise claimed by) a Sable ship must not also take the
     * walking-player wormhole path, or they leave the ship behind / consume a jump twice.
     */
    public static boolean shouldSkipPlayerTravel(Player player) {
        return available() && AeroPortalsCompat.shouldSkipPlayerTravel(player);
    }
}
