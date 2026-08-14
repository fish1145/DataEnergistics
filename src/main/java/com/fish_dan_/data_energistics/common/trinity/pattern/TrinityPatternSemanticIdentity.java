package com.fish_dan_.data_energistics.common.trinity.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;

import java.util.List;

/**
 * Complete decoded execution semantics used to collapse equivalent encoded pattern items during migration.
 *
 * <p>
 * The encoded definition is intentionally excluded. The runtime family remains part of the identity so two
 * pattern implementations with equal planner stacks but different execution contracts are never merged.
 * </p>
 */
record TrinityPatternSemanticIdentity(Class<?> patternFamily,
                                      List<TrinityPatternPublicationSignature.Input> inputs,
                                      List<GenericStack> outputs,
                                      boolean pushesInputsToExternalInventory) {

    static TrinityPatternSemanticIdentity capture(IPatternDetails pattern) {
        TrinityPatternPublicationSignature signature = TrinityPatternPublicationSignature.capture(pattern);
        return new TrinityPatternSemanticIdentity(
                pattern.getClass(),
                signature.inputs(),
                signature.outputs(),
                signature.pushesInputsToExternalInventory());
    }

    TrinityPatternSemanticIdentity {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
