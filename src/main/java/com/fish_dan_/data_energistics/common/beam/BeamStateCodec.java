package com.fish_dan_.data_energistics.common.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;

import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** Bounded block/part stream decoding; mutable network data is normalized once at this boundary. */
final class BeamStateCodec {

    private BeamStateCodec() {}

    static void write(RegistryFriendlyByteBuf data, boolean hidden, int cards, int connections, int bindings,
                      ObjectList<BeamVisual> visuals) {
        data.writeBoolean(hidden);
        data.writeByte(cards);
        data.writeVarInt(connections);
        data.writeVarInt(bindings);
        data.writeVarInt(visuals.size());
        for (BeamVisual visual : visuals) {
            data.writeBlockPos(visual.target());
            data.writeByte(visual.targetFacing().get3DDataValue());
            data.writeInt(visual.color());
        }
    }

    static Snapshot read(RegistryFriendlyByteBuf data, BlockPos source, BeamDeviceKind kind) {
        boolean hidden = data.readBoolean();
        int cards = data.readUnsignedByte();
        int connections = data.readVarInt();
        int bindings = data.readVarInt();
        int size = data.readVarInt();
        if (cards > BeamDeviceKind.UPGRADE_SLOTS || connections < 0 || bindings < 0 || size < 0 ||
                size > connections || size > data.readableBytes() / 13 ||
                (kind != BeamDeviceKind.OMNI && (connections > 1 || bindings != 0))) {
            throw new DecoderException("Invalid beam state header");
        }
        var seen = new LongOpenHashSet(size);
        ObjectList<BeamVisual> visuals = new ObjectArrayList<>(size);
        int range = kind.range(cards);
        for (int i = 0; i < size; i++) {
            BlockPos target = data.readBlockPos();
            int facing = data.readUnsignedByte();
            int color = data.readInt();
            double distance = source.distSqr(target);
            if (facing >= Direction.values().length || color < 0 || color > 0xFFFFFF || distance <= 0 ||
                    distance > (double) range * range || !seen.add(target.asLong())) {
                throw new DecoderException("Invalid beam visual endpoint");
            }
            visuals.add(new BeamVisual(target, Direction.from3DDataValue(facing), color));
        }
        return new Snapshot(hidden, cards, connections, bindings, ObjectLists.unmodifiable(visuals));
    }

    record Snapshot(boolean hidden, int cards, int connections, int bindings, ObjectList<BeamVisual> visuals) {}
}
