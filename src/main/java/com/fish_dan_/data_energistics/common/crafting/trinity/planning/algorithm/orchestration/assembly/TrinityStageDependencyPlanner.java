package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives quantity-proven execution dependencies without turning shared keys or repeat blocks into global barriers.
 *
 * <p>
 * Each initial or produced balance is represented as a stable token lot. A stage depends only on the earlier units
 * whose returned or produced lots it actually needs. Unused initial lots remain available to later stages, so two
 * consumers may run together when the captured initial balance covers both. A scarce catalyst returned by one unit
 * becomes a lot owned by that unit, which preserves the required order for a later consumer without serializing
 * consumers backed by different lots.
 * </p>
 *
 * <p>
 * A compressed repeat block is one allocation unit: its minimum seed is the transient start requirement, its exact
 * negative net change is also reserved, its first stage receives external dependencies, and its last stage is the
 * completion anchor. The repeat cursor remains the sole authority for ordering stages inside the block.
 * </p>
 */
final class TrinityStageDependencyPlanner {

    private TrinityStageDependencyPlanner() {}

    /**
     * Replaces provisional empty dependencies with exact quantity-proven predecessor sets.
     *
     * @param initialInputs initial balances owned by the executable plan
     * @param stages        provisional stages with exact start requirements and net changes
     * @param stageOrder    stable sequential order already verified by the planner
     * @param repeatBlocks  compressed cycle units referenced by the stages
     * @return immutable stages carrying quantity-proven dependencies
     */
    static List<TrinityPlanStage> plan(
                                       Map<AEKey, BigInteger> initialInputs,
                                       List<TrinityPlanStage> stages,
                                       List<Integer> stageOrder,
                                       List<TrinityCycleRepeatBlock> repeatBlocks) {
        if (initialInputs == null || stages == null || stageOrder == null || repeatBlocks == null) {
            throw new IllegalArgumentException("Trinity stage dependency planning requires complete plan state");
        }

        Map<Integer, TrinityPlanStage> stagesByIndex = stagesByIndex(stages);
        Map<Integer, TrinityCycleRepeatBlock> repeatByStage = repeatByStage(repeatBlocks, stagesByIndex);
        List<ExecutionUnit> units = executionUnits(stageOrder, stagesByIndex, repeatByStage);
        Map<Integer, Set<Integer>> dependenciesByEntry = allocateDependencies(initialInputs, units);

        ArrayList<TrinityPlanStage> planned = new ArrayList<>(stages.size());
        for (TrinityPlanStage stage : stages) {
            planned.add(new TrinityPlanStage(
                    stage.index(),
                    stage.cycleStage(),
                    dependenciesByEntry.getOrDefault(stage.index(), Set.of()),
                    stage.firings(),
                    stage.requiredAtStart(),
                    stage.netChange()));
        }
        return List.copyOf(planned);
    }

    private static Map<Integer, TrinityPlanStage> stagesByIndex(List<TrinityPlanStage> stages) {
        LinkedHashMap<Integer, TrinityPlanStage> indexed = new LinkedHashMap<>();
        for (TrinityPlanStage stage : stages) {
            if (stage == null || indexed.putIfAbsent(stage.index(), stage) != null) {
                throw new IllegalArgumentException("Trinity dependency planning requires unique non-null stages");
            }
        }
        return indexed;
    }

    private static Map<Integer, TrinityCycleRepeatBlock> repeatByStage(
                                                                       List<TrinityCycleRepeatBlock> repeatBlocks,
                                                                       Map<Integer, TrinityPlanStage> stages) {
        HashMap<Integer, TrinityCycleRepeatBlock> indexed = new HashMap<>();
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            if (block == null) {
                throw new IllegalArgumentException("Trinity dependency planning cannot contain a null repeat block");
            }
            for (Integer stageIndex : block.stageOrder()) {
                TrinityPlanStage stage = stages.get(stageIndex);
                if (stage == null || !stage.cycleStage() || indexed.putIfAbsent(stageIndex, block) != null) {
                    throw new IllegalArgumentException("Trinity dependency planning found an invalid repeat stage");
                }
            }
        }
        for (TrinityPlanStage stage : stages.values()) {
            if (stage.cycleStage() != indexed.containsKey(stage.index())) {
                throw new IllegalArgumentException("Every Trinity cycle stage must belong to exactly one repeat block");
            }
        }
        return indexed;
    }

    private static List<ExecutionUnit> executionUnits(
                                                      List<Integer> stageOrder,
                                                      Map<Integer, TrinityPlanStage> stages,
                                                      Map<Integer, TrinityCycleRepeatBlock> repeatByStage) {
        if (stageOrder.size() != stages.size() || !new LinkedHashSet<>(stageOrder).equals(stages.keySet())) {
            throw new IllegalArgumentException("Trinity dependency planning requires one complete stage order");
        }
        ArrayList<ExecutionUnit> units = new ArrayList<>();
        int position = 0;
        while (position < stageOrder.size()) {
            int stageIndex = stageOrder.get(position);
            TrinityPlanStage stage = stages.get(stageIndex);
            if (!stage.cycleStage()) {
                units.add(ExecutionUnit.forStage(stage));
                position++;
                continue;
            }

            TrinityCycleRepeatBlock block = repeatByStage.get(stageIndex);
            if (!block.stageOrder().getFirst().equals(stageIndex)) {
                throw new IllegalArgumentException("A Trinity repeat block must begin at its first ordered stage");
            }
            for (Integer blockStage : block.stageOrder()) {
                if (position >= stageOrder.size() || !stageOrder.get(position).equals(blockStage)) {
                    throw new IllegalArgumentException("A Trinity repeat block must be contiguous in execution order");
                }
                position++;
            }
            units.add(ExecutionUnit.forRepeat(block));
        }
        return List.copyOf(units);
    }

    private static Map<Integer, Set<Integer>> allocateDependencies(
                                                                   Map<AEKey, BigInteger> initialInputs,
                                                                   List<ExecutionUnit> units) {
        LinkedHashMap<AEKey, ArrayDeque<TokenLot>> balances = new LinkedHashMap<>();
        initialInputs.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity dependency initial balances must be positive");
            }
            balances.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                    .addLast(new TokenLot(amount, null));
        });

        LinkedHashMap<Integer, Set<Integer>> dependencies = new LinkedHashMap<>();
        for (ExecutionUnit unit : units) {
            LinkedHashSet<Integer> unitDependencies = new LinkedHashSet<>();
            LinkedHashMap<AEKey, BigInteger> requirements = reservationRequirements(unit);
            LinkedHashSet<AEKey> touchedKeys = new LinkedHashSet<>(requirements.keySet());
            touchedKeys.addAll(unit.netChange().keySet());
            for (AEKey key : touchedKeys) {
                BigInteger required = requirements.getOrDefault(key, BigInteger.ZERO);
                consumeLots(balances, key, required, unitDependencies);
                BigInteger returned = required.add(unit.netChange().getOrDefault(key, BigInteger.ZERO));
                if (returned.signum() < 0) {
                    throw new IllegalStateException("A Trinity dependency unit returned a negative material balance");
                }
                if (returned.signum() > 0) {
                    balances.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                            .addLast(new TokenLot(returned, unit.completionStage()));
                }
            }
            dependencies.put(unit.entryStage(), Set.copyOf(unitDependencies));
        }
        return dependencies;
    }

    private static LinkedHashMap<AEKey, BigInteger> reservationRequirements(ExecutionUnit unit) {
        LinkedHashMap<AEKey, BigInteger> requirements = new LinkedHashMap<>(unit.requiredAtStart());
        unit.netChange().forEach((key, change) -> {
            if (change.signum() < 0) {
                requirements.merge(key, change.negate(), BigInteger::max);
            }
        });
        return requirements;
    }

    private static void consumeLots(
                                    Map<AEKey, ArrayDeque<TokenLot>> balances,
                                    AEKey key,
                                    BigInteger required,
                                    Set<Integer> dependencies) {
        BigInteger remaining = required;
        ArrayDeque<TokenLot> lots = balances.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (remaining.signum() > 0) {
            TokenLot lot = lots.pollFirst();
            if (lot == null) {
                throw new IllegalStateException("A validated Trinity execution order lacks a required material balance");
            }
            BigInteger consumed = remaining.min(lot.amount());
            if (lot.sourceStage() != null) {
                dependencies.add(lot.sourceStage());
            }
            BigInteger leftover = lot.amount().subtract(consumed);
            if (leftover.signum() > 0) {
                lots.addFirst(new TokenLot(leftover, lot.sourceStage()));
            }
            remaining = remaining.subtract(consumed);
        }
    }

    private record TokenLot(BigInteger amount, Integer sourceStage) {

        private TokenLot {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity dependency token lot must be positive");
            }
        }
    }

    private record ExecutionUnit(
                                 int entryStage,
                                 int completionStage,
                                 Map<AEKey, BigInteger> requiredAtStart,
                                 Map<AEKey, BigInteger> netChange) {

        private ExecutionUnit {
            requiredAtStart = Map.copyOf(requiredAtStart);
            netChange = Map.copyOf(netChange);
        }

        private static ExecutionUnit forStage(TrinityPlanStage stage) {
            return new ExecutionUnit(
                    stage.index(),
                    stage.index(),
                    stage.requiredAtStart(),
                    stage.netChange());
        }

        private static ExecutionUnit forRepeat(TrinityCycleRepeatBlock block) {
            return new ExecutionUnit(
                    block.stageOrder().getFirst(),
                    block.stageOrder().getLast(),
                    block.minimumSeed(),
                    block.netChange());
        }
    }
}
