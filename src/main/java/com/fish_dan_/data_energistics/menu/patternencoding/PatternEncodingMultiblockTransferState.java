package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of pattern-source state that must not leak into a generic multiblock Processing pattern.
 *
 * @param pendingPatternSource         workstation selected for the next encode
 * @param lastEncodedPatternSource     workstation remembered from the previous encode
 * @param pendingKeyInput              Data Ripper key input awaiting encode
 * @param pendingKeyOutput             Data Ripper key output awaiting encode
 * @param pendingFluidInputs           Data Ripper fluid inputs awaiting encode
 * @param pendingFluidOutputs          Data Ripper fluid outputs awaiting encode
 * @param displayedKeyInputSerialized  server menu fallback for a displayed key input
 * @param displayedKeyOutputSerialized server menu fallback for a displayed key output
 */
public record PatternEncodingMultiblockTransferState(
                                                     @Nullable ResourceLocation pendingPatternSource,
                                                     @Nullable ResourceLocation lastEncodedPatternSource,
                                                     @Nullable GenericStack pendingKeyInput,
                                                     @Nullable GenericStack pendingKeyOutput,
                                                     List<GenericStack> pendingFluidInputs,
                                                     List<GenericStack> pendingFluidOutputs,
                                                     @Nullable String displayedKeyInputSerialized,
                                                     @Nullable String displayedKeyOutputSerialized) {

    private static final PatternEncodingMultiblockTransferState CLEAR = new PatternEncodingMultiblockTransferState(
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            null);

    /**
     * Defensively copies every GenericStack collection used by rollback.
     */
    public PatternEncodingMultiblockTransferState {
        pendingKeyInput = copyStack("pending key input", pendingKeyInput);
        pendingKeyOutput = copyStack("pending key output", pendingKeyOutput);
        pendingFluidInputs = copyStacks("pending fluid inputs", pendingFluidInputs);
        pendingFluidOutputs = copyStacks("pending fluid outputs", pendingFluidOutputs);
    }

    /**
     * Returns the canonical state required before a generic multiblock recipe can be encoded.
     */
    public static PatternEncodingMultiblockTransferState cleared() {
        return CLEAR;
    }

    /**
     * Reports whether every source-specific field has been cleared.
     */
    public boolean isClear() {
        return equals(CLEAR);
    }

    @Nullable
    private static GenericStack copyStack(String role, @Nullable GenericStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.what() == null || stack.amount() <= 0L) {
            throw new IllegalArgumentException("Invalid pattern encoding " + role + ": " + stack);
        }
        return new GenericStack(stack.what(), stack.amount());
    }

    private static List<GenericStack> copyStacks(String role, List<GenericStack> stacks) {
        if (stacks == null) {
            throw new IllegalArgumentException("Pattern encoding " + role + " cannot be null");
        }
        List<GenericStack> copies = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            GenericStack copy = copyStack(role, stack);
            if (copy == null) {
                throw new IllegalArgumentException("Pattern encoding " + role + " cannot contain null stacks");
            }
            copies.add(copy);
        }
        return List.copyOf(copies);
    }
}
