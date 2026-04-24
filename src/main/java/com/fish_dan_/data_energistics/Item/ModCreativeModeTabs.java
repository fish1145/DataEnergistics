package com.fish_dan_.data_energistics.Item;


import com.fish_dan_.data_energistics.Data_Energistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Data_Energistics.MODID);

    public static final Supplier<CreativeModeTab> DATA_CARRIER =
            CREATIVE_MODE_TABS.register("data_carrier", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.DATA_CARRIER.get()))
                    .title(Component.translatable("itemGroup.data_energistics"))
                    .displayItems((Parameters, output) -> {
                        output.accept(ModItems.DATA_CARRIER);
                        output.accept(ModItems.SOLIDIFY_DATA);
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
