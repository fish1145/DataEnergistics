package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.util.LongAmountMath;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
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
        if (gridNode.getOwner() instanceof TrinityAccessHatchBlockEntity) {
            withdraw(gridNode);
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
        long latestChange = 0L;
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            runtime.tick(this.energyGrid, service);
            latestChange = Math.max(latestChange, runtime.getLastModifiedOnTick());
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

    @Inject(method = "insertIntoCpus", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$insertIntoTrinityDataCoreCpus(AEKey what,
                                                               long amount,
                                                               Actionable type,
                                                               CallbackInfoReturnable<Long> cir) {
        long inserted = cir.getReturnValue();
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            inserted = runtime.insertIntoCpus(what, amount, type, inserted);
        }
        cir.setReturnValue(inserted);
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$submitTrinityDataCoreCpuJob(ICraftingPlan job,
                                                             ICraftingRequester requestingMachine,
                                                             ICraftingCPU target,
                                                             boolean prioritizePower,
                                                             IActionSource src,
                                                             CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (job.simulation()) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
        if (target instanceof TrinityDataCoreVirtualCpu trinityDataCoreCpu) {
            if (dataEnergistics$hasTrinityDataCoreCpu(trinityDataCoreCpu)) {
                cir.setReturnValue(trinityDataCoreCpu.submitJob(this.grid, job, src, requestingMachine));
            } else {
                cir.setReturnValue(CraftingSubmitResult.CPU_OFFLINE);
            }
            return;
        }
        if (target != null) {
            return;
        }

        DataEnergisticsCpuSelection selection = dataEnergistics$findSuitableCpu(job, prioritizePower, src);
        ICraftingCPU selectedCpu = selection.cpu();
        if (selectedCpu instanceof TrinityDataCoreVirtualCpu trinityDataCoreCpu) {
            cir.setReturnValue(trinityDataCoreCpu.submitJob(this.grid, job, src, requestingMachine));
            return;
        }
        if (selectedCpu instanceof CraftingCPUCluster cpuCluster) {
            cir.setReturnValue(cpuCluster.submitJob(this.grid, job, src, requestingMachine));
            return;
        }
        if (selection.hasUnsuitableCpus()) {
            cir.setReturnValue(CraftingSubmitResult.noSuitableCpu(selection.unsuitableCpus()));
        } else {
            cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
        }
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

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$getTrinityDataCoreRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        long requested = cir.getReturnValue();
        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            requested = LongAmountMath.saturatingAddNonNegative(requested, runtime.getRequestedAmount(what));
        }
        cir.setReturnValue(requested);
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
    private DataEnergisticsCpuSelection dataEnergistics$findSuitableCpu(ICraftingPlan job,
                                                                        boolean prioritizePower,
                                                                        IActionSource source) {
        ArrayList<ICraftingCPU> validCpus = new ArrayList<>();
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;

        for (CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (!cpu.isActive()) {
                offline++;
                continue;
            }
            if (cpu.isBusy()) {
                busy++;
                continue;
            }
            if (cpu.getAvailableStorage() < job.bytes()) {
                tooSmall++;
                continue;
            }
            if (!dataEnergistics$canBeAutoSelectedFor(cpu, source)) {
                excluded++;
                continue;
            }
            validCpus.add(cpu);
        }

        DataEnergisticsCpuCandidateCounts trinityDataCoreCounts = dataEnergistics$collectTrinityDataCoreCpuCandidates(job, source, validCpus);
        offline += trinityDataCoreCounts.offline();
        busy += trinityDataCoreCounts.busy();
        tooSmall += trinityDataCoreCounts.tooSmall();
        excluded += trinityDataCoreCounts.excluded();

        if (validCpus.isEmpty()) {
            return new DataEnergisticsCpuSelection(null, offline, busy, tooSmall, excluded);
        }
        validCpus.sort((first, second) -> {
            boolean firstPreferred = dataEnergistics$isPreferredFor(first, source);
            boolean secondPreferred = dataEnergistics$isPreferredFor(second, source);
            if (firstPreferred != secondPreferred) {
                return Boolean.compare(secondPreferred, firstPreferred);
            }
            Comparator<ICraftingCPU> comparator = prioritizePower ? DATA_ENERGISTICS_FAST_FIRST_COMPARATOR : DATA_ENERGISTICS_FAST_LAST_COMPARATOR;
            return comparator.compare(first, second);
        });
        return new DataEnergisticsCpuSelection(validCpus.getFirst(), offline, busy, tooSmall, excluded);
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
    private DataEnergisticsCpuCandidateCounts dataEnergistics$collectTrinityDataCoreCpuCandidates(
                                                                                                  ICraftingPlan job, IActionSource source, ArrayList<ICraftingCPU> validCpus) {
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;

        for (TrinityDataCoreCraftingRuntime runtime : dataEnergistics$trinityDataCoreRuntimes()) {
            for (TrinityDataCoreVirtualCpu cpu : runtime.publishedCpus()) {
                if (!cpu.isActive()) {
                    offline++;
                    continue;
                }
                if (cpu.isBusy()) {
                    busy++;
                    continue;
                }
                if (cpu.getAvailableStorage() < job.bytes()) {
                    tooSmall++;
                    continue;
                }
                if (!dataEnergistics$canBeAutoSelectedFor(cpu, source)) {
                    excluded++;
                    continue;
                }
                validCpus.add(cpu);
            }
        }
        return new DataEnergisticsCpuCandidateCounts(offline, busy, tooSmall, excluded);
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

    @Unique
    private record DataEnergisticsCpuSelection(ICraftingCPU cpu, int offline, int busy, int tooSmall, int excluded) {

        private boolean hasUnsuitableCpus() {
            return this.offline > 0 || this.busy > 0 || this.tooSmall > 0 || this.excluded > 0;
        }

        private UnsuitableCpus unsuitableCpus() {
            return new UnsuitableCpus(this.offline, this.busy, this.tooSmall, this.excluded);
        }
    }

    @Unique
    private record DataEnergisticsCpuCandidateCounts(int offline, int busy, int tooSmall, int excluded) {}
}
