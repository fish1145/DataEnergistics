package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.stacks.AEFluidKey;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class AdaptivePatternProviderReturnFluidHandler implements IFluidHandler {

    private final Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier;

    public AdaptivePatternProviderReturnFluidHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        this.logicSupplier = logicSupplier;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        if (tank != 0 || stack.isEmpty()) {
            return false;
        }

        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        return logic != null && AEFluidKey.of(stack) != null;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }

        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        if (logic == null) {
            return 0;
        }

        AEFluidKey fluidKey = AEFluidKey.of(resource);
        if (fluidKey == null) {
            return 0;
        }

        long inserted = logic.insertReturnInventoryKey(
                fluidKey,
                resource.getAmount(),
                action.simulate());
        return (int) Math.min(Integer.MAX_VALUE, inserted);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
