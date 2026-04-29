package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Data_Energistics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DATA_ENERGISTICS_TAB = CREATIVE_MODE_TABS.register(
            Data_Energistics.MODID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Data_Energistics.MODID))
                    .icon(ModItems.REDSTONE_ALLOY::toStack)
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DATA_FLOW_CELL_1K);
                        output.accept(ModItems.DATA_FLOW_CELL_4K);
                        output.accept(ModItems.DATA_FLOW_CELL_16K);
                        output.accept(ModItems.DATA_FLOW_CELL_64K);
                        output.accept(ModItems.DATA_FLOW_CELL_256K);

                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_1K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_4K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_16K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_64K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_256K);

                        output.accept(ModItems.DATA_STORAGE_COMPONENT_1K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_4K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_16K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_64K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_256K);

                        output.accept(ModItems.DATA_FLOW_GENERATOR);
                        output.accept(ModItems.DATA_EXTRACTOR);
                        output.accept(ModItems.DATA_FRAMEWORK);
                        output.accept(ModItems.DATA_DISTRIBUTION_TOWER);
                        output.accept(ModItems.REDSTONE_ALLOY);
                        output.accept(ModItems.SOLIDIFIED_OBSIDIAN);
                        output.accept(ModItems.MIXED_REDSTONE_DUST);
                        output.accept(ModItems.OBSIDIAN_DUST);
                        output.accept(ModItems.DATA_CARRIER);
                        output.accept(ModItems.TIME_CORE);
                        output.accept(ModItems.DATA_FLOW_COMPONENT_HOUSING);
                        output.accept(ModItems.DATA_INSCRIBER_TEMPLATE);
                        output.accept(ModItems.DATA_CIRCUIT_BOARD);
                        output.accept(ModItems.DATA_PROCESSOR);
                        output.accept(ModItems.DATA_RIPPER);
                    })
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS.location())
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
