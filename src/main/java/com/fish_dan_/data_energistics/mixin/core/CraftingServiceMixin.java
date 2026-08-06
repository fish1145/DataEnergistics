package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.VirtualGridBridge;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle.TrinityDispatchProposalLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalMetrics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchBudget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchGovernor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchMetrics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.TrinityServerTickMetrics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.CraftingCpuCandidate;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.CraftingCpuCandidateSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.CraftingCpuCandidateSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.CraftingCpuKind;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.CraftingCpuSelectionGroup;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection.CraftingCpuSelectionRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchCompletion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchCompletionImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchParticipantImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission.TrinityPlanAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityWorkerDispatchActivity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityCraftingGraphAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityInitialPlanCalculation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityInitialPlanningRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture.NetworkCraftingGraphCaptureSource;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture.TrinityCraftingGraphRebuilder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture.TrinityCraftingProviderRevision;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityCraftingRequestContext;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;
import com.fish_dan_.data_energistics.configuration.runtime.TrinityDispatchGovernorState;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.util.LongAmountMath;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin
                                           implements TrinityCraftingRuntimeRegistry, TrinityCraftingGraphAccess, CraftingProviderPublicationAccess {

    @Unique
    private static final CraftingCpuCandidateSelector DATA_ENERGISTICS_CPU_SELECTOR = CraftingCpuCandidateSelector.create();

    @Unique
    private static final TrinityPlanAdmission DATA_ENERGISTICS_PLAN_ADMISSION = TrinityPlanAdmission.create();

    @Unique
    private static final TrinityInitialPlanCalculation DATA_ENERGISTICS_INITIAL_PLAN_CALCULATION = TrinityInitialPlanCalculation.create(
            TrinityPlanningGatewayLifecycle::gateway);

    @Unique
    private static final AtomicLong DATA_ENERGISTICS_INITIAL_PLANNING_SEQUENCE = new AtomicLong();

    @Unique
    private final TrinityCraftingRuntimeRegistry.Local dataEnergistics$trinityCraftingRuntimeRegistry = TrinityCraftingRuntimeRegistry.createLocal();

    @Unique
    private long dataEnergistics$lastProcessedTrinityDataCoreCraftingLogicChangeTick;

    /**
     * Transient cursor rotates which published Trinity runtime receives the first dispatch opportunity.
     */
    @Unique
    private int dataEnergistics$nextTrinityRuntimeTickStart;

    /**
     * Transient per-hardware cursors balance successful auto-submissions without coupling unrelated CPU groups.
     */
    @Unique
    private final Map<CraftingCpuSelectionGroup, String> dataEnergistics$nextCpuSubmitByGroup = new HashMap<>();

    /**
     * Configuration revision and runtime-derived Governor are replaced as one server-thread-only value.
     */
    @Unique
    private TrinityDispatchGovernorState dataEnergistics$dispatchGovernorState = TrinityDispatchGovernorState.capture(
            DataEnergisticsConfiguration.INSTANCE);

    /**
     * Server-thread-only incremental capture state; published snapshots contain no reference back to this object.
     */
    @Unique
    @Nullable
    private TrinityCraftingGraphRebuilder dataEnergistics$trinityCraftingGraphRebuilder;

    /**
     * Prevents one malformed provider revision from writing an error every server tick.
     */
    @Unique
    private long dataEnergistics$lastLoggedGraphFailureRevision = Long.MIN_VALUE;

    /**
     * Ensures an empty Grid releases its server-lifetime planning partition exactly once until it becomes active again.
     */
    @Unique
    private boolean dataEnergistics$planningGridCleared;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private IEnergyService energyGrid;

    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private NetworkCraftingProviders craftingProviders;

    @Shadow
    private boolean updateList;

    @Shadow
    private long lastProcessedCraftingLogicChangeTick;

    @Override
    public CraftingProviderPublicationIndex data_energistics$craftingProviderPublicationIndex() {
        if (!(this.craftingProviders instanceof CraftingProviderPublicationIndex publicationIndex)) {
            throw new IllegalStateException("AE2 crafting providers do not expose the Trinity publication index");
        }
        return publicationIndex;
    }

    @WrapMethod(method = "beginCraftingCalculation")
    private Future<ICraftingPlan> dataEnergistics$beginTrinityCraftingCalculation(
                                                                                  Level level,
                                                                                  ICraftingSimulationRequester simRequester,
                                                                                  AEKey what,
                                                                                  long amount,
                                                                                  CalculationStrategy strategy,
                                                                                  Operation<Future<ICraftingPlan>> original) {
        if (level == null || simRequester == null || what == null || strategy == null || amount <= 0L) {
            return original.call(level, simRequester, what, amount, strategy);
        }

        TrinityCrafting settings = DataEnergisticsConfiguration.INSTANCE.trinityCrafting();
        IActionSource actionSource = simRequester.getActionSource();
        CraftingQuantityMode quantityMode = TrinityCraftingRequestContext.resolve(
                actionSource,
                settings.defaultQuantityMode());
        long maxTrinityBytes = dataEnergistics$maxEligibleTrinityBytes(actionSource);
        if (maxTrinityBytes <= 0L) {
            return original.call(level, simRequester, what, amount, strategy);
        }

        long requestId = DATA_ENERGISTICS_INITIAL_PLANNING_SEQUENCE.incrementAndGet();
        Optional<TrinityCraftingGraphSnapshot> graph = data_energistics$trinityCraftingGraphSnapshot();
        Map<AEKey, BigInteger> available = graph
                .map(this::dataEnergistics$capturePlanningInventory)
                .orElse(Map.of());

        CraftingProviderPublicationIndex publications = data_energistics$craftingProviderPublicationIndex();
        long gridScope = publications.publicationScope();
        long graphRevision = graph
                .map(TrinityCraftingGraphSnapshot::revision)
                .orElse(publications.publicationRevision());
        return TrinityPlanningGatewayLifecycle.gateway().begin(
                true,
                gridScope,
                graphRevision,
                () -> dataEnergistics$calculateInitialTrinityPlan(
                        gridScope,
                        requestId,
                        graph,
                        what,
                        amount,
                        quantityMode,
                        available,
                        maxTrinityBytes,
                        settings),
                () -> original.call(level, simRequester, what, amount, strategy));
    }

    @Unique
    private TrinityPlanningAttempt dataEnergistics$calculateInitialTrinityPlan(
                                                                               long gridScope,
                                                                               long requestId,
                                                                               Optional<TrinityCraftingGraphSnapshot> graph,
                                                                               AEKey target,
                                                                               long amount,
                                                                               CraftingQuantityMode quantityMode,
                                                                               Map<AEKey, BigInteger> available,
                                                                               long maxTrinityBytes,
                                                                               TrinityCrafting settings) throws Exception {
        if (graph.isEmpty()) {
            TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.STALE_GRAPH,
                    Component.translatable("gui.data_energistics.trinity_planning.graph_unavailable"),
                    Map.of("request", Long.toString(requestId)));
            Data_Energistics.LOGGER.info(
                    "Trinity planning fallback request={} target={} mode={} revision=-1 reason={} metadata={}",
                    requestId,
                    target,
                    quantityMode,
                    diagnostic.code(),
                    diagnostic.metadata());
            return TrinityPlanningAttempt.failure(diagnostic);
        }

        TrinityInitialPlanningRequest request = TrinityInitialPlanningRequest.builder()
                .gridScope(gridScope)
                .requestId(requestId)
                .graph(graph.orElseThrow())
                .target(target)
                .requestedAmount(BigInteger.valueOf(amount))
                .quantityMode(quantityMode)
                .available(available)
                .settings(settings)
                .maxTrinityBytes(maxTrinityBytes)
                .build();
        return DATA_ENERGISTICS_INITIAL_PLAN_CALCULATION.calculate(request);
    }

    @Unique
    private Map<AEKey, BigInteger> dataEnergistics$capturePlanningInventory(
                                                                            TrinityCraftingGraphSnapshot graph) {
        LinkedHashMap<AEKey, BigInteger> available = new LinkedHashMap<>();
        var cachedInventory = this.grid.getStorageService().getCachedInventory();
        for (AEKey key : graph.keys()) {
            long amount = cachedInventory.get(key);
            if (amount > 0L) {
                available.put(key, BigInteger.valueOf(amount));
            }
        }
        return Collections.unmodifiableMap(available);
    }

    @Unique
    private long dataEnergistics$maxEligibleTrinityBytes(@Nullable IActionSource source) {
        long maxBytes = 0L;
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            if (runtime.publishedCpus().isEmpty()) {
                continue;
            }
            TrinityDataCoreVirtualCpu coordinator = runtime.publishedCpus().getFirst();
            boolean sourceAllowed = source == null ?
                    coordinator.getSelectionMode() == CpuSelectionMode.ANY :
                    coordinator.canBeAutoSelectedFor(source);
            if (coordinator.number() == 0 &&
                    coordinator.isActive() &&
                    coordinator.canAcceptJob() &&
                    sourceAllowed) {
                maxBytes = Math.max(maxBytes, coordinator.getAvailableStorage());
            }
        }
        return maxBytes;
    }

    @Override
    public boolean data_energistics$publish(IGridNode node, TrinityDataCoreCraftingRuntime runtime) {
        if (node.getGrid() != this.grid && !((VirtualGridBridge) this.grid).containsIncomingVirtualMember(node)) {
            Data_Energistics.LOGGER.error("Cannot publish a Trinity crafting runtime through a different grid service");
            throw new IllegalArgumentException("The Trinity crafting node belongs to a different grid");
        }
        return this.dataEnergistics$trinityCraftingRuntimeRegistry.data_energistics$publish(node, runtime);
    }

    @Override
    public boolean data_energistics$withdraw(IGridNode node) {
        return this.dataEnergistics$trinityCraftingRuntimeRegistry.data_energistics$withdraw(node);
    }

    @Override
    public Optional<TrinityCraftingGraphSnapshot> data_energistics$trinityCraftingGraphSnapshot() {
        TrinityCraftingGraphRebuilder rebuilder = this.dataEnergistics$trinityCraftingGraphRebuilder;
        return rebuilder == null ? Optional.empty() : rebuilder.publishedSnapshot();
    }

    @Inject(method = "addNode", at = @At("RETURN"))
    private void dataEnergistics$markAddedTrinityDataCoreCpuListDirty(IGridNode gridNode,
                                                                      CompoundTag savedData,
                                                                      CallbackInfo ci) {
        if (gridNode.getOwner() instanceof TrinityAccessHatchBlockEntity) {
            this.updateList = true;
        }
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void dataEnergistics$removeTrinityDataCoreCpuNode(IGridNode gridNode, CallbackInfo ci) {
        boolean withdrawn = data_energistics$withdraw(gridNode);
        if (withdrawn || gridNode.getOwner() instanceof TrinityAccessHatchBlockEntity) {
            this.updateList = true;
        }
    }

    @Inject(method = "updateCPUClusters", at = @At("RETURN"))
    private void dataEnergistics$updateTrinityDataCoreCpuClusters(CallbackInfo ci) {
        Map<IGridNode, TrinityDataCoreCraftingRuntime> scannedRuntimes = new IdentityHashMap<>();
        for (IGridNode node : this.grid.getMachineNodes(TrinityAccessHatchBlockEntity.class)) {
            TrinityAccessHatchBlockEntity hatch = (TrinityAccessHatchBlockEntity) node.getOwner();
            TrinityDataCoreCraftingRuntime runtime = hatch.boundCraftingRuntime();
            if (runtime != null) {
                scannedRuntimes.put(node, runtime);
            }
        }

        List<TrinityDataCoreCraftingRuntime> reconciledRuntimes = this.dataEnergistics$trinityCraftingRuntimeRegistry.reconcile(scannedRuntimes);
        CraftingService service = (CraftingService) (Object) this;
        for (TrinityDataCoreCraftingRuntime runtime : reconciledRuntimes) {
            runtime.restoreLinks(service);
        }
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void dataEnergistics$tickTrinityDataCoreCpuClusters(CallbackInfo ci) {
        if (this.grid.isEmpty()) {
            return;
        }
        dataEnergistics$reloadDispatchGovernor();
        CraftingDispatchGovernor governor = this.dataEnergistics$dispatchGovernorState.governor();
        MinecraftServer server = this.grid.getPivot().getLevel().getServer();
        long gridGeneration = data_energistics$craftingProviderPublicationIndex().publicationScope();
        List<TrinityDataCoreCraftingRuntime> runtimes = dataEnergistics$trinityDataCoreRuntimes();
        if (runtimes.isEmpty()) {
            CraftingDispatchCompletion completion = new CraftingDispatchCompletionImpl(
                    "publicationScope=" + gridGeneration + ", gridIdentity=" + System.identityHashCode(this.grid),
                    () -> dataEnergistics$completeEmptyTrinityDispatchTick(server, gridGeneration, governor),
                    (source, failure) -> governor.recordUnexpectedFailure(
                            source + " for Grid publication scope " + gridGeneration,
                            failure));
            dataEnergistics$registerTrinityDispatchCompletion(server, gridGeneration, governor, completion);
            return;
        }
        CraftingService service = (CraftingService) (Object) this;
        CraftingDispatchBudget dispatchBudget = governor.budget();
        CraftingDispatchWindow dispatchWindow = CraftingDispatchWindow.create(
                dispatchBudget.dispatchLimits(),
                TrinityServerTickMetrics.dispatchBudget(server));
        List<TrinityDataCoreCraftingRuntime> preparedRuntimes = runtimes;
        try {
            for (TrinityDataCoreCraftingRuntime runtime : runtimes) {
                runtime.prepareTick();
            }
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error(
                    "Trinity Grid publication scope {} failed while preparing its server dispatch participant",
                    gridGeneration,
                    failure);
            governor.recordUnexpectedFailure("Grid dispatch preparation", failure);
            preparedRuntimes = List.of();
        }
        List<TrinityDataCoreCraftingRuntime> dispatchRuntimes = preparedRuntimes;
        CraftingDispatchParticipantImpl participant = new CraftingDispatchParticipantImpl(
                "publicationScope=" + gridGeneration + ", gridIdentity=" + System.identityHashCode(this.grid),
                dispatchRuntimes,
                this.dataEnergistics$nextTrinityRuntimeTickStart,
                this.energyGrid,
                service,
                dispatchWindow,
                dispatchBudget,
                nextCursor -> this.dataEnergistics$nextTrinityRuntimeTickStart = nextCursor,
                () -> dataEnergistics$completeTrinityDispatchTick(
                        server,
                        gridGeneration,
                        runtimes,
                        dispatchWindow,
                        governor),
                (source, failure) -> governor.recordUnexpectedFailure(
                        source + " for Grid publication scope " + gridGeneration,
                        failure));
        dataEnergistics$registerTrinityDispatchParticipant(server, gridGeneration, governor, participant);
    }

    @Unique
    private void dataEnergistics$registerTrinityDispatchParticipant(
                                                                      MinecraftServer server,
                                                                      long gridGeneration,
                                                                      CraftingDispatchGovernor governor,
                                                                      CraftingDispatchParticipantImpl participant) {
        try {
            TrinityServerTickMetrics.registerDispatchParticipant(server, participant);
        } catch (RuntimeException failure) {
            dataEnergistics$handleTrinityDispatchRegistrationFailure(
                    gridGeneration,
                    governor,
                    participant,
                    failure);
        }
    }

    @Unique
    private void dataEnergistics$registerTrinityDispatchCompletion(MinecraftServer server,
                                                                    long gridGeneration,
                                                                    CraftingDispatchGovernor governor,
                                                                    CraftingDispatchCompletion completion) {
        try {
            TrinityServerTickMetrics.registerDispatchCompletion(server, completion);
        } catch (RuntimeException failure) {
            dataEnergistics$handleTrinityDispatchRegistrationFailure(
                    gridGeneration,
                    governor,
                    completion,
                    failure);
        }
    }

    @Unique
    private void dataEnergistics$handleTrinityDispatchRegistrationFailure(
                                                                            long gridGeneration,
                                                                            CraftingDispatchGovernor governor,
                                                                            CraftingDispatchCompletion completion,
                                                                            RuntimeException failure) {
        Data_Energistics.LOGGER.error(
                "Trinity Grid publication scope {} could not register its server dispatch boundary",
                gridGeneration,
                failure);
        governor.recordUnexpectedFailure("server dispatch registration", failure);
        try {
            completion.completeTick();
        } catch (RuntimeException completionFailure) {
            Data_Energistics.LOGGER.error(
                    "Trinity Grid publication scope {} failed while completing an unregistered dispatch tick",
                    gridGeneration,
                    completionFailure);
            governor.recordUnexpectedFailure("unregistered dispatch completion", completionFailure);
        }
    }

    @Unique
    private void dataEnergistics$completeEmptyTrinityDispatchTick(MinecraftServer server,
                                                                  long gridGeneration,
                                                                  CraftingDispatchGovernor governor) {
        dataEnergistics$refreshLastProcessedTrinityCraftingLogicChange(0L);
        DispatchProposalMetrics proposalMetrics = TrinityDispatchProposalLifecycle.scheduler()
                .snapshotAndResetMetrics(gridGeneration);
        governor.observe(CraftingDispatchMetrics.captureWithoutDispatch(
                TrinityServerTickMetrics.lastCompletedNanos(server),
                proposalMetrics));
    }

    @Unique
    private void dataEnergistics$refreshLastProcessedTrinityCraftingLogicChange(
                                                                                List<TrinityDataCoreCraftingRuntime> runtimes) {
        long latestChange = 0L;
        for (TrinityDataCoreCraftingRuntime runtime : runtimes) {
            latestChange = Math.max(latestChange, runtime.getLastModifiedOnTick());
        }
        dataEnergistics$refreshLastProcessedTrinityCraftingLogicChange(latestChange);
    }

    @Unique
    private void dataEnergistics$refreshLastProcessedTrinityCraftingLogicChange(long latestChange) {
        if (latestChange != this.dataEnergistics$lastProcessedTrinityDataCoreCraftingLogicChangeTick) {
            this.dataEnergistics$lastProcessedTrinityDataCoreCraftingLogicChangeTick = latestChange;
            this.lastProcessedCraftingLogicChangeTick = -1L;
        }
    }

    @Unique
    private void dataEnergistics$completeTrinityDispatchTick(MinecraftServer server,
                                                             long gridGeneration,
                                                             List<TrinityDataCoreCraftingRuntime> runtimes,
                                                             CraftingDispatchWindow dispatchWindow,
                                                             CraftingDispatchGovernor governor) {
        dataEnergistics$refreshLastProcessedTrinityCraftingLogicChange(runtimes);
        TrinityWorkerDispatchActivity workerActivity = TrinityWorkerDispatchActivity.EMPTY;
        for (TrinityDataCoreCraftingRuntime runtime : runtimes) {
            workerActivity = workerActivity.combine(runtime.dispatchActivity());
        }
        DispatchProposalMetrics proposalMetrics = TrinityDispatchProposalLifecycle.scheduler()
                .snapshotAndResetMetrics(gridGeneration);
        long serverTickNanos = TrinityServerTickMetrics.lastCompletedNanos(server);
        governor.observe(CraftingDispatchMetrics.capture(
                serverTickNanos,
                dispatchWindow,
                proposalMetrics,
                workerActivity.busiestShare()));
    }

    /**
     * Replaces observation state atomically when the dedicated COMMON config is reloaded.
     */
    @Unique
    private void dataEnergistics$reloadDispatchGovernor() {
        this.dataEnergistics$dispatchGovernorState = this.dataEnergistics$dispatchGovernorState.refresh(
                DataEnergisticsConfiguration.INSTANCE);
    }

    @Inject(method = "onServerEndTick", at = @At("TAIL"))
    private void dataEnergistics$advanceTrinityCraftingGraph(CallbackInfo ci) {
        if (this.grid.isEmpty()) {
            dataEnergistics$clearEmptyGridPlanningState();
            return;
        }
        this.dataEnergistics$planningGridCleared = false;
        try {
            if (this.dataEnergistics$trinityCraftingGraphRebuilder == null) {
                this.dataEnergistics$trinityCraftingGraphRebuilder = new TrinityCraftingGraphRebuilder(
                        new NetworkCraftingGraphCaptureSource(
                                this.craftingProviders,
                                this.grid.getPivot().getLevel().registryAccess()),
                        System::nanoTime);
            }
            long budgetNanos = TimeUnit.MILLISECONDS.toNanos(
                    DataEnergisticsConfiguration.INSTANCE.trinityCrafting().graphRebuildBudgetMs());
            this.dataEnergistics$trinityCraftingGraphRebuilder.advance(budgetNanos);
        } catch (RuntimeException exception) {
            long revision = ((TrinityCraftingProviderRevision) this.craftingProviders)
                    .data_energistics$trinityCraftingProviderRevision();
            if (revision != this.dataEnergistics$lastLoggedGraphFailureRevision) {
                this.dataEnergistics$lastLoggedGraphFailureRevision = revision;
                Data_Energistics.LOGGER.error(
                        "Failed to rebuild the immutable Trinity crafting graph at provider revision {}",
                        revision,
                        exception);
            }
        }
    }

    @Unique
    private void dataEnergistics$clearEmptyGridPlanningState() {
        if (this.dataEnergistics$planningGridCleared) {
            return;
        }
        long gridScope = data_energistics$craftingProviderPublicationIndex().publicationScope();
        try {
            TrinityDispatchProposalLifecycle.scheduler().clearGrid(gridScope);
            TrinityPlanningGatewayLifecycle.gateway().clearGrid(gridScope);
            this.dataEnergistics$trinityCraftingGraphRebuilder = null;
            this.dataEnergistics$planningGridCleared = true;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to clear Trinity planning state for unloaded Grid publication scope {}",
                    gridScope,
                    exception);
        }
    }

    @Inject(
            method = "onServerEndTick",
            at = @At(
                     value = "FIELD",
                     target = "Lappeng/me/service/CraftingService;interests:Lcom/google/common/collect/Multimap;",
                     opcode = Opcodes.GETFIELD,
                     ordinal = 0))
    private void dataEnergistics$collectTrinityDataCoreCpuWaitingKeys(CallbackInfo ci) {
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            runtime.getAllWaitingFor(this.currentlyCrafting);
        }
    }

    @WrapMethod(method = "insertIntoCpus")
    private long dataEnergistics$insertIntoTrinityDataCoreCpus(AEKey what,
                                                               long amount,
                                                               Actionable type,
                                                               Operation<Long> original) {
        long inserted = original.call(what, amount, type);
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            inserted = runtime.insertIntoCpus(what, amount, type, inserted);
        }
        return inserted;
    }

    @WrapMethod(method = "submitJob")
    private ICraftingSubmitResult dataEnergistics$submitTrinityDataCoreCpuJob(
                                                                              ICraftingPlan job,
                                                                              ICraftingRequester requestingMachine,
                                                                              ICraftingCPU target,
                                                                              boolean prioritizePower,
                                                                              IActionSource src,
                                                                              Operation<ICraftingSubmitResult> original) {
        if (target != null) {
            TrinityPlanAdmission.CpuFamily targetFamily = target instanceof TrinityDataCoreVirtualCpu ?
                    TrinityPlanAdmission.CpuFamily.TRINITY :
                    TrinityPlanAdmission.CpuFamily.NON_TRINITY;
            if (!DATA_ENERGISTICS_PLAN_ADMISSION.isCompatibleWith(job, targetFamily)) {
                return dataEnergistics$incompatiblePlanCpu();
            }
        }

        if (target instanceof TrinityDataCoreVirtualCpu trinityDataCoreCpu) {
            if (DATA_ENERGISTICS_PLAN_ADMISSION.decide(job, TrinityPlanAdmission.Route.EXPLICIT_TARGET) !=
                    TrinityPlanAdmission.Decision.SUBMIT_TO_TRINITY) {
                return dataEnergistics$incompatiblePlanCpu();
            }
            if (!job.simulation()) {
                return dataEnergistics$hasTrinityDataCoreCpu(trinityDataCoreCpu) ?
                        trinityDataCoreCpu.submitJob(this.grid, job, src, requestingMachine) :
                        CraftingSubmitResult.CPU_OFFLINE;
            }
        }

        boolean trinityOnly = !DATA_ENERGISTICS_PLAN_ADMISSION.isCompatibleWith(
                job,
                TrinityPlanAdmission.CpuFamily.NON_TRINITY);
        if (target == null &&
                !job.simulation() &&
                (trinityOnly || !dataEnergistics$hasExternalCraftingCpu()) &&
                DATA_ENERGISTICS_PLAN_ADMISSION.decide(job, TrinityPlanAdmission.Route.AUTOMATIC_SELECTION) ==
                        TrinityPlanAdmission.Decision.SUBMIT_TO_TRINITY) {
            ICraftingSubmitResult attemptedKnownCpuResult = dataEnergistics$submitToKnownCpuCandidates(
                    job,
                    prioritizePower,
                    src,
                    requestingMachine,
                    !trinityOnly,
                    !trinityOnly,
                    original);
            if (attemptedKnownCpuResult != null) {
                return attemptedKnownCpuResult;
            }
            if (trinityOnly) {
                return dataEnergistics$incompatiblePlanCpu();
            }
        }

        ICraftingSubmitResult originalResult = original.call(
                job,
                requestingMachine,
                target,
                prioritizePower,
                src);
        if (target != null || originalResult.successful()) {
            return originalResult;
        }
        CraftingSubmitErrorCode errorCode = originalResult.errorCode();
        if (errorCode != CraftingSubmitErrorCode.NO_CPU_FOUND &&
                errorCode != CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND) {
            return originalResult;
        }
        if (DATA_ENERGISTICS_PLAN_ADMISSION.decide(job, TrinityPlanAdmission.Route.FALLBACK) !=
                TrinityPlanAdmission.Decision.SUBMIT_TO_TRINITY) {
            return originalResult;
        }
        ICraftingSubmitResult fallbackResult = dataEnergistics$submitToKnownCpuCandidates(
                job,
                prioritizePower,
                src,
                requestingMachine,
                false,
                false,
                original);
        if (fallbackResult == null ||
                fallbackResult.errorCode() == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND ||
                DATA_ENERGISTICS_CPU_SELECTOR.isRetryable(fallbackResult)) {
            return originalResult;
        }
        return fallbackResult;
    }

    @Unique
    private static ICraftingSubmitResult dataEnergistics$incompatiblePlanCpu() {
        return CraftingSubmitResult.noSuitableCpu(new UnsuitableCpus(0, 0, 0, 1));
    }

    @Inject(
            method = "getCpus",
            at = @At(
                     value = "INVOKE",
                     target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void dataEnergistics$getTrinityDataCoreCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir,
                                                        ImmutableSet.Builder<ICraftingCPU> cpus) {
        dataEnergistics$addActiveTrinityDataCoreCpus(cpus);
    }

    @WrapMethod(method = "getRequestedAmount")
    private long dataEnergistics$getTrinityDataCoreRequestedAmount(AEKey what, Operation<Long> original) {
        long requested = original.call(what);
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            requested = LongAmountMath.saturatingAddNonNegative(requested, runtime.getRequestedAmount(what));
        }
        return requested;
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$hasTrinityDataCoreCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (dataEnergistics$hasTrinityDataCoreCpu(cpu)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private List<TrinityDataCoreCraftingRuntime> dataEnergistics$trinityDataCoreRuntimes() {
        return this.dataEnergistics$trinityCraftingRuntimeRegistry.snapshot();
    }

    @Unique
    private boolean dataEnergistics$hasTrinityDataCoreCpu(ICraftingCPU cpu) {
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            if (runtime.hasCpu(cpu)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects immutable CPU facts, orders them without retaining grid objects in the selector and submits through the
     * server-thread handles. At most the first native attempt delegates through the wrapped AE2 method; later native
     * race fallbacks call the already selected cluster directly so third-party wrappers are never invoked repeatedly.
     * Missing ingredients are terminal because retrying would repeat initial extraction.
     */
    @Nullable
    @Unique
    private ICraftingSubmitResult dataEnergistics$submitToKnownCpuCandidates(
                                                                             ICraftingPlan job,
                                                                             boolean prioritizePower,
                                                                             IActionSource source,
                                                                             @Nullable ICraftingRequester requester,
                                                                             boolean includeNativeCpus,
                                                                             boolean delegateFirstNativeAttempt,
                                                                             Operation<ICraftingSubmitResult> original) {
        Map<String, ICraftingCPU> submissionHandles = new HashMap<>();
        CraftingCpuCandidateSelection selection = dataEnergistics$findSuitableCpuCandidates(
                job,
                prioritizePower,
                source,
                includeNativeCpus,
                submissionHandles);
        List<CraftingCpuCandidate> candidates = selection.candidates();
        if (candidates.isEmpty()) {
            if (delegateFirstNativeAttempt) {
                ICraftingSubmitResult originalResult = original.call(
                        job,
                        requester,
                        null,
                        prioritizePower,
                        source);
                return dataEnergistics$mergeKnownCpuDiagnostics(originalResult, selection);
            }
            return null;
        }

        UnsuitableCpus unsuitableCpus = selection.unsuitableCpus();
        boolean nativeAttemptDelegated = false;
        for (CraftingCpuCandidate candidate : candidates) {
            ICraftingCPU submissionHandle = submissionHandles.get(candidate.stableIdentity());
            if (submissionHandle == null) {
                Data_Energistics.LOGGER.error(
                        "Crafting CPU selection lost the server-thread handle for {}",
                        candidate.stableIdentity());
                throw new IllegalStateException("Selected crafting CPU has no submission handle");
            }
            ICraftingSubmitResult result = dataEnergistics$submitKnownCpuCandidate(
                    candidate,
                    submissionHandle,
                    job,
                    prioritizePower,
                    source,
                    requester,
                    delegateFirstNativeAttempt && !nativeAttemptDelegated,
                    original);
            if (candidate.kind() == CraftingCpuKind.NATIVE && delegateFirstNativeAttempt && !nativeAttemptDelegated) {
                nativeAttemptDelegated = true;
            }
            if (result.successful()) {
                dataEnergistics$advanceCpuSubmitStart(candidate, candidates, source.player().isPresent());
                return result;
            }
            if (result.errorCode() == CraftingSubmitErrorCode.CPU_OFFLINE) {
                this.updateList = true;
            }
            if (!DATA_ENERGISTICS_CPU_SELECTOR.isRetryable(result)) {
                return result;
            }
            unsuitableCpus = dataEnergistics$addRetryableFailure(unsuitableCpus, result.errorCode());
        }
        return CraftingSubmitResult.noSuitableCpu(unsuitableCpus);
    }

    @Unique
    private CraftingCpuCandidateSelection dataEnergistics$findSuitableCpuCandidates(
                                                                                    ICraftingPlan job,
                                                                                    boolean prioritizePower,
                                                                                    IActionSource source,
                                                                                    boolean includeNativeCpus,
                                                                                    Map<String, ICraftingCPU> submissionHandles) {
        ArrayList<CraftingCpuCandidate> candidateFacts = new ArrayList<>();
        if (includeNativeCpus) {
            dataEnergistics$collectNativeCpuCandidates(candidateFacts, submissionHandles);
        }
        dataEnergistics$collectTrinityCpuCandidates(candidateFacts, submissionHandles);
        return DATA_ENERGISTICS_CPU_SELECTOR.evaluate(
                candidateFacts,
                new CraftingCpuSelectionRequest(
                        job.bytes(),
                        source.player().isPresent(),
                        prioritizePower,
                        this.dataEnergistics$nextCpuSubmitByGroup));
    }

    @Unique
    private static ICraftingSubmitResult dataEnergistics$mergeKnownCpuDiagnostics(
                                                                                  ICraftingSubmitResult originalResult,
                                                                                  CraftingCpuCandidateSelection selection) {
        if (originalResult.successful()) {
            return originalResult;
        }
        CraftingSubmitErrorCode errorCode = originalResult.errorCode();
        if ((errorCode == CraftingSubmitErrorCode.NO_CPU_FOUND ||
                errorCode == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND) &&
                selection.hasUnsuitableCpus()) {
            return CraftingSubmitResult.noSuitableCpu(selection.unsuitableCpus());
        }
        return originalResult;
    }

    @Unique
    private static UnsuitableCpus dataEnergistics$addRetryableFailure(
                                                                      UnsuitableCpus unsuitableCpus,
                                                                      CraftingSubmitErrorCode errorCode) {
        return switch (errorCode) {
            case CPU_OFFLINE -> new UnsuitableCpus(
                    Math.addExact(unsuitableCpus.offline(), 1),
                    unsuitableCpus.busy(),
                    unsuitableCpus.tooSmall(),
                    unsuitableCpus.excluded());
            case CPU_BUSY -> new UnsuitableCpus(
                    unsuitableCpus.offline(),
                    Math.addExact(unsuitableCpus.busy(), 1),
                    unsuitableCpus.tooSmall(),
                    unsuitableCpus.excluded());
            case CPU_TOO_SMALL -> new UnsuitableCpus(
                    unsuitableCpus.offline(),
                    unsuitableCpus.busy(),
                    Math.addExact(unsuitableCpus.tooSmall(), 1),
                    unsuitableCpus.excluded());
            default -> throw new IllegalArgumentException("Crafting CPU failure is not retryable: " + errorCode);
        };
    }

    @Unique
    private void dataEnergistics$collectNativeCpuCandidates(
                                                            List<CraftingCpuCandidate> candidateFacts,
                                                            Map<String, ICraftingCPU> submissionHandles) {
        for (CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (cpu.isDestroyed()) {
                continue;
            }
            IGridNode node = cpu.getNode();
            if (node == null) {
                continue;
            }
            boolean online = node.isActive();
            boolean busy = cpu.isBusy();
            CraftingCpuCandidate candidate = CraftingCpuCandidate.builder()
                    .stableIdentity(dataEnergistics$nativeCpuStableIdentity(cpu, node))
                    .kind(CraftingCpuKind.NATIVE)
                    .selectionMode(cpu.getSelectionMode())
                    .online(online)
                    .acceptsJob(!busy)
                    .shared(false)
                    .storageBytes(cpu.getAvailableStorage())
                    .coProcessors(cpu.getCoProcessors())
                    .activeJobs(busy ? 1 : 0)
                    .recentOperationLoad(0L)
                    .build();
            dataEnergistics$registerCpuCandidate(candidateFacts, submissionHandles, candidate, cpu);
        }
    }

    @Unique
    private void dataEnergistics$collectTrinityCpuCandidates(
                                                             List<CraftingCpuCandidate> candidateFacts,
                                                             Map<String, ICraftingCPU> submissionHandles) {
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            List<TrinityDataCoreVirtualCpu> publishedCpus = runtime.publishedCpus();
            if (publishedCpus.isEmpty()) {
                continue;
            }
            TrinityDataCoreVirtualCpu coordinator = publishedCpus.getFirst();
            if (coordinator.number() != 0) {
                Data_Energistics.LOGGER.error(
                        "Trinity runtime published CPU {} before its coordinator",
                        coordinator.number());
                throw new IllegalStateException("Trinity runtime CPU publication order is invalid");
            }
            CraftingCpuCandidate candidate = CraftingCpuCandidate.builder()
                    .stableIdentity(coordinator.getStableDispatchIdentity())
                    .kind(CraftingCpuKind.TRINITY)
                    .selectionMode(coordinator.getSelectionMode())
                    .online(coordinator.isActive())
                    .acceptsJob(coordinator.canAcceptJob())
                    .shared(false)
                    .storageBytes(coordinator.getAvailableStorage())
                    .coProcessors(coordinator.getCoProcessors())
                    .activeJobs(coordinator.getOccupiedWorkerCount())
                    .recentOperationLoad(coordinator.getRecentOperationLoad())
                    .build();
            dataEnergistics$registerCpuCandidate(
                    candidateFacts,
                    submissionHandles,
                    candidate,
                    coordinator);
        }
    }

    @Unique
    private static void dataEnergistics$registerCpuCandidate(
                                                             List<CraftingCpuCandidate> candidateFacts,
                                                             Map<String, ICraftingCPU> submissionHandles,
                                                             CraftingCpuCandidate candidate,
                                                             ICraftingCPU submissionHandle) {
        ICraftingCPU previous = submissionHandles.putIfAbsent(candidate.stableIdentity(), submissionHandle);
        if (previous != null) {
            Data_Energistics.LOGGER.error(
                    "Duplicate crafting CPU stable identity {} was collected for {} and {}",
                    candidate.stableIdentity(),
                    previous,
                    submissionHandle);
            throw new IllegalStateException("Duplicate crafting CPU stable identity");
        }
        candidateFacts.add(candidate);
    }

    @Unique
    private static String dataEnergistics$nativeCpuStableIdentity(CraftingCPUCluster cpu, IGridNode node) {
        String dimension = node.getLevel().dimension().location().toString();
        return "ae2:" + dimension + ':' + cpu.getBoundsMin().asLong() + ':' + cpu.getBoundsMax().asLong();
    }

    @Unique
    private ICraftingSubmitResult dataEnergistics$submitKnownCpuCandidate(
                                                                          CraftingCpuCandidate candidate,
                                                                          ICraftingCPU submissionHandle,
                                                                          ICraftingPlan job,
                                                                          boolean prioritizePower,
                                                                          IActionSource source,
                                                                          @Nullable ICraftingRequester requester,
                                                                          boolean delegateNativeAttempt,
                                                                          Operation<ICraftingSubmitResult> original) {
        if (candidate.kind() == CraftingCpuKind.TRINITY) {
            if (!DATA_ENERGISTICS_PLAN_ADMISSION.isCompatibleWith(
                    job,
                    TrinityPlanAdmission.CpuFamily.TRINITY)) {
                return dataEnergistics$incompatiblePlanCpu();
            }
            if (!(submissionHandle instanceof TrinityDataCoreVirtualCpu trinityCpu)) {
                Data_Energistics.LOGGER.error(
                        "Trinity candidate {} resolved to incompatible handle {}",
                        candidate.stableIdentity(),
                        submissionHandle);
                throw new IllegalStateException("Trinity crafting CPU candidate has an incompatible handle");
            }
            return trinityCpu.submitJob(this.grid, job, source, requester);
        }
        if (candidate.kind() == CraftingCpuKind.NATIVE) {
            if (!DATA_ENERGISTICS_PLAN_ADMISSION.isCompatibleWith(
                    job,
                    TrinityPlanAdmission.CpuFamily.NON_TRINITY)) {
                return dataEnergistics$incompatiblePlanCpu();
            }
            if (!(submissionHandle instanceof CraftingCPUCluster nativeCpu)) {
                Data_Energistics.LOGGER.error(
                        "Native candidate {} resolved to incompatible handle {}",
                        candidate.stableIdentity(),
                        submissionHandle);
                throw new IllegalStateException("Native crafting CPU candidate has an incompatible handle");
            }
            if (delegateNativeAttempt) {
                return original.call(job, requester, nativeCpu, prioritizePower, source);
            }
            return nativeCpu.submitJob(this.grid, job, source, requester);
        }
        if (candidate.kind() == CraftingCpuKind.SUPPORTED_EXTERNAL) {
            Data_Energistics.LOGGER.error(
                    "External candidate {} has no compile-time submission adapter for handle {}",
                    candidate.stableIdentity(),
                    submissionHandle);
            throw new IllegalStateException("External crafting CPU candidate has no submission adapter");
        }
        Data_Energistics.LOGGER.error(
                "Crafting CPU candidate {} has unsupported kind {}",
                candidate.stableIdentity(),
                candidate.kind());
        throw new IllegalStateException("Crafting CPU candidate has an unsupported kind");
    }

    /**
     * Moves only the successful hardware group's round-robin cursor to its next stable identity.
     */
    @Unique
    private void dataEnergistics$advanceCpuSubmitStart(
                                                       CraftingCpuCandidate successfulCandidate,
                                                       List<CraftingCpuCandidate> selectedCandidates,
                                                       boolean playerRequest) {
        CraftingCpuSelectionGroup successfulGroup = DATA_ENERGISTICS_CPU_SELECTOR.group(successfulCandidate, playerRequest);
        ArrayList<String> groupIdentities = new ArrayList<>();
        for (CraftingCpuCandidate candidate : selectedCandidates) {
            if (successfulGroup.equals(DATA_ENERGISTICS_CPU_SELECTOR.group(candidate, playerRequest))) {
                groupIdentities.add(candidate.stableIdentity());
            }
        }
        groupIdentities.sort(String::compareTo);
        int successfulIndex = groupIdentities.indexOf(successfulCandidate.stableIdentity());
        if (successfulIndex < 0) {
            Data_Energistics.LOGGER.warn(
                    "Successful crafting CPU {} was absent while advancing its auto-selection cursor",
                    successfulCandidate.stableIdentity());
            return;
        }
        String nextIdentity = groupIdentities.get((successfulIndex + 1) % groupIdentities.size());
        this.dataEnergistics$nextCpuSubmitByGroup.put(successfulGroup, nextIdentity);
    }

    @Unique
    private void dataEnergistics$addActiveTrinityDataCoreCpus(ImmutableSet.Builder<ICraftingCPU> cpus) {
        dataEnergistics$forEachTrinityDataCoreCpu(cpu -> {
            if (cpu.isActive()) {
                cpus.add(cpu);
            }
        });
    }

    @Unique
    private boolean dataEnergistics$hasExternalCraftingCpu() {
        CraftingService service = (CraftingService) (Object) this;
        for (ICraftingCPU cpu : service.getCpus()) {
            if (!(cpu instanceof CraftingCPUCluster) && !(cpu instanceof TrinityDataCoreVirtualCpu)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void dataEnergistics$forEachTrinityDataCoreCpu(Consumer<TrinityDataCoreVirtualCpu> consumer) {
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            for (TrinityDataCoreVirtualCpu cpu : runtime.publishedCpus()) {
                consumer.accept(cpu);
            }
        }
    }
}
