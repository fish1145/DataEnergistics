package com.fish_dan_.data_energistics.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.implementations.items.IAEItemPowerStorage;

public class PoweredItemEnergyStorage implements IEnergyStorage {

    private final ItemStack stack;
    private final IAEItemPowerStorage item;

    public PoweredItemEnergyStorage(ItemStack stack, IAEItemPowerStorage item) {
        this.stack = stack;
        this.item = item;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        double convertedOffer = PowerUnit.FE.convertTo(PowerUnit.AE, maxReceive);
        double overflow = this.item.injectAEPower(
                this.stack,
                convertedOffer,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);

        return maxReceive - (int) PowerUnit.AE.convertTo(PowerUnit.FE, overflow);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) PowerUnit.AE.convertTo(PowerUnit.FE, this.item.getAECurrentPower(this.stack));
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) PowerUnit.AE.convertTo(PowerUnit.FE, this.item.getAEMaxPower(this.stack));
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
