package com.fish_dan_.data_energistics.common.trinity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * AE-visible pattern semantics used to distinguish a harmless recipe-object rebind from a publication change.
 *
 * @param definition                      encoded pattern definition exposed to AE2
 * @param inputs                          ordered input alternatives and multipliers used by planning
 * @param outputs                         ordered outputs used by planning
 * @param pushesInputsToExternalInventory whether the pattern supports direct external input transfer
 */
public record TrinityPatternPublicationSignature(AEItemKey definition,
                                                 List<Input> inputs,
                                                 List<GenericStack> outputs,
                                                 boolean pushesInputsToExternalInventory) {

    /**
     * Copies and validates every planning value so a decoded third-party pattern cannot mutate or poison a retained
     * publication.
     */
    public TrinityPatternPublicationSignature {
        Objects.requireNonNull(definition, "A Trinity pattern publication requires a definition");
        inputs = List.copyOf(inputs);
        outputs = copyPositiveOutputs(outputs);
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("A Trinity pattern publication requires at least one output");
        }
    }

    /**
     * Captures the complete AE planning surface of one runtime recipe binding.
     *
     * <p>
     * Capture must run on the server thread. The returned value retains only AE keys, amounts and collection values;
     * it never retains the mutable pattern implementation.
     * </p>
     *
     * @param pattern current decoded crafting pattern
     * @return immutable publication signature
     */
    public static TrinityPatternPublicationSignature capture(IPatternDetails pattern) {
        Objects.requireNonNull(pattern, "A Trinity pattern publication requires pattern details");
        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        if (patternInputs == null) {
            throw new IllegalArgumentException("A Trinity pattern publication requires an input array");
        }
        ArrayList<Input> inputs = new ArrayList<>(patternInputs.length);
        for (IPatternDetails.IInput input : patternInputs) {
            if (input == null) {
                throw new IllegalArgumentException("A Trinity pattern publication cannot contain a null input");
            }
            inputs.add(Input.capture(input));
        }
        return new TrinityPatternPublicationSignature(
                pattern.getDefinition(),
                inputs,
                pattern.getOutputs(),
                pattern.supportsPushInputsToExternalInventory());
    }

    /**
     * One ordered planner input and every accepted alternative.
     *
     * @param multiplier   amount of the selected alternative consumed by one craft
     * @param alternatives immutable accepted alternatives and their corresponding remaining keys
     */
    public record Input(long multiplier, List<Alternative> alternatives) {

        /**
         * Validates the positive multiplier and isolates the alternatives array returned by AE2.
         *
         * <p>
         * Exact duplicate alternatives are semantically redundant. They are collapsed while retaining first-choice
         * order; alternatives with the same key but a different amount remain distinct.
         * </p>
         */
        public Input {
            if (multiplier <= 0L) {
                throw new IllegalArgumentException("A Trinity pattern input multiplier must be positive");
            }
            Objects.requireNonNull(alternatives, "A Trinity pattern input alternative collection is required");
            if (alternatives.isEmpty()) {
                throw new IllegalArgumentException("A Trinity pattern input requires at least one alternative");
            }
            LinkedHashSet<Alternative> unique = new LinkedHashSet<>();
            for (Alternative alternative : alternatives) {
                if (alternative == null) {
                    throw new IllegalArgumentException("A Trinity pattern input cannot contain a null alternative");
                }
                unique.add(alternative);
            }
            alternatives = List.copyOf(unique);
        }

        /**
         * Captures every remaining key through the same template used to select its alternative.
         *
         * @param input live server-thread AE input
         * @return immutable input semantics
         */
        public static Input capture(IPatternDetails.IInput input) {
            Objects.requireNonNull(input, "A Trinity pattern input is required");
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs == null) {
                throw new IllegalArgumentException("A Trinity pattern input requires an alternative array");
            }
            ArrayList<Alternative> alternatives = new ArrayList<>(possibleInputs.length);
            for (GenericStack stack : possibleInputs) {
                GenericStack validated = requirePositiveStack(stack, "input alternative");
                alternatives.add(new Alternative(validated, input.getRemainingKey(validated.what())));
            }
            return new Input(input.getMultiplier(), alternatives);
        }

        /**
         * @return immutable accepted stacks in their first-choice order
         */
        public List<GenericStack> possibleInputs() {
            return this.alternatives.stream().map(Alternative::stack).toList();
        }
    }

    /**
     * One legal input template and the key returned after that exact template is consumed.
     *
     * @param stack        input key and per-template amount
     * @param remainingKey returned container or remainder, or {@code null} when consumption has no remainder
     */
    public record Alternative(GenericStack stack, @Nullable AEKey remainingKey) {

        /**
         * Validates the immutable positive input stack while allowing AE's explicit no-remainder value.
         */
        public Alternative {
            stack = requirePositiveStack(stack, "input alternative");
        }
    }

    private static List<GenericStack> copyPositiveOutputs(List<GenericStack> stacks) {
        Objects.requireNonNull(stacks, "A Trinity pattern output collection is required");
        ArrayList<GenericStack> copied = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            copied.add(requirePositiveStack(stack, "output"));
        }
        return List.copyOf(copied);
    }

    private static GenericStack requirePositiveStack(@Nullable GenericStack stack, String role) {
        if (stack == null || stack.what() == null) {
            throw new IllegalArgumentException("A Trinity pattern cannot contain a null " + role);
        }
        if (stack.amount() <= 0L) {
            throw new IllegalArgumentException("A Trinity pattern " + role + " amount must be positive");
        }
        return stack;
    }
}
