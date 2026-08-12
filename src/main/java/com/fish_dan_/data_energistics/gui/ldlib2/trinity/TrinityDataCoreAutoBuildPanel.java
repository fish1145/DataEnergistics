package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewVisibleLayer;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructureSelection;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUi;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/**
 * Binds one automatic-build draft to the controls authored in the hosted-window NBT.
 */
final class TrinityDataCoreAutoBuildPanel {

    static final String PANEL_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_controls";
    static final String STRUCTURE_SELECTOR_ID = PANEL_ID + "_structure_selector";
    static final String STRUCTURE_PREVIOUS_ID = PANEL_ID + "_structure_previous";
    static final String STRUCTURE_TITLE_ID = PANEL_ID + "_structure_title";
    static final String STRUCTURE_NEXT_ID = PANEL_ID + "_structure_next";
    static final String CONTEXT_PREVIOUS_ID = PANEL_ID + "_context_previous";
    static final String CONTEXT_TITLE_ID = PANEL_ID + "_context_title";
    static final String CONTEXT_VALUE_ID = PANEL_ID + "_context_value";
    static final String CONTEXT_NEXT_ID = PANEL_ID + "_context_next";
    static final String VALUE_PREVIOUS_ID = PANEL_ID + "_value_previous";
    static final String VALUE_TITLE_ID = PANEL_ID + "_value_title";
    static final String VALUE_VALUE_ID = PANEL_ID + "_value_value";
    static final String VALUE_NEXT_ID = PANEL_ID + "_value_next";
    static final String CONFIRM_BUTTON_ID = PANEL_ID + "_confirm";

    private static final String PREVIEW_TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final String AUTO_BUILD_TRANSLATION_PREFIX = "screen.data_energistics.trinity_data_core.auto_build.adjustment.";

    private final StructurePreviewUi preview;
    private final HostSubUiContext context;
    private final BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction;
    private final LongPredicate hostedAutoBuildPending;
    private final TrinityAutoBuildMaterialGrid materialGrid;
    private final LayerScrollerBinding layerScroller;
    private final Button previousStructureButton;
    private final Button nextStructureButton;
    private final Button previousContextButton;
    private final Button nextContextButton;
    private final Button previousValueButton;
    private final Button nextValueButton;
    private final Button confirmButton;
    private final Label structureTitleLabel;
    private final Label contextValueLabel;
    private final Label valueValueLabel;
    private TrinityAutoBuildDraft draft;
    private List<AdjustmentContext> adjustmentContexts = List.of();
    private int adjustmentContextIndex;

    static Layout requireLayout(@NotNull UIElement root) {
        TrinityUiXmlLayouts.require(root, STRUCTURE_SELECTOR_ID, UIElement.class);
        return new Layout(
                root,
                TrinityUiXmlLayouts.require(root, STRUCTURE_PREVIOUS_ID, Button.class),
                TrinityUiXmlLayouts.require(root, STRUCTURE_TITLE_ID, Label.class),
                TrinityUiXmlLayouts.require(root, STRUCTURE_NEXT_ID, Button.class),
                TrinityUiXmlLayouts.require(root, CONTEXT_PREVIOUS_ID, Button.class),
                TrinityUiXmlLayouts.require(root, CONTEXT_TITLE_ID, Label.class),
                TrinityUiXmlLayouts.require(root, CONTEXT_VALUE_ID, Label.class),
                TrinityUiXmlLayouts.require(root, CONTEXT_NEXT_ID, Button.class),
                TrinityUiXmlLayouts.require(root, VALUE_PREVIOUS_ID, Button.class),
                TrinityUiXmlLayouts.require(root, VALUE_TITLE_ID, Label.class),
                TrinityUiXmlLayouts.require(root, VALUE_VALUE_ID, Label.class),
                TrinityUiXmlLayouts.require(root, VALUE_NEXT_ID, Button.class),
                TrinityUiXmlLayouts.require(root, CONFIRM_BUTTON_ID, Button.class));
    }

    TrinityDataCoreAutoBuildPanel(@NotNull Layout layout,
                                  @NotNull StructurePreviewUi preview,
                                  @NotNull TrinityAutoBuildDraft draft,
                                  @NotNull HostSubUiContext context,
                                  @NotNull BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                  @NotNull LongPredicate hostedAutoBuildPending,
                                  @NotNull TrinityAutoBuildMaterialGrid materialGrid,
                                  @NotNull Scroller.Horizontal layerScroller) {
        validateSupportedSelection(draft.spec(), draft.previewSelection());
        if (!preview.session().selection().equals(draft.previewSelection())) {
            throw new IllegalArgumentException("Trinity automatic-build draft and preview must start synchronized");
        }
        if (!preview.session().allowedStructureKeys().equals(draft.structureKeys())) {
            throw new IllegalArgumentException("Trinity automatic-build preview must allow all draft structures");
        }

        this.preview = preview;
        this.draft = draft;
        this.context = context;
        this.hostedAutoBuildAction = hostedAutoBuildAction;
        this.hostedAutoBuildPending = hostedAutoBuildPending;
        this.materialGrid = materialGrid;
        this.layerScroller = new LayerScrollerBinding(preview, layerScroller);
        this.previousStructureButton = layout.previousStructureButton();
        this.nextStructureButton = layout.nextStructureButton();
        this.previousContextButton = layout.previousContextButton();
        this.nextContextButton = layout.nextContextButton();
        this.previousValueButton = layout.previousValueButton();
        this.nextValueButton = layout.nextValueButton();
        this.confirmButton = layout.confirmButton();
        this.structureTitleLabel = layout.structureTitleLabel();
        this.contextValueLabel = layout.contextValueLabel();
        this.valueValueLabel = layout.valueValueLabel();

        configureButton(this.previousStructureButton, () -> selectRelativeStructure(-1));
        configureButton(this.nextStructureButton, () -> selectRelativeStructure(1));
        configureButton(this.previousContextButton, () -> selectRelativeContext(-1));
        configureButton(this.nextContextButton, () -> selectRelativeContext(1));
        configureButton(this.previousValueButton, () -> adjustValue(false));
        configureButton(this.nextValueButton, () -> adjustValue(true));
        configureButton(this.confirmButton, this::submit);
        layout.contextTitleLabel().setText(Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "context"));
        layout.valueTitleLabel().setText(Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "value"));
        this.preview.panel().setSelectionChangeListener(this::replacePreviewSelection);
        layout.root().addEventListener(UIEvents.TICK, ignored -> screenTick());
        refreshBoundState(null);
        screenTick();
    }

    private void screenTick() {
        long generation = this.context.generation();
        this.confirmButton.setActive(
                this.context.canSendServerAction() && !this.hostedAutoBuildPending.test(generation));
    }

    private void selectRelativeStructure(int direction) {
        this.preview.panel().selectStructure(relativeStructureKey(direction));
    }

    private void selectRelativeContext(int direction) {
        if (this.adjustmentContexts.size() < 2) {
            return;
        }
        this.adjustmentContextIndex = Math.floorMod(
                this.adjustmentContextIndex + direction,
                this.adjustmentContexts.size());
        refreshAdjustmentLabels();
    }

    private void adjustValue(boolean next) {
        if (this.adjustmentContexts.isEmpty()) {
            return;
        }
        AdjustmentContext active = this.adjustmentContexts.get(this.adjustmentContextIndex);
        if (!active.adjustable().getAsBoolean()) {
            return;
        }
        if (next) {
            active.next().run();
        } else {
            active.previous().run();
        }
    }

    private void replacePreviewSelection(PreviewSelection selection) {
        String retainedContextId = activeAdjustmentContextId();
        validateSupportedSelection(this.draft.spec(), selection);
        TrinityAutoBuildDraft updated = this.draft;
        for (String structureKey : updated.structureKeys()) {
            SubstructureSelection structureSelection = selection.selection(structureKey);
            updated = updated.select(structureKey)
                    .withVariantIndex(structureSelection.variantIndex());
            String tierDomainId = updated.activeTierDomain().id();
            Integer tierValue = structureSelection.tierSelections().get(tierDomainId);
            if (tierValue == null) {
                throw new IllegalStateException("Trinity automatic-build selection lacks tier domain " + tierDomainId);
            }
            updated = updated.withTier(tierValue);
            for (int unitIndex = 0; unitIndex < structureSelection.repeatCounts().size(); unitIndex++) {
                updated = updated.withRepeat(unitIndex, structureSelection.repeatCounts().get(unitIndex));
            }
            for (var candidate : structureSelection.candidateSelections().entrySet()) {
                updated = updated.withCandidate(candidate.getKey(), candidate.getValue());
            }
        }
        updated = updated.select(selection.activeSubstructureId());
        if (!updated.previewSelection().equals(selection)) {
            throw new IllegalStateException("Trinity automatic-build draft could not reproduce its preview selection");
        }
        this.draft = updated;
        refreshBoundState(retainedContextId);
    }

    private void submit() {
        long generation = this.context.generation();
        if (!this.context.canSendServerAction() || this.hostedAutoBuildPending.test(generation)) {
            return;
        }
        if (!this.draft.previewSelection().equals(this.preview.session().selection())) {
            throw new IllegalStateException("Trinity automatic-build draft diverged from its preview session");
        }
        validateSupportedSelection(this.draft.spec(), this.draft.previewSelection());
        TrinityAutoBuildDraft submittedDraft = this.draft.withBuildRequested(true);
        this.hostedAutoBuildAction.accept(generation, submittedDraft.submission());
        this.draft = submittedDraft;
    }

    private void refreshBoundState(@Nullable String retainedContextId) {
        this.structureTitleLabel.setText(structureTitle(this.draft.previewSelection().activeSubstructureId()));
        this.materialGrid.setMaterials(this.preview.session().recipeView().inputs());
        this.layerScroller.refresh();
        rebuildAdjustmentContexts(retainedContextId);
        refreshStructureNavigationTooltips();
    }

    private void rebuildAdjustmentContexts(@Nullable String retainedContextId) {
        List<AdjustmentContext> contexts = new ArrayList<>();
        PreviewTierDomain tierDomain = this.draft.activeTierDomain();
        contexts.add(new AdjustmentContext(
                "tier:" + tierDomain.id(),
                tierDomain::label,
                () -> tierDomain.option(this.draft.activeTierValue()).label(),
                this.preview.panel()::previousTier,
                this.preview.panel()::nextTier,
                () -> tierDomain.options().size() > 1));

        OptionalInt repeatUnit = this.draft.activeVariableRepeatUnit();
        if (repeatUnit.isPresent()) {
            int unitIndex = repeatUnit.getAsInt();
            contexts.add(new AdjustmentContext(
                    "repeat:" + unitIndex,
                    () -> Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "repeat"),
                    () -> Component.literal(Integer.toString(this.draft.activeRepeatCount())),
                    () -> this.preview.panel().previousRepeat(unitIndex),
                    () -> this.preview.panel().nextRepeat(unitIndex),
                    () -> true));
        }

        this.adjustmentContexts = List.copyOf(contexts);
        this.adjustmentContextIndex = indexOfContext(retainedContextId);
        refreshAdjustmentLabels();
    }

    private void refreshAdjustmentLabels() {
        boolean hasMultipleContexts = this.adjustmentContexts.size() > 1;
        this.previousContextButton.setActive(hasMultipleContexts);
        this.nextContextButton.setActive(hasMultipleContexts);
        if (this.adjustmentContexts.isEmpty()) {
            this.contextValueLabel.setText(Component.empty());
            this.valueValueLabel.setText(Component.empty());
            this.previousValueButton.setActive(false);
            this.nextValueButton.setActive(false);
            return;
        }

        AdjustmentContext active = this.adjustmentContexts.get(this.adjustmentContextIndex);
        Component label = active.label().get();
        this.contextValueLabel.setText(label);
        this.valueValueLabel.setText(active.value().get());
        boolean adjustable = active.adjustable().getAsBoolean();
        this.previousValueButton.setActive(adjustable);
        this.nextValueButton.setActive(adjustable);
        setTooltip(
                this.previousContextButton,
                Component.translatable(PREVIEW_TRANSLATION_PREFIX + "previous",
                        Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "context")));
        setTooltip(
                this.nextContextButton,
                Component.translatable(PREVIEW_TRANSLATION_PREFIX + "next",
                        Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "context")));
        setTooltip(
                this.previousValueButton,
                Component.translatable(PREVIEW_TRANSLATION_PREFIX + "previous", label));
        setTooltip(
                this.nextValueButton,
                Component.translatable(PREVIEW_TRANSLATION_PREFIX + "next", label));
    }

    private int indexOfContext(@Nullable String retainedContextId) {
        if (retainedContextId != null) {
            for (int index = 0; index < this.adjustmentContexts.size(); index++) {
                if (retainedContextId.equals(this.adjustmentContexts.get(index).id())) {
                    return index;
                }
            }
        }
        return 0;
    }

    @Nullable
    private String activeAdjustmentContextId() {
        return this.adjustmentContexts.isEmpty() ? null :
                this.adjustmentContexts.get(this.adjustmentContextIndex).id();
    }

    private void refreshStructureNavigationTooltips() {
        setTooltip(
                this.previousStructureButton,
                Component.translatable(
                        PREVIEW_TRANSLATION_PREFIX + "previous",
                        structureTitle(relativeStructureKey(-1))));
        setTooltip(
                this.nextStructureButton,
                Component.translatable(
                        PREVIEW_TRANSLATION_PREFIX + "next",
                        structureTitle(relativeStructureKey(1))));
        setTooltip(
                this.confirmButton,
                Component.translatable("screen.data_energistics.multiblock_auto_build.confirm"));
    }

    private String relativeStructureKey(int direction) {
        List<String> structureKeys = this.draft.structureKeys();
        int current = structureKeys.indexOf(this.draft.previewSelection().activeSubstructureId());
        if (current < 0) {
            throw new IllegalStateException("Active Trinity automatic-build structure is absent from its draft");
        }
        return structureKeys.get(Math.floorMod(current + direction, structureKeys.size()));
    }

    private static Component structureTitle(String structureKey) {
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.auto_build.structure." + structureKey);
    }

    private static void configureButton(Button button, Runnable action) {
        button.setText(Component.empty());
        button.setOnClick(event -> action.run());
    }

    private static void setTooltip(Button button, Component tooltip) {
        button.text.style(style -> style.tooltips(tooltip));
        button.style(style -> style.tooltips(tooltip));
    }

    private static void validateSupportedSelection(MultiblockPreviewSpec spec, PreviewSelection selection) {
        selection.validateAgainst(spec);
        for (var substructure : spec.substructures()) {
            SubstructureSelection structureSelection = selection.selection(substructure.id());
            if (substructure.variantCount() != 1 || structureSelection.variantIndex() != 0) {
                throw new IllegalStateException("Trinity automatic build currently requires one variant for " +
                        substructure.id());
            }
        }
    }

    private record AdjustmentContext(String id,
                                     Supplier<Component> label,
                                     Supplier<Component> value,
                                     Runnable previous,
                                     Runnable next,
                                     BooleanSupplier adjustable) {}

    record Layout(UIElement root,
                  Button previousStructureButton,
                  Label structureTitleLabel,
                  Button nextStructureButton,
                  Button previousContextButton,
                  Label contextTitleLabel,
                  Label contextValueLabel,
                  Button nextContextButton,
                  Button previousValueButton,
                  Label valueTitleLabel,
                  Label valueValueLabel,
                  Button nextValueButton,
                  Button confirmButton) {}

    /**
     * Maps ALL plus every logical layer onto the single horizontal scroller authored in the NBT.
     */
    private static final class LayerScrollerBinding {

        private final StructurePreviewUi preview;
        private final Scroller.Horizontal scroller;
        private boolean refreshing;

        private LayerScrollerBinding(StructurePreviewUi preview, Scroller.Horizontal scroller) {
            this.preview = preview;
            this.scroller = scroller;
            scroller.setRange(0.0F, 1.0F);
            scroller.setOnValueChanged(ignored -> selectFromScroller());
        }

        private void refresh() {
            int layerCount = this.preview.session().snapshot().layers().size();
            int selection = selectedIndex();
            float normalized = layerCount == 0 ? 0.0F : (float) selection / layerCount;
            float scrollDelta = layerCount == 0 ? 1.0F : 1.0F / layerCount;
            float thumbPercent = layerCount == 0 ? 100.0F : 100.0F / (layerCount + 1);
            this.refreshing = true;
            try {
                this.scroller.scrollerStyle(style -> style.scrollDelta(scrollDelta));
                this.scroller.setNormalizedValue(normalized, false);
                this.scroller.setScrollBarSize(thumbPercent);
                this.scroller.selfAndAllChildren()
                        .forEach(element -> element.setAllowHitTest(layerCount > 0));
            } finally {
                this.refreshing = false;
            }
        }

        private void selectFromScroller() {
            if (this.refreshing) {
                return;
            }
            int layerCount = this.preview.session().snapshot().layers().size();
            if (layerCount == 0) {
                return;
            }
            int selection = Math.round(this.scroller.getNormalizedValue() * layerCount);
            this.refreshing = true;
            try {
                this.scroller.setNormalizedValue((float) selection / layerCount, false);
            } finally {
                this.refreshing = false;
            }
            if (selection == 0) {
                this.preview.panel().showAllLayers();
            } else {
                this.preview.panel().showLayer(selection - 1);
            }
        }

        private int selectedIndex() {
            PreviewVisibleLayer visibleLayer = this.preview.session().viewState().visibleLayer();
            return visibleLayer instanceof PreviewVisibleLayer.LogicalLayer layer ? layer.layerIndex() + 1 : 0;
        }
    }
}
