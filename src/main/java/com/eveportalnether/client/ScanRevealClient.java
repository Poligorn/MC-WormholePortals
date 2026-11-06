package com.eveportalnether.client;

import com.eveportalnether.network.ScanRevealPayload;
import com.eveportalnether.world.PortalShape;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Local 60s through-view gate. Only the scanning client receives {@link ScanRevealPayload},
 * so other players keep drawing the vortex.
 */
public final class ScanRevealClient {
    private static ScanRevealPayload active;
    private static long expireAt = -1L;

    private ScanRevealClient() {
    }

    public static void handle(ScanRevealPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.isClear() || minecraft.level == null) {
            clear();
            return;
        }
        active = payload;
        expireAt = minecraft.level.getGameTime() + payload.durationTicks();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || minecraft.level == null) {
            return;
        }
        if (minecraft.level.getGameTime() >= expireAt) {
            clear();
        }
    }

    public static void clear() {
        active = null;
        expireAt = -1L;
    }

    public static boolean isRevealing(Level level, BlockPos pos) {
        ScanRevealPayload payload = active;
        if (payload == null || level == null || payload.isClear()) {
            return false;
        }
        if (level.getGameTime() >= expireAt) {
            clear();
            return false;
        }
        BlockPos origin = level.dimension() == Level.NETHER ? payload.netherOrigin() : payload.overworldOrigin();
        return PortalShape.contains(origin, payload.axis(), payload.width(), payload.height(), pos);
    }
}
