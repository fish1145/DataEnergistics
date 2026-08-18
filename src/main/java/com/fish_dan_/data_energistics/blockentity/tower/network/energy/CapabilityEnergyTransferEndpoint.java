package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegrationRegistry;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess.EnergySnapshot;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

/**
 * Capability-backed transaction endpoint dispatched through the registered Mod integration for its route.
 */
public final class CapabilityEnergyTransferEndpoint implements TowerEnergyTransferEndpoint {

    private final TowerDomainEnergyEndpoint endpoint;
    private final TowerEnergyEndpointIntegrationRegistry integrations;

    /**
     * Creates an endpoint using a shared registry owned by the network domain.
     */
    public CapabilityEnergyTransferEndpoint(TowerDomainEnergyEndpoint endpoint,
                                            TowerEnergyEndpointIntegrationRegistry integrations) {
        this.endpoint = endpoint;
        this.integrations = integrations;
    }

    @Override
    public TowerEnergyEndpointId endpoint() {
        return this.endpoint.endpoint();
    }

    @Override
    public TowerEnergyEndpointSnapshot freeze() {
        requireLoaded();
        TowerEnergyEndpointContext context = endpointContext();
        try {
            TowerEnergyEndpointIntegration integration = integration(context);
            if (integration.isBuffer()) {
                long extractable = captureBudget(integration, context, Long.MAX_VALUE, false);
                long receivable = captureBudget(integration, context, Long.MAX_VALUE, true);
                return TowerEnergyEndpointSnapshot.buffer(endpoint(), extractable, receivable);
            }

            EnergySnapshot snapshot = integration.snapshot(context);
            validateSnapshot(snapshot);
            TowerEnergyDirection direction = integration.direction(context);
            if (direction == null) {
                throw new TowerEnergyTransferException(
                        "Energy endpoint no longer permits transfer: " + description());
            }

            long free = snapshot.capacity() - snapshot.stored();
            long extractable = direction.allowsExtract() ? captureBudget(integration, context, snapshot.stored(), false) : 0L;
            long receivable = direction.allowsReceive() ? captureBudget(integration, context, free, true) : 0L;
            return new TowerEnergyEndpointSnapshot(
                    endpoint(), snapshot.stored(), snapshot.capacity(), extractable, receivable, direction);
        } catch (TowerEnergyTransferException exception) {
            throw exception;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw new TowerEnergyTransferException("Could not freeze energy endpoint " + description(), exception);
        }
    }

    @Override
    public long simulateExtraction(long amount) {
        return transfer(amount, true, false);
    }

    @Override
    public long extractionQuantum() {
        TowerEnergyEndpointContext context = endpointContext();
        long quantum = integration(context).extractionQuantum(context);
        return requireQuantum("extraction", quantum);
    }

    @Override
    public long extract(long amount) {
        return transfer(amount, false, false);
    }

    @Override
    public long compensateExtraction(long amount) {
        validateAmount(amount);
        if (amount == 0L) {
            return 0L;
        }
        requireLoaded();
        TowerEnergyEndpointContext context = endpointContext();
        try {
            long restored = integration(context).compensateExtraction(context, amount);
            return validateResult("compensate extraction", amount, restored);
        } catch (TowerEnergyTransferException exception) {
            throw exception;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw new TowerEnergyTransferException(
                    "Could not compensate extraction on " + description(), exception);
        }
    }

    @Override
    public long simulateInsertion(long amount) {
        return transfer(amount, true, true);
    }

    @Override
    public long insertionQuantum() {
        TowerEnergyEndpointContext context = endpointContext();
        long quantum = integration(context).insertionQuantum(context);
        return requireQuantum("insertion", quantum);
    }

    @Override
    public long insert(long amount) {
        return transfer(amount, false, true);
    }

    @Override
    public void publishMutation() {
        requireLoaded();
        TowerEnergyEndpointContext context = endpointContext();
        try {
            integration(context).publishMutation(context);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw new TowerEnergyTransferException(
                    "Could not publish mutation for " + description(), exception);
        }
    }

    @Override
    public String description() {
        return this.endpoint.endpoint().dimensionId() + " " + this.endpoint.endpoint().pos() + " side=" + this.endpoint.endpoint().side() + " storage=" + this.endpoint.storage().getClass().getName();
    }

    private long transfer(long amount, boolean simulate, boolean inserting) {
        validateAmount(amount);
        if (amount == 0L) {
            return 0L;
        }
        requireLoaded();
        TowerEnergyEndpointContext context = endpointContext();
        try {
            TowerEnergyEndpointIntegration integration = integration(context);
            long transferred = inserting ? integration.insert(context, amount, simulate) : integration.extract(context, amount, simulate);
            return validateResult(inserting ? "insert" : "extract", amount, transferred);
        } catch (TowerEnergyTransferException exception) {
            throw exception;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw new TowerEnergyTransferException(
                    "Could not " + (inserting ? "insert into " : "extract from ") + description(), exception);
        }
    }

    private long captureBudget(TowerEnergyEndpointIntegration integration,
                               TowerEnergyEndpointContext context,
                               long available,
                               boolean inserting) {
        if (available <= 0L) {
            return 0L;
        }
        long transferred = inserting ? integration.insert(context, available, true) : integration.extract(context, available, true);
        return validateResult(inserting ? "capture insertion budget" : "capture extraction budget",
                available, transferred);
    }

    private TowerEnergyEndpointContext endpointContext() {
        return new TowerEnergyEndpointContext(
                this.endpoint.location().level(),
                this.endpoint.location().position(),
                this.endpoint.endpoint().side(),
                this.endpoint.storage());
    }

    private TowerEnergyEndpointIntegration integration(TowerEnergyEndpointContext context) {
        return this.integrations.resolve(context);
    }

    private void requireLoaded() {
        if (!this.endpoint.location().level().isLoaded(this.endpoint.location().position())) {
            throw new TowerEnergyTransferException("Energy endpoint chunk unloaded: " + description());
        }
    }

    private static void validateSnapshot(EnergySnapshot snapshot) {
        if (snapshot.stored() < 0L || snapshot.capacity() < snapshot.stored()) {
            throw new TowerEnergyTransferException(
                    "Energy endpoint returned invalid frozen state " + snapshot.stored() + "/" + snapshot.capacity());
        }
    }

    private static long requireQuantum(String operation, long quantum) {
        if (quantum <= 0L) {
            throw new TowerEnergyTransferException(
                    "Energy endpoint returned invalid " + operation + " quantum " + quantum);
        }
        return quantum;
    }

    private static void validateAmount(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Tower energy transfer amount must not be negative");
        }
    }

    private static long validateResult(String operation, long requested, long actual) {
        if (actual < 0L || actual > requested) {
            throw new TowerEnergyTransferException(
                    "Energy endpoint returned invalid " + operation + " result " + actual + " for " + requested);
        }
        return actual;
    }
}
