package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

/**
 * UI-only wrapper that preserves an AE2 simulation result while attaching the failed Trinity attempt.
 *
 * <p>
 * This type is deliberately distinct from {@link TrinityCraftingPlan}; every submission path must reject it.
 * </p>
 */
public final class TrinityDiagnosedCraftingPlan implements ICraftingPlan {

    private final ICraftingPlan delegate;
    private final TrinityPlanningDiagnostic diagnostic;

    /**
     * @param delegate   original AE2 simulation, retained without rewriting missing/used/emitted contents
     * @param diagnostic failed Trinity calculation
     */
    public TrinityDiagnosedCraftingPlan(ICraftingPlan delegate, TrinityPlanningDiagnostic diagnostic) {
        if (delegate == null || !delegate.simulation()) {
            throw new IllegalArgumentException("A diagnosed crafting plan requires an AE2 simulation");
        }
        if (diagnostic == null) {
            throw new IllegalArgumentException("A diagnosed crafting plan requires a Trinity diagnostic");
        }
        this.delegate = delegate;
        this.diagnostic = diagnostic;
    }

    /**
     * @return failed Trinity calculation attached to the original AE2 simulation
     */
    public TrinityPlanningDiagnostic diagnostic() {
        return this.diagnostic;
    }

    /**
     * @return exact original AE2 plan for UI comparison and debugging
     */
    public ICraftingPlan delegate() {
        return this.delegate;
    }

    @Override
    public GenericStack finalOutput() {
        return this.delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return this.delegate.bytes();
    }

    @Override
    public boolean simulation() {
        return true;
    }

    @Override
    public boolean multiplePaths() {
        return this.delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return this.delegate.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return this.delegate.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return this.delegate.missingItems();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return this.delegate.patternTimes();
    }
}
