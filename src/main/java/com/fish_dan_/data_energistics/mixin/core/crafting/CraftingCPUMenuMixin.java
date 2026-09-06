package com.fish_dan_.data_energistics.mixin.core.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCraftingStatusEntry;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftingStatusPayload;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.core.network.clientbound.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
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

    @Unique
    private long dataEnergistics$statusSequence;

    public CraftingCPUMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "setCPU", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$setCPU(@Nullable ICraftingCPU c, CallbackInfo ci) {
        if (this.dataEnergistics$cpu != null) {
            this.dataEnergistics$cpu.removeListener(this.cpuChangeListener);
            this.dataEnergistics$cpu = null;
            if (c == null) {
                // AE2 sees its native CPU field already null and otherwise skips clearing the previous Trinity table.
                this.incrementalUpdateHelper.reset();
                sendPacketToClient(new CraftingStatusPacket(this.containerId, CraftingStatus.EMPTY));
            }
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

        for (AEKey key : trinityDataCoreCpu.getStatusKeys()) {
            this.incrementalUpdateHelper.addChange(key);
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
            var header = new TrinityCraftingStatusPayload.Header(this.incrementalUpdateHelper.isFullUpdate(),
                    this.dataEnergistics$cpu.getElapsedTimeNanos(), this.dataEnergistics$cpu.getRemainingItemCount(),
                    this.dataEnergistics$cpu.getStartItemCount(), suspended);
            List<TrinityCraftingStatusEntry> entries = dataEnergistics$createStatusEntries(
                    this.incrementalUpdateHelper,
                    this.dataEnergistics$cpu);
            this.dataEnergistics$statusSequence = Math.incrementExact(this.dataEnergistics$statusSequence);
            for (var payload : TrinityCraftingStatusPayload.batches(this.containerId, this.dataEnergistics$statusSequence, header, entries)) {
                PacketDistributor.sendToPlayer((ServerPlayer) getPlayer(), payload);
            }
            this.incrementalUpdateHelper.commitChanges();
            this.dataEnergistics$cachedSuspended = suspended;
        }
    }

    @Unique
    private static List<TrinityCraftingStatusEntry> dataEnergistics$createStatusEntries(IncrementalUpdateHelper changes,
                                                                                        TrinityDataCoreVirtualCpu cpu) {
        boolean full = changes.isFullUpdate();
        List<TrinityCraftingStatusEntry> entries = new ObjectArrayList<>();
        for (AEKey what : changes) {
            AEKey sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }

            TrinityCraftingStatusEntry entry = new TrinityCraftingStatusEntry(
                    changes.getOrAssignSerial(what),
                    sentStack,
                    cpu.getStored(what),
                    cpu.getWaitingFor(what),
                    cpu.getPendingOutputs(what));
            entries.add(entry);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        return entries;
    }
}
