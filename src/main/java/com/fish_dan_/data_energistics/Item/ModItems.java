package com.fish_dan_.data_energistics.Item;

import com.fish_dan_.data_energistics.Data_Energistics;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Data_Energistics.MODID);

    public static final DeferredItem<Item> DATA_CARRIER =
            ITEMS.register("data_carrier", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SOLIDIFY_DATA =
            ITEMS.register("solidify_data", () -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
