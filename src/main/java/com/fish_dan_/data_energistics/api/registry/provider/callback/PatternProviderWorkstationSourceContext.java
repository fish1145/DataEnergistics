package com.fish_dan_.data_energistics.api.registry.provider.callback;

import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternContainer;
import org.jspecify.annotations.Nullable;

/**
 * Ephemeral server-thread context used by a custom provider to expose its real workstation routes.
 *
 * @param player                player who requested the upload
 * @param provider              exact provider leaf whose inventory may receive the pattern
 * @param providerIdentity      stable identity of the provider leaf
 * @param patternDetails        server-decoded pattern semantics, or {@code null} during pattern-less panel grouping
 * @param recipeTypeId          optional recipe-type/category hint derived from the final encoded pattern
 * @param recipeId              optional stable processing recipe identity captured from the viewer transfer
 * @param requestedPatternCount number of encoded patterns still awaiting this provider leaf, or zero during
 *                              pattern-less panel grouping
 */
public record PatternProviderWorkstationSourceContext(ServerPlayer player,
                                                      PatternContainer provider,
                                                      PatternProviderIdentity providerIdentity,
                                                      @Nullable IPatternDetails patternDetails,
                                                      @Nullable ResourceLocation recipeTypeId,
                                                      @Nullable ResourceLocation recipeId,
                                                      int requestedPatternCount) {}
