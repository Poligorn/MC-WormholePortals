package com.eveportalnether.client;

import com.eveportalnether.compat.immersiveportals.ImmersivePortalsClient;
import com.eveportalnether.compat.immersiveportals.ImmersivePortalsHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Client-only Immersive Portals lookups that do not load {@code qouteall} until
 * the mod is actually present. {@link ScanRevealClient} must not mention
 * {@link ImmersivePortalsClient} in its own constant pool: that class imports
 * {@code ClientWorldLoader} / {@code PortalRendering}, which are missing when
 * Immersive Portals is not installed.
 */
public final class ImmersivePortalsClientAccess {
    private ImmersivePortalsClientAccess() {
    }

    public static boolean isNestedPortalRender() {
        return ImmersivePortalsHook.available() && Holder.isNestedPortalRender();
    }

    public static boolean hasRevealPortal(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return ImmersivePortalsHook.available() && Holder.hasRevealPortal(level, origin, axis, width, height);
    }

    public static boolean isDestinationViewReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return ImmersivePortalsHook.available() && Holder.isDestinationViewReady(level, origin, axis, width, height);
    }

    public static boolean isDestinationCenterReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
        return ImmersivePortalsHook.available() && Holder.isDestinationCenterReady(level, origin, axis, width, height);
    }

    /**
     * Loaded only when {@link ImmersivePortalsHook#available()} is true, so a
     * client without Immersive Portals never resolves qouteall types.
     */
    private static final class Holder {
        private Holder() {
        }

        static boolean isNestedPortalRender() {
            return ImmersivePortalsClient.isNestedPortalRender();
        }

        static boolean hasRevealPortal(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
            return ImmersivePortalsClient.hasRevealPortal(level, origin, axis, width, height);
        }

        static boolean isDestinationViewReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
            return ImmersivePortalsClient.isDestinationViewReady(level, origin, axis, width, height);
        }

        static boolean isDestinationCenterReady(Level level, BlockPos origin, Direction.Axis axis, int width, int height) {
            return ImmersivePortalsClient.isDestinationCenterReady(level, origin, axis, width, height);
        }
    }
}
