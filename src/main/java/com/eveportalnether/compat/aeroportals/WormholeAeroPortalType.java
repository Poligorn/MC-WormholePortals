package com.eveportalnether.compat.aeroportals;

import com.breakinblocks.aeroportals.api.AeroPortalType;
import com.breakinblocks.aeroportals.api.PortalDestination;
import com.eveportalnether.EvePortalNether;
import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.world.PortalEffects;
import com.eveportalnether.world.data.PortalManager;
import com.eveportalnether.world.data.PortalPair;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Registers our window blocks with AeroPortals so overlapping Sable ships are sent
 * to the linked wormhole. Uses only {@code com.breakinblocks.aeroportals.api}.
 */
public final class WormholeAeroPortalType implements AeroPortalType {
    static final String LABEL = "wormhole";
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "wormhole");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Collection<Block> matchedBlocks() {
        return List.of(ModBlocks.WORMHOLE.get());
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(ModBlocks.WORMHOLE.get()) && !state.getValue(WormholeBlock.FRAME);
    }

    @Override
    public PortalDestination resolve(ServerLevel srcLevel, ServerSubLevel sub, BlockPos hitPos) {
        PortalManager manager = PortalManager.get(srcLevel);
        PortalPair pair = manager.findPairAt(srcLevel.dimension(), hitPos);
        if (pair == null || pair.isForming() || pair.isExpired()) {
            return null;
        }

        UUID subId = uuidOf(sub);
        if (subId != null && AeroPortalsCompat.isReturnLocked(subId, pair.getId(), srcLevel.getGameTime())) {
            return null;
        }

        ServerLevel dstLevel = srcLevel.dimension() == Level.OVERWORLD
                ? srcLevel.getServer().getLevel(Level.NETHER)
                : srcLevel.getServer().getLevel(Level.OVERWORLD);
        if (dstLevel == null) {
            return null;
        }

        Vec3 srcWindow = PortalEffects.center(srcLevel, pair);
        Vec3 dstWindow = PortalEffects.center(dstLevel, pair);
        Vec3 shipCenter = worldCenter(sub, hitPos);
        Vec3 dest = dstWindow.add(shipCenter.subtract(srcWindow));
        dest = clampToWorld(dstLevel, dest);

        if (subId != null) {
            AeroPortalsCompat.rememberPrimary(subId, pair.getId());
        }
        return PortalDestination.of(dstLevel, dest, LABEL);
    }

    static UUID uuidOf(ServerSubLevel sub) {
        Object value = invokeNoArg(sub, "getUniqueId", "uuid", "getUuid", "id", "getId");
        return value instanceof UUID uuid ? uuid : null;
    }

    static Vec3 worldCenter(ServerSubLevel sub, BlockPos hitPos) {
        Object pose = invokeNoArg(sub, "logicalPose", "getLogicalPose");
        if (pose != null) {
            Object position = invokeNoArg(pose, "position", "getPosition");
            Vec3 vec = asVec3(position);
            if (vec != null) {
                return vec;
            }
        }
        Object box = invokeNoArg(sub, "boundingBox", "getBoundingBox", "worldBoundingBox");
        if (box instanceof AABB aabb) {
            return aabb.getCenter();
        }
        Vec3 fromBox = asVec3(box);
        if (fromBox != null) {
            return fromBox;
        }
        return Vec3.atCenterOf(hitPos);
    }

    private static Vec3 clampToWorld(ServerLevel level, Vec3 dest) {
        double min = level.getMinBuildHeight() + 2.0;
        double max = level.getMaxBuildHeight() - 2.0;
        if (dest.y >= min && dest.y <= max) {
            return dest;
        }
        return new Vec3(dest.x, Mth.clamp(dest.y, min, max), dest.z);
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String name : names) {
            try {
                Method method = findMethod(type, name);
                if (method != null) {
                    method.setAccessible(true);
                    return method.invoke(target);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                return cursor.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Vec3 asVec3(Object position) {
        if (position instanceof Vec3 vec) {
            return vec;
        }
        if (position == null) {
            return null;
        }
        try {
            Method x = findMethod(position.getClass(), "x");
            Method y = findMethod(position.getClass(), "y");
            Method z = findMethod(position.getClass(), "z");
            if (x == null) {
                x = findMethod(position.getClass(), "getX");
                y = findMethod(position.getClass(), "getY");
                z = findMethod(position.getClass(), "getZ");
            }
            if (x == null || y == null || z == null) {
                return null;
            }
            x.setAccessible(true);
            y.setAccessible(true);
            z.setAccessible(true);
            return new Vec3(toDouble(x.invoke(position)), toDouble(y.invoke(position)), toDouble(z.invoke(position)));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static double toDouble(Object number) {
        return number instanceof Number n ? n.doubleValue() : 0.0;
    }
}
