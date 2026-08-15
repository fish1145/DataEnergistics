package com.fish_dan_.data_energistics.network.trinity.crafting.protocol;

import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * Closed classification of the three record families carried by the cycle-summary protocol.
 */
public sealed interface TrinityCraftConfirmCycleRecord permits TrinityCraftConfirmCycleRecord.Header,
                                                       TrinityCraftConfirmCycleRecord.Material, TrinityCraftConfirmCycleRecord.InventoryInput {

    /** Carries one validated repeat-block header. */
    record Header(TrinityCraftingCycleHeader value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries one validated material-to-cycle contribution. */
    record Material(TrinityCraftingCycleMaterialContribution value) implements TrinityCraftConfirmCycleRecord {}

    /** Carries one exact global ME withdrawal without assigning it to an individual cycle. */
    record InventoryInput(AEKey key, BigInteger amount) implements TrinityCraftConfirmCycleRecord {

        /** Rejects invalid inventory amounts at construction and decode boundaries. */
        public InventoryInput {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity crafting confirmation inventory inputs must be positive");
            }
        }
    }
}
