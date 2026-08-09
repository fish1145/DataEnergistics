package com.fish_dan_.data_energistics.common.multiblock.preview.catalog;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.item.order.OrderPackageTarget;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordinary dynamic XEI recipe view containing structure and controller inputs plus one encoded result output.
 *
 * @param registeredRecipeId    stable controller-level wrapper id
 * @param controllerId          owning multiblock controller id
 * @param substructureId        active substructure for visible diagnostics only
 * @param definitionRevision    generation used to compute the inputs
 * @param projectionFingerprint dynamic identity of every recipe-affecting preview choice
 * @param inputs                selected aggregate material inputs including the controller
 * @param output                marked order-package output with amount one
 */
public record MultiblockRecipeView(ResourceLocation registeredRecipeId,
                                   ResourceLocation controllerId,
                                   String substructureId,
                                   long definitionRevision,
                                   ProjectionFingerprint projectionFingerprint,
                                   List<PreviewMaterial> inputs,
                                   PreviewMaterial output) {

    /**
     * Copies inputs and rejects state that cannot form a current ordinary recipe view.
     */
    public MultiblockRecipeView {
        if (registeredRecipeId == null || controllerId == null || substructureId == null || substructureId.isBlank() ||
                projectionFingerprint == null || inputs == null || output == null) {
            throw new IllegalArgumentException("Multiblock recipe view arguments cannot be null or blank");
        }
        if (definitionRevision < 0L) {
            throw new IllegalArgumentException("Multiblock recipe revision cannot be negative: " + definitionRevision);
        }
        ResourceLocation expectedRecipeId = registeredRecipeIdFor(controllerId);
        if (!registeredRecipeId.equals(expectedRecipeId)) {
            throw new IllegalArgumentException("Multiblock registered recipe id must be controller-level: expected " +
                    expectedRecipeId + ", got " + registeredRecipeId);
        }
        if (!projectionFingerprint.controllerId().equals(controllerId) ||
                projectionFingerprint.definitionRevision() != definitionRevision ||
                !projectionFingerprint.structureKey().structureName().equals(substructureId)) {
            throw new IllegalArgumentException("Multiblock recipe projection fingerprint does not match its view");
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
            throw new IllegalArgumentException("Multiblock recipe output amount must be one");
        }
    }

    /**
     * Returns the controller-qualified active structure identity.
     */
    public JsonMultiBlockStructureKey structureKey() {
        return this.projectionFingerprint.structureKey();
    }

    /**
     * Maps one current snapshot without serializing tier, repeat, layer, orientation, or block states.
     *
     * @param spec     revision-bound preview definition
     * @param snapshot current projected snapshot
     * @return ordinary structure-and-controller input recipe view
     */
    public static MultiblockRecipeView from(MultiblockPreviewSpec spec, StructurePreviewSnapshot snapshot) {
        if (spec == null || snapshot == null) {
            throw new IllegalArgumentException("Multiblock recipe mapping arguments cannot be null");
        }
        snapshot.selection().validateAgainst(spec);
        if (!snapshot.definitionKey().structureName().equals(snapshot.selection().activeSubstructureId())) {
            throw new IllegalArgumentException("Multiblock recipe snapshot is not the active substructure");
        }
        return new MultiblockRecipeView(
                registeredRecipeIdFor(spec.controllerId()),
                spec.controllerId(),
                snapshot.definitionKey().structureName(),
                snapshot.definitionRevision(),
                ProjectionFingerprint.from(snapshot.selection()),
                mergeControllerInput(snapshot.materials(), spec.ownerOutput()),
                markedOrderPackage(spec.ownerOutput()));
    }

    private static List<PreviewMaterial> mergeControllerInput(List<PreviewMaterial> materials,
                                                              AEItemKey controller) {
        Map<AEItemKey, Long> amounts = new LinkedHashMap<>();
        for (PreviewMaterial material : materials) {
            amounts.merge(material.key(), material.amount(), Math::addExact);
        }
        amounts.compute(controller, (unused, current) -> current == null ? 1L : Math.addExact(current, 1L));
        return amounts.entrySet().stream()
                .map(entry -> new PreviewMaterial(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static PreviewMaterial markedOrderPackage(AEItemKey controller) {
        ItemStack packageStack = OrderPackageTarget.get().createMarkedPackage(controller);
        AEItemKey packageKey = AEItemKey.of(packageStack);
        if (packageKey == null) {
            throw new IllegalStateException("Registered order package did not produce an AE item key");
        }
        return new PreviewMaterial(packageKey, 1L);
    }

    /**
     * Derives the one stable XEI registration id owned by a controller.
     *
     * @param controllerId controller-level preview identity
     * @return stable recipe registration id independent of page selection
     */
    public static ResourceLocation registeredRecipeIdFor(ResourceLocation controllerId) {
        if (controllerId == null) {
            throw new IllegalArgumentException("Multiblock registered recipe id requires a controller");
        }
        return ResourceLocation.fromNamespaceAndPath(
                controllerId.getNamespace(),
                "multiblock/" + controllerId.getPath());
    }
}
