package com.fish_dan_.data_energistics.network.trinity.crafting.protocol;

import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingExactShortage;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingUnresolvedDemand;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingExactPlanAmounts;

import appeng.api.stacks.AEKey;

/**
 * Closed classification of the five record families carried by the cycle-summary protocol.
 */
public sealed interface TrinityCraftConfirmCycleRecord permits TrinityCraftConfirmCycleRecord.Header,
                                                       TrinityCraftConfirmCycleRecord.Material,
                                                       TrinityCraftConfirmCycleRecord.InventoryUsage,
                                                       TrinityCraftConfirmCycleRecord.ExactShortage,
                                                       TrinityCraftConfirmCycleRecord.UnresolvedDemand,
                                                       TrinityCraftConfirmCycleRecord.ExactPlanAmounts {

    /** Carries one validated repeat-block header. */
    record Header(TrinityCraftingCycleHeader value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries one validated material-to-cycle contribution. */
    record Material(TrinityCraftingCycleMaterialContribution value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries one exact finite-input shortage. */
    record ExactShortage(TrinityCraftingExactShortage value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries one unresolved intermediate demand. */
    record UnresolvedDemand(TrinityCraftingUnresolvedDemand value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries exact stored, missing and crafting counters for one confirmation-table row. */
    record ExactPlanAmounts(TrinityCraftingExactPlanAmounts value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries one capped ME inventory-usage percentage without assigning it to an individual cycle. */
    record InventoryUsage(AEKey key, int basisPoints) implements TrinityCraftConfirmCycleRecord {

        /** Rejects percentages outside [0%, 100%] at construction and decode boundaries. */
        public InventoryUsage {
            if (basisPoints < 0 || basisPoints > TrinityCraftingCycleSummary.MAX_INVENTORY_USAGE_BASIS_POINTS) {
                throw new IllegalArgumentException("Trinity crafting confirmation inventory usage is out of range");
            }
        }
    }
}
