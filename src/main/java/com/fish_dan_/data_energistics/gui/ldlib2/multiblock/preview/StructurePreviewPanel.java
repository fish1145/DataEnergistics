package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild.AutoBuildComposition;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneElement;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Host-neutral LDLib2 preview surface containing one large scene and compact, clearly separated controls.
 */
public final class StructurePreviewPanel extends UIElement {

    /**
     * Suffix used by the independently owned scene element.
     */
    public static final String SCENE_SUFFIX = "_scene";
    /**
     * Suffix used by the previous-variant control.
     */
    public static final String VARIANT_PREVIOUS_SUFFIX = "_variant_previous";
    /**
     * Suffix used by the next-variant control.
     */
    public static final String VARIANT_NEXT_SUFFIX = "_variant_next";
    /**
     * Suffix used by the previous-tier control.
     */
    public static final String TIER_PREVIOUS_SUFFIX = "_tier_previous";
    /**
     * Suffix used by the next-tier control.
     */
    public static final String TIER_NEXT_SUFFIX = "_tier_next";
    /**
     * Suffix prefix used by repeat-unit controls, followed by the unit index and direction.
     */
    public static final String REPEAT_SUFFIX = "_repeat_";
    /**
     * Suffix used by the explicit all-layers control.
     */
    public static final String LAYER_ALL_SUFFIX = "_layer_all";
    /**
     * Suffix used by the previous-layer control.
     */
    public static final String LAYER_PREVIOUS_SUFFIX = "_layer_previous";
    /**
     * Suffix used by the next-layer control.
     */
    public static final String LAYER_NEXT_SUFFIX = "_layer_next";
    /**
     * Suffix used by the selector that exposes ALL and every exact logical layer.
     */
    public static final String LAYER_SELECTOR_SUFFIX = "_layer_selector";
    /**
     * Suffix used by the selected-block display slot.
     */
    public static final String SELECTED_BLOCK_SUFFIX = "_selected_block";
    /**
     * Suffix used by the canonical material scroller.
     */
    public static final String MATERIALS_SUFFIX = "_materials";

    private static final String TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final int VARIANT_WIDTH = 56;
    private static final int TIER_LEFT = 58;
    private static final int TIER_WIDTH = 76;
    private static final int REPEAT_LEFT = 136;
    private static final int REPEAT_WIDTH = 60;
    private static final int LAYER_WIDTH = 92;

    private final String idPrefix;
    private final StructurePreviewSession session;
    private final StructurePreviewPresentation presentation;
    private final StructurePreviewSceneElement scene;
    private final ItemSlot selectedBlockSlot;
    private final PreviewCandidateColumn candidateColumn;
    private final PreviewLayerSelector layerSelector;
    private final UIElement recipeControls;
    private final ScrollerView repeatControls;
    @Nullable
    private final PreviewMaterialStrip materials;
    private Consumer<PreviewSelection> selectionChangeListener = selection -> {};
    private boolean selectionChangeListenerRegistered;
    @Nullable
    private StructurePreviewSceneBinding sceneBinding;
    private boolean sceneBindingReleased;

    StructurePreviewPanel(String idPrefix, StructurePreviewSession session) {
        this(idPrefix, session, StructurePreviewPresentation.HOSTED);
    }

    StructurePreviewPanel(String idPrefix,
                          StructurePreviewSession session,
                          StructurePreviewPresentation presentation) {
        if (idPrefix == null || idPrefix.isBlank() || session == null || presentation == null) {
            throw new IllegalArgumentException("Structure preview panel arguments cannot be null or blank");
        }
        this.idPrefix = idPrefix;
        this.session = session;
        this.presentation = presentation;
        this.scene = createScene();
        this.selectedBlockSlot = createSelectedBlockSlot();
        this.candidateColumn = new PreviewCandidateColumn(
                idPrefix + "_candidate_column",
                this::selectCandidate,
                this::selectTier);
        this.candidateColumn.setDisplay(false);
        this.layerSelector = createLayerSelector();
        this.repeatControls = createRepeatControls();
        this.recipeControls = createRecipeControls();
        this.materials = presentation.hasMaterialStrip() ? createMaterials() : null;

        setId(idPrefix);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .width(StructurePreviewPresentation.WIDTH)
                .height(presentation.height()));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        addChildren(this.scene, this.selectedBlockSlot, this.candidateColumn, this.layerSelector, this.recipeControls);
        if (this.materials != null) {
            addChild(this.materials);
        }
        refreshRepeatControls();
        refreshMaterials();
        refreshSelectedBlockSlot();
    }

    /**
     * Returns the independently owned model session backing this exact panel.
     */
    public StructurePreviewSession session() {
        return this.session;
    }

    /**
     * Returns the independently owned double-sided scene shell contained by this panel.
     */
    public StructurePreviewSceneElement scene() {
        return this.scene;
    }

    /**
     * Returns the immutable composition that determines panel height and material ownership.
     */
    public StructurePreviewPresentation presentation() {
        return this.presentation;
    }

    /**
     * Installs the sole listener notified after recipe-affecting controls replace the retained selection.
     */
    public void setSelectionChangeListener(Consumer<PreviewSelection> selectionChangeListener) {
        if (selectionChangeListener == null) {
            throw new IllegalArgumentException("Structure preview selection listener cannot be null");
        }
        if (this.selectionChangeListenerRegistered) {
            throw new IllegalStateException("Structure preview selection listener can only be registered once");
        }
        this.selectionChangeListener = selectionChangeListener;
        this.selectionChangeListenerRegistered = true;
    }

    /**
     * Activates another allowed named structure through the visible-control refresh path.
     */
    public void selectStructure(String structureKey) {
        changeSelection(() -> this.session.selectStructure(structureKey));
    }

    /**
     * Selects one exact active-structure variant without replacing this panel, session, or Scene.
     */
    public void selectVariant(int variantIndex) {
        changeSelection(() -> this.session.selectVariant(variantIndex));
    }

    /**
     * Selects one exact predicate candidate without replacing this panel, session, or Scene.
     */
    public void selectCandidate(PreviewPredicateKey predicateKey, int candidateIndex) {
        PreviewCellSnapshot selectedCell = this.session.selectedCell();
        BlockPos selectedPosition = selectedCell == null ? null : selectedCell.relativePosition();
        changeSelection(() -> this.session.selectCandidate(predicateKey, candidateIndex), selectedPosition);
    }

    /**
     * Selects one exact tier value while retaining the block that exposed its replacement list.
     */
    public void selectTier(String domainId, int value) {
        PreviewCellSnapshot selectedCell = this.session.selectedCell();
        BlockPos selectedPosition = selectedCell == null ? null : selectedCell.relativePosition();
        changeSelection(() -> this.session.selectTier(domainId, value), selectedPosition);
    }

    /**
     * Selects the next tier through the same refresh path used by the visible control.
     */
    public void nextTier() {
        changeSelection(this.session::nextTier);
    }

    /**
     * Selects the previous tier through the same refresh path used by the visible control.
     */
    public void previousTier() {
        changeSelection(this.session::previousTier);
    }

    /**
     * Increments one repeat unit through the same refresh path used by the visible control.
     */
    public void nextRepeat(int unitIndex) {
        changeSelection(() -> this.session.nextRepeat(unitIndex));
    }

    /**
     * Decrements one repeat unit through the same refresh path used by the visible control.
     */
    public void previousRepeat(int unitIndex) {
        changeSelection(() -> this.session.previousRepeat(unitIndex));
    }

    /**
     * Selects the next logical layer through the same refresh path used by the visible control.
     */
    public void nextLayer() {
        this.layerSelector.nextLayer();
    }

    /**
     * Selects one exact zero-based logical layer through the visible selector state.
     */
    public void showLayer(int layerIndex) {
        this.layerSelector.showLayer(layerIndex);
    }

    /**
     * Restores the explicit all-layers view through the visible selector state.
     */
    public void showAllLayers() {
        this.layerSelector.showAllLayers();
    }

    /**
     * Fits the hosted preview into the editor-authored automatic-build scene frame.
     *
     * <p>
     * The NBT layout owns the external layer scroller, material grid, and adjustment controls. This method retains
     * the original panel as the Scene's lifecycle owner while suppressing only its duplicate integrated controls.
     * </p>
     */
    @ApiStatus.Internal
    public void useAutoBuildComposition(AutoBuildComposition.PreviewGeometry geometry) {
        PreviewMaterialStrip materialStrip = this.materials;
        if (this.presentation != StructurePreviewPresentation.HOSTED || materialStrip == null) {
            throw new IllegalStateException("Only a hosted structure preview can use the automatic-build composition");
        }
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(geometry.panel().left())
                .top(geometry.panel().top())
                .width(geometry.panel().width())
                .height(geometry.panel().height()));
        this.scene.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(geometry.scene().left())
                .top(geometry.scene().top())
                .width(geometry.scene().width())
                .height(geometry.scene().height()));
        this.selectedBlockSlot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(geometry.selectedBlock().left())
                .top(geometry.selectedBlock().top())
                .width(geometry.selectedBlock().width())
                .height(geometry.selectedBlock().height()));
        this.selectedBlockSlot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        this.candidateColumn.setDisplay(true);
        detachIntegratedControl(this.layerSelector, "layer selector");
        detachIntegratedControl(this.recipeControls, "recipe controls");
        detachIntegratedControl(materialStrip, "material strip");
    }

    private void detachIntegratedControl(UIElement control, String description) {
        if (!removeChild(control)) {
            throw new IllegalStateException("Automatic-build preview could not detach its integrated " + description);
        }
    }

    void bindScene(StructurePreviewSceneBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("Structure preview scene binding cannot be null");
        }
        if (this.sceneBinding != null) {
            throw new IllegalStateException("Structure preview scene can only be bound once");
        }
        if (this.sceneBindingReleased) {
            throw new IllegalStateException("Released structure preview scene cannot be rebound");
        }
        this.sceneBinding = binding;
        refreshScene();
    }

    @Override
    protected void onRemoved() {
        Throwable failure = null;
        if (this.sceneBinding != null) {
            StructurePreviewSceneBinding binding = this.sceneBinding;
            this.sceneBinding = null;
            this.sceneBindingReleased = true;
            try {
                binding.release();
            } catch (RuntimeException | Error releaseFailure) {
                failure = mergeFailures(failure, releaseFailure);
            }
        }
        try {
            super.onRemoved();
        } catch (RuntimeException | Error removalFailure) {
            failure = mergeFailures(failure, removalFailure);
        }
        rethrow(failure);
    }

    void selectBlock(BlockPos position) {
        this.session.selectBlock(position);
        refreshSelectedBlockSlot();
    }

    private StructurePreviewSceneElement createScene() {
        StructurePreviewSceneElement element = new StructurePreviewSceneElement();
        element.setId(this.idPrefix + SCENE_SUFFIX);
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(StructurePreviewPresentation.WIDTH)
                .height(StructurePreviewPresentation.SCENE_HEIGHT));
        return element;
    }

    private ItemSlot createSelectedBlockSlot() {
        ItemSlot slot = new ItemSlot();
        slot.setId(this.idPrefix + SELECTED_BLOCK_SUFFIX);
        slot.setItem(ItemStack.EMPTY);
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(4)
                .width(18)
                .height(18));
        slot.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> tooltip = selectedBlockTooltip();
            if (!tooltip.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(tooltip, null, null, null);
            }
        });
        return slot;
    }

    private PreviewLayerSelector createLayerSelector() {
        PreviewLayerSelector selector = new PreviewLayerSelector(
                this.idPrefix,
                this.session,
                this::onLayerChanged,
                LAYER_WIDTH);
        selector.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(StructurePreviewPresentation.WIDTH - LAYER_WIDTH - 4)
                .top(4));
        return selector;
    }

    private UIElement createRecipeControls() {
        UIElement controls = new UIElement();
        controls.setId(this.idPrefix + "_controls");
        controls.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(StructurePreviewPresentation.CONTROL_RAIL_TOP)
                .width(StructurePreviewPresentation.WIDTH)
                .height(StructurePreviewPresentation.CONTROL_RAIL_HEIGHT));
        controls.getStyle().backgroundTexture(Sprites.RECT_DARK);

        PreviewStepper variant = new PreviewStepper(
                this.idPrefix + "_variant",
                this.idPrefix + VARIANT_PREVIOUS_SUFFIX,
                this.idPrefix + VARIANT_NEXT_SUFFIX,
                () -> Component.translatable(TRANSLATION_PREFIX + "variant"),
                this::variantText,
                () -> changeSelection(this.session::previousVariant),
                () -> changeSelection(this.session::nextVariant),
                VARIANT_WIDTH);
        variant.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0));

        PreviewStepper tier = new PreviewStepper(
                this.idPrefix + "_tier",
                this.idPrefix + TIER_PREVIOUS_SUFFIX,
                this.idPrefix + TIER_NEXT_SUFFIX,
                this::tierTitle,
                this::tierText,
                () -> changeSelection(this.session::previousTier),
                this::nextTier,
                TIER_WIDTH);
        tier.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(TIER_LEFT)
                .top(0));
        controls.addChildren(variant, tier, this.repeatControls);
        return controls;
    }

    private ScrollerView createRepeatControls() {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(this.idPrefix + "_repeat_controls");
        scroller.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(REPEAT_LEFT)
                .top(0)
                .width(REPEAT_WIDTH)
                .height(StructurePreviewPresentation.CONTROL_RAIL_HEIGHT));
        scroller.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        scroller.viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(0))
                .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        scroller.viewContainer(viewContainer -> viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .height(StructurePreviewPresentation.CONTROL_CONTENT_HEIGHT)));
        return scroller;
    }

    private PreviewMaterialStrip createMaterials() {
        PreviewMaterialStrip strip = new PreviewMaterialStrip(this.idPrefix);
        strip.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(StructurePreviewPresentation.MATERIAL_STRIP_TOP)
                .width(StructurePreviewPresentation.WIDTH)
                .height(StructurePreviewPresentation.MATERIAL_STRIP_HEIGHT));
        return strip;
    }

    private void changeSelection(Runnable change) {
        changeSelection(change, null);
    }

    private void changeSelection(Runnable change, @Nullable BlockPos retainedSelectedPosition) {
        PreviewSelection previous = this.session.selection();
        change.run();
        PreviewSelection current = this.session.selection();
        if (previous.equals(current)) {
            return;
        }
        if (retainedSelectedPosition != null) {
            this.session.selectBlock(retainedSelectedPosition);
        }
        if (!previous.activeSubstructureId().equals(current.activeSubstructureId()) ||
                previous.activeSelection().variantIndex() != current.activeSelection().variantIndex()) {
            refreshRepeatControls();
        }
        refreshMaterials();
        refreshSelectedBlockSlot();
        this.layerSelector.refresh();
        refreshScene();
        this.selectionChangeListener.accept(current);
    }

    private void onLayerChanged() {
        refreshSelectedBlockSlot();
        refreshScene();
    }

    private void refreshScene() {
        if (this.sceneBinding != null) {
            this.sceneBinding.refresh(this.session.snapshot(), this.session.viewState());
        }
    }

    private void refreshMaterials() {
        if (this.materials != null) {
            this.materials.setMaterials(this.session.recipeView().inputs());
        }
    }

    private void refreshRepeatControls() {
        this.repeatControls.clearAllScrollViewChildren();
        for (int unitIndex : this.session.variableRepeatUnits()) {
            PreviewStepper repeat = new PreviewStepper(
                    this.idPrefix + REPEAT_SUFFIX + unitIndex,
                    this.idPrefix + REPEAT_SUFFIX + unitIndex + "_previous",
                    this.idPrefix + REPEAT_SUFFIX + unitIndex + "_next",
                    () -> Component.translatable(TRANSLATION_PREFIX + "repeat", unitIndex + 1),
                    () -> Component.translatable(
                            TRANSLATION_PREFIX + "repeat.value",
                            this.session.selection().activeSelection().repeatCounts().get(unitIndex)),
                    () -> changeSelection(() -> this.session.previousRepeat(unitIndex)),
                    () -> nextRepeat(unitIndex),
                    REPEAT_WIDTH);
            this.repeatControls.addScrollViewChild(repeat);
        }
    }

    private void refreshSelectedBlockSlot() {
        PreviewCellSnapshot selected = this.session.selectedCell();
        this.candidateColumn.refresh(
                selected,
                activeSubstructure().tierDomains(),
                this.session.selection().activeSelection().tierSelections());
        if (selected == null) {
            this.selectedBlockSlot.setItem(ItemStack.EMPTY);
            return;
        }
        PreviewCandidate candidate = selected.predicate().selectedCandidate().orElse(null);
        if (candidate == null || !candidate.concrete()) {
            this.selectedBlockSlot.setItem(ItemStack.EMPTY);
            return;
        }
        this.selectedBlockSlot.setItem(candidate.placementKey().orElseThrow().toStack(1));
    }

    private Component variantText() {
        SubstructurePreviewSpec substructure = activeSubstructure();
        return Component.translatable(
                TRANSLATION_PREFIX + "variant.value",
                this.session.selection().activeSelection().variantIndex() + 1,
                substructure.variantCount());
    }

    private Component tierTitle() {
        return tierDomain().label();
    }

    private Component tierText() {
        PreviewTierDomain domain = tierDomain();
        int value = this.session.selection().activeSelection().tierSelections().get(domain.id());
        return domain.option(value).label();
    }

    private List<Component> selectedBlockTooltip() {
        PreviewCellSnapshot selected = this.session.selectedCell();
        if (selected == null) {
            return List.of(Component.translatable(TRANSLATION_PREFIX + "selected_block.none"));
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(
                TRANSLATION_PREFIX + "selected_block.position",
                selected.relativePosition().toShortString()));
        tooltip.add(Component.translatable(
                TRANSLATION_PREFIX + "selected_block.layer",
                this.session.selectedCellLayer() + 1));
        tooltip.add(Component.translatable(
                TRANSLATION_PREFIX + "selected_block.role",
                Component.translatable(TRANSLATION_PREFIX + "role." +
                        selected.predicate().role().name().toLowerCase(Locale.ROOT))));
        for (PreviewCandidate candidate : selected.predicate().candidates()) {
            tooltip.add(candidate.placementKey()
                    .<Component>map(key -> key.getDisplayName().copy())
                    .orElseGet(() -> Component.translatable("block.minecraft.air")));
        }
        return List.copyOf(tooltip);
    }

    private SubstructurePreviewSpec activeSubstructure() {
        return this.session.spec().substructure(this.session.structureKey());
    }

    private PreviewTierDomain tierDomain() {
        return activeSubstructure().tierDomains().getFirst();
    }

    private static Throwable mergeFailures(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
