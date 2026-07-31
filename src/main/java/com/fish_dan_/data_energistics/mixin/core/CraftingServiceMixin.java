package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingCpuCandidate;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingCpuCandidateSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingCpuCandidateSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingCpuKind;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingCpuSelectionGroup;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingCpuSelectionRequest;
import com.fish_dan_.data_energistics.util.LongAmountMath;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin implements TrinityCraftingRuntimeRegistry {

    @Unique
    private static final CraftingCpuCandidateSelector DATA_ENERGISTICS_CPU_SELECTOR = CraftingCpuCandidateSelector.create();

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
    private boolean updateList;

    @Shadow
    private long lastProcessedCraftingLogicChangeTick;

    @Override
    public boolean publish(IGridNode node, TrinityDataCoreCraftingRuntime runtime) {
        if (node.getGrid() != this.grid) {
            Data_Energistics.LOGGER.error("Cannot publish a Trinity crafting runtime through a different grid service");
            throw new IllegalArgumentException("The Trinity crafting node belongs to a different grid");
        }
        return this.dataEnergistics$trinityCraftingRuntimeRegistry.publish(node, runtime);
    }

    @Override
    public boolean withdraw(IGridNode node) {
        return this.dataEnergistics$trinityCraftingRuntimeRegistry.withdraw(node);
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
        boolean withdrawn = withdraw(gridNode);
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
        CraftingService service = (CraftingService) (Object) this;
        CraftingDispatchWindow dispatchWindow = CraftingDispatchWindow.create();
        List<TrinityDataCoreCraftingRuntime> runtimes = dataEnergistics$trinityDataCoreRuntimes();
        long latestChange = 0L;
        for (TrinityDataCoreCraftingRuntime runtime : runtimes) {
            latestChange = Math.max(latestChange, runtime.getLastModifiedOnTick());
        }
        if (!runtimes.isEmpty()) {
            int start = Math.floorMod(this.dataEnergistics$nextTrinityRuntimeTickStart, runtimes.size());
            this.dataEnergistics$nextTrinityRuntimeTickStart = (start + 1) % runtimes.size();
            for (int offset = 0; offset < runtimes.size(); offset++) {
                TrinityDataCoreCraftingRuntime runtime = runtimes.get((start + offset) % runtimes.size());
                runtime.tick(this.energyGrid, service, dispatchWindow);
                latestChange = Math.max(latestChange, runtime.getLastModifiedOnTick());
                if (dispatchWindow.isExhausted()) {
                    break;
                }
            }
        }
        if (latestChange != this.dataEnergistics$lastProcessedTrinityDataCoreCraftingLogicChangeTick) {
            this.dataEnergistics$lastProcessedTrinityDataCoreCraftingLogicChangeTick = latestChange;
            this.lastProcessedCraftingLogicChangeTick = -1L;
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
        if (target instanceof TrinityDataCoreVirtualCpu trinityDataCoreCpu && !job.simulation()) {
            return dataEnergistics$hasTrinityDataCoreCpu(trinityDataCoreCpu) ?
                    trinityDataCoreCpu.submitJob(this.grid, job, src, requestingMachine) :
                    CraftingSubmitResult.CPU_OFFLINE;
        }

        if (target == null && !job.simulation() && !dataEnergistics$hasExternalCraftingCpu()) {
            ICraftingSubmitResult attemptedKnownCpuResult = dataEnergistics$submitToKnownCpuCandidates(
                    job,
                    prioritizePower,
                    src,
                    requestingMachine,
                    true,
                    true,
                    original);
            if (attemptedKnownCpuResult != null) {
                return attemptedKnownCpuResult;
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
