package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessImpl;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess.EnergySnapshot;
import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Capability-backed transaction endpoint with long-width typed access and a public-API fallback.
 */
public final class TowerEnergyTransferEndpointImpl implements TowerEnergyTransferEndpoint {

    /** Resolved capability and loaded owner location. */
    private final TowerDomainEnergyEndpoint endpoint;

    /** Optional BrandonsCore long-width bridge. */
    private final BrandonsCoreEnergyBridge brandonsCore = new BrandonsCoreEnergyBridge();

    /** Verified typed direct access for standard and explicitly unlimited storages. */
    private final UnlimitedEnergyAccess unlimitedEnergy = new UnlimitedEnergyAccessImpl();

    /**
     * Creates an executable endpoint from one topology resolution.
     *
     * @param endpoint resolved capability endpoint
     */
    public TowerEnergyTransferEndpointImpl(TowerDomainEnergyEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public TowerEnergyEndpointId endpoint() {
        return this.endpoint.endpoint();
    }

    @Override
    public TowerEnergyEndpointSnapshot freeze() {
        requireLoaded();
        IEnergyStorage storage = this.endpoint.storage();
        try {
            boolean brandonsCoreSupported = this.brandonsCore.supports(storage);
            long stored;
            long capacity;
            boolean canExtract;
            boolean canReceive;
            if (brandonsCoreSupported) {
                stored = this.brandonsCore.stored(storage);
                capacity = this.brandonsCore.capacity(storage);
                canExtract = this.brandonsCore.canExtract(storage);
                canReceive = this.brandonsCore.canReceive(storage);
            } else {
                EnergySnapshot snapshot = this.unlimitedEnergy.snapshot(storage);
                stored = snapshot.stored();
                capacity = snapshot.capacity();
                canExtract = this.unlimitedEnergy.canExtract(storage);
                canReceive = this.unlimitedEnergy.canReceive(storage);
            }
            TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(canExtract, canReceive);
            if (direction == null) {
                throw new TowerEnergyTransferException("Energy endpoint no longer permits transfer: " + description());
            }
            if (stored < 0 || capacity < stored) {
                throw new TowerEnergyTransferException(
                        "Energy endpoint returned invalid frozen state " + stored + "/" + capacity + ": " + description());
            }
            return new TowerEnergyEndpointSnapshot(endpoint(), stored, capacity, direction);
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
    public long extract(long amount) {
        return transfer(amount, false, false);
    }

    @Override
    public long compensateExtraction(long amount) {
        validateAmount(amount);
        if (amount == 0) {
            return 0;
        }
        requireLoaded();
        IEnergyStorage storage = this.endpoint.storage();
        try {
            long restored;
            if (this.brandonsCore.supports(storage)) {
                restored = this.brandonsCore.canReceive(storage) ? this.brandonsCore.insert(storage, amount, false) : 0;
            } else {
                restored = this.unlimitedEnergy.rollbackExtraction(storage, amount);
                if (restored == UnlimitedEnergyAccess.UNAVAILABLE) {
                    restored = receiveThroughCapability(storage, amount, false);
                }
            }
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
    public long insert(long amount) {
        return transfer(amount, false, true);
    }

    @Override
    public void publishMutation() {
        requireLoaded();
        IEnergyStorage storage = this.endpoint.storage();
        if (!this.brandonsCore.supports(storage)) {
            this.unlimitedEnergy.notifyStorageChanged(storage);
        }
        BlockEntity blockEntity = this.endpoint.location().level().getBlockEntity(
                this.endpoint.location().position());
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    @Override
    public String description() {
        return this.endpoint.endpoint().dimensionId() + " " + this.endpoint.endpoint().pos() + " side=" + this.endpoint.endpoint().side() + " storage=" + this.endpoint.storage().getClass().getName();
    }

    /**
     * Selects the verified long-width path or a single bounded capability operation.
     */
    private long transfer(long amount, boolean simulate, boolean inserting) {
        validateAmount(amount);
        if (amount == 0) {
            return 0;
        }
        requireLoaded();
        IEnergyStorage storage = this.endpoint.storage();
        try {
            long transferred;
            if (this.brandonsCore.supports(storage)) {
                transferred = inserting ? this.brandonsCore.insert(storage, amount, simulate) : this.brandonsCore.extract(storage, amount, simulate);
            } else {
                transferred = inserting ? this.unlimitedEnergy.insert(storage, amount, simulate) : this.unlimitedEnergy.extract(storage, amount, simulate);
                if (transferred == UnlimitedEnergyAccess.UNAVAILABLE) {
                    transferred = inserting ? receiveThroughCapability(storage, amount, simulate) : extractThroughCapability(storage, amount, simulate);
                }
            }
            return validateResult(inserting ? "insert" : "extract", amount, transferred);
        } catch (TowerEnergyTransferException exception) {
            throw exception;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            throw new TowerEnergyTransferException(
                    "Could not " + (inserting ? "insert into " : "extract from ") + description(), exception);
        }
    }

    /**
     * Uses one public capability call because unknown implementations expose only int-width state and capacity.
     */
    private static long receiveThroughCapability(IEnergyStorage storage, long amount, boolean simulate) {
        if (!storage.canReceive()) {
            return 0;
        }
        if (amount > Integer.MAX_VALUE) {
            throw new TowerEnergyTransferException(
                    "Int-width energy receiver cannot accept one long-width transaction of " + amount + " FE");
        }
        return storage.receiveEnergy((int) amount, simulate);
    }

    /**
     * Uses one public capability call because unknown implementations expose only int-width state and capacity.
     */
    private static long extractThroughCapability(IEnergyStorage storage, long amount, boolean simulate) {
        if (!storage.canExtract()) {
            return 0;
        }
        if (amount > Integer.MAX_VALUE) {
            throw new TowerEnergyTransferException(
                    "Int-width energy source cannot provide one long-width transaction of " + amount + " FE");
        }
        return storage.extractEnergy((int) amount, simulate);
    }

    /** Ensures no capability query can force-load an unloaded chunk. */
    private void requireLoaded() {
        if (!this.endpoint.location().level().isLoaded(this.endpoint.location().position())) {
            throw new TowerEnergyTransferException("Energy endpoint chunk unloaded: " + description());
        }
    }

    /** Rejects negative requests before invoking third-party code. */
    private static void validateAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Tower energy transfer amount must not be negative");
        }
    }

    /** Rejects invalid third-party transfer responses at the boundary. */
    private static long validateResult(String operation, long requested, long actual) {
        if (actual < 0 || actual > requested) {
            throw new TowerEnergyTransferException(
                    "Energy endpoint returned invalid " + operation + " result " + actual + " for " + requested);
        }
        return actual;
    }
}
