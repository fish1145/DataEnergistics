package com.fish_dan_.data_energistics.common.compartment;

/**
 * Compartment part role that exposes pattern buffer inventories to a formed multiblock host.
 *
 * <p>
 * Hosts use this contract when they need pattern-specific storage without depending on a
 * concrete block entity class.
 */
public interface PatternBufferCompartmentPart extends CompartmentPart {

    /**
     * Returns the pattern item/configuration inventory shown to the pattern buffer UI.
     */
    CompartmentInventory patternStorage();

    /**
     * Returns the structure-facing storage associated with one pattern slot.
     *
     * @param slot pattern slot index
     */
    CompartmentStorage patternBufferStorage(int slot);

    /**
     * Returns an aggregate structure-facing view over all visible pattern buffer slot storages.
     */
    CompartmentStorage patternAggregateStorage();
}
