package com.fish_dan_.data_energistics.common.crafting.dynamic;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutput;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputMatchMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Persists and resolves the player-controlled SAME_ITEM rule attached to an encoded processing pattern.
 */
public final class EncodedPatternDynamicOutput {

    /** Stable source recorded in the CPU dynamic-output ledger. */
    public static final ResourceLocation SOURCE_ID = Data_Energistics.id("encoded_pattern_output");

    private EncodedPatternDynamicOutput() {}

    /**
     * Applies the selected output rule to the final encoded pattern stack.
     *
     * @param encodedPattern final pattern item produced by AE2 or another encoder
     * @param sameItem       whether its first processing output accepts the same registered item
     */
    public static void apply(ItemStack encodedPattern, boolean sameItem) {
        if (encodedPattern.isEmpty()) {
            throw new IllegalArgumentException("Cannot mark an empty encoded pattern");
        }
        if (sameItem) {
            encodedPattern.set(DEDataComponents.PROCESSING_OUTPUT_SAME_ITEM, true);
        } else {
            encodedPattern.remove(DEDataComponents.PROCESSING_OUTPUT_SAME_ITEM);
        }
    }

    /**
     * Reads the rule without weakening the complete encoded-pattern definition key.
     *
     * @param definition complete encoded-pattern identity
     * @return whether the pattern explicitly opted into SAME_ITEM output matching
     */
    public static boolean isMarked(AEItemKey definition) {
        return Boolean.TRUE.equals(definition.get(DEDataComponents.PROCESSING_OUTPUT_SAME_ITEM.get()));
    }

    /**
     * Resolves and validates the marked pattern's first output against its physical output list.
     *
     * @param details original outer pattern details selected for dispatch
     * @return one per-push SAME_ITEM declaration
     */
    public static DynamicCraftingOutput resolve(IPatternDetails details) {
        GenericStack output = details.getPrimaryOutput();
        if (output == null || output.amount() <= 0L || !(output.what() instanceof AEItemKey)) {
            throw new DynamicCraftingOutputResolutionException(
                    "Encoded pattern output matching requires a positive item output for pattern " +
                            details.getDefinition());
        }

        long declaredAmount = 0L;
        try {
            for (GenericStack declared : details.getOutputs()) {
                if (declared == null || declared.what() == null || declared.amount() <= 0L) {
                    throw new DynamicCraftingOutputResolutionException(
                            "Encoded pattern exposes an invalid physical output: " + details.getDefinition());
                }
                if (output.what().equals(declared.what())) {
                    declaredAmount = Math.addExact(declaredAmount, declared.amount());
                }
            }
        } catch (ArithmeticException exception) {
            throw new DynamicCraftingOutputResolutionException(
                    "Encoded pattern output amount overflow for pattern " + details.getDefinition(),
                    exception);
        }
        if (declaredAmount < output.amount()) {
            throw new DynamicCraftingOutputResolutionException(
                    "Encoded pattern declares a selected output that is absent from its physical outputs: " +
                            details.getDefinition());
        }
        return new DynamicCraftingOutput(output, DynamicCraftingOutputMatchMode.SAME_ITEM);
    }
}
