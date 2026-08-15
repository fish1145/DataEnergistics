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

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

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
    static final String ADJUSTMENT_ID = PANEL_ID + "_adjustment";
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

    static final String PREVIEW_MOUNT_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_preview_mount";
    static final String LAYER_SCROLLER_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_layer_scroller";
    static final String MATERIALS_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_materials";
    static final String MATERIALS_SCROLLER_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID +
            "_materials_scroller";
    static final String CLOSE_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_close";
    static final String WINDOW_TITLE_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_title";

    private static final int AUTHORED_ROOT_CHILD_COUNT = 6;
    private static final int AUTHORED_ADJUSTMENT_CHILD_COUNT = 9;

    private static final String PREVIEW_TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final String AUTO_BUILD_TRANSLATION_PREFIX = "screen.data_energistics.trinity_data_core.auto_build.adjustment.";

    private final StructurePreviewUi preview;
    private final HostSubUiContext context;
    private final BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction;
    private final LongPredicate hostedAutoBuildPending;
    private final AutoBuildComposition composition;
    private TrinityAutoBuildDraft draft;

    static Layout requireLayout(@NotNull UIElement root) {
        List<UIElement> rootChildren = root.getChildren();
        if (rootChildren.size() != AUTHORED_ROOT_CHILD_COUNT) {
            throw new IllegalStateException("Automatic-build layout expected " + AUTHORED_ROOT_CHILD_COUNT +
                    " authored root children, found " + rootChildren.size());
        }
        root.setId(TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID);
        UIElement previewMount = identify(
                authoredChild(rootChildren, 0, UIElement.class, "structure preview mount"),
                PREVIEW_MOUNT_ID);
        Scroller.Horizontal layerScroller = identify(
                authoredOnlyChild(previewMount, Scroller.Horizontal.class, "layer scroller"),
                LAYER_SCROLLER_ID);
        UIElement materialsMount = identify(
                authoredChild(rootChildren, 1, UIElement.class, "material mount"),
                MATERIALS_ID);
        Scroller.Vertical materialScroller = identify(
                authoredOnlyChild(materialsMount, Scroller.Vertical.class, "material scroller"),
                MATERIALS_SCROLLER_ID);
        UIElement structureSelector = authoredChild(rootChildren, 2, UIElement.class, "structure selector");
        structureSelector.setId(STRUCTURE_SELECTOR_ID);
        List<UIElement> structureControls = structureSelector.getChildren();
        if (structureControls.size() != 3) {
            throw new IllegalStateException("Automatic-build structure selector expected 3 authored children, found " +
                    structureControls.size());
        }
        UIElement adjustment = authoredChild(rootChildren, 3, UIElement.class, "adjustment panel");
        adjustment.setId(ADJUSTMENT_ID);
        List<UIElement> adjustmentControls = adjustment.getChildren();
        if (adjustmentControls.size() != AUTHORED_ADJUSTMENT_CHILD_COUNT) {
            throw new IllegalStateException("Automatic-build adjustment layout expected " +
                    AUTHORED_ADJUSTMENT_CHILD_COUNT + " authored children, found " + adjustmentControls.size());
        }
        Button confirm = authoredChild(adjustmentControls, 0, Button.class, "confirm");
        confirm.setId(CONFIRM_BUTTON_ID);
        TextElement confirmTitle = authoredConfirmTitle(confirm);
        confirmTitle.setId(CONFIRM_TITLE_ID);
        confirmTitle.addClass("trinity-auto-build-confirm-title");
        confirmTitle.setAllowHitTest(false);
        confirmTitle.setOverflowVisible(false);
        return new Layout(
                root,
                previewMount,
                layerScroller,
                materialsMount,
                materialScroller,
                identify(authoredChild(structureControls, 0, Button.class, "previous structure"), STRUCTURE_PREVIOUS_ID),
                identifyAndClass(
                        authoredChild(structureControls, 2, Label.class, "structure title"),
                        STRUCTURE_TITLE_ID,
                        "trinity-auto-build-structure-title"),
                identify(authoredChild(structureControls, 1, Button.class, "next structure"), STRUCTURE_NEXT_ID),
                identify(authoredChild(adjustmentControls, 1, Button.class, "previous context"), CONTEXT_PREVIOUS_ID),
                identifyAndClass(
                        authoredChild(adjustmentControls, 3, Label.class, "context title"),
                        CONTEXT_TITLE_ID,
                        "trinity-auto-build-adjustment-title"),
                identifyAndClass(
                        authoredChild(adjustmentControls, 2, Label.class, "context value"),
                        CONTEXT_VALUE_ID,
                        "trinity-auto-build-adjustment-value"),
                identify(authoredChild(adjustmentControls, 4, Button.class, "next context"), CONTEXT_NEXT_ID),
                identify(authoredChild(adjustmentControls, 8, Button.class, "previous value"), VALUE_PREVIOUS_ID),
                identifyAndClass(
                        authoredChild(adjustmentControls, 7, Label.class, "value title"),
                        VALUE_TITLE_ID,
                        "trinity-auto-build-adjustment-title"),
                identifyAndClass(
                        authoredChild(adjustmentControls, 6, Label.class, "value value"),
                        VALUE_VALUE_ID,
                        "trinity-auto-build-adjustment-value"),
                identify(authoredChild(adjustmentControls, 5, Button.class, "next value"), VALUE_NEXT_ID),
                confirm,
                confirmTitle,
                identify(authoredChild(rootChildren, 4, Button.class, "close button"), CLOSE_ID),
                identify(authoredChild(rootChildren, 5, Label.class, "window title"), WINDOW_TITLE_ID));
    }

    private static <T extends UIElement> T authoredChild(List<UIElement> children,
                                                         int index,
                                                         Class<T> type,
                                                         String role) {
        if (index < 0 || index >= children.size()) {
            throw new IllegalStateException("Automatic-build layout is missing " + role);
        }
        UIElement child = children.get(index);
        if (!type.isInstance(child)) {
            throw new IllegalStateException("Automatic-build layout " + role + " has type " +
                    child.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(child);
    }

    private static <T extends UIElement> T authoredOnlyChild(UIElement parent, Class<T> type, String role) {
        List<UIElement> children = parent.getChildren();
        if (children.size() != 1) {
            throw new IllegalStateException("Automatic-build layout " + role +
                    " expected one authored child, found " + children.size());
        }
        return authoredChild(children, 0, type, role);
    }

    private static TextElement authoredConfirmTitle(Button confirm) {
        List<TextElement> titles = confirm.getChildren().stream()
                .filter(TextElement.class::isInstance)
                .map(TextElement.class::cast)
                .filter(child -> child != confirm.text)
                .toList();
        if (titles.size() != 1) {
            throw new IllegalStateException("Automatic-build confirm button expected one authored title, found " +
                    titles.size());
        }
        return titles.getFirst();
    }

    private static <T extends UIElement> T identify(T element, String id) {
        element.setId(id);
        return element;
    }

    private static <T extends UIElement> T identifyAndClass(T element, String id, String className) {
        identify(element, id);
        element.addClass(className);
        return element;
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
                  UIElement previewMount,
                  Scroller.Horizontal layerScroller,
                  UIElement materialsMount,
                  Scroller.Vertical materialScroller,
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
                  TextElement confirmTitle,
                  Button closeButton,
                  Label windowTitleLabel) {

        AutoBuildComposition.Elements elements() {
            return new AutoBuildComposition.Elements(
                    this.root,
                    this.previewMount,
                    this.layerScroller,
                    this.materialsMount,
                    this.materialScroller,
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
                    new AutoBuildComposition.Region(20, 3, 156, 123),
                    new AutoBuildComposition.HorizontalSpan(23, 150),
                    new AutoBuildComposition.Region(3, 3, 16, 16),
                    new AutoBuildComposition.Region(2, 2, 54, 108));
        }
    }
}
