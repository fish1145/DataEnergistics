package com.fish_dan_.data_energistics.api.registry.machine.upload;

import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

/**
 * Ephemeral server-thread context for describing one live workstation variant in the provider upload panel.
 *
 * @param player              player viewing the upload panel
 * @param provider            exact provider leaf represented by the panel entry
 * @param providerIdentity    stable identity of that provider leaf
 * @param level               server level containing the workstation
 * @param workstationPosition exact workstation position
 * @param inputSide           workstation face reached by the provider route
 * @param workstation         exact live block entity matched by the registration
 * @param patternDetails      current server-decoded pattern, or {@code null} before a pattern is encoded
 * @param recipeTypeId        optional recipe-type/category hint derived from the final encoded pattern
 * @param recipeId            optional stable processing recipe identity captured from viewer transfer
 */
public record PatternUploadWorkstationInspectionContext(ServerPlayer player,
                                                        PatternContainer provider,
                                                        PatternProviderIdentity providerIdentity,
                                                        ServerLevel level,
                                                        BlockPos workstationPosition,
                                                        Direction inputSide,
                                                        BlockEntity workstation,
                                                        @Nullable IPatternDetails patternDetails,
                                                        @Nullable ResourceLocation recipeTypeId,
                                                        @Nullable ResourceLocation recipeId) {

    public PatternUploadWorkstationInspectionContext {
        workstationPosition = workstationPosition.immutable();
    }
}
