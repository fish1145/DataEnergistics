package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resolves Data Distribution Tower target display data.
 *
 * <p>
 * The tower UI needs compact summaries, CableBus part rows, and AE crafting cluster de-duplication without keeping
 * display grouping logic inside the block entity lifecycle code.
 */
public interface TowerTargetDisplayResolver {

    /**
     * Counts displayable bound targets.
     *
     * @return target count visible to the menu
     */
    int boundTargetCount();

    /**
     * Returns structured target summaries for the tower menu.
     *
     * @param maxEntries maximum amount of summaries to return
     * @return immutable target summaries
     */
    List<BoundTargetSummary> boundTargetSummaries(int maxEntries);

    /**
     * Checks whether an AE target should appear in the bound target UI.
     *
     * @param blockEntity block entity being inspected
     * @return true when the target should be shown
     */
    boolean hasDisplayableAeTarget(@Nullable BlockEntity blockEntity);

    /**
     * Checks whether a target should be hidden from bound target summaries.
     *
     * @param blockEntity block entity being inspected
     * @return true when the target is noise or a non-preferred subsystem component
     */
    boolean shouldHideFromBoundTargetDisplay(@Nullable BlockEntity blockEntity);
}
