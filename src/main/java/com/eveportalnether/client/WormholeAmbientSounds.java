package com.eveportalnether.client;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.registry.ModSounds;
import com.eveportalnether.world.WormholeAmbient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * One looping nether-portal drone per live window. Stops when the host tile is gone
 * or the player leaves the dimension, so dying/collapse cannot leak the sound.
 * Distance uses {@link WormholeAmbient#loopGain} from the window centre.
 * OpenAL LINEAR is off ({@link SoundInstance.Attenuation#NONE}) so the engine
 * cannot fade a second time. {@code relative=false} keeps world panning.
 * {@code minecraft.level} is not used as the dimension check: Immersive Portals
 * can swap it to the dest world while nested-rendering.
 */
public final class WormholeAmbientSounds {
    private static final Map<BlockPos, Drone> PLAYING = new HashMap<>();

    private WormholeAmbientSounds() {
    }

    public static void tick(WormholeBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = entity.getBlockState();
        if (!state.is(ModBlocks.WORMHOLE.get()) || state.getValue(WormholeBlock.FRAME)) {
            return;
        }
        Direction.Axis axis = state.getValue(WormholeBlock.AXIS);
        BlockPos pos = entity.getBlockPos();
        boolean below = isWindow(level, pos.below());
        boolean minus = isWindow(level, axis == Direction.Axis.X ? pos.offset(0, 0, -1) : pos.offset(-1, 0, 0));
        if (!WormholeAmbient.isLoopHost(below, minus)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null || ImmersivePortalsClientAccess.isNestedPortalRender()) {
            return;
        }
        if (!viewerInHostDimension(minecraft, level)) {
            return;
        }
        Drone existing = PLAYING.get(pos);
        if (existing != null && !existing.isStopped()) {
            return;
        }
        Drone drone = new Drone(level, pos, axis);
        PLAYING.put(pos.immutable(), drone);
        minecraft.getSoundManager().play(drone);
    }

    public static void sweepStopped() {
        Iterator<Map.Entry<BlockPos, Drone>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isStopped()) {
                iterator.remove();
            }
        }
    }

    public static void stopAll() {
        for (Drone drone : PLAYING.values()) {
            drone.halt();
        }
        PLAYING.clear();
    }

    private static boolean viewerInHostDimension(Minecraft minecraft, Level host) {
        Player player = minecraft.player;
        if (player == null || player.level() == null) {
            return false;
        }
        return WormholeAmbient.sameDimension(host.dimension(), player.level().dimension());
    }

    private static boolean isWindow(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.WORMHOLE.get()) && !state.getValue(WormholeBlock.FRAME);
    }

    private static final class Drone extends AbstractTickableSoundInstance {
        private final Level level;
        private final BlockPos pos;
        private final Direction.Axis axis;
        private int lastLoggedBucket = Integer.MIN_VALUE;

        private Drone(Level level, BlockPos pos, Direction.Axis axis) {
            super(ModSounds.PORTAL_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
            this.level = level;
            this.pos = pos.immutable();
            this.axis = axis;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = 0.85F;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.NONE;
            placeAtWindowCentre();
        }

        void halt() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (ImmersivePortalsClientAccess.isNestedPortalRender()) {
                return;
            }
            if (!viewerInHostDimension(minecraft, level)) {
                stop();
                return;
            }
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block != ModBlocks.WORMHOLE.get() || state.getValue(WormholeBlock.FRAME)) {
                stop();
                return;
            }
            placeAtWindowCentre();
            Player player = minecraft.player;
            double dx = player.getX() - x;
            double dy = player.getEyeY() - y;
            double dz = player.getZ() - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int hear = WormholeAmbient.hearDistance(Config.AMBIENT_HEAR_DISTANCE.get());
            this.volume = WormholeAmbient.LOOP_VOLUME * WormholeAmbient.loopGain(distance, hear);
            logIfNeeded(distance, hear);
        }

        private void placeAtWindowCentre() {
            int across = 1;
            int up = 1;
            while (isWindow(level, axis == Direction.Axis.X ? pos.offset(0, 0, across) : pos.offset(across, 0, 0))) {
                across++;
            }
            while (isWindow(level, pos.above(up))) {
                up++;
            }
            double midAcross = (across - 1) * 0.5;
            double midUp = (up - 1) * 0.5;
            this.x = pos.getX() + 0.5 + (axis == Direction.Axis.X ? 0.0 : midAcross);
            this.y = pos.getY() + 0.5 + midUp;
            this.z = pos.getZ() + 0.5 + (axis == Direction.Axis.X ? midAcross : 0.0);
        }

        private void logIfNeeded(double distance, int hear) {
            int bucket = distance < 16 ? 0 : distance < 64 ? 1 : distance < 100 ? 2 : distance < hear ? 3 : 4;
            if (bucket == lastLoggedBucket) {
                return;
            }
            lastLoggedBucket = bucket;
            EvePortalNether.LOGGER.debug(
                    "portal.hum host={} center=({},{},{}) dist={} hear={} vol={} linear={} loadedChunks≤{}",
                    pos.toShortString(),
                    String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z),
                    String.format("%.1f", distance), hear, String.format("%.3f", volume),
                    WormholeAmbient.usesOpenAlLinear(),
                    WormholeAmbient.loadedChunkBlocks(Minecraft.getInstance().options.renderDistance().get()));
        }
    }
}
