package com.fish_dan_.data_energistics.integration.ae2ct;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.util.LongAmountMath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.neuvillette.ae2ct.api.RecipeHelper;
import org.jetbrains.annotations.ApiStatus;

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
 * Converts a compact Trinity plan into AE2 Crafting Tree's public transport model.
 */
@ApiStatus.Internal
public final class TrinityCraftingTreeProjection {

    private static final int MAX_PROJECTED_TREE_DEPTH = 128;

    private TrinityCraftingTreeProjection() {}

    /**
     * Aggregates every selected firing by primary output because AE2 Crafting Tree only retains one recipe for each
     * output key and does not consume its recipe-times field.
     *
     * @param plan validated executable Trinity plan
     * @return complete recipe tree payload for the current request
     */
    public static RecipeHelper create(TrinityCraftingPlan plan) {
        Map<Integer, BigInteger> stageMultipliers = stageMultipliers(plan);
        LinkedHashMap<AEKey, AggregateRecipe> recipesByPrimaryOutput = new LinkedHashMap<>();
        for (TrinityPlanStage stage : plan.stages()) {
            BigInteger multiplier = stageMultipliers.get(stage.index());
            for (TrinityPlanPatternFiring firing : stage.firings()) {
                BigInteger totalFirings = firing.count().multiply(multiplier);
                recipesByPrimaryOutput
                        .computeIfAbsent(firing.primaryOutput(), AggregateRecipe::new)
                        .add(firing, totalFirings);
            }
        }

        AEKey requestedOutput = plan.finalOutput().what();
        recipesByPrimaryOutput.computeIfAbsent(
                requestedOutput,
                ignored -> AggregateRecipe.forOutput(plan.finalOutput()));
        removeCycleEdges(requestedOutput, recipesByPrimaryOutput);
        truncateDeepEdges(requestedOutput, recipesByPrimaryOutput);
        Set<AEKey> reachableOutputs = findReachableOutputs(requestedOutput, recipesByPrimaryOutput);

        ArrayList<RecipeHelper.Recipe> recipes = new ArrayList<>(reachableOutputs.size());
        recipesByPrimaryOutput.forEach((output, recipe) -> {
            if (reachableOutputs.contains(output)) {
                recipes.add(recipe.toRecipe());
            }
        });
        return new RecipeHelper(plan.finalOutput(), List.copyOf(recipes));
    }

    /**
     * Creates a finite AE2 Crafting Tree root for a terminal diagnostic that has no executable recipe plan. AE2
     * Crafting Tree cannot build an empty recipe list, and its summary extension is always serialized.
     *
     * @param requestedOutput original crafting request shown by the confirmation menu
     * @return a serializable leaf tree rooted at the requested output
     */
    public static RecipeHelper createDiagnostic(GenericStack requestedOutput) {
        RecipeHelper.Recipe root = new RecipeHelper.Recipe(
                List.of(),
                List.of(requestedOutput),
                1L);
        return new RecipeHelper(requestedOutput, List.of(root));
    }

    private static Map<Integer, BigInteger> stageMultipliers(TrinityCraftingPlan plan) {
        HashMap<Integer, BigInteger> multipliers = new HashMap<>();
        plan.stages().forEach(stage -> multipliers.put(stage.index(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : plan.cycleRepeatBlocks()) {
            block.stageOrder().forEach(stage -> multipliers.put(stage, block.repetitions()));
        }
        return multipliers;
    }

    /**
     * AE2 Crafting Tree recursively follows one recipe per primary output and ignores its recipe-times field. A
     * Trinity repeat block must therefore be projected as aggregate amounts, with dependency back edges removed from
     * the display graph. The executable Trinity plan remains authoritative; this projection is only a finite view.
     */
    private static void removeCycleEdges(
                                         AEKey requestedOutput,
                                         Map<AEKey, AggregateRecipe> recipesByPrimaryOutput) {
        HashMap<AEKey, VisitState> visitStates = new HashMap<>();
        ArrayDeque<TraversalFrame> stack = new ArrayDeque<>();
        visitStates.put(requestedOutput, VisitState.VISITING);
        stack.push(new TraversalFrame(
                requestedOutput,
                producerInputs(requestedOutput, recipesByPrimaryOutput)));

        while (!stack.isEmpty()) {
            TraversalFrame frame = stack.peek();
            if (!frame.hasNext()) {
                visitStates.put(frame.output(), VisitState.VISITED);
                stack.pop();
                continue;
            }

            AEKey input = frame.next();
            VisitState inputState = visitStates.get(input);
            if (inputState == VisitState.VISITING) {
                recipesByPrimaryOutput.get(frame.output()).removeInput(input);
                continue;
            }
            if (inputState == null) {
                visitStates.put(input, VisitState.VISITING);
                stack.push(new TraversalFrame(
                        input,
                        producerInputs(input, recipesByPrimaryOutput)));
            }
        }
    }

    private static void truncateDeepEdges(
                                          AEKey requestedOutput,
                                          Map<AEKey, AggregateRecipe> recipesByPrimaryOutput) {
        Set<AEKey> reachableOutputs = findReachableOutputs(requestedOutput, recipesByPrimaryOutput);
        LinkedHashMap<AEKey, Integer> indegrees = new LinkedHashMap<>();
        reachableOutputs.forEach(output -> indegrees.put(output, 0));
        for (AEKey output : reachableOutputs) {
            for (AEKey input : producerInputs(output, recipesByPrimaryOutput)) {
                if (reachableOutputs.contains(input)) {
                    indegrees.computeIfPresent(input, (ignored, degree) -> degree + 1);
                }
            }
        }

        ArrayDeque<AEKey> ready = new ArrayDeque<>();
        indegrees.forEach((output, degree) -> {
            if (degree == 0) {
                ready.addLast(output);
            }
        });
        HashMap<AEKey, Integer> longestDepths = new HashMap<>();
        longestDepths.put(requestedOutput, 0);

        while (!ready.isEmpty()) {
            AEKey output = ready.removeFirst();
            Integer outputDepth = longestDepths.get(output);
            for (AEKey input : producerInputs(output, recipesByPrimaryOutput)) {
                Integer inputDegree = indegrees.get(input);
                if (inputDegree == null) {
                    continue;
                }
                if (outputDepth != null) {
                    int inputDepth = outputDepth + 1;
                    if (inputDepth > MAX_PROJECTED_TREE_DEPTH) {
                        recipesByPrimaryOutput.get(output).removeInput(input);
                    } else {
                        longestDepths.merge(input, inputDepth, Math::max);
                    }
                }
                int remainingDegree = inputDegree - 1;
                indegrees.put(input, remainingDegree);
                if (remainingDegree == 0) {
                    ready.addLast(input);
                }
            }
        }
    }

    private static Set<AEKey> findReachableOutputs(
                                                   AEKey requestedOutput,
                                                   Map<AEKey, AggregateRecipe> recipesByPrimaryOutput) {
        LinkedHashSet<AEKey> reachableOutputs = new LinkedHashSet<>();
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        if (recipesByPrimaryOutput.containsKey(requestedOutput)) {
            reachableOutputs.add(requestedOutput);
            pending.addLast(requestedOutput);
        }
        while (!pending.isEmpty()) {
            AEKey output = pending.removeFirst();
            for (AEKey input : producerInputs(output, recipesByPrimaryOutput)) {
                if (reachableOutputs.add(input)) {
                    pending.addLast(input);
                }
            }
        }
        return reachableOutputs;
    }

    private static List<AEKey> producerInputs(
                                              AEKey output,
                                              Map<AEKey, AggregateRecipe> recipesByPrimaryOutput) {
        AggregateRecipe recipe = recipesByPrimaryOutput.get(output);
        if (recipe == null) {
            return List.of();
        }
        ArrayList<AEKey> inputs = new ArrayList<>();
        recipe.inputs().keySet().forEach(input -> {
            if (recipesByPrimaryOutput.containsKey(input)) {
                inputs.add(input);
            }
        });
        return List.copyOf(inputs);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private static final class TraversalFrame {

        private final AEKey output;
        private final List<AEKey> inputs;
        private int nextInput;

        private TraversalFrame(AEKey output, List<AEKey> inputs) {
            this.output = output;
            this.inputs = inputs;
        }

        private AEKey output() {
            return this.output;
        }

        private boolean hasNext() {
            return this.nextInput < this.inputs.size();
        }

        private AEKey next() {
            return this.inputs.get(this.nextInput++);
        }
    }

    private static final class AggregateRecipe {

        private final AEKey primaryOutput;
        private final LinkedHashMap<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, BigInteger> outputs = new LinkedHashMap<>();

        private AggregateRecipe(AEKey primaryOutput) {
            this.primaryOutput = primaryOutput;
        }

        private static AggregateRecipe forOutput(GenericStack output) {
            AggregateRecipe recipe = new AggregateRecipe(output.what());
            recipe.outputs.put(output.what(), BigInteger.valueOf(output.amount()));
            return recipe;
        }

        private void add(TrinityPlanPatternFiring firing, BigInteger totalFirings) {
            addAmounts(this.inputs, firing.inputs(), totalFirings);
            addAmounts(this.outputs, firing.outputs(), totalFirings);
        }

        private static void addAmounts(
                                       Map<AEKey, BigInteger> aggregate,
                                       Map<AEKey, BigInteger> amounts,
                                       BigInteger multiplier) {
            amounts.forEach((key, amount) -> aggregate.merge(
                    key,
                    amount.multiply(multiplier),
                    BigInteger::add));
        }

        private Map<AEKey, BigInteger> inputs() {
            return this.inputs;
        }

        private void removeInput(AEKey input) {
            this.inputs.remove(input);
        }

        private RecipeHelper.Recipe toRecipe() {
            return new RecipeHelper.Recipe(
                    toStacks(this.inputs),
                    toOutputStacks(),
                    1L);
        }

        private List<GenericStack> toOutputStacks() {
            ArrayList<GenericStack> stacks = new ArrayList<>(this.outputs.size());
            stacks.add(new GenericStack(
                    this.primaryOutput,
                    LongAmountMath.saturatingLongValueNonNegative(this.outputs.get(this.primaryOutput))));
            this.outputs.forEach((key, amount) -> {
                if (!key.equals(this.primaryOutput)) {
                    stacks.add(new GenericStack(key, LongAmountMath.saturatingLongValueNonNegative(amount)));
                }
            });
            return List.copyOf(stacks);
        }

        private static List<GenericStack> toStacks(Map<AEKey, BigInteger> amounts) {
            ArrayList<GenericStack> stacks = new ArrayList<>(amounts.size());
            amounts.forEach((key, amount) -> stacks.add(new GenericStack(
                    key,
                    LongAmountMath.saturatingLongValueNonNegative(amount))));
            return List.copyOf(stacks);
        }
    }
}
