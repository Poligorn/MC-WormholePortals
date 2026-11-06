package com.eveportalnether.network;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.world.data.PortalPair;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Clientbound: the scanning player may iris the vortex interior for {@code durationTicks}.
 * {@code durationTicks <= 0} clears the local reveal. Destinations are not sent.
 */
public record ScanRevealPayload(UUID pairId, BlockPos overworldOrigin, BlockPos netherOrigin,
                                Direction.Axis axis, int width, int height, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<ScanRevealPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "scan_reveal"));
    public static final StreamCodec<ByteBuf, ScanRevealPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                FriendlyByteBuf out = new FriendlyByteBuf(buf);
                out.writeUUID(payload.pairId());
                out.writeBlockPos(payload.overworldOrigin());
                out.writeBlockPos(payload.netherOrigin());
                out.writeEnum(payload.axis());
                out.writeVarInt(payload.width());
                out.writeVarInt(payload.height());
                out.writeVarInt(payload.durationTicks());
            },
            buf -> {
                FriendlyByteBuf in = new FriendlyByteBuf(buf);
                return new ScanRevealPayload(in.readUUID(), in.readBlockPos(), in.readBlockPos(),
                        in.readEnum(Direction.Axis.class), in.readVarInt(), in.readVarInt(), in.readVarInt());
            });

    public static ScanRevealPayload of(PortalPair pair, int durationTicks) {
        return new ScanRevealPayload(pair.getId(), pair.getOverworldOrigin(), pair.getNetherOrigin(),
                pair.getAxis(), pair.getWidth(), pair.getHeight(), durationTicks);
    }

    public static ScanRevealPayload clear() {
        return new ScanRevealPayload(new UUID(0L, 0L), BlockPos.ZERO, BlockPos.ZERO, Direction.Axis.Z, 0, 0, 0);
    }

    public boolean isClear() {
        return durationTicks <= 0 || width <= 0 || height <= 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
