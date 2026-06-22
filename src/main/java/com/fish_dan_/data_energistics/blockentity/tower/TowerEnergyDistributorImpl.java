package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.energy.DirectEnergyAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default active FE balancing implementation for Data Distribution Towers.
 */
public final class TowerEnergyDistributorImpl implements TowerEnergyDistributor {

    private static final int TRANSFER_SUBSTEPS_PER_TICK = 5;
    private static final int TRANSFER_SCAN_CACHE_TICKS = 5;
    private static final int MAX_CURSOR_ENTRIES = 128;

    private final TowerEnergyDistributorContext context;
    private final TowerEnergyEndpointResolver endpointResolver;
    private final DirectEnergyAccess directEnergyAccess;
    private final Map<BlockPos, EnergyQuerySummary> cachedExtractQuerySummaries = new HashMap<>();
    private final Map<BlockPos, ReceiverQuerySummary> cachedReceiveQuerySummaries = new HashMap<>();
    private final Map<BlockPos, Integer> extractRoundRobinCursor = new HashMap<>();
    private final Map<BlockPos, Integer> receiveRoundRobinCursor = new HashMap<>();
    private final Map<ExtractSimulationKey, Integer> cachedSimulatedExtracts = new HashMap<>();
    private TransferScanSnapshot cachedTransferScanSnapshot = TransferScanSnapshot.EMPTY;
    private long cachedSimulatedExtractTick = Long.MIN_VALUE;

    /**
     * Creates an active FE distributor.
     *
     * @param context            owning tower callbacks
     * @param endpointResolver   endpoint resolver used for side probing and caching
     * @param directEnergyAccess direct storage access bridge
     */
    public TowerEnergyDistributorImpl(TowerEnergyDistributorContext context,
                                      TowerEnergyEndpointResolver endpointResolver,
                                      DirectEnergyAccess directEnergyAccess) {
        this.context = context;
        this.endpointResolver = endpointResolver;
        this.directEnergyAccess = directEnergyAccess;
    }

    @Override
    public void performActiveRangeTransfer() {
        long transferBudget = this.context.transferBudgetPerTick();
        if (transferBudget <= 0) {
            return;
        }

        TransferScanSnapshot transferScanSnapshot = getTransferScanSnapshot();
        if (transferScanSnapshot.receiveEndpoints().isEmpty()) {
            return;
        }

        long remainingBudget = transferBudget;
        for (int substep = 0; substep < TRANSFER_SUBSTEPS_PER_TICK; substep++) {
            if (remainingBudget <= 0) {
                break;
            }

            long stepBudget = divideCeil(remainingBudget, TRANSFER_SUBSTEPS_PER_TICK - substep);
            long simulatedExtract = simulateCachedTransferExtract(stepBudget, transferScanSnapshot);
            if (simulatedExtract <= 0) {
                break;
            }

            long simulatedInsert = distributeEnergyInRange(simulatedExtract, true, null, transferScanSnapshot.receiveEndpoints());
            if (simulatedInsert <= 0) {
                break;
            }

            long transferAmount = Math.min(simulatedExtract, Math.min(simulatedInsert, stepBudget));
            long actuallyExtracted = 0;
            long remainingExtraction = transferAmount;

            if (AE2FluxIntegration.isAvailable()) {
                long extracted = AE2FluxIntegration.extractEnergyFromOwnNetwork(this.context.aeNetworkHost(), remainingExtraction, false);
                if (extracted > 0) {
                    actuallyExtracted += extracted;
                    remainingExtraction -= extracted;
                }
            }
            if (remainingExtraction > 0) {
                actuallyExtracted += extractFromEndpointsRoundRobin(remainingExtraction, false, transferScanSnapshot.extractEndpoints());
            }
            if (actuallyExtracted <= 0) {
                break;
            }

            long actuallyInserted = distributeEnergyInRange(actuallyExtracted, false, null, transferScanSnapshot.receiveEndpoints());
            if (actuallyInserted <= 0) {
                Data_Energistics.LOGGER.warn("Active range transfer extracted {} FE but failed to insert it; aborting to avoid further loss.", actuallyExtracted);
                break;
            }

            if (actuallyInserted < actuallyExtracted) {
                Data_Energistics.LOGGER.warn("Active range transfer inserted only {} / {} FE after simulation; stopping to avoid desync.", actuallyInserted, actuallyExtracted);
                break;
            }

            remainingBudget -= actuallyInserted;
        }
    }

    @Override
    public long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        return distributeEnergyInRange(amount, simulate, excludedPos, this.endpointResolver.collectClusterEnergyEndpoints(true));
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
        this.cachedTransferScanSnapshot = TransferScanSnapshot.EMPTY;
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

    private TransferScanSnapshot getTransferScanSnapshot() {
        Level level = this.context.level();
        if (level == null) {
            return TransferScanSnapshot.EMPTY;
        }

        long gameTime = level.getGameTime();
        TransferScanSnapshot cached = this.cachedTransferScanSnapshot;
        if (cached.tick() != Long.MIN_VALUE && gameTime - cached.tick() < TRANSFER_SCAN_CACHE_TICKS) {
            return cached;
        }

        List<TowerEnergyEndpoint> extractEndpoints = this.endpointResolver.getCachedResolvedEnergyEndpoints(false);
        List<TowerEnergyEndpoint> receiveEndpoints = this.endpointResolver.getCachedResolvedEnergyEndpoints(true);

        long aeExtractable = 0;
        if (AE2FluxIntegration.isAvailable()) {
            aeExtractable = Math.max(0L, AE2FluxIntegration.extractEnergyFromOwnNetwork(this.context.aeNetworkHost(), Long.MAX_VALUE, true));
        }

        ArrayList<TowerEnergyEndpoint> activeExtractEndpoints = new ArrayList<>(extractEndpoints.size());
        for (TowerEnergyEndpoint endpoint : extractEndpoints) {
            IEnergyStorage storage = endpoint.storage();
            if (storage.canExtract() && storage.getEnergyStored() > 0) {
                activeExtractEndpoints.add(endpoint);
            }
        }

        ArrayList<TowerEnergyEndpoint> activeReceiveEndpoints = new ArrayList<>(receiveEndpoints.size());
        for (TowerEnergyEndpoint endpoint : receiveEndpoints) {
            IEnergyStorage storage = endpoint.storage();
            if (this.endpointResolver.canReceiveEnergy(storage)) {
                activeReceiveEndpoints.add(endpoint);
            }
        }

        TransferScanSnapshot snapshot = new TransferScanSnapshot(
                gameTime,
                aeExtractable,
                List.copyOf(activeExtractEndpoints),
                List.copyOf(activeReceiveEndpoints));
        this.cachedTransferScanSnapshot = snapshot;
        return snapshot;
    }

    private long simulateCachedTransferExtract(long amount, TransferScanSnapshot transferScanSnapshot) {
        if (amount <= 0) {
            return 0;
        }

        long totalExtractable = 0;
        if (transferScanSnapshot.aeExtractable() > 0) {
            totalExtractable = Math.min(amount, transferScanSnapshot.aeExtractable());
        }

        long remaining = amount - totalExtractable;
        if (remaining > 0) {
            totalExtractable += extractFromEndpointsRoundRobin(remaining, true, transferScanSnapshot.extractEndpoints());
        }
        return Math.min(amount, totalExtractable);
    }

    private long extractFromEndpointsRoundRobin(long amount, boolean simulate, List<TowerEnergyEndpoint> extractEndpoints) {
        if (amount <= 0 || extractEndpoints.isEmpty()) {
            return 0;
        }

        long totalExtracted = 0;
        long remaining = amount;
        int endpointCount = extractEndpoints.size();
        int startIndex = getExtractStartIndex(null, endpointCount);
        int lastSuccessfulIndex = -1;

        for (int offset = 0; offset < endpointCount; offset++) {
            if (remaining <= 0) {
                break;
            }

            int endpointIndex = (startIndex + offset) % endpointCount;
            IEnergyStorage storage = extractEndpoints.get(endpointIndex).storage();
            if (!storage.canExtract() || storage.getEnergyStored() <= 0) {
                continue;
            }

            int extracted = storage.extractEnergy(clampEnergyRequest(remaining), simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (lastSuccessfulIndex >= 0) {
            this.extractRoundRobinCursor.put(null, lastSuccessfulIndex);
        }

        return totalExtracted;
    }

    private long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos,
                                         List<TowerEnergyEndpoint> receiveEndpoints) {
        if (!this.context.isTowerActive() || amount <= 0) {
            return 0;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeReceiveExcludedPos(excludedPos);
        List<TowerEnergyEndpoint> endpoints = this.endpointResolver.collectEnergyEndpoints(true, normalizedExcludedPos);
        if (receiveEndpoints != this.endpointResolver.getCachedResolvedEnergyEndpoints(true)) {
            endpoints = excludeEnergyEndpoint(receiveEndpoints, normalizedExcludedPos);
        }
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
            if (!this.endpointResolver.canReceiveEnergy(storage)) {
                continue;
            }

            long inserted = insertEnergyIntoEndpoint(endpoint, remaining, simulate);
            if (inserted > 0) {
                totalInserted += inserted;
                remaining -= inserted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (lastSuccessfulIndex >= 0) {
            this.receiveRoundRobinCursor.put(normalizedExcludedPos, lastSuccessfulIndex);
        }

        if (!simulate && totalInserted > 0) {
            invalidateEnergyQueryCache();
        }
        return totalInserted;
    }

    private long insertEnergyIntoEndpoint(TowerEnergyEndpoint endpoint, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        IEnergyStorage storage = endpoint.storage();
        long directInserted = this.directEnergyAccess.insert(storage, amount, simulate);
        if (directInserted != DirectEnergyAccess.INSERT_UNAVAILABLE) {
            if (directInserted > 0 || !storage.canReceive()) {
                if (!simulate && directInserted > 0) {
                    this.directEnergyAccess.notifyStorageChanged(storage);
                    this.context.markEndpointChanged(endpoint.pos());
                }
                return directInserted;
            }
        }
        return storage.receiveEnergy(clampEnergyRequest(amount), simulate);
    }

    private int getCachedSimulatedExtract(int amount, @Nullable BlockPos excludedPos) {
        Level level = this.context.level();
        if (amount <= 0 || level == null) {
            return 0;
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
        if (!this.context.isTowerActive() || amount <= 0) {
            return 0;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeExtractExcludedPos(excludedPos);
        List<TowerEnergyEndpoint> endpoints = this.endpointResolver.collectEnergyEndpoints(false, normalizedExcludedPos);
        this.context.recordMaxExtractEndpoints(endpoints.size());
        long totalExtracted = 0;
        long remaining = amount;

        if (AE2FluxIntegration.isAvailable()) {
            long extracted = AE2FluxIntegration.extractEnergyFromOwnNetwork(this.context.aeNetworkHost(), remaining, simulate);
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
            if (!storage.canExtract()) {
                continue;
            }

            int extracted = storage.extractEnergy(clampEnergyRequest(remaining), simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (lastSuccessfulIndex >= 0) {
            this.extractRoundRobinCursor.put(normalizedExcludedPos, lastSuccessfulIndex);
        }

        if (!simulate && totalExtracted > 0) {
            invalidateEnergyQueryCache();
        }
        return totalExtracted;
    }

    private EnergyQuerySummary getExtractQuerySummary(@Nullable BlockPos excludedPos) {
        Level level = this.context.level();
        if (!this.context.isTowerActive() || level == null) {
            return EnergyQuerySummary.EMPTY;
        }

        BlockPos normalizedExcludedPos = this.endpointResolver.normalizeExtractExcludedPos(excludedPos);
        long gameTime = level.getGameTime();
        EnergyQuerySummary cached = this.cachedExtractQuerySummaries.get(normalizedExcludedPos);
        if (cached != null && cached.tick() == gameTime) {
            return cached;
        }

        long totalStored = 0L;
        long totalCapacity = 0L;
        List<TowerEnergyEndpoint> endpoints = this.endpointResolver.collectEnergyEndpoints(false, normalizedExcludedPos);
        for (TowerEnergyEndpoint endpoint : endpoints) {
            totalStored = saturatingAdd(totalStored, endpoint.storage().getEnergyStored());
            totalCapacity = saturatingAdd(totalCapacity, endpoint.storage().getMaxEnergyStored());
        }
        long aeExtractable = 0L;
        if (AE2FluxIntegration.isAvailable()) {
            aeExtractable = AE2FluxIntegration.extractEnergyFromOwnNetwork(this.context.aeNetworkHost(), Long.MAX_VALUE, true);
            totalStored = saturatingAdd(totalStored, aeExtractable);
        }

        EnergyQuerySummary summary = new EnergyQuerySummary(gameTime, totalStored, totalCapacity, !endpoints.isEmpty() || aeExtractable > 0);
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

    private static long divideCeil(long dividend, int divisor) {
        if (dividend <= 0 || divisor <= 0) {
            return 0;
        }
        return 1L + (dividend - 1L) / divisor;
    }

    private record ExtractSimulationKey(@Nullable BlockPos excludedPos, int amount) {}

    private record EnergyQuerySummary(long tick, long totalStored, long totalCapacity, boolean hasSource) {

        private static final EnergyQuerySummary EMPTY = new EnergyQuerySummary(Long.MIN_VALUE, 0L, 0L, false);
    }

    private record ReceiverQuerySummary(long tick, boolean hasReceiver) {

        private static final ReceiverQuerySummary EMPTY = new ReceiverQuerySummary(Long.MIN_VALUE, false);
    }

    private record TransferScanSnapshot(long tick, long aeExtractable, List<TowerEnergyEndpoint> extractEndpoints,
                                        List<TowerEnergyEndpoint> receiveEndpoints) {

        private static final TransferScanSnapshot EMPTY = new TransferScanSnapshot(Long.MIN_VALUE, 0L, List.of(), List.of());
    }
}
