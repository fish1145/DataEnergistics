package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.appflux.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess.EnergySnapshot;
import com.fish_dan_.data_energistics.integration.energy.VerifiedUnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.modernindustrialization.ModernIndustrializationEnergyStorage;
import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.MekanismEnergyAccess;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Capability-backed transaction endpoint with long-width typed access and a public-API fallback.
 */
public final class CapabilityEnergyTransferEndpoint implements TowerEnergyTransferEndpoint {

    /**
     * Resolved capability and loaded owner location.
     */
    private final TowerDomainEnergyEndpoint endpoint;

    /**
     * Optional BrandonsCore long-width bridge.
     */
    private final BrandonsCoreEnergyBridge brandonsCore = new BrandonsCoreEnergyBridge();

    /**
     * Verified typed direct access for standard and explicitly unlimited storages.
     */
    private final UnlimitedEnergyAccess unlimitedEnergy = new VerifiedUnlimitedEnergyAccess();

    /**
     * Stable BrandonsCore storage classification for this immutable topology route.
     */
    private final boolean brandonsCoreStorage;

    /**
     * Stable Applied Flux storage classification for this immutable topology route.
     */
    private final boolean appFluxStorage;

    /**
     * Creates an executable endpoint from one topology resolution.
     *
     * @param endpoint resolved capability endpoint
     */
    public CapabilityEnergyTransferEndpoint(TowerDomainEnergyEndpoint endpoint) {
        this.endpoint = endpoint;
        this.brandonsCoreStorage = this.brandonsCore.supports(endpoint.storage());
        this.appFluxStorage = ModFlags.isAppFluxEnergySupportLoaded() && AE2FluxIntegration.isNetworkEnergyStorage(endpoint.storage());
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
            long stored;
            long capacity;
            long extractable;
            long receivable;
            long appFluxFree = 0;
            boolean canExtract;
            boolean canReceive;
            if (!this.brandonsCoreStorage && mekanismSupported(storage)) {
                return MekanismEnergyAccess.freeze(
                        this.endpoint.location().level(),
                        this.endpoint.location().position(),
                        endpoint().side(),
                        storage,
                        endpoint());
            }
            if (this.brandonsCoreStorage) {
                stored = this.brandonsCore.stored(storage);
                capacity = this.brandonsCore.capacity(storage);
                canExtract = this.brandonsCore.canExtract(storage);
                canReceive = this.brandonsCore.canReceive(storage);
            } else if (this.appFluxStorage) {
                stored = AE2FluxIntegration.extractEnergyFromNetworkStorage(storage, Long.MAX_VALUE, true);
                appFluxFree = AE2FluxIntegration.insertEnergyIntoNetworkStorage(storage, Long.MAX_VALUE, true);
                capacity = saturatingAdd(stored, appFluxFree);
                canExtract = storage.canExtract();
                canReceive = storage.canReceive();
            } else if (storage instanceof ModernIndustrializationEnergyStorage modernIndustrializationStorage) {
                EnergySnapshot snapshot = modernIndustrializationStorage.snapshot();
                stored = snapshot.stored();
                capacity = snapshot.capacity();
                canExtract = modernIndustrializationStorage.canExtract();
                canReceive = modernIndustrializationStorage.canReceive();
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
            long free = capacity - stored;
            if (this.appFluxStorage) {
                extractable = canExtract ? stored : 0;
                receivable = canReceive ? Math.min(appFluxFree, free) : 0;
            } else {
                extractable = canExtract ? captureBudget(storage, stored, false) : 0;
                receivable = canReceive ? captureBudget(storage, free, true) : 0;
            }
            return new TowerEnergyEndpointSnapshot(
                    endpoint(), stored, capacity, extractable, receivable, direction);
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
            if (this.brandonsCoreStorage) {
                restored = this.brandonsCore.canReceive(storage) ? this.brandonsCore.insert(storage, amount, false) : 0;
            } else if (mekanismSupported(storage)) {
                restored = MekanismEnergyAccess.compensateExtraction(
                        this.endpoint.location().level(),
                        this.endpoint.location().position(),
                        storage,
                        amount);
            } else if (this.appFluxStorage) {
                restored = AE2FluxIntegration.insertEnergyIntoNetworkStorage(storage, amount, false);
            } else if (storage instanceof ModernIndustrializationEnergyStorage modernIndustrializationStorage) {
                restored = modernIndustrializationStorage.compensateExtraction(amount);
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
        if (this.appFluxStorage) {
            return;
        }
        requireLoaded();
        IEnergyStorage storage = this.endpoint.storage();
        if (!this.brandonsCoreStorage) {
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
            if (this.brandonsCoreStorage) {
                transferred = inserting ? this.brandonsCore.insert(storage, amount, simulate) : this.brandonsCore.extract(storage, amount, simulate);
            } else if (mekanismSupported(storage)) {
                transferred = inserting ? MekanismEnergyAccess.insert(
                        this.endpoint.location().level(),
                        this.endpoint.location().position(),
                        endpoint().side(),
                        storage,
                        amount,
                        simulate) :
                        MekanismEnergyAccess.extract(
                                this.endpoint.location().level(),
                                this.endpoint.location().position(),
                                endpoint().side(),
                                storage,
                                amount,
                                simulate);
            } else if (this.appFluxStorage) {
                transferred = inserting ? AE2FluxIntegration.insertEnergyIntoNetworkStorage(storage, amount, simulate) : AE2FluxIntegration.extractEnergyFromNetworkStorage(storage, amount, simulate);
            } else if (storage instanceof ModernIndustrializationEnergyStorage modernIndustrializationStorage) {
                transferred = inserting ? modernIndustrializationStorage.insert(amount, simulate) : modernIndustrializationStorage.extract(amount, simulate);
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
     * Captures one endpoint's maximum public or verified direct transfer commitment.
     */
    private long captureBudget(IEnergyStorage storage, long available, boolean inserting) {
        if (available == 0) {
            return 0;
        }
        long transferred;
        if (this.brandonsCoreStorage) {
            transferred = inserting ? this.brandonsCore.insert(storage, available, true) : this.brandonsCore.extract(storage, available, true);
        } else if (storage instanceof ModernIndustrializationEnergyStorage modernIndustrializationStorage) {
            transferred = inserting ? modernIndustrializationStorage.insert(available, true) : modernIndustrializationStorage.extract(available, true);
        } else {
            transferred = inserting ? this.unlimitedEnergy.insert(storage, available, true) : this.unlimitedEnergy.extract(storage, available, true);
            if (transferred == UnlimitedEnergyAccess.UNAVAILABLE) {
                long publicRequest = Math.min(available, Integer.MAX_VALUE);
                transferred = inserting ? receiveThroughCapability(storage, publicRequest, true) : extractThroughCapability(storage, publicRequest, true);
                return validateResult(inserting ? "capture insertion budget" : "capture extraction budget",
                        publicRequest,
                        transferred);
            }
        }
        return validateResult(inserting ? "capture insertion budget" : "capture extraction budget",
                available,
                transferred);
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

    /**
     * Checks whether the current route still has Mekanism long-width access.
     */
    private boolean mekanismSupported(IEnergyStorage storage) {
        return MekanismEnergyAccess.supports(
                this.endpoint.location().level(),
                this.endpoint.location().position(),
                endpoint().side(),
                storage);
    }

    /**
     * Ensures no capability query can force-load an unloaded chunk.
     */
    private void requireLoaded() {
        if (!this.endpoint.location().level().isLoaded(this.endpoint.location().position())) {
            throw new TowerEnergyTransferException("Energy endpoint chunk unloaded: " + description());
        }
    }

    /**
     * Rejects negative requests before invoking third-party code.
     */
    private static void validateAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Tower energy transfer amount must not be negative");
        }
    }

    /**
     * Rejects invalid third-party transfer responses at the boundary.
     */
    private static long validateResult(String operation, long requested, long actual) {
        if (actual < 0 || actual > requested) {
            throw new TowerEnergyTransferException(
                    "Energy endpoint returned invalid " + operation + " result " + actual + " for " + requested);
        }
        return actual;
    }

    /**
     * Adds non-negative energy components without wrapping.
     */
    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
