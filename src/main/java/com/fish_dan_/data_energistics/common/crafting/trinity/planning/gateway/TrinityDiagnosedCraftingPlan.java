package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.util.LongAmountMath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.Map;

/**
 * UI-only plan that projects a terminal Trinity diagnostic without exposing it as executable work.
 *
 * <p>
 * This type is deliberately distinct from {@link TrinityCraftingPlan}; every submission path must reject it.
 * </p>
 */
public final class TrinityDiagnosedCraftingPlan implements ICraftingPlan {

    private final ICraftingPlan view;
    private final TrinityPlanningDiagnostic diagnostic;
    private final boolean ae2FallbackEstimate;
    private final long calculationNanos;

    /**
     * @param delegate   original AE2 simulation, retained without rewriting missing/used/emitted contents
     * @param diagnostic failed Trinity calculation
     */
    public TrinityDiagnosedCraftingPlan(
                                        ICraftingPlan delegate,
                                        TrinityPlanningDiagnostic diagnostic) {
        this(delegate, diagnostic, true, 0L);
    }

    /**
     * @param delegate         original AE2 simulation, retained without rewriting missing/used/emitted contents
     * @param diagnostic       failed Trinity calculation
     * @param calculationNanos elapsed AE2 calculation time in nanoseconds
     */
    public TrinityDiagnosedCraftingPlan(
                                        ICraftingPlan delegate,
                                        TrinityPlanningDiagnostic diagnostic,
                                        long calculationNanos) {
        this(delegate, diagnostic, true, calculationNanos);
    }

    private TrinityDiagnosedCraftingPlan(
                                         ICraftingPlan view,
                                         TrinityPlanningDiagnostic diagnostic,
                                         boolean ae2FallbackEstimate,
                                         long calculationNanos) {
        if (!view.simulation()) {
            throw new IllegalArgumentException("A diagnosed crafting plan requires a simulation view");
        }
        if (calculationNanos < 0L) {
            throw new IllegalArgumentException("Crafting calculation time must not be negative");
        }
        this.view = view;
        this.diagnostic = diagnostic;
        this.ae2FallbackEstimate = ae2FallbackEstimate;
        this.calculationNanos = calculationNanos;
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
        diagnostic.inputShortage().orElseThrow(() -> new IllegalArgumentException(
                "A standalone Trinity diagnostic plan requires an exact input shortage"));
        return forDiagnostic(finalOutput, diagnostic);
    }

    /**
     * Builds a terminal result without starting or adopting the native AE2 planner. Exact shortages and verified
     * partial progress retain their material counters; diagnostics without material evidence expose the requested
     * output as unresolved.
     *
     * @param finalOutput requested delivery retained for the confirmation menu
     * @param diagnostic  terminal Trinity result
     * @return non-executable standalone diagnostic plan
     */
    public static TrinityDiagnosedCraftingPlan forDiagnostic(
                                                             GenericStack finalOutput,
                                                             TrinityPlanningDiagnostic diagnostic) {
        ICraftingPlan view;
        if (diagnostic.inputShortage().isPresent()) {
            TrinityPlanningDiagnostic.InputShortage shortage = diagnostic.inputShortage().orElseThrow();
            view = new InputShortageSimulation(
                    finalOutput,
                    shortage.key(),
                    LongAmountMath.saturatingLongValueNonNegative(shortage.available()),
                    LongAmountMath.saturatingLongValueNonNegative(shortage.missing()));
        } else if (diagnostic.partialPlan().isPresent()) {
            TrinityPlanningDiagnostic.PartialPlan partial = diagnostic.partialPlan().orElseThrow();
            view = new PartialSimulation(
                    finalOutput,
                    partial.usedItems(),
                    partial.emittedItems(),
                    partial.missingItems());
        } else {
            view = new DiagnosticSimulation(finalOutput);
        }
        return new TrinityDiagnosedCraftingPlan(view, diagnostic, false, 0L);
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

    /**
     * @return elapsed AE2 calculation time in nanoseconds, or zero for a standalone Trinity diagnostic
     */
    public long calculationNanos() {
        return this.calculationNanos;
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
            if (finalOutput.amount() <= 0L || availableAmount < 0L || missingAmount <= 0L) {
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

    private record DiagnosticSimulation(GenericStack finalOutput) implements ICraftingPlan {

        private DiagnosticSimulation {
            if (finalOutput.amount() <= 0L) {
                throw new IllegalArgumentException("A Trinity diagnostic simulation requires a positive request");
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
            return new KeyCounter();
        }

        @Override
        public KeyCounter emittedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter missingItems() {
            KeyCounter missing = new KeyCounter();
            missing.add(this.finalOutput.what(), this.finalOutput.amount());
            return missing;
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }
    }

    private record PartialSimulation(
                                     GenericStack finalOutput,
                                     Map<AEKey, BigInteger> used,
                                     Map<AEKey, BigInteger> emitted,
                                     Map<AEKey, BigInteger> missing)
            implements ICraftingPlan {

        private PartialSimulation {
            if (finalOutput.amount() <= 0L) {
                throw new IllegalArgumentException("A Trinity partial simulation requires a positive request");
            }
            used = Map.copyOf(used);
            emitted = Map.copyOf(emitted);
            missing = Map.copyOf(missing);
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
            return toCounter(this.used);
        }

        @Override
        public KeyCounter emittedItems() {
            return toCounter(this.emitted);
        }

        @Override
        public KeyCounter missingItems() {
            return toCounter(this.missing);
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }

        private static KeyCounter toCounter(Map<AEKey, BigInteger> amounts) {
            KeyCounter counter = new KeyCounter();
            amounts.forEach((key, amount) -> counter.add(
                    key,
                    LongAmountMath.saturatingLongValueNonNegative(amount)));
            return counter;
        }
    }
}
