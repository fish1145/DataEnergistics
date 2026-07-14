package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewVisibleLayer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Host-neutral LDLib2 preview surface containing one scene, complete selection controls, and material diagnostics.
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
     * Suffix used by the selected-block detail label.
     */
    public static final String SELECTED_BLOCK_SUFFIX = "_selected_block";
    /**
     * Suffix used by the canonical material scroller.
     */
    public static final String MATERIALS_SUFFIX = "_materials";

    private static final int WIDTH = 180;
    private static final int HEIGHT = 208;
    private static final int SCENE_WIDTH = 124;
    private static final int SCENE_HEIGHT = 140;
    private static final int CONTROLS_LEFT = 126;
    private static final int CONTROLS_WIDTH = 54;
    private static final int CONTROL_HEIGHT = 14;

    private final String idPrefix;
    private final StructurePreviewSession session;
    private final StructurePreviewSceneElement scene;
    private final ScrollerView repeatControls;
    private final ScrollerView materials;
    private Consumer<PreviewSelection> selectionChangeListener = selection -> {};
    private boolean selectionChangeListenerRegistered;
    @Nullable
    private StructurePreviewSceneBinding sceneBinding;

    StructurePreviewPanel(String idPrefix, StructurePreviewSession session) {
        if (idPrefix == null || idPrefix.isBlank() || session == null) {
            throw new IllegalArgumentException("Structure preview panel arguments cannot be null or blank");
        }
        this.idPrefix = idPrefix;
        this.session = session;
        this.scene = createScene();
        this.repeatControls = createRepeatControls();
        this.materials = createMaterials();

        setId(idPrefix);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .width(WIDTH)
                .height(HEIGHT));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        addChildren(this.scene, createControls(), selectedBlockLabel(), this.materials);
        refreshRepeatControls();
        refreshMaterials();
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
     * Activates another allowed named structure through the same refresh and listener path as visible controls.
     */
    public void selectStructure(String structureKey) {
        changeSelection(() -> this.session.selectStructure(structureKey));
    }

    void bindScene(StructurePreviewSceneBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("Structure preview scene binding cannot be null");
        }
        if (this.sceneBinding != null) {
            throw new IllegalStateException("Structure preview scene can only be bound once");
        }
        this.sceneBinding = binding;
        refreshScene();
    }

    void selectBlock(BlockPos position) {
        this.session.selectBlock(position);
    }

    private StructurePreviewSceneElement createScene() {
        StructurePreviewSceneElement element = new StructurePreviewSceneElement();
        element.setId(this.idPrefix + SCENE_SUFFIX);
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(SCENE_WIDTH)
                .height(SCENE_HEIGHT));
        return element;
    }

    private UIElement createControls() {
        UIElement controls = new UIElement();
        controls.setId(this.idPrefix + "_controls");
        controls.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(CONTROLS_LEFT)
                .top(0)
                .width(CONTROLS_WIDTH)
                .height(SCENE_HEIGHT));
        controls.addChildren(
                selectorRow(
                        0,
                        iconButton(
                                this.idPrefix + VARIANT_PREVIOUS_SUFFIX,
                                Icons.LEFT_ARROW_NO_BAR,
                                Component.literal("Previous variant"),
                                () -> changeSelection(this.session::previousVariant)),
                        valueLabel(() -> "P:" + this.session.selection().activeSelection().variantIndex()),
                        iconButton(
                                this.idPrefix + VARIANT_NEXT_SUFFIX,
                                Icons.RIGHT_ARROW_NO_BAR,
                                Component.literal("Next variant"),
                                () -> changeSelection(this.session::nextVariant))),
                selectorRow(
                        17,
                        iconButton(
                                this.idPrefix + TIER_PREVIOUS_SUFFIX,
                                Icons.LEFT_ARROW_NO_BAR,
                                Component.literal("Previous tier"),
                                () -> changeSelection(this.session::previousTier)),
                        valueLabel(this::tierText),
                        iconButton(
                                this.idPrefix + TIER_NEXT_SUFFIX,
                                Icons.RIGHT_ARROW_NO_BAR,
                                Component.literal("Next tier"),
                                () -> changeSelection(this.session::nextTier))),
                this.repeatControls,
                layerControls());
        return controls;
    }

    private ScrollerView createRepeatControls() {
        ScrollerView repeatControls = new ScrollerView();
        repeatControls.setId(this.idPrefix + "_repeat_controls");
        repeatControls.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(34)
                .width(CONTROLS_WIDTH)
                .height(52));
        repeatControls.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        repeatControls.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .scrollerViewStyle(0));
        repeatControls.viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(0))
                .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        return repeatControls;
    }

    private UIElement layerControls() {
        UIElement controls = new UIElement();
        controls.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(88)
                .width(CONTROLS_WIDTH)
                .height(50));
        Button all = new Button();
        all.setId(this.idPrefix + LAYER_ALL_SUFFIX);
        all.setText("ALL");
        all.setOnClick(event -> changeLayer(this.session::showAllLayers));
        all.style(style -> style.tooltips(Component.literal("Show all logical layers")));
        all.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(CONTROLS_WIDTH)
                .height(CONTROL_HEIGHT));
        controls.addChildren(
                all,
                selectorRow(
                        17,
                        iconButton(
                                this.idPrefix + LAYER_PREVIOUS_SUFFIX,
                                Icons.LEFT_ARROW_NO_BAR,
                                Component.literal("Previous logical layer"),
                                () -> changeLayer(this.session::previousLayer)),
                        valueLabel(this::layerText),
                        iconButton(
                                this.idPrefix + LAYER_NEXT_SUFFIX,
                                Icons.RIGHT_ARROW_NO_BAR,
                                Component.literal("Next logical layer"),
                                () -> changeLayer(this.session::nextLayer))));
        return controls;
    }

    private Label selectedBlockLabel() {
        Label label = new Label();
        label.setId(this.idPrefix + SELECTED_BLOCK_SUFFIX);
        label.bindDataSource(SupplierDataSource.of(this::selectedBlockText));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.5f)
                .textWrap(TextWrap.WRAP)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(142)
                .width(WIDTH)
                .height(22));
        label.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> tooltip = selectedBlockTooltip();
            if (!tooltip.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(tooltip, null, null, null);
            }
        });
        return label;
    }

    private ScrollerView createMaterials() {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(this.idPrefix + MATERIALS_SUFFIX);
        scroller.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(166)
                .width(WIDTH)
                .height(42));
        scroller.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        scroller.viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(1).paddingBottom(3))
                .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        scroller.viewContainer(viewContainer -> viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .height(20)));
        return scroller;
    }

    private UIElement materialEntry(PreviewMaterial material, int index) {
        UIElement entry = new UIElement();
        entry.setId(this.idPrefix + "_material_" + index);
        entry.layout(layout -> layout.width(48).height(20));
        ItemStack displayStack = material.key().toStack(1);
        ItemSlot slot = new ItemSlot();
        slot.setItem(displayStack);
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0));
        Label amount = new Label();
        amount.setText(Component.literal("x" + material.amount()));
        amount.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.5f)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        amount.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(19)
                .top(0)
                .width(29)
                .height(18));
        entry.addChildren(slot, amount);
        return entry;
    }

    private UIElement selectorRow(int top, Button previous, Label value, Button next) {
        UIElement row = new UIElement();
        row.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(top)
                .width(CONTROLS_WIDTH)
                .height(CONTROL_HEIGHT));
        previous.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(CONTROL_HEIGHT)
                .height(CONTROL_HEIGHT));
        value.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(CONTROL_HEIGHT)
                .top(0)
                .width(CONTROLS_WIDTH - CONTROL_HEIGHT * 2)
                .height(CONTROL_HEIGHT));
        next.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(CONTROLS_WIDTH - CONTROL_HEIGHT)
                .top(0)
                .width(CONTROL_HEIGHT)
                .height(CONTROL_HEIGHT));
        row.addChildren(previous, value, next);
        return row;
    }

    private static Button iconButton(String id, IGuiTexture icon, Component tooltip, Runnable action) {
        Button button = new Button();
        button.setId(id);
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> action.run());
        button.style(style -> style.tooltips(tooltip));
        return button;
    }

    private static Label valueLabel(Supplier<String> text) {
        Label label = new Label();
        label.bindDataSource(SupplierDataSource.of(() -> Component.literal(text.get())));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.5f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        return label;
    }

    private void changeSelection(Runnable change) {
        PreviewSelection previous = this.session.selection();
        change.run();
        PreviewSelection current = this.session.selection();
        if (previous.equals(current)) {
            return;
        }
        if (!previous.activeSubstructureId().equals(current.activeSubstructureId()) ||
                previous.activeSelection().variantIndex() != current.activeSelection().variantIndex()) {
            refreshRepeatControls();
        }
        refreshMaterials();
        refreshScene();
        this.selectionChangeListener.accept(current);
    }

    private void changeLayer(Runnable change) {
        change.run();
        refreshScene();
    }

    private void refreshScene() {
        if (this.sceneBinding != null) {
            this.sceneBinding.refresh(this.session.snapshot(), this.session.viewState());
        }
    }

    private void refreshMaterials() {
        this.materials.clearAllScrollViewChildren();
        List<PreviewMaterial> inputs = this.session.recipeView().inputs();
        for (int index = 0; index < inputs.size(); index++) {
            this.materials.addScrollViewChild(materialEntry(inputs.get(index), index));
        }
    }

    private void refreshRepeatControls() {
        this.repeatControls.clearAllScrollViewChildren();
        List<Integer> variableUnits = this.session.variableRepeatUnits();
        if (variableUnits.isEmpty()) {
            this.repeatControls.addScrollViewChild(valueLabel(() -> "R:-"));
            return;
        }
        for (int unitIndex : variableUnits) {
            this.repeatControls.addScrollViewChild(selectorRow(
                    0,
                    iconButton(
                            this.idPrefix + REPEAT_SUFFIX + unitIndex + "_previous",
                            Icons.LEFT_ARROW_NO_BAR,
                            Component.literal("Previous repeat count"),
                            () -> changeSelection(() -> this.session.previousRepeat(unitIndex))),
                    valueLabel(() -> "R" + unitIndex + ":" +
                            this.session.selection().activeSelection().repeatCounts().get(unitIndex)),
                    iconButton(
                            this.idPrefix + REPEAT_SUFFIX + unitIndex + "_next",
                            Icons.RIGHT_ARROW_NO_BAR,
                            Component.literal("Next repeat count"),
                            () -> changeSelection(() -> this.session.nextRepeat(unitIndex)))));
        }
    }

    private String tierText() {
        PreviewTierDomain domain = this.session.spec().substructure(this.session.structureKey()).tierDomains().getFirst();
        return "T:" + this.session.selection().activeSelection().tierSelections().get(domain.id());
    }

    private String layerText() {
        PreviewVisibleLayer visibleLayer = this.session.viewState().visibleLayer();
        if (visibleLayer instanceof PreviewVisibleLayer.All) {
            return "ALL";
        }
        return "L:" + ((PreviewVisibleLayer.LogicalLayer) visibleLayer).layerIndex();
    }

    private Component selectedBlockText() {
        PreviewCellSnapshot selected = this.session.selectedCell();
        if (selected == null) {
            return Component.literal("Block: -");
        }
        return Component.literal(selected.relativePosition().toShortString() + " L:" +
                this.session.selectedCellLayer() + " " + selected.predicate().role().name());
    }

    private List<Component> selectedBlockTooltip() {
        PreviewCellSnapshot selected = this.session.selectedCell();
        if (selected == null) {
            return List.of();
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(selectedBlockText());
        for (PreviewCandidate candidate : selected.predicate().candidates()) {
            if (candidate.concrete()) {
                tooltip.add(candidate.placementKey().orElseThrow().getDisplayName());
            } else {
                tooltip.add(Component.literal("Air"));
            }
        }
        return List.copyOf(tooltip);
    }
}
