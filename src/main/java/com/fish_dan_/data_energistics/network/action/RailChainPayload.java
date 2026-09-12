package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.crossbow.RailImpactPresentation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Short FE arcs emitted once at projectile impact, never a held beam. */
public record RailChainPayload(List<Vec3> points) implements CustomPacketPayload {

    public static final Type<RailChainPayload> TYPE = new Type<>(Data_Energistics.id("star_shard_impact_chain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailChainPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public RailChainPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 2 || count > 4096) throw new IllegalArgumentException("Invalid impact chain count");
            List<Vec3> points = new ObjectArrayList<>(count);
            for (int i = 0; i < count; i++) points.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
            return new RailChainPayload(List.copyOf(points));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RailChainPayload payload) {
            buffer.writeVarInt(payload.points.size());
            for (Vec3 point : payload.points) {
                buffer.writeDouble(point.x);
                buffer.writeDouble(point.y);
                buffer.writeDouble(point.z);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RailChainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RailImpactPresentation.accept(payload));
    }
}
