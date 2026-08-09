package com.fish_dan_.data_energistics.blockentity.tower.topology;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Supplies tower-owned target state required by {@link TowerTargetSummaryResolver}.
 *
 * <p>
 * The resolver consumes already-discovered targets and mode checks from the owning block entity while keeping display
 * formatting and AE crafting cluster grouping out of the core block entity.
 */
public interface TowerTargetDisplayResolverContext {

    /**
     * Returns the level containing the tower.
     *
     * @return level, or null before attachment
     */
    @Nullable
    Level level();

    /**
     * Removes invalid tracked targets before building display summaries.
     */
    void cleanupInvalidDisplayTargets();

    /**
     * Returns persisted linked target positions.
     *
     * @return linked positions
     */
    Collection<BlockPos> linkedPositions();

    /**
     * Returns all persisted and pending point-to-point target positions.
     *
     * @return tracked target positions
     */
    Collection<BlockPos> trackedPositions();

    /**
     * Returns target positions with explicit transfer mode settings.
     *
     * @return configured target positions
     */
    Collection<BlockPos> configuredTargetPositions();

    /**
     * Returns cached AE display target positions.
     *
     * @return AE display targets
     */
    List<BlockPos> cachedAeDisplayTargets();

    /**
     * Returns cached FE endpoint positions.
     *
     * @return FE endpoint targets
     */
    List<BlockPos> cachedEndpointPositions();

    /**
     * Checks whether AE targets are visible for the current connection mode.
     *
     * @return true when AE display targets are allowed
     */
    boolean allowsAeDisplayTargets();

    /**
     * Checks whether a target is allowed to appear as an AE target.
     *
     * @param pos target position
     * @return true when AE target mode allows the position
     */
    boolean targetAllowsAe(BlockPos pos);

    /**
     * Checks whether a target is allowed to appear as an FE target.
     *
     * @param pos target position
     * @return true when FE target mode allows the position
     */
    boolean targetAllowsFe(BlockPos pos);

    /**
     * Checks whether the target is inside tower coverage.
     *
     * @param pos target position
     * @return true when covered
     */
    boolean isWithinTowerCoverage(BlockPos pos);

    /**
     * Checks whether a target should be represented as a dedicated AE grid target.
     *
     * @param pos target position
     * @return true when the target has an AE grid node that should be displayed as AE
     */
    boolean isDedicatedAeGridTarget(BlockPos pos);

    /**
     * Checks whether a target has an AE node capability.
     *
     * @param pos target position
     * @return true when an AE node host is present
     */
    boolean hasExposedAeNode(BlockPos pos);

    /**
     * Checks whether a target has receive-capable FE storage.
     *
     * @param pos target position
     * @return true when FE can be inserted
     */
    boolean hasReceiveEnergyTarget(BlockPos pos);

    /**
     * Returns the explicit transfer mode for a target.
     *
     * @param pos target position
     * @return target transfer mode
     */
    TargetTransferMode targetTransferMode(BlockPos pos);

    /**
     * Returns transfer details for display summaries.
     *
     * @param pos target position
     * @return transfer information
     */
    TargetTransferInfo targetTransferInfo(BlockPos pos);

    /**
     * Returns the preferred display kind for a target.
     *
     * @param pos target position
     * @return preferred target kind, or null when unsupported
     */
    @Nullable
    TargetKind preferredDisplayKind(BlockPos pos);
}
