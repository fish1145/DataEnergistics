package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerSchedulingHint;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchStepResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuContribution;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuPartitionProfile;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuProfile;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Runtime CPU pool owned by one Trinity Data Core block entity.
 */
public final class TrinityDataCoreCraftingRuntime {

    private static final AtomicLong RUNTIME_GENERATION_SEQUENCE = new AtomicLong();

    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final int SCHEMA_VERSION = 2;
    private static final String CONTRIBUTIONS_TAG = "contributions";
    private static final String CONTRIBUTION_NAME_TAG = "name";
    private static final String STORAGE_BYTES_TAG = "storage_bytes";
    private static final String CO_PROCESSORS_TAG = "co_processors";
    private static final String PARTITION_COUNT_TAG = "partition_count";
    private static final String SELECTION_MODE_TAG = "selection_mode";
    private static final String PARTITIONS_TAG = "partitions";
    private static final String PARTITION_INDEX_TAG = "index";
    private static final String PARTITION_LOGIC_TAG = "logic";

    private final TrinityDataCoreBlockEntity host;
    private final long runtimeGeneration = RUNTIME_GENERATION_SEQUENCE.updateAndGet(Math::incrementExact);
    private final Map<String, TrinityDataCoreCpuContribution> externalContributions = new TreeMap<>();
    private final NavigableMap<Integer, TrinityDataCoreVirtualCpu> retainedWorkers = new TreeMap<>();
    private final NavigableMap<Integer, CompoundTag> pendingWorkerLogic = new TreeMap<>();
    /**
     * Derived request lookup removes full-worker scans from network output routing.
     */
    private final TrinityCpuWaitingIndex waitingIndex = new TrinityCpuWaitingIndex();
    /**
     * Background completion callbacks enqueue only worker numbers into this thread-safe handoff.
     */
    private final ConcurrentLinkedQueue<Integer> proposalCompletions = new ConcurrentLinkedQueue<>();
    /**
     * Event-selected workers replace the previous every-tick retained-worker scan.
     */
    private final ArrayDeque<WorkerScheduleEntry> readyWorkers = new ArrayDeque<>();
    private final Set<Integer> readyWorkerNumbers = new HashSet<>();
    private final PriorityQueue<WorkerScheduleEntry> providerRetries = new PriorityQueue<>();
    private final Map<Integer, Long> workerScheduleRevisions = new HashMap<>();
    @Nullable
    private TrinityDataCoreVirtualCpu reservedCpu;
    private TrinityDataCoreCpuProfile profile = TrinityDataCoreCpuProfile.EMPTY;
    /**
     * Immutable publication snapshot remains identity-stable until CPU topology changes.
     */
    private List<TrinityDataCoreVirtualCpu> publishedCpus = List.of();
    /**
     * Cached latest change tick is updated while workers are already being visited.
     */
    private long lastModifiedOnTick;
    /**
     * Allocation cursor always identifies the smallest free positive worker number.
     */
    private int nextAvailableWorkerNumber = 1;
    /**
     * Transient dispatch cursor preserves round-robin priority across event-driven runtime passes.
     */
    private int nextWorkerTickStartNumber = 1;
    /**
     * Server tick whose proposal completions, retries and cleanup state have already been prepared.
     */
    private long preparedDispatchTick = Long.MIN_VALUE;
    private boolean mainStructureFormed;
    private boolean paused;

    public TrinityDataCoreCraftingRuntime(TrinityDataCoreBlockEntity host) {
        this.host = host;
    }

    /**
     * Updates whether child structure CPU contributions are active.
     */
    public void setMainStructureFormed(boolean formed) {
        if (this.mainStructureFormed == formed) {
            return;
        }
        this.mainStructureFormed = formed;
        if (formed) {
            rebuildWorkerScheduling();
        } else {
            clearTransientScheduling();
            for (TrinityDataCoreVirtualCpu worker : this.retainedWorkers.values()) {
                worker.logic().cancelPendingDispatch();
            }
        }
        rebuildPublishedCpus();
    }

    /**
     * Pauses or resumes execution without discarding jobs or their inventories.
     */
    public void setPaused(boolean paused) {
        if (this.paused == paused) {
            return;
        }
        this.paused = paused;
        if (paused) {
            clearTransientScheduling();
            for (TrinityDataCoreVirtualCpu worker : this.retainedWorkers.values()) {
                worker.logic().cancelPendingDispatch();
            }
        } else {
            rebuildWorkerScheduling();
        }
        rebuildPublishedCpus();
    }

    /**
     * Cancels every active worker job. This is reserved for permanent host removal.
     */
    public void cancelAllJobs() {
        for (TrinityDataCoreVirtualCpu cpu : this.retainedWorkers.values()) {
            cpu.logic().cancel();
        }
        rebuildWaitingIndex();
        rebuildWorkerScheduling();
        rebuildPublishedCpus();
    }

    /**
     * Moves inventory left behind after cancellation into durable host-owned storage.
     */
    public boolean recoverCancelledInventory(BiFunction<AEKey, Long, Long> recovery) {
        boolean recoveredAll = true;
        for (TrinityDataCoreVirtualCpu cpu : this.retainedWorkers.values()) {
            recoveredAll &= cpu.recoverIdleInventory(recovery);
        }
        return recoveredAll;
    }

    /**
     * Returns whether at least one worker currently owns a job.
     */
    public boolean hasBusyJobs() {
        for (TrinityDataCoreVirtualCpu cpu : this.retainedWorkers.values()) {
            if (cpu.isBusy()) {
                return true;
            }
        }
        for (CompoundTag pendingLogic : this.pendingWorkerLogic.values()) {
            if (TrinityDataCoreCpuLogic.persistedHasJob(pendingLogic)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the coordinator can allocate a worker without mutating retained runtime state.
     */
    boolean canAcceptJob() {
        if (this.paused || !this.mainStructureFormed || !this.profile.active()) {
            return false;
        }
        if (this.nextAvailableWorkerNumber <= this.profile.partitionCount()) {
            return true;
        }
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (entry.getKey() <= this.profile.partitionCount() &&
                    !this.pendingWorkerLogic.containsKey(entry.getKey()) &&
                    entry.getValue().isReleasable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of retained workers that currently consume an allocation slot.
     */
    int occupiedWorkerCount() {
        int occupied = 0;
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (entry.getKey() <= this.profile.partitionCount() &&
                    (this.pendingWorkerLogic.containsKey(entry.getKey()) || !entry.getValue().isReleasable())) {
                occupied++;
            }
        }
        return occupied;
    }

    /**
     * Returns the aggregate recent physical-operation load of workers still retained by this runtime.
     */
    long recentOperationLoad() {
        long recentOperations = 0L;
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (entry.getKey() <= this.profile.partitionCount()) {
                recentOperations = Math.addExact(recentOperations, entry.getValue().getRecentOperationLoad());
            }
        }
        return recentOperations;
    }

    /**
     * Adds or replaces CPU data contributed by a named child structure.
     */
    public void setContribution(String structureName, TrinityDataCoreCpuContribution contribution) {
        String checkedStructureName = requireStructureName(structureName);
        if (contribution.equals(this.externalContributions.get(checkedStructureName))) {
            return;
        }
        Map<String, TrinityDataCoreCpuContribution> nextContributions = new TreeMap<>(this.externalContributions);
        nextContributions.put(checkedStructureName, contribution);
        TrinityDataCoreCpuProfile nextProfile = TrinityDataCoreCpuProfile.fromContributions(nextContributions);
        this.externalContributions.clear();
        this.externalContributions.putAll(nextContributions);
        applyProfile(nextProfile);
    }

    /**
     * Clears CPU data contributed by a named child structure.
     */
    public void clearContribution(String structureName) {
        String checkedStructureName = requireStructureName(structureName);
        if (!this.externalContributions.containsKey(checkedStructureName)) {
            return;
        }
        Map<String, TrinityDataCoreCpuContribution> nextContributions = new TreeMap<>(this.externalContributions);
        nextContributions.remove(checkedStructureName);
        TrinityDataCoreCpuProfile nextProfile = TrinityDataCoreCpuProfile.fromContributions(nextContributions);
        this.externalContributions.clear();
        this.externalContributions.putAll(nextContributions);
        applyProfile(nextProfile);
    }

    /**
     * Returns whether a named child structure currently has stored CPU data.
     */
    public boolean hasContribution(String structureName) {
        return this.externalContributions.containsKey(requireStructureName(structureName));
    }

    /**
     * Returns the AE2-visible CPU view: the reserved CPU first, followed by active busy workers in numeric order.
     */
    public List<TrinityDataCoreVirtualCpu> publishedCpus() {
        return this.publishedCpus;
    }

    /**
     * Compatibility alias used by the host's existing published CPU view contract.
     */
    public List<TrinityDataCoreVirtualCpu> partitions() {
        return publishedCpus();
    }

    /**
     * Returns whether this runtime must remain attached to AE2 even when no CPU is currently published.
     */
    public boolean shouldRemainRegistered() {
        return (this.mainStructureFormed && this.profile.active()) || !this.retainedWorkers.isEmpty();
    }

    /**
     * Returns the current aggregate worker profile.
     */
    public TrinityDataCoreCpuProfile profile() {
        return this.profile;
    }

    /**
     * Allocates the smallest available worker and submits the job directly to that worker.
     */
    ICraftingSubmitResult submitJob(IGrid grid,
                                    ICraftingPlan plan,
                                    IActionSource source,
                                    @Nullable ICraftingRequester requester) {
        TrinityDataCoreVirtualCpu coordinator = this.reservedCpu;
        if (coordinator == null || !coordinator.isActiveOnGrid(grid)) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }

        releaseReleasableWorkers();
        int workerNumber = findAvailableWorkerNumber();
        if (workerNumber < 0) {
            return CraftingSubmitResult.CPU_BUSY;
        }

        TrinityDataCoreVirtualCpu worker = new TrinityDataCoreVirtualCpu(
                this.host,
                this,
                this.profile.partition(workerNumber));
        this.retainedWorkers.put(workerNumber, worker);
        advanceAvailableWorkerNumber();
        try {
            ICraftingSubmitResult result = worker.submitWorkerJob(grid, plan, source, requester);
            if (!result.successful()) {
                removeWorkerIfReleasable(workerNumber, worker);
            } else {
                refreshWorkerWaiting(worker);
                cacheLastModified(worker);
                enqueueReady(worker.number());
                rebuildPublishedCpus();
            }
            return result;
        } catch (RuntimeException exception) {
            worker.logic().abortFailedSubmission();
            removeWorkerIfReleasable(workerNumber, worker);
            if (this.retainedWorkers.get(workerNumber) == worker) {
                refreshWorkerWaiting(worker);
                cacheLastModified(worker);
                scheduleFromEvent(worker);
                rebuildPublishedCpus();
            }
            Data_Energistics.LOGGER.error(
                    "Failed to submit a Trinity crafting job to worker CPU {}",
                    workerNumber,
                    exception);
            throw exception;
        }
    }

    /**
     * Ticks retained workers through one grid-shared dispatch window and releases workers only after both job and
     * inventory are empty.
     *
     * @param energyService   AE2 energy service shared by this runtime's grid
     * @param craftingService AE2 crafting service used to resolve pattern providers
     * @param dispatchWindow  physical provider-attempt budget shared across the complete grid tick
     * @param dispatchBudget  Governor policy captured for this complete grid tick
     */
    public void tick(IEnergyService energyService,
                     CraftingService craftingService,
                     CraftingDispatchWindow dispatchWindow,
                     CraftingDispatchBudget dispatchBudget) {
        prepareTick();
        CraftingDispatchStepResult result;
        do {
            result = dispatchStep(energyService, craftingService, dispatchWindow, dispatchBudget);
        } while (result.progressed() && result.hasReadyWork() && !result.windowExhausted());
    }

    /**
     * Applies runtime-level completion, retry and cleanup events exactly once for the current server tick.
     */
    public void prepareTick() {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (this.preparedDispatchTick == currentTick) {
            return;
        }
        if (this.preparedDispatchTick > currentTick) {
            throw new IllegalStateException(
                    "Trinity runtime tick moved backwards from " + this.preparedDispatchTick + " to " + currentTick);
        }
        this.preparedDispatchTick = currentTick;
        if (this.paused || !this.mainStructureFormed || !this.profile.active()) {
            return;
        }
        releaseReleasableWorkers();
        if (this.retainedWorkers.isEmpty()) {
            return;
        }
        drainProposalCompletions();
        activateDueWorkerRetries(currentTick);
        orderReadyWorkersFromCursor();
    }

    /**
     * Skips stale or non-progressing workers and performs at most one real provider call.
     *
     * @param energyService   AE2 energy service shared by this runtime's Grid
     * @param craftingService AE2 crafting service used to resolve pattern providers
     * @param dispatchWindow  physical provider-attempt budget shared by the complete Grid tick
     * @param dispatchBudget  Governor policy captured for the complete Grid tick
     * @return immutable progress facts for server-level round-robin scheduling
     */
    public CraftingDispatchStepResult dispatchStep(IEnergyService energyService,
                                                   CraftingService craftingService,
                                                   CraftingDispatchWindow dispatchWindow,
                                                   CraftingDispatchBudget dispatchBudget) {
        prepareTick();
        if (this.paused || !this.mainStructureFormed || !this.profile.active()) {
            return CraftingDispatchStepResult.IDLE;
        }
        int workersToInspect = this.readyWorkers.size();
        long currentTick = TickHandler.instance().getCurrentTick();
        while (workersToInspect > 0 && !this.readyWorkers.isEmpty() && !dispatchWindow.isExhausted()) {
            workersToInspect--;
            WorkerScheduleEntry scheduled = this.readyWorkers.removeFirst();
            if (!consumeReadySchedule(scheduled)) {
                continue;
            }
            TrinityDataCoreVirtualCpu worker = this.retainedWorkers.get(scheduled.workerNumber());
            if (worker == null) {
                continue;
            }
            int workerNumber = worker.number();
            CraftingDispatchStepResult result;
            try {
                result = worker.dispatchStep(
                        energyService,
                        craftingService,
                        dispatchWindow,
                        dispatchBudget);
            } catch (RuntimeException failure) {
                if (this.retainedWorkers.get(workerNumber) == worker) {
                    scheduleAfterWorkerTick(worker, currentTick);
                }
                throw failure;
            }
            removeWorkerIfReleasable(workerNumber, worker);
            if (this.retainedWorkers.get(workerNumber) == worker && !result.progressed()) {
                scheduleAfterWorkerTick(worker, currentTick);
            }
            if (result.physicalAttempted()) {
                advanceWorkerTickStartAfter(workerNumber);
                if (this.retainedWorkers.get(workerNumber) == worker) {
                    if (result.hasReadyWork()) {
                        enqueueReady(workerNumber);
                    } else {
                        scheduleAfterWorkerTick(worker, currentTick);
                    }
                }
                return new CraftingDispatchStepResult(
                        true,
                        result.stateChanged(),
                        !this.readyWorkers.isEmpty(),
                        dispatchWindow.isExhausted());
            }
            if (result.stateChanged()) {
                return new CraftingDispatchStepResult(
                        false,
                        true,
                        !this.readyWorkers.isEmpty(),
                        dispatchWindow.isExhausted());
            }
        }
        return new CraftingDispatchStepResult(
                false,
                false,
                !this.readyWorkers.isEmpty(),
                dispatchWindow.isExhausted());
    }

    /**
     * @return whether an event-selected worker remains eligible for another current-tick server rotation
     */
    public boolean hasReadyDispatchWork() {
        return !this.paused && this.mainStructureFormed && this.profile.active() && !this.readyWorkers.isEmpty();
    }

    /**
     * Retains the pre-Governor execution contract with deterministic hard limits.
     */
    public void tick(IEnergyService energyService,
                     CraftingService craftingService,
                     CraftingDispatchWindow dispatchWindow) {
        tick(energyService, craftingService, dispatchWindow, CraftingDispatchBudget.legacyFixedHard());
    }

    /**
     * Inserts returned crafting outputs into retained workers.
     */
    public long insertIntoCpus(AEKey what, long amount, Actionable mode, long inserted) {
        long totalInserted = inserted;
        for (int workerNumber : this.waitingIndex.waitingWorkerNumbers(what)) {
            if (totalInserted >= amount) {
                break;
            }
            TrinityDataCoreVirtualCpu cpu = this.retainedWorkers.get(workerNumber);
            totalInserted += cpu.insert(what, amount - totalInserted, mode);
        }
        return totalInserted;
    }

    /**
     * Adds all currently awaited keys to AE2's request set.
     */
    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        this.waitingIndex.addWaitingKeys(waitingFor);
    }

    /**
     * Returns the amount all retained workers are waiting for.
     */
    public long getRequestedAmount(AEKey what) {
        return this.waitingIndex.requestedAmount(what);
    }

    /**
     * Returns whether a CPU object is owned by this runtime, including hidden retained workers.
     */
    public boolean hasCpu(Object cpu) {
        if (this.reservedCpu != null && cpu == this.reservedCpu) {
            return true;
        }
        if (!(cpu instanceof TrinityDataCoreVirtualCpu virtualCpu)) {
            return false;
        }
        return this.retainedWorkers.get(virtualCpu.number()) == virtualCpu;
    }

    /**
     * Returns the latest crafting-visible change tick across retained workers.
     */
    public long getLastModifiedOnTick() {
        return this.lastModifiedOnTick;
    }

    /**
     * Captures recent per-worker physical-operation distribution without exposing mutable worker budgets.
     *
     * @return immutable activity snapshot
     */
    public TrinityWorkerDispatchActivity dispatchActivity() {
        long totalOperations = 0L;
        long busiestOperations = 0L;
        for (TrinityDataCoreVirtualCpu worker : this.retainedWorkers.values()) {
            long operations = worker.logic().recentOperationLoad();
            totalOperations = Math.addExact(totalOperations, operations);
            busiestOperations = Math.max(busiestOperations, operations);
        }
        return new TrinityWorkerDispatchActivity(
                this.retainedWorkers.size(),
                totalOperations,
                busiestOperations);
    }

    /**
     * Re-registers persisted links for every retained worker.
     */
    public void restoreLinks(CraftingService service) {
        for (TrinityDataCoreVirtualCpu cpu : this.retainedWorkers.values()) {
            ICraftingLink link = cpu.logic().getLastLink();
            if (link instanceof CraftingLink craftingLink) {
                service.addLink(craftingLink);
            }
        }
    }

    /**
     * Serializes contributions and only workers that retain a job, inventory, or pending raw logic.
     */
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        data.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);

        ListTag contributionsTag = new ListTag();
        for (Map.Entry<String, TrinityDataCoreCpuContribution> entry : this.externalContributions.entrySet()) {
            CompoundTag contributionTag = new CompoundTag();
            contributionTag.putString(CONTRIBUTION_NAME_TAG, entry.getKey());
            writeContribution(contributionTag, entry.getValue());
            contributionsTag.add(contributionTag);
        }
        data.put(CONTRIBUTIONS_TAG, contributionsTag);

        ListTag partitionsTag = new ListTag();
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            TrinityDataCoreVirtualCpu cpu = entry.getValue();
            CompoundTag pendingLogic = this.pendingWorkerLogic.get(entry.getKey());
            if (pendingLogic == null && !cpu.hasRetainedState()) {
                continue;
            }

            CompoundTag partitionTag = new CompoundTag();
            partitionTag.putInt(PARTITION_INDEX_TAG, cpu.number());
            partitionTag.putInt(PARTITION_COUNT_TAG, cpu.workerCapacity());
            partitionTag.putLong(STORAGE_BYTES_TAG, cpu.getAvailableStorage());
            partitionTag.putInt(CO_PROCESSORS_TAG, cpu.getCoProcessors());
            partitionTag.putString(SELECTION_MODE_TAG, cpu.getSelectionMode().name());
            partitionTag.put(
                    PARTITION_LOGIC_TAG,
                    pendingLogic != null ? pendingLogic.copy() : cpu.logic().writeToTag(registries));
            partitionsTag.add(partitionTag);
        }
        data.put(PARTITIONS_TAG, partitionsTag);
    }

    /**
     * Restores contributions and normalizes persisted workers before their level-dependent logic is decoded.
     */
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        clearPersistedState();
        if (!data.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            Data_Energistics.LOGGER.warn("Ignoring Trinity Data Core CPU runtime without a schema version");
            return;
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != LEGACY_SCHEMA_VERSION && schemaVersion != SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring Trinity Data Core CPU runtime schema version {}; expected {} or {}",
                    schemaVersion,
                    LEGACY_SCHEMA_VERSION,
                    SCHEMA_VERSION);
            return;
        }
        ListTag contributionsTag;
        ListTag partitionsTag;
        try {
            contributionsTag = readCompoundList(data, CONTRIBUTIONS_TAG);
            partitionsTag = readCompoundList(data, PARTITIONS_TAG);
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.error("Ignoring invalid Trinity Data Core CPU runtime lists", exception);
            return;
        }

        Map<String, TrinityDataCoreCpuContribution> restoredContributions = readContributions(contributionsTag);
        TrinityDataCoreCpuProfile restoredProfile;
        try {
            restoredProfile = TrinityDataCoreCpuProfile.fromContributions(restoredContributions);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Ignoring invalid Trinity CPU contribution aggregate", exception);
            return;
        }
        this.externalContributions.putAll(restoredContributions);
        applyProfile(restoredProfile);
        restorePendingWorkers(partitionsTag, schemaVersion);
        restorePendingPartitionLogic(registries);
    }

    /**
     * Discards all persisted CPU contributions and jobs after the owning host rejects its root NBT schema.
     */
    public void discardPersistedState() {
        clearPersistedState();
    }

    /**
     * Restores pending worker logic once the owning block entity is attached to a level.
     */
    public void restorePendingPartitionLogic(HolderLookup.Provider registries) {
        if (this.pendingWorkerLogic.isEmpty() || this.host.getLevel() == null) {
            return;
        }

        var iterator = this.pendingWorkerLogic.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CompoundTag> entry = iterator.next();
            TrinityDataCoreVirtualCpu worker = this.retainedWorkers.get(entry.getKey());
            if (worker == null) {
                Data_Energistics.LOGGER.error(
                        "Cannot restore Trinity CPU worker {} because its runtime object is missing",
                        entry.getKey());
                iterator.remove();
                continue;
            }
            try {
                worker.logic().readFromTag(entry.getValue(), registries);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Rejecting invalid persisted logic for Trinity CPU worker {}",
                        entry.getKey(),
                        exception);
                worker.logic().discardPersistedState();
            }
            iterator.remove();
        }
        releaseReleasableWorkers();
        rebuildRuntimeCaches();
    }

    /**
     * Returns whether this CPU remains an active member of the current worker capacity.
     */
    boolean isCurrentCpu(TrinityDataCoreVirtualCpu cpu) {
        if (!this.mainStructureFormed || !this.profile.active()) {
            return false;
        }
        if (cpu.number() == 0) {
            return cpu == this.reservedCpu;
        }
        return cpu.number() <= this.profile.partitionCount() &&
                this.retainedWorkers.get(cpu.number()) == cpu;
    }

    /**
     * Applies one key-level CPU change to the aggregate waiting index and modification cache.
     */
    void workerCraftingVisibleChanged(TrinityDataCoreVirtualCpu worker, AEKey what) {
        if (this.retainedWorkers.get(worker.number()) != worker) {
            return;
        }
        this.waitingIndex.update(worker.number(), what, worker.getWaitingFor(what));
        cacheLastModified(worker);
    }

    /**
     * Synchronizes caches after a worker operation that may start or finish a job.
     */
    void workerOperationCompleted(TrinityDataCoreVirtualCpu worker, boolean wasBusy) {
        if (this.retainedWorkers.get(worker.number()) != worker) {
            return;
        }
        cacheLastModified(worker);
        scheduleFromEvent(worker);
        if (wasBusy != worker.isBusy()) {
            refreshWorkerWaiting(worker);
            rebuildPublishedCpus();
        }
        removeWorkerIfReleasable(worker.number(), worker);
    }

    /**
     * Replaces the worker's transient schedule after its persisted suspension state changes.
     */
    void workerSuspensionChanged(TrinityDataCoreVirtualCpu worker) {
        if (this.retainedWorkers.get(worker.number()) != worker) {
            return;
        }
        cacheLastModified(worker);
        scheduleFromEvent(worker);
    }

    /**
     * Enqueues one background proposal completion without touching worker or grid state off-thread.
     */
    void workerProposalCompleted(int workerNumber) {
        this.proposalCompletions.add(workerNumber);
    }

    /**
     * @return stable host identity used by transient worker proposal leases
     */
    UUID runtimeId() {
        return this.host.getHostId();
    }

    /**
     * @return process-local generation of this runtime instance
     */
    long runtimeGeneration() {
        return this.runtimeGeneration;
    }

    private int findAvailableWorkerNumber() {
        return this.nextAvailableWorkerNumber <= this.profile.partitionCount() ? this.nextAvailableWorkerNumber : -1;
    }

    private void removeWorkerIfReleasable(int number, TrinityDataCoreVirtualCpu worker) {
        if (!this.pendingWorkerLogic.containsKey(number) && worker.isReleasable()) {
            if (this.retainedWorkers.remove(number, worker)) {
                this.waitingIndex.removeWorker(number);
                invalidateWorkerSchedule(number);
                makeWorkerNumberAvailable(number);
            }
        }
    }

    private void releaseReleasableWorkers() {
        var iterator = this.retainedWorkers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry = iterator.next();
            if (!this.pendingWorkerLogic.containsKey(entry.getKey()) && entry.getValue().isReleasable()) {
                this.waitingIndex.removeWorker(entry.getKey());
                invalidateWorkerSchedule(entry.getKey());
                makeWorkerNumberAvailable(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void drainProposalCompletions() {
        Integer workerNumber;
        while ((workerNumber = this.proposalCompletions.poll()) != null) {
            TrinityDataCoreVirtualCpu worker = this.retainedWorkers.get(workerNumber);
            if (worker != null && isCurrentCpu(worker)) {
                enqueueReady(workerNumber);
            }
        }
    }

    private void activateDueWorkerRetries(long currentTick) {
        while (!this.providerRetries.isEmpty() && this.providerRetries.peek().retryAt() <= currentTick) {
            WorkerScheduleEntry retry = this.providerRetries.remove();
            if (isCurrentSchedule(retry)) {
                enqueueReady(retry.workerNumber());
            }
        }
    }

    /**
     * Orders only event-selected workers around the persistent cursor; unrelated idle workers are never visited.
     */
    private void orderReadyWorkersFromCursor() {
        if (this.readyWorkers.size() < 2) {
            return;
        }
        List<WorkerScheduleEntry> ordered = new ArrayList<>(this.readyWorkers);
        ordered.sort((left, right) -> {
            boolean leftAfterCursor = left.workerNumber() >= this.nextWorkerTickStartNumber;
            boolean rightAfterCursor = right.workerNumber() >= this.nextWorkerTickStartNumber;
            if (leftAfterCursor != rightAfterCursor) {
                return leftAfterCursor ? -1 : 1;
            }
            return Integer.compare(left.workerNumber(), right.workerNumber());
        });
        this.readyWorkers.clear();
        this.readyWorkers.addAll(ordered);
    }

    private boolean consumeReadySchedule(WorkerScheduleEntry scheduled) {
        if (!isCurrentSchedule(scheduled) || !this.readyWorkerNumbers.remove(scheduled.workerNumber())) {
            return false;
        }
        return true;
    }

    private void scheduleAfterWorkerTick(TrinityDataCoreVirtualCpu worker, long currentTick) {
        TrinityWorkerSchedulingHint hint = worker.logic().schedulingHint(currentTick);
        if (hint.kind() == TrinityWorkerSchedulingHint.Kind.READY) {
            scheduleRetry(worker.number(), currentTick);
            return;
        }
        applySchedulingHint(worker.number(), hint);
    }

    private void scheduleFromEvent(TrinityDataCoreVirtualCpu worker) {
        if (this.paused || !isCurrentCpu(worker)) {
            return;
        }
        TrinityWorkerSchedulingHint hint = worker.logic().schedulingHint(TickHandler.instance().getCurrentTick());
        if (hint.kind() == TrinityWorkerSchedulingHint.Kind.READY) {
            enqueueReady(worker.number());
            return;
        }
        applySchedulingHint(worker.number(), hint);
    }

    private void applySchedulingHint(int workerNumber, TrinityWorkerSchedulingHint hint) {
        switch (hint.kind()) {
            case READY -> enqueueReady(workerNumber);
            case RETRY_AT -> scheduleRetry(workerNumber, hint.retryAt());
            case WAITING_EVENT, IDLE -> invalidateWorkerSchedule(workerNumber);
        }
    }

    private void enqueueReady(int workerNumber) {
        if (this.readyWorkerNumbers.contains(workerNumber)) {
            return;
        }
        long revision = nextWorkerScheduleRevision(workerNumber);
        this.readyWorkerNumbers.add(workerNumber);
        this.readyWorkers.addLast(new WorkerScheduleEntry(workerNumber, revision, -1L));
    }

    private void scheduleRetry(int workerNumber, long retryAt) {
        long revision = nextWorkerScheduleRevision(workerNumber);
        this.readyWorkerNumbers.remove(workerNumber);
        this.providerRetries.add(new WorkerScheduleEntry(workerNumber, revision, retryAt));
    }

    private void invalidateWorkerSchedule(int workerNumber) {
        nextWorkerScheduleRevision(workerNumber);
        this.readyWorkerNumbers.remove(workerNumber);
    }

    private long nextWorkerScheduleRevision(int workerNumber) {
        long revision = Math.incrementExact(this.workerScheduleRevisions.getOrDefault(workerNumber, 0L));
        this.workerScheduleRevisions.put(workerNumber, revision);
        return revision;
    }

    private boolean isCurrentSchedule(WorkerScheduleEntry entry) {
        return this.workerScheduleRevisions.getOrDefault(entry.workerNumber(), -1L) == entry.revision();
    }

    private void rebuildWorkerScheduling() {
        clearTransientScheduling();
        if (this.paused || !this.mainStructureFormed || !this.profile.active()) {
            return;
        }
        long currentTick = TickHandler.instance().getCurrentTick();
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (entry.getKey() > this.profile.partitionCount()) {
                continue;
            }
            TrinityDataCoreVirtualCpu worker = entry.getValue();
            TrinityWorkerSchedulingHint hint = worker.logic().schedulingHint(currentTick);
            if (hint.kind() == TrinityWorkerSchedulingHint.Kind.READY) {
                enqueueReady(worker.number());
            } else {
                applySchedulingHint(worker.number(), hint);
            }
        }
    }

    private void clearTransientScheduling() {
        this.readyWorkers.clear();
        this.readyWorkerNumbers.clear();
        this.providerRetries.clear();
        this.workerScheduleRevisions.clear();
        this.proposalCompletions.clear();
    }

    /**
     * Advances fairness only after a worker actually consumes shared physical dispatch capacity.
     */
    private void advanceWorkerTickStartAfter(int workerNumber) {
        if (this.retainedWorkers.isEmpty()) {
            this.nextWorkerTickStartNumber = 1;
            return;
        }
        Integer followingNumber = this.retainedWorkers.higherKey(workerNumber);
        this.nextWorkerTickStartNumber = followingNumber != null ? followingNumber : this.retainedWorkers.firstKey();
    }

    private void applyProfile(TrinityDataCoreCpuProfile nextProfile) {
        this.profile = nextProfile;
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (!nextProfile.active() || entry.getKey() > nextProfile.partitionCount()) {
                entry.getValue().logic().cancelPendingDispatch();
            }
        }
        if (!nextProfile.active()) {
            rebuildAvailableWorkerNumber();
            rebuildWorkerScheduling();
            rebuildPublishedCpus();
            return;
        }

        TrinityDataCoreCpuPartitionProfile coordinatorProfile = nextProfile.partition(0);
        if (this.reservedCpu == null) {
            this.reservedCpu = new TrinityDataCoreVirtualCpu(this.host, this, coordinatorProfile);
        } else {
            this.reservedCpu.updateProfile(coordinatorProfile);
        }

        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (entry.getKey() <= nextProfile.partitionCount()) {
                entry.getValue().updateProfile(nextProfile.partition(entry.getKey()));
            }
        }
        rebuildAvailableWorkerNumber();
        rebuildWorkerScheduling();
        rebuildPublishedCpus();
    }

    /**
     * Rebuilds all derived runtime caches after persisted logic has been decoded.
     */
    private void rebuildRuntimeCaches() {
        rebuildWaitingIndex();
        rebuildAvailableWorkerNumber();
        this.lastModifiedOnTick = 0L;
        for (TrinityDataCoreVirtualCpu worker : this.retainedWorkers.values()) {
            cacheLastModified(worker);
        }
        rebuildWorkerScheduling();
        rebuildPublishedCpus();
    }

    /**
     * Rebuilds the waiting index from authoritative worker jobs after load or bulk cancellation.
     */
    private void rebuildWaitingIndex() {
        this.waitingIndex.clear();
        for (TrinityDataCoreVirtualCpu worker : this.retainedWorkers.values()) {
            refreshWorkerWaiting(worker);
        }
    }

    /**
     * Replaces all indexed keys for one worker without exposing a partially rebuilt membership.
     */
    private void refreshWorkerWaiting(TrinityDataCoreVirtualCpu worker) {
        this.waitingIndex.removeWorker(worker.number());
        Set<AEKey> workerKeys = new HashSet<>();
        worker.getAllWaitingFor(workerKeys);
        for (AEKey what : workerKeys) {
            this.waitingIndex.update(worker.number(), what, worker.getWaitingFor(what));
        }
    }

    /**
     * Updates the O(1) last-modified query while a worker is already being visited.
     */
    private void cacheLastModified(TrinityDataCoreVirtualCpu worker) {
        this.lastModifiedOnTick = Math.max(this.lastModifiedOnTick, worker.getLastModifiedOnTick());
    }

    /**
     * Replaces the immutable AE2 CPU view only when publication topology changes.
     */
    private void rebuildPublishedCpus() {
        TrinityDataCoreVirtualCpu coordinator = this.reservedCpu;
        if (this.paused || !this.mainStructureFormed || !this.profile.active() || coordinator == null) {
            replacePublishedCpus(List.of());
            return;
        }

        List<TrinityDataCoreVirtualCpu> published = new ArrayList<>(this.retainedWorkers.size() + 1);
        published.add(coordinator);
        for (Map.Entry<Integer, TrinityDataCoreVirtualCpu> entry : this.retainedWorkers.entrySet()) {
            if (entry.getKey() <= this.profile.partitionCount() && entry.getValue().isBusy()) {
                published.add(entry.getValue());
            }
        }
        replacePublishedCpus(List.copyOf(published));
    }

    /**
     * Preserves the list identity when a repeated lifecycle event leaves CPU publication unchanged.
     */
    private void replacePublishedCpus(List<TrinityDataCoreVirtualCpu> nextPublishedCpus) {
        if (!this.publishedCpus.equals(nextPublishedCpus)) {
            this.publishedCpus = nextPublishedCpus;
        }
    }

    /**
     * Advances the allocation cursor after occupying its current minimum worker number.
     */
    private void advanceAvailableWorkerNumber() {
        while (this.nextAvailableWorkerNumber <= this.profile.partitionCount() &&
                this.retainedWorkers.containsKey(this.nextAvailableWorkerNumber)) {
            this.nextAvailableWorkerNumber++;
        }
    }

    /**
     * Makes a released lower worker number the next allocation candidate.
     */
    private void makeWorkerNumberAvailable(int workerNumber) {
        if (workerNumber < this.nextAvailableWorkerNumber) {
            this.nextAvailableWorkerNumber = workerNumber;
        }
    }

    /**
     * Recomputes the minimum free worker after profile or persisted-state replacement.
     */
    private void rebuildAvailableWorkerNumber() {
        this.nextAvailableWorkerNumber = 1;
        advanceAvailableWorkerNumber();
    }

    private Map<String, TrinityDataCoreCpuContribution> readContributions(ListTag contributionsTag) {
        Map<String, TrinityDataCoreCpuContribution> restored = new TreeMap<>();
        for (int index = 0; index < contributionsTag.size(); index++) {
            CompoundTag contributionTag = contributionsTag.getCompound(index);
            try {
                String structureName = requireStructureName(contributionTag.getString(CONTRIBUTION_NAME_TAG));
                restored.put(structureName, readContribution(contributionTag));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Rejecting invalid Trinity CPU contribution at persisted index {}",
                        index,
                        exception);
            }
        }
        return restored;
    }

    private static ListTag readCompoundList(CompoundTag data, String name) {
        Tag rawTag = data.get(name);
        if (!(rawTag instanceof ListTag listTag) ||
                (!listTag.isEmpty() && listTag.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Persisted Trinity CPU tag '" + name + "' is not a compound list");
        }
        return listTag;
    }

    private void restorePendingWorkers(ListTag partitionsTag, int schemaVersion) {
        Set<Integer> seenWorkerNumbers = new HashSet<>();
        for (int tagIndex = 0; tagIndex < partitionsTag.size(); tagIndex++) {
            CompoundTag partitionTag = partitionsTag.getCompound(tagIndex);
            try {
                TrinityDataCoreCpuPartitionProfile savedProfile = readWorkerProfile(partitionTag, schemaVersion);
                int workerNumber = savedProfile.index();
                if (!seenWorkerNumbers.add(workerNumber)) {
                    Data_Energistics.LOGGER.error(
                            "Rejecting duplicate persisted Trinity CPU worker {} at index {}",
                            workerNumber,
                            tagIndex);
                    continue;
                }
                if (!partitionTag.contains(PARTITION_LOGIC_TAG, Tag.TAG_COMPOUND)) {
                    Data_Energistics.LOGGER.error(
                            "Rejecting persisted Trinity CPU worker {} without compound logic",
                            workerNumber);
                    continue;
                }
                CompoundTag pendingLogic = partitionTag.getCompound(PARTITION_LOGIC_TAG);
                if (!TrinityDataCoreCpuLogic.persistedHasRetainedState(pendingLogic)) {
                    continue;
                }

                TrinityDataCoreCpuPartitionProfile effectiveProfile = savedProfile;
                if (this.profile.active() && workerNumber <= this.profile.partitionCount()) {
                    effectiveProfile = this.profile.partition(workerNumber);
                }
                TrinityDataCoreVirtualCpu worker = new TrinityDataCoreVirtualCpu(this.host, this, effectiveProfile);
                this.retainedWorkers.put(workerNumber, worker);
                this.pendingWorkerLogic.put(workerNumber, pendingLogic.copy());
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Rejecting invalid persisted Trinity CPU worker at index {}",
                        tagIndex,
                        exception);
            }
        }
        rebuildAvailableWorkerNumber();
    }

    private static TrinityDataCoreCpuPartitionProfile readWorkerProfile(CompoundTag data, int schemaVersion) {
        if (!data.contains(PARTITION_INDEX_TAG, Tag.TAG_INT) ||
                !data.contains(PARTITION_COUNT_TAG, Tag.TAG_INT) ||
                !data.contains(STORAGE_BYTES_TAG, Tag.TAG_LONG) ||
                !data.contains(CO_PROCESSORS_TAG, Tag.TAG_INT) ||
                !data.contains(SELECTION_MODE_TAG, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Persisted Trinity CPU worker profile is incomplete");
        }

        int persistedIndex = data.getInt(PARTITION_INDEX_TAG);
        int workerCapacity = data.getInt(PARTITION_COUNT_TAG);
        int workerNumber;
        if (schemaVersion == LEGACY_SCHEMA_VERSION) {
            if (persistedIndex < 0 || persistedIndex >= workerCapacity) {
                throw new IllegalArgumentException("Legacy Trinity CPU partition index is out of range: " + persistedIndex);
            }
            workerNumber = Math.addExact(persistedIndex, 1);
        } else {
            if (persistedIndex <= 0 || persistedIndex > workerCapacity) {
                throw new IllegalArgumentException("Trinity CPU worker number is out of range: " + persistedIndex);
            }
            workerNumber = persistedIndex;
        }

        return new TrinityDataCoreCpuPartitionProfile(
                workerNumber,
                workerCapacity,
                data.getLong(STORAGE_BYTES_TAG),
                data.getInt(CO_PROCESSORS_TAG),
                CpuSelectionMode.valueOf(data.getString(SELECTION_MODE_TAG)));
    }

    private void clearPersistedState() {
        for (TrinityDataCoreVirtualCpu worker : this.retainedWorkers.values()) {
            worker.logic().cancelPendingDispatch();
        }
        clearTransientScheduling();
        this.externalContributions.clear();
        this.retainedWorkers.clear();
        this.pendingWorkerLogic.clear();
        this.waitingIndex.clear();
        this.profile = TrinityDataCoreCpuProfile.EMPTY;
        this.publishedCpus = List.of();
        this.lastModifiedOnTick = 0L;
        this.nextAvailableWorkerNumber = 1;
        this.nextWorkerTickStartNumber = 1;
    }

    /**
     * Immutable queue entry invalidated by the per-worker transient scheduling revision.
     */
    private record WorkerScheduleEntry(int workerNumber, long revision, long retryAt)
            implements Comparable<WorkerScheduleEntry> {

        @Override
        public int compareTo(WorkerScheduleEntry other) {
            int byTick = Long.compare(this.retryAt, other.retryAt);
            if (byTick != 0) {
                return byTick;
            }
            int byWorker = Integer.compare(this.workerNumber, other.workerNumber);
            return byWorker != 0 ? byWorker : Long.compare(this.revision, other.revision);
        }
    }

    private static void writeContribution(CompoundTag data, TrinityDataCoreCpuContribution contribution) {
        data.putLong(STORAGE_BYTES_TAG, contribution.storageBytes());
        data.putInt(CO_PROCESSORS_TAG, contribution.coProcessors());
        data.putInt(PARTITION_COUNT_TAG, contribution.partitionCount());
        data.putString(SELECTION_MODE_TAG, contribution.selectionMode().name());
    }

    private static TrinityDataCoreCpuContribution readContribution(CompoundTag data) {
        if (!data.contains(STORAGE_BYTES_TAG, Tag.TAG_LONG) ||
                !data.contains(CO_PROCESSORS_TAG, Tag.TAG_INT) ||
                !data.contains(PARTITION_COUNT_TAG, Tag.TAG_INT) ||
                !data.contains(SELECTION_MODE_TAG, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Persisted Trinity CPU contribution is incomplete");
        }
        CpuSelectionMode selectionMode = CpuSelectionMode.valueOf(data.getString(SELECTION_MODE_TAG));
        return new TrinityDataCoreCpuContribution(
                data.getLong(STORAGE_BYTES_TAG),
                data.getInt(CO_PROCESSORS_TAG),
                data.getInt(PARTITION_COUNT_TAG),
                selectionMode);
    }

    private static String requireStructureName(String structureName) {
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("CPU contribution structure name must not be blank");
        }
        return structureName;
    }
}
