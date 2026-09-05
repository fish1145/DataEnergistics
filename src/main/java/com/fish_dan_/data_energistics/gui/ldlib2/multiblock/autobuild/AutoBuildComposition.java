package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUi;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Host-neutral composition of one multiblock preview, its material viewport, layer rail, and adjustment controls.
 */
@ApiStatus.Internal
public final class AutoBuildComposition {

    private final StructurePreviewUi preview;
    private final Elements elements;
    private final AutoBuildMaterialGrid materialGrid;
    private final AutoBuildLayerScroller layerScroller;
    private final AutoBuildAdjustmentRail adjustmentRail;

    private AutoBuildComposition(Builder builder) {
        PreviewGeometry geometry = builder.requireGeometry();
        this.preview = builder.preview;
        this.elements = builder.elements;

        addBefore(
                this.elements.previewMount(),
                this.preview.panel(),
                this.elements.layerScroller(),
                "structure preview");
        this.preview.panel().useAuthoredComposition(geometry, builder.candidateSelectionVisible);

        this.materialGrid = new AutoBuildMaterialGrid(
                builder.requireMaterialGridId(),
                geometry.materialGrid(),
                builder.requireAmountFormatter(),
                builder.materialRole,
                this.elements.materialScroller());
        addBefore(
                this.elements.materialsMount(),
                this.materialGrid,
                this.elements.materialScroller(),
                "material grid");

        this.layerScroller = new AutoBuildLayerScroller(
                this.preview,
                this.elements.layerScroller(),
                geometry.layerTrack());
        this.adjustmentRail = new AutoBuildAdjustmentRail(this.elements.adjustmentControls());
    }

    /**
     * Starts a composition builder around elements already authored into one host window.
     */
    public static Builder builder(StructurePreviewUi preview, Elements elements) {
        return new Builder(preview, elements);
    }

    /**
     * Returns the root that owns the authored controls and runtime preview children.
     */
    public UIElement root() {
        return this.elements.root();
    }

    /**
     * Returns the single preview whose lifecycle remains owned by this composition.
     */
    public StructurePreviewUi preview() {
        return this.preview;
    }

    /**
     * Binds host-specific structure navigation callbacks without exposing the internal control tree.
     */
    public void bindStructureActions(StructureActions actions) {
        configureButton(this.elements.structureControls().previous(), actions.previous());
        configureButton(this.elements.structureControls().next(), actions.next());
    }

    /**
     * Localizes the editor-authored adjustment headings while leaving their geometry host-defined.
     */
    public void setAdjustmentHeadings(Component adjustmentContext,
                                      Component adjustmentValue) {
        this.elements.adjustmentControls().contextTitle().setText(adjustmentContext);
        this.elements.adjustmentControls().valueTitle().setText(adjustmentValue);
    }

    /**
     * Replaces the current structure name shown by the authored navigation rail.
     */
    public void setStructureTitle(Component title) {
        this.elements.structureControls().title().setText(title);
    }

    /**
     * Replaces the fixed virtual material viewport without changing its element count.
     */
    public void setMaterials(List<PreviewMaterial> materials) {
        this.materialGrid.setMaterials(materials);
    }

    /**
     * Rebinds the authored layer scrollbar to the current preview snapshot.
     */
    public void refreshLayers() {
        this.layerScroller.refresh();
    }

    /**
     * Replaces the ordered adjustment registry and retains the stable key when it remains available.
     */
    public void setAdjustments(List<Adjustment> adjustments, @Nullable String retainedStableKey) {
        this.adjustmentRail.setAdjustments(adjustments, retainedStableKey);
    }

    /**
     * Returns the selected adjustment's stable key, or {@code null} when no adjustment is registered.
     */
    @Nullable
    public String activeAdjustmentKey() {
        return this.adjustmentRail.activeStableKey();
    }

    /**
     * Supplies localized directional tooltips used by the adjustment rail.
     */
    public void setAdjustmentTooltips(Component contextLabel,
                                      UnaryOperator<Component> previous,
                                      UnaryOperator<Component> next) {
        this.adjustmentRail.setTooltipFactories(contextLabel, previous, next);
    }

    /** Updates the structure-navigation tooltips. */
    public void setStructureTooltips(Component previousStructure,
                                     Component nextStructure) {
        setTooltip(this.elements.structureControls().previous(), previousStructure);
        setTooltip(this.elements.structureControls().next(), nextStructure);
    }

    private static void configureButton(Button button, Runnable action) {
        button.setText(Component.empty());
        button.setOnClick(event -> action.run());
    }

    static void setTooltip(Button button, Component tooltip) {
        button.text.style(style -> style.tooltips(tooltip));
        button.style(style -> style.tooltips(tooltip));
    }

    private static void addBefore(UIElement parent,
                                  UIElement element,
                                  UIElement followingSibling,
                                  String description) {
        if (followingSibling.getParent() != parent) {
            throw new IllegalStateException("Editor-authored " + description + " scrollbar belongs to another panel");
        }
        int index = parent.getChildren().indexOf(followingSibling);
        if (index < 0) {
            throw new IllegalStateException("Editor-authored " + description + " scrollbar is missing");
        }
        parent.addChildAt(element, index);
    }

    /**
     * Builds one composition after all host-authored geometry and formatting dependencies are supplied.
     */
    public static final class Builder {

        private final StructurePreviewUi preview;
        private final Elements elements;
        @Nullable
        private PreviewGeometry geometry;
        @Nullable
        private String materialGridId;
        @Nullable
        private LongFunction<String> amountFormatter;
        private IngredientIO materialRole = IngredientIO.NONE;
        private boolean candidateSelectionVisible = true;

        private Builder(StructurePreviewUi preview, Elements elements) {
            this.preview = preview;
            this.elements = elements;
        }

        /**
         * Defines the exact content regions inside the host's editor-authored frames.
         */
        public Builder geometry(PreviewGeometry geometry) {
            this.geometry = geometry;
            return this;
        }

        /**
         * Defines the runtime material element id and the host's compact amount formatter.
         */
        public Builder materials(String materialGridId, LongFunction<String> amountFormatter) {
            this.materialGridId = materialGridId;
            this.amountFormatter = amountFormatter;
            this.materialRole = IngredientIO.NONE;
            return this;
        }

        /**
         * Publishes the fixed authored material grid as XEI recipe inputs while retaining its amount labels.
         */
        public Builder recipeInputs(String materialGridId, LongFunction<String> amountFormatter) {
            this.materialGridId = materialGridId;
            this.amountFormatter = amountFormatter;
            this.materialRole = IngredientIO.INPUT;
            return this;
        }

        /** Removes candidate-selection controls for a read-only authored composition. */
        public Builder withoutCandidateSelection() {
            this.candidateSelectionVisible = false;
            return this;
        }

        /**
         * Creates and mounts the complete composition.
         */
        public AutoBuildComposition build() {
            return new AutoBuildComposition(this);
        }

        private PreviewGeometry requireGeometry() {
            if (this.geometry == null) {
                throw new IllegalStateException("Automatic-build preview geometry was not configured");
            }
            return this.geometry;
        }

        private String requireMaterialGridId() {
            if (this.materialGridId == null || this.materialGridId.isBlank()) {
                throw new IllegalStateException("Automatic-build material grid id was not configured");
            }
            return this.materialGridId;
        }

        private LongFunction<String> requireAmountFormatter() {
            if (this.amountFormatter == null) {
                throw new IllegalStateException("Automatic-build material amount formatter was not configured");
            }
            return this.amountFormatter;
        }
    }

    /**
     * Host callbacks for the controls whose business meaning is not part of the generic composition.
     */
    public record StructureActions(Runnable previous,
                                   Runnable next) {}

    /**
     * One stable adjustment entry whose operations own their host-specific selection updates.
     */
    public record Adjustment(String stableKey,
                             Supplier<Component> label,
                             Supplier<Component> value,
                             Runnable previous,
                             Runnable next,
                             BooleanSupplier adjustable) {}

    /**
     * Exact local rectangle inside one editor-authored mount.
     */
    public record Region(int left, int top, int width, int height) {

        public Region {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Automatic-build regions require positive dimensions");
            }
        }
    }

    /** Exact horizontal span occupied by one editor-authored rail. */
    public record HorizontalSpan(int left, int width) {

        public HorizontalSpan {
            if (width <= 0) {
                throw new IllegalArgumentException("Automatic-build horizontal spans require a positive width");
            }
        }
    }

    /**
     * Host-specific geometry used to fit runtime content inside editor-authored frames.
     */
    public record PreviewGeometry(Region panel,
                                  Region scene,
                                  HorizontalSpan layerTrack,
                                  Region selectedBlock,
                                  Region materialGrid) {}

    /**
     * All host-authored elements consumed by one composition.
     */
    public record Elements(UIElement root,
                           UIElement previewMount,
                           Scroller.Horizontal layerScroller,
                           UIElement materialsMount,
                           Scroller.Vertical materialScroller,
                           StructureControls structureControls,
                           AdjustmentControls adjustmentControls) {}

    /**
     * Structure navigation elements authored by the host layout.
     */
    public record StructureControls(Button previous,
                                    Label title,
                                    Button next) {}

    /**
     * Adjustment-context and current-value elements authored by the host layout.
     */
    public record AdjustmentControls(Button previousContext,
                                     Label contextTitle,
                                     Label contextValue,
                                     Button nextContext,
                                     Button previousValue,
                                     Label valueTitle,
                                     Label valueValue,
                                     Button nextValue) {}
}
