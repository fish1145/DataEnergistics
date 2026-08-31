package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Immutable cycle algorithm selection consumed by graph-stage assembly.
 *
 * @param componentIndex       owning SCC index
 * @param prefixOrder          one-time residual batches executed before the complete cycle units
 * @param localOrder           compressed executable batches for one repeat unit
 * @param repetitions          positive repeat count applied by graph assembly
 * @param suffixOrder          one-time residual batches executed after every complete cycle unit
 * @param minimumSeed          exact prefix reserve exposed to graph assembly
 * @param initialInputs        exact initial inventory reserved for the selected cycle
 * @param netChange            exact signed aggregate cycle effect
 * @param exportableNet        settled positive outputs proved safe to expose outside the complete cycle block
 * @param scheduleStates       bounded search states visited
 * @param mipNanos             deterministic opportunity time plus ojAlgo solver time
 * @param quality              exact proof strength retained by this cycle
 * @param retainedSeed         internal balance that must remain after downstream settlement
 * @param seedRefinementPasses additional solves required to make the selected route restart-safe
 */
public record TrinityCycleSelection(
                                    int componentIndex,
                                    List<TrinityVariantFiring> prefixOrder,
                                    List<TrinityVariantFiring> localOrder,
                                    BigInteger repetitions,
                                    List<TrinityVariantFiring> suffixOrder,
                                    Map<AEKey, BigInteger> minimumSeed,
                                    Map<AEKey, BigInteger> initialInputs,
                                    Map<AEKey, BigInteger> netChange,
                                    Map<AEKey, BigInteger> exportableNet,
                                    int scheduleStates,
                                    long mipNanos,
                                    TrinityPlanQuality quality,
                                    Map<AEKey, BigInteger> retainedSeed,
                                    int seedRefinementPasses) {

    /**
     * Compatibility constructor for cycle paths that already carry a complete optimality proof.
     */
    public TrinityCycleSelection(
                                 int componentIndex,
                                 List<TrinityVariantFiring> prefixOrder,
                                 List<TrinityVariantFiring> localOrder,
                                 BigInteger repetitions,
                                 List<TrinityVariantFiring> suffixOrder,
                                 Map<AEKey, BigInteger> minimumSeed,
                                 Map<AEKey, BigInteger> initialInputs,
                                 Map<AEKey, BigInteger> netChange,
                                 Map<AEKey, BigInteger> exportableNet,
                                 int scheduleStates,
                                 long mipNanos) {
        this(
                componentIndex,
                prefixOrder,
                localOrder,
                repetitions,
                suffixOrder,
                minimumSeed,
                initialInputs,
                netChange,
                exportableNet,
                scheduleStates,
                mipNanos,
                TrinityPlanQuality.PROVED_OPTIMAL,
                Map.of(),
                0);
    }

    /**
     * Compatibility constructor for selections created before terminal seed refinement.
     */
    public TrinityCycleSelection(
                                 int componentIndex,
                                 List<TrinityVariantFiring> prefixOrder,
                                 List<TrinityVariantFiring> localOrder,
                                 BigInteger repetitions,
                                 List<TrinityVariantFiring> suffixOrder,
                                 Map<AEKey, BigInteger> minimumSeed,
                                 Map<AEKey, BigInteger> initialInputs,
                                 Map<AEKey, BigInteger> netChange,
                                 Map<AEKey, BigInteger> exportableNet,
                                 int scheduleStates,
                                 long mipNanos,
                                 TrinityPlanQuality quality) {
        this(
                componentIndex,
                prefixOrder,
                localOrder,
                repetitions,
                suffixOrder,
                minimumSeed,
                initialInputs,
                netChange,
                exportableNet,
                scheduleStates,
                mipNanos,
                quality,
                Map.of(),
                0);
    }
}
