package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.ITerminalHost;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps Trinity CPU entries in their stable numeric order without changing AE2's private selection serials. */
@Mixin(CraftingStatusMenu.class)
public abstract class CraftingStatusMenuMixin extends CraftingCPUMenu
                                              implements TrinityCraftingStatusSelection.TargetedMenu {

    @Shadow
    @Final
    private WeakHashMap<ICraftingCPU, Integer> cpuSerialMap;

    @Shadow
    private ImmutableSet<ICraftingCPU> lastCpuSet;

    @Invoker("createCpuList")
    protected abstract CraftingStatusMenu.CraftingCpuList dataEnergistics$invokeCreateCpuList();

    @Unique
    @Nullable
    private TrinityCraftingStatusSelection.Target dataEnergistics$requestedTarget;

    protected CraftingStatusMenuMixin(MenuType<?> menuType, int id, Inventory inventory, Object host) {
        super(menuType, id, inventory, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$captureRequestedCpu(int id,
                                                     Inventory inventory,
                                                     ITerminalHost host,
                                                     CallbackInfo ci) {
        TrinityCraftingStatusSelection.Target target = TrinityCraftingStatusSelection.claim(inventory.player, host);
        if (target == null) {
            return;
        }
        this.dataEnergistics$requestedTarget = target;
        if (!(host instanceof TrinityAccessHatchBlockEntity hatch) || !hatch.isCurrentCpuStatusTarget(target)) {
            throw new IllegalStateException("Trinity CPU status target became stale during menu construction");
        }

        IGrid grid = hatch.accessGrid();
        if (grid == null) {
            throw new IllegalStateException("Trinity CPU status target lost its grid during menu construction");
        }
        this.lastCpuSet = grid.getCraftingService().getCpus();
        CraftingStatusMenu menu = (CraftingStatusMenu) (Object) this;
        menu.cpuList = dataEnergistics$invokeCreateCpuList();

        Integer serial = this.cpuSerialMap.get(target.cpu());
        boolean listed = serial != null && menu.cpuList.cpus().stream()
                .anyMatch(entry -> entry.serial() == serial);
        if (!listed) {
            throw new IllegalStateException("Trinity CPU status target has no AE menu serial");
        }
        menu.selectCpu(serial);
        TrinityCraftingStatusSelection.markSelected(target);
    }

    /**
     * Refuses AE's default fallback as soon as the originally requested CPU leaves its lease grid.
     */
    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$validateRequestedCpu(CallbackInfo ci) {
        TrinityCraftingStatusSelection.Target target = this.dataEnergistics$requestedTarget;
        if (target == null) {
            return;
        }

        ITerminalHost menuHost = ((CraftingStatusMenu) (Object) this).getHost();
        if (menuHost instanceof TrinityAccessHatchBlockEntity hatch && hatch.isCurrentCpuStatusTarget(target)) {
            return;
        }

        dataEnergistics$closeStaleTarget("target host, runtime, lease or grid identity changed", target);
        ci.cancel();
    }

    @Override
    public @Nullable TrinityCraftingStatusSelection.Target dataEnergistics$getTrinityTarget() {
        return this.dataEnergistics$requestedTarget;
    }

    @Unique
    private void dataEnergistics$closeStaleTarget(String reason,
                                                  TrinityCraftingStatusSelection.Target target) {
        CraftingStatusMenu menu = (CraftingStatusMenu) (Object) this;
        Data_Energistics.LOGGER.warn(
                "Closing Trinity CPU status because {}: player={}, host={}, cpuNumber={}",
                reason,
                getPlayer().getName().getString(),
                target.hostId(),
                target.cpu().number());
        this.dataEnergistics$requestedTarget = null;
        menu.setValidMenu(false);
        if (getPlayer().containerMenu == menu) {
            getPlayer().closeContainer();
        } else {
            Data_Energistics.LOGGER.error(
                    "Could not close stale Trinity CPU status because it is no longer current: player={}, menu={}",
                    getPlayer().getName().getString(),
                    menu.containerId);
        }
    }

    @Inject(method = "createCpuList", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$sortTrinityCpus(
                                                 CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuList> cir) {
        CraftingStatusMenu.CraftingCpuList current = cir.getReturnValue();
        if (current.cpus().size() < 2) {
            return;
        }

        Map<Integer, Integer> trinityNumbersBySerial = new HashMap<>();
        for (Map.Entry<ICraftingCPU, Integer> entry : this.cpuSerialMap.entrySet()) {
            if (entry.getKey() instanceof TrinityDataCoreVirtualCpu cpu) {
                trinityNumbersBySerial.put(entry.getValue(), cpu.number());
            }
        }
        if (trinityNumbersBySerial.isEmpty()) {
            return;
        }

        ArrayList<CraftingStatusMenu.CraftingCpuListEntry> sorted = new ArrayList<>(current.cpus());
        sorted.sort((left, right) -> compareCpuEntries(left, right, trinityNumbersBySerial));
        cir.setReturnValue(new CraftingStatusMenu.CraftingCpuList(List.copyOf(sorted)));
    }

    private static int compareCpuEntries(CraftingStatusMenu.CraftingCpuListEntry left,
                                         CraftingStatusMenu.CraftingCpuListEntry right,
                                         Map<Integer, Integer> trinityNumbersBySerial) {
        Integer leftNumber = trinityNumbersBySerial.get(left.serial());
        Integer rightNumber = trinityNumbersBySerial.get(right.serial());
        if (leftNumber == null) {
            return rightNumber == null ? 0 : 1;
        }
        if (rightNumber == null) {
            return -1;
        }
        return Integer.compare(leftNumber, rightNumber);
    }
}
