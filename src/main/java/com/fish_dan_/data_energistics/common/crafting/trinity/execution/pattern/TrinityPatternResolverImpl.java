package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;

import net.minecraft.core.HolderLookup;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;

/** Server-thread implementation backed by AE2's primary-output pattern index. */
final class TrinityPatternResolverImpl implements TrinityPatternResolver {

    @Override
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
