package com.fish_dan_.data_energistics.common.trinity.host;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigInteger;

/**
 * Immutable storage snapshot assembled from a Trinity Data Core host's authoritative contents and capacity profile.
 */
public record TrinityDataCoreStorageStatus(int typeCount,
                                           int typeCapacity,
                                           BigInteger itemAmount,
                                           BigInteger fluidAmount,
                                           BigInteger otherKeyAmount,
                                           BigInteger amountCapacity,
                                           boolean unlimited) {

    private static final Codec<BigInteger> BIG_INTEGER_CODEC = Codec.STRING.xmap(
            BigInteger::new,
            BigInteger::toString);

    public static final Codec<TrinityDataCoreStorageStatus> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.fieldOf("type_count").forGetter(TrinityDataCoreStorageStatus::typeCount),
                    Codec.INT.fieldOf("type_capacity").forGetter(TrinityDataCoreStorageStatus::typeCapacity),
                    BIG_INTEGER_CODEC.fieldOf("item_amount").forGetter(TrinityDataCoreStorageStatus::itemAmount),
                    BIG_INTEGER_CODEC.fieldOf("fluid_amount").forGetter(TrinityDataCoreStorageStatus::fluidAmount),
                    BIG_INTEGER_CODEC.fieldOf("other_key_amount").forGetter(TrinityDataCoreStorageStatus::otherKeyAmount),
                    BIG_INTEGER_CODEC.fieldOf("amount_capacity").forGetter(TrinityDataCoreStorageStatus::amountCapacity),
                    Codec.BOOL.fieldOf("unlimited").forGetter(TrinityDataCoreStorageStatus::unlimited))
            .apply(instance, TrinityDataCoreStorageStatus::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityDataCoreStorageStatus> STREAM_CODEC = StreamCodec.of(TrinityDataCoreStorageStatus::encode, TrinityDataCoreStorageStatus::decode);

    public static final TrinityDataCoreStorageStatus EMPTY = new TrinityDataCoreStorageStatus(
            0,
            0,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            false);

    public TrinityDataCoreStorageStatus {
        if (typeCount < 0) {
            throw new IllegalArgumentException("Storage type count must not be negative");
        }
        if (typeCapacity < 0) {
            throw new IllegalArgumentException("Storage type capacity must not be negative");
        }
        requireNonNegative(itemAmount, "Stored item amount");
        requireNonNegative(fluidAmount, "Stored fluid amount");
        requireNonNegative(otherKeyAmount, "Stored other-key amount");
        requireNonNegative(amountCapacity, "Storage amount capacity");
    }

    /**
     * Returns the exact amount stored across all AE key categories.
     */
    public BigInteger totalAmount() {
        return this.itemAmount.add(this.fluidAmount).add(this.otherKeyAmount);
    }

    private static void requireNonNegative(BigInteger value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TrinityDataCoreStorageStatus value) {
        buffer.writeVarInt(value.typeCount);
        buffer.writeVarInt(value.typeCapacity);
        writeBigInteger(buffer, value.itemAmount);
        writeBigInteger(buffer, value.fluidAmount);
        writeBigInteger(buffer, value.otherKeyAmount);
        writeBigInteger(buffer, value.amountCapacity);
        buffer.writeBoolean(value.unlimited);
    }

    private static TrinityDataCoreStorageStatus decode(RegistryFriendlyByteBuf buffer) {
        return new TrinityDataCoreStorageStatus(
                buffer.readVarInt(),
                buffer.readVarInt(),
                readBigInteger(buffer),
                readBigInteger(buffer),
                readBigInteger(buffer),
                readBigInteger(buffer),
                buffer.readBoolean());
    }

    private static void writeBigInteger(RegistryFriendlyByteBuf buffer, BigInteger value) {
        buffer.writeUtf(value.toString());
    }

    private static BigInteger readBigInteger(RegistryFriendlyByteBuf buffer) {
        return new BigInteger(buffer.readUtf());
    }
}
