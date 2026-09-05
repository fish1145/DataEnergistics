package com.fish_dan_.data_energistics.ae2.cell;

import com.fish_dan_.data_energistics.item.cell.InfiniteDataCellItem;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

public final class InfiniteDataCellHandler implements ICellHandler {

    public static final InfiniteDataCellHandler INSTANCE = new InfiniteDataCellHandler();

    private InfiniteDataCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof InfiniteDataCellItem;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider container) {
        return this.isCell(stack) ? new InfiniteDataCellInventory(stack) : null;
    }
}
