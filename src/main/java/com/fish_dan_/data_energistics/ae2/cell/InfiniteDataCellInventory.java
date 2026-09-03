package com.fish_dan_.data_energistics.ae2.cell;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;
import com.fish_dan_.data_energistics.ae2.grid.UnlimitedExtractableStorage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;

public final class InfiniteDataCellInventory implements StorageCell, UnlimitedExtractableStorage {

    public static final long STORED_AMOUNT = Long.MAX_VALUE;

    private final ItemStack stack;

    public InfiniteDataCellInventory(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        return supports(what) ? amount : 0L;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        return supports(what) ? amount : 0L;
    }

    @Override
    public boolean supportsUnlimitedExtraction(AEKey key, IActionSource source) {
        return supports(key);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AEKey key : DEAE2Keys.keys()) {
            out.add(key, STORED_AMOUNT);
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        return supports(input);
    }

    @Override
    public CellState getStatus() {
        return CellState.TYPES_FULL;
    }

    @Override
    public double getIdleDrain() {
        return 0.0D;
    }

    @Override
    public boolean canFitInsideCell() {
        return false;
    }

    @Override
    public Component getDescription() {
        return this.stack.getHoverName();
    }

    @Override
    public void persist() {}

    private static boolean supports(AEKey key) {
        return DEAE2Keys.isCustomKey(key);
    }
}
