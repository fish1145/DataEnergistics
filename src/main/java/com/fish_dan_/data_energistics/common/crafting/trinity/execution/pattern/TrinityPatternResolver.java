package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import net.minecraft.core.HolderLookup;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;

/**
 * Resolves an immutable plan identity back to the exact live pattern published on the server thread.
 */
public interface TrinityPatternResolver {

    /**
     * @return the production resolver
     */
    static TrinityPatternResolver create() {
        return new TrinityPatternResolverImpl();
    }

    /**
     * Finds the current recipe object without weakening the publication signature captured by the plan.
     *
     * @param identity        stable pattern semantics retained by the plan
     * @param primaryOutput   output used by AE2's provider index
     * @param craftingService live grid-local crafting index
     * @param registries      server registry lookup used to recapture the signature
     * @return exact match, semantic change, or absence
     */
    Resolution resolve(TrinityPatternIdentity identity,
                       AEKey primaryOutput,
                       CraftingService craftingService,
                       HolderLookup.Provider registries);

    /** A non-null, explicit pattern lookup outcome. */
    sealed interface Resolution permits Matched, Stale, Missing {}

    /**
     * @param pattern exact live publication represented by the retained plan identity
     */
    record Matched(IPatternDetails pattern) implements Resolution {}

    /**
     * @param currentIdentity current semantics published for the same encoded pattern definition
     */
    record Stale(TrinityPatternIdentity currentIdentity) implements Resolution {}

    /** No live pattern currently publishes the retained encoded definition. */
    record Missing() implements Resolution {}
}
