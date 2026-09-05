package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;

import net.minecraft.core.HolderLookup;

/**
 * Resolves an immutable plan identity back to the exact live pattern published on the server thread.
 *
 */
public final class TrinityPatternResolver {

    /**
     * @return the production resolver
     */
    public static TrinityPatternResolver create() {
        return new TrinityPatternResolver();
    }

    /**
     * A non-null, explicit pattern lookup outcome.
     */
    public sealed interface Resolution permits Matched, Stale, Missing {}

    /**
     * @param pattern exact live publication represented by the retained plan identity
     */
    public record Matched(IPatternDetails pattern) implements Resolution {}

    /**
     * @param currentIdentity current semantics published for the same encoded pattern definition
     */
    public record Stale(TrinityPatternIdentity currentIdentity) implements Resolution {}

    /**
     * No live pattern currently publishes the retained encoded definition.
     */
    public record Missing() implements Resolution {}

    /**
     * Finds the current recipe object without weakening the publication signature captured by the plan.
     *
     * @param identity        stable pattern semantics retained by the plan
     * @param primaryOutput   output used by AE2's provider index
     * @param craftingService live grid-local crafting index
     * @param registries      server registry lookup used to recapture the signature
     * @return exact match, semantic change, or absence
     */
    public Resolution resolve(TrinityPatternIdentity identity,
                              AEKey primaryOutput,
                              CraftingService craftingService,
                              HolderLookup.Provider registries) {
        TrinityPatternIdentity changedPublication = null;
        for (IPatternDetails pattern : craftingService.getCraftingFor(primaryOutput)) {
            TrinityPatternIdentity current = TrinityPatternIdentity.capture(
                    TrinityPatternPublicationSignature.capture(pattern),
                    registries);
            if (identity.equals(current)) {
                return new Matched(pattern);
            }
            if (changedPublication == null &&
                    identity.definitionEncoding().equals(current.definitionEncoding())) {
                changedPublication = current;
            }
        }
        return changedPublication == null ? new Missing() : new Stale(changedPublication);
    }
}
