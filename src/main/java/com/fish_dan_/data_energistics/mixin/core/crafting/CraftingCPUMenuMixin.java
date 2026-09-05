package com.fish_dan_.data_energistics.mixin.core.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.network.clientbound.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin extends AEBaseMenu {

    @Shadow
    @Final
    private IncrementalUpdateHelper incrementalUpdateHelper;

    @Shadow
    @Final
    private Consumer<AEKey> cpuChangeListener;

    @Shadow
    @Nullable
    private CraftingCPUCluster cpu;

    @Shadow
    public CpuSelectionMode schedulingMode;

    @Shadow
    public boolean cantStoreItems;

    @Unique
    @Nullable
    private TrinityDataCoreVirtualCpu dataEnergistics$cpu;

    @Unique
    private boolean dataEnergistics$cachedSuspended;

    public CraftingCPUMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "setCPU", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$setCPU(ICraftingCPU c, CallbackInfo ci) {
        if (this.dataEnergistics$cpu != null) {
            this.dataEnergistics$cpu.removeListener(this.cpuChangeListener);
            this.dataEnergistics$cpu = null;
        }
        this.dataEnergistics$cachedSuspended = false;
        if (!(c instanceof TrinityDataCoreVirtualCpu trinityDataCoreCpu)) {
            return;
        }

        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(this.cpuChangeListener);
            this.cpu = null;
        }
        this.incrementalUpdateHelper.reset();
        this.dataEnergistics$cpu = trinityDataCoreCpu;

        KeyCounter allItems = new KeyCounter();
        trinityDataCoreCpu.getAllItems(allItems);
        for (var entry : allItems) {
            this.incrementalUpdateHelper.addChange(entry.getKey());
        }
        trinityDataCoreCpu.addListener(this.cpuChangeListener);
        ci.cancel();
    }

    @Inject(method = "cancelCrafting", at = @At("TAIL"))
    private void dataEnergistics$cancelCrafting(CallbackInfo ci) {
        if (isServerSide() && this.dataEnergistics$cpu != null) {
            this.dataEnergistics$cpu.cancelJob();
        }
    }

    @Inject(method = "toggleScheduling", at = @At("TAIL"))
    private void dataEnergistics$toggleScheduling(CallbackInfo ci) {
        if (isServerSide() && this.dataEnergistics$cpu != null) {
            this.dataEnergistics$cpu.setJobSuspended(!this.dataEnergistics$cpu.isJobSuspended());
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void dataEnergistics$removed(Player player, CallbackInfo ci) {
        if (this.dataEnergistics$cpu != null) {
            this.dataEnergistics$cpu.removeListener(this.cpuChangeListener);
            this.dataEnergistics$cpu = null;
        }
        this.dataEnergistics$cachedSuspended = false;
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$broadcastChanges(CallbackInfo ci) {
        if (!isServerSide() || this.dataEnergistics$cpu == null) {
            return;
        }

        this.schedulingMode = this.dataEnergistics$cpu.getSelectionMode();
        this.cantStoreItems = this.dataEnergistics$cpu.isCantStoreItems();
        boolean suspended = this.dataEnergistics$cpu.isJobSuspended();
        if (this.incrementalUpdateHelper.hasChanges() || this.dataEnergistics$cachedSuspended != suspended) {
            CraftingStatus status = dataEnergistics$createStatus(
                    this.incrementalUpdateHelper,
                    this.dataEnergistics$cpu,
                    suspended);
            this.incrementalUpdateHelper.commitChanges();
            this.dataEnergistics$cachedSuspended = suspended;
            sendPacketToClient(new CraftingStatusPacket(this.containerId, status));
        }
    }

    @Unique
    private static CraftingStatus dataEnergistics$createStatus(IncrementalUpdateHelper changes,
                                                               TrinityDataCoreVirtualCpu cpu,
                                                               boolean suspended) {
        boolean full = changes.isFullUpdate();
        ImmutableList.Builder<CraftingStatusEntry> entries = ImmutableList.builder();
        for (AEKey what : changes) {
            long storedAmount = cpu.getStored(what);
            long activeAmount = cpu.getWaitingFor(what);
            long pendingAmount = cpu.getPendingOutputs(what);
            AEKey sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }

            CraftingStatusEntry entry = new CraftingStatusEntry(
                    changes.getOrAssignSerial(what),
                    sentStack,
                    storedAmount,
                    activeAmount,
                    pendingAmount);
            entries.add(entry);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        return new CraftingStatus(
                full,
                cpu.getElapsedTimeNanos(),
                cpu.getRemainingItemCount(),
                cpu.getStartItemCount(),
                entries.build(), suspended);
    }
}
