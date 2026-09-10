package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailFiring;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Button transitions only; elapsed charge and final damage are calculated by the server. */
public record CannonChargePayload(InteractionHand hand, Action action, Vec3 muzzleOffset, Vec3 direction) implements CustomPacketPayload {

    public enum Action {
        START,
        RELEASE,
        CANCEL
    }

    public static final Type<CannonChargePayload> TYPE = new Type<>(Data_Energistics.id("cannon_charge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CannonChargePayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public CannonChargePayload decode(RegistryFriendlyByteBuf buffer) {
            return new CannonChargePayload(buffer.readEnum(InteractionHand.class), buffer.readEnum(Action.class),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CannonChargePayload value) {
            buffer.writeEnum(value.hand);
            buffer.writeEnum(value.action);
            buffer.writeDouble(value.muzzleOffset.x);
            buffer.writeDouble(value.muzzleOffset.y);
            buffer.writeDouble(value.muzzleOffset.z);
            buffer.writeDouble(value.direction.x);
            buffer.writeDouble(value.direction.y);
            buffer.writeDouble(value.direction.z);
        }
    };

    @Override
    public Type<CannonChargePayload> type() {
        return TYPE;
    }

    public static void handle(CannonChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            ItemStack stack = player.getItemInHand(payload.hand);
            if (!(stack.getItem() instanceof MatterConvergingCrossbowItem item)) return;
            if (payload.action == Action.CANCEL || player.hasEffect(DEMobEffects.RADIX_LOSS)) {
                RailFiring.stop(player, stack);
                stack.remove(DEDataComponents.CANNON_CHARGE.get());
                return;
            }
            if (payload.action == Action.START) item.beginCannonCharge(player, payload.hand, stack);
            else item.releaseCannonCharge(player, payload.hand, stack, payload.muzzleOffset, payload.direction);
        });
    }
}
