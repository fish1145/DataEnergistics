package com.fish_dan_.data_energistics.client.registry;

import com.fish_dan_.data_energistics.registry.DEFluids;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

import appeng.items.storage.BasicStorageCell;
import appeng.items.tools.powered.AbstractPortableCell;

public final class DEItemColors {

    private DEItemColors() {}

    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register(makeOpaque(DEItemColors::getPortableCellColor),
                DEItems.PORTABLE_DATA_FLOW_CELL_1K.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_4K.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_16K.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_64K.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_256K.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_1M.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_4M.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_16M.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_64M.get(),
                DEItems.PORTABLE_DATA_FLOW_CELL_256M.get());

        event.register(makeOpaque(BasicStorageCell::getColor),
                DEItems.DATA_FLOW_CELL_1K.get(),
                DEItems.DATA_FLOW_CELL_4K.get(),
                DEItems.DATA_FLOW_CELL_16K.get(),
                DEItems.DATA_FLOW_CELL_64K.get(),
                DEItems.DATA_FLOW_CELL_256K.get(),
                DEItems.DATA_FLOW_CELL_1M.get(),
                DEItems.DATA_FLOW_CELL_4M.get(),
                DEItems.DATA_FLOW_CELL_16M.get(),
                DEItems.DATA_FLOW_CELL_64M.get(),
                DEItems.DATA_FLOW_CELL_256M.get());

        event.register(new DynamicFluidContainerModel.Colors(),
                DEFluids.ENDER_BUCKET.get(),
                DEFluids.DATA_CORROSION_LIQUID_BUCKET.get());
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
