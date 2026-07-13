package com.fish_dan_.data_energistics.common.trinity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import java.util.ArrayList;
import java.util.List;

/**
 * AE-visible pattern semantics used to distinguish a harmless recipe-object rebind from a publication change.
 *
 * @param definition                      encoded pattern definition exposed to AE2
 * @param inputs                          ordered input alternatives and multipliers used by planning
 * @param outputs                         ordered outputs used by planning
 * @param pushesInputsToExternalInventory whether the pattern supports direct external input transfer
 */
record TrinityPatternPublicationSignature(AEItemKey definition,
                                          List<Input> inputs,
                                          List<GenericStack> outputs,
                                          boolean pushesInputsToExternalInventory) {

    /** Copies all collection components so decoded pattern implementations cannot mutate a retained signature. */
    TrinityPatternPublicationSignature {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /**
     * Captures the complete AE planning surface of one runtime recipe binding.
     *
     * @param pattern current decoded molecular-assembler pattern
     * @return immutable publication signature
     */
    static TrinityPatternPublicationSignature capture(IMolecularAssemblerSupportedPattern pattern) {
        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        ArrayList<Input> inputs = new ArrayList<>(patternInputs.length);
        for (IPatternDetails.IInput input : patternInputs) {
            inputs.add(new Input(input.getMultiplier(), List.of(input.getPossibleInputs())));
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
     * @param multiplier     amount of the selected alternative consumed by one craft
     * @param possibleInputs immutable accepted alternatives
     */
    record Input(long multiplier, List<GenericStack> possibleInputs) {

        /** Validates the positive multiplier and isolates the alternatives array returned by AE2. */
        Input {
            if (multiplier <= 0L) {
                throw new IllegalArgumentException("A Trinity pattern input multiplier must be positive");
            }
            possibleInputs = List.copyOf(possibleInputs);
        }
    }
}
