package com.eveportalnether.network;

import com.eveportalnether.client.ClientPayloadHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Single entry point for client/server sync. Clientbound handlers live in {@link ClientPayloadHandler}
 * and are only resolved on the client when a packet actually arrives.
 */
public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "5";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(AnonymityPayload.TYPE, AnonymityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleAnonymity(payload)));
        registrar.playToClient(ShakePayload.TYPE, ShakePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleShake(payload)));
        registrar.playToClient(PortalScanPayload.TYPE, PortalScanPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleScan(payload)));
        registrar.playToClient(ScanLostPayload.TYPE, ScanLostPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientPayloadHandler::handleScanLost));
        registrar.playToClient(TransitPayload.TYPE, TransitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleTransit(payload)));
        registrar.playToClient(ScanRevealPayload.TYPE, ScanRevealPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleReveal(payload)));
    }

    public static void sendTransit(ServerPlayer player, TransitPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendScanLost(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ScanLostPayload.INSTANCE);
    }

    public static void sendAnonymity(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new AnonymityPayload(enabled));
    }

    public static void sendShake(ServerPlayer player, float strength, int duration) {
        PacketDistributor.sendToPlayer(player, new ShakePayload(strength, duration));
    }

    public static void sendScan(ServerPlayer player, PortalScanPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendReveal(ServerPlayer player, ScanRevealPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
