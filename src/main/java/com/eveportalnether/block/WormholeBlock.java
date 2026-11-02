package com.eveportalnether.block;

import com.eveportalnether.Config;
import com.eveportalnether.advancement.WormholeEvents;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.compat.aeroportals.AeroPortalsHook;
import com.eveportalnether.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import com.eveportalnether.registry.ModBlockEntities;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.registry.ModSounds;
import com.eveportalnether.world.PortalEffects;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalColor;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class WormholeBlock extends BaseEntityBlock {
    public static final BooleanProperty FRAME = BooleanProperty.create("frame");
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<PortalColor> COLOR = EnumProperty.create("color", PortalColor.class);
    public static final IntegerProperty DECAY = IntegerProperty.create("decay", 0, 3);
    public static final MapCodec<WormholeBlock> CODEC = simpleCodec(WormholeBlock::new);
    private static final String LAST_PAIR_KEY = "EvePortalLastPair";
    private static final ThreadLocal<Boolean> REMOVING_STRUCTURE = ThreadLocal.withInitial(() -> false);

    public WormholeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FRAME, false)
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(COLOR, PortalColor.BLUE)
                .setValue(DECAY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FRAME, AXIS, COLOR, DECAY);
    }

    public static int lightLevel(BlockState state) {
        if (state.getValue(FRAME)) {
            return 0;
        }
        return switch (state.getValue(DECAY)) {
            case 0 -> 11;
            case 1 -> 9;
            case 2 -> 6;
            default -> 4;
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!REMOVING_STRUCTURE.get() && !state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            PortalManager manager = PortalManager.get(serverLevel);
            PortalPair pair = manager.findPairAt(serverLevel.dimension(), pos);
            if (pair != null) {
                manager.collapse(serverLevel.getServer(), pair, serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    public boolean placeStructure(Level level, BlockPos origin, Direction.Axis axis, int width, int height, PortalColor color, boolean force) {
        if (!force && !PortalShape.isVolumeReplaceable(level, origin, axis, width, height)) {
            return false;
        }

        BlockPos stand = PortalShape.standPos(origin, axis, width, height);
        BlockState base = defaultBlockState().setValue(AXIS, axis).setValue(COLOR, color);
        for (int across = 0; across < width; across++) {
            for (int up = 0; up < height; up++) {
                BlockPos pos = PortalShape.offset(origin, across, up, axis);
                boolean frame = PortalShape.isFrame(across, up, width, height);
                level.setBlock(pos, base.setValue(FRAME, frame), 3);
                if (pos.equals(stand)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof WormholeBlockEntity wormhole) {
                        wormhole.setCenter(true);
                    }
                }
            }
        }
        PortalShape.carveLandingPocket(level, origin, axis, width, height);
        return true;
    }

    public void removeStructure(Level level, BlockPos origin, Direction.Axis axis, int width, int height, @Nullable BlockPos skip) {
        REMOVING_STRUCTURE.set(true);
        try {
            for (int across = 0; across < width; across++) {
                for (int up = 0; up < height; up++) {
                    BlockPos pos = PortalShape.offset(origin, across, up, axis);
                    if (skip != null && skip.equals(pos)) {
                        continue;
                    }
                    if (level.getBlockState(pos).is(this)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        } finally {
            REMOVING_STRUCTURE.set(false);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FRAME, true)
                .setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FRAME)) {
            return Shapes.block();
        }
        // Inner window must not eat the pick ray, otherwise survival players can only target the
        // unbreakable portal and cannot mine out of a sealed landing spot. Creative players still
        // need an outline to remove stray window blocks.
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player && player.isCreative()) {
            return Shapes.block();
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FRAME) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FRAME) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FRAME)) {
            return;
        }
        if (shouldSkipAmbient(level, state, pos, random)) {
            return;
        }
        int decay = state.getValue(DECAY);
        PortalColor color = state.getValue(COLOR);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (random.nextInt(80) == 0) {
            float pitch = random.nextFloat() * 0.4F + 0.8F - decay * 0.15F;
            level.playLocalSound(x, y, z, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, pitch, false);
        }
        if (decay <= 1 && random.nextInt(160) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.5F + random.nextFloat() * 0.1F, false);
        }
        if (decay >= 2 && random.nextInt(decay == 3 ? 6 : 30) == 0) {
            level.playLocalSound(x, y, z, ModSounds.PORTAL_CRACKLE.get(), SoundSource.BLOCKS, 0.6F, 0.8F + random.nextFloat() * 0.4F, false);
        }

        int portalParticles = switch (decay) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            default -> 4;
        };
        double speed = decay == 3 ? 1.2 : 0.5;
        for (int i = 0; i < portalParticles; i++) {
            level.addParticle(ParticleTypes.PORTAL,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * speed,
                    (random.nextDouble() - 0.5) * speed,
                    (random.nextDouble() - 0.5) * speed);
        }

        if (random.nextInt(decay == 0 ? 3 : 6) == 0) {
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(color.red(), color.green(), color.blue()), 1.0F);
            level.addParticle(dust, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
        if (decay >= 2 && random.nextInt(decay == 3 ? 2 : 8) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        }
        if (decay == 3 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);
        }
    }

    private static boolean isReturnLocked(Player player, PortalPair pair) {
        if (!player.hasEffect(ModEffects.SPATIAL_TRACE)) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        return data.hasUUID(LAST_PAIR_KEY) && data.getUUID(LAST_PAIR_KEY).equals(pair.getId());
    }

    private static void applyReturnLock(Player player, PortalPair pair) {
        int duration = Config.RETURN_LOCK_TICKS.get();
        if (duration <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        player.getPersistentData().putUUID(LAST_PAIR_KEY, pair.getId());
        player.addEffect(new MobEffectInstance(ModEffects.SPATIAL_TRACE, duration, 0, false, false, true));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(FRAME) ? null : new WormholeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (state.getValue(FRAME)) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.WORMHOLE_BLOCK_ENTITY.get(), WormholeBlockEntity::tick);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(FRAME) || level.isClientSide || !(entity instanceof Player player) || !(level instanceof ServerLevel serverLevel)) {
            super.entityInside(state, level, pos, entity);
            return;
        }

        if (AeroPortalsHook.shouldSkipPlayerTravel(player)) {
            super.entityInside(state, level, pos, entity);
            return;
        }

        if (player.isOnPortalCooldown()) {
            player.setPortalCooldown();
            super.entityInside(state, level, pos, entity);
            return;
        }

        PortalManager manager = PortalManager.get(serverLevel);
        PortalPair pair = manager.findPairAt(serverLevel.dimension(), pos);
        if (pair == null || pair.isForming()) {
            super.entityInside(state, level, pos, entity);
            return;
        }
        if (isReturnLocked(player, pair)) {
            if (serverLevel.getGameTime() % 20 == 0) {
                MobEffectInstance trace = player.getEffect(ModEffects.SPATIAL_TRACE);
                int seconds = trace == null ? 0 : Math.max(1, trace.getDuration() / 20);
                player.displayClientMessage(Component.translatable("eveportalnether.portal.return_locked", seconds)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                serverLevel.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
                if (player instanceof ServerPlayer serverPlayer) {
                    WormholeEvents.fire(serverPlayer, WormholeEvents.RETURN_LOCKED);
                }
            }
            super.entityInside(state, level, pos, entity);
            return;
        }

        ServerLevel targetLevel = serverLevel.dimension() == Level.OVERWORLD
                ? serverLevel.getServer().getLevel(Level.NETHER)
                : serverLevel.getServer().getLevel(Level.OVERWORLD);
        if (targetLevel == null) {
            super.entityInside(state, level, pos, entity);
            return;
        }

        pair.consumeJump();
        manager.setDirty();
        player.setPortalCooldown();

        BlockPos stand = pair.getStandPos(targetLevel.dimension());
        targetLevel.getChunk(stand);
        PortalShape.carveLandingPocket(targetLevel, pair.getOrigin(targetLevel.dimension()), pair.getAxis(), pair.getWidth(), pair.getHeight());
        Vec3 destination = Vec3.atBottomCenterOf(stand);

        if (player instanceof ServerPlayer serverPlayer) {
            PortalEffects.travelled(serverLevel, pos, targetLevel, destination, serverPlayer, pair);
        }

        entity.changeDimension(new DimensionTransition(
                targetLevel,
                destination,
                Vec3.ZERO,
                entity.getYRot(),
                entity.getXRot(),
                DimensionTransition.DO_NOTHING
        ));

        applyReturnLock(player, pair);

        if (player instanceof ServerPlayer serverPlayer) {
            WormholeEvents.fire(serverPlayer, WormholeEvents.TRAVEL);
            WormholeEvents.fire(serverPlayer, WormholeEvents.travelColor(pair.getColor()));
            if (pair.isExpired()) {
                WormholeEvents.fire(serverPlayer, WormholeEvents.LAST_ENTRY);
            }
        }

        if (pair.isExpired()) {
            manager.collapse(serverLevel.getServer(), pair, null, null, true, entity);
        } else {
            PortalManager.syncDecay(serverLevel, pair);
            PortalManager.syncDecay(targetLevel, pair);
        }

        super.entityInside(state, level, pos, entity);
    }

    /**
     * Keep particle and sound budget roughly constant as the window grows.
     * A 4×5 portal is unchanged; a 50×50 window would otherwise emit thousands
     * of portal particles and ambient sounds every tick.
     */
    private static boolean shouldSkipAmbient(Level level, BlockState state, BlockPos pos, RandomSource random) {
        Direction.Axis axis = state.getValue(AXIS);
        int skip = 1;
        if (isWindow(level, pos.above(4)) && isWindow(level, across(pos, axis, 4))) {
            skip = 8;
            if (isWindow(level, pos.above(12)) && isWindow(level, across(pos, axis, 12))) {
                skip = 32;
            }
        }
        if (skip == 1) {
            return false;
        }
        int digest = Math.floorMod(pos.getX() * 127 + pos.getY() * 311 + pos.getZ() * 431 + (int) level.getGameTime(), skip);
        return digest != 0 && random.nextInt(4) != 0;
    }

    private static BlockPos across(BlockPos pos, Direction.Axis axis, int steps) {
        return axis == Direction.Axis.X ? pos.offset(0, 0, steps) : pos.offset(steps, 0, 0);
    }

    private static boolean isWindow(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.WORMHOLE.get()) && !state.getValue(FRAME);
    }
}
