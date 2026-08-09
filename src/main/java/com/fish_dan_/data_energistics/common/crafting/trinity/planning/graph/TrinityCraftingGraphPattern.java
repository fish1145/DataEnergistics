package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import java.util.List;

/**
 * Immutable planner-facing transition captured from one AE crafting pattern.
 *
 * @param identity    stable component-aware semantic identity used for ordering and later invalidation
 * @param publication complete immutable input/output surface
 */
public record TrinityCraftingGraphPattern(TrinityPatternIdentity identity,
                                          TrinityPatternPublicationSignature publication) {

    /**
     * @return encoded pattern key with all data components
     */
    public AEItemKey definition() {
        return this.publication.definition();
    }

    /**
     * @return immutable ordered input slots and their legal alternatives
     */
    public List<TrinityPatternPublicationSignature.Input> inputs() {
        return this.publication.inputs();
    }

    /**
     * @return immutable ordered outputs, with the primary output first
     */
    public List<GenericStack> outputs() {
        return this.publication.outputs();
    }

    /**
     * @return whether generic external inventories may accept this transition's inputs directly
     */
    public boolean pushesInputsToExternalInventory() {
        return this.publication.pushesInputsToExternalInventory();
    }
}
