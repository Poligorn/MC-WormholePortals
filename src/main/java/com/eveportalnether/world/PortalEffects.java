package com.eveportalnether.world;

import com.eveportalnether.Config;
import com.eveportalnether.advancement.WormholeEvents;
import com.eveportalnether.network.ModNetwork;
import com.eveportalnether.network.TransitPayload;
import com.eveportalnether.particle.SpiralParticleOptions;
import net.minecraft.core.Direction;
import com.eveportalnether.registry.ModItems;
import com.eveportalnether.registry.ModSounds;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import com.eveportalnether.world.data.PortalPair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Server-side audiovisual choreography for the portal lifecycle.
 */
public final class PortalEffects {
    private static final double OPEN_SHAKE_RADIUS = 48.0;

    private PortalEffects() {
    }

    public static Vec3 center(ServerLevel level, PortalPair pair) {
        BlockPos origin = pair.getOrigin(level.dimension());
        Vec3 corner = Vec3.atLowerCornerOf(origin);
        double across = pair.getWidth() / 2.0;
        double up = pair.getHeight() / 2.0;
        return switch (pair.getAxis()) {
            case X -> corner.add(0.5, up, across);
            default -> corner.add(across, up, 0.5);
        };
    }

    /**
     * Ambient swirl of an open portal: a few coloured motes spiral into the window. Denser when fresh,
     * sparse when worn, frantic when dying.
     */
    public static void spiral(ServerLevel level, PortalPair pair) {
        if (!isLoaded(level, pair)) {
            return;
        }
        int decay = pair.getDecay();
        int count = switch (decay) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            default -> 4;
        };
        RandomSource random = level.getRandom();
        Vec3 center = center(level, pair);
        double radius = Math.min(pair.getWidth(), pair.getHeight() * 0.75) / 2.0 - 0.2;
        int rgb = pair.getColor().tint(decay);
        for (int i = 0; i < count; i++) {
            spiralMote(level, pair, center, radius * (0.8 + random.nextDouble() * 0.25), random.nextDouble() * Math.PI * 2, rgb);
        }
    }

    private static void spiralMote(ServerLevel level, PortalPair pair, Vec3 center, double radius, double angle, int rgb) {
        double planeSpansX = pair.getAxis() == Direction.Axis.Z ? 1.0 : 0.0;
        level.sendParticles(new SpiralParticleOptions(rgb), center.x, center.y, center.z, 0, radius, angle, planeSpansX, 1.0);
    }

    private static boolean isLoaded(ServerLevel level, PortalPair pair) {
        return level.hasChunkAt(pair.getOrigin(level.dimension()));
    }

    private static DustParticleOptions colorDust(PortalColor color, float size) {
        return new DustParticleOptions(new Vector3f(color.red(), color.green(), color.blue()), size);
    }

    public static void shake(ServerLevel level, Vec3 center, double radius, float strength, int duration) {
        if (!Config.SCREEN_SHAKE.get() || radius <= 0) {
            return;
        }
        float multiplier = Config.SCREEN_SHAKE_STRENGTH.get().floatValue();
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(center);
            if (distance > radius) {
                continue;
            }
            float falloff = (float) (1.0 - distance / radius);
            ModNetwork.sendShake(player, strength * multiplier * (0.25F + 0.75F * falloff), duration);
        }
    }

    /**
     * Particles converging on {@code target}: portal particles fly from target + offset towards target.
     */
    private static void converge(ServerLevel level, ParticleOptions type, Vec3 target, int count, double radius, RandomSource random) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(radius * (0.6 + random.nextDouble() * 0.4));
            level.sendParticles(type, target.x, target.y, target.z, 0, dir.x, dir.y, dir.z, 1.0);
        }
    }

    /**
     * Low drone for the portal's whole lifetime. {@link Config#HUM_RANGE} is the hearing distance:
     * the sound engine maps 16 blocks of range to a volume of 1, and the actual loudness stays capped.
     */
    public static void hum(ServerLevel level, PortalPair pair) {
        int range = Config.HUM_RANGE.get();
        if (range <= 0) {
            return;
        }
        float volume = Math.max(range, 16) / 16.0F;
        Vec3 center = center(level, pair);
        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_HUM.get(), SoundSource.BLOCKS, volume, 1.0F);
    }

    public static void formingStarted(ServerLevel level, PortalPair pair) {
        if (!isLoaded(level, pair)) {
            return;
        }
        Vec3 center = center(level, pair);
        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_CHARGE.get(), SoundSource.BLOCKS, 3.0F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3.0F, 0.5F);
        shake(level, center, 32.0, 0.4F, 30);
    }

    public static void formingTick(ServerLevel level, PortalPair pair, float progress) {
        if (!isLoaded(level, pair)) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 center = center(level, pair);
        double radius = 2.5 + pair.getWidth() * 0.6;

        converge(level, ParticleTypes.PORTAL, center, 6 + (int) (progress * 14), radius, random);
        double angle = level.getGameTime() * 0.35;
        for (int arm = 0; arm < 3; arm++) {
            double a = angle + arm * (Math.PI * 2 / 3);
            spiralMote(level, pair, center, radius * (1.0 - progress * 0.5), a, pair.getColor().rgb());
        }
        if (random.nextFloat() < 0.15F + progress * 0.5F) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 4,
                    pair.getWidth() * 0.35, pair.getHeight() * 0.35, pair.getWidth() * 0.35, 0.3);
        }

        int formingLeft = pair.getFormingTicks();
        if (formingLeft % 10 == 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS,
                    1.5F + progress * 1.5F, 0.5F + progress * 1.5F);
        }
        if (formingLeft % 20 == 0 && progress > 0.4F) {
            shake(level, center, 24.0, 0.2F + progress * 0.5F, 20);
        }
    }

    public static void opened(ServerLevel level, PortalPair pair) {
        if (!isLoaded(level, pair)) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 center = center(level, pair);
        BlockPos stand = pair.getStandPos(level.dimension());

        if (Config.OPENING_LIGHTNING.get()) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(stand));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }

        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_OPEN.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.6F);

        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 120, 0.3, 0.3, 0.3, 0.5);
        level.sendParticles(colorDust(pair.getColor(), 2.5F), center.x, center.y, center.z, 80,
                pair.getWidth() * 0.5, pair.getHeight() * 0.5, pair.getWidth() * 0.5, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 200, 0.5, 0.8, 0.5, 0.8);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(ParticleTypes.CLOUD, center.x, stand.getY() + 0.1, center.z, 0, Math.cos(a), 0.02, Math.sin(a), 0.6 + random.nextDouble() * 0.2);
        }

        shake(level, center, OPEN_SHAKE_RADIUS, 2.2F, 30);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(center) <= OPEN_SHAKE_RADIUS) {
                WormholeEvents.fire(player, WormholeEvents.WITNESS_OPENING);
            }
        }
        announce(level, center);
    }

    private static void announce(ServerLevel level, Vec3 center) {
        int radius = Config.ANNOUNCE_RADIUS.get();
        if (radius <= 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(center);
            if (distance <= OPEN_SHAKE_RADIUS || distance > radius) {
                continue;
            }
            float closeness = (float) (1.0 - distance / radius);
            player.playNotifySound(ModSounds.PORTAL_RUMBLE.get(), SoundSource.AMBIENT, 0.4F + closeness * 0.6F, 0.5F + closeness * 0.2F);
            player.displayClientMessage(Component.translatable("eveportalnether.event.rumble").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
            WormholeEvents.fire(player, WormholeEvents.RUMBLE);
            if (Config.SCREEN_SHAKE.get()) {
                ModNetwork.sendShake(player, (0.2F + closeness * 0.4F) * Config.SCREEN_SHAKE_STRENGTH.get().floatValue(), 40);
            }
        }
    }

    public static void travelled(ServerLevel from, BlockPos fromPos, ServerLevel to, Vec3 destination, ServerPlayer player, PortalPair pair) {
        PortalColor color = pair.getColor();
        ModNetwork.sendTransit(player, new TransitPayload(color, pair.getSignature()));
        from.playSound(null, fromPos, ModSounds.PORTAL_TRAVEL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, fromPos.getX() + 0.5, fromPos.getY() + 1.0, fromPos.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.3);
        from.sendParticles(colorDust(color, 1.5F), fromPos.getX() + 0.5, fromPos.getY() + 1.0, fromPos.getZ() + 0.5, 30, 0.5, 1.0, 0.5, 0.0);

        to.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        to.playSound(null, destination.x, destination.y, destination.z, ModSounds.PORTAL_TRAVEL.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
        to.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1.0, destination.z, 80, 0.6, 1.0, 0.6, 0.6);
        to.sendParticles(colorDust(color, 1.5F), destination.x, destination.y + 1.0, destination.z, 30, 0.6, 1.0, 0.6, 0.0);
        if (Config.SCREEN_SHAKE.get()) {
            ModNetwork.sendShake(player, 1.2F * Config.SCREEN_SHAKE_STRENGTH.get().floatValue(), 18);
        }
    }

    public static void warningPulse(ServerLevel level, PortalPair pair, float progress) {
        if (!isLoaded(level, pair)) {
            return;
        }
        Vec3 center = center(level, pair);
        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_WARNING.get(), SoundSource.BLOCKS, 2.0F, 0.8F + progress * 0.8F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F + progress);
        level.sendParticles(colorDust(pair.getColor(), 1.8F), center.x, center.y, center.z, 30,
                pair.getWidth() * 0.4, pair.getHeight() * 0.4, pair.getWidth() * 0.4, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 16,
                pair.getWidth() * 0.4, pair.getHeight() * 0.4, pair.getWidth() * 0.4, 0.3);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 6, 0.6, 1.0, 0.6, 0.02);
        converge(level, ParticleTypes.PORTAL, center, 20, 4.0, level.getRandom());
        shake(level, center, 24.0, 0.4F + progress * 1.2F, 12);
    }

    public static void imploded(ServerLevel level, PortalPair pair) {
        if (!isLoaded(level, pair)) {
            return;
        }
        Vec3 center = center(level, pair);
        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_IMPLODE.get(), SoundSource.BLOCKS, 3.0F, 0.7F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0F, 0.6F);

        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 250, 1.0, 1.5, 1.0, 0.8);
        level.sendParticles(colorDust(pair.getColor(), 3.0F), center.x, center.y, center.z, 100, 2.0, 2.0, 2.0, 0.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 60, 0.3, 0.3, 0.3, 0.35);

        shake(level, center, 40.0, 3.0F, 35);
    }

    public static void lastEntryPulse(ServerLevel level, PortalPair pair) {
        Vec3 center = center(level, pair);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.5F);
        level.sendParticles(colorDust(pair.getColor(), 1.4F), center.x, center.y, center.z, 12,
                pair.getWidth() * 0.35, pair.getHeight() * 0.35, pair.getWidth() * 0.35, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 6,
                pair.getWidth() * 0.35, pair.getHeight() * 0.35, pair.getWidth() * 0.35, 0.2);
    }

    public static void maybeDropShard(ServerLevel level, PortalPair pair) {
        if (!isLoaded(level, pair) || level.getRandom().nextDouble() >= Config.SHARD_DROP_CHANCE.get()) {
            return;
        }
        Vec3 center = center(level, pair);
        ItemEntity shard = new ItemEntity(level, center.x, center.y, center.z, new ItemStack(ModItems.PORTAL_SHARD.get()));
        shard.setDeltaMovement(0, 0.25, 0);
        shard.setGlowingTag(true);
        shard.setDefaultPickUpDelay();
        level.addFreshEntity(shard);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 30, 0.2, 0.2, 0.2, 0.15);
    }

    public static void closedQuietly(ServerLevel level, PortalPair pair) {
        if (!isLoaded(level, pair)) {
            return;
        }
        Vec3 center = center(level, pair);
        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_CLOSE.get(), SoundSource.BLOCKS, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 80, 0.6, 1.0, 0.6, 0.2);
    }
}
