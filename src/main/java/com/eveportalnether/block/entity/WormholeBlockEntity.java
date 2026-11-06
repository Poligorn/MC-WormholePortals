package com.eveportalnether.block.entity;

import com.eveportalnether.registry.ModBlockEntities;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class WormholeBlockEntity extends BlockEntity {
    /**
     * Client-only ambient hook. Assigned from {@code EvePortalNetherClient}; dedicated
     * servers never invoke {@link #clientTick} so they never load the sound class.
     */
    public static Consumer<WormholeBlockEntity> clientAmbient = entity -> {
    };

    private int ticksRemaining = 6000;
    private int jumpsRemaining = 10;
    private boolean isCenter = false;

    public WormholeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORMHOLE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setCenter(boolean center) {
        this.isCenter = center;
        setChanged();
    }

    public boolean isCenter() {
        return isCenter;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
        setChanged();
    }

    public int getJumpsRemaining() {
        return jumpsRemaining;
    }

    public void setJumpsRemaining(int jumpsRemaining) {
        this.jumpsRemaining = jumpsRemaining;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("TicksRemaining", this.ticksRemaining);
        tag.putInt("JumpsRemaining", this.jumpsRemaining);
        tag.putBoolean("IsCenter", this.isCenter);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("TicksRemaining")) {
            this.ticksRemaining = tag.getInt("TicksRemaining");
        }
        if (tag.contains("JumpsRemaining")) {
            this.jumpsRemaining = tag.getInt("JumpsRemaining");
        }
        if (tag.contains("IsCenter")) {
            this.isCenter = tag.getBoolean("IsCenter");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WormholeBlockEntity blockEntity) {
        clientAmbient.accept(blockEntity);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WormholeBlockEntity blockEntity) {
        if (level.isClientSide || !blockEntity.isCenter || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PortalManager manager = PortalManager.get(serverLevel);
        PortalPair pair = manager.findPairAt(serverLevel.dimension(), pos);
        if (pair == null) {
            return;
        }

        blockEntity.ticksRemaining = pair.getTicksRemaining();
        blockEntity.jumpsRemaining = pair.getJumpsRemaining();
        if (serverLevel.getGameTime() % 20 == 0) {
            blockEntity.setChanged();
        }
    }
}
