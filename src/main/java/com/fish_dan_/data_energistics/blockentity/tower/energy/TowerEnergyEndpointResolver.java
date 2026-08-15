package com.fish_dan_.data_energistics.blockentity.tower.energy;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Resolves FE endpoints that a Data Distribution Tower can extract from or receive into.
 *
 * <p>
 * The tower uses this boundary to keep side probing, target filtering, endpoint de-duplication, and resolved endpoint
 * caches outside of block entity lifecycle and NBT code.
 */
public interface TowerEnergyEndpointResolver {

    /**
     * Finds a storage at a block position and side.
     *
     * @param pos  target block position
     * @param side queried side, or null for internal access
     * @return native or integration-backed storage, or null
     */
    @Nullable
    IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable Direction side);

    /**
     * Finds the first accessible storage at a target position.
     *
     * @param pos        target block position
     * @param forReceive true when resolving insertion endpoints, false for extraction
     * @return accessible storage, or null
     */
    @Nullable
    IEnergyStorage findAccessibleEnergyStorage(BlockPos pos, boolean forReceive);

    /**
     * Finds all accessible endpoint views at a target position.
     *
     * @param pos        target block position
     * @param forReceive true when resolving insertion endpoints, false for extraction
     * @return immutable endpoint list
     */
    List<TowerEnergyEndpoint> findAccessibleEnergyEndpoints(BlockPos pos, boolean forReceive);

    /**
     * Returns cached cluster endpoints with one position excluded when needed.
     *
     * @param forReceive  true for insertion endpoints, false for extraction endpoints
     * @param excludedPos position that should not be returned, or null
     * @return endpoint list for transfer operations
     */
    List<TowerEnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos);

    /**
     * Resolves endpoints for the supplied tower cluster without using this resolver's resolved endpoint cache.
     *
     * @param towers     tower cluster being inspected
     * @param forReceive true for insertion endpoints, false for extraction endpoints
     * @return immutable endpoint list
     */
    List<TowerEnergyEndpoint> collectEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers, boolean forReceive);

    /**
     * Resolves endpoints for the current tower cluster without using the resolved endpoint cache.
     *
     * @param forReceive true for insertion endpoints, false for extraction endpoints
     * @return immutable endpoint list
     */
    List<TowerEnergyEndpoint> collectClusterEnergyEndpoints(boolean forReceive);

    /**
     * Returns cached resolved endpoints for the current tower cluster.
     *
     * @param forReceive true for insertion endpoints, false for extraction endpoints
     * @return immutable endpoint list
     */
    List<TowerEnergyEndpoint> getCachedResolvedEnergyEndpoints(boolean forReceive);

    /**
     * Normalizes an excluded extraction target only if it exists in the extraction endpoint cache.
     *
     * @param excludedPos raw excluded position
     * @return immutable excluded position, or null when not applicable
     */
    @Nullable
    BlockPos normalizeExtractExcludedPos(@Nullable BlockPos excludedPos);

    /**
     * Normalizes an excluded receive target only if it exists in the receive endpoint cache.
     *
     * @param excludedPos raw excluded position
     * @return immutable excluded position, or null when not applicable
     */
    @Nullable
    BlockPos normalizeReceiveExcludedPos(@Nullable BlockPos excludedPos);

    /**
     * Checks whether a storage can receive FE through native or direct integration access.
     *
     * @param storage storage being inspected
     * @return true when insert operations may succeed
     */
    boolean canReceiveEnergy(@Nullable IEnergyStorage storage);

    /**
     * Clears resolved endpoint caches after topology or target changes.
     */
    void invalidateResolvedCache();

    /**
     * Clears reusable temporary endpoint state.
     */
    void clearReusableCache();
}
