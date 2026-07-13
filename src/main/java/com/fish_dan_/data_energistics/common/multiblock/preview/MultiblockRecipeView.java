package com.fish_dan_.data_energistics.common.multiblock.preview;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordinary dynamic XEI recipe view containing only material inputs and one controller or owner output.
 *
 * @param recipeId           stable controller-level wrapper id
 * @param controllerId       owning multiblock controller id
 * @param substructureId     active substructure for visible diagnostics only
 * @param definitionRevision generation used to compute the inputs
 * @param inputs             selected aggregate material inputs
 * @param output             controller or owner output with amount one
 */
public record MultiblockRecipeView(ResourceLocation recipeId,
                                   ResourceLocation controllerId,
                                   String substructureId,
                                   long definitionRevision,
                                   List<PreviewMaterial> inputs,
                                   PreviewMaterial output) {

    /**
     * Copies inputs and rejects state that cannot form a current ordinary recipe view.
     */
    public MultiblockRecipeView {
        if (recipeId == null || controllerId == null || substructureId == null || substructureId.isBlank() ||
                inputs == null || output == null) {
            throw new IllegalArgumentException("Multiblock recipe view arguments cannot be null or blank");
        }
        if (definitionRevision < 0L) {
            throw new IllegalArgumentException("Multiblock recipe revision cannot be negative: " + definitionRevision);
        }
        inputs = List.copyOf(inputs);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Multiblock recipe view requires at least one material input");
        }
        Set<AEItemKey> inputKeys = new HashSet<>();
        for (PreviewMaterial input : inputs) {
            if (!inputKeys.add(input.key())) {
                throw new IllegalArgumentException("Multiblock recipe view contains a duplicate material input key");
            }
        }
        if (output.amount() != 1L) {
            throw new IllegalArgumentException("Multiblock recipe owner output amount must be one");
        }
    }

    /**
     * Maps one current snapshot without serializing tier, repeat, layer, orientation, or block states.
     *
     * @param spec     revision-bound preview definition
     * @param snapshot current projected snapshot
     * @return ordinary material-input/controller-output recipe view
     */
    public static MultiblockRecipeView from(MultiblockPreviewSpec spec, StructurePreviewSnapshot snapshot) {
        if (spec == null || snapshot == null) {
            throw new IllegalArgumentException("Multiblock recipe mapping arguments cannot be null");
        }
        snapshot.selection().validateAgainst(spec);
        if (!snapshot.definitionKey().structureName().equals(snapshot.selection().activeSubstructureId())) {
            throw new IllegalArgumentException("Multiblock recipe snapshot is not the active substructure");
        }
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                spec.controllerId().getNamespace(),
                "multiblock/" + spec.controllerId().getPath());
        return new MultiblockRecipeView(
                recipeId,
                spec.controllerId(),
                snapshot.definitionKey().structureName(),
                snapshot.definitionRevision(),
                snapshot.materials(),
                new PreviewMaterial(spec.ownerOutput(), 1L));
    }
}
