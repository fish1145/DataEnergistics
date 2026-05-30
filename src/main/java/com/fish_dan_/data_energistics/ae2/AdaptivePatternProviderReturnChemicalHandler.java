package com.fish_dan_.data_energistics.ae2;

import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

public class AdaptivePatternProviderReturnChemicalHandler implements IChemicalHandler {

    private final Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier;

    public AdaptivePatternProviderReturnChemicalHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        this.logicSupplier = Objects.requireNonNull(logicSupplier, "logicSupplier");
    }

    @Override
    public int getChemicalTanks() {
        return 1;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return ChemicalStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {}

    @Override
    public long getChemicalTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        if (tank != 0 || stack.isEmpty()) {
            return false;
        }

        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        return logic != null && MekanismKey.of(stack) != null;
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (tank != 0 || stack.isEmpty()) {
            return stack;
        }

        AdaptivePatternProviderLogic logic = this.logicSupplier.get();
        if (logic == null) {
            return stack;
        }

        MekanismKey chemicalKey = MekanismKey.of(stack);
        if (chemicalKey == null) {
            return stack;
        }

        long inserted = logic.insertReturnInventoryKey(chemicalKey, stack.getAmount(), action.simulate());
        if (inserted <= 0) {
            return stack;
        }

        long remaining = stack.getAmount() - inserted;
        return remaining <= 0 ? ChemicalStack.EMPTY : stack.copyWithAmount(remaining);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        return ChemicalStack.EMPTY;
    }
}
