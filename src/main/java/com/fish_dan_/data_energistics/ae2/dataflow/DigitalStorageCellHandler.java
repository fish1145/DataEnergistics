package com.fish_dan_.data_energistics.ae2.dataflow;

import com.fish_dan_.data_energistics.item.cell.DigitalStorageCellItem;
import com.fish_dan_.data_energistics.item.cell.PortableDigitalStorageCellItem;

import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import org.jspecify.annotations.Nullable;

/**
 * Routes digital storage cells to the multi-resource inventory that stores both Data Flow and Echo.
 */
public final class DigitalStorageCellHandler implements ICellHandler {

    public static final DigitalStorageCellHandler INSTANCE = new DigitalStorageCellHandler();

    private DigitalStorageCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        return isDigitalStorageCell(stack);
    }

    @Override
    public @Nullable DigitalStorageCellInventory getCellInventory(ItemStack stack, @Nullable ISaveProvider container) {
        return isDigitalStorageCell(stack) ? new DigitalStorageCellInventory(stack, container) : null;
    }

    /**
     * Identifies regular and portable digital storage cells that share the dual-resource storage format.
     */
    public static boolean isDigitalStorageCell(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof DigitalStorageCellItem || stack.getItem() instanceof PortableDigitalStorageCellItem);
    }
}
