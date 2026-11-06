package com.eveportalnether.client;

import com.eveportalnether.compat.immersiveportals.ImmersivePortalsHook;
import com.eveportalnether.network.ScanRevealPayload;
import com.eveportalnether.scan.ScanReveal;
import com.eveportalnether.world.PortalShape;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Local 60s through-view gate. Only the scanning client receives {@link ScanRevealPayload},
 * so other players keep drawing the vortex. The iris never opens unless a tagged IP portal
 * is actually present, so a missing destination cannot expose the local world.
 */
public final class ScanRevealClient {
    private static ScanRevealPayload active;
    private static long expireAt = -1L;
    private static long irisClockStart = -1L;
    private static long readyTick = Long.MIN_VALUE;
    private static boolean readyCached;

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
        irisClockStart = -1L;
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
        irisClockStart = -1L;
        readyTick = Long.MIN_VALUE;
        readyCached = false;
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

    /**
     * 0 = fully opaque vortex, 1 = skip vortex (destination fills the aperture).
     * Stays at 0 when Immersive Portals is absent or the reveal entity is not in the level.
     */
    public static float irisProgress(Level level, BlockPos pos) {
        if (!isRevealing(level, pos) || !destinationReady(level, pos)) {
            irisClockStart = -1L;
            return 0.0F;
        }
        long now = level.getGameTime();
        if (irisClockStart < 0L) {
            irisClockStart = now;
        }
        int trans = ScanReveal.TRANSITION_TICKS;
        long remaining = expireAt - now;
        if (remaining <= trans) {
            return ScanReveal.ease(remaining / (float) trans);
        }
        long openAge = now - irisClockStart;
        if (openAge < trans) {
            return ScanReveal.ease(openAge / (float) trans);
        }
        return 1.0F;
    }

    private static boolean destinationReady(Level level, BlockPos pos) {
        if (!ImmersivePortalsHook.available()) {
            return false;
        }
        long tick = level.getGameTime();
        if (tick != readyTick) {
            readyTick = tick;
            readyCached = ImmersivePortalsHook.hasRevealPortal(level, pos);
        }
        return readyCached;
    }
}
