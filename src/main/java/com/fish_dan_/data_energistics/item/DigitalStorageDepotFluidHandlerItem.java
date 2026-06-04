package com.fish_dan_.data_energistics.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public class DigitalStorageDepotFluidHandlerItem implements IFluidHandlerItem {

    private final ItemStack container;

    public DigitalStorageDepotFluidHandlerItem(ItemStack container) {
        this.container = container;
    }

    @Override
    public ItemStack getContainer() {
        return this.container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0 || !DigitalStorageDepotBlockItem.isBucketMode(this.container)) {
            return FluidStack.EMPTY;
        }
        return DigitalStorageDepotBlockItem.getSelectedStoredFluid(this.container);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? DigitalStorageDepotBlockItem.getFluidCapacity(this.container) : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && DigitalStorageDepotBlockItem.isBucketMode(this.container) && DigitalStorageDepotBlockItem.fillSelectedFluidSlot(this.container.copy(), stack, FluidAction.SIMULATE) > 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return DigitalStorageDepotBlockItem.fillSelectedFluidSlot(this.container, resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return DigitalStorageDepotBlockItem.drainSelectedFluidSlot(this.container, resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return DigitalStorageDepotBlockItem.drainSelectedFluidSlot(this.container, maxDrain, action);
    }
}
