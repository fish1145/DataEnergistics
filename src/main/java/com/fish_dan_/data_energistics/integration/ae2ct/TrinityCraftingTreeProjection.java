package com.fish_dan_.data_energistics.integration.ae2ct;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.neuvillette.ae2ct.api.RecipeHelper;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a compact Trinity plan into AE2 Crafting Tree's public transport model.
 */
@ApiStatus.Internal
public final class TrinityCraftingTreeProjection {

    private TrinityCraftingTreeProjection() {}

    /**
     * Preserves the selected input variant, declared outputs and total logical firing count of every planned recipe.
     *
     * @param plan validated executable Trinity plan
     * @return complete recipe tree payload for the current request
     */
    public static RecipeHelper create(TrinityCraftingPlan plan) {
        Map<Integer, BigInteger> stageMultipliers = stageMultipliers(plan);
        LinkedHashMap<RecipeShape, BigInteger> counts = new LinkedHashMap<>();
        for (TrinityPlanStage stage : plan.stages()) {
            BigInteger multiplier = stageMultipliers.get(stage.index());
            for (TrinityPlanPatternFiring firing : stage.firings()) {
                RecipeShape shape = new RecipeShape(toStacks(firing.inputs()), toStacks(firing.outputs()));
                counts.merge(shape, firing.count().multiply(multiplier), BigInteger::add);
            }
        }

        ArrayList<RecipeHelper.Recipe> recipes = new ArrayList<>(counts.size());
        counts.forEach((shape, count) -> recipes.add(new RecipeHelper.Recipe(
                shape.inputs(),
                shape.outputs(),
                count.longValueExact())));
        return new RecipeHelper(plan.finalOutput(), List.copyOf(recipes));
    }

    private static Map<Integer, BigInteger> stageMultipliers(TrinityCraftingPlan plan) {
        HashMap<Integer, BigInteger> multipliers = new HashMap<>();
        plan.stages().forEach(stage -> multipliers.put(stage.index(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : plan.cycleRepeatBlocks()) {
            block.stageOrder().forEach(stage -> multipliers.put(stage, block.repetitions()));
        }
        return multipliers;
    }

    private static List<GenericStack> toStacks(Map<AEKey, BigInteger> amounts) {
        ArrayList<GenericStack> stacks = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> stacks.add(new GenericStack(key, amount.longValueExact())));
        return List.copyOf(stacks);
    }

    private record RecipeShape(List<GenericStack> inputs, List<GenericStack> outputs) {

        private RecipeShape {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }
}
