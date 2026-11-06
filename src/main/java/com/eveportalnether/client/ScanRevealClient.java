package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.compat.immersiveportals.ImmersivePortalsHook;
import com.eveportalnether.network.ScanRevealPayload;
import com.eveportalnether.scan.ScanRevealIris;
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
    private static long readyTick = Long.MIN_VALUE;
    private static boolean readyCached;
    private static boolean waitLogged;
    private static final ScanRevealIris IRIS = new ScanRevealIris();

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
        IRIS.reset();
        readyTick = Long.MIN_VALUE;
        readyCached = false;
        waitLogged = false;
        EvePortalNether.LOGGER.info("Scan reveal granted for {} ticks at {} / {}",
                payload.durationTicks(), payload.overworldOrigin().toShortString(),
                payload.netherOrigin().toShortString());
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (now >= expireAt) {
            clear();
            return;
        }
        if (!waitLogged && IRIS.clockStart() < 0L && now + active.durationTicks() - expireAt >= 20L) {
            waitLogged = true;
            EvePortalNether.LOGGER.warn(
                    "Scan reveal is active but no tagged Immersive Portals entity is visible yet; vortex stays opaque");
        }
    }

    public static void clear() {
        active = null;
        expireAt = -1L;
        readyTick = Long.MIN_VALUE;
        readyCached = false;
        waitLogged = false;
        IRIS.reset();
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
        return PortalShape.contains(windowOrigin(level, payload), payload.axis(), payload.width(), payload.height(), pos);
    }

    /**
     * 0 = fully opaque vortex, 1 = skip vortex (destination fills the aperture).
     * Stays at 0 when Immersive Portals is absent or the reveal entity is not in the level.
     */
    public static float irisProgress(Level level, BlockPos pos) {
        boolean revealing = isRevealing(level, pos);
        boolean ready = revealing && destinationReady(level);
        if (level == null) {
            return 0.0F;
        }
        return IRIS.sample(revealing, ready, level.getGameTime(), expireAt);
    }

    private static boolean destinationReady(Level level) {
        if (!ImmersivePortalsHook.available()) {
            return false;
        }
        ScanRevealPayload payload = active;
        if (payload == null) {
            return false;
        }
        long tick = level.getGameTime();
        if (tick != readyTick) {
            readyTick = tick;
            readyCached = ImmersivePortalsHook.hasRevealPortal(
                    level, windowOrigin(level, payload), payload.axis(), payload.width(), payload.height());
        }
        return readyCached;
    }

    private static BlockPos windowOrigin(Level level, ScanRevealPayload payload) {
        return level.dimension() == Level.NETHER ? payload.netherOrigin() : payload.overworldOrigin();
    }
}
