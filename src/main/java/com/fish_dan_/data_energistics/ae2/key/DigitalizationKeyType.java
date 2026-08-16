package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.stream.Stream;

/**
 * AE key type shared by Data Flow, Echo and Celestial Energy so AE2 exposes one Digitalization visibility toggle.
 */
public final class DigitalizationKeyType extends AEKeyType {

    static final String RESOURCE_FIELD = "resource";
    private static final int DATA_FLOW_PACKET_ID = 0;
    private static final int ECHO_PACKET_ID = 1;
    private static final int CELESTIAL_ENERGY_PACKET_ID = 2;

    public static final DigitalizationKeyType TYPE = new DigitalizationKeyType();

    private static final MapCodec<DigitalizationKey> CURRENT_CODEC = ResourceLocation.CODEC
            .fieldOf(RESOURCE_FIELD)
            .flatXmap(DigitalizationKeyType::resolveResource, key -> DataResult.success(key.getId()));
    private static final MapCodec<DigitalizationKey> CODEC = new MapCodec<>() {

        @Override
        public <T> DataResult<DigitalizationKey> decode(DynamicOps<T> ops, MapLike<T> input) {
            if (input.get(RESOURCE_FIELD) != null) {
                return CURRENT_CODEC.decode(ops, input);
            }

            T legacyType = input.get(AEKey.TYPE_FIELD);
            if (legacyType == null) {
                return DataResult.error(() -> "Digitalization key is missing both resource and legacy type fields");
            }
            return ResourceLocation.CODEC.parse(ops, legacyType).flatMap(DigitalizationKeyType::resolveResource);
        }

        @Override
        public <T> RecordBuilder<T> encode(DigitalizationKey input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return CURRENT_CODEC.encode(input, ops, prefix);
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return CURRENT_CODEC.keys(ops);
        }
    };

    private DigitalizationKeyType() {
        super(
                Data_Energistics.id("digitalization"),
                DigitalizationKey.class,
                Component.translatable("key_type." + Data_Energistics.MODID + ".digitalization"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        int resourceId = buffer.readVarInt();
        return switch (resourceId) {
            case DATA_FLOW_PACKET_ID -> DataFlowKey.of();
            case ECHO_PACKET_ID -> EchoKey.of();
            case CELESTIAL_ENERGY_PACKET_ID -> CelestialEnergyKey.of();
            default -> {
                Data_Energistics.LOGGER.error("Received unknown Digitalization key packet id {}", resourceId);
                yield null;
            }
        };
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    static void writeToPacket(RegistryFriendlyByteBuf buffer, DigitalizationKey key) {
        if (key instanceof DataFlowKey) {
            buffer.writeVarInt(DATA_FLOW_PACKET_ID);
        } else if (key instanceof EchoKey) {
            buffer.writeVarInt(ECHO_PACKET_ID);
        } else if (key instanceof CelestialEnergyKey) {
            buffer.writeVarInt(CELESTIAL_ENERGY_PACKET_ID);
        } else {
            throw new IllegalArgumentException("Unsupported Digitalization key: " + key.getClass().getName());
        }
    }

    private static DataResult<DigitalizationKey> resolveResource(ResourceLocation resourceId) {
        if (resourceId.equals(DataFlowKey.ID)) {
            return DataResult.success(DataFlowKey.of());
        }
        if (resourceId.equals(EchoKey.ID)) {
            return DataResult.success(EchoKey.of());
        }
        if (resourceId.equals(CelestialEnergyKey.ID)) {
            return DataResult.success(CelestialEnergyKey.of());
        }
        return DataResult.error(() -> "Unknown Digitalization resource id: " + resourceId);
    }
}
