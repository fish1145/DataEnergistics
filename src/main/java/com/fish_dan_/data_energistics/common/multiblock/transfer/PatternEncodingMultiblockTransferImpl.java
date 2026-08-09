package com.fish_dan_.data_energistics.common.multiblock.transfer;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjection;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewProjectionImpl;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferState;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferTarget;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import appeng.api.stacks.GenericStack;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production transfer using the current atomic preview catalog and the common structure projector.
 */
public final class PatternEncodingMultiblockTransferImpl implements PatternEncodingMultiblockTransfer {

    private final MultiblockPreviewCatalog catalog;
    private final StructurePreviewProjection projection;

    /**
     * Creates the production transfer bound to the reload-aware global multiblock catalog.
     */
    public PatternEncodingMultiblockTransferImpl() {
        this(ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS, new StructurePreviewProjectionImpl());
    }

    /**
     * Creates an explicitly wired transfer so catalog reconstruction can be tested directly.
     *
     * @param catalog    authoritative preview catalog
     * @param projection common structure projection implementation
     */
    public PatternEncodingMultiblockTransferImpl(MultiblockPreviewCatalog catalog,
                                                 StructurePreviewProjection projection) {
        if (catalog == null || projection == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer dependencies cannot be null");
        }
        this.catalog = catalog;
        this.projection = projection;
    }

    /**
     * Reconstructs every recipe-affecting selection against one current server catalog generation.
     */
    @Override
    public MultiblockRecipeView resolveRecipe(MultiblockPatternTransferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer request cannot be null");
        }
        ProjectionFingerprint fingerprint = request.projectionFingerprint();
        MultiblockPreviewCatalogSnapshot catalogSnapshot = this.catalog.snapshot();
        if (fingerprint.definitionRevision() != catalogSnapshot.definitionRevision()) {
            throw new IllegalArgumentException("Multiblock pattern transfer definition revision is stale: " +
                    fingerprint.definitionRevision() + ", current " + catalogSnapshot.definitionRevision());
        }

        MultiblockPreviewSpec spec = catalogSnapshot.require(fingerprint.controllerId());
        if (!fingerprint.structureKey().machineId().equals(spec.controllerId())) {
            throw new IllegalArgumentException("Multiblock pattern transfer structure belongs to another controller");
        }
        if (!request.registeredRecipeId().equals(MultiblockRecipeView.registeredRecipeIdFor(spec.controllerId()))) {
            throw new IllegalArgumentException("Multiblock pattern transfer registered recipe id does not match its controller");
        }

        String structureName = fingerprint.structureKey().structureName();
        SubstructurePreviewSpec substructure = spec.substructure(structureName);
        JsonMultiBlockStructureKey currentStructureKey = substructure.definition(fingerprint.variantIndex()).key();
        if (!currentStructureKey.equals(fingerprint.structureKey())) {
            throw new IllegalArgumentException("Multiblock pattern transfer structure key does not match the current catalog");
        }

        PreviewSelection selection = PreviewSelection.initial(spec)
                .select(structureName)
                .withVariantIndex(fingerprint.variantIndex());
        if (fingerprint.repeatCounts().size() != substructure.repeatRanges(fingerprint.variantIndex()).size()) {
            throw new IllegalArgumentException("Multiblock pattern transfer repeat selections do not match " +
                    structureName);
        }
        for (int unitIndex = 0; unitIndex < fingerprint.repeatCounts().size(); unitIndex++) {
            selection = selection.withRepeat(unitIndex, fingerprint.repeatCounts().get(unitIndex));
        }

        List<String> tierDomains = substructure.tierDomains().stream()
                .map(domain -> domain.id())
                .toList();
        if (fingerprint.tierSelections().size() != tierDomains.size() ||
                !fingerprint.tierSelections().keySet().containsAll(tierDomains)) {
            throw new IllegalArgumentException("Multiblock pattern transfer tier selections do not match " +
                    structureName);
        }
        for (Map.Entry<String, Integer> tier : fingerprint.tierSelections().entrySet()) {
            selection = selection.withTier(tier.getKey(), tier.getValue());
        }
        for (Map.Entry<PreviewPredicateKey, Integer> candidate : fingerprint.candidateSelections().entrySet()) {
            selection = selection.withCandidate(candidate.getKey(), candidate.getValue());
        }

        StructurePreviewSnapshot projected = this.projection.project(spec, selection);
        MultiblockRecipeView recipe = MultiblockRecipeView.from(spec, projected);
        if (!recipe.registeredRecipeId().equals(request.registeredRecipeId()) ||
                !recipe.projectionFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("Multiblock pattern transfer projection did not reconstruct exactly");
        }
        return recipe;
    }

    /**
     * Resolves current materials before beginning any mutation of the menu-owned inventories.
     */
    @Override
    public void transfer(MultiblockPatternTransferRequest request,
                         PatternEncodingMultiblockTransferTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer target cannot be null");
        }
        applyRecipe(target, resolveRecipe(request));
    }

    /**
     * Atomically applies an already authoritative view. Package visibility keeps direct transaction tests on the
     * production path without exposing a second public transfer entry point.
     */
    void applyRecipe(PatternEncodingMultiblockTransferTarget target, MultiblockRecipeView recipe) {
        if (target == null || recipe == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer application arguments cannot be null");
        }
        ConfigInventory inputInventory = target.data_energistics$getMultiblockTransferInputInventory();
        ConfigInventory outputInventory = target.data_energistics$getMultiblockTransferOutputInventory();
        EncodingMode originalMode = target.data_energistics$getMultiblockTransferEncodingMode();
        if (inputInventory == null || outputInventory == null || originalMode == null) {
            throw new IllegalStateException("Multiblock pattern transfer target returned null encoding state");
        }

        List<PreviewMaterial> inputs = recipe.inputs();
        List<PreviewMaterial> outputs = List.of(recipe.output());
        validateCapacityAndStacks("input", inputInventory, inputs);
        validateCapacityAndStacks("output", outputInventory, outputs);

        PatternEncodingMultiblockTransferState originalTransferState = target.data_energistics$snapshotMultiblockTransferState();
        if (originalTransferState == null) {
            throw new IllegalStateException("Multiblock pattern transfer target returned null source state");
        }
        GenericStack[] originalInputs = snapshot(inputInventory);
        GenericStack[] originalOutputs = snapshot(outputInventory);
        boolean inputBatchOpen = false;
        boolean outputBatchOpen = false;
        boolean publicationAttempted = false;
        try {
            inputInventory.beginBatch();
            inputBatchOpen = true;
            outputInventory.beginBatch();
            outputBatchOpen = true;

            writeAll(inputInventory, inputs);
            writeAll(outputInventory, outputs);
            verifyAll("input", inputInventory, inputs);
            verifyAll("output", outputInventory, outputs);

            if (originalMode != EncodingMode.PROCESSING) {
                publicationAttempted = true;
                target.data_energistics$setMultiblockTransferEncodingMode(EncodingMode.PROCESSING);
                if (target.data_energistics$getMultiblockTransferEncodingMode() != EncodingMode.PROCESSING) {
                    throw new IllegalStateException("Pattern encoding target rejected processing mode");
                }
            }

            publicationAttempted = true;
            target.data_energistics$clearMultiblockTransferState();
            PatternEncodingMultiblockTransferState clearedTransferState = target.data_energistics$snapshotMultiblockTransferState();
            if (clearedTransferState == null || !clearedTransferState.isClear()) {
                throw new IllegalStateException("Pattern encoding source state did not clear for multiblock transfer");
            }

            inputBatchOpen = false;
            inputInventory.endBatch();
            outputBatchOpen = false;
            outputInventory.endBatch();
        } catch (RuntimeException | Error failure) {
            boolean rollbackIncomplete = rollback(
                    target,
                    inputInventory,
                    outputInventory,
                    originalMode,
                    originalTransferState,
                    originalInputs,
                    originalOutputs,
                    inputBatchOpen,
                    outputBatchOpen,
                    publicationAttempted,
                    failure);
            if (rollbackIncomplete) {
                try {
                    target.data_energistics$invalidateMultiblockTransferTarget();
                } catch (RuntimeException | Error invalidationFailure) {
                    failure.addSuppressed(invalidationFailure);
                }
            }
            throw failure;
        }
    }

    private static void validateCapacityAndStacks(String role,
                                                  ConfigInventory inventory,
                                                  List<PreviewMaterial> materials) {
        if (materials.size() > inventory.size()) {
            throw new IllegalArgumentException("Multiblock pattern transfer requires " + materials.size() + " " +
                    role + " slots, target exposes " + inventory.size());
        }
        for (int slot = 0; slot < materials.size(); slot++) {
            PreviewMaterial material = materials.get(slot);
            if (material == null || material.key() == null || material.amount() <= 0L) {
                throw new IllegalArgumentException("Invalid multiblock pattern transfer " + role + " at slot " +
                        slot);
            }
            if (!inventory.isAllowedIn(slot, material.key())) {
                throw new IllegalArgumentException("Multiblock pattern transfer " + role + " key is not allowed at slot " +
                        slot + ": " + material.key());
            }
            long maximum = inventory.getMaxAmount(material.key());
            if (maximum <= 0L || material.amount() > maximum) {
                throw new IllegalArgumentException("Multiblock pattern transfer " + role + " amount " +
                        material.amount() + " exceeds slot " + slot + " maximum " + maximum);
            }
        }
    }

    private static GenericStack[] snapshot(ConfigInventory inventory) {
        GenericStack[] snapshot = new GenericStack[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            snapshot[slot] = inventory.getStack(slot);
        }
        return snapshot;
    }

    private static void writeAll(ConfigInventory inventory, List<PreviewMaterial> materials) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, expectedStack(materials, slot));
        }
    }

    private static void verifyAll(String role,
                                  ConfigInventory inventory,
                                  List<PreviewMaterial> materials) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            GenericStack expected = expectedStack(materials, slot);
            GenericStack actual = inventory.getStack(slot);
            if (!sameStack(expected, actual)) {
                throw new IllegalStateException("Pattern encoding " + role + " slot " + slot +
                        " rejected or changed transferred stack: expected " + expected + ", got " + actual);
            }
        }
    }

    private static GenericStack expectedStack(List<PreviewMaterial> materials, int slot) {
        if (slot >= materials.size()) {
            return null;
        }
        PreviewMaterial material = materials.get(slot);
        return new GenericStack(material.key(), material.amount());
    }

    private static boolean rollback(PatternEncodingMultiblockTransferTarget target,
                                    ConfigInventory inputInventory,
                                    ConfigInventory outputInventory,
                                    EncodingMode originalMode,
                                    PatternEncodingMultiblockTransferState originalTransferState,
                                    GenericStack[] originalInputs,
                                    GenericStack[] originalOutputs,
                                    boolean inputBatchOpen,
                                    boolean outputBatchOpen,
                                    boolean publicationAttempted,
                                    Throwable primaryFailure) {
        List<Throwable> rollbackFailures = new ArrayList<>();
        boolean inputRollbackBatch = ensureRollbackBatch(inputInventory, inputBatchOpen, rollbackFailures);
        boolean outputRollbackBatch = ensureRollbackBatch(outputInventory, outputBatchOpen, rollbackFailures);

        try {
            if (target.data_energistics$getMultiblockTransferEncodingMode() != originalMode) {
                target.data_energistics$setMultiblockTransferEncodingMode(originalMode);
            }
            if (target.data_energistics$getMultiblockTransferEncodingMode() != originalMode) {
                rollbackFailures.add(new IllegalStateException("Pattern encoding mode did not roll back to " +
                        originalMode));
            }
        } catch (RuntimeException | Error failure) {
            rollbackFailures.add(failure);
        }
        restoreInventory("input", inputInventory, originalInputs, rollbackFailures);
        restoreInventory("output", outputInventory, originalOutputs, rollbackFailures);
        restoreTransferState(target, originalTransferState, rollbackFailures);

        verifySnapshot("input", inputInventory, originalInputs, rollbackFailures);
        verifySnapshot("output", outputInventory, originalOutputs, rollbackFailures);
        endRollbackBatch(inputInventory, inputRollbackBatch, publicationAttempted, rollbackFailures);
        endRollbackBatch(outputInventory, outputRollbackBatch, publicationAttempted, rollbackFailures);

        for (Throwable rollbackFailure : rollbackFailures) {
            primaryFailure.addSuppressed(rollbackFailure);
        }
        return !rollbackFailures.isEmpty();
    }

    private static void restoreTransferState(PatternEncodingMultiblockTransferTarget target,
                                             PatternEncodingMultiblockTransferState originalState,
                                             List<Throwable> failures) {
        try {
            target.data_energistics$restoreMultiblockTransferState(originalState);
        } catch (RuntimeException | Error failure) {
            failures.add(new IllegalStateException("Failed to restore pattern encoding source state", failure));
        }
        try {
            PatternEncodingMultiblockTransferState restored = target.data_energistics$snapshotMultiblockTransferState();
            if (!originalState.equals(restored)) {
                failures.add(new IllegalStateException("Pattern encoding source state did not match its snapshot"));
            }
        } catch (RuntimeException | Error failure) {
            failures.add(new IllegalStateException("Failed to verify restored pattern encoding source state", failure));
        }
    }

    private static boolean ensureRollbackBatch(ConfigInventory inventory,
                                               boolean batchAlreadyOpen,
                                               List<Throwable> failures) {
        if (batchAlreadyOpen) {
            return true;
        }
        try {
            inventory.beginBatch();
            return true;
        } catch (RuntimeException | Error failure) {
            failures.add(failure);
            return false;
        }
    }

    private static void restoreInventory(String role,
                                         ConfigInventory inventory,
                                         GenericStack[] snapshot,
                                         List<Throwable> failures) {
        for (int slot = 0; slot < snapshot.length; slot++) {
            try {
                if (!sameStack(snapshot[slot], inventory.getStack(slot))) {
                    inventory.setStack(slot, snapshot[slot]);
                }
            } catch (RuntimeException | Error failure) {
                failures.add(new IllegalStateException("Failed to restore pattern encoding " + role + " slot " +
                        slot, failure));
            }
        }
    }

    private static void verifySnapshot(String role,
                                       ConfigInventory inventory,
                                       GenericStack[] snapshot,
                                       List<Throwable> failures) {
        for (int slot = 0; slot < snapshot.length; slot++) {
            try {
                GenericStack actual = inventory.getStack(slot);
                if (!sameStack(snapshot[slot], actual)) {
                    failures.add(new IllegalStateException("Pattern encoding " + role + " slot " + slot +
                            " was not restored: expected " + snapshot[slot] + ", got " + actual));
                }
            } catch (RuntimeException | Error failure) {
                failures.add(new IllegalStateException("Failed to verify restored pattern encoding " + role +
                        " slot " + slot, failure));
            }
        }
    }

    private static void endRollbackBatch(ConfigInventory inventory,
                                         boolean batchOpen,
                                         boolean publicationAttempted,
                                         List<Throwable> failures) {
        if (!batchOpen) {
            return;
        }
        try {
            if (publicationAttempted) {
                inventory.endBatch();
            } else {
                inventory.endBatchSuppressed();
            }
        } catch (RuntimeException | Error failure) {
            failures.add(failure);
        }
    }

    private static boolean sameStack(GenericStack expected, GenericStack actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }
}
