package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable pure-algorithm request shared by initial planning and remaining-work replanning.
 *
 * @param gridScope       server-lifetime Grid publication scope
 * @param graph           immutable current graph revision
 * @param target          requested output
 * @param requestedAmount positive requested delivery
 * @param quantityMode    net-new or final-total semantics
 * @param available       positive server-thread inventory snapshot
 * @param limits          immutable planner bounds
 */
public record TrinityPlanningInput(
                                   long gridScope,
                                   TrinityCraftingGraphSnapshot graph,
                                   AEKey target,
                                   BigInteger requestedAmount,
                                   CraftingQuantityMode quantityMode,
                                   Map<AEKey, BigInteger> available,
                                   TrinityPlanningLimits limits) {

    /**
     * Copies inventory values and rejects mutable or incomplete request state before background submission.
     */
    public TrinityPlanningInput {
        if (gridScope <= 0L || graph == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || available == null || limits == null) {
            throw new IllegalArgumentException("A Trinity cached planning request is incomplete");
        }
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        available.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity cached inventory may contain only positive named amounts");
            }
            copied.put(key, amount);
        });
        available = Collections.unmodifiableMap(copied);
    }

    /**
     * Compatibility constructor that captures a mutable configuration immediately.
     */
    public TrinityPlanningInput(
                                long gridScope,
                                TrinityCraftingGraphSnapshot graph,
                                AEKey target,
                                BigInteger requestedAmount,
                                CraftingQuantityMode quantityMode,
                                Map<AEKey, BigInteger> available,
                                TrinityCraftingSchema settings) {
        this(
                gridScope,
                graph,
                target,
                requestedAmount,
                quantityMode,
                available,
                TrinityPlanningLimits.capture(settings));
    }

    /**
     * @return detached compatibility configuration that cannot mutate this input
     * @deprecated use {@link #limits()}
     */
    @Deprecated(forRemoval = false)
    public TrinityCraftingSchema settings() {
        return this.limits.detachedSchema();
    }
}
