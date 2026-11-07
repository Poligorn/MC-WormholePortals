package com.eveportalnether.item;

import com.eveportalnether.particle.SpiralParticleOptions;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client-visible FX while the detector decrypts a wormhole. Vanilla
 * {@code ParticleTypes.PORTAL} uses the nether-portal particle sprite; those
 * motes were spawned at the clicked frame during scan start / repeat click and
 * sat on the custom vortex. The scan readout uses the same spiral motes as the
 * wormhole, at the window centre, not the frame block that received the click.
 */
public final class DetectorScanFx {
    private DetectorScanFx() {
    }

    public static ParticleOptions portalMote(PortalPair pair) {
        int decay = pair.isForming() ? 3 : pair.getDecay();
        return new SpiralParticleOptions(pair.getColor().tint(decay));
    }

    public static Vec3 moteOrigin(PortalPair pair, ResourceKey<Level> dimension) {
        return PortalShape.windowCenter(pair.getOrigin(dimension), pair.getAxis(), pair.getWidth(), pair.getHeight());
    }

    public static double moteRadius(PortalPair pair) {
        int inner = Math.min(Math.max(0, pair.getWidth() - 2), Math.max(0, pair.getHeight() - 2));
        return Math.max(0.6, inner * 0.45);
    }

    public static boolean alongX(PortalPair pair) {
        return pair.getAxis() == Direction.Axis.X;
    }
}
