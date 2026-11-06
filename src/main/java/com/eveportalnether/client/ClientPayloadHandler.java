package com.eveportalnether.client;

import com.eveportalnether.network.AnonymityPayload;
import com.eveportalnether.network.PortalScanPayload;
import com.eveportalnether.network.ScanRevealPayload;
import com.eveportalnether.network.ShakePayload;
import com.eveportalnether.network.TransitPayload;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleAnonymity(AnonymityPayload payload) {
        AnonymityPayload.clientEnabled = payload.enabled();
    }

    public static void handleShake(ShakePayload payload) {
        ShakePayload.applyOnClient(payload);
    }

    public static void handleScan(PortalScanPayload payload) {
        ScanPanel.show(payload);
    }

    public static void handleScanLost() {
        ScanPanel.lost();
    }

    public static void handleTransit(TransitPayload payload) {
        TransitScreen.begin(payload);
    }

    public static void handleReveal(ScanRevealPayload payload) {
        ScanRevealClient.handle(payload);
    }
}
