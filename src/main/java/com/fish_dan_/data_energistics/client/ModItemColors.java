package com.fish_dan_.data_energistics.client;

import appeng.items.storage.BasicStorageCell;
import com.fish_dan_.data_energistics.registry.ModItems;
import appeng.items.tools.powered.AbstractPortableCell;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

public final class ModItemColors {
    private ModItemColors() {
    }

    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register(makeOpaque(ModItemColors::getPortableCellColor),
                ModItems.PORTABLE_DATA_FLOW_CELL_1K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_4K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_16K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_64K.get(),
                ModItems.PORTABLE_DATA_FLOW_CELL_256K.get());

        event.register(makeOpaque(BasicStorageCell::getColor),
                ModItems.DATA_FLOW_CELL_1K.get(),
                ModItems.DATA_FLOW_CELL_4K.get(),
                ModItems.DATA_FLOW_CELL_16K.get(),
                ModItems.DATA_FLOW_CELL_64K.get(),
                ModItems.DATA_FLOW_CELL_256K.get());
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
