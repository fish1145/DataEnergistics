package com.fish_dan_.data_energistics.menu.crafting.tree.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission.TrinityPlanAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission.TrinityPlanAdmission.CpuFamily;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Menu-local CPU selection. Automatic selection is represented by null, never by a stale numeric index. */
public final class CraftingPlanCpuSelection {

    private static final TrinityPlanAdmission ADMISSION = TrinityPlanAdmission.create();
    private List<ICraftingCPU> candidates = List.of();
    private @Nullable ICraftingCPU selected;

    public CraftingPlanCpuSelection(@Nullable ICraftingCPU selected) {
        this.selected = selected;
    }

    public void refresh(Iterable<ICraftingCPU> cpus, @Nullable ICraftingPlan plan) {
        ObjectArrayList<ICraftingCPU> next = new ObjectArrayList<>();
        for (ICraftingCPU cpu : cpus) {
            if (plan != null && !plan.simulation() && accepts(cpu, plan)) next.add(cpu);
        }
        next.sort(Comparator.comparingInt(ICraftingCPU::getCoProcessors).reversed()
                .thenComparing(Comparator.comparingLong(ICraftingCPU::getAvailableStorage).reversed())
                .thenComparing(cpu -> cpu.getName() == null ? "" : cpu.getName().getString()));
        this.candidates = List.copyOf(next);
        // A missing/simulated plan cannot establish that a previously chosen CPU is unsuitable.
        if (plan != null && !plan.simulation() && this.selected != null && next.stream().noneMatch(cpu -> cpu == this.selected)) this.selected = null;
    }

    public static boolean accepts(ICraftingCPU cpu, ICraftingPlan plan) {
        CpuFamily family = cpu instanceof TrinityDataCoreVirtualCpu ? CpuFamily.TRINITY : CpuFamily.NON_TRINITY;
        return !cpu.isBusy() && cpu.getAvailableStorage() >= plan.bytes() && ADMISSION.isCompatibleWith(plan, family) && (!(cpu instanceof TrinityDataCoreVirtualCpu trinity) || trinity.canAcceptJob());
    }

    public void cycle(boolean forward) {
        int index = -1;
        for (int i = 0; i < this.candidates.size(); i++) if (this.candidates.get(i) == this.selected) index = i;
        int next = Math.floorMod(index + 1 + (forward ? 1 : -1), this.candidates.size() + 1) - 1;
        this.selected = next < 0 ? null : this.candidates.get(next);
    }

    public @Nullable ICraftingCPU selected() {
        return this.selected;
    }

    public boolean available() {
        return !this.candidates.isEmpty();
    }

    public List<ICraftingCPU> candidates() {
        return this.candidates;
    }
}
