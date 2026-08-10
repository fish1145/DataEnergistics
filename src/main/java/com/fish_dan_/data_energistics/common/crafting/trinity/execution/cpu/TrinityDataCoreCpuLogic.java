package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletion;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletionMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle.TrinityDispatchProposalLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerProposalCoordinator;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerSchedulingHint;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.WorkerOperationBudget;
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
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchStepResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternResolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.route.TrinityCraftingExecutionRoute;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityBorrowingTransaction;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityRemainingPlanCalculation;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityCraftingGraphAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputProjection;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
    private static final int SCHEMA_VERSION = 2;
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final String INVENTORY_TAG = "inventory";
    private static final String VIRTUAL_COMPLETIONS_TAG = "virtual_completions";
    private static final String NO_OUTPUT_VIRTUAL_COMPLETIONS_TAG = "no_output_virtual_completions";
    private static final String JOB_TAG = "job";
    private static final double ENERGY_TOLERANCE = 0.01D;

    private final TrinityDataCoreVirtualCpu cpu;
    private final CraftingDispatchCommitter dispatchCommitter = CraftingDispatchCommitter.create();
    private final ProviderCapacityResolver capacityResolver = ProviderCapacityResolver.create(
            TrinityPlanningGatewayLifecycle::computationCache);
    private final DispatchCapacityPlanner capacityPlanner = DispatchCapacityPlanner.create(
            TrinityPlanningGatewayLifecycle::computationCache);
    private final TrinityPatternResolver patternResolver = TrinityPatternResolver.create();
    private final TrinityPatternSelector patternSelector = TrinityPatternSelector.create();
    private final TrinityRemainingPlanCalculation remainingPlanCalculation = TrinityRemainingPlanCalculation.create(TrinityPlanningGatewayLifecycle::gateway);
    private final TrinityWorkerProposalCoordinator proposalCoordinator = TrinityWorkerProposalCoordinator.create(
            TrinityDispatchProposalLifecycle::scheduler);
    @Nullable
    private TrinityDataCoreExecutingCraftingJob job;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final ListCraftingInventory pendingVirtualCompletions;
    private final ListCraftingInventory pendingNoOutputCompletions;
    private final WorkerOperationBudget operationBudget = WorkerOperationBudget.create();
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
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
        if (this.cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        if (!this.inventory.list.isEmpty()) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU inventory is not empty when a job is submitted");
        }

        GenericStack missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, this.inventory, source);
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
            CraftingExecutionOutcome outcome = executeCrafting(
                    1,
                    craftingService,
                    energyService,
                    level,
                    dispatchWindow,
                    dispatchBudget);
            physicalAttempts = outcome.physicalAttempts();
            if (physicalAttempts > 1) {
                throw new IllegalStateException("A Trinity worker dispatch step attempted more than one provider call");
            }
            this.operationBudget.recordTickUsage(currentTick, physicalAttempts);
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
        int remainingOperations = this.operationBudget.availableOperations(this.cpu.getCoProcessors(), currentTick);
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
            this.operationBudget.recordTickUsage(currentTick, started - remainingOperations);
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
            if (!this.inventory.list.isEmpty()) {
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
            return;
        }
        this.proposalRetryAt = -1L;
    }

    private boolean readyForDispatch(long currentTick) {
        if (!this.cpu.isOnline() || !this.cpu.isActive() || this.job == null || this.job.suspended) {
            return false;
        }
        if (this.job.link.isCanceled() || this.proposalRetryAt > currentTick) {
            return false;
        }
        return this.operationBudget.availableOperations(this.cpu.getCoProcessors(), currentTick) > 0;
    }

    private CraftingDispatchStepResult stepResult(WorkerProgressSnapshot before,
                                                  long currentTick,
                                                  int physicalAttempts,
                                                  CraftingDispatchWindow dispatchWindow) {
        WorkerProgressSnapshot after = progressSnapshot(currentTick);
        boolean hasReadyWork = !dispatchWindow.isExhausted() &&
                after.schedulingHint().kind() == TrinityWorkerSchedulingHint.Kind.READY &&
                this.operationBudget.availableOperations(this.cpu.getCoProcessors(), currentTick) > 0;
        return new CraftingDispatchStepResult(
                physicalAttempts == 1,
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
            if (outcome.proposalOutstanding() || outcome.proposalDeferred()) {
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
     * Advances one event-selected compact-plan work item without scanning unrelated stages.
     */
    private CraftingExecutionOutcome executeTrinityCrafting(TrinityDataCoreExecutingCraftingJob currentJob,
                                                            int maxPatterns,
                                                            CraftingService craftingService,
                                                            IEnergyService energyService,
                                                            Level level,
                                                            CraftingDispatchWindow dispatchWindow,
                                                            CraftingDispatchBudget dispatchBudget) {
        if (advanceTrinityCompletion(currentJob)) {
            return CraftingExecutionOutcome.NONE;
        }

        TrinityPlanExecution execution = currentJob.trinityExecution();
        long currentTick = TickHandler.instance().getCurrentTick();
        TrinityCrafting settings = DataEnergisticsConfiguration.INSTANCE.trinityCrafting();
        if (execution.status() == TrinityPlanExecution.Status.PLANNING) {
            advanceTrinityReplanning(currentJob, craftingService, currentTick);
            return CraftingExecutionOutcome.NONE;
        }
        var workOffer = execution.poll(currentTick);
        if (workOffer.isEmpty()) {
            if (execution.status() == TrinityPlanExecution.Status.FAILED) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} terminated compact job {}: {}",
                        this.cpu.number(),
                        currentJob.link.getCraftingID(),
                        execution.failureReason().orElse("unknown runtime failure"));
                finishJob(false);
            } else if (execution.deadlocked(!currentJob.waitingFor.list.isEmpty())) {
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
                    settings.dynamicRetryMaxTicks());
            return CraftingExecutionOutcome.NONE;
        }
        if (!(resolution instanceof TrinityPatternResolver.Matched(IPatternDetails pattern))) {
            execution.markPlanning(work);
            advanceTrinityReplanning(currentJob, craftingService, currentTick);
            return CraftingExecutionOutcome.NONE;
        }

        IGrid activeGrid = this.cpu.grid();
        if (activeGrid == null) {
            execution.deferProvider(
                    work,
                    currentTick,
                    settings.dynamicRetryMaxTicks());
            return CraftingExecutionOutcome.NONE;
        }
        MEStorage network = activeGrid.getStorageService().getInventory();
        long maximumLogicalFirings = work.maximumLogicalFirings();
        if (work.cycle()) {
            TrinityPlanExecution.CycleWaveLimit cycleWaveLimit;
            try {
                cycleWaveLimit = execution.maximumCycleLogicalFirings(
                        work,
                        key -> combinedCycleSeedAvailability(network, key));
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
                        settings.dynamicRetryMaxTicks());
                return CraftingExecutionOutcome.NONE;
            }
        }
        TrinityPatternSelector.Result selection = this.patternSelector.select(
                pattern,
                work.plannedVariantOrdinal(),
                work.cycle(),
                maximumLogicalFirings,
                this.inventory.list::get,
                key -> work.cycle() ? simulateNetworkExtraction(network, key) : 0L,
                settings.maxBindingVariants());
        TrinityPatternSelector.Selected selected;
        switch (selection) {
            case TrinityPatternSelector.Selected value -> selected = value;
            case TrinityPatternSelector.Unavailable unavailable -> {
                if (work.cycle()) {
                    execution.deferDynamicInput(
                            work,
                            unavailable.observedKeys(),
                            currentTick,
                            settings.dynamicRetryMaxTicks());
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

        Optional<TrinityBorrowingTransaction> borrowing = work.cycle() ?
                borrowDynamicInputs(
                        selected.inputsPerCraft(),
                        selected.maximumCrafts(),
                        network,
                        execution.borrowingLedger()) :
                Optional.of(newBorrowingTransaction(network, execution.borrowingLedger()));
        if (borrowing.isEmpty()) {
            execution.deferDynamicInput(
                    work,
                    selected.observedKeys(),
                    currentTick,
                    settings.dynamicRetryMaxTicks());
            return CraftingExecutionOutcome.NONE;
        }
        TrinityBorrowingTransaction borrowed = borrowing.orElseThrow();

        ProviderDispatchOutcome outcome;
        try {
            outcome = dispatchToAvailableProvider(
                    currentJob,
                    pattern,
                    selected.extractionPattern(),
                    Math.min(maximumLogicalFirings, selected.maximumCrafts()),
                    work,
                    work.generation(),
                    false,
                    craftingProviderPublications(craftingService),
                    stablePatternIdentity(work.patternIdentity()),
                    energyService,
                    level,
                    dispatchWindow,
                    maxPatterns,
                    dispatchBudget,
                    commit -> {
                        borrowed.commitConsumed(selected.inputsPerCraft(), commit.count());
                        commitTrinityPatternPush(currentJob, execution, work, commit);
                    });
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
        if (outcome.proposalOutstanding()) {
            return new CraftingExecutionOutcome(outcome.physicalAttempts(), false);
        }
        if (dispatchWindow.isExhausted()) {
            execution.markBudgetExhausted(work, currentTick);
        } else {
            execution.deferProvider(work, currentTick, settings.dynamicRetryMaxTicks());
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
        TrinityCrafting settings = DataEnergisticsConfiguration.INSTANCE.trinityCrafting();
        MEStorage network = activeGrid.getStorageService().getInventory();
        Optional<TrinityCraftingGraphSnapshot> graphSnapshot = graphAccess.data_energistics$trinityCraftingGraphSnapshot();
        TrinityRemainingPlanCalculation.Result result = this.remainingPlanCalculation.advance(
                graphSnapshot,
                craftingProviderPublications(craftingService).publicationScope(),
                () -> graphSnapshot
                        .map(snapshot -> captureReplanAvailability(snapshot, network))
                        .orElseGet(Map::of),
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

        long availableStorage = this.cpu.getAvailableStorage();
        if (ready.plan().bytes() > availableStorage) {
            Data_Energistics.LOGGER.warn(
                    "Trinity CPU {} retained job {} because replacement plan at catalog revision {} requires {} bytes but the worker currently has {}",
                    this.cpu.number(),
                    currentJob.link.getCraftingID(),
                    ready.revision(),
                    ready.plan().bytes(),
                    availableStorage);
            this.remainingPlanCalculation.retrySameRevision(
                    ready.revision(),
                    currentTick,
                    settings.dynamicRetryMaxTicks());
            return;
        }

        Optional<TrinityBorrowingTransaction> reservation = reserveReplacementInputs(
                ready.plan(),
                network,
                execution.borrowingLedger());
        if (reservation.isEmpty()) {
            Data_Energistics.LOGGER.warn(
                    "Trinity CPU {} retained job {} while replacement inputs for catalog revision {} were unavailable",
                    this.cpu.number(),
                    currentJob.link.getCraftingID(),
                    ready.revision());
            this.remainingPlanCalculation.retrySameRevision(
                    ready.revision(),
                    currentTick,
                    settings.dynamicRetryMaxTicks());
            return;
        }
        reservation.orElseThrow().retain();
        HashSet<AEKey> changedOutputKeys = new HashSet<>(execution.pendingOutputs().keySet());
        execution.replaceRemainingPlan(ready.plan(), currentTick);
        changedOutputKeys.addAll(execution.pendingOutputs().keySet());
        this.remainingPlanCalculation.acceptRevision(ready.revision());
        this.cpu.markDirty();
        changedOutputKeys.forEach(this::postChange);
    }

    private Map<AEKey, BigInteger> captureReplanAvailability(TrinityCraftingGraphSnapshot snapshot,
                                                             MEStorage network) {
        LinkedHashMap<AEKey, BigInteger> available = new LinkedHashMap<>();
        for (AEKey key : snapshot.keys()) {
            long cpuAmount = this.inventory.list.get(key);
            long networkAmount = simulateNetworkExtraction(network, key);
            BigInteger amount = BigInteger.valueOf(cpuAmount).add(BigInteger.valueOf(networkAmount));
            if (amount.signum() > 0) {
                available.put(key, amount);
            }
        }
        return Map.copyOf(available);
    }

    private Optional<TrinityBorrowingTransaction> reserveReplacementInputs(TrinityCraftingPlan replacement,
                                                                           MEStorage network,
                                                                           TrinityBorrowingLedger ledger) {
        ArrayList<GenericStack> inputs = new ArrayList<>(replacement.initialExpectedInputs().size());
        try {
            replacement.initialExpectedInputs().forEach((key, amount) -> inputs.add(new GenericStack(key, amount.longValueExact())));
        } catch (ArithmeticException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} cannot reserve overflowing replacement-plan inputs",
                    this.cpu.number(),
                    exception);
            return Optional.empty();
        }
        return borrowDynamicInputs(inputs, 1L, network, ledger);
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
    private long combinedCycleSeedAvailability(MEStorage network, AEKey key) {
        long cpuAmount = this.inventory.list.get(key);
        long networkAmount = simulateNetworkExtraction(network, key);
        if (cpuAmount < 0L || networkAmount < 0L) {
            throw new IllegalStateException("Trinity cycle seed availability cannot be negative");
        }
        return Long.MAX_VALUE - cpuAmount < networkAmount ? Long.MAX_VALUE : cpuAmount + networkAmount;
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
        if (!execution.productionComplete() || !currentJob.waitingFor.list.isEmpty()) {
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
            long available = this.inventory.extract(target.what(), target.amount(), Actionable.SIMULATE);
            if (available != target.amount()) {
                String reason = "RUNTIME_DEADLOCK: completed Trinity production owns " + available +
                        " of required delivery " + target.amount() + " for " + target.what();
                Data_Energistics.LOGGER.error(reason);
                execution.fail(reason);
                finishJob(false);
                return true;
            }
            long extracted = this.inventory.extract(target.what(), target.amount(), Actionable.MODULATE);
            if (extracted != target.amount()) {
                this.inventory.insert(target.what(), extracted, Actionable.MODULATE);
                String reason = "RUNTIME_DEADLOCK: Trinity completion inventory changed while sealing " + target.what();
                Data_Energistics.LOGGER.error(reason);
                execution.fail(reason);
                finishJob(false);
                return true;
            }
            execution.sealCompletion(extracted);
            postChange(target.what());
        }

        if (currentJob.link.isStandalone()) {
            execution.releaseCompletionForStandalone().ifPresent(released -> {
                this.inventory.insert(released.what(), released.amount(), Actionable.MODULATE);
                postChange(released.what());
            });
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
            if (accepted > 0L) {
                execution.recordDelivered(accepted);
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
            return ProviderDispatchOutcome.NONE;
        }
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
            // A provider window must not be lost solely because the optimistic background proposal has not completed.
            // Release it and use the synchronous safe path for this already-selected worker pass.
            this.proposalCoordinator.cancel();
            proposalDecision = TrinityWorkerProposalCoordinator.Empty.INSTANCE;
            synchronousFallback = true;
        }
        if (proposalDecision instanceof TrinityWorkerProposalCoordinator.NoCapacity) {
            // Capacity is a transient server-thread fact. A completed background miss must recapture the current
            // provider window instead of suppressing this worker for the next fair runtime pass.
            proposalDecision = TrinityWorkerProposalCoordinator.Empty.INSTANCE;
        }
        CraftingDispatchProposal selectedProposal = proposalDecision instanceof TrinityWorkerProposalCoordinator.Ready ready ?
                ready.proposal() : null;
        boolean asynchronousSelection = selectedProposal != null;

        ExtractedPatternInputs prototype;
        long maximumCount;
        long currentTick;
        ProviderCapacityCapture capacityCapture = null;
        List<ProviderCapacitySnapshot> snapshots;
        try (CraftingDispatchWindow.CapacityCaptureScope ignored = dispatchWindow.beginProviderCapacityCapture()) {
            prototype = capturePatternInputPrototype(extractionDetails, level);
            if (prototype == null || dispatchWindow.isExhausted()) {
                return settleProposal(asynchronousSelection, ProviderDispatchOutcome.NONE);
            }

            double prototypePower = CraftingCpuHelper.calculatePatternPower(prototype.inputHolder());
            maximumCount = limitByUnextractedInputAvailability(prototype.inputsPerCraft(), remainingCrafts);
            maximumCount = limitByWaitingCapacity(currentJob, prototype.waitingPerCraft(), maximumCount);
            maximumCount = limitByEnergy(prototypePower, maximumCount, energyService);
            if (maximumCount <= 0L || dispatchWindow.isExhausted()) {
                return settleProposal(asynchronousSelection, ProviderDispatchOutcome.NONE);
            }

            currentTick = TickHandler.instance().getCurrentTick();
            if (selectedProposal == null) {
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
            } else {
                snapshots = List.of(selectedProposal.target());
            }
        }
        if (snapshots.isEmpty()) {
            return settleProposal(asynchronousSelection, ProviderDispatchOutcome.NONE);
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
        if (asynchronousSelection) {
            maximumCount = Math.min(maximumCount, selectedProposal.logicalCrafts());
            physicalCallLimit = 1;
        }

        int physicalAttempts = 0;
        int inspectedSnapshots = 0;
        CraftingDispatchCursor searchCursor = this.capacitySliceCursor;
        Set<ProviderCapacitySnapshot> inspectedTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        while (inspectedSnapshots < snapshots.size() &&
                physicalAttempts < physicalCallLimit &&
                !dispatchWindow.isExhausted()) {
            DispatchCapacitySlicePlan candidatePlan = asynchronousSelection ?
                    new DispatchCapacitySlicePlan(List.of(new DispatchCapacitySlicePlan.Slice(
                            selectedProposal.target(),
                            Math.min(selectedProposal.logicalCrafts(), maximumCount),
                            selectedProposal.nextCursor()))) :
                    this.capacityPlanner.plan(
                            capacityCapture,
                            BigInteger.valueOf(maximumCount),
                            1,
                            searchCursor);
            if (candidatePlan.slices().isEmpty()) {
                break;
            }
            DispatchCapacitySlicePlan.Slice slice = candidatePlan.slices().getFirst();
            ProviderCapacitySnapshot snapshot = slice.target();
            if (!inspectedTargets.add(snapshot)) {
                break;
            }
            inspectedSnapshots++;
            searchCursor = slice.nextCursor();
            long prototypeOffer = offeredCount(snapshot, slice, maximumCount);
            ICraftingProvider provider = resolveCurrentProvider(
                    nativeSingleCraftFallback,
                    publications,
                    details,
                    prototype.inputHolder(),
                    prototypeOffer,
                    patternIdentity,
                    snapshot,
                    currentTick);
            if (provider == null) {
                if (asynchronousSelection) {
                    this.proposalCoordinator.discardStale();
                }
                continue;
            }
            if (!dispatchWindow.canAttempt(provider, details, snapshot.route())) {
                continue;
            }

            try (CraftingDispatchWindow.SubmissionScope submission = dispatchWindow.beginSubmission(provider, details)) {
                if (providerBusy(provider, details, dispatchWindow)) {
                    continue;
                }
                if (dispatchWindow.isExhausted()) {
                    break;
                }

                PatternInputTransaction inputTransaction = beginPatternInputTransaction(extractionDetails, level);
                if (inputTransaction == null) {
                    return settleProposal(
                            asynchronousSelection,
                            new ProviderDispatchOutcome(physicalAttempts, false));
                }
                try {
                    if (dispatchWindow.isExhausted()) {
                        break;
                    }
                    ExtractedPatternInputs inputs = inputTransaction.inputs();
                    double powerPerCraft = CraftingCpuHelper.calculatePatternPower(inputs.inputHolder());
                    long currentMaximum = limitByInputAvailability(inputs.inputsPerCraft(), remainingCrafts);
                    currentMaximum = limitByWaitingCapacity(currentJob, inputs.waitingPerCraft(), currentMaximum);
                    currentMaximum = limitByEnergy(powerPerCraft, currentMaximum, energyService);
                    if (currentMaximum <= 0L || dispatchWindow.isExhausted()) {
                        break;
                    }

                    long offeredCount = offeredCount(snapshot, slice, currentMaximum);
                    ICraftingProvider currentProvider = resolveCurrentProvider(
                            nativeSingleCraftFallback,
                            publications,
                            details,
                            inputs.inputHolder(),
                            offeredCount,
                            patternIdentity,
                            snapshot,
                            currentTick);
                    if (currentProvider != provider) {
                        if (asynchronousSelection) {
                            this.proposalCoordinator.discardStale();
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
                            this.proposalCoordinator.discardStale();
                        }
                        return settleProposal(
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }

                    CountedCraftingPreparation preparation;
                    try {
                        preparation = prepareSelectedProvider(
                                provider,
                                details,
                                inputs.inputHolder(),
                                offeredCount,
                                snapshot,
                                dispatchWindow,
                                nativeSingleCraftFallback);
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
                        continue;
                    }
                    for (CraftingDispatchRejection rejection : preparation.rejections()) {
                        dispatchWindow.recordResult(provider, details, rejection.target(), rejection.status());
                    }
                    if (!preparation.accepted()) {
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
                            !dispatchWindow.canAttempt(provider, details, target)) {
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
                        continue;
                    }
                    if (dispatchWindow.isExhausted()) {
                        break;
                    }

                    PreparedPatternCommit commit = preparePatternCommit(
                            currentJob,
                            details,
                            remainingCrafts,
                            inputs,
                            count,
                            validateScheduledOutputs);
                    if (commit == null || dispatchWindow.isExhausted()) {
                        break;
                    }
                    AdditionalInputTransaction additionalInputs = extractAdditionalInputs(inputs.inputsPerCraft(), count);
                    if (additionalInputs == null) {
                        return settleProposal(
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
                            this.proposalCoordinator.discardStale();
                        }
                        return settleProposal(
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }
                    PatternInputTransaction acceptedInputs = inputTransaction;
                    CraftingDispatchAccountingDelta accounting = CraftingDispatchAccountingDelta.create(
                            count,
                            () -> commitAcceptedDispatch(
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
                        this.capacitySliceCursor = asynchronousSelection ?
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
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, false));
                    }
                    if (result.dispatched()) {
                        return settleProposal(
                                asynchronousSelection,
                                new ProviderDispatchOutcome(physicalAttempts, true));
                    }
                } finally {
                    inputTransaction.rollback();
                }
            }
        }
        return settleProposal(
                asynchronousSelection,
                new ProviderDispatchOutcome(physicalAttempts, false));
    }

    private ProviderDispatchOutcome settleProposal(boolean consumedProposal, ProviderDispatchOutcome outcome) {
        if (consumedProposal || outcome.dispatched()) {
            this.proposalCoordinator.cancel();
        }
        return outcome.withProposalOutstanding(this.proposalCoordinator.outstanding());
    }

    private CountedCraftingPreparation prepareSelectedProvider(
                                                               ICraftingProvider provider,
                                                               IPatternDetails details,
                                                               KeyCounter[] prototype,
                                                               long offeredCount,
                                                               ProviderCapacitySnapshot snapshot,
                                                               CraftingDispatchWindow dispatchWindow,
                                                               boolean nativeSingleCraftFallback) {
        if (nativeSingleCraftFallback) {
            return CountedCraftingProviderAdapters.prepareNativeSingleCraft(
                    provider,
                    details,
                    target -> dispatchWindow.canAttempt(provider, details, target));
        }
        return CountedCraftingProviderAdapters.prepare(
                provider,
                details,
                prototype,
                offeredCount,
                snapshot,
                target -> dispatchWindow.canAttempt(provider, details, target));
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
    private void commitAcceptedDispatch(PatternInputTransaction inputTransaction,
                                        AdditionalInputTransaction additionalInputs,
                                        EnergyCharge energyCharge,
                                        PreparedPatternCommit commit,
                                        Consumer<PreparedPatternCommit> acceptedDispatch) {
        energyCharge.commit();
        additionalInputs.commit();
        inputTransaction.commit();
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
        KeyCounter[] inputHolder = CraftingCpuHelper.extractPatternInputs(
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
        ArrayList<GenericStack> captured = new ArrayList<>();
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
        for (GenericStack waiting : waitingPerCraft) {
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
        ArrayList<GenericStack> snapshot = new ArrayList<>();
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
        if (expectedOutputs == null || expectedContainerItems == null || scheduledOutputs == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot commit overflowing pattern outputs");
            return null;
        }
        if (validateScheduledOutputs) {
            for (GenericStack output : scheduledOutputs) {
                if (currentJob.getPendingOutputs(output.what()) < output.amount()) {
                    Data_Energistics.LOGGER.error(
                            "Trinity Data Core CPU cannot remove {} scheduled units of {}",
                            output.amount(),
                            output.what());
                    return null;
                }
            }
        }
        HashSet<AEKey> changedKeys = new HashSet<>();
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
                virtualCompletions,
                new PreparedScheduledOutputs(details.getDefinition(), scheduledOutputs),
                Set.copyOf(changedKeys));
    }

    @Nullable
    private static List<GenericStack> scaleAmounts(List<GenericStack> amounts, long count) {
        ArrayList<GenericStack> scaled = new ArrayList<>();
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
                                          PreparedPatternCommit commit) {
        addWaiting(currentJob, commit.expectedOutputs());
        addWaiting(currentJob, commit.expectedContainerItems());
        execution.recordAccepted(work, commit.count());
        for (GenericStack output : commit.expectedOutputs()) {
            currentJob.timeTracker.addMaxItems(output.amount(), output.what().getType());
        }
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
                long simulated = network.extract(
                        input.what(),
                        missing,
                        Actionable.SIMULATE,
                        this.cpu.actionSource());
                if (simulated != missing) {
                    transaction.releaseUncommitted();
                    return Optional.empty();
                }
                transaction.validateRecord(input.what(), missing);
                long extracted = network.extract(
                        input.what(),
                        missing,
                        Actionable.MODULATE,
                        this.cpu.actionSource());
                if (extracted > 0L) {
                    this.inventory.insert(input.what(), extracted, Actionable.MODULATE);
                    transaction.record(input.what(), extracted);
                    postChange(input.what());
                }
                if (extracted != missing) {
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

        ArrayList<GenericStack> intermediate = new ArrayList<>();
        ArrayList<GenericStack> finalResults = new ArrayList<>();
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
        ArrayList<GenericStack> completions = new ArrayList<>();
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
        ArrayList<GenericStack> recoverable = new ArrayList<>();
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

    private record CraftingExecutionOutcome(int physicalAttempts, boolean dispatched) {

        private static final CraftingExecutionOutcome NONE = new CraftingExecutionOutcome(0, false);

        private CraftingExecutionOutcome {
            if (physicalAttempts < 0) {
                throw new IllegalArgumentException("Physical attempt count must not be negative");
            }
            if (dispatched && physicalAttempts == 0) {
                throw new IllegalArgumentException("Advanced crafting work must consume a physical attempt");
            }
        }
    }

    private record WorkerProgressSnapshot(long jobRevision,
                                          long durableRevision,
                                          long lastModifiedOnTick,
                                          long proposalRetryAt,
                                          CraftingDispatchCursor capacitySliceCursor,
                                          boolean cantStoreItems,
                                          boolean proposalOutstanding,
                                          TrinityWorkerSchedulingHint schedulingHint) {}

    private record ProviderDispatchOutcome(int physicalAttempts,
                                           boolean dispatched,
                                           boolean proposalOutstanding,
                                           boolean proposalDeferred) {

        private static final ProviderDispatchOutcome NONE = new ProviderDispatchOutcome(0, false, false, false);
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
            if (dispatched && (proposalOutstanding || proposalDeferred)) {
                throw new IllegalArgumentException("A committed provider slice cannot retain its stale proposal");
            }
            if (proposalOutstanding && proposalDeferred) {
                throw new IllegalArgumentException("A provider proposal cannot be outstanding and deferred");
            }
        }

        private ProviderDispatchOutcome withProposalOutstanding(boolean outstanding) {
            return this.proposalOutstanding == outstanding ?
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
        if (waitingFor <= 0) {
            return 0L;
        }
        long requested = Math.min(amount, waitingFor);
        boolean finalOutput = what.matches(currentJob.finalOutput);
        boolean receiveLocally = currentJob.isTrinityPlan() || !finalOutput || currentJob.link.isStandalone();
        long accepted;
        if (receiveLocally) {
            accepted = requested;
        } else {
            accepted = currentJob.link.insert(what, requested, type);
            validateLinkAcceptance(what, requested, accepted, type);
        }
        if (accepted <= 0L || type == Actionable.SIMULATE) {
            return accepted;
        }

        if (receiveLocally) {
            this.inventory.insert(what, accepted, Actionable.MODULATE);
            if (currentJob.isTrinityPlan()) {
                currentJob.trinityExecution().wake(what);
            }
        }
        currentJob.timeTracker.decrementItems(accepted, what.getType());
        currentJob.waitingFor.extract(what, accepted, Actionable.MODULATE);
        if (finalOutput && !currentJob.isTrinityPlan()) {
            currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - accepted);
        }
        this.cpu.markDirty();

        if (currentJob.isComplete()) {
            finishJob(true);
        }
        return accepted;
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
        if (this.proposalCoordinator.pending()) {
            return TrinityWorkerSchedulingHint.ready();
        }
        if (this.proposalRetryAt > currentTick) {
            return TrinityWorkerSchedulingHint.retryAt(this.proposalRetryAt);
        }
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
            if (!currentJob.tasks.isEmpty()) {
                return TrinityWorkerSchedulingHint.ready();
            }
            return currentJob.waitingFor.list.isEmpty() ?
                    TrinityWorkerSchedulingHint.ready() :
                    TrinityWorkerSchedulingHint.waitingEvent();
        }

        TrinityPlanExecution execution = currentJob.trinityExecution();
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
        if (schemaVersion != SCHEMA_VERSION && schemaVersion != LEGACY_SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring Trinity Data Core CPU logic schema version {}; expected {} or legacy {}",
                    schemaVersion,
                    SCHEMA_VERSION,
                    LEGACY_SCHEMA_VERSION);
            return;
        }
        Tag rawInventory = data.get(INVENTORY_TAG);
        if (!(rawInventory instanceof ListTag inventoryTag) ||
                (!inventoryTag.isEmpty() && inventoryTag.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error("Ignoring Trinity Data Core CPU logic without a list inventory");
            return;
        }

        this.inventory.readFromNBT(inventoryTag, registries);
        if (schemaVersion == SCHEMA_VERSION) {
            Tag rawVirtualCompletions = data.get(VIRTUAL_COMPLETIONS_TAG);
            if (!(rawVirtualCompletions instanceof ListTag virtualCompletionsTag) ||
                    (!virtualCompletionsTag.isEmpty() &&
                            virtualCompletionsTag.getElementType() != Tag.TAG_COMPOUND)) {
                Data_Energistics.LOGGER.error(
                        "Ignoring Trinity Data Core CPU logic without a virtual completion ledger");
                this.inventory.clear();
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
            if (data.contains(NO_OUTPUT_VIRTUAL_COMPLETIONS_TAG)) {
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
            }
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
        }
    }

    void discardPersistedState() {
        this.proposalCoordinator.cancel();
        cancelPendingReplan();
        this.inventory.clear();
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
        this.cpu.markDirty();
        return this.inventory.list.isEmpty();
    }

    void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job == null) {
            return;
        }
        out.addAll(this.job.waitingFor.list);
        if (this.job.isTrinityPlan()) {
            this.job.trinityExecution().completionOffer().ifPresent(offer -> out.add(offer.what(), offer.amount()));
        }
        this.job.addScheduledOutputsTo(out);
    }

    long getStored(AEKey template) {
        long stored = this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        if (this.job == null || !this.job.isTrinityPlan()) {
            return stored;
        }
        return this.job.trinityExecution().completionOffer()
                .filter(offer -> offer.what().equals(template))
                .map(offer -> Math.addExact(stored, offer.amount()))
                .orElse(stored);
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
            this.job.trinityExecution().releaseCompletionForStandalone().ifPresent(released -> this.inventory.insert(released.what(), released.amount(), Actionable.MODULATE));
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
        if (this.inventory.list.isEmpty()) {
            return;
        }

        IGrid grid = this.cpu.grid();
        if (grid == null) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        IActionSource source = this.cpu.actionSource();
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

    /**
     * Returns this worker's recent physical-operation load without exposing its mutable budget window.
     */
    long recentOperationLoad() {
        return this.operationBudget.recentOperations(TickHandler.instance().getCurrentTick());
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
