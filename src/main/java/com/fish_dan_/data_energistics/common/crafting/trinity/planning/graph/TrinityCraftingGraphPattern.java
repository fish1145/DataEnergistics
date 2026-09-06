package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;

/**
 * Immutable planner-facing transition captured from one AE crafting pattern.
 *
 * @param identity         stable component-aware semantic identity used for ordering and later invalidation
 * @param publication      complete immutable input/output surface
 * @param reusableBindings request-local complete assignments proved on the server, or empty for legacy Cartesian
 *                         binding
 */
public record TrinityCraftingGraphPattern(TrinityPatternIdentity identity,
                                          TrinityPatternPublicationSignature publication,
                                          List<List<TrinityBoundPatternInput>> reusableBindings) {

    public TrinityCraftingGraphPattern {
        reusableBindings = reusableBindings.stream().map(List::copyOf).toList();
        ObjectOpenHashSet<List<TrinityBoundPatternInput>> unique = new ObjectOpenHashSet<>();
        for (List<TrinityBoundPatternInput> assignment : reusableBindings) {
            if (assignment.size() != publication.inputs().size() || !unique.add(assignment)) {
                throw new IllegalArgumentException("Reusable graph bindings must be complete and unique");
            }
            for (int slot = 0; slot < assignment.size(); slot++) {
                if (assignment.get(slot).slotIndex() != slot ||
                        assignment.get(slot).multiplier() != publication.inputs().get(slot).multiplier()) {
                    throw new IllegalArgumentException("Reusable graph binding does not match the original input slots");
                }
            }
        }
    }

    /** Retains the original provider-only graph representation when no request-local rule was captured. */
    public TrinityCraftingGraphPattern(TrinityPatternIdentity identity, TrinityPatternPublicationSignature publication) {
        this(identity, publication, List.of());
    }

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
