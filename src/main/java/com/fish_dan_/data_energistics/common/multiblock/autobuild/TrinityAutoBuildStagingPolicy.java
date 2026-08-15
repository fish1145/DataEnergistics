package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.StagingPolicy;
import com.fish_dan_.data_energistics.common.multiblock.json.autobuild.JsonMultiBlockAutoBuildStaging;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Staging policy derived from one resolved Trinity JSON structure definition.
 *
 * <p>
 * The JSON metadata selects existing predicate symbols rather than repeating block or item ids. One policy instance is
 * created from the same definition instance as the pattern, preventing data-pack reloads from splitting the pattern
 * from its staging permissions.
 * </p>
 */
public final class TrinityAutoBuildStagingPolicy implements StagingPolicy {

    private final JsonMultiBlockAutoBuildStaging staging;

    public TrinityAutoBuildStagingPolicy(JsonMultiBlockDefinition definition) {
        this.staging = definition.autoBuildStaging();
    }

    @Override
    public boolean canStageBlock(BlockPos position, ItemStack stack, BlockState desiredState) {
        return this.staging.allowsBlock(desiredState);
    }

    @Override
    public boolean canPhysicallyStageBlock(BlockPos position, ItemStack stack, BlockState desiredState) {
        return this.staging.allowsPhysicalBlock(desiredState);
    }

    @Nullable
    @Override
    public BlockState partHostState(BlockPos position, ItemStack partStack, Direction side) {
        return this.staging.partHostState(partStack);
    }
}
