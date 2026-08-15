package com.fish_dan_.data_energistics.menu.crafting.projection.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects executable Trinity repeat blocks into exact confirmation-screen cycle statistics.
 */
public final class TrinityCraftingCycleSummaryProjection {

    private TrinityCraftingCycleSummaryProjection() {}

    /**
     * Creates one immutable summary without converting exact plan amounts through primitive numeric types.
     *
     * @param plan validated executable Trinity plan
     * @return cycle headers, material contributions and global inventory withdrawals
     */
    public static TrinityCraftingCycleSummary create(TrinityCraftingPlan plan) {
        Map<Integer, TrinityPlanStage> stagesByIndex = indexStages(plan.stages());
        ArrayList<TrinityCycleRepeatBlock> blocks = new ArrayList<>(plan.cycleRepeatBlocks());
        blocks.sort(Comparator.comparingInt(TrinityCycleRepeatBlock::index));

        ArrayList<TrinityCraftingCycleHeader> cycles = new ArrayList<>(blocks.size());
        ArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            TrinityCycleRepeatBlock block = blocks.get(index);
            int displayOrdinal = index + 1;
            BlockProjection projection = projectBlock(block, displayOrdinal, stagesByIndex);
            cycles.add(projection.cycle());
            contributions.addAll(projection.contributions());
        }
        return TrinityCraftingCycleSummary.create(plan.initialExpectedInputs(), cycles, contributions);
    }

    private static Map<Integer, TrinityPlanStage> indexStages(List<TrinityPlanStage> stages) {
        HashMap<Integer, TrinityPlanStage> stagesByIndex = new HashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        return stagesByIndex;
    }

    private static BlockProjection projectBlock(TrinityCycleRepeatBlock block,
                                                int displayOrdinal,
                                                Map<Integer, TrinityPlanStage> stagesByIndex) {
        BigInteger patternExecutions = BigInteger.ZERO;
        Set<TrinityPatternIdentity> patternTypes = new HashSet<>();
        LinkedHashMap<AEKey, MaterialRoles> materials = new LinkedHashMap<>();
        for (Integer stageIndex : block.stageOrder()) {
            TrinityPlanStage stage = stagesByIndex.get(stageIndex);
            for (TrinityPlanPatternFiring firing : stage.firings()) {
                patternExecutions = patternExecutions.add(firing.count().multiply(block.repetitions()));
                patternTypes.add(firing.patternIdentity());
                firing.inputs().keySet().forEach(key -> materials
                        .computeIfAbsent(key, ignored -> new MaterialRoles())
                        .markInput());
                firing.outputs().keySet().forEach(key -> materials
                        .computeIfAbsent(key, ignored -> new MaterialRoles())
                        .markOutput());
            }
        }
        block.minimumSeed().forEach((key, amount) -> materials
                .computeIfAbsent(key, ignored -> new MaterialRoles())
                .setMinimumSeed(amount));
        block.netChange().forEach((key, amount) -> materials
                .computeIfAbsent(key, ignored -> new MaterialRoles())
                .setNetChange(amount));

        TrinityCraftingCycleHeader cycle = new TrinityCraftingCycleHeader(
                block.index(),
                displayOrdinal,
                block.repetitions(),
                patternExecutions,
                block.stageOrder().size(),
                patternTypes.size());
        ArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ArrayList<>(materials.size());
        materials.forEach((key, roles) -> contributions.add(roles.toContribution(
                block.index(),
                displayOrdinal,
                key)));
        return new BlockProjection(cycle, List.copyOf(contributions));
    }

    private record BlockProjection(TrinityCraftingCycleHeader cycle,
                                   List<TrinityCraftingCycleMaterialContribution> contributions) {}

    private static final class MaterialRoles {

        private boolean input;
        private boolean output;
        private BigInteger minimumSeed = BigInteger.ZERO;
        private BigInteger netChange = BigInteger.ZERO;

        private void markInput() {
            this.input = true;
        }

        private void markOutput() {
            this.output = true;
        }

        private void setMinimumSeed(BigInteger amount) {
            this.minimumSeed = amount;
        }

        private void setNetChange(BigInteger amount) {
            this.netChange = amount;
        }

        private TrinityCraftingCycleMaterialContribution toContribution(int blockIndex,
                                                                        int displayOrdinal,
                                                                        AEKey key) {
            return new TrinityCraftingCycleMaterialContribution(
                    blockIndex,
                    displayOrdinal,
                    key,
                    this.input,
                    this.output,
                    this.minimumSeed,
                    this.netChange);
        }
    }
}
