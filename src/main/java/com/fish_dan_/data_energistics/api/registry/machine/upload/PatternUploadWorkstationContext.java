package com.fish_dan_.data_energistics.api.registry.machine.upload;

import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternContainer;
import org.jspecify.annotations.Nullable;

/**
 * Ephemeral server-thread context for one exact provider leaf and one actual workstation.
 *
 * <p>
 * The player, provider, workstation and decoded pattern remain owned by the runtime and may only be inspected during
 * the adapter callback. {@code patternDetails.getDefinition()} exposes the immutable encoded item and components
 * without copying a mutable {@code ItemStack} for every workstation. The workstation may live in a different server
 * level from the player when a custom provider source explicitly exposes such a route.
 * </p>
 *
 * @param player                player who requested the upload
 * @param provider              exact provider leaf whose inventory may receive the pattern
 * @param providerIdentity      stable identity of that provider leaf
 * @param level                 server level containing the workstation
 * @param workstationPosition   exact workstation position
 * @param inputSide             workstation face reached by the provider route
 * @param workstation           exact live block entity matched by the registration
 * @param patternDetails        server-decoded pattern semantics
 * @param recipeTypeId          optional recipe-type/category hint derived from the final encoded pattern; adapters
 *                              must validate it against {@code patternDetails}
 * @param requestedPatternCount positive number of encoded patterns still awaiting this leaf
 */
public record PatternUploadWorkstationContext(ServerPlayer player,
                                              PatternContainer provider,
                                              PatternProviderIdentity providerIdentity,
                                              ServerLevel level,
                                              BlockPos workstationPosition,
                                              Direction inputSide,
                                              BlockEntity workstation,
                                              IPatternDetails patternDetails,
                                              @Nullable ResourceLocation recipeTypeId,
                                              int requestedPatternCount) {

    public PatternUploadWorkstationContext {
        workstationPosition = workstationPosition.immutable();
    }
}
