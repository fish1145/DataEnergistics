package com.fish_dan_.data_energistics.registry;

import appeng.menu.implementations.MenuTypeBuilder;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.DataRipperMenu;
import com.fish_dan_.data_energistics.part.DataRipperPart;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Data_Energistics.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DataRipperMenu>> DATA_RIPPER =
            MENUS.register("data_ripper", () -> MenuTypeBuilder
                    .create(DataRipperMenu::new, DataRipperPart.class)
                    .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_ripper")));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
