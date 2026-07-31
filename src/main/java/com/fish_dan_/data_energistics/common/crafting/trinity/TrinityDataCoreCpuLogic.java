package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.WorkerOperationBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternResolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityBorrowingTransaction;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime.TrinityRemainingPlanCalculation;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityCraftingGraphAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

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
import java.util.HashSet;
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
    private static final int SCHEMA_VERSION = 1;
    private static final String INVENTORY_TAG = "inventory";
    private static final String JOB_TAG = "job";
    private static final double ENERGY_TOLERANCE = 0.01D;

    private final TrinityDataCoreVirtualCpu cpu;
    private final TrinityPatternResolver patternResolver = TrinityPatternResolver.create();
    private final TrinityPatternSelector patternSelector = TrinityPatternSelector.create();
    private final TrinityRemainingPlanCalculation remainingPlanCalculation = TrinityRemainingPlanCalculation.create(TrinityPlanningGatewayLifecycle::gateway);
    @Nullable
    private TrinityDataCoreExecutingCraftingJob job;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final WorkerOperationBudget operationBudget = WorkerOperationBudget.create();
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private boolean cantStoreItems;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    TrinityDataCoreCpuLogic(TrinityDataCoreVirtualCpu cpu) {
        this.cpu = cpu;
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

        Integer playerId = source.player()
                .map(player -> player instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        UUID craftId = UUID.randomUUID();
        CraftingLink linkCpu = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, requester == null, false),
                this.cpu);
        this.job = new TrinityDataCoreExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
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
    void tickCraftingLogic(IEnergyService energyService,
                           CraftingService craftingService,
                           CraftingDispatchWindow dispatchWindow) {
        if (!this.cpu.isOnline()) {
            return;
        }
        this.cantStoreItems = false;
        if (this.job == null) {
            storeItems();
            if (!this.inventory.list.isEmpty()) {
                this.cantStoreItems = true;
            }
            return;
        }
        if (!this.cpu.isActive()) {
            return;
        }
        if (this.job.link.isCanceled()) {
            cancel();
            return;
        }
        long currentTick = TickHandler.instance().getCurrentTick();
        int remainingOperations = this.operationBudget.availableOperations(this.cpu.getCoProcessors(), currentTick);
        int started = remainingOperations;
        Level level = this.cpu.level();
        if (level == null) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU cannot tick crafting job without a level");
            return;
        }

        while (remainingOperations > 0 && !dispatchWindow.isExhausted()) {
            int pushedPatterns = executeCrafting(
                    remainingOperations,
                    craftingService,
                    energyService,
                    level,
                    dispatchWindow);
            if (pushedPatterns <= 0) {
                break;
            }
            remainingOperations -= pushedPatterns;
        }
        this.operationBudget.recordTickUsage(currentTick, started - remainingOperations);
    }

    /**
     * Dispatches available pattern tasks to AE2 crafting providers.
     *
     * @param maxPatterns     maximum pattern pushes for this tick
     * @param craftingService AE2 crafting service
     * @param energyService   AE2 energy service
     * @param level           server level used by pattern validation
     * @param dispatchWindow  shared per-grid physical submission budget
     * @return number of physical dispatch operations consumed
     */
    int executeCrafting(int maxPatterns,
                        CraftingService craftingService,
                        IEnergyService energyService,
                        Level level,
                        CraftingDispatchWindow dispatchWindow) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null || maxPatterns <= 0) {
            return 0;
        }
        if (currentJob.isTrinityPlan()) {
            return executeTrinityCrafting(
                    currentJob,
                    craftingService,
                    energyService,
                    level,
                    dispatchWindow);
        }

        int pushedPatterns = 0;
        var iterator = currentJob.tasks.entrySet().iterator();
        while (!dispatchWindow.isExhausted() && iterator.hasNext() && pushedPatterns < maxPatterns) {
            var task = iterator.next();
            if (task.getValue().value <= 0) {
                iterator.remove();
                continue;
            }

            var details = task.getKey();
            boolean accepted;
            try {
                accepted = dispatchToAvailableProvider(
                        currentJob,
                        details,
                        task.getValue(),
                        craftingService.getProviders(details),
                        energyService,
                        level,
                        dispatchWindow);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity CPU {} isolated an unexpected dispatch failure for pattern {}",
                        this.cpu.number(),
                        details.getDefinition(),
                        exception);
                accepted = false;
            }
            if (!accepted) {
                continue;
            }

            pushedPatterns++;
            if (task.getValue().value <= 0L) {
                iterator.remove();
            }
        }

        return pushedPatterns;
    }

    /**
     * Advances one event-selected compact-plan work item without scanning unrelated stages.
     */
    private int executeTrinityCrafting(TrinityDataCoreExecutingCraftingJob currentJob,
                                       CraftingService craftingService,
                                       IEnergyService energyService,
                                       Level level,
                                       CraftingDispatchWindow dispatchWindow) {
        if (advanceTrinityCompletion(currentJob)) {
            return 0;
        }

        TrinityPlanExecution execution = currentJob.trinityExecution();
        long currentTick = TickHandler.instance().getCurrentTick();
        if (execution.status() == TrinityPlanExecution.Status.PLANNING) {
            advanceTrinityReplanning(currentJob, craftingService, currentTick);
            return 0;
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
            return 0;
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
                    TrinityCraftingConfig.settings().dynamicRetryMaxTicks());
            return 0;
        }
        if (!(resolution instanceof TrinityPatternResolver.Matched(IPatternDetails pattern))) {
            execution.markPlanning(work);
            advanceTrinityReplanning(currentJob, craftingService, currentTick);
            return 0;
        }

        IGrid activeGrid = this.cpu.grid();
        if (activeGrid == null) {
            execution.deferProvider(
                    work,
                    currentTick,
                    TrinityCraftingConfig.settings().dynamicRetryMaxTicks());
            return 0;
        }
        MEStorage network = activeGrid.getStorageService().getInventory();
        TrinityCraftingConfig.Settings settings = TrinityCraftingConfig.settings();
        TrinityPatternSelector.Result selection = this.patternSelector.select(
                pattern,
                work.plannedVariantOrdinal(),
                work.cycle(),
                work.maximumLogicalFirings(),
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
                return 0;
            }
            case TrinityPatternSelector.VariantLimit limit -> {
                execution.fail("VARIANT_LIMIT: runtime binding requires " + limit.required() +
                        " variants, configured limit is " + limit.limit());
                return 0;
            }
            case TrinityPatternSelector.ArithmeticOverflow overflow -> {
                execution.fail("ARITHMETIC_OVERFLOW: " + overflow.operation());
                return 0;
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
            return 0;
        }
        TrinityBorrowingTransaction borrowed = borrowing.orElseThrow();

        boolean accepted;
        try {
            accepted = dispatchToAvailableProvider(
                    currentJob,
                    pattern,
                    selected.extractionPattern(),
                    Math.min(work.maximumLogicalFirings(), selected.maximumCrafts()),
                    false,
                    craftingService.getProviders(pattern),
                    energyService,
                    level,
                    dispatchWindow,
                    commit -> {
                        borrowed.commitConsumed(selected.inputsPerCraft(), commit.count());
                        commitTrinityPatternPush(currentJob, execution, work, commit);
                    });
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} isolated an unexpected compact-plan dispatch failure for pattern {}",
                    this.cpu.number(),
                    pattern.getDefinition(),
                    exception);
            accepted = false;
        } finally {
            borrowed.releaseUncommitted();
        }
        if (accepted) {
            return 1;
        }
        if (dispatchWindow.isExhausted()) {
            execution.markBudgetExhausted(work, currentTick);
        } else {
            execution.deferProvider(work, currentTick, settings.dynamicRetryMaxTicks());
        }
        return 0;
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
        TrinityCraftingConfig.Settings settings = TrinityCraftingConfig.settings();
        MEStorage network = activeGrid.getStorageService().getInventory();
        Optional<TrinityCraftingGraphSnapshot> graphSnapshot = graphAccess.data_energistics$trinityCraftingGraphSnapshot();
        TrinityRemainingPlanCalculation.Result result = this.remainingPlanCalculation.advance(
                graphSnapshot,
                () -> graphSnapshot
                        .map(snapshot -> captureReplanAvailability(snapshot, network))
                        .orElseGet(Map::of),
                execution.finalOutput().what(),
                BigInteger.valueOf(execution.deliveryRemaining()),
                execution.quantityMode(),
                settings);
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
            return;
        }
        reservation.orElseThrow().retain();
        execution.replaceRemainingPlan(ready.plan(), currentTick);
        this.cpu.markDirty();
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
     * Attempts providers directly through AE2's cyclic iterable and stops consuming it as soon as one accepts. Pattern
     * input extraction is lazy, and the retained one-craft prototype is rolled back by {@code finally} on every
     * unaccepted exit.
     */
    private boolean dispatchToAvailableProvider(TrinityDataCoreExecutingCraftingJob currentJob,
                                                IPatternDetails details,
                                                TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                                Iterable<ICraftingProvider> candidates,
                                                IEnergyService energyService,
                                                Level level,
                                                CraftingDispatchWindow dispatchWindow) {
        return dispatchToAvailableProvider(
                currentJob,
                details,
                details,
                task.value,
                true,
                candidates,
                energyService,
                level,
                dispatchWindow,
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
    private boolean dispatchToAvailableProvider(TrinityDataCoreExecutingCraftingJob currentJob,
                                                IPatternDetails details,
                                                IPatternDetails extractionDetails,
                                                long remainingCrafts,
                                                boolean validateScheduledOutputs,
                                                Iterable<ICraftingProvider> candidates,
                                                IEnergyService energyService,
                                                Level level,
                                                CraftingDispatchWindow dispatchWindow,
                                                Consumer<PreparedPatternCommit> acceptedDispatch) {
        PatternInputTransaction inputTransaction = null;
        try {
            ExtractedPatternInputs inputs = null;
            double powerPerCraft = 0.0D;
            long maximumCount = 0L;
            var candidateIterator = candidates.iterator();
            while (!dispatchWindow.isExhausted() && candidateIterator.hasNext()) {
                ICraftingProvider provider = candidateIterator.next();
                if (!dispatchWindow.canAttempt(provider, details)) {
                    continue;
                }
                try (CraftingDispatchWindow.SubmissionScope submission = dispatchWindow.beginSubmission(provider, details)) {
                    if (providerBusy(provider, details, dispatchWindow)) {
                        continue;
                    }
                    if (dispatchWindow.isExhausted()) {
                        continue;
                    }
                    if (inputTransaction == null) {
                        inputTransaction = beginPatternInputTransaction(extractionDetails, level);
                        if (inputTransaction == null) {
                            return false;
                        }
                        inputs = inputTransaction.inputs();
                        powerPerCraft = CraftingCpuHelper.calculatePatternPower(inputs.inputHolder());
                        maximumCount = limitByInputAvailability(inputs.inputsPerCraft(), remainingCrafts);
                        maximumCount = limitByWaitingCapacity(currentJob, inputs.waitingPerCraft(), maximumCount);
                        maximumCount = limitByEnergy(powerPerCraft, maximumCount, energyService);
                        if (maximumCount <= 0L) {
                            return false;
                        }
                    }
                    if (dispatchWindow.isExhausted()) {
                        continue;
                    }

                    CountedCraftingPreparation preparation;
                    try {
                        preparation = prepareAdmission(
                                provider,
                                details,
                                inputs.inputHolder(),
                                maximumCount,
                                target -> dispatchWindow.canAttempt(provider, details, target));
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
                                null,
                                CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP);
                        continue;
                    }
                    for (CraftingDispatchRejection rejection : preparation.rejections()) {
                        dispatchWindow.recordResult(provider, details, rejection.target(), rejection.status());
                    }
                    if (!preparation.accepted() || dispatchWindow.isExhausted()) {
                        continue;
                    }

                    CountedCraftingAdmission admission = preparation.admission();
                    CraftingDispatchTarget target = preparation.target();
                    if (admission == null || target == null) {
                        throw new IllegalStateException("Accepted counted preparation lost its admission or target");
                    }
                    if (!dispatchWindow.canAttempt(provider, details, target)) {
                        continue;
                    }
                    long count;
                    try {
                        count = validatedAdmissionCount(provider, admission, maximumCount);
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
                                null,
                                CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP);
                        continue;
                    }
                    if (dispatchWindow.isExhausted()) {
                        continue;
                    }

                    PreparedPatternCommit commit = preparePatternCommit(
                            currentJob,
                            details,
                            remainingCrafts,
                            inputs,
                            count,
                            validateScheduledOutputs);
                    if (commit == null) {
                        return false;
                    }
                    if (dispatchWindow.isExhausted()) {
                        continue;
                    }
                    AdditionalInputTransaction additionalInputs = extractAdditionalInputs(inputs.inputsPerCraft(), count);
                    if (additionalInputs == null) {
                        return false;
                    }
                    if (dispatchWindow.isExhausted()) {
                        additionalInputs.rollback();
                        continue;
                    }
                    EnergyCharge energyCharge = chargeEnergy(energyService, powerPerCraft * count);
                    if (energyCharge == null) {
                        additionalInputs.rollback();
                        return false;
                    }
                    try {
                        KeyCounter[] rejectedPrototype = copyInputCounters(inputs.inputHolder());
                        if (!submission.tryAcquire(target)) {
                            continue;
                        }

                        boolean accepted;
                        try {
                            accepted = admission.commit(inputs.inputHolder());
                        } catch (RuntimeException exception) {
                            if (transferredInputOwnership(
                                    provider,
                                    details,
                                    admission,
                                    target,
                                    rejectedPrototype,
                                    inputs.inputHolder())) {
                                Data_Energistics.LOGGER.error(
                                        "Crafting provider {} target {} threw after taking ownership of {} crafts for pattern {} on Trinity CPU {}; recording the batch as dispatched to prevent input duplication",
                                        provider,
                                        target.stableIdentity(),
                                        count,
                                        details.getDefinition(),
                                        this.cpu.number(),
                                        exception);
                                dispatchWindow.recordResult(
                                        provider,
                                        details,
                                        target,
                                        CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP);
                                commitAcceptedDispatch(
                                        inputTransaction,
                                        additionalInputs,
                                        energyCharge,
                                        commit,
                                        acceptedDispatch);
                                return true;
                            }
                            Data_Energistics.LOGGER.error(
                                    "Crafting provider {} target {} threw before taking ownership of {} crafts for pattern {} on Trinity CPU {}; isolating it for this dispatch window",
                                    provider,
                                    target.stableIdentity(),
                                    count,
                                    details.getDefinition(),
                                    this.cpu.number(),
                                    exception);
                            dispatchWindow.recordResult(
                                    provider,
                                    details,
                                    null,
                                    CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP);
                            continue;
                        }
                        if (!accepted) {
                            if (transferredInputOwnership(
                                    provider,
                                    details,
                                    admission,
                                    target,
                                    rejectedPrototype,
                                    inputs.inputHolder())) {
                                Data_Energistics.LOGGER.error(
                                        "Crafting provider {} target {} returned false after taking ownership of {} crafts for pattern {} on Trinity CPU {}; recording the batch as dispatched to prevent input duplication",
                                        provider,
                                        target.stableIdentity(),
                                        count,
                                        details.getDefinition(),
                                        this.cpu.number());
                                dispatchWindow.recordResult(
                                        provider,
                                        details,
                                        target,
                                        CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP);
                                commitAcceptedDispatch(
                                        inputTransaction,
                                        additionalInputs,
                                        energyCharge,
                                        commit,
                                        acceptedDispatch);
                                return true;
                            }
                            dispatchWindow.recordResult(
                                    provider,
                                    details,
                                    target,
                                    CraftingDispatchStatus.REJECTED);
                            continue;
                        }

                        dispatchWindow.recordResult(
                                provider,
                                details,
                                target,
                                CraftingDispatchStatus.ACCEPTED);
                        commitAcceptedDispatch(
                                inputTransaction,
                                additionalInputs,
                                energyCharge,
                                commit,
                                acceptedDispatch);
                        return true;
                    } finally {
                        energyCharge.rollback();
                        additionalInputs.rollback();
                    }
                }
            }
            return false;
        } finally {
            if (inputTransaction != null) {
                inputTransaction.rollback();
            }
        }
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
     * Resolves the ownership boundary after a rejected or failed provider commit. A throwing ownership query violates
     * the admission contract; the conservative result is ownership transferred, preventing duplicate inputs.
     */
    private boolean transferredInputOwnership(ICraftingProvider provider,
                                              IPatternDetails details,
                                              CountedCraftingAdmission admission,
                                              CraftingDispatchTarget target,
                                              KeyCounter[] rejectedPrototype,
                                              KeyCounter[] currentPrototype) {
        if (!inputCountersMatch(rejectedPrototype, currentPrototype)) {
            return true;
        }
        try {
            return admission.hasTransferredInputOwnership();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Crafting provider {} target {} failed to report input ownership for pattern {} on Trinity CPU {}; treating ownership as transferred to prevent duplication",
                    provider,
                    target.stableIdentity(),
                    details.getDefinition(),
                    this.cpu.number(),
                    exception);
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

    private static CountedCraftingPreparation prepareAdmission(
                                                               ICraftingProvider provider,
                                                               IPatternDetails details,
                                                               KeyCounter[] prototype,
                                                               long maximumCount,
                                                               CraftingDispatchTargetAvailability targetAvailability) {
        if (provider instanceof CountedCraftingProvider countedProvider) {
            return countedProvider.prepareBatch(
                    details,
                    prototype,
                    maximumCount,
                    targetAvailability);
        }
        return CountedCraftingPreparation.accepted(
                new SingleCraftingAdmission(provider, details),
                CraftingDispatchTarget.provider());
    }

    private static long validatedAdmissionCount(ICraftingProvider provider,
                                                CountedCraftingAdmission admission,
                                                long maximumCount) {
        long count = admission.count();
        if (count <= 0L || count > maximumCount) {
            throw new IllegalStateException(
                    "Crafting provider " + provider + " admitted " + count +
                            " crafts outside requested range 1.." + maximumCount);
        }
        return count;
    }

    @Nullable
    private PatternInputTransaction beginPatternInputTransaction(IPatternDetails details, Level level) {
        KeyCounter expectedOutputs = new KeyCounter();
        KeyCounter expectedContainerItems = new KeyCounter();
        KeyCounter[] inputHolder = CraftingCpuHelper.extractPatternInputs(
                details,
                this.inventory,
                level,
                expectedOutputs,
                expectedContainerItems);
        if (inputHolder == null) {
            return null;
        }
        CapturedPatternInputs capturedInputs = capturePatternInputs(inputHolder);
        if (capturedInputs == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot track overflowing extracted pattern inputs");
            CraftingCpuHelper.reinjectPatternInputs(this.inventory, inputHolder);
            return null;
        }
        CapturedPatternResults capturedResults = capturePatternResults(expectedOutputs, expectedContainerItems);
        if (capturedResults == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot track overflowing expected pattern outputs");
            CraftingCpuHelper.reinjectPatternInputs(this.inventory, inputHolder);
            return null;
        }
        return new PatternInputTransaction(
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

    private static KeyCounter[] copyInputCounters(KeyCounter[] source) {
        KeyCounter[] copy = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            KeyCounter counter = new KeyCounter();
            counter.addAll(source[index]);
            copy[index] = counter;
        }
        return copy;
    }

    private static boolean inputCountersMatch(KeyCounter[] expected, KeyCounter[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (counterDiffers(expected[index], actual[index]) || counterDiffers(actual[index], expected[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean counterDiffers(KeyCounter expected, KeyCounter actual) {
        for (var entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) {
                return true;
            }
        }
        return false;
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
    private static PreparedPatternCommit preparePatternCommit(TrinityDataCoreExecutingCraftingJob currentJob,
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

    private record PreparedPatternCommit(long count,
                                         List<GenericStack> expectedOutputs,
                                         List<GenericStack> expectedContainerItems,
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

    /**
     * Single-craft admission preserves the exact behavior of providers that do not opt into counted dispatch.
     */
    private record SingleCraftingAdmission(ICraftingProvider provider, IPatternDetails details)
            implements CountedCraftingAdmission {

        @Override
        public long count() {
            return 1L;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            return this.provider.pushPattern(this.details, prototype);
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
     * @return true when this CPU currently owns a job
     */
    boolean hasJob() {
        return this.job != null;
    }

    /**
     * Returns whether this worker owns state that must survive hiding, saving, and pool reuse.
     */
    boolean hasRetainedState() {
        return this.job != null || !this.inventory.list.isEmpty();
    }

    /**
     * Returns whether the runtime can discard this worker and reuse its number.
     */
    boolean isReleasable() {
        return !hasRetainedState();
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
        if (schemaVersion != SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring Trinity Data Core CPU logic schema version {}; expected {}",
                    schemaVersion,
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
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Ignoring invalid persisted Trinity Data Core CPU job", exception);
            this.job = null;
        }
    }

    void discardPersistedState() {
        cancelPendingReplan();
        this.inventory.clear();
        this.job = null;
    }

    static boolean persistedHasJob(CompoundTag data) {
        return data.contains(JOB_TAG, Tag.TAG_COMPOUND);
    }

    static boolean persistedHasRetainedState(CompoundTag data) {
        return data.contains(JOB_TAG) ||
                !data.getList(INVENTORY_TAG, Tag.TAG_COMPOUND).isEmpty();
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
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
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
        cancelPendingReplan();
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
        notifyJobOwner(
                this.job,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);
        this.job = null;
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
