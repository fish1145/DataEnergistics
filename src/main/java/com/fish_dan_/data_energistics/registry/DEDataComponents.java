package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.carrier.CropDataCarrierItemData;
import com.fish_dan_.data_energistics.item.carrier.MobDataCarrierItemData;
import com.fish_dan_.data_energistics.item.carrier.OreDataCarrierItemData;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItemData;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotItemData;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotMemoryCardData;
import com.fish_dan_.data_energistics.item.terminal.UniversalTerminalItemData;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;

import java.util.UUID;

public final class DEDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(
            Registries.DATA_COMPONENT_TYPE,
            Data_Energistics.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> BEAM_BINDING_SOURCE = DATA_COMPONENT_TYPES.register(
            "beam_binding_source", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DigitalStorageDepotItemData>> DIGITAL_STORAGE_DEPOT = DATA_COMPONENT_TYPES.register(
            "digital_storage_depot",
            () -> DataComponentType.<DigitalStorageDepotItemData>builder()
                    .persistent(DigitalStorageDepotItemData.CODEC)
                    .networkSynchronized(DigitalStorageDepotItemData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DataDistributionConnectorItemData>> DATA_DISTRIBUTION_CONNECTOR = DATA_COMPONENT_TYPES.register(
            "data_distribution_connector",
            () -> DataComponentType.<DataDistributionConnectorItemData>builder()
                    .persistent(DataDistributionConnectorItemData.CODEC)
                    .networkSynchronized(DataDistributionConnectorItemData.STREAM_CODEC)
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AEKey>> ORDER_PACKAGE_TARGET = DATA_COMPONENT_TYPES.register(
            "order_package_target",
            () -> DataComponentType.<AEKey>builder()
                    .persistent(AEKey.CODEC)
                    .networkSynchronized(AEKey.STREAM_CODEC)
                    .cacheEncoding()
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> TRINITY_DATA_CORE_STORAGE_ID = DATA_COMPONENT_TYPES.register(
            "trinity_data_core_storage_id",
            () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> TRINITY_DATA_CORE_HOST_ID = DATA_COMPONENT_TYPES.register(
            "trinity_data_core_host_id",
            () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TRINITY_DATA_CORE_STORAGE_PRIORITY = DATA_COMPONENT_TYPES.register(
            "trinity_data_core_storage_priority",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TRINITY_DATA_CORE_PATTERN_PRIORITY = DATA_COMPONENT_TYPES.register(
            "trinity_data_core_pattern_priority",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PROCESSING_OUTPUT_SAME_ITEM = DATA_COMPONENT_TYPES.register(
            "processing_output_same_item",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .cacheEncoding()
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PROCESSING_PATTERN_RECIPE_TYPE = DATA_COMPONENT_TYPES.register(
            "processing_pattern_recipe_type",
            () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    private DEDataComponents() {}

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
