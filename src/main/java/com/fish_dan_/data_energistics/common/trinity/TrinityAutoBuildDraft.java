package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructureSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import com.modularmc.mdl.api.multiblock.RepeatRange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Immutable Trinity auto-build editor state retaining independent choices for all three named structures.
 *
 * <p>
 * The draft delegates variant, repeat, tier, and candidate validation to the revision-bound preview specification.
 * Build enablement is the only additional UI choice and is retained separately for each structure.
 * </p>
 */
public final class TrinityAutoBuildDraft {

    private static final List<String> STRUCTURE_KEYS = List.of(
            JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME,
            ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
            ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME);

    private final MultiblockPreviewSpec spec;
    private final PreviewSelection previewSelection;
    private final Map<String, Boolean> buildRequestedByStructure;

    private TrinityAutoBuildDraft(MultiblockPreviewSpec spec,
                                  PreviewSelection previewSelection,
                                  Map<String, Boolean> buildRequestedByStructure) {
        if (spec == null || previewSelection == null || buildRequestedByStructure == null) {
            throw new IllegalArgumentException("Trinity auto-build draft arguments cannot be null");
        }
        validateTrinitySpec(spec);
        previewSelection.validateAgainst(spec);
        this.spec = spec;
        this.previewSelection = previewSelection;
        this.buildRequestedByStructure = copyBuildChoices(buildRequestedByStructure);
    }

    /**
     * Creates a fresh draft at the current definition revision.
     *
     * <p>
     * The main structure retains the legacy enabled default. CPU and crafting remain opt-in until selected.
     * </p>
     *
     * @param spec current Trinity preview specification
     * @return fresh revision-bound draft
     */
    public static TrinityAutoBuildDraft initial(MultiblockPreviewSpec spec) {
        validateTrinitySpec(spec);
        Map<String, Boolean> buildChoices = new LinkedHashMap<>();
        buildChoices.put(JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME, true);
        buildChoices.put(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME, false);
        buildChoices.put(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME, false);
        return new TrinityAutoBuildDraft(spec, PreviewSelection.initial(spec), buildChoices);
    }

    /** Returns the revision-bound structure metadata used by every draft update. */
    public MultiblockPreviewSpec spec() {
        return this.spec;
    }

    /** Returns the complete preview selection retained for all structures. */
    public PreviewSelection previewSelection() {
        return this.previewSelection;
    }

    /** Returns the stable Trinity structure order exposed by this draft. */
    public List<String> structureKeys() {
        return STRUCTURE_KEYS;
    }

    /** Returns whether the named structure is currently marked for construction. */
    public boolean buildRequested(String structureKey) {
        Boolean requested = this.buildRequestedByStructure.get(structureKey);
        if (requested == null) {
            throw new IllegalArgumentException("Unknown Trinity auto-build structure: " + structureKey);
        }
        return requested;
    }

    /** Returns whether the active structure is currently marked for construction. */
    public boolean activeBuildRequested() {
        return buildRequested(this.previewSelection.activeSubstructureId());
    }

    /** Activates another structure without resetting any structure-local choice. */
    public TrinityAutoBuildDraft select(String structureKey) {
        return withPreviewSelection(this.previewSelection.select(structureKey));
    }

    /** Changes the active structure's shape variant. */
    public TrinityAutoBuildDraft withVariantIndex(int variantIndex) {
        return withPreviewSelection(this.previewSelection.withVariantIndex(variantIndex));
    }

    /** Changes one repeatable unit of the active structure. */
    public TrinityAutoBuildDraft withRepeat(int unitIndex, int repeatCount) {
        return withPreviewSelection(this.previewSelection.withRepeat(unitIndex, repeatCount));
    }

    /** Changes the active structure's sole Trinity tier domain. */
    public TrinityAutoBuildDraft withTier(int tierValue) {
        PreviewTierDomain tierDomain = activeTierDomain();
        return withPreviewSelection(this.previewSelection.withTier(tierDomain.id(), tierValue));
    }

    /** Changes one source-predicate candidate of the active structure. */
    public TrinityAutoBuildDraft withCandidate(PreviewPredicateKey predicateKey, int candidateIndex) {
        return withPreviewSelection(this.previewSelection.withCandidate(predicateKey, candidateIndex));
    }

    /** Changes only the active structure's build enablement. */
    public TrinityAutoBuildDraft withBuildRequested(boolean buildRequested) {
        Map<String, Boolean> updated = new LinkedHashMap<>(this.buildRequestedByStructure);
        updated.put(this.previewSelection.activeSubstructureId(), buildRequested);
        return new TrinityAutoBuildDraft(this.spec, this.previewSelection, updated);
    }

    /** Returns the active structure's single Trinity tier domain. */
    public PreviewTierDomain activeTierDomain() {
        SubstructurePreviewSpec activeSpec = activeSpec();
        if (activeSpec.tierDomains().size() != 1) {
            throw new IllegalStateException("Trinity auto-build structure " + activeSpec.id() +
                    " must expose exactly one tier domain");
        }
        PreviewTierDomain domain = activeSpec.tierDomains().getFirst();
        String expectedCategory = TrinityAutoBuildBlockMap.categoryForStructure(
                structureIndex(activeSpec.id()));
        if (!expectedCategory.equals(domain.id())) {
            throw new IllegalStateException("Trinity auto-build structure " + activeSpec.id() +
                    " exposes tier domain " + domain.id() + ", expected " + expectedCategory);
        }
        return domain;
    }

    /** Returns the active structure's selected tier value. */
    public int activeTierValue() {
        return this.previewSelection.activeSelection().tierSelections().get(activeTierDomain().id());
    }

    /**
     * Finds the sole variable pattern unit used by the legacy one-count build planner.
     *
     * @return empty for the fixed main structure, otherwise the variable unit index
     */
    public OptionalInt activeVariableRepeatUnit() {
        SubstructureSelection activeSelection = this.previewSelection.activeSelection();
        List<RepeatRange> ranges = activeSpec().repeatRanges(activeSelection.variantIndex());
        int variableIndex = -1;
        for (int index = 0; index < ranges.size(); index++) {
            RepeatRange range = ranges.get(index);
            if (range.min() == range.max()) {
                continue;
            }
            if (variableIndex >= 0) {
                throw new IllegalStateException("Trinity auto-build structure " +
                        this.previewSelection.activeSubstructureId() + " exposes multiple variable repeat units");
            }
            variableIndex = index;
        }
        return variableIndex < 0 ? OptionalInt.empty() : OptionalInt.of(variableIndex);
    }

    /** Returns the active structure's build-planner repeat count. */
    public int activeRepeatCount() {
        OptionalInt variableUnit = activeVariableRepeatUnit();
        if (variableUnit.isEmpty()) {
            if (!JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME.equals(
                    this.previewSelection.activeSubstructureId())) {
                throw new IllegalStateException("Trinity child auto-build structure requires one variable repeat unit: " +
                        this.previewSelection.activeSubstructureId());
            }
            return TrinityAutoBuildOptions.MIN_REPEAT_COUNT;
        }
        if (JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME.equals(
                this.previewSelection.activeSubstructureId())) {
            throw new IllegalStateException("Trinity main auto-build structure cannot expose a variable repeat unit");
        }
        return this.previewSelection.activeSelection().repeatCounts().get(variableUnit.getAsInt());
    }

    /**
     * Creates the full revision-bound submission used by the hosted action protocol.
     *
     * @return immutable current submission without view-only layer or camera state
     */
    public TrinityAutoBuildSubmission submission() {
        return new TrinityAutoBuildSubmission(
                ProjectionFingerprint.from(this.previewSelection),
                activeBuildRequested());
    }

    /**
     * Converts the current single-variant/default-candidate state for the existing builder entry point.
     *
     * <p>
     * The conversion rejects fields the legacy request cannot represent instead of silently dropping them. The
     * generation-aware hosted payload retains {@link #submission()} as its transport source of truth.
     * </p>
     *
     * @return validated existing Trinity builder request
     */
    public TrinityAutoBuildRequest toLegacyRequest() {
        SubstructureSelection activeSelection = this.previewSelection.activeSelection();
        if (activeSelection.variantIndex() != 0) {
            throw new IllegalStateException("Legacy Trinity auto-build request cannot represent variant " +
                    activeSelection.variantIndex());
        }
        if (!activeSelection.candidateSelections().isEmpty()) {
            throw new IllegalStateException("Legacy Trinity auto-build request cannot represent candidate overrides");
        }
        int structureIndex = structureIndex(this.previewSelection.activeSubstructureId());
        String category = TrinityAutoBuildBlockMap.categoryForStructure(structureIndex);
        return new TrinityAutoBuildRequest(
                structureIndex,
                new TrinityAutoBuildOptions(
                        activeBuildRequested(),
                        activeRepeatCount(),
                        Map.of(category, activeTierValue())));
    }

    private TrinityAutoBuildDraft withPreviewSelection(PreviewSelection updated) {
        return new TrinityAutoBuildDraft(this.spec, updated, this.buildRequestedByStructure);
    }

    private SubstructurePreviewSpec activeSpec() {
        return this.spec.substructure(this.previewSelection.activeSubstructureId());
    }

    private static int structureIndex(String structureKey) {
        if (JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME.equals(structureKey)) {
            return TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX;
        }
        if (ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME.equals(structureKey)) {
            return TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX;
        }
        if (ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME.equals(structureKey)) {
            return TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX;
        }
        throw new IllegalArgumentException("Unknown Trinity auto-build structure: " + structureKey);
    }

    private static void validateTrinitySpec(MultiblockPreviewSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Trinity auto-build draft requires a preview spec");
        }
        if (!ModVerticalMultiBlocks.trinityDataCoreId().equals(spec.controllerId())) {
            throw new IllegalArgumentException("Trinity auto-build draft belongs to another controller: " +
                    spec.controllerId());
        }
        List<String> actualKeys = spec.substructures().stream().map(SubstructurePreviewSpec::id).toList();
        if (!STRUCTURE_KEYS.equals(actualKeys)) {
            throw new IllegalArgumentException("Trinity auto-build spec must expose main, cpu, and crafting in order");
        }
    }

    private static Map<String, Boolean> copyBuildChoices(Map<String, Boolean> choices) {
        if (!STRUCTURE_KEYS.equals(List.copyOf(choices.keySet()))) {
            throw new IllegalArgumentException("Trinity auto-build choices must match main, cpu, and crafting order");
        }
        Map<String, Boolean> copy = new LinkedHashMap<>();
        for (String structureKey : STRUCTURE_KEYS) {
            Boolean requested = choices.get(structureKey);
            if (requested == null) {
                throw new IllegalArgumentException("Missing Trinity auto-build choice for " + structureKey);
            }
            copy.put(structureKey, requested);
        }
        return Collections.unmodifiableMap(copy);
    }
}
