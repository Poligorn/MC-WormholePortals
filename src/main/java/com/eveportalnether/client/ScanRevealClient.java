package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.compat.immersiveportals.ImmersivePortalsHook;
import com.eveportalnether.network.ScanRevealPayload;
import com.eveportalnether.scan.ScanRevealIris;
import com.eveportalnether.world.PortalShape;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Local 60s through-view gate. Only the scanning client receives {@link ScanRevealPayload},
 * so other players keep drawing the vortex. The iris never opens unless a tagged IP portal
 * is present and dest sections are compiled, so a missing or unmeshed destination cannot
 * expose the local world.
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
                    "Scan reveal is active but the destination through-view is not ready yet; vortex stays opaque");
        }
    }

    /**
     * Immersive Portals composites the dest after block entities. Redraw the vortex with
     * the iris hole on {@code AFTER_LEVEL} so the 2s ease is actually visible, and so an
     * unready dest cannot flash through.
     */
    public static void onAfterLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (ImmersivePortalsHook.isNestedPortalRender()) {
            return;
        }
        ScanRevealPayload payload = active;
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || payload.isClear() || minecraft.level == null) {
            return;
        }
        Level level = minecraft.level;
        BlockPos origin = windowOrigin(level, payload);
        if (!isRevealing(level, origin)) {
            return;
        }
        if (!ImmersivePortalsHook.hasRevealPortal(level, origin, payload.axis(), payload.width(), payload.height())) {
            return;
        }
        // Dest sections compile during IP's pass, which is after BER. Recheck before overlay.
        readyTick = Long.MIN_VALUE;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float iris = irisProgress(level, origin, partial);
        WormholePulseRenderer.renderRevealOverlay(event, level, origin, payload.axis(), payload.width(),
                payload.height(), iris);
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
     * Stays at 0 until a tagged portal exists <em>and</em> dest sections are compiled.
     */
    public static float irisProgress(Level level, BlockPos pos) {
        return irisProgress(level, pos, 0.0F);
    }

    public static float irisProgress(Level level, BlockPos pos, float partialTick) {
        boolean revealing = isRevealing(level, pos);
        boolean ready = revealing && destinationReady(level);
        if (level == null) {
            return 0.0F;
        }
        return IRIS.sample(revealing, ready, level.getGameTime() + partialTick, expireAt);
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
            readyCached = ImmersivePortalsHook.isDestinationViewReady(
                    level, windowOrigin(level, payload), payload.axis(), payload.width(), payload.height());
        }
        return readyCached;
    }

    private static BlockPos windowOrigin(Level level, ScanRevealPayload payload) {
        return level.dimension() == Level.NETHER ? payload.netherOrigin() : payload.overworldOrigin();
    }
}
