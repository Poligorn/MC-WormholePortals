package com.eveportalnether.world;

import com.eveportalnether.Config;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.block.entity.WormholeBlockEntity;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.registry.ModSounds;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class PortalSpawner {
    private static final int LOCATION_ATTEMPTS = 12;
    private static final double LOOK_RANGE = 48.0;
    private static final double MISS_DISTANCE = 10.0;
    private static final double MIN_LOOK_DISTANCE = 4.0;
    private static final Random RANDOM = new Random();

    private PortalSpawner() {
    }

    public static int effectiveCap(int online) {
        int min = Config.MIN_ACTIVE_PORTAL_PAIRS.get();
        int max = Math.max(min, Config.MAX_ACTIVE_PORTAL_PAIRS.get());
        int byOnline = (int) Math.floor(online * Config.PORTALS_PER_PLAYER_RATIO.get());
        return Math.max(min, Math.min(max, byOnline));
    }

    public static void tick(MinecraftServer server, PortalManager manager) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (overworld == null || nether == null) {
            return;
        }
        if (overworld.getGameTime() % Config.PORTAL_CHECK_INTERVAL_TICKS.get() != 0) {
            return;
        }

        List<ServerPlayer> anchors = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && (player.level().dimension() == Level.OVERWORLD || player.level().dimension() == Level.NETHER)) {
                anchors.add(player);
            }
        }
        if (anchors.isEmpty()) {
            return;
        }
        if (manager.countNaturalPairs() >= effectiveCap(server.getPlayerCount())) {
            return;
        }
        if (RANDOM.nextDouble() >= Config.SPAWN_CHANCE_PER_CHECK.get()) {
            return;
        }

        ServerPlayer anchor = anchors.get(RANDOM.nextInt(anchors.size()));
        boolean air = RANDOM.nextInt(100) < Config.AIR_SPAWN_CHANCE_PERCENT.get();
        PortalTier tier = PortalTier.roll(overworld.getRandom());
        PortalPair pair = trySpawnNear(overworld, nether, manager, anchor.blockPosition(), air, tier);
        if (pair != null) {
            EvePortalNether.LOGGER.info("Wormhole ({}) opened near {} ({}) at {}", tier.getSerializedName(),
                    anchor.getGameProfile().getName(), air ? "air" : "ground", pair.getOverworldOrigin().toShortString());
        }
    }

    /**
     * Admin spawn: the portal stands where the player is looking, facing them.
     */
    @Nullable
    public static PortalPair forceSpawnLooking(ServerPlayer player, PortalTier tier) {
        ServerLevel source = player.serverLevel();
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (overworld == null || nether == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();

        BlockHitResult hit = source.clip(new ClipContext(eye, eye.add(look.scale(LOOK_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        BlockPos base;
        if (hit.getType() == HitResult.Type.BLOCK) {
            base = hit.getDirection() == net.minecraft.core.Direction.UP
                    ? hit.getBlockPos().above()
                    : hit.getBlockPos().relative(hit.getDirection());
        } else {
            base = BlockPos.containing(eye.add(look.scale(MISS_DISTANCE)));
        }
        Vec3 fromPlayer = Vec3.atBottomCenterOf(base).subtract(player.position());
        if (fromPlayer.x * fromPlayer.x + fromPlayer.z * fromPlayer.z < MIN_LOOK_DISTANCE * MIN_LOOK_DISTANCE) {
            base = BlockPos.containing(player.position().add(flat.scale(MIN_LOOK_DISTANCE)));
        }
        base = dropToGround(source, base);

        Direction.Axis axis = Math.abs(flat.x) > Math.abs(flat.z) ? Direction.Axis.X : Direction.Axis.Z;
        int width = PortalShape.rollWidth(tier, source.getRandom());
        int height = PortalShape.rollHeight(tier, source.getRandom());
        BlockPos localOrigin = axis == Direction.Axis.X
                ? base.offset(0, 0, -width / 2)
                : base.offset(-width / 2, 0, 0);
        BlockPos overworldOrigin;
        BlockPos netherOrigin;
        if (source.dimension() == Level.NETHER) {
            netherOrigin = localOrigin;
            overworldOrigin = findLinkedOrigin(overworld, netherOrigin, axis, width, height, true);
            if (overworldOrigin == null) {
                overworldOrigin = localOrigin;
            }
        } else {
            overworldOrigin = localOrigin;
            netherOrigin = findLinkedOrigin(nether, overworldOrigin, axis, width, height, true);
            if (netherOrigin == null) {
                netherOrigin = localOrigin;
            }
        }

        PortalManager manager = PortalManager.get(source);
        return placePair(overworld, nether, manager, overworldOrigin, netherOrigin, axis, width, height, tier, true);
    }

    @Nullable
    private static PortalPair trySpawnNear(ServerLevel overworld, ServerLevel nether, PortalManager manager,
                                          BlockPos anchor, boolean air, PortalTier tier) {
        int minDist = Config.MIN_SPAWN_DISTANCE.get();
        int maxDist = Math.max(minDist, Config.MAX_SPAWN_DISTANCE.get());
        int separation = Config.MIN_DISTANCE_BETWEEN_PORTALS.get();

        for (int attempt = 0; attempt < LOCATION_ATTEMPTS; attempt++) {
            int dist = minDist;
            if (maxDist > minDist) {
                dist += RANDOM.nextInt(maxDist - minDist + 1);
            }
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (manager.isTooClose(x, z, separation)) {
                continue;
            }
            Direction.Axis axis = RANDOM.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
            int width = PortalShape.rollWidth(tier, overworld.getRandom());
            int height = PortalShape.rollHeight(tier, overworld.getRandom());

            overworld.getChunk(new BlockPos(x, 64, z));
            BlockPos overworldOrigin = air
                    ? findAirOrigin(overworld, x, z, axis, width, height)
                    : findGroundOrigin(overworld, x, z, axis, width, height);
            if (overworldOrigin == null) {
                continue;
            }

            nether.getChunk(new BlockPos(overworldOrigin.getX(), 64, overworldOrigin.getZ()));
            BlockPos netherOrigin = findLinkedOrigin(nether, overworldOrigin, axis, width, height, false);
            if (netherOrigin == null) {
                continue;
            }

            PortalPair pair = placePair(overworld, nether, manager, overworldOrigin, netherOrigin, axis, width, height, tier, false);
            if (pair != null) {
                return pair;
            }
        }
        return null;
    }

    @Nullable
    private static PortalPair placePair(ServerLevel overworld, ServerLevel nether, PortalManager manager,
                                       BlockPos overworldOrigin, BlockPos netherOrigin, Direction.Axis axis,
                                       int width, int height, PortalTier tier, boolean force) {
        if (!(ModBlocks.WORMHOLE.get() instanceof WormholeBlock wormhole)) {
            return null;
        }
        PortalColor color = PortalColor.random(overworld.getRandom());
        if (!wormhole.placeStructure(overworld, overworldOrigin, axis, width, height, color, force)) {
            return null;
        }
        if (!wormhole.placeStructure(nether, netherOrigin, axis, width, height, color, force)) {
            wormhole.removeStructure(overworld, overworldOrigin, axis, width, height, null);
            return null;
        }

        int ticks = tier.rollTicks(overworld.getRandom());
        int jumps = tier.rollUses(overworld.getRandom());

        PortalPair pair = new PortalPair(UUID.randomUUID(), overworldOrigin, netherOrigin, axis, width, height, tier, ticks, jumps);
        pair.setColor(color);
        pair.setCode(overworld.getRandom().nextInt(1000));
        pair.setFormingTicks(Config.FORMING_TICKS.get());
        manager.addPair(pair);

        applyPairToCenter(overworld, pair.getStandPos(Level.OVERWORLD), pair);
        applyPairToCenter(nether, pair.getStandPos(Level.NETHER), pair);
        PortalManager.syncDecay(overworld, pair);
        PortalManager.syncDecay(nether, pair);
        if (pair.isForming()) {
            PortalEffects.formingStarted(overworld, pair);
            PortalEffects.formingStarted(nether, pair);
        } else {
            PortalEffects.opened(overworld, pair);
            PortalEffects.opened(nether, pair);
        }
        return pair;
    }

    private static void applyPairToCenter(ServerLevel level, BlockPos standPos, PortalPair pair) {
        BlockEntity be = level.getBlockEntity(standPos);
        if (be instanceof WormholeBlockEntity wormhole) {
            wormhole.setCenter(true);
            wormhole.setTicksRemaining(pair.getTicksRemaining());
            wormhole.setJumpsRemaining(pair.getJumpsRemaining());
        }
    }

    private static BlockPos dropToGround(ServerLevel level, BlockPos pos) {
        BlockPos current = pos;
        for (int i = 0; i < 32 && current.getY() > level.getMinBuildHeight() + 1; i++) {
            if (!PortalShape.isReplaceable(level.getBlockState(current.below()))) {
                return current;
            }
            current = current.below();
        }
        return pos;
    }

    @Nullable
    private static BlockPos findGroundOrigin(ServerLevel level, int x, int z, Direction.Axis axis, int width, int height) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int originY = surface;
        if (originY < level.getMinBuildHeight() + 1 || originY + height >= level.getMaxBuildHeight()) {
            return null;
        }
        BlockPos origin = axis == Direction.Axis.X
                ? new BlockPos(x, originY, z - width / 2)
                : new BlockPos(x - width / 2, originY, z);
        if (!PortalShape.hasSolidFooting(level, origin, axis, width)) {
            return null;
        }
        if (!PortalShape.isVolumeReplaceable(level, origin, axis, width, height)) {
            return null;
        }
        return origin;
    }

    @Nullable
    private static BlockPos findAirOrigin(ServerLevel level, int x, int z, Direction.Axis axis, int width, int height) {
        int minY = level.getMinBuildHeight() + 16;
        int maxY = level.getMaxBuildHeight() - height - 8;
        if (maxY <= minY) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            int y = minY + RANDOM.nextInt(maxY - minY);
            BlockPos origin = axis == Direction.Axis.X
                    ? new BlockPos(x, y, z - width / 2)
                    : new BlockPos(x - width / 2, y, z);
            if (PortalShape.isVolumeReplaceable(level, origin, axis, width, height)) {
                return origin;
            }
        }
        return null;
    }


    /**
     * Training portal: a small unstable window in the player's dimension only.
     * No Nether link, no Nether chunk loads, no block destruction.
     */
    @Nullable
    public static PortalPair spawnDemo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        PortalManager manager = PortalManager.get(level);

        PortalPair existing = manager.findDemoOwnedBy(player.getUUID());
        if (existing != null) {
            manager.collapse(level.getServer(), existing);
        }

        Direction.Axis axis;
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
        axis = Math.abs(flat.x) > Math.abs(flat.z) ? Direction.Axis.X : Direction.Axis.Z;

        int width = PortalShape.minWidth(PortalTier.UNSTABLE);
        int height = PortalShape.minHeight(PortalTier.UNSTABLE);
        BlockPos origin = findDemoOrigin(level, player, flat, axis, width, height);
        if (origin == null) {
            return null;
        }

        return placeDemo(level, manager, origin, axis, width, height, player.getUUID());
    }

    @Nullable
    private static BlockPos findDemoOrigin(ServerLevel level, ServerPlayer player, Vec3 flat, Direction.Axis axis,
                                          int width, int height) {
        int[] distances = {5, 6, 7, 4, 8, 9, 3, 10};
        int[] laterals = {0, 1, -1, 2, -2};
        int[] yOffsets = {0, 1, -1, 2, -2};
        AABB playerBox = player.getBoundingBox().inflate(0.2);
        BlockPos feet = player.blockPosition();

        for (int dist : distances) {
            for (int lateral : laterals) {
                for (int dy : yOffsets) {
                    Vec3 side = new Vec3(-flat.z, 0, flat.x);
                    Vec3 center = player.position().add(flat.scale(dist)).add(side.scale(lateral));
                    BlockPos base = BlockPos.containing(center.x, feet.getY() + dy, center.z);
                    if (base.getY() < level.getMinBuildHeight() + 1
                            || base.getY() + height >= level.getMaxBuildHeight()) {
                        continue;
                    }
                    BlockPos origin = axis == Direction.Axis.X
                            ? base.offset(0, 0, -width / 2)
                            : base.offset(-width / 2, 0, 0);
                    if (!PortalShape.isVolumeReplaceable(level, origin, axis, width, height)) {
                        continue;
                    }
                    AABB volume = demoVolume(origin, axis, width, height);
                    if (volume.intersects(playerBox)) {
                        continue;
                    }
                    return origin;
                }
            }
        }
        return null;
    }

    private static AABB demoVolume(BlockPos origin, Direction.Axis axis, int width, int height) {
        BlockPos far = PortalShape.offset(origin, width - 1, height - 1, axis);
        int minX = Math.min(origin.getX(), far.getX());
        int minY = Math.min(origin.getY(), far.getY());
        int minZ = Math.min(origin.getZ(), far.getZ());
        int maxX = Math.max(origin.getX(), far.getX()) + 1;
        int maxY = Math.max(origin.getY(), far.getY()) + 1;
        int maxZ = Math.max(origin.getZ(), far.getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    private static PortalPair placeDemo(ServerLevel level, PortalManager manager, BlockPos origin,
                                       Direction.Axis axis, int width, int height, UUID ownerId) {
        if (!(ModBlocks.WORMHOLE.get() instanceof WormholeBlock wormhole)) {
            return null;
        }
        PortalColor color = PortalColor.random(level.getRandom());
        if (!wormhole.placeStructure(level, origin, axis, width, height, color, false)) {
            return null;
        }

        BlockPos overworldOrigin = level.dimension() == Level.NETHER ? BlockPos.ZERO : origin;
        BlockPos netherOrigin = level.dimension() == Level.NETHER ? origin : BlockPos.ZERO;
        PortalPair pair = new PortalPair(UUID.randomUUID(), overworldOrigin, netherOrigin, axis, width, height,
                PortalTier.UNSTABLE, PortalPair.DEMO_LIFETIME_TICKS, 8);
        pair.setColor(color);
        pair.setCode(level.getRandom().nextInt(1000));
        pair.setFormingTicks(Math.min(40, Config.FORMING_TICKS.get()));
        pair.setDemo(true);
        pair.setOwnerId(ownerId);
        pair.setDemoDimension(level.dimension());
        manager.addPair(pair);

        applyPairToCenter(level, pair.getStandPos(level.dimension()), pair);
        PortalManager.syncDecay(level, pair);
        if (pair.isForming()) {
            PortalEffects.formingStarted(level, pair);
        } else {
            PortalEffects.opened(level, pair);
        }
        return pair;
    }

    @Nullable
    private static BlockPos findLinkedOrigin(ServerLevel nether, BlockPos overworldOrigin, Direction.Axis axis,
                                            int width, int height, boolean force) {
        int startY = Math.max(nether.getMinBuildHeight() + 2, Math.min(nether.getMaxBuildHeight() - height - 2, overworldOrigin.getY()));

        BlockPos candidate = new BlockPos(overworldOrigin.getX(), startY, overworldOrigin.getZ());
        if (isValid(nether, candidate, axis, width, height, false) || (force && isValid(nether, candidate, axis, width, height, true))) {
            return candidate;
        }

        for (int dy = 1; dy < 96; dy++) {
            BlockPos up = new BlockPos(overworldOrigin.getX(), startY + dy, overworldOrigin.getZ());
            BlockPos down = new BlockPos(overworldOrigin.getX(), startY - dy, overworldOrigin.getZ());
            if (isValid(nether, up, axis, width, height, false)) {
                return up;
            }
            if (isValid(nether, down, axis, width, height, false)) {
                return down;
            }
        }

        if (force) {
            return candidate;
        }
        return findAirOrigin(nether, overworldOrigin.getX(), overworldOrigin.getZ(), axis, width, height);
    }

    private static boolean isValid(ServerLevel level, BlockPos origin, Direction.Axis axis, int width, int height, boolean force) {
        if (origin.getY() < level.getMinBuildHeight() + 1 || origin.getY() + height >= level.getMaxBuildHeight()) {
            return false;
        }
        return force || PortalShape.isVolumeReplaceable(level, origin, axis, width, height);
    }
}
