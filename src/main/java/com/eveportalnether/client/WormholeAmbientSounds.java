package com.eveportalnether.client;

import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.world.WormholeAmbient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * One looping nether-portal drone per live window. Stops when the host tile is gone
 * or the player leaves the dimension, so dying/collapse cannot leak the sound.
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
        if (minecraft.level != level || minecraft.getSoundManager() == null) {
            return;
        }
        Drone existing = PLAYING.get(pos);
        if (existing != null && !existing.isStopped()) {
            return;
        }
        Drone drone = new Drone(level, pos);
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

    private static boolean isWindow(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.WORMHOLE.get()) && !state.getValue(WormholeBlock.FRAME);
    }

    private static final class Drone extends AbstractTickableSoundInstance {
        private final Level level;
        private final BlockPos pos;

        private Drone(Level level, BlockPos pos) {
            super(SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, RandomSource.create());
            this.level = level;
            this.pos = pos.immutable();
            this.looping = true;
            this.delay = 0;
            this.volume = WormholeAmbient.LOOP_VOLUME;
            this.pitch = 0.85F;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != level) {
                stop();
                return;
            }
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block != ModBlocks.WORMHOLE.get() || state.getValue(WormholeBlock.FRAME)) {
                stop();
            }
        }
    }
}
