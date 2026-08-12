package com.fish_dan_.data_energistics.gui.ldlib2.trinity.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructureSelection;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild.AutoBuildComposition;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiXmlLayouts;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;

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
    static final String CONFIRM_TITLE_ID = CONFIRM_BUTTON_ID + "_title";

    private static final String PREVIEW_TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final String AUTO_BUILD_TRANSLATION_PREFIX = "screen.data_energistics.trinity_data_core.auto_build.adjustment.";

    private final StructurePreviewUi preview;
    private final HostSubUiContext context;
    private final BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction;
    private final LongPredicate hostedAutoBuildPending;
    private final AutoBuildComposition composition;
    private TrinityAutoBuildDraft draft;

    static Layout requireLayout(@NotNull UIElement root) {
        UIElement controls = TrinityUiXmlLayouts.require(root, PANEL_ID, UIElement.class);
        TrinityUiXmlLayouts.require(root, STRUCTURE_SELECTOR_ID, UIElement.class);
        Label confirmTitle = createConfirmTitle();
        controls.addChild(confirmTitle);
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
                TrinityUiXmlLayouts.require(root, CONFIRM_BUTTON_ID, Button.class),
                confirmTitle);
    }

    private static Label createConfirmTitle() {
        Label title = new Label();
        title.setId(CONFIRM_TITLE_ID);
        title.addClass("trinity-auto-build-confirm-title");
        title.setText(Component.translatable("screen.data_energistics.multiblock_auto_build.confirm"));
        title.setAllowHitTest(false);
        title.setOverflowVisible(false);
        title.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(207)
                .top(2)
                .width(37)
                .height(8));
        return title;
    }

    TrinityDataCoreAutoBuildPanel(@NotNull Layout layout,
                                  @NotNull StructurePreviewUi preview,
                                  @NotNull TrinityAutoBuildDraft draft,
                                  @NotNull HostSubUiContext context,
                                  @NotNull BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                  @NotNull LongPredicate hostedAutoBuildPending,
                                  @NotNull AutoBuildComposition composition) {
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
        this.composition = composition;
        this.composition.bindActions(new AutoBuildComposition.Actions(
                () -> selectRelativeStructure(-1),
                () -> selectRelativeStructure(1),
                this::submit));
        this.composition.setHeadings(
                Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "context"),
                Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "value"),
                Component.translatable("screen.data_energistics.multiblock_auto_build.confirm"));
        this.composition.setAdjustmentTooltips(
                Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "context"),
                label -> Component.translatable(PREVIEW_TRANSLATION_PREFIX + "previous", label),
                label -> Component.translatable(PREVIEW_TRANSLATION_PREFIX + "next", label));
        this.preview.panel().setSelectionChangeListener(this::replacePreviewSelection);
        layout.root().addEventListener(UIEvents.TICK, ignored -> screenTick());
        refreshBoundState(null);
        screenTick();
    }

    private void screenTick() {
        long generation = this.context.generation();
        this.composition.setConfirmActive(
                this.context.canSendServerAction() && !this.hostedAutoBuildPending.test(generation));
    }

    private void selectRelativeStructure(int direction) {
        this.preview.panel().selectStructure(relativeStructureKey(direction));
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
        this.composition.setStructureTitle(structureTitle(this.draft.previewSelection().activeSubstructureId()));
        this.composition.setMaterials(this.preview.session().recipeView().inputs());
        this.composition.refreshLayers();
        rebuildAdjustmentContexts(retainedContextId);
        refreshStructureNavigationTooltips();
    }

    private void rebuildAdjustmentContexts(@Nullable String retainedContextId) {
        List<AutoBuildComposition.Adjustment> contexts = new ArrayList<>();
        PreviewTierDomain tierDomain = this.draft.activeTierDomain();
        contexts.add(new AutoBuildComposition.Adjustment(
                "tier:" + tierDomain.id(),
                tierDomain::label,
                () -> tierDomain.option(this.draft.activeTierValue()).label(),
                this.preview.panel()::previousTier,
                this.preview.panel()::nextTier,
                () -> tierDomain.options().size() > 1));

        OptionalInt repeatUnit = this.draft.activeVariableRepeatUnit();
        if (repeatUnit.isPresent()) {
            int unitIndex = repeatUnit.getAsInt();
            contexts.add(new AutoBuildComposition.Adjustment(
                    "repeat:" + unitIndex,
                    () -> Component.translatable(AUTO_BUILD_TRANSLATION_PREFIX + "repeat"),
                    () -> Component.literal(Integer.toString(this.draft.activeRepeatCount())),
                    () -> this.preview.panel().previousRepeat(unitIndex),
                    () -> this.preview.panel().nextRepeat(unitIndex),
                    () -> true));
        }

        this.composition.setAdjustments(contexts, retainedContextId);
    }

    @Nullable
    private String activeAdjustmentContextId() {
        return this.composition.activeAdjustmentKey();
    }

    private void refreshStructureNavigationTooltips() {
        this.composition.setActionTooltips(
                Component.translatable(
                        PREVIEW_TRANSLATION_PREFIX + "previous",
                        structureTitle(relativeStructureKey(-1))),
                Component.translatable(
                        PREVIEW_TRANSLATION_PREFIX + "next",
                        structureTitle(relativeStructureKey(1))),
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
                  Button confirmButton,
                  Label confirmTitle) {

        AutoBuildComposition.Elements elements(@NotNull UIElement previewMount,
                                               @NotNull Scroller.Horizontal layerScroller,
                                               @NotNull UIElement materialsMount,
                                               @NotNull Scroller.Vertical materialScroller) {
            return new AutoBuildComposition.Elements(
                    this.root,
                    previewMount,
                    layerScroller,
                    materialsMount,
                    materialScroller,
                    new AutoBuildComposition.StructureControls(
                            this.previousStructureButton,
                            this.structureTitleLabel,
                            this.nextStructureButton),
                    new AutoBuildComposition.AdjustmentControls(
                            this.previousContextButton,
                            this.contextTitleLabel,
                            this.contextValueLabel,
                            this.nextContextButton,
                            this.previousValueButton,
                            this.valueTitleLabel,
                            this.valueValueLabel,
                            this.nextValueButton),
                    new AutoBuildComposition.ConfirmControls(this.confirmButton, this.confirmTitle));
        }

        AutoBuildComposition.PreviewGeometry geometry() {
            return new AutoBuildComposition.PreviewGeometry(
                    new AutoBuildComposition.Region(0, 0, 183, 133),
                    new AutoBuildComposition.Region(20, 3, 160, 123),
                    new AutoBuildComposition.Region(3, 3, 16, 16),
                    new AutoBuildComposition.Region(22, 128, 152, 4),
                    new AutoBuildComposition.Region(2, 2, 54, 108));
        }
    }
}
