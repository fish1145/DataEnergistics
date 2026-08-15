package com.fish_dan_.data_energistics.blockentity.tower.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Supplies block-entity-owned state required by {@link TowerEnergyTransferEngine}.
 *
 * <p>
 * The distributor owns transfer logic, while the context keeps world access, AE flux host access, and diagnostic
 * counters tied to the owning block entity.
 */
public interface TowerEnergyDistributorContext {

    /**
     * Returns the level containing the owning tower.
     *
     * @return level, or null before attachment
     */
    @Nullable
    Level level();

    /**
     * Checks whether the owning tower can currently transfer energy.
     *
     * @return true when transfer operations should run
     */
    boolean isTowerActive();

    /**
     * Returns the AE network host used for optional AppFlux extraction.
     *
     * @return AE networked tower block entity
     */
    AENetworkedBlockEntity aeNetworkHost();

    /**
     * Returns energy already extracted from a source but not yet delivered or restored.
     *
     * @return persisted transfer-buffer energy in FE
     */
    long bufferedTransferEnergy();

    /**
     * Replaces the persisted transfer-buffer amount after a verified transfer mutation.
     *
     * @param amount non-negative transfer-buffer energy in FE
     */
    void setBufferedTransferEnergy(long amount);

    /**
     * Returns energy whose final third-party storage location could not be verified after a direct mutation failure.
     *
     * @return persisted, unavailable quarantine amount in FE
     */
    long quarantinedTransferEnergy();

    /**
     * Replaces the persisted quarantine amount after an uncertain direct mutation.
     *
     * @param amount non-negative unavailable quarantine amount in FE
     */
    void setQuarantinedTransferEnergy(long amount);

    /**
     * Marks a direct-access target block entity changed after successful insertion.
     *
     * @param pos endpoint position
     */
    void markEndpointChanged(BlockPos pos);

    /**
     * Records the largest extraction endpoint count seen in the current diagnostic window.
     *
     * @param endpointCount endpoint count
     */
    void recordMaxExtractEndpoints(int endpointCount);

    /**
     * Records the largest receive endpoint count seen in the current diagnostic window.
     *
     * @param endpointCount endpoint count
     */
    void recordMaxReceiveEndpoints(int endpointCount);

    /**
     * Records a simulated extraction cache hit.
     */
    void recordSimulatedCacheHit();

    /**
     * Records a simulated extraction cache miss.
     */
    void recordSimulatedCacheMiss();
}
