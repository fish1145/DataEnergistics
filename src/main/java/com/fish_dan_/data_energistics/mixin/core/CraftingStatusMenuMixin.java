package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps Trinity CPU entries in their stable numeric order without changing AE2's private selection serials. */
@Mixin(CraftingStatusMenu.class)
public abstract class CraftingStatusMenuMixin {

    @Shadow
    @Final
    private WeakHashMap<ICraftingCPU, Integer> cpuSerialMap;

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
