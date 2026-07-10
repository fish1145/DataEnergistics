package com.fish_dan_.data_energistics.ae2;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class AdaptivePatternProviderReturnItemHandler implements IItemHandler {

    private final Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier;

    public AdaptivePatternProviderReturnItemHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        this.logicSupplier = logicSupplier;
    }

    @Override
    public int getSlots() {
        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        return logic != null ? logic.getReturnInventorySlotCount() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        return logic != null ? logic.getReturnInventoryStack(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }

        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        if (logic == null) {
            return stack;
        }

        GenericStack genericStack = GenericStack.fromItemStack(stack);
        if (genericStack == null || genericStack.amount() <= 0) {
            return stack;
        }

        long inserted = logic.insertReturnInventoryStack(slot, genericStack, simulate);
        if (inserted <= 0) {
            return stack;
        }

        long remaining = genericStack.amount() - inserted;
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        return GenericStack.wrapInItemStack(genericStack.what(), remaining);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !stack.isEmpty() && !ItemStack.matches(stack, insertItem(slot, stack.copy(), true));
    }
}
