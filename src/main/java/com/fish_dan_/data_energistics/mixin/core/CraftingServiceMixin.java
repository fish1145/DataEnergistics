package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
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
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin implements TrinityCraftingRuntimeRegistry {

    @Unique
    private static final Comparator<ICraftingCPU> DATA_ENERGISTICS_FAST_FIRST_COMPARATOR = Comparator
            .comparingInt(ICraftingCPU::getCoProcessors)
            .reversed()
            .thenComparingLong(ICraftingCPU::getAvailableStorage);

    @Unique
    private static final Comparator<ICraftingCPU> DATA_ENERGISTICS_FAST_LAST_COMPARATOR = Comparator
            .comparingInt(ICraftingCPU::getCoProcessors)
            .thenComparingLong(ICraftingCPU::getAvailableStorage);

    @Unique
    private final TrinityCraftingRuntimeRegistry.Local dataEnergistics$trinityCraftingRuntimeRegistry = TrinityCraftingRuntimeRegistry.createLocal();

    @Unique
    private long dataEnergistics$lastProcessedTrinityDataCoreCraftingLogicChangeTick;

    /** Transient cursor rotates which published Trinity runtime receives the first dispatch opportunity. */
    @Unique
    private int dataEnergistics$nextTrinityRuntimeTickStart;

    /** Transient cursor balances successful auto-submissions across equally capable Trinity runtimes. */
    @Unique
    private int dataEnergistics$nextTrinitySubmitStart;

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
        if (!runtimes.isEmpty()) {
            int start = Math.floorMod(this.dataEnergistics$nextTrinityRuntimeTickStart, runtimes.size());
            this.dataEnergistics$nextTrinityRuntimeTickStart = (start + 1) % runtimes.size();
            for (int offset = 0; offset < runtimes.size(); offset++) {
                TrinityDataCoreCraftingRuntime runtime = runtimes.get((start + offset) % runtimes.size());
                runtime.tick(this.energyGrid, service, dispatchWindow);
                latestChange = Math.max(latestChange, runtime.getLastModifiedOnTick());
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

        ICraftingSubmitResult attemptedTrinityResult = null;
        if (target == null && !job.simulation() && !dataEnergistics$hasExternalCraftingCpu()) {
            List<ICraftingCPU> candidates = dataEnergistics$findSuitableCpus(job, prioritizePower, src, true);
            if (!candidates.isEmpty() && candidates.getFirst() instanceof TrinityDataCoreVirtualCpu) {
                attemptedTrinityResult = dataEnergistics$submitToTrinityCandidates(
                        candidates,
                        job,
                        src,
                        requestingMachine);
                if (attemptedTrinityResult != null) {
                    if (attemptedTrinityResult.successful()) {
                        return attemptedTrinityResult;
                    }
                    if (!dataEnergistics$isRetryableTrinityFailure(attemptedTrinityResult)) {
                        return attemptedTrinityResult;
                    }
                }
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
        if (attemptedTrinityResult != null) {
            return attemptedTrinityResult;
        }

        ICraftingSubmitResult fallbackResult = dataEnergistics$submitToTrinityCandidates(
                dataEnergistics$findSuitableCpus(job, prioritizePower, src, false),
                job,
                src,
                requestingMachine);
        return fallbackResult != null ? fallbackResult : originalResult;
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

    @Unique
    private List<ICraftingCPU> dataEnergistics$findSuitableCpus(ICraftingPlan job,
                                                                boolean prioritizePower,
                                                                IActionSource source,
                                                                boolean includeNativeCpus) {
        ArrayList<ICraftingCPU> validCpus = new ArrayList<>();
        if (includeNativeCpus) {
            for (CraftingCPUCluster cpu : this.craftingCPUClusters) {
                if (cpu.isActive() && !cpu.isBusy() && cpu.getAvailableStorage() >= job.bytes() &&
                        dataEnergistics$canBeAutoSelectedFor(cpu, source)) {
                    validCpus.add(cpu);
                }
            }
        }
        dataEnergistics$collectTrinityDataCoreCpuCandidates(job, source, validCpus);

        if (validCpus.isEmpty()) {
            return List.of();
        }
        validCpus.sort((first, second) -> {
            boolean firstPreferred = dataEnergistics$isPreferredFor(first, source);
            boolean secondPreferred = dataEnergistics$isPreferredFor(second, source);
            if (firstPreferred != secondPreferred) {
                return Boolean.compare(secondPreferred, firstPreferred);
            }
            Comparator<ICraftingCPU> comparator = prioritizePower ?
                    DATA_ENERGISTICS_FAST_FIRST_COMPARATOR :
                    DATA_ENERGISTICS_FAST_LAST_COMPARATOR;
            int hardwareOrder = comparator.compare(first, second);
            if (hardwareOrder != 0) {
                return hardwareOrder;
            }
            if (first instanceof TrinityDataCoreVirtualCpu firstTrinity &&
                    second instanceof TrinityDataCoreVirtualCpu secondTrinity) {
                return Integer.compare(
                        firstTrinity.getOccupiedWorkerCount(),
                        secondTrinity.getOccupiedWorkerCount());
            }
            return 0;
        });
        return List.copyOf(validCpus);
    }

    /**
     * Attempts eligible Trinity coordinators in selection order. Missing ingredients are terminal because retrying a
     * plan would repeat initial ingredient extraction; only submission-time availability failures permit failover.
     */
    @Nullable
    @Unique
    private ICraftingSubmitResult dataEnergistics$submitToTrinityCandidates(List<ICraftingCPU> candidates,
                                                                            ICraftingPlan job,
                                                                            IActionSource source,
                                                                            @Nullable ICraftingRequester requester) {
        ICraftingSubmitResult lastFailure = null;
        for (ICraftingCPU candidate : candidates) {
            if (!(candidate instanceof TrinityDataCoreVirtualCpu trinityDataCoreCpu)) {
                continue;
            }
            ICraftingSubmitResult result = trinityDataCoreCpu.submitJob(this.grid, job, source, requester);
            if (result.successful()) {
                dataEnergistics$advanceTrinitySubmitStart(trinityDataCoreCpu);
                return result;
            }
            lastFailure = result;
            if (!dataEnergistics$isRetryableTrinityFailure(result)) {
                return result;
            }
        }
        return lastFailure;
    }

    /** Returns whether a failed coordinator may safely be bypassed without repeating ingredient extraction. */
    @Unique
    private static boolean dataEnergistics$isRetryableTrinityFailure(ICraftingSubmitResult result) {
        return result.errorCode() == CraftingSubmitErrorCode.CPU_BUSY ||
                result.errorCode() == CraftingSubmitErrorCode.CPU_OFFLINE ||
                result.errorCode() == CraftingSubmitErrorCode.CPU_TOO_SMALL;
    }

    /** Moves the equal-candidate round-robin cursor after one successful Trinity allocation. */
    @Unique
    private void dataEnergistics$advanceTrinitySubmitStart(TrinityDataCoreVirtualCpu successfulCpu) {
        List<TrinityDataCoreCraftingRuntime> runtimes = dataEnergistics$trinityDataCoreRuntimes();
        for (int runtimeIndex = 0; runtimeIndex < runtimes.size(); runtimeIndex++) {
            if (runtimes.get(runtimeIndex).hasCpu(successfulCpu)) {
                this.dataEnergistics$nextTrinitySubmitStart = (runtimeIndex + 1) % runtimes.size();
                return;
            }
        }
        Data_Energistics.LOGGER.warn(
                "Successful Trinity CPU {} was no longer present while advancing the auto-selection cursor",
                successfulCpu.number());
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
    private void dataEnergistics$collectTrinityDataCoreCpuCandidates(ICraftingPlan job,
                                                                     IActionSource source,
                                                                     ArrayList<ICraftingCPU> validCpus) {
        List<TrinityDataCoreCraftingRuntime> runtimes = dataEnergistics$trinityDataCoreRuntimes();
        if (runtimes.isEmpty()) {
            return;
        }
        int start = Math.floorMod(this.dataEnergistics$nextTrinitySubmitStart, runtimes.size());
        for (int offset = 0; offset < runtimes.size(); offset++) {
            TrinityDataCoreCraftingRuntime runtime = runtimes.get((start + offset) % runtimes.size());
            for (TrinityDataCoreVirtualCpu cpu : runtime.publishedCpus()) {
                if (!cpu.isActive()) {
                    continue;
                }
                if (!cpu.canAcceptJob()) {
                    continue;
                }
                if (cpu.getAvailableStorage() < job.bytes()) {
                    continue;
                }
                if (!dataEnergistics$canBeAutoSelectedFor(cpu, source)) {
                    continue;
                }
                validCpus.add(cpu);
            }
        }
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

    @Unique
    private static boolean dataEnergistics$canBeAutoSelectedFor(ICraftingCPU cpu, IActionSource source) {
        return switch (cpu.getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    @Unique
    private static boolean dataEnergistics$isPreferredFor(ICraftingCPU cpu, IActionSource source) {
        return switch (cpu.getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }
}
