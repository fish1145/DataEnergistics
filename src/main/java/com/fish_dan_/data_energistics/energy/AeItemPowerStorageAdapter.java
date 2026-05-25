package com.fish_dan_.data_energistics.energy;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class AeItemPowerStorageAdapter implements MutableDataEnergyStorage {
    private final IAEItemPowerStorage storage;
    private final ItemStack stack;

    public AeItemPowerStorageAdapter(IAEItemPowerStorage storage, ItemStack stack) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.stack = Objects.requireNonNull(stack, "stack");
    }

    public IAEItemPowerStorage unwrapStorage() {
        return this.storage;
    }

    public ItemStack unwrapStack() {
        return this.stack;
    }

    @Override
    public double getStored() {
        return this.storage.getAECurrentPower(this.stack);
    }

    @Override
    public double getCapacity() {
        return this.storage.getAEMaxPower(this.stack);
    }

    @Override
    public double insert(double amount, boolean simulate) {
        double accepted = amount - this.storage.injectAEPower(this.stack, amount, toActionable(simulate));
        return Math.max(0.0D, accepted);
    }

    @Override
    public double extract(double amount, boolean simulate) {
        return this.storage.extractAEPower(this.stack, amount, toActionable(simulate));
    }

    private static Actionable toActionable(boolean simulate) {
        return simulate ? Actionable.SIMULATE : Actionable.MODULATE;
    }
}
