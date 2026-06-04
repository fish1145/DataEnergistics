package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotItemData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    private ModDataComponents() {}

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
