package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.CropDataCarrierItemData;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotItemData;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotMemoryCardData;
import com.fish_dan_.data_energistics.item.MobDataCarrierItemData;
import com.fish_dan_.data_energistics.item.OreDataCarrierItemData;
import com.fish_dan_.data_energistics.item.UniversalTerminalItemData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mojang.serialization.Codec;

public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(
            Registries.DATA_COMPONENT_TYPE,
            Data_Energistics.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DigitalStorageDepotItemData>> DIGITAL_STORAGE_DEPOT = DATA_COMPONENT_TYPES.register(
            "digital_storage_depot",
            () -> DataComponentType.<DigitalStorageDepotItemData>builder()
                    .persistent(DigitalStorageDepotItemData.CODEC)
                    .networkSynchronized(DigitalStorageDepotItemData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DigitalStorageDepotMemoryCardData>> DIGITAL_STORAGE_DEPOT_OUTPUT_SETTINGS = DATA_COMPONENT_TYPES.register(
            "digital_storage_depot_output_settings",
            () -> DataComponentType.<DigitalStorageDepotMemoryCardData>builder()
                    .persistent(DigitalStorageDepotMemoryCardData.CODEC)
                    .networkSynchronized(DigitalStorageDepotMemoryCardData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> MACHINE_MEMORY_CARD_SETTINGS = DATA_COMPONENT_TYPES.register(
            "machine_memory_card_settings",
            () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> ADAPTIVE_PATTERN_PROVIDER_SETTINGS = DATA_COMPONENT_TYPES.register(
            "adaptive_pattern_provider_settings",
            () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REDSTONE_TUNING_MODE = DATA_COMPONENT_TYPES.register(
            "redstone_tuning_mode",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OreDataCarrierItemData>> ORE_DATA_CARRIER = DATA_COMPONENT_TYPES.register(
            "ore_data_carrier",
            () -> DataComponentType.<OreDataCarrierItemData>builder()
                    .persistent(OreDataCarrierItemData.CODEC)
                    .networkSynchronized(OreDataCarrierItemData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MobDataCarrierItemData>> MOB_DATA_CARRIER = DATA_COMPONENT_TYPES.register(
            "mob_data_carrier",
            () -> DataComponentType.<MobDataCarrierItemData>builder()
                    .persistent(MobDataCarrierItemData.CODEC)
                    .networkSynchronized(MobDataCarrierItemData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CropDataCarrierItemData>> CROP_DATA_CARRIER = DATA_COMPONENT_TYPES.register(
            "crop_data_carrier",
            () -> DataComponentType.<CropDataCarrierItemData>builder()
                    .persistent(CropDataCarrierItemData.CODEC)
                    .networkSynchronized(CropDataCarrierItemData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UniversalTerminalItemData>> UNIVERSAL_TERMINAL = DATA_COMPONENT_TYPES.register(
            "universal_terminal",
            () -> DataComponentType.<UniversalTerminalItemData>builder()
                    .persistent(UniversalTerminalItemData.CODEC)
                    .networkSynchronized(UniversalTerminalItemData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LIGHT_SABER_COLOR = DATA_COMPONENT_TYPES.register(
            "light_saber_color",
            () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> MATTER_CONVERGING_CROSSBOW_STORED_DATA = DATA_COMPONENT_TYPES.register(
            "matter_converging_crossbow_stored_data",
            () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> MATTER_CONVERGING_BOLT_DAMAGE_RATIO = DATA_COMPONENT_TYPES.register(
            "matter_converging_bolt_damage_ratio",
            () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> INFINITE_DATA_CELL_OBTAINED_CHECKED = DATA_COMPONENT_TYPES.register(
            "infinite_data_cell_obtained_checked",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POWERED_SHOVEL_BREAK_RADIUS = DATA_COMPONENT_TYPES.register(
            "powered_shovel_break_radius",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding()
                    .build());

    private ModDataComponents() {}

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
