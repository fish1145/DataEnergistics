package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletion;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletionMode;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutput;
import com.fish_dan_.data_energistics.common.crafting.dynamic.DynamicCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.dynamic.DynamicCraftingOutputResolutionException;
import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle.TrinityDispatchProposalLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchExclusion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerProposalCoordinator;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerSchedulingHint;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchCapacityPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchCapacitySlicePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityCapture;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityResolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchCommitRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchCommitter;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchAccountingDelta;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.WorkerOperationTracker;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchStepResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityBoundPatternDetails;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternResolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.route.TrinityCraftingExecutionRoute;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityBorrowingTransaction;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityCompletionInputExtractor;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityInitialInputExtractor;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityRemainingPlanCalculation;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinitySameItemInputInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory.TrinityExactWorkingInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityCraftingGraphAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityAvailableAmount;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventorySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection.TrinityAe2AmountProjection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputProjection;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Executes one virtual Trinity Data Core crafting CPU partition.
 *
 * <p>
 * This logic follows AE2's CPU execution flow while replacing native cluster callbacks with the Trinity Data Core
 * runtime.
 */
final class TrinityDataCoreCpuLogic {

    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 3;
    private static final int LONG_INVENTORY_SCHEMA_VERSION = 2;
    private static final String INVENTORY_TAG = "inventory";
    private static final String EXACT_INVENTORY_TAG = "exact_inventory";
    private static final String VIRTUAL_COMPLETIONS_TAG = "virtual_completions";
    private static final String NO_OUTPUT_VIRTUAL_COMPLETIONS_TAG = "no_output_virtual_completions";
    private static final String JOB_TAG = "job";
    private static final double ENERGY_TOLERANCE = 0.01D;
    /** The shared dispatch window, rather than structure co-processors, owns the physical-operation limit. */
    private static final int UNLIMITED_WORKER_OPERATIONS = Integer.MAX_VALUE;

    private final TrinityDataCoreVirtualCpu cpu;
    private final CraftingDispatchCommitter dispatchCommitter = CraftingDispatchCommitter.create();
    private final ProviderCapacityResolver capacityResolver = ProviderCapacityResolver.create(
            TrinityDispatchProposalLifecycle::dispatchComputationCache);
    private final DispatchCapacityPlanner capacityPlanner = DispatchCapacityPlanner.create(
            TrinityDispatchProposalLifecycle::dispatchComputationCache);
    private final TrinityPatternResolver patternResolver = TrinityPatternResolver.create();
    private final TrinityPatternSelector patternSelector = TrinityPatternSelector.create();
    private final TrinityRemainingPlanCalculation remainingPlanCalculation = TrinityRemainingPlanCalculation.create(TrinityPlanningGatewayLifecycle::gateway);
    private final TrinityWorkerProposalCoordinator proposalCoordinator = TrinityWorkerProposalCoordinator.create(
            TrinityDispatchProposalLifecycle::scheduler);
    @Nullable
    private TrinityDataCoreExecutingCraftingJob job;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final TrinityExactWorkingInventory exactWorkingInventory = new TrinityExactWorkingInventory();
    private final ListCraftingInventory pendingVirtualCompletions;
    private final ListCraftingInventory pendingNoOutputCompletions;
    private final WorkerOperationTracker operationTracker = WorkerOperationTracker.create();
    private final Set<Consumer<AEKey>> listeners = new ObjectOpenHashSet<>();
    private boolean cantStoreItems;
    private CraftingDispatchCursor capacitySliceCursor = CraftingDispatchCursor.initial();
    private long proposalRetryAt = -1L;
    private long jobRevision;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    private long preparedServerTick = Long.MIN_VALUE;

    TrinityDataCoreCpuLogic(TrinityDataCoreVirtualCpu cpu) {
        this.cpu = cpu;
        this.pendingVirtualCompletions = new ListCraftingInventory(ignored -> this.cpu.markDirty());
        this.pendingNoOutputCompletions = new ListCraftingInventory(ignored -> this.cpu.markDirty());
    }

    /**
     * @return CPU partition owning this logic
     */
    TrinityDataCoreVirtualCpu cpu() {
        return this.cpu;
    }

    KeyCounter dynamicInputInventory() {
        return this.inventory.list;
    }

    /**
     * Attempts to bind and start a crafting plan on this CPU partition.
     *
     * @param grid      AE2 grid that owns the request
     * @param plan      calculated crafting plan
     * @param source    action source used to extract initial ingredients
     * @param requester optional requester that receives final outputs
     * @return submit result
     */
    ICraftingSubmitResult trySubmitJob(IGrid grid,
                                       ICraftingPlan plan,
                                       IActionSource source,
                                       @Nullable ICraftingRequester requester) {
        if (!this.cpu.isActiveOnGrid(grid)) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.job != null) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (plan instanceof TrinityCraftingPlan trinityPlan ?
                !this.cpu.storageCapacity().accepts(trinityPlan.exactBytes()) :
                this.cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        if (!this.inventory.list.isEmpty() || !this.exactWorkingInventory.isEmpty()) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU inventory is not empty when a job is submitted");
        }

        GenericStack missingIngredient = plan instanceof TrinityCraftingPlan trinityPlan ?
                TrinityInitialInputExtractor.extract(
                        trinityPlan,
                        grid,
                        this.inventory,
                        this.exactWorkingInventory,
                        source) :
                CraftingCpuHelper.tryExtractInitialItems(plan, grid, this.inventory, source);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        // Initial ingredient extraction transfers ownership before the executable job can be constructed.
        this.cpu.markDirty();

        Integer playerId = source.player()
                .map(player -> player instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        UUID craftId = UUID.randomUUID();
        CraftingLink linkCpu = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, requester == null, false),
                this.cpu);
        this.job = new TrinityDataCoreExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
        this.jobRevision = Math.incrementExact(this.jobRevision);
        this.capacitySliceCursor = CraftingDispatchCursor.initial();
        this.proposalRetryAt = -1L;
        this.cpu.markDirty();
        notifyJobOwner(this.job, CraftingJobStatusPacket.Status.STARTED);

        if (requester != null) {
            CraftingLink linkRequester = new CraftingLink(
                    CraftingCpuHelper.generateLinkData(craftId, false, true),
                    requester);
            CraftingService craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkRequester);
            return CraftingSubmitResult.successful(linkRequester);
        }
        return CraftingSubmitResult.successful(null);
    }

    /**
     * Advances pattern dispatch and inventory cleanup for one server tick.
     *
     * @param energyService   AE2 energy service
     * @param craftingService AE2 crafting service
     */
    CraftingDispatchStepResult dispatchStep(IEnergyService energyService,
                                            CraftingService craftingService,
                                            CraftingDispatchWindow dispatchWindow,
                                            CraftingDispatchBudget dispatchBudget) {
        long currentTick = TickHandler.instance().getCurrentTick();
        WorkerProgressSnapshot before = progressSnapshot(currentTick);
        prepareTick(currentTick, dispatchBudget);
        if (!readyForDispatch(currentTick) || dispatchWindow.isExhausted()) {
            return stepResult(before, currentTick, 0, dispatchWindow);
        }

        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null) {
            return stepResult(before, currentTick, 0, dispatchWindow);
        }
        TrinityPlanExecution execution = currentJob.isTrinityPlan() ? currentJob.trinityExecution() : null;
        long durableRevision = execution == null ? 0L : execution.durableRevision();
        Level level = this.cpu.level();
        if (level == null) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU cannot tick crafting job without a level");
            return stepResult(before, currentTick, 0, dispatchWindow);
        }

        int physicalAttempts;
        try {
            int workPassLimit = currentJob.isTrinityPlan() ?
                    dispatchBudget.providerQuantum() : 1;
            CraftingExecutionOutcome outcome = executeCrafting(
                    workPassLimit,
                    craftingService,
                    energyService,
                    level,
                    dispatchWindow,
                    dispatchBudget);
            physicalAttempts = outcome.physicalAttempts();
            this.operationTracker.recordTickUsage(currentTick, physicalAttempts);
        } finally {
            if (execution != null && execution.durableRevision() != durableRevision) {
                this.cpu.markDirty();
            }
        }
        return stepResult(before, currentTick, physicalAttempts, dispatchWindow);
    }

    /**
     * Retains the historical complete-worker pass for direct compatibility callers outside the central scheduler.
     */
    void tickCraftingLogic(IEnergyService energyService,
                           CraftingService craftingService,
                           CraftingDispatchWindow dispatchWindow,
                           CraftingDispatchBudget dispatchBudget) {
        long currentTick = TickHandler.instance().getCurrentTick();
        prepareTick(currentTick, dispatchBudget);
        if (!readyForDispatch(currentTick) || dispatchWindow.isExhausted()) {
            return;
        }

        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null) {
            return;
        }
        int remainingOperations = UNLIMITED_WORKER_OPERATIONS;
        int started = remainingOperations;
        TrinityPlanExecution execution = currentJob.isTrinityPlan() ? currentJob.trinityExecution() : null;
        long durableRevision = execution == null ? 0L : execution.durableRevision();
        Level level = this.cpu.level();
        if (level == null) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU cannot tick crafting job without a level");
            return;
        }

        try {
            while (remainingOperations > 0 && !dispatchWindow.isExhausted()) {
                CraftingExecutionOutcome outcome = executeCrafting(
                        remainingOperations,
                        craftingService,
                        energyService,
                        level,
                        dispatchWindow,
                        dispatchBudget);
                int physicalAttempts = outcome.physicalAttempts();
                if (physicalAttempts <= 0) {
                    break;
                }
                remainingOperations -= physicalAttempts;
                if (!outcome.dispatched()) {
                    break;
                }
            }
            this.operationTracker.recordTickUsage(currentTick, started - remainingOperations);
        } finally {
            if (execution != null && execution.durableRevision() != durableRevision) {
                this.cpu.markDirty();
            }
        }
    }

    private void prepareTick(long currentTick, CraftingDispatchBudget dispatchBudget) {
        if (this.preparedServerTick == currentTick) {
            return;
        }
        if (this.preparedServerTick > currentTick) {
            throw new IllegalStateException(
                    "Trinity worker tick moved backwards from " + this.preparedServerTick + " to " + currentTick);
        }
        this.preparedServerTick = currentTick;
        if (!this.cpu.isOnline()) {
            cancelPendingDispatch();
            return;
        }
        this.cantStoreItems = false;
        if (this.job == null) {
            recoverVirtualCompletions();
            storeItems();
            if (!this.inventory.list.isEmpty() || !this.exactWorkingInventory.isEmpty()) {
                this.cantStoreItems = true;
            }
            return;
        }
        if (!this.cpu.isActive()) {
            cancelPendingDispatch();
            return;
        }
        if (!dispatchBudget.asynchronousEnabled()) {
            this.proposalCoordinator.cancel();
            this.proposalRetryAt = -1L;
        }
        if (this.job.link.isCanceled()) {
            cancel();
            return;
        }
        if (!drainVirtualCompletions(this.job)) {
            finishJob(false);
            return;
        }
        if (this.job == null) {
            return;
        }
        if (this.job.suspended) {
            if (this.job.isTrinityPlan()) {
                advanceTrinityCompletion(this.job);
            }
            return;
        }
        if (this.proposalRetryAt > currentTick) {
            if (!this.proposalCoordinator.hasActionableProposal()) {
                return;
            }
        } else {
            this.proposalRetryAt = -1L;
        }
    }

    private boolean readyForDispatch(long currentTick) {
        if (!this.cpu.isOnline() || !this.cpu.isActive() || this.job == null || this.job.suspended) {
            return false;
        }
        if (this.job.link.isCanceled() ||
                (this.proposalRetryAt > currentTick && !this.proposalCoordinator.hasActionableProposal())) {
            return false;
        }
        return true;
    }

    private CraftingDispatchStepResult stepResult(WorkerProgressSnapshot before,
                                                  long currentTick,
                                                  int physicalAttempts,
                                                  CraftingDispatchWindow dispatchWindow) {
        WorkerProgressSnapshot after = progressSnapshot(currentTick);
        boolean hasReadyWork = !dispatchWindow.isExhausted() &&
                after.schedulingHint().kind() == TrinityWorkerSchedulingHint.Kind.READY;
        return new CraftingDispatchStepResult(
                physicalAttempts > 0,
                !before.equals(after),
                hasReadyWork,
                dispatchWindow.isExhausted());
    }

    private WorkerProgressSnapshot progressSnapshot(long currentTick) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        long durableRevision = currentJob != null && currentJob.isTrinityPlan() ?
                currentJob.trinityExecution().durableRevision() :
                0L;
        return new WorkerProgressSnapshot(
                this.jobRevision,
                durableRevision,
                this.lastModifiedOnTick,
                this.proposalRetryAt,
                this.capacitySliceCursor,
                this.cantStoreItems,
                this.proposalCoordinator.outstanding(),
                schedulingHint(currentTick));
    }

    /**
     * Dispatches available pattern tasks to AE2 crafting providers.
     *
     * @param maxPatterns     maximum pattern pushes for this tick
     * @param craftingService AE2 crafting service
     * @param energyService   AE2 energy service
     * @param level           server level used by pattern validation
     * @param dispatchWindow  shared per-grid physical submission budget
     * @return physical dispatch operations consumed and whether logical work advanced
     */
    private CraftingExecutionOutcome executeCrafting(int maxPatterns,
                                                     CraftingService craftingService,
                                                     IEnergyService energyService,
                                                     Level level,
                                                     CraftingDispatchWindow dispatchWindow,
                                                     CraftingDispatchBudget dispatchBudget) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null || maxPatterns <= 0) {
            return CraftingExecutionOutcome.NONE;
        }
        if (currentJob.isTrinityPlan()) {
            return executeTrinityCrafting(
                    currentJob,
                    maxPatterns,
                    craftingService,
                    energyService,
                    level,
                    dispatchWindow,
                    dispatchBudget);
        }

        int pushedPatterns = 0;
        boolean dispatched = false;
        var iterator = currentJob.tasks.entrySet().iterator();
        while (!dispatchWindow.isExhausted() &&
                dispatchWindow.canCaptureProviderCapacity() &&
                iterator.hasNext() &&
                pushedPatterns < maxPatterns) {
            var task = iterator.next();
            if (task.getValue().value <= 0) {
                iterator.remove();
                continue;
            }

            var details = task.getKey();
            ProviderDispatchOutcome outcome;
            try {
                outcome = dispatchToAvailableProvider(
                        currentJob,
                        details,
                        task.getValue(),
                        craftingProviderPublications(craftingService),
                        capturePatternIdentity(details, level),
                        energyService,
                        level,
                        dispatchWindow,
                        maxPatterns - pushedPatterns,
                        dispatchBudget);
            } catch (DynamicCraftingOutputResolutionException exception) {
                this.proposalCoordinator.cancel();
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} cancelled job {} before provider ownership because dynamic outputs for pattern {} are invalid",
                        this.cpu.number(),
                        currentJob.link.getCraftingID(),
                        details.getDefinition(),
                        exception);
                finishJob(false);
                return new CraftingExecutionOutcome(pushedPatterns, dispatched);
            } catch (RuntimeException exception) {
                this.proposalCoordinator.cancel();
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} isolated an unexpected dispatch failure for pattern {}",
                        this.cpu.number(),
                        details.getDefinition(),
                        exception);
                throw exception;
            }
            pushedPatterns = Math.addExact(pushedPatterns, outcome.physicalAttempts());
            dispatched |= outcome.dispatched();
            if (outcome.currentProposalOutstanding() || outcome.proposalDeferred()) {
                return new CraftingExecutionOutcome(pushedPatterns, dispatched);
            }
            if (!outcome.dispatched()) {
                if (this.job != currentJob) {
                    return new CraftingExecutionOutcome(pushedPatterns, dispatched);
                }
                continue;
            }

            if (task.getValue().value <= 0L) {
                iterator.remove();
            }
        }

        return new CraftingExecutionOutcome(pushedPatterns, dispatched);
    }

    /**
     * Advances independent compact-plan work items up to the current provider fairness quantum.
     */
    private CraftingExecutionOutcome executeTrinityCrafting(TrinityDataCoreExecutingCraftingJob currentJob,
                                                            int maxPatterns,
                                                            CraftingService craftingService,
                                                            IEnergyService energyService,
                                                            Level level,
                                                            CraftingDispatchWindow dispatchWindow,
                                                            CraftingDispatchBudget dispatchBudget) {
        int physicalAttempts = 0;
        boolean dispatched = false;
        int passes = 0;
        IntSet inspectedStages = new IntOpenHashSet();
        while (passes < maxPatterns && !dispatchWindow.isExhausted() && this.job == currentJob) {
            CraftingExecutionOutcome outcome = executeTrinityCraftingOne(
                    currentJob,
                    craftingService,
                    energyService,
                    level,
                    dispatchWindow,
                    dispatchBudget,
                    inspectedStages);
            physicalAttempts = Math.addExact(physicalAttempts, outcome.physicalAttempts());
            dispatched |= outcome.dispatched();
            passes++;
            if (outcome.proposalDeferred()) {
                break;
            }
            if (outcome.currentProposalOutstanding()) {
                continue;
            }
            if (outcome.physicalAttempts() == 0) {
                break;
            }
        }
        return new CraftingExecutionOutcome(physicalAttempts, dispatched);
    }

    /**
     * Advances one event-selected compact-plan work item without scanning unrelated stages.
     */
    private CraftingExecutionOutcome executeTrinityCraftingOne(TrinityDataCoreExecutingCraftingJob currentJob,
                                                               CraftingService craftingService,
                                                               IEnergyService energyService,
                                                               Level level,
                                                               CraftingDispatchWindow dispatchWindow,
                                                               CraftingDispatchBudget dispatchBudget,
                                                               IntSet inspectedStages) {
        if (advanceTrinityCompletion(currentJob)) {
            return CraftingExecutionOutcome.NONE;
        }

        TrinityPlanExecution execution = currentJob.trinityExecution();
        long currentTick = TickHandler.instance().getCurrentTick();
        TrinityCraftingSchema settings = DataEnergisticsConfiguration.INSTANCE.trinity.crafting;
        if (execution.status() == TrinityPlanExecution.Status.PLANNING) {
            advanceTrinityReplanning(currentJob, craftingService, currentTick);
            return CraftingExecutionOutcome.NONE;
        }
        boolean allowNewProposal = this.proposalRetryAt <= currentTick;
        var workOffer = execution.pollDispatchable(
                currentTick,
                inspectedStages,
                work -> this.proposalCoordinator.dispatchable(work, allowNewProposal),
                allowNewProposal);
        if (workOffer.isEmpty()) {
            if (execution.status() == TrinityPlanExecution.Status.FAILED) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} terminated compact job {}: {}",
                        this.cpu.number(),
                        currentJob.link.getCraftingID(),
                        execution.failureReason().orElse("unknown runtime failure"));
                finishJob(false);
            } else if (execution.deadlocked(
                    !currentJob.waitingFor.list.isEmpty() || !currentJob.dynamicOutputs.isEmpty())) {
                        String reason = "RUNTIME_DEADLOCK: no ready, waiting, retry, planning or in-flight path remains";
                        execution.fail(reason);
                        Data_Energistics.LOGGER.error(
                                "Trinity CPU {} detected deadlock for compact job {}",
                                this.cpu.number(),
                                currentJob.link.getCraftingID());
                        finishJob(false);
                    }
            return CraftingExecutionOutcome.NONE;
        }

        TrinityPlanExecution.Work work = workOffer.orElseThrow();
        if (!inspectedStages.add(work.stageIndex())) {
            throw new IllegalStateException("A Trinity worker pass selected the same stage twice");
        }
        TrinityPatternResolver.Resolution resolution;
        try {
            resolution = this.patternResolver.resolve(
                    work.patternIdentity(),
                    work.primaryOutput(),
                    craftingService,
                    level.registryAccess());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} failed to recapture pattern {} for compact job {}",
                    this.cpu.number(),
                    work.patternIdentity().definitionEncoding(),
                    currentJob.link.getCraftingID(),
                    exception);
            execution.deferProvider(
                    work,
                    currentTick,
                    settings.dynamicRetryMaxTicks);
            return CraftingExecutionOutcome.NONE;
        }
        if (!(resolution instanceof TrinityPatternResolver.Matched(IPatternDetails pattern))) {
            this.proposalCoordinator.cancel();
            execution.markPlanning(work);
            advanceTrinityReplanning(currentJob, craftingService, currentTick);
            return CraftingExecutionOutcome.NONE;
        }

        IGrid activeGrid = this.cpu.grid();
        if (activeGrid == null) {
            execution.deferProvider(
                    work,
                    currentTick,
                    settings.dynamicRetryMaxTicks);
            return CraftingExecutionOutcome.NONE;
        }
        MEStorage network = activeGrid.getStorageService().getInventory();
        if (this.exactWorkingInventory.refillPhysicalWindows(this.inventory)) {
            this.cpu.markDirty();
        }
        long maximumLogicalFirings = work.maximumLogicalFirings();
        if (work.cycle()) {
            TrinityPlanExecution.CycleWaveLimit cycleWaveLimit;
            try {
                cycleWaveLimit = execution.maximumCycleLogicalFirings(
                        work,
                        (key, usefulUpper) -> combinedCycleSeedAvailability(
                                network, execution.sameItemPolicy(), key, usefulUpper));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} failed to calculate the seed-safe cycle wave for compact job {}",
                        this.cpu.number(),
                        currentJob.link.getCraftingID(),
                        exception);
                execution.fail("CYCLE_SEED_LIMIT: " + exception.getClass().getSimpleName());
                return CraftingExecutionOutcome.NONE;
            }
            maximumLogicalFirings = cycleWaveLimit.maximumLogicalFirings();
            if (maximumLogicalFirings == 0L) {
                execution.deferDynamicInput(
                        work,
                        cycleWaveLimit.observedKeys(),
                        currentTick,
                        settings.dynamicRetryMaxTicks);
                return CraftingExecutionOutcome.NONE;
            }
        }
        TrinitySameItemInputInventory sameItemInputs = new TrinitySameItemInputInventory(
                execution.sameItemPolicy(), this.inventory.list,
                work.cycle() ? network.getAvailableStacks() : new KeyCounter(),
                key -> simulateNetworkExtraction(network, key));
        TrinityPatternSelector.Result selection = this.patternSelector.select(
                pattern,
                work.plannedVariantOrdinal(),
                work.cycle(),
                maximumLogicalFirings,
                this.inventory.list::get,
                key -> work.cycle() && !currentJob.dynamicOutputs.isInputAlias(key) ?
                        simulateNetworkExtraction(network, key) : 0L,
                key -> execution.sameItemPolicy().allowsSameItem(key) ? sameItemInputs.candidates(key) :
                        currentJob.dynamicOutputs.resolveInputs(key, this.inventory.list),
                settings.maxBindingVariants);
        TrinityPatternSelector.Selected selected;
        switch (selection) {
            case TrinityPatternSelector.Selected value -> selected = value;
            case TrinityPatternSelector.Unavailable unavailable -> {
                if (work.cycle()) {
                    execution.deferDynamicInput(
                            work,
                            unavailable.observedKeys(),
                            currentTick,
                            settings.dynamicRetryMaxTicks);
                } else {
                    execution.deferInput(work, unavailable.observedKeys());
                }
                return CraftingExecutionOutcome.NONE;
            }
            case TrinityPatternSelector.VariantLimit limit -> {
                execution.fail("VARIANT_LIMIT: runtime binding requires " + limit.required() +
                        " variants, configured limit is " + limit.limit());
                return CraftingExecutionOutcome.NONE;
            }
            case TrinityPatternSelector.ArithmeticOverflow overflow -> {
                execution.fail("ARITHMETIC_OVERFLOW: " + overflow.operation());
                return CraftingExecutionOutcome.NONE;
            }
        }

        Optional<TrinityBorrowingTransaction> borrowing = borrowDynamicInputs(
                selected.inputsPerCraft(),
                selected.maximumCrafts(),
                network,
                execution.borrowingLedger());
        if (borrowing.isEmpty()) {
            execution.deferDynamicInput(
                    work,
                    selected.observedKeys(),
                    currentTick,
                    settings.dynamicRetryMaxTicks);
            return CraftingExecutionOutcome.NONE;
        }
        TrinityBorrowingTransaction borrowed = borrowing.orElseThrow();
        long logicalOffer = Math.min(maximumLogicalFirings, selected.maximumCrafts());
        if (logicalOffer <= 0L) {
            borrowed.releaseUncommitted();
            execution.deferDynamicInput(
                    work,
                    selected.observedKeys(),
                    currentTick,
                    settings.dynamicRetryMaxTicks);
            return CraftingExecutionOutcome.NONE;
        }

        ProviderDispatchOutcome outcome;
        try {
            outcome = dispatchToAvailableProvider(
                    currentJob,
                    pattern,
                    selected.extractionPattern(),
                    logicalOffer,
                    work,
                    work.generation(),
                    false,
                    craftingProviderPublications(craftingService),
                    stablePatternIdentity(work.patternIdentity()),
                    energyService,
                    level,
                    dispatchWindow,
                    1,
                    dispatchBudget,
                    commit -> {
                        borrowed.commitConsumed(selected.inputsPerCraft(), commit.count());
                        commitTrinityPatternPush(currentJob, execution, work, logicalOffer, commit);
                    });
        } catch (DynamicCraftingOutputResolutionException exception) {
            this.proposalCoordinator.cancel();
            String reason = "DYNAMIC_OUTPUT: " + exception.getMessage();
            execution.fail(reason);
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} cancelled compact job {} before provider ownership because dynamic outputs for pattern {} are invalid",
                    this.cpu.number(),
                    currentJob.link.getCraftingID(),
                    pattern.getDefinition(),
                    exception);
            borrowed.releaseUncommitted();
            finishJob(false);
            return CraftingExecutionOutcome.NONE;
        } catch (RuntimeException exception) {
            this.proposalCoordinator.cancel();
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} isolated an unexpected compact-plan dispatch failure for pattern {}",
                    this.cpu.number(),
                    pattern.getDefinition(),
                    exception);
            throw exception;
        } finally {
            borrowed.releaseUncommitted();
        }
        if (this.job != currentJob) {
            return new CraftingExecutionOutcome(outcome.physicalAttempts(), outcome.dispatched());
        }
        if (outcome.dispatched()) {
            return new CraftingExecutionOutcome(outcome.physicalAttempts(), true);
        }
        if (outcome.currentProposalOutstanding()) {
            return new CraftingExecutionOutcome(outcome.physicalAttempts(), false, true, false);
        }
        if (outcome.proposalDeferred()) {
            return new CraftingExecutionOutcome(outcome.physicalAttempts(), false, false, true);
        }
        if (dispatchWindow.isExhausted()) {
            execution.markBudgetExhausted(work, currentTick);
        } else {
            execution.deferProvider(work, currentTick, settings.dynamicRetryMaxTicks);
        }
        return new CraftingExecutionOutcome(outcome.physicalAttempts(), false);
    }

    /**
     * Captures one immutable remaining-work proposal on the server thread and applies its future result only here.
     */
    private void advanceTrinityReplanning(TrinityDataCoreExecutingCraftingJob currentJob,
                                          CraftingService craftingService,
                                          long currentTick) {
        TrinityPlanExecution execution = currentJob.trinityExecution();
        if (!(craftingService instanceof TrinityCraftingGraphAccess graphAccess)) {
            String reason = "STALE_GRAPH: crafting service does not expose a Trinity graph snapshot";
            execution.fail(reason);
            Data_Energistics.LOGGER.error(reason);
            return;
        }
        IGrid activeGrid = this.cpu.grid();
        if (activeGrid == null) {
            return;
        }
        TrinityCraftingSchema settings = DataEnergisticsConfiguration.INSTANCE.trinity.crafting;
        MEStorage network = activeGrid.getStorageService().getInventory();
        Optional<TrinityCraftingGraphSnapshot> graphSnapshot = graphAccess.data_energistics$trinityCraftingGraphSnapshot();
        TrinityRemainingPlanCalculation.Result result = this.remainingPlanCalculation.advance(
                graphSnapshot,
                craftingProviderPublications(craftingService).publicationScope(),
                () -> graphSnapshot
                        .map(snapshot -> captureReplanAvailability(snapshot, activeGrid, currentJob.finalOutput.what()))
                        .orElseGet(TrinityPlanningInventory::empty),
                execution.finalOutput().what(),
                BigInteger.valueOf(execution.deliveryRemaining()),
                execution.quantityMode(),
                settings,
                currentTick);
        TrinityRemainingPlanCalculation.Ready ready;
        switch (result) {
            case TrinityRemainingPlanCalculation.Ready value -> ready = value;
            case TrinityRemainingPlanCalculation.Waiting ignored -> {
                return;
            }
            case TrinityRemainingPlanCalculation.Rejected rejected -> {
                Data_Energistics.LOGGER.warn(
                        "Trinity CPU {} could not replan compact job {} at catalog revision {}: {}",
                        this.cpu.number(),
                        currentJob.link.getCraftingID(),
                        rejected.revision(),
                        rejected.diagnostic().code());
                return;
            }
            case TrinityRemainingPlanCalculation.Fault fault -> {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} failed to calculate remaining work for job {}",
                        this.cpu.number(),
                        currentJob.link.getCraftingID(),
                        fault.cause());
                return;
            }
        }

        if (!this.cpu.storageCapacity().accepts(ready.plan().exactBytes())) {
            Data_Energistics.LOGGER.warn(
                    "Trinity CPU {} retained job {} because replacement plan at catalog revision {} requires {} bytes but the worker currently has {}",
                    this.cpu.number(),
                    currentJob.link.getCraftingID(),
                    ready.revision(),
                    ready.plan().exactBytes(),
                    this.cpu.storageCapacity().diagnosticValue());
            this.remainingPlanCalculation.retrySameRevision(
                    ready.revision(),
                    currentTick,
                    settings.dynamicRetryMaxTicks);
            return;
        }

        if (!reserveReplacementInputs(ready.plan(), network)) {
            Data_Energistics.LOGGER.warn(
                    "Trinity CPU {} retained job {} while replacement inputs for catalog revision {} were unavailable",
                    this.cpu.number(),
                    currentJob.link.getCraftingID(),
                    ready.revision());
            this.remainingPlanCalculation.retrySameRevision(
                    ready.revision(),
                    currentTick,
                    settings.dynamicRetryMaxTicks);
            return;
        }
        Map<AEKey, BigInteger> previousPendingOutputs = execution.pendingOutputs();
        ObjectOpenHashSet<AEKey> changedOutputKeys = new ObjectOpenHashSet<>(previousPendingOutputs.keySet());
        execution.replaceRemainingPlan(ready.plan(), currentTick);
        currentJob.timeTracker.replacePendingPlan(previousPendingOutputs, ready.plan().plannedOutputs());
        changedOutputKeys.addAll(execution.pendingOutputs().keySet());
        this.remainingPlanCalculation.acceptRevision(ready.revision());
        this.cpu.markDirty();
        changedOutputKeys.forEach(this::postChange);
    }

    private TrinityPlanningInventory captureReplanAvailability(
                                                               TrinityCraftingGraphSnapshot snapshot,
                                                               IGrid grid,
                                                               AEKey target) {
        var storageService = grid.getStorageService();
        TrinitySameItemPolicy policy = snapshot.sameItemPolicy(target);
        return TrinityPlanningInventorySnapshot.capture(
                snapshot.keys(),
                policy,
                storageService.getInventory(),
                this.cpu.actionSource(),
                TrinityPlanningProgressReporter.none()).inventory()
                .plus(this.inventory.list)
                .plus(this.exactWorkingInventory.snapshot()).normalized(policy);
    }

    private boolean reserveReplacementInputs(TrinityCraftingPlan replacement, MEStorage network) {
        return TrinityInitialInputExtractor.reserveReplacement(
                replacement.initialExpectedInputs(),
                replacement.sameItemPolicy(),
                network,
                this.inventory,
                this.exactWorkingInventory,
                this.cpu.actionSource()) == null;
    }

    private long simulateNetworkExtraction(MEStorage network, AEKey key) {
        try {
            return network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, this.cpu.actionSource());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} failed to simulate dynamic material availability for {}",
                    this.cpu.number(),
                    key,
                    exception);
            return 0L;
        }
    }

    /**
     * Returns the saturated combined CPU and network amount used to decide whether a whole cycle wave can start.
     */
    private BigInteger combinedCycleSeedAvailability(
                                                     MEStorage network,
                                                     TrinitySameItemPolicy policy,
                                                     AEKey key,
                                                     BigInteger usefulUpper) {
        if (!policy.allowsSameItem(key)) {
            return combinedExactCycleSeedAvailability(network, key, usefulUpper);
        }
        AEKey logicalKey = policy.normalizeKey(key);
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        keys.add(key);
        this.inventory.list.forEach(entry -> keys.add(entry.getKey()));
        keys.addAll(this.exactWorkingInventory.snapshot().keySet());
        network.getAvailableStacks().forEach(entry -> keys.add(entry.getKey()));
        BigInteger available = BigInteger.ZERO;
        for (AEKey candidate : keys) {
            if (policy.normalizeKey(candidate).equals(logicalKey)) {
                available = available.add(combinedExactCycleSeedAvailability(
                        network, candidate, usefulUpper.subtract(available)));
                if (available.equals(usefulUpper)) {
                    break;
                }
            }
        }
        return available;
    }

    private BigInteger combinedExactCycleSeedAvailability(MEStorage network, AEKey key, BigInteger usefulUpper) {
        BigInteger cpuAmount = this.exactWorkingInventory.totalAmount(key, this.inventory).min(usefulUpper);
        if (cpuAmount.equals(usefulUpper)) {
            return cpuAmount;
        }
        if (!(network instanceof FiniteNetworkStorageAccess storageAccess)) {
            return cpuAmount.add(BigInteger.valueOf(simulateNetworkExtraction(network, key)))
                    .min(usefulUpper);
        }
        TrinityAvailableAmount networkAmount = storageAccess.exactAvailability(key, this.cpu.actionSource());
        return cpuAmount.add(networkAmount.availableUpTo(usefulUpper.subtract(cpuAmount)));
    }

    /**
     * Resolves stable provider publications and offers fair capacity slices within the worker's physical-call budget.
     */
    private ProviderDispatchOutcome dispatchToAvailableProvider(
                                                                TrinityDataCoreExecutingCraftingJob currentJob,
                                                                IPatternDetails details,
                                                                TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                                                CraftingProviderPublicationIndex publications,
                                                                String patternIdentity,
                                                                IEnergyService energyService,
                                                                Level level,
                                                                CraftingDispatchWindow dispatchWindow,
                                                                int physicalCallLimit,
                                                                CraftingDispatchBudget dispatchBudget) {
        return dispatchToAvailableProvider(
                currentJob,
                details,
                details,
                task.value,
                task,
                0L,
                true,
                publications,
                patternIdentity,
                energyService,
                level,
                dispatchWindow,
                physicalCallLimit,
                dispatchBudget,
                commit -> commitPatternPush(currentJob, task, commit));
    }

    /**
     * Seals completed Trinity production away from cycle inputs and advances partial requester delivery.
     *
     * @return whether the job reached a terminal success state
     */
    private boolean advanceTrinityCompletion(TrinityDataCoreExecutingCraftingJob currentJob) {
        TrinityPlanExecution execution = currentJob.trinityExecution();
        if (!execution.productionComplete() || !currentJob.waitingFor.list.isEmpty() ||
                !currentJob.dynamicOutputs.isEmpty()) {
            return false;
        }

        if (VirtualCraftingOutputAdapters.hasNoOutputCompletion(execution.finalOutput())) {
            if (execution.deliveryRemaining() > 0L) {
                String reason = "RUNTIME_DEADLOCK: completed Trinity order-package production still lacks " +
                        execution.deliveryRemaining() + " virtual completion units for " + execution.finalOutput().what();
                Data_Energistics.LOGGER.error(reason);
                execution.fail(reason);
                finishJob(false);
                return true;
            }
            currentJob.remainingAmount = 0L;
            this.cpu.markDirty();
            if (currentJob.isComplete()) {
                finishJob(true);
                return true;
            }
            return false;
        }

        if (execution.deliveryRemaining() > 0L && execution.completionOffer().isEmpty()) {
            GenericStack target = execution.finalOutput();
            long exactAmount = Math.subtractExact(
                    execution.deliveryRemaining(),
                    execution.actualFinalOutputAmount());
            this.exactWorkingInventory.refillPhysicalWindows(this.inventory);
            List<GenericStack> delivery = TrinityCompletionInputExtractor.extract(
                    execution.sameItemPolicy(), target.what(), exactAmount, this.inventory);
            if (delivery == null) {
                String reason = "RUNTIME_DEADLOCK: completed Trinity production lacks " + exactAmount +
                        " unsealed delivery units for " + target.what();
                Data_Energistics.LOGGER.error(reason);
                execution.fail(reason);
                finishJob(false);
                return true;
            }
            long exactDelivery = 0L;
            for (GenericStack slice : delivery) {
                if (slice.what().equals(target.what())) {
                    exactDelivery += slice.amount();
                } else {
                    execution.recordActualFinalOutput((AEItemKey) slice.what(), slice.amount());
                }
            }
            execution.sealCompletion(exactDelivery);
            postChange(target.what());
        }

        if (currentJob.link.isStandalone()) {
            for (Object2LongMap.Entry<AEKey> entry : execution.releaseCompletionForStandalone().object2LongEntrySet()) {
                this.inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                postChange(entry.getKey());
            }
        } else {
            Optional<GenericStack> completionOffer = execution.completionOffer();
            if (completionOffer.isPresent() && !deliverCompletionToRequester(currentJob, execution, completionOffer.get())) {
                this.cpu.markDirty();
                return false;
            }
        }
        currentJob.remainingAmount = execution.deliveryRemaining();
        this.cpu.markDirty();
        if (currentJob.isComplete()) {
            finishJob(true);
            return true;
        }
        return false;
    }

    private boolean deliverCompletionToRequester(TrinityDataCoreExecutingCraftingJob currentJob,
                                                 TrinityPlanExecution execution,
                                                 GenericStack offer) {
        try {
            long accepted = currentJob.link.insert(
                    offer.what(),
                    offer.amount(),
                    Actionable.MODULATE);
            validateLinkAcceptance(offer.what(), offer.amount(), accepted, Actionable.MODULATE);
            if (accepted == 0L && !offer.what().equals(execution.finalOutput().what())) {
                IGrid grid = this.cpu.grid();
                if (grid != null) {
                    accepted = grid.getStorageService().getInventory().insert(
                            offer.what(),
                            offer.amount(),
                            Actionable.MODULATE,
                            this.cpu.actionSource());
                    validateLinkAcceptance(offer.what(), offer.amount(), accepted, Actionable.MODULATE);
                }
            }
            if (accepted > 0L) {
                execution.recordDelivered(offer.what(), accepted);
            }
            return true;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} requester failed while accepting completion output {} x{}; retaining the sealed offer",
                    this.cpu.number(),
                    offer.what(),
                    offer.amount(),
                    exception);
            return false;
        }
    }

    /**
     * Executes the shared transactional provider path with independently selected extraction semantics and accounting.
     */
    private ProviderDispatchOutcome dispatchToAvailableProvider(
                                                                TrinityDataCoreExecutingCraftingJob currentJob,
                                                                IPatternDetails details,
                                                                IPatternDetails extractionDetails,
                                                                long remainingCrafts,
                                                                Object workIdentity,
                                                                long workGeneration,
                                                                boolean validateScheduledOutputs,
                                                                CraftingProviderPublicationIndex publications,
                                                                String patternIdentity,
                                                                IEnergyService energyService,
                                                                Level level,
                                                                CraftingDispatchWindow dispatchWindow,
                                                                int physicalCallLimit,
                                                                CraftingDispatchBudget dispatchBudget,
                                                                Consumer<PreparedPatternCommit> acceptedDispatch) {
        if (physicalCallLimit <= 0 ||
                dispatchWindow.isExhausted() ||
                !dispatchWindow.canCaptureProviderCapacity()) {
            return settleProposal(workIdentity, false, ProviderDispatchOutcome.NONE);
        }
        long remainingLogicalCrafts = remainingCrafts;
        CraftingDispatchLease dispatchLease = captureDispatchLease(
                currentJob,
                publications,
                workGeneration);
        if (dispatchLease == null) {
            this.proposalCoordinator.cancel();
            return ProviderDispatchOutcome.NONE;
        }
        TrinityWorkerProposalCoordinator.Decision proposalDecision = this.proposalCoordinator.poll(
                dispatchLease,
                workIdentity);
        boolean synchronousFallback = false;
        boolean nativeSingleCraftFallback = false;
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.Pending) {
            return ProviderDispatchOutcome.AWAITING_PROPOSAL;
        }
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.NoCapacity) {
            // Capacity is a transient server-thread fact. A completed background miss must recapture the current
            // provider window instead of suppressing this worker for the next fair runtime pass.
            proposalDecision = TrinityWorkerProposalCoordinator.Empty.INSTANCE;
            synchronousFallback = true;
        }
        CraftingDispatchProposal selectedProposal = proposalDecision instanceof TrinityWorkerProposalCoordinator.Ready ready ?
                ready.proposal() : null;
        boolean asynchronousSelection = selectedProposal != null;

        ExtractedPatternInputs prototype;
        long maximumCount;
        long currentTick;
        ProviderCapacityCapture capacityCapture;
        List<ProviderCapacitySnapshot> snapshots;
        var capacityScope = dispatchWindow.tryBeginProviderCapacityCapture();
        if (capacityScope == null) {
            return settleProposal(workIdentity, asynchronousSelection, ProviderDispatchOutcome.NONE);
        }
        try (capacityScope) {
            prototype = capturePatternInputPrototype(extractionDetails, level);
            if (prototype == null || dispatchWindow.isExhausted()) {
                return settleProposal(workIdentity, asynchronousSelection, ProviderDispatchOutcome.NONE);
            }

            double prototypePower = CraftingCpuHelper.calculatePatternPower(prototype.inputHolder());
            maximumCount = limitByUnextractedInputAvailability(prototype.inputsPerCraft(), remainingLogicalCrafts);
            maximumCount = limitByWaitingCapacity(currentJob, prototype.waitingPerCraft(), maximumCount);
            maximumCount = limitByEnergy(prototypePower, maximumCount, energyService);
            if (maximumCount <= 0L || dispatchWindow.isExhausted()) {
                return settleProposal(workIdentity, asynchronousSelection, ProviderDispatchOutcome.NONE);
            }

            currentTick = TickHandler.instance().getCurrentTick();
            capacityCapture = this.capacityResolver.capture(
                    publications,
                    details,
                    prototype.inputHolder(),
                    maximumCount,
                    patternIdentity,
                    currentTick);
            snapshots = capacityCapture.snapshots();
            if (snapshots.isEmpty()) {
                capacityCapture = nativeSingleCraftFallbackCapture(capacityCapture);
                snapshots = capacityCapture.snapshots();
                nativeSingleCraftFallback = !snapshots.isEmpty();
                if (nativeSingleCraftFallback) {
                    synchronousFallback = true;
                }
            }
        }
        if (snapshots.isEmpty() && selectedProposal == null) {
            return settleProposal(workIdentity, asynchronousSelection, ProviderDispatchOutcome.NONE);
        }
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.Empty &&
                !synchronousFallback &&
                !dispatchBudget.asynchronousEnabled()) {
            proposalDecision = new TrinityWorkerProposalCoordinator.Fallback(
                    TrinityWorkerProposalCoordinator.FallbackReason.SCHEDULER_DISABLED);
        }
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.Empty && !synchronousFallback) {
            try {
                proposalDecision = this.proposalCoordinator.submit(
                        new CraftingDispatchProposalRequest(
                                dispatchLease,
                                capacityCapture,
                                BigInteger.valueOf(maximumCount),
                                this.capacitySliceCursor),
                        workIdentity,
                        this.cpu::proposalCompleted,
                        dispatchBudget.proposalPolicy());
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} could not submit an asynchronous dispatch proposal; using the synchronous path",
                        this.cpu.number(),
                        exception);
                proposalDecision = new TrinityWorkerProposalCoordinator.Fallback(
                        TrinityWorkerProposalCoordinator.FallbackReason.CALCULATION_FAILED);
            }
        }
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.Deferred) {
            this.proposalRetryAt = Math.addExact(currentTick, dispatchBudget.retryBackoffTicks());
            return ProviderDispatchOutcome.DEFERRED;
        }
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.Pending) {
            return ProviderDispatchOutcome.AWAITING_PROPOSAL;
        }
        int physicalAttempts = 0;
        int inspectedSnapshots = 0;
        CraftingDispatchCursor searchCursor = this.capacitySliceCursor;
        Set<ProviderCapacitySnapshot> inspectedTargets = new ReferenceOpenHashSet<>();
        int candidateLimit = asynchronousSelection ? 1 : snapshots.size();
        while (inspectedSnapshots < candidateLimit &&
                physicalAttempts < physicalCallLimit &&
                !dispatchWindow.isExhausted()) {
            boolean usingSelectedProposal = asynchronousSelection;
            DispatchCapacitySlicePlan candidatePlan = usingSelectedProposal ?
                    new DispatchCapacitySlicePlan(List.of(new DispatchCapacitySlicePlan.Slice(
                            selectedProposal.target(),
                            Math.min(selectedProposal.logicalCrafts(), maximumCount),
                            selectedProposal.nextCursor()))) :
                    this.capacityPlanner.plan(
                            capacityCapture,
                            BigInteger.valueOf(maximumCount),
                            physicalCallLimit,
                            searchCursor);
            if (candidatePlan.slices().isEmpty()) {
                break;
            }
            DispatchCapacitySlicePlan.Slice slice = candidatePlan.slices().getFirst();
            ProviderCapacitySnapshot snapshot = slice.target();
            inspectedSnapshots++;
            searchCursor = slice.nextCursor();
            if (!inspectedTargets.add(snapshot)) {
                break;
            }
            boolean candidateNativeFallback = !usingSelectedProposal && nativeSingleCraftFallback;
            long prototypeOffer = offeredCount(snapshot, slice, maximumCount);
            ICraftingProvider provider = resolveCurrentProvider(
                    candidateNativeFallback,
                    publications,
                    details,
                    prototype.inputHolder(),
                    prototypeOffer,
                    patternIdentity,
                    snapshot,
                    currentTick);
            if (provider == null) {
                if (asynchronousSelection) {
                    return resubmitAfterSelectedTargetFailure(
                            dispatchLease,
                            capacityCapture,
                            maximumCount,
                            selectedProposal,
                            workIdentity,
                            currentTick,
                            dispatchBudget,
                            true,
                            CraftingDispatchExclusion.target(selectedProposal.target()));
                }
                continue;
            }
            boolean countedDispatch = CountedCraftingProviderAdapters.supportsCountedDispatch(provider);
            if (!canAttemptProvider(dispatchWindow, provider, details, snapshot.route(), countedDispatch)) {
                if (asynchronousSelection) {
                    return resubmitAfterSelectedTargetFailure(
                            dispatchLease,
                            capacityCapture,
                            maximumCount,
                            selectedProposal,
                            workIdentity,
                            currentTick,
                            dispatchBudget,
                            false,
                            CraftingDispatchExclusion.target(selectedProposal.target()));
                }
                continue;
            }

            var submission = dispatchWindow.tryBeginSubmission(provider, details);
            if (submission == null) {
                break;
            }
            try (submission) {
                if (providerBusy(provider, details, dispatchWindow)) {
                    if (asynchronousSelection) {
                        return resubmitAfterSelectedTargetFailure(
                                dispatchLease,
                                capacityCapture,
                                maximumCount,
                                selectedProposal,
                                workIdentity,
                                currentTick,
                                dispatchBudget,
                                false,
                                CraftingDispatchExclusion.provider(selectedProposal.target()));
                    }
                    continue;
                }
                if (dispatchWindow.isExhausted()) {
                    break;
                }

                PatternInputTransaction inputTransaction = beginPatternInputTransaction(extractionDetails, level);
                if (inputTransaction == null) {
                    return settleProposal(
                            workIdentity,
                            asynchronousSelection,
                            new ProviderDispatchOutcome(physicalAttempts, false));
                }
                try {
                    if (dispatchWindow.isExhausted()) {
                        break;
                    }
                    ExtractedPatternInputs inputs = inputTransaction.inputs();
                    double powerPerCraft = CraftingCpuHelper.calculatePatternPower(inputs.inputHolder());
                    long currentMaximum = limitByInputAvailability(inputs.inputsPerCraft(), remainingLogicalCrafts);
                    currentMaximum = limitByWaitingCapacity(currentJob, inputs.waitingPerCraft(), currentMaximum);
                    currentMaximum = limitByEnergy(powerPerCraft, currentMaximum, energyService);
                    if (currentMaximum <= 0L || dispatchWindow.isExhausted()) {
                        break;
                    }

                    long offeredCount = offeredCount(snapshot, slice, currentMaximum);
                    ICraftingProvider currentProvider = resolveCurrentProvider(
                            candidateNativeFallback,
                            publications,
                            details,
                            inputs.inputHolder(),
                            offeredCount,
                            patternIdentity,
                            snapshot,
                            currentTick);
                    if (currentProvider != provider) {
                        if (asynchronousSelection) {
                            return resubmitAfterSelectedTargetFailure(
                                    dispatchLease,
                                    capacityCapture,
                                    maximumCount,
                                    selectedProposal,
                                    workIdentity,
                                    currentTick,
                                    dispatchBudget,
                                    true,
                                    CraftingDispatchExclusion.target(selectedProposal.target()));
                        }
                        continue;
                    }
                    if (!dispatchContextCurrent(
                            dispatchLease,
                            currentJob,
                            publications,
                            workGeneration,
                            snapshot)) {
                        if (asynchronousSelection) {
                            this.proposalCoordinator.discardStale(workIdentity);
                        }
                        return settleProposal(
                                workIdentity,
                                false,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }

                    CountedCraftingPreparation preparation;
                    try {
                        preparation = prepareSelectedProvider(
                                provider,
                                details,
                                extractionDetails,
                                inputs.inputHolder(),
                                offeredCount,
                                snapshot,
                                dispatchWindow,
                                candidateNativeFallback,
                                countedDispatch);
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Crafting provider {} threw while preparing pattern {} on Trinity CPU {}; isolating the provider for this dispatch window",
                                provider,
                                details.getDefinition(),
                                this.cpu.number(),
                                exception);
                        dispatchWindow.recordResult(
                                provider,
                                details,
                                snapshot.route(),
                                CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP);
                        if (asynchronousSelection) {
                            return resubmitAfterSelectedTargetFailure(
                                    dispatchLease,
                                    capacityCapture,
                                    maximumCount,
                                    selectedProposal,
                                    workIdentity,
                                    currentTick,
                                    dispatchBudget,
                                    false,
                                    CraftingDispatchExclusion.provider(selectedProposal.target()));
                        }
                        continue;
                    }
                    for (CraftingDispatchRejection rejection : preparation.rejections()) {
                        dispatchWindow.recordResult(provider, details, rejection.target(), rejection.status());
                    }
                    if (!preparation.accepted()) {
                        if (asynchronousSelection) {
                            return resubmitAfterSelectedTargetFailure(
                                    dispatchLease,
                                    capacityCapture,
                                    maximumCount,
                                    selectedProposal,
                                    workIdentity,
                                    currentTick,
                                    dispatchBudget,
                                    false,
                                    preparationExclusion(preparation, selectedProposal.target()));
                        }
                        continue;
                    }
                    if (dispatchWindow.isExhausted()) {
                        break;
                    }

                    CountedCraftingAdmission admission = preparation.admission();
                    CraftingDispatchTarget target = preparation.target();
                    if (admission == null || target == null) {
                        throw new IllegalStateException("Accepted counted preparation lost its admission or target");
                    }
                    if ((snapshot.routingMode() != ProviderRoutingMode.AGGREGATE &&
                            !target.equals(snapshot.route())) ||
                            !canAttemptProvider(dispatchWindow, provider, details, target, countedDispatch)) {
                        if (asynchronousSelection) {
                            return resubmitAfterSelectedTargetFailure(
                                    dispatchLease,
                                    capacityCapture,
                                    maximumCount,
                                    selectedProposal,
                                    workIdentity,
                                    currentTick,
                                    dispatchBudget,
                                    false,
                                    CraftingDispatchExclusion.target(selectedProposal.target()));
                        }
                        continue;
                    }
                    long count;
                    try {
                        count = CountedCraftingProviderAdapters.validatedAdmissionCount(
                                provider,
                                admission,
                                offeredCount);
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Crafting provider {} returned an invalid counted admission for pattern {} on Trinity CPU {}",
                                provider,
                                details.getDefinition(),
                                this.cpu.number(),
                                exception);
                        dispatchWindow.recordResult(
                                provider,
                                details,
                                target,
                                CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP);
                        if (asynchronousSelection) {
                            return resubmitAfterSelectedTargetFailure(
                                    dispatchLease,
                                    capacityCapture,
                                    maximumCount,
                                    selectedProposal,
                                    workIdentity,
                                    currentTick,
                                    dispatchBudget,
                                    false,
                                    CraftingDispatchExclusion.provider(selectedProposal.target()));
                        }
                        continue;
                    }
                    if (dispatchWindow.isExhausted()) {
                        break;
                    }

                    PreparedPatternCommit commit = preparePatternCommit(
                            currentJob,
                            details,
                            remainingLogicalCrafts,
                            inputs,
                            count,
                            validateScheduledOutputs);
                    if (commit == null || dispatchWindow.isExhausted()) {
                        break;
                    }
                    AdditionalInputTransaction additionalInputs = extractAdditionalInputs(inputs.inputsPerCraft(), count);
                    if (additionalInputs == null) {
                        return settleProposal(
                                workIdentity,
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }
                    if (dispatchWindow.isExhausted()) {
                        additionalInputs.rollback();
                        break;
                    }
                    EnergyCharge energyCharge = chargeEnergy(energyService, powerPerCraft * count);
                    if (energyCharge == null) {
                        additionalInputs.rollback();
                        return settleProposal(
                                workIdentity,
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }
                    if (!dispatchContextCurrent(
                            dispatchLease,
                            currentJob,
                            publications,
                            workGeneration,
                            snapshot)) {
                        energyCharge.rollback();
                        additionalInputs.rollback();
                        if (asynchronousSelection) {
                            this.proposalCoordinator.discardStale(workIdentity);
                        }
                        return settleProposal(
                                workIdentity,
                                false,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }
                    PatternInputTransaction acceptedInputs = inputTransaction;
                    CraftingDispatchAccountingDelta accounting = CraftingDispatchAccountingDelta.create(
                            count,
                            () -> commitAcceptedDispatch(
                                    currentJob,
                                    acceptedInputs,
                                    additionalInputs,
                                    energyCharge,
                                    commit,
                                    acceptedDispatch),
                            () -> {
                                energyCharge.rollback();
                                additionalInputs.rollback();
                            });
                    CraftingDispatchResult result = this.dispatchCommitter.commit(new CraftingDispatchCommitRequest(
                            this.cpu.number(),
                            currentJob.link.getCraftingID(),
                            provider,
                            details,
                            target,
                            admission,
                            inputs.inputHolder(),
                            dispatchWindow,
                            submission,
                            accounting));
                    if (result.physicalAttempted()) {
                        physicalAttempts = Math.incrementExact(physicalAttempts);
                        this.capacitySliceCursor = usingSelectedProposal ?
                                selectedProposal.nextCursor() :
                                slice.nextCursor();
                    }
                    if (result.requiresJobAbort()) {
                        Data_Energistics.LOGGER.error(
                                "Trinity CPU {} is aborting job {} because provider dispatch accounting could not be settled",
                                this.cpu.number(),
                                currentJob.link.getCraftingID());
                        finishJob(false);
                        return settleProposal(
                                workIdentity,
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }
                    if (result.dispatched()) {
                        if (usingSelectedProposal || physicalAttempts >= physicalCallLimit) {
                            return settleProposal(
                                    workIdentity,
                                    asynchronousSelection,
                                    new ProviderDispatchOutcome(physicalAttempts, true));
                        }
                        remainingLogicalCrafts = Math.subtractExact(remainingLogicalCrafts, count);
                        maximumCount = Math.min(maximumCount - count, remainingLogicalCrafts);
                        if (remainingLogicalCrafts <= 0L || maximumCount <= 0L) {
                            return settleProposal(
                                    workIdentity,
                                    asynchronousSelection,
                                    new ProviderDispatchOutcome(physicalAttempts, true));
                        }
                    }
                } finally {
                    inputTransaction.rollback();
                }
            }
        }
        return settleProposal(
                workIdentity,
                asynchronousSelection,
                new ProviderDispatchOutcome(physicalAttempts, false));
    }

    /**
     * Replaces a selected proposal that failed before provider ownership with a freshly reserved asynchronous choice.
     *
     * <p>
     * The selected ticket retains its provider-route and machine reservation until this boundary. The replacement is
     * submitted through the ordinary scheduler so every fallback target is checked against the same shard,
     * provider-quantum and machine-exclusive reservation state as the original proposal. No synchronous fallback may
     * bypass those reservations while other work tickets remain outstanding.
     * </p>
     */
    private ProviderDispatchOutcome resubmitAfterSelectedTargetFailure(
                                                                       CraftingDispatchLease dispatchLease,
                                                                       ProviderCapacityCapture capacityCapture,
                                                                       long maximumCount,
                                                                       CraftingDispatchProposal failedProposal,
                                                                       Object workIdentity,
                                                                       long currentTick,
                                                                       CraftingDispatchBudget dispatchBudget,
                                                                       boolean stale,
                                                                       CraftingDispatchExclusion failureExclusion) {
        if (stale) {
            this.proposalCoordinator.discardStale(workIdentity);
        } else {
            this.proposalCoordinator.release(workIdentity);
        }

        ObjectLinkedOpenHashSet<CraftingDispatchExclusion> exclusions = new ObjectLinkedOpenHashSet<>(failedProposal.exclusions());
        exclusions.add(failureExclusion);
        List<ProviderCapacitySnapshot> alternatives = capacityCapture.snapshots().stream()
                .filter(snapshot -> exclusions.stream().noneMatch(exclusion -> exclusion.excludes(snapshot)))
                .toList();
        if (alternatives.isEmpty()) {
            return ProviderDispatchOutcome.NONE;
        }
        ProviderCapacityCapture alternativeCapture = new ProviderCapacityCapture(
                capacityCapture.key(),
                alternatives);
        TrinityWorkerProposalCoordinator.Decision replacement;
        try {
            replacement = this.proposalCoordinator.submit(
                    new CraftingDispatchProposalRequest(
                            dispatchLease,
                            alternativeCapture,
                            BigInteger.valueOf(maximumCount),
                            this.capacitySliceCursor,
                            exclusions),
                    workIdentity,
                    this.cpu::proposalCompleted,
                    dispatchBudget.proposalPolicy());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} could not replace a pre-ownership dispatch proposal",
                    this.cpu.number(),
                    exception);
            return ProviderDispatchOutcome.NONE;
        }
        if (replacement instanceof TrinityWorkerProposalCoordinator.Pending) {
            return ProviderDispatchOutcome.AWAITING_PROPOSAL;
        }
        if (replacement instanceof TrinityWorkerProposalCoordinator.Deferred) {
            this.proposalRetryAt = Math.addExact(currentTick, dispatchBudget.retryBackoffTicks());
            return ProviderDispatchOutcome.DEFERRED;
        }
        if (replacement instanceof TrinityWorkerProposalCoordinator.Fallback) {
            return ProviderDispatchOutcome.NONE;
        }
        throw new IllegalStateException("A replacement Trinity dispatch proposal returned an impossible decision");
    }

    private static CraftingDispatchExclusion preparationExclusion(
                                                                  CountedCraftingPreparation preparation,
                                                                  ProviderCapacitySnapshot failedTarget) {
        boolean providerScoped = preparation.rejections().stream()
                .anyMatch(rejection -> rejection.target() == null);
        return providerScoped ?
                CraftingDispatchExclusion.provider(failedTarget) :
                CraftingDispatchExclusion.target(failedTarget);
    }

    /**
     * Settles proposal ownership for one exact work identity. Worker-wide outstanding state belongs to scheduling
     * snapshots and must never be projected onto this work's committed provider outcome.
     */
    private ProviderDispatchOutcome settleProposal(
                                                   Object workIdentity,
                                                   boolean consumedProposal,
                                                   ProviderDispatchOutcome outcome) {
        if (consumedProposal) {
            this.proposalCoordinator.release(workIdentity);
        }
        return outcome.withCurrentProposalOutstanding(this.proposalCoordinator.outstanding(workIdentity));
    }

    private CountedCraftingPreparation prepareSelectedProvider(
                                                               ICraftingProvider provider,
                                                               IPatternDetails details,
                                                               IPatternDetails extractionDetails,
                                                               KeyCounter[] prototype,
                                                               long offeredCount,
                                                               ProviderCapacitySnapshot snapshot,
                                                               CraftingDispatchWindow dispatchWindow,
                                                               boolean nativeSingleCraftFallback,
                                                               boolean countedDispatch) {
        if (nativeSingleCraftFallback && (details == extractionDetails ||
                extractionDetails instanceof TrinityBoundPatternDetails bound && bound.preservesNativeInputs(details))) {
            return CountedCraftingProviderAdapters.prepareNativeSingleCraft(
                    provider,
                    details,
                    target -> dispatchWindow.canAttempt(provider, details, target));
        }
        return CountedCraftingProviderAdapters.prepare(
                provider,
                details,
                extractionDetails,
                prototype,
                nativeSingleCraftFallback ? 1L : offeredCount,
                snapshot,
                target -> canAttemptProvider(dispatchWindow, provider, details, target, countedDispatch));
    }

    private static boolean canAttemptProvider(CraftingDispatchWindow dispatchWindow,
                                              ICraftingProvider provider,
                                              IPatternDetails pattern,
                                              CraftingDispatchTarget target,
                                              boolean countedDispatch) {
        return countedDispatch ?
                dispatchWindow.canAttemptCounted(provider, pattern, target) :
                dispatchWindow.canAttempt(provider, pattern, target);
    }

    /**
     * Resolves a live provider through either the counted-capacity or direct native fallback boundary.
     */
    @Nullable
    private ICraftingProvider resolveCurrentProvider(boolean nativeSingleCraftFallback,
                                                     CraftingProviderPublicationIndex publications,
                                                     IPatternDetails details,
                                                     KeyCounter[] prototype,
                                                     long requestedCrafts,
                                                     String patternIdentity,
                                                     ProviderCapacitySnapshot snapshot,
                                                     long currentTick) {
        if (nativeSingleCraftFallback) {
            return resolveNativeSingleCraftProvider(publications, details, snapshot);
        }
        return this.capacityResolver.resolveCurrent(
                publications,
                details,
                prototype,
                requestedCrafts,
                patternIdentity,
                snapshot,
                currentTick);
    }

    /**
     * Resolves a direct fallback target without asking the provider to recalculate capacity.
     */
    @Nullable
    private static ICraftingProvider resolveNativeSingleCraftProvider(
                                                                      CraftingProviderPublicationIndex publications,
                                                                      IPatternDetails details,
                                                                      ProviderCapacitySnapshot snapshot) {
        if (publications.publicationRevision() != snapshot.publicationRevision() ||
                !publications.providerIdsFor(details).contains(snapshot.providerId())) {
            return null;
        }
        return publications.resolveLiveProvider(snapshot.providerId());
    }

    /**
     * Represents every current publication as a conservative one-craft direct AE2 target.
     */
    private static ProviderCapacityCapture nativeSingleCraftFallbackCapture(ProviderCapacityCapture capacityCapture) {
        var captureKey = capacityCapture.key();
        List<ProviderCapacitySnapshot> snapshots = captureKey.providerFingerprint().stream()
                .map(providerId -> new ProviderCapacitySnapshot(
                        providerId,
                        CraftingDispatchTarget.provider(),
                        Optional.empty(),
                        captureKey.patternIdentity(),
                        captureKey.publicationRevision(),
                        captureKey.capacityRevision(),
                        captureKey.capacityEpoch(),
                        ProviderRoutingMode.UNKNOWN,
                        DispatchCapacity.Unknown.INSTANCE,
                        new DispatchCapacity.Known(1L)))
                .toList();
        return new ProviderCapacityCapture(captureKey, snapshots);
    }

    private static long offeredCount(
                                     ProviderCapacitySnapshot snapshot,
                                     DispatchCapacitySlicePlan.Slice slice,
                                     long maximumCount) {
        return switch (snapshot.routingMode()) {
            case TARGETED -> Math.min(slice.logicalCrafts(), maximumCount);
            case AGGREGATE -> maximumCount;
            case ORDERED, UNKNOWN -> 1L;
        };
    }

    private static CraftingProviderPublicationIndex craftingProviderPublications(CraftingService craftingService) {
        if (!(craftingService instanceof CraftingProviderPublicationAccess publicationAccess)) {
            throw new IllegalStateException("Crafting service does not expose the Trinity provider publication index");
        }
        return publicationAccess.data_energistics$craftingProviderPublicationIndex();
    }

    @Nullable
    private CraftingDispatchLease captureDispatchLease(TrinityDataCoreExecutingCraftingJob currentJob,
                                                       CraftingProviderPublicationIndex publications,
                                                       long workGeneration) {
        TrinityCraftingExecutionRoute route = this.cpu.executionRoute();
        if (route == null) {
            return null;
        }
        return new CraftingDispatchLease(
                publications.publicationScope(),
                this.cpu.runtimeId(),
                this.cpu.runtimeGeneration(),
                this.cpu.number(),
                currentJob.link.getCraftingID(),
                this.jobRevision,
                workGeneration,
                route.leaseEpoch(),
                route.membershipGeneration());
    }

    private boolean dispatchContextCurrent(CraftingDispatchLease expected,
                                           TrinityDataCoreExecutingCraftingJob currentJob,
                                           CraftingProviderPublicationIndex publications,
                                           long workGeneration,
                                           ProviderCapacitySnapshot snapshot) {
        if (this.job != currentJob || currentJob.link.isCanceled() ||
                publications.publicationRevision() != snapshot.publicationRevision() ||
                CountedCraftingProviderAdapters.mutationRevision() != snapshot.capacityRevision()) {
            return false;
        }
        return expected.equals(captureDispatchLease(currentJob, publications, workGeneration));
    }

    private static String capturePatternIdentity(IPatternDetails details, Level level) {
        TrinityPatternIdentity identity = TrinityPatternIdentity.capture(
                TrinityPatternPublicationSignature.capture(details),
                level.registryAccess());
        return stablePatternIdentity(identity);
    }

    private static String stablePatternIdentity(TrinityPatternIdentity identity) {
        return identity.definitionEncoding().length() + ":" +
                identity.definitionEncoding() + identity.publicationEncoding();
    }

    /**
     * Isolates provider busy-state failures and caches them only for the affected provider-pattern pair.
     */
    private boolean providerBusy(ICraftingProvider provider,
                                 IPatternDetails details,
                                 CraftingDispatchWindow dispatchWindow) {
        try {
            boolean busy = provider.isBusy();
            if (busy) {
                dispatchWindow.recordResult(provider, details, null, CraftingDispatchStatus.BUSY);
            }
            return busy;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} threw while checking busy state for pattern {} on Trinity CPU {}",
                    provider,
                    details.getDefinition(),
                    this.cpu.number(),
                    exception);
            dispatchWindow.recordResult(
                    provider,
                    details,
                    null,
                    CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP);
            return true;
        }
    }

    /**
     * Finalizes CPU ownership and job accounting after a provider has taken the admitted batch.
     */
    private void commitAcceptedDispatch(TrinityDataCoreExecutingCraftingJob currentJob,
                                        PatternInputTransaction inputTransaction,
                                        AdditionalInputTransaction additionalInputs,
                                        EnergyCharge energyCharge,
                                        PreparedPatternCommit commit,
                                        Consumer<PreparedPatternCommit> acceptedDispatch) {
        energyCharge.commit();
        additionalInputs.commit();
        inputTransaction.commit();
        currentJob.dynamicOutputs.consumeInputAliases(inputTransaction.ownedInputs);
        currentJob.dynamicOutputs.consumeInputAliases(additionalInputs.ownedInputs);
        acceptedDispatch.accept(commit);
    }

    @Nullable
    private PatternInputTransaction beginPatternInputTransaction(IPatternDetails details, Level level) {
        PatternInputCapture capture = extractPatternInputs(details, this.inventory, level);
        if (capture == null) {
            return null;
        }
        return new PatternInputTransaction(capture.inputs(), capture.ownedInputs());
    }

    @Nullable
    private ExtractedPatternInputs capturePatternInputPrototype(IPatternDetails details, Level level) {
        ListCraftingInventory prototypeInventory = new ListCraftingInventory(ignored -> {});
        for (var entry : this.inventory.list) {
            prototypeInventory.list.add(entry.getKey(), entry.getLongValue());
        }
        PatternInputCapture capture = extractPatternInputs(details, prototypeInventory, level);
        return capture == null ? null : capture.inputs();
    }

    @Nullable
    private static PatternInputCapture extractPatternInputs(IPatternDetails details,
                                                            ListCraftingInventory sourceInventory,
                                                            Level level) {
        KeyCounter expectedOutputs = new KeyCounter();
        KeyCounter expectedContainerItems = new KeyCounter();
        KeyCounter[] inputHolder = details instanceof TrinityBoundPatternDetails bound ?
                bound.extractInputs(sourceInventory, expectedOutputs, expectedContainerItems) :
                CraftingCpuHelper.extractPatternInputs(
                        details,
                        sourceInventory,
                        level,
                        expectedOutputs,
                        expectedContainerItems);
        if (inputHolder == null) {
            return null;
        }
        CapturedPatternInputs capturedInputs = capturePatternInputs(inputHolder);
        if (capturedInputs == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot track overflowing extracted pattern inputs");
            CraftingCpuHelper.reinjectPatternInputs(sourceInventory, inputHolder);
            return null;
        }
        CapturedPatternResults capturedResults = capturePatternResults(expectedOutputs, expectedContainerItems);
        if (capturedResults == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot track overflowing expected pattern outputs");
            CraftingCpuHelper.reinjectPatternInputs(sourceInventory, inputHolder);
            return null;
        }
        return new PatternInputCapture(
                new ExtractedPatternInputs(
                        inputHolder,
                        capturedInputs.inputsPerCraft(),
                        capturedResults.expectedOutputs(),
                        capturedResults.expectedContainerItems(),
                        capturedResults.waitingPerCraft()),
                capturedInputs.ownedInputs());
    }

    @Nullable
    private static CapturedPatternInputs capturePatternInputs(KeyCounter[] inputHolder) {
        KeyCounter ownedInputs = new KeyCounter();
        for (KeyCounter input : inputHolder) {
            if (!addCounterChecked(ownedInputs, input)) {
                return null;
            }
        }
        return new CapturedPatternInputs(counterSnapshot(ownedInputs), ownedInputs);
    }

    @Nullable
    private static CapturedPatternResults capturePatternResults(KeyCounter expectedOutputs,
                                                                KeyCounter expectedContainerItems) {
        KeyCounter waitingPerCraft = new KeyCounter();
        List<GenericStack> capturedOutputs = captureCounter(expectedOutputs, waitingPerCraft);
        if (capturedOutputs == null) {
            return null;
        }
        List<GenericStack> capturedContainerItems = captureCounter(expectedContainerItems, waitingPerCraft);
        if (capturedContainerItems == null) {
            return null;
        }
        return new CapturedPatternResults(
                capturedOutputs,
                capturedContainerItems,
                counterSnapshot(waitingPerCraft));
    }

    @Nullable
    private static List<GenericStack> captureCounter(KeyCounter source, KeyCounter aggregate) {
        ObjectArrayList<GenericStack> captured = new ObjectArrayList<>();
        for (var entry : source) {
            long amount = entry.getLongValue();
            long existing = aggregate.get(entry.getKey());
            if (amount <= 0L || existing > Long.MAX_VALUE - amount) {
                return null;
            }
            aggregate.add(entry.getKey(), amount);
            captured.add(new GenericStack(entry.getKey(), amount));
        }
        return List.copyOf(captured);
    }

    private static boolean addCounterChecked(KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            long amount = entry.getLongValue();
            long existing = target.get(entry.getKey());
            if (amount <= 0L || existing > Long.MAX_VALUE - amount) {
                return false;
            }
            target.add(entry.getKey(), amount);
        }
        return true;
    }

    private long limitByInputAvailability(List<GenericStack> inputsPerCraft, long maximumCount) {
        long count = maximumCount;
        for (GenericStack input : inputsPerCraft) {
            long amountPerCraft = input.amount();
            long availableCopies = this.inventory.list.get(input.what()) / amountPerCraft;
            long availableCount = availableCopies == Long.MAX_VALUE ? Long.MAX_VALUE : availableCopies + 1L;
            count = Math.min(count, availableCount);
            count = Math.min(count, Long.MAX_VALUE / amountPerCraft);
        }
        return count;
    }

    private long limitByUnextractedInputAvailability(List<GenericStack> inputsPerCraft, long maximumCount) {
        long count = maximumCount;
        for (GenericStack input : inputsPerCraft) {
            long amountPerCraft = input.amount();
            count = Math.min(count, this.inventory.list.get(input.what()) / amountPerCraft);
            count = Math.min(count, Long.MAX_VALUE / amountPerCraft);
        }
        return count;
    }

    private static long limitByWaitingCapacity(TrinityDataCoreExecutingCraftingJob currentJob,
                                               List<GenericStack> waitingPerCraft,
                                               long maximumCount) {
        long count = maximumCount;
        for (GenericStack waiting : sameItemPolicy(currentJob).normalizeStacks(waitingPerCraft)) {
            long currentlyWaiting = currentJob.waitingFor.list.get(waiting.what());
            count = Math.min(count, (Long.MAX_VALUE - currentlyWaiting) / waiting.amount());
        }
        return count;
    }

    private static long limitByEnergy(double powerPerCraft,
                                      long maximumCount,
                                      IEnergyService energyService) {
        if (powerPerCraft < 0.0D || !Double.isFinite(powerPerCraft) || maximumCount <= 0L) {
            return 0L;
        }
        if (powerPerCraft == 0.0D) {
            return maximumCount;
        }
        long finiteCount = Math.min(maximumCount, (long) Math.floor(Double.MAX_VALUE / powerPerCraft));
        if (finiteCount <= 0L) {
            return 0L;
        }
        double requestedPower = powerPerCraft * finiteCount;
        double availablePower;
        try {
            availablePower = energyService.extractAEPower(
                    requestedPower,
                    Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU failed while checking {} AE for a counted pattern dispatch",
                    requestedPower,
                    exception);
            return 0L;
        }
        if (!Double.isFinite(availablePower) || availablePower < 0.0D) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU energy service returned invalid simulated extraction {}",
                    availablePower);
            return 0L;
        }
        if (availablePower >= requestedPower - ENERGY_TOLERANCE) {
            return finiteCount;
        }
        double affordableCount = Math.floor((availablePower + ENERGY_TOLERANCE) / powerPerCraft);
        if (affordableCount <= 0.0D) {
            return 0L;
        }
        return Math.min(finiteCount, (long) affordableCount);
    }

    @Nullable
    private AdditionalInputTransaction extractAdditionalInputs(List<GenericStack> inputsPerCraft, long count) {
        AdditionalInputTransaction transaction = new AdditionalInputTransaction();
        if (count == 1L) {
            return transaction;
        }
        long additionalCopies = count - 1L;
        for (GenericStack stack : inputsPerCraft) {
            long additionalAmount = Math.multiplyExact(stack.amount(), additionalCopies);
            long extracted;
            try {
                extracted = this.inventory.extract(stack.what(), additionalAmount, Actionable.MODULATE);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU failed while extracting {} additional inputs of {}",
                        additionalAmount,
                        stack.what(),
                        exception);
                transaction.rollback();
                return null;
            }
            transaction.record(stack.what(), extracted);
            if (extracted != additionalAmount) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU inventory changed while preparing a counted pattern batch: expected {} of {}, extracted {}",
                        additionalAmount,
                        stack.what(),
                        extracted);
                transaction.rollback();
                return null;
            }
        }
        return transaction;
    }

    private static List<GenericStack> counterSnapshot(KeyCounter counter) {
        ObjectArrayList<GenericStack> snapshot = new ObjectArrayList<>();
        for (var entry : counter) {
            snapshot.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return List.copyOf(snapshot);
    }

    @Nullable
    private static EnergyCharge chargeEnergy(IEnergyService energyService, double power) {
        if (power < 0.0D || !Double.isFinite(power)) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU calculated invalid pattern power {}", power);
            return null;
        }
        if (power == 0.0D) {
            return new EnergyCharge(energyService, 0.0D);
        }
        double extracted;
        try {
            extracted = energyService.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU failed while extracting {} AE for a pattern dispatch",
                    power,
                    exception);
            return null;
        }
        if (!Double.isFinite(extracted) || extracted < power - ENERGY_TOLERANCE ||
                extracted > power + ENERGY_TOLERANCE) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU expected to extract {} AE for a pattern dispatch, but extracted {}",
                    power,
                    extracted);
            refundEnergy(energyService, extracted);
            return null;
        }
        return new EnergyCharge(energyService, extracted);
    }

    private static void refundEnergy(IEnergyService energyService, double extracted) {
        if (extracted <= 0.0D || !Double.isFinite(extracted)) {
            return;
        }
        double refund = PowerMultiplier.CONFIG.multiply(extracted);
        if (!Double.isFinite(refund)) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU calculated invalid energy refund {}", refund);
            return;
        }
        double refundTolerance = PowerMultiplier.CONFIG.multiply(ENERGY_TOLERANCE);
        try {
            double remainder = energyService.injectPower(refund, Actionable.MODULATE);
            if (remainder > refundTolerance) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU could not refund {} of {} AE after a failed pattern dispatch",
                        remainder,
                        refund);
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU failed to refund {} AE after a failed pattern dispatch",
                    refund,
                    exception);
        }
    }

    @Nullable
    private PreparedPatternCommit preparePatternCommit(TrinityDataCoreExecutingCraftingJob currentJob,
                                                       IPatternDetails details,
                                                       long remainingCrafts,
                                                       ExtractedPatternInputs extractedInputs,
                                                       long count,
                                                       boolean validateScheduledOutputs) {
        if (count <= 0L || count > remainingCrafts) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU cannot commit {} crafts from a task with {} remaining",
                    count,
                    remainingCrafts);
            return null;
        }
        if (limitByWaitingCapacity(currentJob, extractedInputs.waitingPerCraft(), count) < count) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot commit overflowing waiting counters");
            return null;
        }
        List<GenericStack> expectedOutputs = scaleAmounts(extractedInputs.expectedOutputs(), count);
        List<GenericStack> expectedContainerItems = scaleAmounts(extractedInputs.expectedContainerItems(), count);
        List<GenericStack> scheduledOutputs = scaleStacks(details.getOutputs(), count);
        if (expectedOutputs == null || expectedContainerItems == null || scheduledOutputs == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot commit overflowing pattern outputs");
            return null;
        }
        List<DynamicCraftingOutputLedger.Registration> dynamicOutputs = resolveDynamicOutputs(
                currentJob,
                details,
                count,
                expectedOutputs,
                expectedContainerItems);
        TrinitySameItemPolicy policy = sameItemPolicy(currentJob);
        expectedOutputs = policy.normalizeStacks(expectedOutputs);
        expectedContainerItems = policy.normalizeStacks(expectedContainerItems);
        scheduledOutputs = policy.normalizeStacks(scheduledOutputs);
        ObjectArrayList<GenericStack> allExpectedPhysicalOutputs = new ObjectArrayList<>(expectedOutputs);
        allExpectedPhysicalOutputs.addAll(expectedContainerItems);
        if (currentJob.dynamicOutputs.evaluate(
                currentJob.waitingFor.list,
                List.copyOf(allExpectedPhysicalOutputs),
                dynamicOutputs) == DynamicCraftingOutputLedger.DispatchSafety.CONFLICT) {
            return null;
        }
        List<VirtualCraftingCompletion> virtualCompletions;
        try {
            VirtualCraftingOutputProjection projection = VirtualCraftingOutputAdapters.project(details);
            virtualCompletions = projection.virtualCompletions(count);
            for (VirtualCraftingCompletion completion : virtualCompletions) {
                ListCraftingInventory ledger = completion.mode() == VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT ?
                        this.pendingNoOutputCompletions : this.pendingVirtualCompletions;
                Math.addExact(ledger.list.get(completion.stack().what()), completion.stack().amount());
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU cannot prepare virtual outputs for pattern {} and {} accepted crafts",
                    details.getDefinition(),
                    count,
                    exception);
            return null;
        }
        if (validateScheduledOutputs) {
            for (GenericStack output : scheduledOutputs) {
                if (currentJob.exactPendingOutput(output.what()).compareTo(BigInteger.valueOf(output.amount())) < 0) {
                    Data_Energistics.LOGGER.error(
                            "Trinity Data Core CPU cannot remove {} scheduled units of {}",
                            output.amount(),
                            output.what());
                    return null;
                }
            }
        }
        ObjectOpenHashSet<AEKey> changedKeys = new ObjectOpenHashSet<>();
        for (GenericStack output : expectedOutputs) {
            changedKeys.add(output.what());
        }
        for (GenericStack output : expectedContainerItems) {
            changedKeys.add(output.what());
        }
        return new PreparedPatternCommit(
                count,
                expectedOutputs,
                expectedContainerItems,
                dynamicOutputs,
                virtualCompletions,
                new PreparedScheduledOutputs(details.getDefinition(), scheduledOutputs),
                Set.copyOf(changedKeys));
    }

    private List<DynamicCraftingOutputLedger.Registration> resolveDynamicOutputs(
                                                                                 TrinityDataCoreExecutingCraftingJob currentJob,
                                                                                 IPatternDetails details,
                                                                                 long count,
                                                                                 List<GenericStack> expectedOutputs,
                                                                                 List<GenericStack> expectedContainerItems) {
        ObjectArrayList<DynamicCraftingOutputLedger.Registration> resolved = new ObjectArrayList<>();
        Optional<DynamicCraftingOutputAdapters.ResolvedSemantics> adapter = DynamicCraftingOutputAdapters.resolve(details);
        if (adapter.isPresent()) {
            DynamicCraftingOutputAdapters.ResolvedSemantics semantics = adapter.orElseThrow();
            for (DynamicCraftingOutput declaration : semantics.outputs()) {
                AEItemKey plannedKey = (AEItemKey) declaration.plannedOutput().what();
                long amount;
                try {
                    amount = Math.multiplyExact(declaration.plannedOutput().amount(), count);
                } catch (ArithmeticException exception) {
                    throw new DynamicCraftingOutputResolutionException(
                            "Dynamic output amount overflow for adapter " + semantics.adapterId(),
                            exception);
                }
                resolved.add(new DynamicCraftingOutputLedger.Registration(
                        plannedKey,
                        amount,
                        dynamicRoute(currentJob, plannedKey),
                        semantics.adapterId()));
            }
        }

        if (EncodedPatternDynamicOutput.isMarked(details.getDefinition())) {
            DynamicCraftingOutput declaration = EncodedPatternDynamicOutput.resolve(details);
            AEItemKey plannedKey = (AEItemKey) declaration.plannedOutput().what();
            long markedAmount;
            try {
                markedAmount = Math.multiplyExact(declaration.plannedOutput().amount(), count);
            } catch (ArithmeticException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Encoded pattern output amount overflow for " + details.getDefinition(),
                        exception);
            }
            if (amountFor(expectedOutputs, plannedKey) < markedAmount) {
                throw new DynamicCraftingOutputResolutionException(
                        "Encoded pattern output is not present in the prepared physical outputs for " +
                                details.getDefinition());
            }
            if (amountFor(expectedContainerItems, plannedKey) > 0L) {
                throw new DynamicCraftingOutputResolutionException(
                        "Encoded pattern output conflicts with a returned input container for " +
                                details.getDefinition());
            }

            long adapterAmount;
            try {
                adapterAmount = resolved.stream()
                        .filter(registration -> registration.plannedKey().equals(plannedKey))
                        .mapToLong(DynamicCraftingOutputLedger.Registration::amount)
                        .reduce(0L, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Combined dynamic output amount overflow for " + details.getDefinition(),
                        exception);
            }
            if (adapterAmount > markedAmount) {
                throw new DynamicCraftingOutputResolutionException(
                        "Registered dynamic output exceeds the encoded pattern output for " +
                                details.getDefinition());
            }
            if (adapterAmount < markedAmount) {
                resolved.add(new DynamicCraftingOutputLedger.Registration(
                        plannedKey,
                        markedAmount - adapterAmount,
                        dynamicRoute(currentJob, plannedKey),
                        EncodedPatternDynamicOutput.SOURCE_ID));
            }
        }

        TrinitySameItemPolicy policy = sameItemPolicy(currentJob);
        if (policy.isEmpty()) {
            return List.copyOf(resolved);
        }
        ObjectArrayList<DynamicCraftingOutputLedger.Registration> normalized = new ObjectArrayList<>();
        Object2LongOpenHashMap<AEKey> registeredAmounts = new Object2LongOpenHashMap<>();
        for (DynamicCraftingOutputLedger.Registration registration : resolved) {
            AEItemKey key = (AEItemKey) policy.normalizeKey(registration.plannedKey());
            normalized.add(new DynamicCraftingOutputLedger.Registration(
                    key, registration.amount(), dynamicRoute(currentJob, key), registration.source()));
            registeredAmounts.mergeLong(key, registration.amount(), Math::addExact);
        }
        ObjectArrayList<GenericStack> physicalOutputs = new ObjectArrayList<>(expectedOutputs);
        physicalOutputs.addAll(expectedContainerItems);
        for (GenericStack output : policy.normalizeStacks(physicalOutputs)) {
            if (policy.allowsSameItem(output.what())) {
                long remaining = output.amount() - registeredAmounts.getLong(output.what());
                if (remaining < 0L) {
                    throw new DynamicCraftingOutputResolutionException(
                            "Dynamic registrations exceed prepared same-item outputs for " + details.getDefinition());
                }
                if (remaining > 0L) {
                    AEItemKey key = (AEItemKey) output.what();
                    normalized.add(new DynamicCraftingOutputLedger.Registration(
                            key, remaining, dynamicRoute(currentJob, key), EncodedPatternDynamicOutput.SOURCE_ID));
                }
            }
        }
        return List.copyOf(normalized);
    }

    private static TrinitySameItemPolicy sameItemPolicy(TrinityDataCoreExecutingCraftingJob currentJob) {
        return currentJob.isTrinityPlan() ? currentJob.trinityExecution().sameItemPolicy() : TrinitySameItemPolicy.empty();
    }

    private static DynamicCraftingOutputLedger.Route dynamicRoute(
                                                                  TrinityDataCoreExecutingCraftingJob currentJob,
                                                                  AEItemKey plannedKey) {
        return plannedKey.equals(currentJob.finalOutput.what()) ?
                DynamicCraftingOutputLedger.Route.FINAL_OUTPUT :
                DynamicCraftingOutputLedger.Route.INVENTORY;
    }

    private static long amountFor(List<GenericStack> stacks, AEKey key) {
        return stacks.stream()
                .filter(stack -> stack.what().equals(key))
                .mapToLong(GenericStack::amount)
                .reduce(0L, Math::addExact);
    }

    @Nullable
    private static List<GenericStack> scaleAmounts(List<GenericStack> amounts, long count) {
        ObjectArrayList<GenericStack> scaled = new ObjectArrayList<>();
        for (GenericStack stack : amounts) {
            long amount = stack.amount();
            if (amount <= 0L || amount > Long.MAX_VALUE / count) {
                return null;
            }
            scaled.add(new GenericStack(stack.what(), amount * count));
        }
        return List.copyOf(scaled);
    }

    @Nullable
    private static List<GenericStack> scaleStacks(List<GenericStack> stacks, long count) {
        KeyCounter scaled = new KeyCounter();
        for (GenericStack stack : stacks) {
            long amount = stack.amount();
            if (amount <= 0L || amount > Long.MAX_VALUE / count) {
                return null;
            }
            long scaledAmount = amount * count;
            long existing = scaled.get(stack.what());
            if (existing > Long.MAX_VALUE - scaledAmount) {
                return null;
            }
            scaled.add(stack.what(), scaledAmount);
        }
        return counterSnapshot(scaled);
    }

    private void commitPatternPush(TrinityDataCoreExecutingCraftingJob currentJob,
                                   TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                   PreparedPatternCommit commit) {
        addWaiting(currentJob, commit.expectedOutputs());
        addWaiting(currentJob, commit.expectedContainerItems());
        currentJob.dynamicOutputs.register(commit.dynamicOutputs());
        task.value -= commit.count();
        currentJob.recordTaskDispatch(commit.scheduledRemoval(), 1L);
        for (GenericStack containerItem : commit.expectedContainerItems()) {
            currentJob.timeTracker.addMaxItems(containerItem.amount(), containerItem.what().getType());
        }
        this.cpu.markDirty();
        for (AEKey changedKey : commit.changedKeys()) {
            postChange(changedKey);
        }
        enqueueVirtualCompletions(commit.virtualCompletions());
    }

    private void commitTrinityPatternPush(TrinityDataCoreExecutingCraftingJob currentJob,
                                          TrinityPlanExecution execution,
                                          TrinityPlanExecution.Work work,
                                          long logicalOffer,
                                          PreparedPatternCommit commit) {
        addWaiting(currentJob, commit.expectedOutputs());
        addWaiting(currentJob, commit.expectedContainerItems());
        currentJob.dynamicOutputs.register(commit.dynamicOutputs());
        execution.recordAccepted(work, commit.count(), logicalOffer);
        for (GenericStack containerItem : commit.expectedContainerItems()) {
            currentJob.timeTracker.addMaxItems(containerItem.amount(), containerItem.what().getType());
        }
        this.cpu.markDirty();
        for (AEKey changedKey : commit.changedKeys()) {
            postChange(changedKey);
        }
        enqueueVirtualCompletions(commit.virtualCompletions());
    }

    private Optional<TrinityBorrowingTransaction> borrowDynamicInputs(List<GenericStack> inputsPerCraft,
                                                                      long maximumCrafts,
                                                                      MEStorage network,
                                                                      TrinityBorrowingLedger ledger) {
        TrinityBorrowingTransaction transaction = newBorrowingTransaction(network, ledger);
        try {
            for (GenericStack input : inputsPerCraft) {
                long required = Math.multiplyExact(input.amount(), maximumCrafts);
                long owned = this.inventory.list.get(input.what());
                long missing = Math.max(0L, required - Math.min(required, owned));
                if (missing == 0L) {
                    continue;
                }
                transaction.validateRecord(input.what(), missing);
                long remaining = missing;
                while (remaining > 0L) {
                    long extracted = network.extract(
                            input.what(),
                            remaining,
                            Actionable.MODULATE,
                            this.cpu.actionSource());
                    if (extracted == 0L) {
                        break;
                    }
                    if (extracted < 0L || extracted > remaining) {
                        if (extracted > 0L) {
                            long restored = network.insert(
                                    input.what(),
                                    extracted,
                                    Actionable.MODULATE,
                                    this.cpu.actionSource());
                            if (restored < extracted) {
                                long retained = extracted - restored;
                                this.inventory.insert(input.what(), retained, Actionable.MODULATE);
                                transaction.record(input.what(), retained);
                            }
                        }
                        throw new IllegalStateException("AE storage violated its extraction amount contract");
                    }
                    this.inventory.insert(input.what(), extracted, Actionable.MODULATE);
                    transaction.record(input.what(), extracted);
                    postChange(input.what());
                    remaining -= extracted;
                }
                if (remaining > 0L) {
                    transaction.releaseUncommitted();
                    return Optional.empty();
                }
            }
            return Optional.of(transaction);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} failed while reserving dynamic materials",
                    this.cpu.number(),
                    exception);
            transaction.releaseUncommitted();
            return Optional.empty();
        }
    }

    private TrinityBorrowingTransaction newBorrowingTransaction(MEStorage network,
                                                                TrinityBorrowingLedger ledger) {
        return TrinityBorrowingTransaction.create(
                network,
                ledger,
                this.inventory,
                this.cpu.actionSource(),
                this.cpu.number(),
                this::postChange);
    }

    private void enqueueVirtualCompletions(List<VirtualCraftingCompletion> completions) {
        for (VirtualCraftingCompletion completion : completions) {
            GenericStack stack = completion.stack();
            ListCraftingInventory ledger = completion.mode() == VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT ?
                    this.pendingNoOutputCompletions : this.pendingVirtualCompletions;
            Math.addExact(ledger.list.get(stack.what()), stack.amount());
            ledger.insert(
                    stack.what(),
                    stack.amount(),
                    Actionable.MODULATE);
        }
    }

    private boolean drainVirtualCompletions(TrinityDataCoreExecutingCraftingJob currentJob) {
        if (this.pendingVirtualCompletions.list.isEmpty() && this.pendingNoOutputCompletions.list.isEmpty()) {
            return true;
        }
        if (this.job != currentJob) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} cannot apply virtual completions to a stale crafting job",
                    this.cpu.number());
            return false;
        }

        if (!drainNoOutputVirtualCompletions(currentJob)) {
            return false;
        }

        ObjectArrayList<GenericStack> intermediate = new ObjectArrayList<>();
        ObjectArrayList<GenericStack> finalResults = new ObjectArrayList<>();
        for (var entry : this.pendingVirtualCompletions.list) {
            GenericStack completion = new GenericStack(entry.getKey(), entry.getLongValue());
            (entry.getKey().matches(currentJob.finalOutput) ? finalResults : intermediate).add(completion);
        }
        intermediate.addAll(finalResults);

        for (GenericStack completion : intermediate) {
            if (this.job != currentJob) {
                return this.job == null && this.pendingVirtualCompletions.list.isEmpty();
            }
            long removed = this.pendingVirtualCompletions.extract(
                    completion.what(),
                    completion.amount(),
                    Actionable.MODULATE);
            if (removed != completion.amount()) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} virtual completion ledger changed while applying {} x{}",
                        this.cpu.number(),
                        completion.what(),
                        completion.amount());
                return false;
            }
            long accepted;
            try {
                accepted = insert(completion.what(), completion.amount(), Actionable.MODULATE);
            } catch (RuntimeException exception) {
                this.pendingVirtualCompletions.insert(
                        completion.what(),
                        completion.amount(),
                        Actionable.MODULATE);
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} failed to apply accepted virtual completion {} x{}",
                        this.cpu.number(),
                        completion.what(),
                        completion.amount(),
                        exception);
                return false;
            }
            if (accepted < 0L || accepted > completion.amount()) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} received invalid acceptance {} for virtual completion {} x{}",
                        this.cpu.number(),
                        accepted,
                        completion.what(),
                        completion.amount());
                return false;
            }
            long remainder = completion.amount() - accepted;
            if (remainder > 0L) {
                if (completion.what().matches(currentJob.finalOutput)) {
                    this.pendingVirtualCompletions.insert(completion.what(), remainder, Actionable.MODULATE);
                } else {
                    try {
                        Math.addExact(this.inventory.list.get(completion.what()), remainder);
                        this.inventory.insert(completion.what(), remainder, Actionable.MODULATE);
                    } catch (RuntimeException exception) {
                        this.pendingVirtualCompletions.insert(
                                completion.what(),
                                remainder,
                                Actionable.MODULATE);
                        Data_Energistics.LOGGER.error(
                                "Trinity CPU {} could not retain virtual completion remainder {} x{}",
                                this.cpu.number(),
                                completion.what(),
                                remainder,
                                exception);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean drainNoOutputVirtualCompletions(TrinityDataCoreExecutingCraftingJob currentJob) {
        if (this.pendingNoOutputCompletions.list.isEmpty()) {
            return true;
        }
        ObjectArrayList<GenericStack> completions = new ObjectArrayList<>();
        for (var entry : this.pendingNoOutputCompletions.list) {
            completions.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        for (GenericStack completion : completions) {
            if (this.job != currentJob) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} cannot apply a no-output virtual completion to a stale crafting job",
                        this.cpu.number());
                return false;
            }
            if (!completion.what().matches(currentJob.finalOutput)) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} received no-output completion {} that does not match final output {}",
                        this.cpu.number(),
                        completion.what(),
                        currentJob.finalOutput.what());
                return false;
            }
            long waitingFor = currentJob.waitingFor.extract(
                    completion.what(),
                    Long.MAX_VALUE,
                    Actionable.SIMULATE);
            if (waitingFor != completion.amount()) {
                boolean productionFinished = currentJob.isTrinityPlan() ?
                        currentJob.trinityExecution().productionComplete() : currentJob.tasks.isEmpty();
                if (productionFinished && waitingFor > completion.amount()) {
                    Data_Energistics.LOGGER.error(
                            "Trinity CPU {} completed order-package dispatches with {} waiting virtual units for {} but only {} were accepted",
                            this.cpu.number(),
                            waitingFor,
                            completion.what(),
                            completion.amount());
                    return false;
                }
                if (waitingFor < completion.amount()) {
                    Data_Energistics.LOGGER.error(
                            "Trinity CPU {} received too many no-output virtual completion units for {}: waiting {}, received {}",
                            this.cpu.number(),
                            completion.what(),
                            waitingFor,
                            completion.amount());
                    return false;
                }
                continue;
            }
            if (currentJob.isTrinityPlan() && !currentJob.trinityExecution().productionComplete()) {
                continue;
            }
            long removed = this.pendingNoOutputCompletions.extract(
                    completion.what(),
                    completion.amount(),
                    Actionable.MODULATE);
            if (removed != completion.amount()) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} no-output virtual completion ledger changed while applying {} x{}",
                        this.cpu.number(),
                        completion.what(),
                        completion.amount());
                return false;
            }

            if (currentJob.isTrinityPlan()) {
                TrinityPlanExecution execution = currentJob.trinityExecution();
                if (!execution.productionComplete() || execution.deliveryRemaining() != completion.amount()) {
                    Data_Energistics.LOGGER.error(
                            "Trinity CPU {} cannot close order-package plan completion {} x{} before its exact production target is ready",
                            this.cpu.number(),
                            completion.what(),
                            completion.amount());
                    return false;
                }
                execution.completeVirtually(completion.amount());
            }
            currentJob.timeTracker.decrementItems(completion.amount(), completion.what().getType());
            currentJob.waitingFor.extract(completion.what(), completion.amount(), Actionable.MODULATE);
            if (!currentJob.isTrinityPlan()) {
                currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - completion.amount());
            } else {
                currentJob.remainingAmount = currentJob.trinityExecution().deliveryRemaining();
            }
            this.cpu.markDirty();
            if (currentJob.isComplete()) {
                finishJob(true);
                return true;
            }
        }
        return true;
    }

    private void recoverVirtualCompletions() {
        boolean discardedNoOutput = !this.pendingNoOutputCompletions.list.isEmpty();
        this.pendingNoOutputCompletions.clear();
        if (this.pendingVirtualCompletions.list.isEmpty()) {
            if (discardedNoOutput) {
                this.cpu.markDirty();
            }
            return;
        }
        ObjectArrayList<GenericStack> recoverable = new ObjectArrayList<>();
        for (var entry : this.pendingVirtualCompletions.list) {
            recoverable.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        this.pendingVirtualCompletions.clear();
        for (GenericStack completion : recoverable) {
            try {
                Math.addExact(this.inventory.list.get(completion.what()), completion.amount());
                this.inventory.insert(completion.what(), completion.amount(), Actionable.MODULATE);
            } catch (RuntimeException exception) {
                this.pendingVirtualCompletions.insert(
                        completion.what(),
                        completion.amount(),
                        Actionable.MODULATE);
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} could not recover accepted virtual completion {} x{}",
                        this.cpu.number(),
                        completion.what(),
                        completion.amount(),
                        exception);
            }
        }
        this.cpu.markDirty();
    }

    private static void addWaiting(TrinityDataCoreExecutingCraftingJob currentJob,
                                   List<GenericStack> additions) {
        for (GenericStack addition : additions) {
            currentJob.waitingFor.list.add(addition.what(), addition.amount());
        }
    }

    private record CapturedPatternInputs(List<GenericStack> inputsPerCraft, KeyCounter ownedInputs) {}

    private record CapturedPatternResults(List<GenericStack> expectedOutputs,
                                          List<GenericStack> expectedContainerItems,
                                          List<GenericStack> waitingPerCraft) {}

    private record ExtractedPatternInputs(KeyCounter[] inputHolder,
                                          List<GenericStack> inputsPerCraft,
                                          List<GenericStack> expectedOutputs,
                                          List<GenericStack> expectedContainerItems,
                                          List<GenericStack> waitingPerCraft) {}

    private record PatternInputCapture(ExtractedPatternInputs inputs, KeyCounter ownedInputs) {}

    private record PreparedPatternCommit(long count,
                                         List<GenericStack> expectedOutputs,
                                         List<GenericStack> expectedContainerItems,
                                         List<DynamicCraftingOutputLedger.Registration> dynamicOutputs,
                                         List<VirtualCraftingCompletion> virtualCompletions,
                                         IPatternDetails scheduledRemoval,
                                         Set<AEKey> changedKeys) {}

    private record PreparedScheduledOutputs(AEItemKey definition, List<GenericStack> outputs)
            implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }
    }

    private record CraftingExecutionOutcome(int physicalAttempts,
                                            boolean dispatched,
                                            boolean currentProposalOutstanding,
                                            boolean proposalDeferred) {

        private static final CraftingExecutionOutcome NONE = new CraftingExecutionOutcome(0, false, false, false);

        private CraftingExecutionOutcome(int physicalAttempts, boolean dispatched) {
            this(physicalAttempts, dispatched, false, false);
        }

        private CraftingExecutionOutcome {
            if (physicalAttempts < 0) {
                throw new IllegalArgumentException("Physical attempt count must not be negative");
            }
            if (dispatched && physicalAttempts == 0) {
                throw new IllegalArgumentException("Advanced crafting work must consume a physical attempt");
            }
            if (dispatched && (currentProposalOutstanding || proposalDeferred)) {
                throw new IllegalArgumentException("A committed work item cannot retain proposal state");
            }
            if (currentProposalOutstanding && proposalDeferred) {
                throw new IllegalArgumentException("A work item cannot be outstanding and deferred together");
            }
        }
    }

    private record WorkerProgressSnapshot(long jobRevision,
                                          long durableRevision,
                                          long lastModifiedOnTick,
                                          long proposalRetryAt,
                                          CraftingDispatchCursor capacitySliceCursor,
                                          boolean cantStoreItems,
                                          boolean workerProposalOutstanding,
                                          TrinityWorkerSchedulingHint schedulingHint) {}

    private record ProviderDispatchOutcome(int physicalAttempts,
                                           boolean dispatched,
                                           boolean currentProposalOutstanding,
                                           boolean proposalDeferred) {

        private static final ProviderDispatchOutcome NONE = new ProviderDispatchOutcome(0, false, false, false);
        private static final ProviderDispatchOutcome AWAITING_PROPOSAL = new ProviderDispatchOutcome(0, false, true, false);
        private static final ProviderDispatchOutcome DEFERRED = new ProviderDispatchOutcome(0, false, false, true);

        private ProviderDispatchOutcome(int physicalAttempts, boolean dispatched) {
            this(physicalAttempts, dispatched, false, false);
        }

        private ProviderDispatchOutcome {
            if (physicalAttempts < 0) {
                throw new IllegalArgumentException("Physical attempt count must not be negative");
            }
            if (dispatched && physicalAttempts == 0) {
                throw new IllegalArgumentException("A dispatched provider slice must consume a physical attempt");
            }
            if (dispatched && (currentProposalOutstanding || proposalDeferred)) {
                throw new IllegalArgumentException("A committed provider slice cannot retain its current proposal");
            }
            if (currentProposalOutstanding && proposalDeferred) {
                throw new IllegalArgumentException("A provider proposal cannot be outstanding and deferred");
            }
        }

        private ProviderDispatchOutcome withCurrentProposalOutstanding(boolean outstanding) {
            return this.currentProposalOutstanding == outstanding ?
                    this :
                    new ProviderDispatchOutcome(
                            this.physicalAttempts,
                            this.dispatched,
                            outstanding,
                            this.proposalDeferred);
        }
    }

    private final class PatternInputTransaction {

        private final ExtractedPatternInputs inputs;
        private final KeyCounter ownedInputs;
        private boolean active = true;

        private PatternInputTransaction(ExtractedPatternInputs inputs, KeyCounter ownedInputs) {
            this.inputs = inputs;
            this.ownedInputs = ownedInputs;
        }

        private ExtractedPatternInputs inputs() {
            return this.inputs;
        }

        private void commit() {
            this.active = false;
        }

        private void rollback() {
            if (!this.active) {
                return;
            }
            this.active = false;
            for (var entry : this.ownedInputs) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
    }

    /**
     * Independently rollbackable ownership of the copies beyond the retained one-craft prototype.
     */
    private final class AdditionalInputTransaction {

        private final KeyCounter ownedInputs = new KeyCounter();
        private boolean active = true;

        private void record(AEKey what, long amount) {
            if (amount <= 0L) {
                return;
            }
            long existing = this.ownedInputs.get(what);
            if (existing > Long.MAX_VALUE - amount) {
                throw new IllegalStateException("Trinity Data Core CPU additional-input ownership overflow for " + what);
            }
            this.ownedInputs.add(what, amount);
        }

        private void commit() {
            this.active = false;
        }

        private void rollback() {
            if (!this.active) {
                return;
            }
            this.active = false;
            for (var entry : this.ownedInputs) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
    }

    private static final class EnergyCharge {

        private final IEnergyService energyService;
        private final double extracted;
        private boolean active = true;

        private EnergyCharge(IEnergyService energyService, double extracted) {
            this.energyService = energyService;
            this.extracted = extracted;
        }

        private void commit() {
            this.active = false;
        }

        private void rollback() {
            if (!this.active) {
                return;
            }
            this.active = false;
            refundEnergy(this.energyService, this.extracted);
        }
    }

    /**
     * Inserts returned crafting outputs into this CPU when it is waiting for them.
     *
     * @param what   key to insert
     * @param amount amount to insert
     * @param type   simulation or mutation mode
     * @return accepted amount
     */
    long insert(AEKey what, long amount, Actionable type) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null || amount <= 0) {
            return 0L;
        }

        long waitingFor = currentJob.waitingFor.extract(what, amount, Actionable.SIMULATE);
        long exactRequested = Math.min(amount, waitingFor);
        boolean exactFinalOutput = what.matches(currentJob.finalOutput);
        boolean exactReceiveLocally = currentJob.isTrinityPlan() || !exactFinalOutput || currentJob.link.isStandalone();
        long exactAccepted;
        if (exactReceiveLocally) {
            exactAccepted = exactRequested;
        } else {
            exactAccepted = currentJob.link.insert(what, exactRequested, type);
            validateLinkAcceptance(what, exactRequested, exactAccepted, type);
        }

        long remainder = amount - exactAccepted;
        Optional<DynamicCraftingOutputLedger.Match> dynamicMatch = Optional.empty();
        if (remainder > 0L && what instanceof AEItemKey actualItem) {
            dynamicMatch = currentJob.dynamicOutputs.match(actualItem, remainder, currentJob.waitingFor.list)
                    .filter(match -> !match.plannedKey().equals(what));
        }
        long dynamicAccepted = dynamicMatch.map(DynamicCraftingOutputLedger.Match::amount).orElse(0L);
        long totalAccepted = Math.addExact(exactAccepted, dynamicAccepted);
        if (totalAccepted <= 0L || type == Actionable.SIMULATE) {
            return totalAccepted;
        }

        if (exactAccepted > 0L) {
            if (exactReceiveLocally) {
                this.exactWorkingInventory.deposit(what, exactAccepted, this.inventory);
                if (currentJob.isTrinityPlan()) {
                    currentJob.trinityExecution().wake(what);
                }
            }
            currentJob.timeTracker.decrementItems(exactAccepted, what.getType());
            currentJob.waitingFor.extract(what, exactAccepted, Actionable.MODULATE);
            currentJob.dynamicOutputs.consumeExact(what, exactAccepted);
            if (exactFinalOutput && !currentJob.isTrinityPlan()) {
                currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - exactAccepted);
            }
            postChange(what);
        }

        if (dynamicMatch.isPresent()) {
            DynamicCraftingOutputLedger.Match match = dynamicMatch.orElseThrow();
            AEItemKey actualItem = (AEItemKey) what;
            if (match.route() == DynamicCraftingOutputLedger.Route.FINAL_OUTPUT && currentJob.isTrinityPlan() &&
                    !sameItemPolicy(currentJob).allowsSameItem(actualItem)) {
                currentJob.trinityExecution().recordActualFinalOutput(actualItem, dynamicAccepted);
            } else {
                this.exactWorkingInventory.deposit(actualItem, dynamicAccepted, this.inventory);
                if (match.route() == DynamicCraftingOutputLedger.Route.INVENTORY &&
                        !sameItemPolicy(currentJob).allowsSameItem(actualItem)) {
                    currentJob.dynamicOutputs.recordInputAlias(actualItem, dynamicAccepted);
                }
                if (currentJob.isTrinityPlan()) {
                    currentJob.trinityExecution().wake(actualItem);
                    currentJob.trinityExecution().wake(match.plannedKey());
                } else if (match.route() == DynamicCraftingOutputLedger.Route.FINAL_OUTPUT) {
                    currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - dynamicAccepted);
                }
            }
            currentJob.timeTracker.decrementItems(dynamicAccepted, match.plannedKey().getType());
            long removed = currentJob.waitingFor.extract(
                    match.plannedKey(),
                    dynamicAccepted,
                    Actionable.MODULATE);
            if (removed != dynamicAccepted) {
                throw new IllegalStateException("Dynamic output waiting counter changed after acceptance simulation");
            }
            currentJob.dynamicOutputs.consume(match, dynamicAccepted);
            postChange(match.plannedKey());
            postChange(actualItem);
        }

        this.cpu.markDirty();
        if (currentJob.isComplete()) {
            finishJob(true);
        }
        return totalAccepted;
    }

    private static void validateLinkAcceptance(AEKey what,
                                               long requested,
                                               long accepted,
                                               Actionable type) {
        if (accepted >= 0L && accepted <= requested) {
            return;
        }
        String message = "Crafting link violated the insertion contract for " + what + " in " + type + " mode: requested " + requested + ", accepted " + accepted + "; expected 0 <= accepted <= requested";
        Data_Energistics.LOGGER.error(message);
        throw new IllegalStateException(message);
    }

    /**
     * Cancels the current job, if one is active.
     */
    void cancel() {
        if (this.job == null) {
            return;
        }
        finishJob(false);
    }

    /**
     * Aborts a job submission that threw after initial materials became CPU-owned.
     *
     * <p>
     * The caller did not receive a successful submission result, so no provider work can have been dispatched. Keep
     * the extracted inventory durable and schedulable for normal idle recovery rather than retaining a partially bound
     * job.
     * </p>
     */
    void abortFailedSubmission() {
        TrinityDataCoreExecutingCraftingJob failedJob = this.job;
        this.proposalCoordinator.cancel();
        cancelPendingReplan();
        this.job = null;
        this.jobRevision = Math.incrementExact(this.jobRevision);
        this.capacitySliceCursor = CraftingDispatchCursor.initial();
        this.proposalRetryAt = -1L;
        this.cpu.markDirty();
        if (failedJob == null) {
            return;
        }
        try {
            failedJob.link.cancel();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} could not cancel a partially submitted crafting link",
                    this.cpu.number(),
                    exception);
        }
        try {
            notifyJobOwner(failedJob, CraftingJobStatusPacket.Status.CANCELLED);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} could not notify the owner of a failed crafting submission",
                    this.cpu.number(),
                    exception);
        }
    }

    /**
     * @return whether the active job is suspended through the AE2 CPU scheduling control
     */
    boolean isJobSuspended() {
        return this.job != null && this.job.suspended;
    }

    /**
     * Suspends or resumes only this worker's active job without changing host lifecycle state.
     *
     * @param suspended requested scheduling state
     * @return whether the active job changed scheduling state
     */
    boolean setJobSuspended(boolean suspended) {
        if (this.job == null || this.job.suspended == suspended) {
            return false;
        }
        this.proposalCoordinator.cancel();
        this.proposalRetryAt = -1L;
        this.jobRevision = Math.incrementExact(this.jobRevision);
        this.job.suspended = suspended;
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        this.cpu.markDirty();
        return true;
    }

    /**
     * @return true when this CPU currently owns a job
     */
    boolean hasJob() {
        return this.job != null;
    }

    /**
     * Returns whether this worker owns state that must survive hiding, saving, and pool reuse.
     */
    boolean hasRetainedState() {
        return this.job != null || !this.inventory.list.isEmpty() ||
                !this.pendingVirtualCompletions.list.isEmpty() || !this.pendingNoOutputCompletions.list.isEmpty();
    }

    /**
     * Returns whether the runtime can discard this worker and reuse its number.
     */
    boolean isReleasable() {
        return !hasRetainedState();
    }

    /**
     * Returns the next worker-level event destination without mutating job or stage state.
     *
     * @param currentTick current server tick
     * @return immediate, event-gated, timed-retry or idle disposition
     */
    TrinityWorkerSchedulingHint schedulingHint(long currentTick) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null) {
            return this.inventory.list.isEmpty() &&
                    this.pendingVirtualCompletions.list.isEmpty() &&
                    this.pendingNoOutputCompletions.list.isEmpty() ?
                            TrinityWorkerSchedulingHint.idle() :
                            TrinityWorkerSchedulingHint.ready();
        }
        if (currentJob.suspended) {
            return TrinityWorkerSchedulingHint.waitingEvent();
        }
        if (!currentJob.isTrinityPlan()) {
            if (this.proposalRetryAt > currentTick) {
                return TrinityWorkerSchedulingHint.retryAt(this.proposalRetryAt);
            }
            if (this.proposalCoordinator.pending()) {
                return TrinityWorkerSchedulingHint.waitingEvent();
            }
            if (!currentJob.tasks.isEmpty()) {
                return TrinityWorkerSchedulingHint.ready();
            }
            return currentJob.waitingFor.list.isEmpty() ?
                    TrinityWorkerSchedulingHint.ready() :
                    TrinityWorkerSchedulingHint.waitingEvent();
        }

        TrinityPlanExecution execution = currentJob.trinityExecution();
        boolean allowNewProposal = this.proposalRetryAt <= currentTick;
        if (execution.hasDispatchableWork(
                work -> this.proposalCoordinator.dispatchable(work, allowNewProposal),
                allowNewProposal)) {
            return TrinityWorkerSchedulingHint.ready();
        }
        if (!allowNewProposal && execution.hasDispatchableWork(
                work -> this.proposalCoordinator.dispatchable(work, true),
                true)) {
            return TrinityWorkerSchedulingHint.retryAt(this.proposalRetryAt);
        }
        if (this.proposalCoordinator.pending()) {
            return TrinityWorkerSchedulingHint.waitingEvent();
        }
        if (!allowNewProposal) {
            return TrinityWorkerSchedulingHint.retryAt(this.proposalRetryAt);
        }
        return switch (execution.status()) {
            case READY, COMPLETED, FAILED -> TrinityWorkerSchedulingHint.ready();
            case WAITING_INPUT -> TrinityWorkerSchedulingHint.waitingEvent();
            case PLANNING -> TrinityWorkerSchedulingHint.retryAt(Math.incrementExact(currentTick));
            case WAITING_DYNAMIC_INPUT, WAITING_PROVIDER, BUDGET_EXHAUSTED -> TrinityWorkerSchedulingHint.retryAt(execution.nextRetryTick().orElseThrow(
                    () -> new IllegalStateException("Timed Trinity execution state is missing its retry tick")));
        };
    }

    /**
     * Cancels only transient proposal state when the runtime route or worker publication pauses.
     */
    void cancelPendingDispatch() {
        this.proposalCoordinator.cancel();
        this.proposalRetryAt = -1L;
    }

    /**
     * @return current final output, or null when idle
     */
    @Nullable
    GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    /**
     * @return progress tracker for the active job
     */
    TrinityDataCoreElapsedTimeTracker elapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        }
        return new TrinityDataCoreElapsedTimeTracker();
    }

    /**
     * @return last tick where crafting-visible state changed
     */
    long getLastModifiedOnTick() {
        return this.lastModifiedOnTick;
    }

    /**
     * @return last job link for AE2 crafting service restoration
     */
    @Nullable
    ICraftingLink getLastLink() {
        return this.job != null ? this.job.link : null;
    }

    /**
     * Adds waiting keys to the output set used by AE2 request watchers.
     *
     * @param waitingFor output key set
     */
    void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job == null) {
            return;
        }
        for (var entry : this.job.waitingFor.list) {
            waitingFor.add(entry.getKey());
        }
    }

    /**
     * @param template requested key
     * @return amount this CPU is waiting for
     */
    long getWaitingFor(AEKey template) {
        if (this.job == null) {
            return 0L;
        }
        return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    /**
     * @param registries registry lookup
     * @return serialized logic state
     */
    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        data.put(INVENTORY_TAG, this.inventory.writeToNBT(registries));
        if (!this.exactWorkingInventory.isEmpty()) {
            data.put(EXACT_INVENTORY_TAG, this.exactWorkingInventory.save(registries));
        }
        data.put(VIRTUAL_COMPLETIONS_TAG, this.pendingVirtualCompletions.writeToNBT(registries));
        data.put(NO_OUTPUT_VIRTUAL_COMPLETIONS_TAG, this.pendingNoOutputCompletions.writeToNBT(registries));
        if (this.job != null) {
            data.put(JOB_TAG, this.job.writeToTag(registries));
        }
        return data;
    }

    /**
     * Restores inventory and job state for this CPU partition.
     *
     * @param data       serialized logic state
     * @param registries registry lookup
     */
    void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        discardPersistedState();
        if (!data.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            Data_Energistics.LOGGER.warn("Ignoring Trinity Data Core CPU logic without a schema version");
            return;
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != LONG_INVENTORY_SCHEMA_VERSION && schemaVersion != SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring Trinity Data Core CPU logic schema version {}; expected {} or {}",
                    schemaVersion,
                    LONG_INVENTORY_SCHEMA_VERSION,
                    SCHEMA_VERSION);
            return;
        }
        Tag rawInventory = data.get(INVENTORY_TAG);
        if (!(rawInventory instanceof ListTag inventoryTag) ||
                (!inventoryTag.isEmpty() && inventoryTag.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error("Ignoring Trinity Data Core CPU logic without a list inventory");
            return;
        }

        this.inventory.readFromNBT(inventoryTag, registries);
        if (schemaVersion == SCHEMA_VERSION && data.contains(EXACT_INVENTORY_TAG)) {
            if (!data.contains(EXACT_INVENTORY_TAG, Tag.TAG_COMPOUND)) {
                Data_Energistics.LOGGER.error("Ignoring Trinity Data Core CPU logic with invalid exact inventory");
                discardPersistedState();
                return;
            }
            try {
                this.exactWorkingInventory.load(data.getCompound(EXACT_INVENTORY_TAG), registries);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Ignoring Trinity Data Core CPU logic with damaged exact inventory",
                        exception);
                discardPersistedState();
                return;
            }
        }
        Tag rawVirtualCompletions = data.get(VIRTUAL_COMPLETIONS_TAG);
        if (!(rawVirtualCompletions instanceof ListTag virtualCompletionsTag) ||
                (!virtualCompletionsTag.isEmpty() &&
                        virtualCompletionsTag.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error(
                    "Ignoring Trinity Data Core CPU logic without a virtual completion ledger");
            discardPersistedState();
            return;
        }
        try {
            this.pendingVirtualCompletions.readFromNBT(virtualCompletionsTag, registries);
            for (var entry : this.pendingVirtualCompletions.list) {
                if (entry.getLongValue() <= 0L) {
                    throw new IllegalArgumentException("Virtual completion amount must be positive");
                }
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Ignoring Trinity Data Core CPU logic with a damaged virtual completion ledger",
                    exception);
            discardPersistedState();
            return;
        }
        Tag rawNoOutputCompletions = data.get(NO_OUTPUT_VIRTUAL_COMPLETIONS_TAG);
        if (!(rawNoOutputCompletions instanceof ListTag noOutputTag) ||
                (!noOutputTag.isEmpty() && noOutputTag.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error(
                    "Ignoring Trinity Data Core CPU logic without a valid no-output virtual completion ledger");
            discardPersistedState();
            return;
        }
        try {
            this.pendingNoOutputCompletions.readFromNBT(noOutputTag, registries);
            for (var entry : this.pendingNoOutputCompletions.list) {
                if (entry.getLongValue() <= 0L) {
                    throw new IllegalArgumentException("No-output virtual completion amount must be positive");
                }
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Ignoring Trinity Data Core CPU logic with a damaged no-output virtual completion ledger",
                    exception);
            discardPersistedState();
            return;
        }
        if (!data.contains(JOB_TAG)) {
            return;
        }
        if (!data.contains(JOB_TAG, Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error("Ignoring Trinity Data Core CPU job because its persisted tag is not a compound");
            return;
        }

        CompoundTag jobData = data.getCompound(JOB_TAG);
        if (!TrinityDataCoreExecutingCraftingJob.hasSupportedSchema(jobData)) {
            return;
        }
        try {
            this.job = new TrinityDataCoreExecutingCraftingJob(
                    jobData,
                    registries,
                    this::postChange,
                    this);
            this.jobRevision = Math.incrementExact(this.jobRevision);
            this.capacitySliceCursor = CraftingDispatchCursor.initial();
            this.proposalRetryAt = -1L;
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Ignoring invalid persisted Trinity Data Core CPU job", exception);
            this.job = null;
            Object2LongMap<AEKey> recoveredCompletion = TrinityDataCoreExecutingCraftingJob.recoverCompletionContents(jobData, registries);
            for (Object2LongMap.Entry<AEKey> entry : recoveredCompletion.object2LongEntrySet()) {
                try {
                    this.inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                } catch (RuntimeException recoveryException) {
                    Data_Energistics.LOGGER.error(
                            "Trinity CPU {} could not recover persisted completion item {} x{}",
                            this.cpu.number(),
                            entry.getKey(),
                            entry.getLongValue(),
                            recoveryException);
                }
            }
            if (!recoveredCompletion.isEmpty()) {
                Data_Energistics.LOGGER.warn(
                        "Trinity CPU {} moved {} persisted completion variants into recovery inventory",
                        this.cpu.number(),
                        recoveredCompletion.size());
            }
            this.cpu.markDirty();
        }
    }

    void discardPersistedState() {
        this.proposalCoordinator.cancel();
        cancelPendingReplan();
        this.inventory.clear();
        this.exactWorkingInventory.clear();
        this.pendingVirtualCompletions.clear();
        this.job = null;
        this.capacitySliceCursor = CraftingDispatchCursor.initial();
        this.proposalRetryAt = -1L;
        this.jobRevision = Math.incrementExact(this.jobRevision);
    }

    static boolean persistedHasJob(CompoundTag data) {
        return data.contains(JOB_TAG, Tag.TAG_COMPOUND);
    }

    static boolean persistedHasRetainedState(CompoundTag data) {
        return data.contains(JOB_TAG) ||
                !data.getList(INVENTORY_TAG, Tag.TAG_COMPOUND).isEmpty() ||
                data.contains(EXACT_INVENTORY_TAG, Tag.TAG_COMPOUND) ||
                !data.getList(VIRTUAL_COMPLETIONS_TAG, Tag.TAG_COMPOUND).isEmpty();
    }

    /**
     * @return true when idle inventory could not be returned to the network
     */
    boolean isCantStoreItems() {
        return this.cantStoreItems;
    }

    /**
     * Moves idle inventory through a durable sink and reports whether no remainder is retained.
     */
    boolean recoverIdleInventory(BiFunction<AEKey, Long, Long> recovery) {
        Preconditions.checkState(this.job == null, "CPU should not have a job while recovering inventory");
        recoverVirtualCompletions();
        for (var entry : this.inventory.list) {
            long available = entry.getLongValue();
            long recovered = recovery.apply(entry.getKey(), available);
            if (recovered < 0L || recovered > available) {
                throw new IllegalStateException("Trinity CPU inventory recovery violated the insertion contract for " +
                        entry.getKey() + ": offered " + available + ", recovered " + recovered);
            }
            if (recovered > 0L) {
                postChange(entry.getKey());
                entry.setValue(available - recovered);
            }
        }
        this.inventory.list.removeZeros();
        boolean recoveredExact = this.exactWorkingInventory.recover(recovery);
        this.cpu.markDirty();
        return recoveredExact && this.inventory.list.isEmpty();
    }

    void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        this.exactWorkingInventory.snapshot().forEach(
                (key, amount) -> TrinityAe2AmountProjection.addToKeyCounter(out, key, amount));
        if (this.job == null) {
            return;
        }
        out.addAll(this.job.waitingFor.list);
        if (this.job.isTrinityPlan()) {
            for (Object2LongMap.Entry<AEKey> entry : this.job.trinityExecution().completionContents().object2LongEntrySet()) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }
        this.job.addScheduledOutputsTo(out);
    }

    long getStored(AEKey template) {
        BigInteger stored = this.exactWorkingInventory.totalAmount(template, this.inventory);
        if (this.job == null || !this.job.isTrinityPlan()) {
            return TrinityAe2AmountProjection.toAe2Amount(stored);
        }
        return TrinityAe2AmountProjection.toAe2Amount(
                stored.add(BigInteger.valueOf(this.job.trinityExecution().completionAmount(template))));
    }

    long getPendingOutputs(AEKey template) {
        return this.job == null ? 0L : this.job.getPendingOutputs(template);
    }

    void addListener(Consumer<AEKey> listener) {
        this.listeners.add(listener);
    }

    void removeListener(Consumer<AEKey> listener) {
        this.listeners.remove(listener);
    }

    private void finishJob(boolean success) {
        if (this.job == null) {
            return;
        }
        recoverVirtualCompletions();
        this.proposalCoordinator.cancel();
        cancelPendingReplan();
        Set<AEKey> pendingOutputKeys = this.job.isTrinityPlan() ?
                this.job.trinityExecution().pendingOutputs().keySet() :
                Set.of();
        if (this.job.isTrinityPlan()) {
            for (Object2LongMap.Entry<AEKey> entry : this.job.trinityExecution().releaseCompletionForStandalone()
                    .object2LongEntrySet()) {
                this.exactWorkingInventory.deposit(entry.getKey(), entry.getLongValue(), this.inventory);
            }
        }
        if (success) {
            this.job.link.markDone();
        } else {
            this.job.link.cancel();
        }

        this.job.waitingFor.clear();
        for (var entry : this.job.tasks.entrySet()) {
            for (GenericStack output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }
        pendingOutputKeys.forEach(this::postChange);
        notifyJobOwner(
                this.job,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);
        this.job = null;
        this.jobRevision = Math.incrementExact(this.jobRevision);
        this.capacitySliceCursor = CraftingDispatchCursor.initial();
        this.proposalRetryAt = -1L;
        this.cpu.markDirty();
        storeItems();
    }

    private void cancelPendingReplan() {
        this.remainingPlanCalculation.cancel();
    }

    private void storeItems() {
        Preconditions.checkState(this.job == null, "CPU should not have a job while dumping inventory");
        if (this.inventory.list.isEmpty() && this.exactWorkingInventory.isEmpty()) {
            return;
        }

        IGrid grid = this.cpu.grid();
        if (grid == null) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        IActionSource source = this.cpu.actionSource();
        this.exactWorkingInventory.returnAll(storage, source);
        for (var entry : this.inventory.list) {
            postChange(entry.getKey());
            long inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();
        this.cpu.markDirty();
    }

    private void postChange(AEKey what) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (Consumer<AEKey> listener : this.listeners) {
            listener.accept(what);
        }
    }

    /** Returns this worker's recent physical-operation load without exposing its mutable tracking window. */
    long recentOperationLoad() {
        return this.operationTracker.recentOperations(TickHandler.instance().getCurrentTick());
    }

    private void notifyJobOwner(TrinityDataCoreExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        Integer playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        Level level = this.cpu.level();
        if (level == null || level.getServer() == null) {
            return;
        }
        ServerPlayer connectedPlayer = IPlayerRegistry.getConnected(level.getServer(), playerId);
        if (connectedPlayer != null) {
            ClientboundPacket message = new CraftingJobStatusPacket(
                    job.link.getCraftingID(),
                    job.finalOutput.what(),
                    job.finalOutput.amount(),
                    job.remainingAmount,
                    status);
            connectedPlayer.connection.send(message);
        }
    }
}
