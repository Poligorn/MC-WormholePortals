package com.eveportalnether.world.data;

import com.eveportalnether.Config;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.registry.ModSounds;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.advancement.WormholeEvents;
import com.eveportalnether.world.PortalColor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import com.eveportalnether.world.PortalEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.eveportalnether.world.PortalShape;
import com.eveportalnether.world.PortalTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PortalManager extends SavedData {
    private static final double LAST_ENTRY_WARN_RADIUS = 12.0;
    public static final ResourceKey<DamageType> IMPLOSION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "implosion"));

    private final List<PortalPair> pairs = new ArrayList<>();

    public static PortalManager load(CompoundTag tag, HolderLookup.Provider registries) {
        PortalManager manager = new PortalManager();
        ListTag list = tag.getList("Pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            manager.pairs.add(PortalPair.load(list.getCompound(i)));
        }

        // Backward compatibility with the old per-side record list.
        if (manager.pairs.isEmpty() && tag.contains("Portals", Tag.TAG_LIST)) {
            ListTag legacy = tag.getList("Portals", Tag.TAG_COMPOUND);
            for (int i = 0; i + 1 < legacy.size(); i += 2) {
                PortalRecord a = PortalRecord.load(legacy.getCompound(i));
                PortalRecord b = PortalRecord.load(legacy.getCompound(i + 1));
                PortalRecord overworld = a.getDimension() == Level.OVERWORLD ? a : b;
                PortalRecord nether = a.getDimension() == Level.NETHER ? a : b;
                manager.pairs.add(new PortalPair(
                        UUID.randomUUID(),
                        overworld.getPos(),
                        nether.getPos(),
                        Direction.Axis.Z,
                        4,
                        5,
                        PortalTier.STANDARD,
                        Math.min(overworld.getTicksRemaining(), nether.getTicksRemaining()),
                        Math.min(overworld.getJumpsRemaining(), nether.getJumpsRemaining())
                ));
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PortalPair pair : pairs) {
            list.add(pair.save());
        }
        tag.put("Pairs", list);
        return tag;
    }

    public List<PortalPair> getPairs() {
        return pairs;
    }

    public void addPair(PortalPair pair) {
        pairs.add(pair);
        setDirty();
    }

    @Nullable
    public PortalPair findPairAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (PortalPair pair : pairs) {
            if (pair.contains(dimension, pos)) {
                return pair;
            }
        }
        return null;
    }

    @Nullable
    public PortalPair findNearest(ResourceKey<Level> dimension, BlockPos pos) {
        PortalPair best = null;
        double bestDist = Double.MAX_VALUE;
        for (PortalPair pair : pairs) {
            double dist = pair.getStandPos(dimension).distSqr(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = pair;
            }
        }
        return best;
    }

    public boolean isTooClose(int x, int z, int minDistance) {
        long minSq = (long) minDistance * minDistance;
        for (PortalPair pair : pairs) {
            BlockPos other = pair.getOverworldOrigin();
            long dx = other.getX() - x;
            long dz = other.getZ() - z;
            if (dx * dx + dz * dz < minSq) {
                return true;
            }
        }
        return false;
    }

    public void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        List<PortalPair> expired = new ArrayList<>();
        for (PortalPair pair : pairs) {
            if (pair.isForming()) {
                tickForming(server, pair);
                continue;
            }
            pair.setTicksRemaining(pair.getTicksRemaining() - 1);
            if (pair.isExpired()) {
                expired.add(pair);
            } else if (pair.isWarning() && time % 10 == 0) {
                playWarning(server, pair);
            } else if (pair.isLastEntry() && time % 40 == 0) {
                warnLastEntry(server, pair);
            }
            if (!pair.isExpired() && time % 3 == 0) {
                for (ServerLevel level : sides(server)) {
                    PortalEffects.spiral(level, pair);
                }
            }
            if (!pair.isExpired() && time % 80 == 0) {
                for (ServerLevel level : sides(server)) {
                    PortalEffects.hum(level, pair);
                }
            }
        }
        if (!expired.isEmpty()) {
            setDirty();
        }
        for (PortalPair pair : expired) {
            collapse(server, pair, null, null, true, null);
        }

        if (time % 20 == 0) {
            syncDecay(server);
            discardGhosts(server);
        }
    }

    public boolean isVisibleToRadar(ServerLevel level, PortalPair pair) {
        BlockPos origin = pair.getOrigin(level.dimension());
        if (!level.hasChunkAt(origin)) {
            return true;
        }
        return hasWormholeBlocks(level, pair);
    }

    public void discardGhosts(MinecraftServer server) {
        List<PortalPair> ghosts = new ArrayList<>();
        for (PortalPair pair : pairs) {
            if (isGhostSide(server.getLevel(Level.OVERWORLD), pair) || isGhostSide(server.getLevel(Level.NETHER), pair)) {
                ghosts.add(pair);
            }
        }
        for (PortalPair ghost : ghosts) {
            collapse(server, ghost);
        }
    }

    private static boolean isGhostSide(@Nullable ServerLevel level, PortalPair pair) {
        if (level == null) {
            return false;
        }
        BlockPos origin = pair.getOrigin(level.dimension());
        return level.hasChunkAt(origin) && !hasWormholeBlocks(level, pair);
    }

    private static boolean hasWormholeBlocks(ServerLevel level, PortalPair pair) {
        BlockPos origin = pair.getOrigin(level.dimension());
        for (int across = 0; across < pair.getWidth(); across++) {
            for (int up = 0; up < pair.getHeight(); up++) {
                BlockPos pos = PortalShape.offset(origin, across, up, pair.getAxis());
                if (level.getBlockState(pos).getBlock() instanceof WormholeBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private void syncDecay(MinecraftServer server) {
        for (PortalPair pair : pairs) {
            for (ServerLevel level : sides(server)) {
                syncDecay(level, pair);
            }
        }
    }

    public static void syncDecay(ServerLevel level, PortalPair pair) {
        BlockPos stand = pair.getStandPos(level.dimension());
        if (!level.hasChunkAt(stand)) {
            return;
        }
        int decay = pair.getDecay();
        PortalColor color = pair.getColor();
        BlockState standState = level.getBlockState(stand);
        if (!(standState.getBlock() instanceof WormholeBlock)
                || (standState.getValue(WormholeBlock.DECAY) == decay && standState.getValue(WormholeBlock.COLOR) == color)) {
            return;
        }
        BlockPos origin = pair.getOrigin(level.dimension());
        for (int across = 0; across < pair.getWidth(); across++) {
            for (int up = 0; up < pair.getHeight(); up++) {
                BlockPos pos = PortalShape.offset(origin, across, up, pair.getAxis());
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof WormholeBlock) {
                    BlockState updated = state.setValue(WormholeBlock.COLOR, color);
                    if (!state.getValue(WormholeBlock.FRAME)) {
                        updated = updated.setValue(WormholeBlock.DECAY, decay);
                    }
                    if (updated != state) {
                        level.setBlock(pos, updated, 2);
                    }
                }
            }
        }
    }

    private static void warnLastEntry(MinecraftServer server, PortalPair pair) {
        for (ServerLevel level : sides(server)) {
            if (!level.hasChunkAt(pair.getOrigin(level.dimension()))) {
                continue;
            }
            Vec3 center = PortalEffects.center(level, pair);
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceToSqr(center) <= LAST_ENTRY_WARN_RADIUS * LAST_ENTRY_WARN_RADIUS) {
                    player.displayClientMessage(Component.translatable("eveportalnether.portal.last_entry")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
            PortalEffects.lastEntryPulse(level, pair);
        }
    }

    private void tickForming(MinecraftServer server, PortalPair pair) {
        int total = Math.max(1, Config.FORMING_TICKS.get());
        pair.setFormingTicks(pair.getFormingTicks() - 1);
        float progress = Math.min(1.0F, 1.0F - pair.getFormingTicks() / (float) total);
        for (ServerLevel level : sides(server)) {
            if (pair.isForming()) {
                PortalEffects.formingTick(level, pair, progress);
            } else {
                syncDecay(level, pair);
                PortalEffects.opened(level, pair);
            }
        }
        if (!pair.isForming() || pair.getFormingTicks() % 20 == 0) {
            setDirty();
        }
    }

    private static void playWarning(MinecraftServer server, PortalPair pair) {
        int warning = Math.max(1, Config.WARNING_DURATION_TICKS.get());
        float progress = 1.0F - pair.getTicksRemaining() / (float) warning;
        for (ServerLevel level : sides(server)) {
            PortalEffects.warningPulse(level, pair, progress);
        }
    }

    private static void implode(ServerLevel level, PortalPair pair, @Nullable Entity exempt) {
        BlockPos stand = pair.getStandPos(level.dimension());
        if (!level.hasChunkAt(stand)) {
            return;
        }
        Vec3 center = PortalEffects.center(level, pair);
        double radius = Config.IMPLOSION_RADIUS.get();
        float damage = (float) (Config.IMPLOSION_DAMAGE.get() * 2.0);

        PortalEffects.imploded(level, pair);

        if (radius <= 0) {
            return;
        }
        AABB area = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        for (Entity entity : level.getEntities((Entity) null, area, e -> e.isAlive() && e != exempt)) {
            double distance = entity.position().distanceTo(center);
            if (distance > radius) {
                continue;
            }
            double falloff = distance / radius;
            if (entity instanceof LivingEntity living && damage > 0) {
                DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(IMPLOSION));
                boolean hit = living.hurt(source, damage * (float) (1.2 - 0.4 * falloff));
                if (hit && living.isAlive() && living instanceof ServerPlayer player) {
                    WormholeEvents.fire(player, WormholeEvents.SURVIVE_IMPLOSION);
                }
            }
            Vec3 away = entity.position().subtract(center);
            Vec3 push = (away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away.normalize())
                    .scale(1.4 * (1.0 - falloff) + 0.3)
                    .add(0, 0.4, 0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            entity.hurtMarked = true;
        }
    }

    public void collapse(MinecraftServer server, PortalPair pair) {
        collapse(server, pair, null, null, false, null);
    }

    public void collapse(MinecraftServer server, PortalPair pair, @Nullable ServerLevel skipLevel, @Nullable BlockPos skipPos) {
        collapse(server, pair, skipLevel, skipPos, false, null);
    }

    public void collapse(MinecraftServer server, PortalPair pair, @Nullable ServerLevel skipLevel, @Nullable BlockPos skipPos,
                         boolean implode, @Nullable Entity exempt) {
        if (!pairs.remove(pair)) {
            return;
        }
        setDirty();

        for (ServerLevel level : sides(server)) {
            BlockPos origin = pair.getOrigin(level.dimension());
            if (implode) {
                implode(level, pair, exempt);
                PortalEffects.maybeDropShard(level, pair);
            } else {
                PortalEffects.closedQuietly(level, pair);
            }
            removeStructure(level, origin, pair, level == skipLevel ? skipPos : null);
        }
    }

    private static List<ServerLevel> sides(MinecraftServer server) {
        List<ServerLevel> levels = new ArrayList<>(2);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (overworld != null) {
            levels.add(overworld);
        }
        if (nether != null) {
            levels.add(nether);
        }
        return levels;
    }

    private static void removeStructure(ServerLevel level, BlockPos origin, PortalPair pair, @Nullable BlockPos skip) {
        level.getChunk(origin);
        if (ModBlocks.WORMHOLE.get() instanceof WormholeBlock wormholeBlock) {
            wormholeBlock.removeStructure(level, origin, pair.getAxis(), pair.getWidth(), pair.getHeight(), skip);
        }
    }

    public static PortalManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PortalManager::new, PortalManager::load, net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                "eveportalnether_portals"
        );
    }
}
