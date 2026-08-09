package com.fish_dan_.data_energistics.blockentity.tower.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess.EnergySnapshot;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessException;
import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.MekanismEnergyAccess;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs active FE balancing for a Data Distribution Tower cluster.
 *
 * <p>
 * The engine owns transfer scan caches, simulated extraction caches, and round-robin cursors so the block entity can
 * expose energy capability behavior without embedding the transfer algorithm.
 * </p>
 */
public final class TowerEnergyTransferEngine {

    private static final int MAX_CURSOR_ENTRIES = 128;

    private final TowerEnergyDistributorContext context;
    private final TowerEnergyEndpointResolver endpointResolver;
    private final TowerOpEnergyAccess opEnergyAccess;
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
    public TowerEnergyTransferEngine(TowerEnergyDistributorContext context,
                                     TowerEnergyEndpointResolver endpointResolver,
                                     UnlimitedEnergyAccess unlimitedEnergyAccess) {
        this(context, endpointResolver, new BrandonsCoreEnergyBridge(), unlimitedEnergyAccess,
                ModFlags.isAppFluxEnergySupportLoaded(),
                new AppFluxTowerGridEnergyAccess());
    }

    /**
     * Creates an active FE distributor with a shared BrandonsCore capability bridge.
     *
     * @param context                  owning tower callbacks
     * @param endpointResolver         endpoint resolver used for side probing and caching
     * @param brandonsCoreEnergyBridge direct long-width OP access
     * @param unlimitedEnergyAccess    rate-limit-free non-OP storage access
     */
    public TowerEnergyTransferEngine(TowerEnergyDistributorContext context,
                                     TowerEnergyEndpointResolver endpointResolver,
                                     BrandonsCoreEnergyBridge brandonsCoreEnergyBridge,
                                     UnlimitedEnergyAccess unlimitedEnergyAccess) {
        this(context, endpointResolver, brandonsCoreEnergyBridge, unlimitedEnergyAccess,
                ModFlags.isAppFluxEnergySupportLoaded(), new AppFluxTowerGridEnergyAccess());
    }

    TowerEnergyTransferEngine(TowerEnergyDistributorContext context,
                              TowerEnergyEndpointResolver endpointResolver,
                              UnlimitedEnergyAccess unlimitedEnergyAccess,
                              boolean appFluxEnergySupportLoaded) {
        this(context, endpointResolver, new BrandonsCoreEnergyBridge(), unlimitedEnergyAccess,
                appFluxEnergySupportLoaded,
                new AppFluxTowerGridEnergyAccess());
    }

    TowerEnergyTransferEngine(TowerEnergyDistributorContext context,
                              TowerEnergyEndpointResolver endpointResolver,
                              UnlimitedEnergyAccess unlimitedEnergyAccess,
                              boolean appFluxEnergySupportLoaded,
                              TowerGridEnergyAccess gridEnergyAccess) {
        this(context, endpointResolver, new BrandonsCoreEnergyBridge(), unlimitedEnergyAccess,
                appFluxEnergySupportLoaded, gridEnergyAccess);
    }

    TowerEnergyTransferEngine(TowerEnergyDistributorContext context,
                              TowerEnergyEndpointResolver endpointResolver,
                              BrandonsCoreEnergyBridge brandonsCoreEnergyBridge,
                              UnlimitedEnergyAccess unlimitedEnergyAccess,
                              boolean appFluxEnergySupportLoaded,
                              TowerGridEnergyAccess gridEnergyAccess) {
        this(context, endpointResolver, new BrandonsCoreTowerOpEnergyAccess(brandonsCoreEnergyBridge), unlimitedEnergyAccess,
                appFluxEnergySupportLoaded, gridEnergyAccess);
    }

    TowerEnergyTransferEngine(TowerEnergyDistributorContext context,
                              TowerEnergyEndpointResolver endpointResolver,
                              TowerOpEnergyAccess opEnergyAccess,
                              UnlimitedEnergyAccess unlimitedEnergyAccess,
                              boolean appFluxEnergySupportLoaded,
                              TowerGridEnergyAccess gridEnergyAccess) {
        this.context = context;
        this.endpointResolver = endpointResolver;
        this.opEnergyAccess = opEnergyAccess;
        this.unlimitedEnergyAccess = unlimitedEnergyAccess;
        this.appFluxEnergySupportLoaded = appFluxEnergySupportLoaded;
        this.gridEnergyAccess = gridEnergyAccess;
    }

    /**
     * Runs one active range transfer tick for the cluster coordinator.
     *
     * @return true when at least one FE transfer completed
     */
    public boolean performActiveRangeTransfer() {
        if (!this.context.isTowerActive() || this.context.quarantinedTransferEnergy() > 0) {
            return false;
        }

        boolean transferred = flushBufferedEnergy();
        if (this.context.bufferedTransferEnergy() > 0 || this.context.quarantinedTransferEnergy() > 0) {
            return transferred;
        }

        List<TowerEnergyEndpoint> receiveEndpoints = this.endpointResolver.getCachedResolvedEnergyEndpoints(true);
        if (receiveEndpoints.isEmpty()) {
            return transferred;
        }

        ArrayList<TransferSource> sources = createTransferSources();
        if (sources.isEmpty()) {
            return transferred;
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
                    transferred |= inserted > 0;
                } catch (Throwable exception) {
                    ThrowableIsolation.rethrowIfFatal(exception);
                    source.stalled = true;
                    Data_Energistics.LOGGER.error("Unlimited tower transfer failed for source {}", source.description(), exception);
                }
                if (this.context.bufferedTransferEnergy() > 0 || this.context.quarantinedTransferEnergy() > 0) {
                    this.activeSourceCursor = (startIndex + offset + 1) % sourceCount;
                    return transferred;
                }
            }
        } while (madeProgress && hasActiveSource(sources));

        this.activeSourceCursor = (startIndex + 1) % sourceCount;
        return transferred;
    }

    /**
     * Attempts to deliver energy retained by the owning tower after an incomplete transfer.
     *
     * @return true when buffered FE was delivered
     */
    public boolean flushBufferedEnergy() {
        long bufferedEnergy = this.context.bufferedTransferEnergy();
        if (!this.context.isTowerActive() || bufferedEnergy <= 0 || this.context.quarantinedTransferEnergy() > 0) {
            return false;
        }

        long inserted = distributeEnergyInRange(bufferedEnergy, false, null);
        consumeBufferedEnergy(inserted);
        return inserted > 0;
    }

    private ArrayList<TransferSource> createTransferSources() {
        ArrayList<TransferSource> sources = new ArrayList<>();
        if (this.appFluxEnergySupportLoaded) {
            long quota = extractGridEnergy(Long.MAX_VALUE, true, "active source quota");
            if (quota > 0) {
                sources.add(new TransferSource(null, quota));
            }
        }

        for (TowerEnergyEndpoint endpoint : this.endpointResolver.getCachedResolvedEnergyEndpoints(false)) {
            try {
                if (!endpoint.direction().allowsExtract()) {
                    continue;
                }
                long quota = energySnapshot(endpoint).stored();
                if (quota > 0) {
                    sources.add(new TransferSource(endpoint, quota));
                }
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
                Data_Energistics.LOGGER.error("Failed to freeze unlimited tower source quota at {} side {} storage {}",
                        endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(), exception);
            }
        }
        return sources;
    }

    long extractGridEnergy(long requested, boolean simulate, String purpose) {
        if (requested < 0) {
            throw new IllegalArgumentException("AppFlux extraction request must not be negative: " + requested);
        }
        if (requested == 0) {
            return 0L;
        }

        AENetworkedBlockEntity host = this.context.aeNetworkHost();
        long extracted;
        try {
            extracted = this.gridEnergyAccess.extract(host, requested, simulate);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error(
                    "AppFlux grid extraction failed for {} at {}; request={} FE, simulate={}",
                    purpose, describeGridEnergyHost(host), requested, simulate, exception);
            return 0L;
        }
        if (extracted < 0 || extracted > requested) {
            Data_Energistics.LOGGER.error(
                    "AppFlux grid extraction returned invalid amount {} for {} at {}; request={} FE, simulate={}",
                    extracted, purpose, describeGridEnergyHost(host), requested, simulate);
            return 0L;
        }
        return extracted;
    }

    private static String describeGridEnergyHost(@Nullable AENetworkedBlockEntity host) {
        if (host == null) {
            return "<unavailable tower host>";
        }
        return host.getClass().getName() + " at " + host.getBlockPos();
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

    private void addQuarantinedEnergy(long amount) {
        if (amount <= 0) {
            return;
        }
        this.context.setQuarantinedTransferEnergy(
                Math.addExact(this.context.quarantinedTransferEnergy(), amount));
        invalidateEnergyQueryCache();
    }

    private long rollbackUndeliveredEnergy(TransferSource source, long amount) {
        long restored;
        try {
            restored = source.rollbackExtraction(amount);
        } catch (UnlimitedEnergyAccessException exception) {
            if (exception.isMutationAmountKnown()) {
                long confirmedRestored = confirmedMutationAmount(exception, amount);
                if (confirmedRestored > 0) {
                    source.publishFailedMutation("compensation");
                }
                Data_Energistics.LOGGER.error(
                        "Unlimited tower compensation failed for source {}; confirmed {} of {} FE restored",
                        source.description(), confirmedRestored, amount, exception);
                return confirmedRestored;
            }
            addQuarantinedEnergy(amount);
            source.publishFailedMutation("uncertain compensation");
            Data_Energistics.LOGGER.error(
                    "Unlimited tower compensation failed for source {} with unreadable final state; quarantined {} FE",
                    source.description(), amount, exception);
            return amount;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
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

    /**
     * Inserts FE into receiver endpoints in range.
     *
     * @param amount      requested FE amount
     * @param simulate    true for simulation
     * @param excludedPos target position to exclude, or null
     * @return inserted amount
     */
    public long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        Set<IEnergyStorage> stalledReceiveStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        return distributeEnergyInRange(amount, simulate, excludedPos,
                this.endpointResolver.getCachedResolvedEnergyEndpoints(true), stalledReceiveStorages);
    }

    /**
     * Extracts FE from source endpoints and optional AE flux storage.
     *
     * @param amount      requested FE amount
     * @param simulate    true for simulation
     * @param excludedPos target position to exclude, or null
     * @return extracted amount clamped to integer storage limits
     */
    public int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (simulate) {
            return getCachedSimulatedExtract(amount, excludedPos);
        }
        return clampStoredAmount(extractEnergyFromRangeLong(amount, false, excludedPos));
    }

    /**
     * Returns total extractable FE for UI/capability queries.
     *
     * @param excludedPos target position to exclude, or null
     * @return extractable FE
     */
    public long getTotalExtractableEnergy(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).totalStored();
    }

    /**
     * Returns total FE capacity for source endpoints.
     *
     * @param excludedPos target position to exclude, or null
     * @return FE capacity
     */
    public long getTotalEnergyCapacity(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).totalCapacity();
    }

    /**
     * Returns the FE that receiver endpoints can currently accept.
     *
     * @param excludedPos target position to exclude, or null
     * @return currently receivable FE
     */
    public long getTotalReceivableEnergy(@Nullable BlockPos excludedPos) {
        return getReceiveQuerySummary(excludedPos).totalReceivable();
    }

    /**
     * Checks whether any receiver endpoint is available.
     *
     * @param excludedPos target position to exclude, or null
     * @return true when energy can be inserted somewhere
     */
    public boolean hasAnyReceiver(@Nullable BlockPos excludedPos) {
        return getReceiveQuerySummary(excludedPos).hasReceiver();
    }

    /**
     * Checks whether any source endpoint or AE flux source is available.
     *
     * @param excludedPos target position to exclude, or null
     * @return true when energy can be extracted somewhere
     */
    public boolean hasAnySource(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).hasSource();
    }

    /**
     * Clears transfer and query caches after storage state changes.
     */
    public void invalidateEnergyQueryCache() {
        this.cachedExtractQuerySummaries.clear();
        this.cachedReceiveQuerySummaries.clear();
        this.cachedSimulatedExtracts.clear();
        this.cachedSimulatedExtractTick = Long.MIN_VALUE;
    }

    /**
     * Clears cursor state and dependent transfer caches after endpoint topology changes.
     */
    public void invalidateResolvedEndpointCache() {
        this.extractRoundRobinCursor.clear();
        this.receiveRoundRobinCursor.clear();
        invalidateEnergyQueryCache();
    }

    /**
     * Trims bounded caches used by round-robin and query summaries.
     */
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
                if (!endpoint.direction().allowsReceive()) {
                    stalledReceiveStorages.add(storage);
                    continue;
                }
                result = insertEnergyIntoEndpoint(endpoint, remaining, simulate);
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
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
            if (result.terminal()) {
                break;
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
        if (this.opEnergyAccess.supports(storage)) {
            return insertOpIntoEndpoint(endpoint, amount, simulate);
        }
        Level level = this.context.level();
        if (level != null && MekanismEnergyAccess.supports(
                level, endpoint.pos(), endpoint.side(), storage)) {
            return transferMekanismEnergy(level, endpoint, amount, simulate, true);
        }
        long directInserted;
        try {
            directInserted = this.unlimitedEnergyAccess.insert(storage, amount, simulate);
        } catch (UnlimitedEnergyAccessException exception) {
            boolean mutationKnown = exception.isMutationAmountKnown();
            long confirmedInsertion = mutationKnown ? confirmedMutationAmount(exception, amount) : 0L;
            long failedInsertion = simulate ? 0L : mutationKnown ? confirmedInsertion : amount;
            if (!simulate && (confirmedInsertion > 0 || !mutationKnown)) {
                if (!mutationKnown) {
                    addQuarantinedEnergy(amount);
                }
                publishFailedMutation(endpoint, storage, "receiver mutation");
            }
            if (mutationKnown) {
                Data_Energistics.LOGGER.error(
                        "Unlimited energy receiver mutation failed at {} side {} storage {}; confirmed {} of {} FE inserted",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), confirmedInsertion, amount, exception);
            } else if (simulate) {
                Data_Energistics.LOGGER.error(
                        "Unlimited energy receiver simulation failed at {} side {} storage {} with unreadable final state; stopping without reporting simulated progress",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
            } else {
                Data_Energistics.LOGGER.error(
                        "Unlimited energy receiver mutation failed at {} side {} storage {} with unreadable final state; quarantined {} FE",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), amount, exception);
            }
            return new EndpointTransferResult(failedInsertion, true, true);
        }
        if (directInserted != UnlimitedEnergyAccess.UNAVAILABLE) {
            if (!simulate && directInserted > 0) {
                publishMutation(endpoint, storage, "receiver mutation");
            }
            return new EndpointTransferResult(directInserted, directInserted == 0);
        }
        return insertThroughCapability(endpoint, amount, simulate);
    }

    private EndpointTransferResult insertOpIntoEndpoint(TowerEnergyEndpoint endpoint, long amount, boolean simulate) {
        IEnergyStorage storage = endpoint.storage();
        Long before = readOpStored(endpoint, "before receiver mutation");
        if (before == null) {
            return EndpointTransferResult.STALLED;
        }

        long inserted;
        try {
            inserted = this.opEnergyAccess.insert(storage, amount, simulate);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            return resolveFailedOpInsertion(endpoint, amount, simulate, before, exception);
        }

        Long after = readOpStored(endpoint, "after receiver mutation");
        if (after == null) {
            return isolateUnknownOpInsertion(endpoint, amount, simulate, null);
        }
        if (simulate) {
            if (!after.equals(before)) {
                Data_Energistics.LOGGER.error(
                        "BrandonsCore OP receiver simulation mutated {} side {} storage {} from {} to {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), before, after);
                return EndpointTransferResult.STALLED;
            }
            if (inserted < 0 || inserted > amount) {
                Data_Energistics.LOGGER.error(
                        "BrandonsCore OP receiver at {} side {} storage {} returned {} for simulated request {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), inserted, amount);
                return EndpointTransferResult.STALLED;
            }
            return new EndpointTransferResult(inserted, inserted == 0);
        }

        long confirmedInserted = confirmedOpMutation(before, after, amount, true);
        if (confirmedInserted < 0) {
            return isolateUnknownOpInsertion(endpoint, amount, false, null);
        }
        if (inserted != confirmedInserted) {
            Data_Energistics.LOGGER.error(
                    "BrandonsCore OP receiver at {} side {} storage {} reported {} for request {}, but stored state confirms {}",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), inserted, amount, confirmedInserted);
            if (confirmedInserted > 0) {
                publishFailedMutation(endpoint, storage, "OP receiver mutation");
            }
            return new EndpointTransferResult(confirmedInserted, true, true);
        }
        if (inserted > 0) {
            publishMutation(endpoint, storage, "OP receiver mutation");
        }
        return new EndpointTransferResult(inserted, inserted == 0);
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
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
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
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
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
        if (this.opEnergyAccess.supports(storage)) {
            return extractOpFromEndpoint(endpoint, amount, simulate);
        }
        Level level = this.context.level();
        if (level != null && MekanismEnergyAccess.supports(
                level, endpoint.pos(), endpoint.side(), storage)) {
            return transferMekanismEnergy(level, endpoint, amount, simulate, false);
        }
        long directExtracted;
        try {
            directExtracted = this.unlimitedEnergyAccess.extract(storage, amount, simulate);
        } catch (UnlimitedEnergyAccessException exception) {
            if (exception.isMutationAmountKnown()) {
                long confirmedExtracted = confirmedMutationAmount(exception, amount);
                if (!simulate && confirmedExtracted > 0) {
                    publishFailedMutation(endpoint, storage, "source mutation");
                }
                Data_Energistics.LOGGER.error(
                        "Unlimited energy source mutation failed at {} side {} storage {}; confirmed {} of {} FE extracted",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), confirmedExtracted, amount, exception);
                return new EndpointTransferResult(confirmedExtracted, true);
            }
            if (!simulate) {
                addQuarantinedEnergy(amount);
                publishFailedMutation(endpoint, storage, "uncertain source mutation");
            }
            Data_Energistics.LOGGER.error(
                    "Unlimited energy source mutation failed at {} side {} storage {} with unreadable final state; quarantined {} FE",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), amount, exception);
            return EndpointTransferResult.STALLED;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
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
            if (!simulate && directExtracted > 0) {
                publishMutation(endpoint, storage, "source mutation");
            }
            return new EndpointTransferResult(directExtracted, directExtracted == 0);
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
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
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

    private EndpointTransferResult extractOpFromEndpoint(TowerEnergyEndpoint endpoint, long amount,
                                                         boolean simulate) {
        IEnergyStorage storage = endpoint.storage();
        Long before = readOpStored(endpoint, "before source mutation");
        if (before == null) {
            return EndpointTransferResult.STALLED;
        }

        long extracted;
        try {
            extracted = this.opEnergyAccess.extract(storage, amount, simulate);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            return resolveFailedOpExtraction(endpoint, amount, simulate, before, exception);
        }

        Long after = readOpStored(endpoint, "after source mutation");
        if (after == null) {
            return isolateUnknownOpExtraction(endpoint, amount, simulate, null);
        }
        if (simulate) {
            if (!after.equals(before)) {
                Data_Energistics.LOGGER.error(
                        "BrandonsCore OP source simulation mutated {} side {} storage {} from {} to {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), before, after);
                return EndpointTransferResult.STALLED;
            }
            if (extracted < 0 || extracted > amount) {
                Data_Energistics.LOGGER.error(
                        "BrandonsCore OP source at {} side {} storage {} returned {} for simulated request {}",
                        endpoint.pos(), endpoint.side(), storage.getClass().getName(), extracted, amount);
                return EndpointTransferResult.STALLED;
            }
            return new EndpointTransferResult(extracted, extracted == 0);
        }

        long confirmedExtracted = confirmedOpMutation(before, after, amount, false);
        if (confirmedExtracted < 0) {
            return isolateUnknownOpExtraction(endpoint, amount, false, null);
        }
        if (extracted != confirmedExtracted) {
            Data_Energistics.LOGGER.error(
                    "BrandonsCore OP source at {} side {} storage {} reported {} for request {}, but stored state confirms {}",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), extracted, amount, confirmedExtracted);
            if (confirmedExtracted > 0) {
                publishFailedMutation(endpoint, storage, "OP source mutation");
            }
            return new EndpointTransferResult(confirmedExtracted, true);
        }
        if (extracted > 0) {
            publishMutation(endpoint, storage, "OP source mutation");
        }
        return new EndpointTransferResult(extracted, extracted == 0);
    }

    private EndpointTransferResult transferMekanismEnergy(
                                                          Level level,
                                                          TowerEnergyEndpoint endpoint,
                                                          long amount,
                                                          boolean simulate,
                                                          boolean inserting) {
        IEnergyStorage storage = endpoint.storage();
        long transferred;
        try {
            transferred = inserting ? MekanismEnergyAccess.insert(
                    level, endpoint.pos(), endpoint.side(), storage, amount, simulate) :
                    MekanismEnergyAccess.extract(
                            level, endpoint.pos(), endpoint.side(), storage, amount, simulate);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error(
                    "Mekanism energy {} failed at {} side {} storage {}",
                    inserting ? "receiver" : "source",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
            return EndpointTransferResult.STALLED;
        }
        if (transferred < 0 || transferred > amount) {
            Data_Energistics.LOGGER.error(
                    "Mekanism energy {} at {} side {} storage {} returned {} for request {}",
                    inserting ? "receiver" : "source",
                    endpoint.pos(), endpoint.side(), storage.getClass().getName(), transferred, amount);
            return EndpointTransferResult.STALLED;
        }
        if (!simulate && transferred > 0) {
            publishMutation(endpoint, storage, inserting ? "Mekanism receiver mutation" : "Mekanism source mutation");
        }
        return new EndpointTransferResult(transferred, transferred == 0);
    }

    private EndpointTransferResult resolveFailedOpInsertion(TowerEnergyEndpoint endpoint, long amount,
                                                            boolean simulate, long before, Throwable exception) {
        Long after = readOpStored(endpoint, "after failed receiver mutation");
        if (after == null || simulate) {
            return isolateUnknownOpInsertion(endpoint, amount, simulate, exception);
        }
        long confirmedInserted = confirmedOpMutation(before, after, amount, true);
        if (confirmedInserted < 0) {
            return isolateUnknownOpInsertion(endpoint, amount, false, exception);
        }
        if (confirmedInserted > 0) {
            publishFailedMutation(endpoint, endpoint.storage(), "OP receiver mutation");
        }
        Data_Energistics.LOGGER.error(
                "BrandonsCore OP receiver failed at {} side {} storage {}; confirmed {} of {} OP inserted",
                endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(),
                confirmedInserted, amount, exception);
        return new EndpointTransferResult(confirmedInserted, true, true);
    }

    private EndpointTransferResult resolveFailedOpExtraction(TowerEnergyEndpoint endpoint, long amount,
                                                             boolean simulate, long before, Throwable exception) {
        Long after = readOpStored(endpoint, "after failed source mutation");
        if (after == null || simulate) {
            return isolateUnknownOpExtraction(endpoint, amount, simulate, exception);
        }
        long confirmedExtracted = confirmedOpMutation(before, after, amount, false);
        if (confirmedExtracted < 0) {
            return isolateUnknownOpExtraction(endpoint, amount, false, exception);
        }
        if (confirmedExtracted > 0) {
            publishFailedMutation(endpoint, endpoint.storage(), "OP source mutation");
        }
        Data_Energistics.LOGGER.error(
                "BrandonsCore OP source failed at {} side {} storage {}; confirmed {} of {} OP extracted",
                endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(),
                confirmedExtracted, amount, exception);
        return new EndpointTransferResult(confirmedExtracted, true);
    }

    private EndpointTransferResult isolateUnknownOpInsertion(TowerEnergyEndpoint endpoint, long amount,
                                                             boolean simulate, @Nullable Throwable exception) {
        if (!simulate) {
            addQuarantinedEnergy(amount);
            publishFailedMutation(endpoint, endpoint.storage(), "uncertain OP receiver mutation");
        }
        Data_Energistics.LOGGER.error(
                "BrandonsCore OP receiver at {} side {} storage {} has an unverified final state for {} OP; {}",
                endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(), amount,
                simulate ? "simulation stopped" : "amount quarantined", exception);
        return simulate ? EndpointTransferResult.STALLED : new EndpointTransferResult(amount, true, true);
    }

    private EndpointTransferResult isolateUnknownOpExtraction(TowerEnergyEndpoint endpoint, long amount,
                                                              boolean simulate, @Nullable Throwable exception) {
        if (!simulate) {
            addQuarantinedEnergy(amount);
            publishFailedMutation(endpoint, endpoint.storage(), "uncertain OP source mutation");
        }
        Data_Energistics.LOGGER.error(
                "BrandonsCore OP source at {} side {} storage {} has an unverified final state for {} OP; {}",
                endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(), amount,
                simulate ? "simulation stopped" : "amount quarantined", exception);
        return EndpointTransferResult.STALLED;
    }

    @Nullable
    private Long readOpStored(TowerEnergyEndpoint endpoint, String phase) {
        try {
            long stored = this.opEnergyAccess.stored(endpoint.storage());
            if (stored >= 0) {
                return stored;
            }
            Data_Energistics.LOGGER.error(
                    "BrandonsCore OP endpoint at {} side {} storage {} reported negative stored energy {} {}",
                    endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(), stored, phase);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error(
                    "Failed to read BrandonsCore OP endpoint at {} side {} storage {} {}",
                    endpoint.pos(), endpoint.side(), endpoint.storage().getClass().getName(), phase, exception);
        }
        return null;
    }

    private static long confirmedOpMutation(long before, long after, long requested, boolean inserting) {
        if (inserting) {
            if (after < before || after - before > requested) {
                return -1L;
            }
            return after - before;
        }
        if (after > before || before - after > requested) {
            return -1L;
        }
        return before - after;
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
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
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
            long extracted = extractGridEnergy(remaining, simulate, "range extraction");
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
            if (!endpoint.direction().allowsExtract()) {
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
            EnergySnapshot snapshot = energySnapshot(endpoint);
            totalStored = saturatingAdd(totalStored, snapshot.stored());
            totalCapacity = saturatingAdd(totalCapacity, snapshot.capacity());
        }
        long aeExtractable = 0L;
        if (this.appFluxEnergySupportLoaded) {
            aeExtractable = extractGridEnergy(Long.MAX_VALUE, true, "extractable-energy query");
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

        List<TowerEnergyEndpoint> endpoints = this.endpointResolver.collectEnergyEndpoints(
                true, normalizedExcludedPos);
        Set<IEnergyStorage> stalledStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        long totalReceivable = distributeEnergyInRange(
                Long.MAX_VALUE, true, normalizedExcludedPos, endpoints, stalledStorages);
        ReceiverQuerySummary summary = new ReceiverQuerySummary(
                gameTime, totalReceivable, !endpoints.isEmpty());
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

    private EnergySnapshot energySnapshot(TowerEnergyEndpoint endpoint) {
        IEnergyStorage storage = endpoint.storage();
        if (this.opEnergyAccess.supports(storage)) {
            return new EnergySnapshot(
                    this.opEnergyAccess.stored(storage),
                    this.opEnergyAccess.capacity(storage));
        }
        Level level = this.context.level();
        if (level != null && MekanismEnergyAccess.supports(
                level, endpoint.pos(), endpoint.side(), storage)) {
            return MekanismEnergyAccess.snapshot(
                    level, endpoint.pos(), endpoint.side(), storage);
        }
        return this.unlimitedEnergyAccess.snapshot(storage);
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

    private static long confirmedMutationAmount(UnlimitedEnergyAccessException exception, long requested) {
        if (!exception.isMutationAmountKnown()) {
            return 0L;
        }
        long confirmed = exception.mutationAmount();
        return confirmed <= requested ? confirmed : 0L;
    }

    private void publishFailedMutation(TowerEnergyEndpoint endpoint, IEnergyStorage storage, String operation) {
        publishMutation(endpoint, storage, operation);
    }

    private void publishMutation(TowerEnergyEndpoint endpoint, IEnergyStorage storage, String operation) {
        if (!this.opEnergyAccess.supports(storage)) {
            try {
                this.unlimitedEnergyAccess.notifyStorageChanged(storage);
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
                Data_Energistics.LOGGER.error(
                        "Failed to notify unlimited tower {} at {} side {} storage {}",
                        operation, endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
            }
        }
        try {
            this.context.markEndpointChanged(endpoint.pos());
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            Data_Energistics.LOGGER.error(
                    "Failed to mark unlimited tower {} endpoint changed at {} side {} storage {}",
                    operation, endpoint.pos(), endpoint.side(), storage.getClass().getName(), exception);
        }
    }

    private record ExtractSimulationKey(@Nullable BlockPos excludedPos, int amount) {}

    private record EndpointTransferResult(long amount, boolean stalled, boolean terminal) {

        private EndpointTransferResult(long amount, boolean stalled) {
            this(amount, stalled, false);
        }

        private static final EndpointTransferResult STALLED = new EndpointTransferResult(0L, true, false);
    }

    private record EnergyQuerySummary(long tick, long totalStored, long totalCapacity, boolean hasSource) {

        private static final EnergyQuerySummary EMPTY = new EnergyQuerySummary(Long.MIN_VALUE, 0L, 0L, false);
    }

    private record ReceiverQuerySummary(long tick, long totalReceivable, boolean hasReceiver) {

        private static final ReceiverQuerySummary EMPTY = new ReceiverQuerySummary(Long.MIN_VALUE, 0L, false);
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
                return TowerEnergyTransferEngine.this.extractGridEnergy(amount, simulate, "active source transfer");
            }
            EndpointTransferResult result = TowerEnergyTransferEngine.this.extractEnergyFromEndpointResult(
                    this.endpoint, amount, simulate);
            this.stalled |= result.stalled();
            return result.amount();
        }

        private long rollbackExtraction(long amount) {
            if (this.endpoint == null) {
                return TowerEnergyTransferEngine.this.gridEnergyAccess.restore(
                        TowerEnergyTransferEngine.this.context.aeNetworkHost(), amount);
            }

            IEnergyStorage storage = this.endpoint.storage();
            Level level = TowerEnergyTransferEngine.this.context.level();
            long restored;
            if (TowerEnergyTransferEngine.this.opEnergyAccess.supports(storage)) {
                restored = TowerEnergyTransferEngine.this.opEnergyAccess.insert(storage, amount, false);
            } else if (level != null && MekanismEnergyAccess.supports(
                    level, this.endpoint.pos(), this.endpoint.side(), storage)) {
                        restored = MekanismEnergyAccess.compensateExtraction(
                                level,
                                this.endpoint.pos(),
                                storage,
                                amount);
                    } else {
                        restored = TowerEnergyTransferEngine.this.unlimitedEnergyAccess.rollbackExtraction(storage, amount);
                    }
            if (restored > 0) {
                TowerEnergyTransferEngine.this.publishMutation(this.endpoint, storage, "source compensation");
            }
            return restored;
        }

        private void publishFailedMutation(String operation) {
            if (this.endpoint != null) {
                TowerEnergyTransferEngine.this.publishFailedMutation(
                        this.endpoint, this.endpoint.storage(), operation);
            }
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
