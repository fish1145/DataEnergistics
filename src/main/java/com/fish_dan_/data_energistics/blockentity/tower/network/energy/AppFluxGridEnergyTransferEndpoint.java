package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.integration.appflux.AE2FluxIntegration;

import net.minecraft.world.level.Level;

import appeng.blockentity.grid.AENetworkedBlockEntity;

/**
 * Bidirectional virtual endpoint backed by the primary grid's Applied Flux inventory.
 */
public final class AppFluxGridEnergyTransferEndpoint implements TowerEnergyTransferEndpoint {

    /**
     * Stable identity reserved for the grid-level Applied Flux endpoint.
     */
    private final TowerEnergyEndpointId endpoint;

    /**
     * Loaded tower used as the AE action source.
     */
    private final AENetworkedBlockEntity host;

    /**
     * Creates the single Applied Flux endpoint for one primary-grid domain.
     *
     * @param endpoint stable grid endpoint identity
     * @param host     loaded tower action source
     */
    public AppFluxGridEnergyTransferEndpoint(
                                             TowerEnergyEndpointId endpoint, AENetworkedBlockEntity host) {
        this.endpoint = endpoint;
        this.host = host;
    }

    @Override
    public TowerEnergyEndpointId endpoint() {
        return this.endpoint;
    }

    @Override
    public TowerEnergyEndpointSnapshot freeze() {
        requireLoaded();
        long extractable = AE2FluxIntegration.extractEnergyFromOwnNetwork(this.host, Long.MAX_VALUE, true);
        long receivable = AE2FluxIntegration.insertEnergyIntoOwnNetwork(this.host, Long.MAX_VALUE, true);
        if (extractable < 0 || receivable < 0) {
            throw new TowerEnergyTransferException("Applied Flux returned a negative network snapshot");
        }
        return TowerEnergyEndpointSnapshot.buffer(this.endpoint, extractable, receivable);
    }

    @Override
    public long simulateExtraction(long amount) {
        validateAmount(amount);
        requireLoaded();
        return validateResult(amount, AE2FluxIntegration.extractEnergyFromOwnNetwork(this.host, amount, true));
    }

    @Override
    public long extract(long amount) {
        validateAmount(amount);
        requireLoaded();
        return validateResult(amount, AE2FluxIntegration.extractEnergyFromOwnNetwork(this.host, amount, false));
    }

    @Override
    public long compensateExtraction(long amount) {
        validateAmount(amount);
        requireLoaded();
        return validateResult(amount, AE2FluxIntegration.insertEnergyIntoOwnNetwork(this.host, amount, false));
    }

    @Override
    public long simulateInsertion(long amount) {
        validateAmount(amount);
        requireLoaded();
        return validateResult(amount, AE2FluxIntegration.insertEnergyIntoOwnNetwork(this.host, amount, true));
    }

    @Override
    public long insert(long amount) {
        validateAmount(amount);
        requireLoaded();
        return validateResult(amount, AE2FluxIntegration.insertEnergyIntoOwnNetwork(this.host, amount, false));
    }

    @Override
    public void publishMutation() {
        // Applied Flux persists MODULATE operations through its ME storage cells. Dirtying the action-source tower
        // would only keep its chunk pending for save while FE is flowing through the network.
    }

    @Override
    public String description() {
        return "Applied Flux grid endpoint via " + this.host.getBlockPos();
    }

    /**
     * Rejects access after the action-source tower unloads.
     */
    private void requireLoaded() {
        Level level = this.host.getLevel();
        if (level == null || !level.isLoaded(this.host.getBlockPos())) {
            throw new TowerEnergyTransferException("Applied Flux action source is unloaded");
        }
    }

    /**
     * Rejects negative requests before invoking optional integration code.
     */
    private static void validateAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Applied Flux transfer amount must not be negative");
        }
    }

    /**
     * Rejects invalid optional-integration responses.
     */
    private static long validateResult(long requested, long actual) {
        if (actual < 0 || actual > requested) {
            throw new TowerEnergyTransferException(
                    "Applied Flux returned invalid transfer result " + actual + " for " + requested);
        }
        return actual;
    }
}
