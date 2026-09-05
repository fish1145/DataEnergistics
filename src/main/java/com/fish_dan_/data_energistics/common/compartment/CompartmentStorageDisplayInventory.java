package com.fish_dan_.data_energistics.common.compartment;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Read-only menu inventory exposing a stable sorted window over map-backed compartment storage.
 */
public final class CompartmentStorageDisplayInventory implements InternalInventory {

    private final Supplier<CompartmentStorage> storageSupplier;
    private final int windowSize;

    public CompartmentStorageDisplayInventory(Supplier<CompartmentStorage> storageSupplier, int windowSize) {
        this.storageSupplier = storageSupplier;
        this.windowSize = windowSize;
    }

    @Override
    public int size() {
        return this.windowSize;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        GenericStack stack = stackAt(slotIndex);
        return stack == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(stack.what(), stack.amount());
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {}

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    private @Nullable GenericStack stackAt(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.windowSize) {
            return null;
        }
        List<Object2LongMap.Entry<AEKey>> entries = sortedEntries();
        if (slotIndex >= entries.size()) {
            return null;
        }
        Object2LongMap.Entry<AEKey> entry = entries.get(slotIndex);
        return new GenericStack(entry.getKey(), entry.getLongValue());
    }

    private List<Object2LongMap.Entry<AEKey>> sortedEntries() {
        return this.storageSupplier.get().entries().object2LongEntrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getLongValue() > 0L)
                .sorted(Comparator
                        .comparing((Object2LongMap.Entry<AEKey> entry) -> entry.getKey().getType().getId().toString())
                        .thenComparing(entry -> entry.getKey().toString()))
                .toList();
    }
}
