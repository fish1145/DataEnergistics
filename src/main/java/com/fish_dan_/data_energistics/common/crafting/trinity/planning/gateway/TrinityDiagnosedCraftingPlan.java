package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputShortage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

/**
 * UI-only plan that either preserves an AE2 simulation or projects one exact Trinity shortage in constant space.
 *
 * <p>
 * This type is deliberately distinct from {@link TrinityCraftingPlan}; every submission path must reject it.
 * </p>
 */
public final class TrinityDiagnosedCraftingPlan implements ICraftingPlan {

    private final ICraftingPlan view;
    private final TrinityPlanningDiagnostic diagnostic;
    private final boolean ae2FallbackEstimate;

    /**
     * @param delegate   original AE2 simulation, retained without rewriting missing/used/emitted contents
     * @param diagnostic failed Trinity calculation
     */
    public TrinityDiagnosedCraftingPlan(ICraftingPlan delegate, TrinityPlanningDiagnostic diagnostic) {
        this(delegate, diagnostic, true);
    }

    private TrinityDiagnosedCraftingPlan(
                                         ICraftingPlan view,
                                         TrinityPlanningDiagnostic diagnostic,
                                         boolean ae2FallbackEstimate) {
        if (view == null || !view.simulation()) {
            throw new IllegalArgumentException("A diagnosed crafting plan requires a simulation view");
        }
        if (diagnostic == null) {
            throw new IllegalArgumentException("A diagnosed crafting plan requires a Trinity diagnostic");
        }
        this.view = view;
        this.diagnostic = diagnostic;
        this.ae2FallbackEstimate = ae2FallbackEstimate;
    }

    /**
     * Builds a constant-size simulation for an exact Trinity input shortage, avoiding an unbounded AE2 fallback.
     *
     * @param finalOutput requested delivery retained for the confirmation menu
     * @param diagnostic  exact typed shortage produced by the Trinity planner
     * @return non-executable standalone diagnostic plan
     */
    public static TrinityDiagnosedCraftingPlan forInputShortage(
                                                                GenericStack finalOutput,
                                                                TrinityPlanningDiagnostic diagnostic) {
        InputShortage shortage = diagnostic.inputShortage().orElseThrow(() -> new IllegalArgumentException(
                "A standalone Trinity diagnostic plan requires an exact input shortage"));
        ICraftingPlan view = new InputShortageSimulation(
                finalOutput,
                shortage.key(),
                shortage.available().longValueExact(),
                shortage.missing().longValueExact());
        return new TrinityDiagnosedCraftingPlan(view, diagnostic, false);
    }

    /**
     * @return failed Trinity calculation attached to the original AE2 simulation
     */
    public TrinityPlanningDiagnostic diagnostic() {
        return this.diagnostic;
    }

    /**
     * @return AE2 delegate or the constant-size Trinity shortage projection used by the confirmation menu
     */
    public ICraftingPlan delegate() {
        return this.view;
    }

    /**
     * @return whether counters were produced by AE2 rather than the standalone Trinity diagnostic projection
     */
    public boolean ae2FallbackEstimate() {
        return this.ae2FallbackEstimate;
    }

    @Override
    public GenericStack finalOutput() {
        return this.view.finalOutput();
    }

    @Override
    public long bytes() {
        return this.view.bytes();
    }

    @Override
    public boolean simulation() {
        return true;
    }

    @Override
    public boolean multiplePaths() {
        return this.view.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return this.view.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return this.view.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return this.view.missingItems();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return this.view.patternTimes();
    }

    private record InputShortageSimulation(
                                           GenericStack finalOutput,
                                           AEKey shortageKey,
                                           long availableAmount,
                                           long missingAmount)
            implements ICraftingPlan {

        private InputShortageSimulation {
            if (finalOutput == null || finalOutput.amount() <= 0L || shortageKey == null ||
                    availableAmount < 0L || missingAmount <= 0L) {
                throw new IllegalArgumentException(
                        "A Trinity shortage simulation requires non-negative availability and a positive shortage");
            }
        }

        @Override
        public long bytes() {
            return 0L;
        }

        @Override
        public boolean simulation() {
            return true;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            KeyCounter used = new KeyCounter();
            if (this.availableAmount > 0L) {
                used.add(this.shortageKey, this.availableAmount);
            }
            return used;
        }

        @Override
        public KeyCounter emittedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter missingItems() {
            KeyCounter missing = new KeyCounter();
            missing.add(this.shortageKey, this.missingAmount);
            return missing;
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }
    }
}
