package com.fish_dan_.data_energistics.item.powered.cannon;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Transient server-authored charge; synchronized with the held stack but never persisted in a save. */
public record CannonCharge(long startedAt, int duration, MatterConvergingCrossbowMode mode,
                           InteractionHand hand, UUID owner, ResourceLocation dimension) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CannonCharge> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public CannonCharge decode(RegistryFriendlyByteBuf buffer) {
            return new CannonCharge(buffer.readVarLong(), buffer.readVarInt(),
                    buffer.readEnum(MatterConvergingCrossbowMode.class), buffer.readEnum(InteractionHand.class),
                    buffer.readUUID(), buffer.readResourceLocation());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CannonCharge value) {
            buffer.writeVarLong(value.startedAt);
            buffer.writeVarInt(value.duration);
            buffer.writeEnum(value.mode);
            buffer.writeEnum(value.hand);
            buffer.writeUUID(value.owner);
            buffer.writeResourceLocation(value.dimension);
        }
    };

    public CannonCharge {
        if (duration <= 0 || mode == MatterConvergingCrossbowMode.CROSSBOW) {
            throw new IllegalArgumentException("Invalid cannon charge duration or mode");
        }
    }

    public float progress(long gameTime) {
        return CannonBallistics.charge(gameTime - this.startedAt, this.duration);
    }

    public boolean belongsTo(LivingEntity entity, InteractionHand hand, MatterConvergingCrossbowMode mode) {
        return entity.isAlive() && this.owner.equals(entity.getUUID()) && this.hand == hand && this.mode == mode && this.dimension.equals(entity.level().dimension().location());
    }
}
