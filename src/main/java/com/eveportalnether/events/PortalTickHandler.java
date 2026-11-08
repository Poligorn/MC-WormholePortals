package com.eveportalnether.events;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.item.DetectorDecryption;
import com.eveportalnether.scan.ScanReveal;
import com.eveportalnether.world.PortalSpawner;
import com.eveportalnether.world.data.PortalManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = EvePortalNether.MODID)
public class PortalTickHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        PortalManager manager = PortalManager.get(overworld);
        manager.tick(server);
        PortalSpawner.tick(server, manager);
        DetectorDecryption.tick(server);
        ScanReveal.tick(server);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScanReveal.stop(player.server, player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ScanReveal.stopAll(event.getServer());
    }
}
