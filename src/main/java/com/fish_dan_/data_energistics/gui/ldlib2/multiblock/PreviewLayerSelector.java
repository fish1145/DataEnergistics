package com.fish_dan_.data_energistics.gui.ldlib2.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewVisibleLayer;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the complete logical-layer command surface and closes its root-mounted selector popup on removal.
 */
final class PreviewLayerSelector extends UIElement {

    private static final String TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final int TITLE_HEIGHT = 10;
    private static final int ALL_BUTTON_WIDTH = 18;
    private static final int ARROW_WIDTH = 14;
    private static final int SELECTOR_LEFT = ALL_BUTTON_WIDTH + ARROW_WIDTH;
    private static final int SELECTOR_WIDTH = 46;
    private static final int CONTROL_HEIGHT = StructurePreviewPresentation.CONTROL_RAIL_HEIGHT - TITLE_HEIGHT;

    private final StructurePreviewSession session;
    private final Runnable layerChangeListener;
    private final Selector<PreviewVisibleLayer> selector;
    private List<PreviewVisibleLayer> candidates = List.of();

    PreviewLayerSelector(String idPrefix,
                         StructurePreviewSession session,
                         Runnable layerChangeListener,
                         int width) {
        if (idPrefix == null || idPrefix.isBlank() || session == null || layerChangeListener == null ||
                width < SELECTOR_LEFT + SELECTOR_WIDTH + ARROW_WIDTH) {
            throw new IllegalArgumentException("Preview layer selector arguments cannot be null, blank, or too narrow");
        }
        this.session = session;
        this.layerChangeListener = layerChangeListener;
        this.selector = createSelector(idPrefix);

        setId(idPrefix + "_layer");
        layout(layout -> layout.width(width).height(StructurePreviewPresentation.CONTROL_RAIL_HEIGHT));
        addChildren(
                titleLabel(width),
                allLayersButton(idPrefix),
                arrowButton(idPrefix + StructurePreviewPanel.LAYER_PREVIOUS_SUFFIX, Icons.LEFT_ARROW_NO_BAR,
                        this::previousLayer, ALL_BUTTON_WIDTH),
                this.selector,
                arrowButton(idPrefix + StructurePreviewPanel.LAYER_NEXT_SUFFIX, Icons.RIGHT_ARROW_NO_BAR,
                        this::nextLayer, SELECTOR_LEFT + SELECTOR_WIDTH));
        refresh();
    }

    /** Rebuilds the exact ALL-plus-layer candidate domain after a new structure snapshot is installed. */
    void refresh() {
        List<PreviewVisibleLayer> updated = new ArrayList<>(this.session.snapshot().layers().size() + 1);
        updated.add(PreviewVisibleLayer.all());
        for (int layerIndex = 0; layerIndex < this.session.snapshot().layers().size(); layerIndex++) {
            updated.add(PreviewVisibleLayer.logicalLayer(layerIndex));
        }
        this.candidates = List.copyOf(updated);
        this.selector.setCandidates(this.candidates);
        refreshSelected();
    }

    /** Selects one exact menu candidate through the same view-only callback used by pointer interaction. */
    void selectVisibleLayer(PreviewVisibleLayer visibleLayer) {
        PreviewVisibleLayer candidate = requireCandidate(visibleLayer);
        PreviewVisibleLayer before = this.session.viewState().visibleLayer();
        if (candidate instanceof PreviewVisibleLayer.All) {
            this.session.showAllLayers();
        } else {
            this.session.showLayer(((PreviewVisibleLayer.LogicalLayer) candidate).layerIndex());
        }
        this.selector.setSelected(candidate, false);
        notifyLayerChange(before);
    }

    /** Selects one exact zero-based logical layer from the current popup domain. */
    void showLayer(int layerIndex) {
        selectVisibleLayer(PreviewVisibleLayer.logicalLayer(layerIndex));
    }

    /** Returns the immutable candidate list currently rendered by the popup. */
    List<PreviewVisibleLayer> candidates() {
        return this.candidates;
    }

    /** Returns whether the selector dialog is currently attached to the ModularUI root. */
    boolean isPopupOpen() {
        return this.selector.isOpen();
    }

    @Override
    protected void onRemoved() {
        Throwable failure = null;
        try {
            this.selector.hide();
        } catch (RuntimeException | Error popupFailure) {
            failure = popupFailure;
        }
        try {
            super.onRemoved();
        } catch (RuntimeException | Error removalFailure) {
            failure = mergeFailures(failure, removalFailure);
        }
        rethrow(failure);
    }

    private Selector<PreviewVisibleLayer> createSelector(String idPrefix) {
        Selector<PreviewVisibleLayer> created = new Selector<>();
        created.setId(idPrefix + StructurePreviewPanel.LAYER_SELECTOR_SUFFIX);
        created.dialog.addClass(HostUiExtension.TRANSIENT_POPUP_CLASS);
        Style.importantPipeline(
                created.dialog.getStyle(),
                style -> style.zIndex(HostUiExtension.TRANSIENT_POPUP_Z));
        created.setCandidateUIProvider(this::candidateLabel);
        created.selectorStyle(style -> style
                .maxItemCount(8)
                .scrollerViewHeight(90)
                .showOverlay(true)
                .closeAfterSelect(true));
        created.setOnValueChanged(this::selectVisibleLayer);
        created.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(SELECTOR_LEFT)
                .top(TITLE_HEIGHT)
                .width(SELECTOR_WIDTH)
                .height(CONTROL_HEIGHT));
        return created;
    }

    private Label titleLabel(int width) {
        Label label = new Label();
        label.setText(Component.translatable(TRANSLATION_PREFIX + "layer"));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.0f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        label.setOverflowVisible(false);
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(width)
                .height(TITLE_HEIGHT));
        return label;
    }

    private Button allLayersButton(String idPrefix) {
        Button button = new Button();
        button.setId(idPrefix + StructurePreviewPanel.LAYER_ALL_SUFFIX);
        button.noText();
        button.addPreIcon(Icons.GRID);
        button.setOnClick(event -> showAllLayers());
        button.style(style -> style.tooltips(Component.translatable(TRANSLATION_PREFIX + "layer.all")));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(TITLE_HEIGHT)
                .width(ALL_BUTTON_WIDTH)
                .height(CONTROL_HEIGHT));
        return button;
    }

    private Button arrowButton(String id, IGuiTexture icon, Runnable action, int left) {
        Button button = new Button();
        button.setId(id);
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> action.run());
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(TITLE_HEIGHT)
                .width(ARROW_WIDTH)
                .height(CONTROL_HEIGHT));
        return button;
    }

    private Label candidateLabel(@Nullable PreviewVisibleLayer visibleLayer) {
        Label label = new Label();
        label.setText(visibleLayer == null ? Component.empty() : layerText(visibleLayer));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.0f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        label.setOverflowVisible(false);
        label.layout(layout -> layout.widthPercent(100).height(14));
        return label;
    }

    private Component layerText(PreviewVisibleLayer visibleLayer) {
        if (visibleLayer instanceof PreviewVisibleLayer.All) {
            return Component.translatable(TRANSLATION_PREFIX + "layer.all");
        }
        int layerIndex = ((PreviewVisibleLayer.LogicalLayer) visibleLayer).layerIndex();
        return Component.translatable(
                TRANSLATION_PREFIX + "layer.value",
                layerIndex + 1,
                this.session.snapshot().layers().size());
    }

    void showAllLayers() {
        changeLayer(this.session::showAllLayers);
    }

    void previousLayer() {
        changeLayer(this.session::previousLayer);
    }

    void nextLayer() {
        changeLayer(this.session::nextLayer);
    }

    private void changeLayer(Runnable change) {
        PreviewVisibleLayer before = this.session.viewState().visibleLayer();
        change.run();
        refreshSelected();
        notifyLayerChange(before);
    }

    private void refreshSelected() {
        this.selector.setSelected(requireCandidate(this.session.viewState().visibleLayer()), false);
    }

    private PreviewVisibleLayer requireCandidate(PreviewVisibleLayer visibleLayer) {
        if (visibleLayer == null) {
            throw new IllegalArgumentException("Preview visible layer cannot be null");
        }
        for (PreviewVisibleLayer candidate : this.candidates) {
            if (candidate.equals(visibleLayer)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Preview visible layer is outside the current snapshot: " + visibleLayer);
    }

    private void notifyLayerChange(PreviewVisibleLayer before) {
        if (!before.equals(this.session.viewState().visibleLayer())) {
            this.layerChangeListener.run();
        }
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
