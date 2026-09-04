package com.fish_dan_.data_energistics.integration.viewer.xei.multiblock;

import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild.AutoBuildComposition;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;

import java.util.List;

/** Strict runtime binding for the editor-authored XEI multiblock layout. */
final class MultiblockXeiLayout {

    private static final int ROOT_CHILD_COUNT = 5;
    private static final int ADJUSTMENT_CHILD_COUNT = 8;

    private MultiblockXeiLayout() {}

    static Layout require(UIElement root, String idPrefix) {
        List<UIElement> rootChildren = root.getChildren();
        if (rootChildren.size() != ROOT_CHILD_COUNT) {
            throw new IllegalStateException("XEI multiblock layout expected " + ROOT_CHILD_COUNT +
                    " authored root children, found " + rootChildren.size());
        }
        root.setId(idPrefix + "_root");

        UIElement previewMount = identify(
                authoredChild(rootChildren, 0, UIElement.class, "structure preview mount"),
                idPrefix + "_preview_mount");
        Scroller.Horizontal layerScroller = identify(
                authoredOnlyChild(previewMount, Scroller.Horizontal.class, "layer scroller"),
                idPrefix + "_layer_scroller");

        UIElement materialsMount = identify(
                authoredChild(rootChildren, 1, UIElement.class, "material mount"),
                idPrefix + "_materials");
        Scroller.Vertical materialScroller = identify(
                authoredOnlyChild(materialsMount, Scroller.Vertical.class, "material scroller"),
                idPrefix + "_materials_scroller");

        UIElement structureSelector = identify(
                authoredChild(rootChildren, 2, UIElement.class, "structure selector"),
                idPrefix + "_structure_selector");
        List<UIElement> structureControls = structureSelector.getChildren();
        if (structureControls.size() != 3) {
            throw new IllegalStateException("XEI multiblock structure selector expected 3 authored children, found " +
                    structureControls.size());
        }

        UIElement adjustment = identify(
                authoredChild(rootChildren, 3, UIElement.class, "adjustment panel"),
                idPrefix + "_adjustment");
        List<UIElement> adjustmentControls = adjustment.getChildren();
        if (adjustmentControls.size() != ADJUSTMENT_CHILD_COUNT) {
            throw new IllegalStateException("XEI multiblock adjustment layout expected " +
                    ADJUSTMENT_CHILD_COUNT + " authored children, found " + adjustmentControls.size());
        }

        Label title = identify(
                authoredChild(rootChildren, 4, Label.class, "window title"),
                idPrefix + "_title");
        return new Layout(
                root,
                previewMount,
                layerScroller,
                materialsMount,
                materialScroller,
                identify(
                        authoredChild(structureControls, 0, Button.class, "previous structure"),
                        idPrefix + "_structure_previous"),
                identifyAndClass(
                        authoredChild(structureControls, 2, Label.class, "structure title"),
                        idPrefix + "_structure_title",
                        "trinity-auto-build-structure-title"),
                identify(
                        authoredChild(structureControls, 1, Button.class, "next structure"),
                        idPrefix + "_structure_next"),
                identify(
                        authoredChild(adjustmentControls, 0, Button.class, "previous adjustment context"),
                        idPrefix + "_context_previous"),
                identifyAndClass(
                        authoredChild(adjustmentControls, 2, Label.class, "adjustment context title"),
                        idPrefix + "_context_title",
                        "trinity-auto-build-adjustment-title"),
                identifyAndClass(
                        authoredChild(adjustmentControls, 1, Label.class, "adjustment context value"),
                        idPrefix + "_context_value",
                        "trinity-auto-build-adjustment-value"),
                identify(
                        authoredChild(adjustmentControls, 3, Button.class, "next adjustment context"),
                        idPrefix + "_context_next"),
                identify(
                        authoredChild(adjustmentControls, 7, Button.class, "previous adjustment value"),
                        idPrefix + "_value_previous"),
                identifyAndClass(
                        authoredChild(adjustmentControls, 6, Label.class, "adjustment value title"),
                        idPrefix + "_value_title",
                        "trinity-auto-build-adjustment-title"),
                identifyAndClass(
                        authoredChild(adjustmentControls, 5, Label.class, "adjustment value"),
                        idPrefix + "_value_value",
                        "trinity-auto-build-adjustment-value"),
                identify(
                        authoredChild(adjustmentControls, 4, Button.class, "next adjustment value"),
                        idPrefix + "_value_next"),
                title);
    }

    private static <T extends UIElement> T authoredChild(List<UIElement> children,
                                                         int index,
                                                         Class<T> type,
                                                         String role) {
        if (index < 0 || index >= children.size()) {
            throw new IllegalStateException("XEI multiblock layout is missing " + role);
        }
        UIElement child = children.get(index);
        if (!type.isInstance(child)) {
            throw new IllegalStateException("XEI multiblock layout " + role + " has type " +
                    child.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(child);
    }

    private static <T extends UIElement> T authoredOnlyChild(UIElement parent, Class<T> type, String role) {
        List<UIElement> children = parent.getChildren();
        if (children.size() != 1) {
            throw new IllegalStateException("XEI multiblock layout " + role +
                    " expected one authored child, found " + children.size());
        }
        return authoredChild(children, 0, type, role);
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

    record Layout(UIElement root,
                  UIElement previewMount,
                  Scroller.Horizontal layerScroller,
                  UIElement materialsMount,
                  Scroller.Vertical materialScroller,
                  Button previousStructure,
                  Label structureTitle,
                  Button nextStructure,
                  Button previousContext,
                  Label contextTitle,
                  Label contextValue,
                  Button nextContext,
                  Button previousValue,
                  Label valueTitle,
                  Label valueValue,
                  Button nextValue,
                  Label title) {

        AutoBuildComposition.Elements elements() {
            return new AutoBuildComposition.Elements(
                    this.root,
                    this.previewMount,
                    this.layerScroller,
                    this.materialsMount,
                    this.materialScroller,
                    new AutoBuildComposition.StructureControls(
                            this.previousStructure,
                            this.structureTitle,
                            this.nextStructure),
                    new AutoBuildComposition.AdjustmentControls(
                            this.previousContext,
                            this.contextTitle,
                            this.contextValue,
                            this.nextContext,
                            this.previousValue,
                            this.valueTitle,
                            this.valueValue,
                            this.nextValue));
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
