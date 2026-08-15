package com.fish_dan_.data_energistics.blockentity.tower.energy;

import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Supplies tower-specific state required by {@link CachedTowerEnergyEndpointResolver}.
 *
 * <p>
 * The resolver needs target mode checks and cluster traversal from the owning block entity, while the block entity
 * keeps ownership of persistence, UI state, and AE link graph mutation.
 */
public interface TowerEnergyEndpointResolverContext {

    /**
     * Returns the level containing the owning tower.
     *
     * @return level, or null before the block entity is attached
     */
    @Nullable
    Level level();

    /**
     * Returns the current tower cluster.
     *
     * @return tower cluster used for shared FE transfer
     */
    List<DataDistributionTowerBlockEntity> collectTowerCluster();

    /**
     * Returns cached FE-capable target positions for a tower.
     *
     * @param tower tower being inspected
     * @return target positions already accepted by tower discovery
     */
    List<BlockPos> cachedEndpointPositions(DataDistributionTowerBlockEntity tower);

    /**
     * Checks whether a tower allows FE interaction with a target.
     *
     * @param tower tower owning the target mode state
     * @param pos   target position
     * @return true when FE transfer may use the target
     */
    boolean targetAllowsFe(DataDistributionTowerBlockEntity tower, BlockPos pos);

    /**
     * Checks whether a target should be reserved for AE grid display/linking rather than receive-side FE transfer.
     *
     * @param tower tower performing the target check
     * @param pos   target position
     * @return true when receive endpoint resolution should skip the target
     */
    boolean isDedicatedAeGridTarget(DataDistributionTowerBlockEntity tower, BlockPos pos);

    /**
     * Checks whether the position belongs to a Data Distribution Tower block.
     *
     * @param pos target position
     * @return true when the target is a tower block
     */
    boolean isTowerBlock(BlockPos pos);

    /**
     * Resolves accessible endpoints through the target tower's resolver.
     *
     * @param tower      tower whose side lookup should be used
     * @param pos        target position
     * @param forReceive true for insertion endpoints, false for extraction endpoints
     * @return endpoint list
     */
    List<TowerEnergyEndpoint> accessibleEnergyEndpoints(DataDistributionTowerBlockEntity tower, BlockPos pos, boolean forReceive);
}
