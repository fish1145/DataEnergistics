package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.registry.ModFluids;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

import appeng.items.storage.BasicStorageCell;
import appeng.items.tools.powered.AbstractPortableCell;

public final class ModItemColors {

    private ModItemColors() {}

    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register(makeOpaque(ModItemColors::getPortableCellColor),
                ModItems.PORTABLE_DATA_FLOW_CELL_1K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_4K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_16K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_64K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_256K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_1M.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_4M.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_16M.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_64M.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_256M.get());

        event.register(makeOpaque(BasicStorageCell::getColor),
                ModItems.DATA_FLOW_CELL_1K.get(),
                ModItems.DATA_FLOW_CELL_4K.get(),
                ModItems.DATA_FLOW_CELL_16K.get(),
                ModItems.DATA_FLOW_CELL_64K.get(),
                ModItems.DATA_FLOW_CELL_256K.get(),
                ModItems.DATA_FLOW_CELL_1M.get(),
                ModItems.DATA_FLOW_CELL_4M.get(),
                ModItems.DATA_FLOW_CELL_16M.get(),
                ModItems.DATA_FLOW_CELL_64M.get(),
                ModItems.DATA_FLOW_CELL_256M.get());

        event.register(new DynamicFluidContainerModel.Colors(),
                ModFluids.ENDER_BUCKET.get(),
                ModFluids.DATA_CORROSION_LIQUID_BUCKET.get());
    }

    private static ItemColor makeOpaque(ItemColor color) {
        return (ItemStack stack, int tintIndex) -> FastColor.ARGB32.opaque(color.getColor(stack, tintIndex));
    }

    private static int getPortableCellColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 1) {
            return AbstractPortableCell.getColor(stack, tintIndex);
        }

        return 0xFFFFFF;
    }
}
