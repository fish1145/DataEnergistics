package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructureSelection;

import net.minecraft.resources.ResourceLocation;

import com.modularmc.mdl.api.multiblock.RepeatRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable preview session selection that preserves independent state for every named substructure.
 */
public final class PreviewSelection {

    private final MultiblockPreviewSpec spec;
    private final ResourceLocation controllerId;
    private final String activeSubstructureId;
    private final long definitionRevision;
    private final Map<String, SubstructureSelection> substructureSelections;

    private PreviewSelection(MultiblockPreviewSpec spec,
                             ResourceLocation controllerId,
                             String activeSubstructureId,
                             long definitionRevision,
                             Map<String, SubstructureSelection> substructureSelections) {
        this.spec = spec;
        this.controllerId = controllerId;
        this.activeSubstructureId = activeSubstructureId;
        this.definitionRevision = definitionRevision;
        this.substructureSelections = immutableSelections(substructureSelections);
        validateAgainst(spec);
    }

    /**
     * Creates a new session using every substructure's validated defaults and activates the first declared entry.
     *
     * @param spec current revision-bound preview catalog
     * @return initial immutable session selection
     */
    public static PreviewSelection initial(MultiblockPreviewSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Preview selection requires a spec");
        }
        Map<String, SubstructureSelection> selections = new LinkedHashMap<>();
        for (SubstructurePreviewSpec substructure : spec.substructures()) {
            selections.put(substructure.id(), substructure.defaults());
        }
        return new PreviewSelection(
                spec,
                spec.controllerId(),
                spec.substructures().getFirst().id(),
                spec.definitionRevision(),
                selections);
    }

    /**
     * Returns the controller id this session was created for.
     */
    public ResourceLocation controllerId() {
        return this.controllerId;
    }

    /**
     * Returns the currently visible named substructure id.
     */
    public String activeSubstructureId() {
        return this.activeSubstructureId;
    }

    /**
     * Returns the stable controller-qualified key of the active named structure.
     */
    public JsonMultiBlockStructureKey activeStructureKey() {
        return this.spec.substructure(this.activeSubstructureId)
                .definition(activeSelection().variantIndex())
                .key();
    }

    /**
     * Returns the definition revision that must remain current for projection or transfer.
     */
    public long definitionRevision() {
        return this.definitionRevision;
    }

    /**
     * Returns all substructure choices in the declaration order of the bound spec.
     */
    public Map<String, SubstructureSelection> substructureSelections() {
        return this.substructureSelections;
    }

    /**
     * Returns the selection belonging to the currently active substructure.
     */
    public SubstructureSelection activeSelection() {
        return selection(this.activeSubstructureId);
    }

    /**
     * Returns one retained substructure selection by id.
     *
     * @param substructureId stable named structure id
     * @return retained immutable selection
     */
    public SubstructureSelection selection(String substructureId) {
        SubstructureSelection selection = this.substructureSelections.get(substructureId);
        if (selection == null) {
            throw new IllegalArgumentException("Unknown multiblock preview substructure: " + substructureId);
        }
        return selection;
    }

    /**
     * Activates another named substructure without resetting any retained choices.
     *
     * @param substructureId stable named structure id
     * @return updated immutable session selection
     */
    public PreviewSelection select(String substructureId) {
        this.spec.substructure(substructureId);
        return new PreviewSelection(
                this.spec,
                this.controllerId,
                substructureId,
                this.definitionRevision,
                this.substructureSelections);
    }

    /**
     * Changes the shape variant inside the active named structure. Repeat counts survive only where the same unit
     * index remains legal in the target variant; new or incompatible units use the target minimum. Candidate
     * overrides are shape-local and are therefore cleared, while tier selections remain unchanged.
     *
     * @param variantIndex zero-based variant index declared by the active structure
     * @return updated immutable session selection
     */
    public PreviewSelection withVariantIndex(int variantIndex) {
        SubstructurePreviewSpec substructure = this.spec.substructure(this.activeSubstructureId);
        SubstructureSelection active = activeSelection();
        if (active.variantIndex() == variantIndex) {
            return this;
        }
        List<RepeatRange> targetRanges = substructure.repeatRanges(variantIndex);
        List<Integer> targetRepeats = new ArrayList<>(targetRanges.size());
        for (int unitIndex = 0; unitIndex < targetRanges.size(); unitIndex++) {
            RepeatRange range = targetRanges.get(unitIndex);
            int repeatCount = unitIndex < active.repeatCounts().size() ? active.repeatCounts().get(unitIndex) : range.min();
            targetRepeats.add(repeatCount >= range.min() && repeatCount <= range.max() ? repeatCount : range.min());
        }
        SubstructureSelection updated = substructure.validateSelection(new SubstructureSelection(
                variantIndex,
                targetRepeats,
                active.tierSelections(),
                Map.of()));
        return withActiveSelection(updated);
    }

    /**
     * Changes one repeatable unit of the active substructure.
     *
     * @param unitIndex   MDLib pattern unit index
     * @param repeatCount selected repeat count
     * @return updated immutable session selection
     */
    public PreviewSelection withRepeat(int unitIndex, int repeatCount) {
        SubstructurePreviewSpec substructure = this.spec.substructure(this.activeSubstructureId);
        SubstructureSelection updated = substructure.validateSelection(
                activeSelection().withRepeat(unitIndex, repeatCount));
        return withActiveSelection(updated);
    }

    /**
     * Changes one tier category of the active substructure.
     *
     * @param domainId stable tier-domain id
     * @param value    selected tier value
     * @return updated immutable session selection
     */
    public PreviewSelection withTier(String domainId, int value) {
        SubstructurePreviewSpec substructure = this.spec.substructure(this.activeSubstructureId);
        SubstructureSelection updated = substructure.validateSelection(activeSelection().withTier(domainId, value));
        return withActiveSelection(updated);
    }

    /**
     * Changes one source predicate candidate of the active substructure.
     *
     * @param predicateKey   unexpanded source predicate coordinate
     * @param candidateIndex non-negative candidate index
     * @return updated immutable session selection
     */
    public PreviewSelection withCandidate(PreviewPredicateKey predicateKey, int candidateIndex) {
        SubstructurePreviewSpec substructure = this.spec.substructure(this.activeSubstructureId);
        SubstructureSelection updated = substructure.validateSelection(
                activeSelection().withCandidate(predicateKey, candidateIndex));
        return withActiveSelection(updated);
    }

    /**
     * Rejects use with a different controller, definition revision, substructure order, or selection domain.
     *
     * @param candidateSpec spec that intends to consume this selection
     */
    public void validateAgainst(MultiblockPreviewSpec candidateSpec) {
        if (candidateSpec == null) {
            throw new IllegalArgumentException("Preview selection requires a spec");
        }
        if (!this.controllerId.equals(candidateSpec.controllerId())) {
            throw new IllegalArgumentException("Preview selection controller does not match the supplied spec");
        }
        if (this.definitionRevision != candidateSpec.definitionRevision()) {
            throw new IllegalArgumentException("Preview selection definition revision does not match the supplied spec");
        }
        List<String> expectedIds = candidateSpec.substructures().stream()
                .map(SubstructurePreviewSpec::id)
                .toList();
        if (!List.copyOf(this.substructureSelections.keySet()).equals(expectedIds)) {
            throw new IllegalArgumentException("Preview selection substructures do not match the supplied spec");
        }
        candidateSpec.substructure(this.activeSubstructureId);
        for (SubstructurePreviewSpec substructure : candidateSpec.substructures()) {
            substructure.validateSelection(this.substructureSelections.get(substructure.id()));
        }
    }

    /**
     * Compares the complete revision-bound session state without depending on the bound spec instance identity.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreviewSelection selection)) {
            return false;
        }
        return this.definitionRevision == selection.definitionRevision &&
                this.controllerId.equals(selection.controllerId) &&
                this.activeSubstructureId.equals(selection.activeSubstructureId) &&
                this.substructureSelections.equals(selection.substructureSelections);
    }

    /**
     * Returns a hash of every field that participates in revision-bound selection equality.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                this.controllerId,
                this.activeSubstructureId,
                this.definitionRevision,
                this.substructureSelections);
    }

    /**
     * Returns a concise diagnostic representation of the immutable session state.
     */
    @Override
    public String toString() {
        return "PreviewSelection[controllerId=" + this.controllerId +
                ", activeSubstructureId=" + this.activeSubstructureId +
                ", definitionRevision=" + this.definitionRevision +
                ", substructureSelections=" + this.substructureSelections + "]";
    }

    private PreviewSelection withActiveSelection(SubstructureSelection selection) {
        Map<String, SubstructureSelection> updated = new LinkedHashMap<>(this.substructureSelections);
        updated.put(this.activeSubstructureId, selection);
        return new PreviewSelection(
                this.spec,
                this.controllerId,
                this.activeSubstructureId,
                this.definitionRevision,
                updated);
    }

    private static Map<String, SubstructureSelection> immutableSelections(
                                                                          Map<String, SubstructureSelection> selections) {
        Map<String, SubstructureSelection> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SubstructureSelection> entry : selections.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Preview substructure selections cannot contain null or blank entries");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
