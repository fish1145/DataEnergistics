package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputProjection;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies persisted virtual results to an AE2 crafting CPU only after its provider accepted the physical dispatch.
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicVirtualCompletionMixin {

    @Unique
    private static final String DATA_ENERGISTICS_VIRTUAL_COMPLETIONS_TAG = "data_energistics_virtual_completions";

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Unique
    private final ListCraftingInventory dataEnergistics$pendingVirtualCompletions = new ListCraftingInventory(ignored -> {});

    @Unique
    private boolean dataEnergistics$virtualAccountingFailed;

    @WrapOperation(
                   method = "executeCrafting",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean dataEnergistics$captureAcceptedVirtualOutputs(ICraftingProvider provider,
                                                                  IPatternDetails details,
                                                                  KeyCounter[] inputs,
                                                                  Operation<Boolean> original) {
        boolean accepted = original.call(provider, details, inputs);
        if (!accepted) {
            return false;
        }
        try {
            VirtualCraftingOutputProjection projection = VirtualCraftingOutputAdapters.project(details);
            dataEnergistics$enqueue(projection.virtualOutputs(1L));
        } catch (RuntimeException exception) {
            this.dataEnergistics$virtualAccountingFailed = true;
            Data_Energistics.LOGGER.error(
                    "AE2 crafting CPU accepted pattern {} but could not prepare its virtual completion",
                    details.getDefinition(),
                    exception);
        }
        return true;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void dataEnergistics$applyRestoredVirtualCompletions(int maxPatterns,
                                                                 CraftingService craftingService,
                                                                 IEnergyService energyService,
                                                                 Level level,
                                                                 CallbackInfoReturnable<Integer> cir) {
        dataEnergistics$drainVirtualCompletions();
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void dataEnergistics$applyAcceptedVirtualCompletions(int maxPatterns,
                                                                 CraftingService craftingService,
                                                                 IEnergyService energyService,
                                                                 Level level,
                                                                 CallbackInfoReturnable<Integer> cir) {
        if (this.dataEnergistics$virtualAccountingFailed) {
            this.dataEnergistics$virtualAccountingFailed = false;
            ((CraftingCpuLogic) (Object) this).cancel();
            return;
        }
        dataEnergistics$drainVirtualCompletions();
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void dataEnergistics$recoverOrphanedVirtualCompletions(
                                                                   IEnergyService eg,
                                                                   CraftingService cc,
                                                                   CallbackInfo ci) {
        if (!((CraftingCpuLogic) (Object) this).hasJob()) {
            dataEnergistics$recoverVirtualCompletions();
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void dataEnergistics$recoverVirtualCompletionsOnCancel(CallbackInfo ci) {
        dataEnergistics$recoverVirtualCompletions();
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void dataEnergistics$recoverVirtualCompletionsOnFinish(boolean success, CallbackInfo ci) {
        dataEnergistics$recoverVirtualCompletions();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void dataEnergistics$writeVirtualCompletions(CompoundTag data,
                                                         HolderLookup.Provider registries,
                                                         CallbackInfo ci) {
        if (this.dataEnergistics$pendingVirtualCompletions.list.isEmpty()) {
            data.remove(DATA_ENERGISTICS_VIRTUAL_COMPLETIONS_TAG);
            return;
        }
        data.put(
                DATA_ENERGISTICS_VIRTUAL_COMPLETIONS_TAG,
                this.dataEnergistics$pendingVirtualCompletions.writeToNBT(registries));
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void dataEnergistics$readVirtualCompletions(CompoundTag data,
                                                        HolderLookup.Provider registries,
                                                        CallbackInfo ci) {
        this.dataEnergistics$pendingVirtualCompletions.clear();
        if (!data.contains(DATA_ENERGISTICS_VIRTUAL_COMPLETIONS_TAG)) {
            return;
        }
        Tag encoded = data.get(DATA_ENERGISTICS_VIRTUAL_COMPLETIONS_TAG);
        if (!(encoded instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error("Ignoring damaged AE2 CPU virtual completion ledger");
            return;
        }
        try {
            this.dataEnergistics$pendingVirtualCompletions.readFromNBT(list, registries);
            for (var entry : this.dataEnergistics$pendingVirtualCompletions.list) {
                if (entry.getLongValue() <= 0L) {
                    throw new IllegalArgumentException("Virtual completion amount must be positive");
                }
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Ignoring damaged AE2 CPU virtual completion ledger", exception);
            this.dataEnergistics$pendingVirtualCompletions.clear();
        }
    }

    @Unique
    private void dataEnergistics$enqueue(List<GenericStack> completions) {
        for (GenericStack completion : completions) {
            Math.addExact(
                    this.dataEnergistics$pendingVirtualCompletions.list.get(completion.what()),
                    completion.amount());
            this.dataEnergistics$pendingVirtualCompletions.insert(
                    completion.what(),
                    completion.amount(),
                    Actionable.MODULATE);
        }
        if (!completions.isEmpty()) {
            this.cluster.markDirty();
        }
    }

    @Unique
    private void dataEnergistics$drainVirtualCompletions() {
        if (this.dataEnergistics$pendingVirtualCompletions.list.isEmpty()) {
            return;
        }
        CraftingCpuLogic logic = (CraftingCpuLogic) (Object) this;
        GenericStack finalOutput = logic.getFinalJobOutput();
        if (finalOutput == null) {
            dataEnergistics$recoverVirtualCompletions();
            return;
        }

        ArrayList<GenericStack> intermediate = new ArrayList<>();
        ArrayList<GenericStack> finalResults = new ArrayList<>();
        for (var entry : this.dataEnergistics$pendingVirtualCompletions.list) {
            GenericStack completion = new GenericStack(entry.getKey(), entry.getLongValue());
            (entry.getKey().matches(finalOutput) ? finalResults : intermediate).add(completion);
        }
        intermediate.addAll(finalResults);
        for (GenericStack completion : intermediate) {
            if (!logic.hasJob()) {
                break;
            }
            long removed = this.dataEnergistics$pendingVirtualCompletions.extract(
                    completion.what(),
                    completion.amount(),
                    Actionable.MODULATE);
            if (removed != completion.amount()) {
                dataEnergistics$abortInvalidCompletion(logic, completion, completion.amount(), null);
                return;
            }
            long accepted;
            try {
                accepted = logic.insert(completion.what(), completion.amount(), Actionable.MODULATE);
            } catch (RuntimeException exception) {
                dataEnergistics$abortInvalidCompletion(logic, completion, completion.amount(), exception);
                return;
            }
            if (accepted < 0L || accepted > completion.amount()) {
                dataEnergistics$abortInvalidCompletion(logic, completion, completion.amount(), null);
                return;
            }
            long remainder = completion.amount() - accepted;
            if (remainder > 0L) {
                try {
                    Math.addExact(this.inventory.list.get(completion.what()), remainder);
                    this.inventory.insert(completion.what(), remainder, Actionable.MODULATE);
                    this.cluster.markDirty();
                } catch (RuntimeException exception) {
                    dataEnergistics$abortInvalidCompletion(logic, completion, remainder, exception);
                    return;
                }
            }
        }
        this.cluster.markDirty();
    }

    @Unique
    private void dataEnergistics$abortInvalidCompletion(CraftingCpuLogic logic,
                                                        GenericStack completion,
                                                        long recoverAmount,
                                                        RuntimeException failure) {
        Data_Energistics.LOGGER.error(
                "AE2 crafting CPU could not apply the accepted virtual completion {} x{}; cancelling without materializing its placeholder",
                completion.what(),
                completion.amount(),
                failure);
        if (recoverAmount > 0L) {
            try {
                Math.addExact(
                        this.dataEnergistics$pendingVirtualCompletions.list.get(completion.what()),
                        recoverAmount);
                this.dataEnergistics$pendingVirtualCompletions.insert(
                        completion.what(),
                        recoverAmount,
                        Actionable.MODULATE);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "AE2 crafting CPU could not retain failed virtual completion {} x{} for recovery",
                        completion.what(),
                        recoverAmount,
                        exception);
            }
        }
        logic.cancel();
    }

    @Unique
    private void dataEnergistics$recoverVirtualCompletions() {
        if (this.dataEnergistics$pendingVirtualCompletions.list.isEmpty()) {
            return;
        }
        ArrayList<GenericStack> recoverable = new ArrayList<>();
        for (var entry : this.dataEnergistics$pendingVirtualCompletions.list) {
            recoverable.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        this.dataEnergistics$pendingVirtualCompletions.clear();
        for (GenericStack completion : recoverable) {
            try {
                Math.addExact(this.inventory.list.get(completion.what()), completion.amount());
                this.inventory.insert(completion.what(), completion.amount(), Actionable.MODULATE);
            } catch (RuntimeException exception) {
                this.dataEnergistics$pendingVirtualCompletions.insert(
                        completion.what(),
                        completion.amount(),
                        Actionable.MODULATE);
                Data_Energistics.LOGGER.error(
                        "AE2 crafting CPU could not recover accepted virtual completion {} x{}",
                        completion.what(),
                        completion.amount(),
                        exception);
            }
        }
        this.cluster.markDirty();
    }
}
