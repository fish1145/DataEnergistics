package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default active FE balancing implementation for Data Distribution Towers.
 */
public final class TowerEnergyDistributorImpl implements TowerEnergyDistributor {

    private static final int MAX_CURSOR_ENTRIES = 128;

    private final TowerEnergyDistributorContext context;
    private final TowerEnergyEndpointResolver endpointResolver;
    private final UnlimitedEnergyAccess unlimitedEnergyAccess;
    private final TowerGridEnergyAccess gridEnergyAccess;
    private final boolean appFluxEnergySupportLoaded;
    private final Map<BlockPos, EnergyQuerySummary> cachedExtractQuerySummaries = new HashMap<>();
    private final Map<BlockPos, ReceiverQuerySummary> cachedReceiveQuerySummaries = new HashMap<>();
    private final Map<BlockPos, Integer> extractRoundRobinCursor = new HashMap<>();
    private final Map<BlockPos, Integer> receiveRoundRobinCursor = new HashMap<>();
    private final Map<ExtractSimulationKey, Integer> cachedSimulatedExtracts = new HashMap<>();
    private long cachedSimulatedExtractTick = Long.MIN_VALUE;
    private int activeSourceCursor;

    /**
     * Creates an active FE distributor.
     *
     * @param context               owning tower callbacks
     * @param endpointResolver      endpoint resolver used for side probing and caching
     * @param unlimitedEnergyAccess rate-limit-free storage access bridge
     */
    public TowerEnergyDistributorImpl(TowerEnergyDistributorContext context,
                                      TowerEnergyEndpointResolver endpointResolver,
                                      UnlimitedEnergyAccess unlimitedEnergyAccess) {
        this(context, endpointResolver, unlimitedEnergyAccess, ModFlags.isAppFluxEnergySupportLoaded(),
                new TowerGridEnergyAccessImpl());
    }

    TowerEnergyDistributorImpl(TowerEnergyDistributorContext context,
                               TowerEnergyEndpointResolver endpointResolver,
                               UnlimitedEnergyAccess unlimitedEnergyAccess,
                               boolean appFluxEnergySupportLoaded) {
        this(context, endpointResolver, unlimitedEnergyAccess, appFluxEnergySupportLoaded,
                new TowerGridEnergyAccessImpl());
    }

    TowerEnergyDistributorImpl(TowerEnergyDistributorContext context,
                               TowerEnergyEndpointResolver endpointResolver,
                               UnlimitedEnergyAccess unlimitedEnergyAccess,
                               boolean appFluxEnergySupportLoaded,
                               TowerGridEnergyAccess gridEnergyAccess) {
        this.context = context;
        this.endpointResolver = endpointResolver;
        this.unlimitedEnergyAccess = unlimitedEnergyAccess;
        this.appFluxEnergySupportLoaded = appFluxEnergySupportLoaded;
        this.gridEnergyAccess = gridEnergyAccess;
    }

    @Override
    public void performActiveRangeTransfer() {
        if (!this.context.isTowerActive()) {
            return;
        }

        flushBufferedEnergy();
        if (this.context.bufferedTransferEnergy() > 0) {
            return;
        }

        List<TowerEnergyEndpoint> receiveEndpoints = this.endpointResolver.getCachedResolvedEnergyEndpoints(true);
        if (receiveEndpoints.isEmpty()) {
            return;
        }

        ArrayList<TransferSource> sources = createTransferSources();
        if (sources.isEmpty()) {
            return;
        }

        int sourceCount = sources.size();
        int startIndex = Math.floorMod(this.activeSourceCursor, sourceCount);
        Set<IEnergyStorage> stalledReceiveStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean madeProgress;
        do {
            madeProgress = false;
            for (int offset = 0; offset < sourceCount; offset++) {
                TransferSource source = sources.get((startIndex + offset) % sourceCount);
                if (source.stalled || source.remainingQuota <= 0) {
                    continue;
                }

                try {
                    long inserted = transferSourceOnce(source, receiveEndpoints, stalledReceiveStorages);
                    madeProgress |= inserted > 0;
                } catch (RuntimeException | LinkageError exception) {
                    source.stalled = true;
                    Data_Energistics.LOGGER.error("Unlimited tower transfer failed for source {}", source.description(), exception);
                }
                if (this.context.bufferedTransferEnergy() > 0) {
                    this.activeSourceCursor = (startIndex + offset + 1) % sourceCount;
                    return;
                }
            }
        } while (madeProgress && hasActiveSource(sources));

        this.activeSourceCursor = (startIndex + 1) % sourceCount;
    }

    @Override
    public void flushBufferedEnergy() {
        long bufferedEnergy = this.context.bufferedTransferEnergy();
        if (!this.context.isTowerActive() || bufferedEnergy <= 0) {
            return;
        }

        long inserted = distributeEnergyInRange(bufferedEnergy, false, null);
        consumeBufferedEnergy(inserted);
    }

    private ArrayList<TransferSource> createTransferSources() {
        ArrayList<TransferSource> sources = new ArrayList<>();
        if (this.appFluxEnergySupportLoaded) {
            try {
                long quota = this.gridEnergyAccess.extract(this.context.aeNetworkHost(), Long.MAX_VALUE, true);
                if (quota > 0) {
                    sources.add(new TransferSource(null, quota));
                } else if (quota < 0) {
                    Data_Energistics.LOGGER.error("AppFlux returned an invalid simulated extraction amount: {}", quota);
                }
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error("Failed to freeze the AppFlux source quota for unlimited tower transfer",
                        exception);
            }
        }

        for (TowerEnergyEndpoint endpoint : this.endpointResolver.getCachedResolvedEnergyEndpoints(false)) {
            try {
                IEnergyStorage storage = endpoint.storage();
                if (!this.unlimitedEnergyAccess.canExtract(storage)) {
                    continue;
                }
                long quota = this.unlimitedEnergyAccess.stored(storage);
                if (quota > 0) {
                    sources.add(new TransferSource(endpoint, quota));
                }
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error("Failed to freeze unlimited tower source quota at {} side {} storage {}",
                        endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(), exception);
            }
        }
        return sources;
    }

    private long transferSourceOnce(TransferSource source, List<TowerEnergyEndpoint> receiveEndpoints,
                                    Set<IEnergyStorage> stalledReceiveStorages) {
        long simulatedExtract = source.extract(source.remainingQuota, true);
        if (!isValidTransferResult(source, "simulate extract", source.remainingQuota, simulatedExtract)) {
            return 0;
        }
        if (simulatedExtract == 0 || source.stalled) {
            source.stalled = true;
            return 0;
        }

        long simulatedInsert = distributeEnergyInRange(
                simulatedExtract, true, source.excludedPos(), receiveEndpoints, stalledReceiveStorages);
        if (simulatedInsert <= 0) {
            source.stalled = true;
            return 0;
        }

        long requested = Math.min(simulatedExtract, simulatedInsert);
        long extracted = source.extract(requested, false);
        if (!isValidTransferResult(source, "extract", requested, extracted) || extracted == 0) {
            source.stalled = true;
            return 0;
        }

        addBufferedEnergy(extracted);

        long inserted = distributeEnergyInRange(
                extracted, false, source.excludedPos(), receiveEndpoints, stalledReceiveStorages);
        consumeBufferedEnergy(inserted);
        long restored = 0L;
        if (inserted != extracted) {
            source.stalled = true;
            restored = rollbackUndeliveredEnergy(source, extracted - inserted);
            consumeBufferedEnergy(restored);
            Data_Energistics.LOGGER.error(
                    "Unlimited tower transfer inserted {} of {} FE from source {}, restored {} FE, and retained {} FE; stopping this source",
                    inserted, extracted, source.description(), restored, this.context.bufferedTransferEnergy());
        }
        source.remainingQuota -= extracted - restored;
        return inserted;
    }

    private void addBufferedEnergy(long amount) {
        if (amount <= 0) {
            return;
        }
        setBufferedEnergy(Math.addExact(this.context.bufferedTransferEnergy(), amount));
    }

    private void consumeBufferedEnergy(long amount) {
        if (amount <= 0) {
            return;
        }
        long bufferedEnergy = this.context.bufferedTransferEnergy();
        if (amount > bufferedEnergy) {
            throw new IllegalStateException(
                    "Cannot consume " + amount + " FE from a " + bufferedEnergy + " FE tower transfer buffer");
        }
        setBufferedEnergy(bufferedEnergy - amount);
    }

    private void setBufferedEnergy(long amount) {
        this.context.setBufferedTransferEnergy(amount);
        invalidateEnergyQueryCache();
    }

    private long rollbackUndeliveredEnergy(TransferSource source, long amount) {
        long restored;
        try {
            restored = source.rollbackExtraction(amount);
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error(
                    "Unlimited tower could not compensate {} FE on source {}", amount, source.description(), exception);
            return 0L;
        }
        if (restored == UnlimitedEnergyAccess.UNAVAILABLE) {
            Data_Energistics.LOGGER.error(
                    "Unlimited tower source {} has no verified compensation path for {} FE",
                    source.description(), amount);
            return 0L;
        }
        if (restored < 0L || restored > amount) {
            Data_Energistics.LOGGER.error(
                    "Unlimited tower source {} returned invalid compensation {} for {} FE",
                    source.description(), restored, amount);
            return 0L;
        }
        if (restored != amount) {
            Data_Energistics.LOGGER.error(
                    "Unlimited tower source {} restored only {} of {} undelivered FE",
                    source.description(), restored, amount);
        }
        return restored;
    }

    private boolean isValidTransferResult(TransferSource source, String operation, long requested, long result) {
        if (result >= 0 && result <= requested) {
            return true;
        }
        source.stalled = true;
        Data_Energistics.LOGGER.error("Unlimited tower {} returned {} for request {} at source {}",
                operation, result, requested, source.description());
        return false;
    }

    private static boolean hasActiveSource(List<TransferSource> sources) {
        for (TransferSource source : sources) {
            if (!source.stalled && source.remainingQuota > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        Set<IEnergyStorage> stalledReceiveStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        return distributeEnergyInRange(amount, simulate, excludedPos,
                this.endpointResolver.collectClusterEnergyEndpoints(true), stalledReceiveStorages);
    }

    @Override
    public int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (simulate) {
            return getCachedSimulatedExtract(amount, excludedPos);
        }
        return clampStoredAmount(extractEnergyFromRangeLong(amount, false, excludedPos));
    }

    @Override
    public long getTotalExtractableEnergy(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).totalStored();
    }

    @Override
    public long getTotalEnergyCapacity(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).totalCapacity();
    }

    @Override
    public boolean hasAnyReceiver(@Nullable BlockPos excludedPos) {
        return getReceiveQuerySummary(excludedPos).hasReceiver();
    }

    @Override
    public boolean hasAnySource(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).hasSource();
    }

    @Override
    public void invalidateEnergyQueryCache() {
        this.cachedExtractQuerySummaries.clear();
        this.cachedReceiveQuerySummaries.clear();
        this.cachedSimulatedExtracts.clear();
        this.cachedSimulatedExtractTick = Long.MIN_VALUE;
    }

    @Override
    public void invalidateResolvedEndpointCache() {
        this.extractRoundRobinCursor.clear();
        this.receiveRoundRobinCursor.clear();
        invalidateEnergyQueryCache();
    }

    @Override
    public void trimCaches() {
        if (this.extractRoundRobinCursor.size() > MAX_CURSOR_ENTRIES) {
            this.extractRoundRobinCursor.clear();
        }
        if (this.receiveRoundRobinCursor.size() > MAX_CURSOR_ENTRIES) {
            this.receiveRoundRobinCursor.clear();
        }
        if (this.cachedExtractQuerySummaries.size() > MAX_CURSOR_ENTRIES) {
            this.cachedExtractQuerySummaries.clear();
        }
        if (this.cachedReceiveQuerySummaries.size() > MAX_CURSOR_ENTRIES) {
            this.cachedReceiveQuerySummaries.clear();
        }
    }

    private long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos,
                                         List<TowerEnergyEndpoint> receiveEndpoints,
                                         Set<IEnergyStorage> stalledReceiveStorages) {
        if (!this.context.isTowerActive() || amount <= 0) {
            return 0;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeReceiveExcludedPos(excludedPos);
        List<TowerEnergyEndpoint> endpoints = excludeEnergyEndpoint(receiveEndpoints, normalizedExcludedPos);
        this.context.recordMaxReceiveEndpoints(endpoints.size());
        if (endpoints.isEmpty()) {
            return 0;
        }

        long totalInserted = 0;
        long remaining = amount;
        int endpointCount = endpoints.size();
        int startIndex = getReceiveStartIndex(normalizedExcludedPos, endpointCount);
        int lastSuccessfulIndex = -1;
        for (int offset = 0; offset < endpointCount; offset++) {
            if (remaining <= 0) {
                break;
            }

            int endpointIndex = (startIndex + offset) % endpointCount;
            TowerEnergyEndpoint endpoint = endpoints.get(endpointIndex);
            IEnergyStorage storage = endpoint.storage();
            if (stalledReceiveStorages.contains(storage)) {
                continue;
            }

            EndpointTransferResult result;
            try {
                if (!this.endpointResolver.canReceiveEnergy(storage)) {
                    stalledReceiveStorages.add(storage);
                    continue;
                }
                result = insertEnergyIntoEndpoint(endpoint, remaining, simulate);
            } catch (RuntimeException | LinkageError exception) {
                stalledReceiveStorages.add(storage);
                Data_Energistics.LOGGER.error("Unlimited tower receiver failed at {} side {} storage {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
                continue;
            }
            long inserted = result.amount();
            if (inserted < 0 || inserted > remaining) {
                stalledReceiveStorages.add(storage);
                Data_Energistics.LOGGER.error(
                        "Unlimited tower receiver at {} side {} storage {} returned {} for request {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), inserted, remaining);
                continue;
            }
            if (result.stalled() || inserted == 0) {
                stalledReceiveStorages.add(storage);
            }
            if (inserted > 0) {
                totalInserted += inserted;
                remaining -= inserted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (!simulate && lastSuccessfulIndex >= 0) {
            this.receiveRoundRobinCursor.put(normalizedExcludedPos, (lastSuccessfulIndex + 1) % endpointCount);
        }

        if (!simulate && totalInserted > 0) {
            invalidateEnergyQueryCache();
        }
        return totalInserted;
    }

    private EndpointTransferResult insertEnergyIntoEndpoint(TowerEnergyEndpoint endpoint, long amount, boolean simulate) {
        if (amount <= 0) {
            return EndpointTransferResult.STALLED;
        }

        IEnergyStorage storage = endpoint.storage();
        long directInserted = this.unlimitedEnergyAccess.insert(storage, amount, simulate);
        if (directInserted != UnlimitedEnergyAccess.UNAVAILABLE) {
            boolean stalled = directInserted == 0;
            if (!simulate && directInserted > 0) {
                try {
                    this.unlimitedEnergyAccess.notifyStorageChanged(storage);
                    this.context.markEndpointChanged(endpoint.pos());
                } catch (RuntimeException | LinkageError exception) {
                    stalled = true;
                    Data_Energistics.LOGGER.error(
                            "Failed to publish unlimited tower receiver mutation at {} side {} storage {}",
                            endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
                }
            }
            return new EndpointTransferResult(directInserted, stalled);
        }
        return insertThroughCapability(endpoint, amount, simulate);
    }

    private EndpointTransferResult insertThroughCapability(TowerEnergyEndpoint endpoint, long amount, boolean simulate) {
        IEnergyStorage storage = endpoint.storage();
        long insertedTotal = 0;
        long remaining = simulate ? Math.min(amount, getCapabilityInsertionSpace(endpoint)) : amount;
        if (remaining <= 0) {
            return EndpointTransferResult.STALLED;
        }
        while (remaining > 0) {
            int request = clampEnergyRequest(remaining);
            int inserted;
            try {
                inserted = storage.receiveEnergy(request, simulate);
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error(
                        "Energy receiver at {} side {} failed after accepting {} FE through its capability",
                        endpoint.pos(), endpoint.side(), insertedTotal, exception);
                return new EndpointTransferResult(insertedTotal, true);
            }
            if (inserted < 0 || inserted > request) {
                Data_Energistics.LOGGER.error("Energy receiver at {} side {} returned {} for request {}",
                        endpoint.pos(), endpoint.side(), inserted, request);
                return new EndpointTransferResult(insertedTotal, true);
            }
            if (inserted == 0) {
                return new EndpointTransferResult(insertedTotal, true);
            }
            insertedTotal += inserted;
            remaining -= inserted;
        }
        return new EndpointTransferResult(insertedTotal, false);
    }

    private long getCapabilityInsertionSpace(TowerEnergyEndpoint endpoint) {
        IEnergyStorage storage = endpoint.storage();
        try {
            int stored = storage.getEnergyStored();
            int capacity = storage.getMaxEnergyStored();
            if (stored < 0 || capacity < stored) {
                Data_Energistics.LOGGER.error(
                        "Energy receiver at {} side {} storage {} reported invalid state {}/{}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), stored, capacity);
                return 0;
            }
            return (long) capacity - stored;
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Energy receiver state query failed at {} side {} storage {}",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
            return 0;
        }
    }

    private long extractEnergyFromEndpoint(TowerEnergyEndpoint endpoint, long amount, boolean simulate) {
        return extractEnergyFromEndpointResult(endpoint, amount, simulate).amount();
    }

    private EndpointTransferResult extractEnergyFromEndpointResult(
                                                                   TowerEnergyEndpoint endpoint,
                                                                   long amount,
                                                                   boolean simulate) {
        if (amount <= 0) {
            return EndpointTransferResult.STALLED;
        }

        IEnergyStorage storage = endpoint.storage();
        long directExtracted;
        try {
            directExtracted = this.unlimitedEnergyAccess.extract(storage, amount, simulate);
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error(
                    "Unlimited energy source mutation failed at {} side {} storage {}",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
            return EndpointTransferResult.STALLED;
        }
        if (directExtracted != UnlimitedEnergyAccess.UNAVAILABLE) {
            if (directExtracted < 0 || directExtracted > amount) {
                Data_Energistics.LOGGER.error(
                        "Unlimited energy source at {} side {} storage {} returned {} for request {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), directExtracted, amount);
                return EndpointTransferResult.STALLED;
            }
            boolean stalled = directExtracted == 0;
            if (!simulate && directExtracted > 0) {
                try {
                    this.unlimitedEnergyAccess.notifyStorageChanged(storage);
                    this.context.markEndpointChanged(endpoint.pos());
                } catch (RuntimeException | LinkageError exception) {
                    stalled = true;
                    Data_Energistics.LOGGER.error(
                            "Failed to publish unlimited tower source mutation at {} side {} storage {}",
                            endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
                }
            }
            return new EndpointTransferResult(directExtracted, stalled);
        }

        long extractedTotal = 0;
        long remaining = simulate ? Math.min(amount, getCapabilityStoredEnergy(endpoint)) : amount;
        if (remaining <= 0) {
            return EndpointTransferResult.STALLED;
        }
        while (remaining > 0) {
            int request = clampEnergyRequest(remaining);
            int extracted;
            try {
                extracted = storage.extractEnergy(request, simulate);
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error(
                        "Energy source at {} side {} failed after providing {} FE through its capability",
                        endpoint.pos(), endpoint.side(), extractedTotal, exception);
                return new EndpointTransferResult(extractedTotal, true);
            }
            if (extracted < 0 || extracted > request) {
                Data_Energistics.LOGGER.error("Energy source at {} side {} returned {} for request {}",
                        endpoint.pos(), endpoint.side(), extracted, request);
                return new EndpointTransferResult(extractedTotal, true);
            }
            if (extracted == 0) {
                return new EndpointTransferResult(extractedTotal, true);
            }
            extractedTotal += extracted;
            remaining -= extracted;
        }
        return new EndpointTransferResult(extractedTotal, false);
    }

    private long getCapabilityStoredEnergy(TowerEnergyEndpoint endpoint) {
        IEnergyStorage storage = endpoint.storage();
        try {
            int stored = storage.getEnergyStored();
            if (stored < 0) {
                Data_Energistics.LOGGER.error("Energy source at {} side {} storage {} reported negative stored energy {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), stored);
                return 0;
            }
            return stored;
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Energy source state query failed at {} side {} storage {}",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
            return 0;
        }
    }

    private int getCachedSimulatedExtract(int amount, @Nullable BlockPos excludedPos) {
        Level level = this.context.level();
        if (amount <= 0) {
            return 0;
        }
        if (level == null) {
            return clampStoredAmount(extractEnergyFromRangeLong(amount, true, excludedPos));
        }

        long gameTime = level.getGameTime();
        if (this.cachedSimulatedExtractTick != gameTime) {
            this.cachedSimulatedExtracts.clear();
            this.cachedSimulatedExtractTick = gameTime;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeExtractExcludedPos(excludedPos);
        ExtractSimulationKey key = new ExtractSimulationKey(normalizedExcludedPos, amount);
        Integer cached = this.cachedSimulatedExtracts.get(key);
        if (cached != null) {
            this.context.recordSimulatedCacheHit();
            return cached;
        }

        this.context.recordSimulatedCacheMiss();
        int simulated = clampStoredAmount(extractEnergyFromRangeLong(amount, true, normalizedExcludedPos));
        this.cachedSimulatedExtracts.put(key, simulated);
        return simulated;
    }

    private long extractEnergyFromRangeLong(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (amount <= 0) {
            return 0;
        }

        long bufferedEnergy = this.context.bufferedTransferEnergy();
        long bufferedExtracted = Math.min(amount, bufferedEnergy);
        if (!simulate) {
            consumeBufferedEnergy(bufferedExtracted);
        }
        if (bufferedExtracted == amount || !this.context.isTowerActive()) {
            return bufferedExtracted;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeExtractExcludedPos(excludedPos);
        List<TowerEnergyEndpoint> endpoints = this.endpointResolver.collectEnergyEndpoints(false, normalizedExcludedPos);
        this.context.recordMaxExtractEndpoints(endpoints.size());
        long totalExtracted = bufferedExtracted;
        long remaining = amount - bufferedExtracted;

        if (this.appFluxEnergySupportLoaded) {
            long extracted = this.gridEnergyAccess.extract(this.context.aeNetworkHost(), remaining, simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
            }
        }

        int endpointCount = endpoints.size();
        int startIndex = getExtractStartIndex(normalizedExcludedPos, endpointCount);
        int lastSuccessfulIndex = -1;
        for (int offset = 0; offset < endpointCount; offset++) {
            if (remaining <= 0) {
                break;
            }

            int endpointIndex = (startIndex + offset) % endpointCount;
            TowerEnergyEndpoint endpoint = endpoints.get(endpointIndex);
            IEnergyStorage storage = endpoint.storage();
            if (!this.unlimitedEnergyAccess.canExtract(storage)) {
                continue;
            }

            long extracted = extractEnergyFromEndpoint(endpoint, remaining, simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (!simulate && lastSuccessfulIndex >= 0) {
            this.extractRoundRobinCursor.put(normalizedExcludedPos, (lastSuccessfulIndex + 1) % endpointCount);
        }

        if (!simulate && totalExtracted > 0) {
            invalidateEnergyQueryCache();
        }
        return totalExtracted;
    }

    private EnergyQuerySummary getExtractQuerySummary(@Nullable BlockPos excludedPos) {
        Level level = this.context.level();
        long bufferedEnergy = this.context.bufferedTransferEnergy();
        if (!this.context.isTowerActive() || level == null) {
            if (bufferedEnergy <= 0) {
                return EnergyQuerySummary.EMPTY;
            }
            return new EnergyQuerySummary(Long.MIN_VALUE, bufferedEnergy, bufferedEnergy, true);
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeExtractExcludedPos(excludedPos);
        long gameTime = level.getGameTime();
        EnergyQuerySummary cached = this.cachedExtractQuerySummaries.get(normalizedExcludedPos);
        if (cached != null && cached.tick() == gameTime) {
            return cached;
        }

        long totalStored = bufferedEnergy;
        long totalCapacity = bufferedEnergy;
        List<TowerEnergyEndpoint> endpoints = this.endpointResolver.collectEnergyEndpoints(false, normalizedExcludedPos);
        for (TowerEnergyEndpoint endpoint : endpoints) {
            totalStored = saturatingAdd(totalStored, this.unlimitedEnergyAccess.stored(endpoint.storage()));
            totalCapacity = saturatingAdd(totalCapacity, this.unlimitedEnergyAccess.capacity(endpoint.storage()));
        }
        long aeExtractable = 0L;
        if (this.appFluxEnergySupportLoaded) {
            aeExtractable = this.gridEnergyAccess.extract(this.context.aeNetworkHost(), Long.MAX_VALUE, true);
            totalStored = saturatingAdd(totalStored, aeExtractable);
        }

        EnergyQuerySummary summary = new EnergyQuerySummary(
                gameTime, totalStored, totalCapacity, bufferedEnergy > 0 || !endpoints.isEmpty() || aeExtractable > 0);
        this.cachedExtractQuerySummaries.put(normalizedExcludedPos, summary);
        return summary;
    }

    private ReceiverQuerySummary getReceiveQuerySummary(@Nullable BlockPos excludedPos) {
        Level level = this.context.level();
        if (!this.context.isTowerActive() || level == null) {
            return ReceiverQuerySummary.EMPTY;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeReceiveExcludedPos(excludedPos);
        long gameTime = level.getGameTime();
        ReceiverQuerySummary cached = this.cachedReceiveQuerySummaries.get(normalizedExcludedPos);
        if (cached != null && cached.tick() == gameTime) {
            return cached;
        }

        ReceiverQuerySummary summary = new ReceiverQuerySummary(gameTime, !this.endpointResolver.collectEnergyEndpoints(true, normalizedExcludedPos).isEmpty());
        this.cachedReceiveQuerySummaries.put(normalizedExcludedPos, summary);
        return summary;
    }

    private List<TowerEnergyEndpoint> excludeEnergyEndpoint(List<TowerEnergyEndpoint> endpoints, @Nullable BlockPos excludedPos) {
        if (excludedPos == null || endpoints.isEmpty()) {
            return endpoints;
        }

        ArrayList<TowerEnergyEndpoint> filtered = new ArrayList<>(endpoints.size());
        for (TowerEnergyEndpoint endpoint : endpoints) {
            if (!excludedPos.equals(endpoint.pos())) {
                filtered.add(endpoint);
            }
        }
        return filtered;
    }

    private int getExtractStartIndex(@Nullable BlockPos excludedPos, int endpointCount) {
        if (endpointCount <= 0) {
            return 0;
        }
        return Math.floorMod(this.extractRoundRobinCursor.getOrDefault(excludedPos, 0), endpointCount);
    }

    private int getReceiveStartIndex(@Nullable BlockPos excludedPos, int endpointCount) {
        if (endpointCount <= 0) {
            return 0;
        }
        return Math.floorMod(this.receiveRoundRobinCursor.getOrDefault(excludedPos, 0), endpointCount);
    }

    private static int clampEnergyRequest(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static int clampStoredAmount(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static long saturatingAdd(long current, long delta) {
        if (delta <= 0) {
            return current;
        }
        if (Long.MAX_VALUE - current < delta) {
            return Long.MAX_VALUE;
        }
        return current + delta;
    }

    private record ExtractSimulationKey(@Nullable BlockPos excludedPos, int amount) {}

    private record EndpointTransferResult(long amount, boolean stalled) {

        private static final EndpointTransferResult STALLED = new EndpointTransferResult(0L, true);
    }

    private record EnergyQuerySummary(long tick, long totalStored, long totalCapacity, boolean hasSource) {

        private static final EnergyQuerySummary EMPTY = new EnergyQuerySummary(Long.MIN_VALUE, 0L, 0L, false);
    }

    private record ReceiverQuerySummary(long tick, boolean hasReceiver) {

        private static final ReceiverQuerySummary EMPTY = new ReceiverQuerySummary(Long.MIN_VALUE, false);
    }

    private final class TransferSource {

        @Nullable
        private final TowerEnergyEndpoint endpoint;
        private long remainingQuota;
        private boolean stalled;

        private TransferSource(@Nullable TowerEnergyEndpoint endpoint, long remainingQuota) {
            this.endpoint = endpoint;
            this.remainingQuota = remainingQuota;
        }

        private long extract(long amount, boolean simulate) {
            if (this.endpoint == null) {
                return TowerEnergyDistributorImpl.this.gridEnergyAccess.extract(
                        TowerEnergyDistributorImpl.this.context.aeNetworkHost(), amount, simulate);
            }
            EndpointTransferResult result = TowerEnergyDistributorImpl.this.extractEnergyFromEndpointResult(
                    this.endpoint, amount, simulate);
            this.stalled |= result.stalled();
            return result.amount();
        }

        private long rollbackExtraction(long amount) {
            if (this.endpoint == null) {
                return TowerEnergyDistributorImpl.this.gridEnergyAccess.restore(
                        TowerEnergyDistributorImpl.this.context.aeNetworkHost(), amount);
            }

            IEnergyStorage storage = this.endpoint.storage();
            long restored = TowerEnergyDistributorImpl.this.unlimitedEnergyAccess.rollbackExtraction(storage, amount);
            if (restored > 0) {
                try {
                    TowerEnergyDistributorImpl.this.unlimitedEnergyAccess.notifyStorageChanged(storage);
                    TowerEnergyDistributorImpl.this.context.markEndpointChanged(this.endpoint.pos());
                } catch (RuntimeException | LinkageError exception) {
                    Data_Energistics.LOGGER.error(
                            "Failed to publish unlimited tower source compensation at {} side {} storage {}",
                            this.endpoint.pos(), this.endpoint.side(), storage.getClass().getName(), exception);
                }
            }
            return restored;
        }

        @Nullable
        private BlockPos excludedPos() {
            return this.endpoint == null ? null : this.endpoint.pos();
        }

        private String description() {
            return this.endpoint == null ? "AppFlux network" : this.endpoint.pos() + " side=" + this.endpoint.side() + " storage=" + this.endpoint.storage().getClass().getName();
        }
    }
}
