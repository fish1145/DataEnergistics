package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructureSelection;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUi;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;

/**
 * Owns one fresh automatic-build draft and keeps it synchronized with an independently owned structure preview.
 */
final class TrinityDataCoreAutoBuildPanel extends UIElement {

    static final String PANEL_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_controls";
    static final String STRUCTURE_PREVIOUS_ID = PANEL_ID + "_structure_previous";
    static final String STRUCTURE_NEXT_ID = PANEL_ID + "_structure_next";
    static final String BUILD_REQUESTED_TOGGLE_ID = PANEL_ID + "_build_requested";
    static final String BUILD_REQUESTED_BUTTON_ID = BUILD_REQUESTED_TOGGLE_ID + "_button";
    static final String CONFIRM_BUTTON_ID = PANEL_ID + "_confirm";

    private final StructurePreviewUi preview;
    private final HostSubUiContext context;
    private final BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction;
    private final LongPredicate hostedAutoBuildPending;
    private final Button previousStructureButton;
    private final Button nextStructureButton;
    private final Toggle buildRequestedToggle;
    private final Button confirmButton;
    private TrinityAutoBuildDraft draft;

    TrinityDataCoreAutoBuildPanel(StructurePreviewUi preview,
                                  TrinityAutoBuildDraft draft,
                                  HostSubUiContext context,
                                  BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                  LongPredicate hostedAutoBuildPending) {
        if (preview == null || draft == null || context == null || hostedAutoBuildAction == null ||
                hostedAutoBuildPending == null) {
            throw new IllegalArgumentException("Trinity automatic-build panel arguments cannot be null");
        }
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
        UIElement template = TrinityUiXmlLayouts.loadRoot("auto_build_panel");
        this.previousStructureButton = TrinityUiXmlLayouts.require(template, STRUCTURE_PREVIOUS_ID, Button.class);
        this.nextStructureButton = TrinityUiXmlLayouts.require(template, STRUCTURE_NEXT_ID, Button.class);
        this.buildRequestedToggle = TrinityUiXmlLayouts.require(template, BUILD_REQUESTED_TOGGLE_ID, Toggle.class);
        this.confirmButton = TrinityUiXmlLayouts.require(template, CONFIRM_BUTTON_ID, Button.class);
        configureIconButton(this.previousStructureButton, Icons.LEFT_ARROW_NO_BAR, () -> selectRelativeStructure(-1));
        configureIconButton(this.nextStructureButton, Icons.RIGHT_ARROW_NO_BAR, () -> selectRelativeStructure(1));
        configureBuildRequestedToggle(this.buildRequestedToggle);
        configureConfirmButton(this.confirmButton);
        this.preview.panel().setSelectionChangeListener(this::replacePreviewSelection);
        refreshStructureNavigationTooltips();

        setId(PANEL_ID);
        addClass("trinity-auto-build-panel");
        TrinityUiXmlLayouts.moveChildren(template, this);
        TrinityUiXmlLayouts.require(this, PANEL_ID + "_structure_title", Label.class)
                .bindDataSource(SupplierDataSource.of(this::structureTitle));
    }

    @Override
    public void screenTick() {
        long generation = this.context.generation();
        this.confirmButton.setActive(
                this.context.canSendServerAction() && !this.hostedAutoBuildPending.test(generation));
        super.screenTick();
    }

    TrinityAutoBuildDraft draft() {
        return this.draft;
    }

    StructurePreviewUi preview() {
        return this.preview;
    }

    void selectStructure(String structureKey) {
        this.preview.panel().selectStructure(structureKey);
    }

    void toggleBuildRequested() {
        if (this.buildRequestedToggle.isActive()) {
            this.buildRequestedToggle.setOn(!this.buildRequestedToggle.isOn(), true);
        }
    }

    private void configureBuildRequestedToggle(Toggle toggle) {
        toggle.toggleButton.setId(BUILD_REQUESTED_BUTTON_ID);
        toggle.toggleButton.addClass("trinity-auto-build-toggle-button");
        toggle.toggleLabel.addClass("trinity-auto-build-toggle-label");
        toggle.setText(Component.translatable("screen.data_energistics.multiblock_auto_build.build_requested"));
        toggle.setOn(this.draft.activeBuildRequested(), false);
        toggle.setOnToggleChanged(buildRequested -> this.draft = this.draft.withBuildRequested(buildRequested));
        toggle.toggleLabel.addEventListener(UIEvents.CLICK, event -> {
            if (event.button == 0 && toggle.isActive()) {
                toggle.setOn(!toggle.isOn(), true);
            }
        });
    }

    private void configureConfirmButton(Button button) {
        button.setText(Component.translatable("screen.data_energistics.multiblock_auto_build.confirm"));
        button.addPreIcon(Icons.CHECK);
        button.setOnClick(event -> submit());
    }

    void selectRelativeStructure(int direction) {
        selectStructure(relativeStructureKey(direction));
    }

    private void replacePreviewSelection(PreviewSelection selection) {
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
        }
        updated = updated.select(selection.activeSubstructureId());
        if (!updated.previewSelection().equals(selection)) {
            throw new IllegalStateException("Trinity automatic-build draft could not reproduce its preview selection");
        }
        this.draft = updated;
        this.buildRequestedToggle.setOn(this.draft.activeBuildRequested(), false);
        refreshStructureNavigationTooltips();
    }

    void submit() {
        long generation = this.context.generation();
        if (!this.context.canSendServerAction() || this.hostedAutoBuildPending.test(generation)) {
            return;
        }
        if (!this.draft.previewSelection().equals(this.preview.session().selection())) {
            throw new IllegalStateException("Trinity automatic-build draft diverged from its preview session");
        }
        validateSupportedSelection(this.draft.spec(), this.draft.previewSelection());
        this.hostedAutoBuildAction.accept(generation, this.draft.submission());
    }

    private Component structureTitle() {
        return structureTitle(this.draft.previewSelection().activeSubstructureId());
    }

    private void refreshStructureNavigationTooltips() {
        this.previousStructureButton.style(style -> style.tooltips(Component.translatable(
                "screen.data_energistics.multiblock_preview.previous",
                structureTitle(relativeStructureKey(-1)))));
        this.nextStructureButton.style(style -> style.tooltips(Component.translatable(
                "screen.data_energistics.multiblock_preview.next",
                structureTitle(relativeStructureKey(1)))));
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

    private static void configureIconButton(Button button, IGuiTexture icon, Runnable action) {
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> action.run());
    }

    private static void validateSupportedSelection(MultiblockPreviewSpec spec, PreviewSelection selection) {
        selection.validateAgainst(spec);
        for (var substructure : spec.substructures()) {
            SubstructureSelection structureSelection = selection.selection(substructure.id());
            if (substructure.variantCount() != 1 || structureSelection.variantIndex() != 0) {
                throw new IllegalStateException("Trinity automatic build currently requires one variant for " +
                        substructure.id());
            }
            if (!structureSelection.candidateSelections().isEmpty()) {
                throw new IllegalStateException("Trinity automatic build cannot submit candidate overrides for " +
                        substructure.id());
            }
        }
    }
}
