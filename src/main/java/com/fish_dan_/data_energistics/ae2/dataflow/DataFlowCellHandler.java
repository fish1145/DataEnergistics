package com.fish_dan_.data_energistics.ae2.dataflow;

import com.fish_dan_.data_energistics.item.cell.DataFlowPortableCellItem;
import com.fish_dan_.data_energistics.item.cell.DataFlowStorageCellItem;

import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import org.jspecify.annotations.Nullable;

/**
 * Routes Data Flow cells to the multi-resource inventory that stores both Data Flow and Echo.
 */
public final class DataFlowCellHandler implements ICellHandler {

    public static final DataFlowCellHandler INSTANCE = new DataFlowCellHandler();

    private DataFlowCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        return isDataFlowCell(stack);
    }

    @Override
    public @Nullable DataFlowCellInventory getCellInventory(ItemStack stack, @Nullable ISaveProvider container) {
        return isDataFlowCell(stack) ? new DataFlowCellInventory(stack, container) : null;
    }

    /**
     * Identifies regular and portable Data Flow cells that share the dual-resource storage format.
     */
    public static boolean isDataFlowCell(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof DataFlowStorageCellItem || stack.getItem() instanceof DataFlowPortableCellItem);
    }
}
