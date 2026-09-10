package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.crossbow.RailBeamPresentation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public record RailBeamPayload(int owner, InteractionHand hand, int color, boolean chain, List<Vec3> points) implements CustomPacketPayload {

    public static final Type<RailBeamPayload> TYPE = new Type<>(Data_Energistics.id("rail_beam"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailBeamPayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public RailBeamPayload decode(RegistryFriendlyByteBuf buffer) {
            int owner = buffer.readVarInt();
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            int color = buffer.readInt();
            boolean chain = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 1 || count > 16384) throw new IllegalArgumentException("Invalid beam point count");
            List<Vec3> points = new ObjectArrayList<>(count);
            for (int i = 0; i < count; i++) points.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
            return new RailBeamPayload(owner, hand, color, chain, List.copyOf(points));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RailBeamPayload payload) {
            buffer.writeVarInt(payload.owner);
            buffer.writeEnum(payload.hand);
            buffer.writeInt(payload.color);
            buffer.writeBoolean(payload.chain);
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

    public static void handle(RailBeamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RailBeamPresentation.accept(payload));
    }
}
