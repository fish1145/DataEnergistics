package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Selects one legal, deterministic input binding from a live pattern immediately before dispatch.
 */
public interface TrinityPatternSelector {

    /**
     * @return the production Cartesian selector
     */
    static TrinityPatternSelector create() {
        return new TrinityPatternSelectorImpl();
    }

    /**
     * Selects either the planned ordinal or, for a cycle stage, the best currently executable alternative.
     *
     * @param pattern             exact live pattern returned by {@link TrinityPatternResolver}
     * @param plannedOrdinal      binding ordinal retained by the plan
     * @param dynamic             whether this cycle stage may switch to another legal binding
     * @param remainingCrafts     remaining logical firings for the work item
     * @param cpuAvailability     current CPU-owned amount for a key
     * @param networkAvailability current simulatable network amount for a key
     * @param maxVariants         configured Cartesian expansion bound
     * @return explicit selection, wait set, or a hard planning bound failure
     */
    Result select(IPatternDetails pattern,
                  int plannedOrdinal,
                  boolean dynamic,
                  long remainingCrafts,
                  ToLongFunction<AEKey> cpuAvailability,
                  ToLongFunction<AEKey> networkAvailability,
                  int maxVariants);

    /** A non-null input-binding outcome. */
    sealed interface Result permits Selected, Unavailable, VariantLimit, ArithmeticOverflow {}

    /**
     * @param extractionPattern immutable wrapper exposing only the chosen alternative in each input slot
     * @param variantOrdinal    selected Cartesian ordinal
     * @param maximumCrafts     maximum currently material-feasible logical batch
     * @param inputsPerCraft    exact aggregated consumption for one firing
     * @param observedKeys      all keys whose inventory changes may change this decision
     */
    record Selected(IPatternDetails extractionPattern,
                    int variantOrdinal,
                    long maximumCrafts,
                    List<GenericStack> inputsPerCraft,
                    Set<AEKey> observedKeys)
            implements Result {

        /** Isolates collections returned by the selector from callers. */
        public Selected {
            inputsPerCraft = List.copyOf(inputsPerCraft);
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    /**
     * @param observedKeys keys that should wake this stage when material availability changes
     */
    record Unavailable(Set<AEKey> observedKeys) implements Result {

        /** Isolates the wake set from mutable callers. */
        public Unavailable {
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    /**
     * @param required number of legal Cartesian variants
     * @param limit    configured maximum
     */
    record VariantLimit(BigInteger required, int limit) implements Result {}

    /**
     * @param operation exact quantity operation that crossed the AE2 long boundary
     */
    record ArithmeticOverflow(String operation) implements Result {}
}
