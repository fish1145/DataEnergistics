package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** A read-only LDLib2 pan/zoom canvas. A single drawn surface avoids thousands of live widget/layout nodes. */
public final class CraftingPlanGraphCanvas extends GraphView {

    private final Surface surface = new Surface();
    private @Nullable CraftingPlanGraph graph;
    private @Nullable CraftingPlanGraphRenderer renderer;
    private @Nullable Layout graphLayout;
    private int selectedNode = -1;
    private final Int2ObjectMap<IntArrayList> incidentRoutes = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet highlighted = new IntOpenHashSet();
    private final IntOpenHashSet highlightedRoutes = new IntOpenHashSet();
    private final CraftingPlanSegmentSelection highlightedSegments = new CraftingPlanSegmentSelection();
    private int highlightedNode = -1;

    public CraftingPlanGraphCanvas() {
        graphViewStyle(style -> style.allowPan(true).allowZoom(true).minScale(0.1F).maxScale(10F).lodEnabled(true)
                .lodSimplifiedPixelScale(1.5F).lodBlockPixelScale(0.6F)
                .gridLineColor(CraftingPlanGraphPalette.GRID).gridAccentColor(CraftingPlanGraphPalette.GRID_ACCENT).gridLineWidth(0.5F));
        style(style -> style.backgroundTexture(GuiTextureGroup.of(new ColorRectTexture(CraftingPlanGraphPalette.CANVAS),
                new ColorBorderTexture(-1, CraftingPlanGraphPalette.FRAME))));
        this.surface.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(0));
        addContentChild(this.surface);
    }

    public UIElement surface() {
        return this.surface;
    }

    public void clearGraph() {
        this.graph = null;
        this.renderer = null;
        this.graphLayout = null;
        this.incidentRoutes.clear();
        this.selectedNode = -1;
        this.highlightedNode = -1;
        this.highlighted.clear();
        this.highlightedRoutes.clear();
        this.highlightedSegments.clear();
    }

    public void show(CraftingPlanGraph graph, Layout layout) {
        boolean presentationChanged = this.graph != graph || this.graphLayout != layout;
        if (this.graph != graph) this.renderer = new CraftingPlanGraphRenderer(graph);
        this.graph = graph;
        this.graphLayout = layout;
        if (presentationChanged) {
            indexRoutes(layout);
            rebuildHighlight(this.highlightedNode);
        }
        this.surface.layout(style -> style.width((float) layout.bounds().width()).height((float) layout.bounds().height()));
    }

    public void setViewport(float offsetX, float offsetY, float scale) {
        setOffsetX(offsetX);
        setOffsetY(offsetY);
        setScale(scale);
        refreshContentTransform();
    }

    public void fitGraph() {
        if (this.graphLayout == null) return;
        Bounds b = this.graphLayout.bounds();
        fit((float) b.x() - 12, (float) b.y() - 12, (float) (b.x() + b.width()) + 12,
                (float) (b.y() + b.height()) + 12, 0.1F);
    }

    public @Nullable PlacedNode nodeAt(double mouseX, double mouseY) {
        if (this.graphLayout == null || !isMouseOverContent((float) mouseX, (float) mouseY)) return null;
        Vector2f point = this.surface.getLocalMouse((float) mouseX, (float) mouseY);
        double x = point.x - this.surface.getPositionX();
        double y = point.y - this.surface.getPositionY();
        for (PlacedNode node : this.graphLayout.nodes()) if (node.contains(x, y)) return node;
        return null;
    }

    public Rect2i iconBounds(PlacedNode node) {
        Vector2f position = this.surface.localToWorld(new Vector2f(this.surface.getPositionX() + (float) node.x() + 6,
                this.surface.getPositionY() + (float) node.y() + 7));
        Vector2f size = this.surface.localToWorldNormal(new Vector2f(16, 16));
        return new Rect2i((int) Math.floor(position.x), (int) Math.floor(position.y),
                Math.max(1, (int) Math.ceil(size.x)), Math.max(1, (int) Math.ceil(size.y)));
    }

    public Vector2f screenPosition(PlacedNode node) {
        return this.surface.localToWorld(new Vector2f(this.surface.getPositionX() + (float) node.x(),
                this.surface.getPositionY() + (float) node.y()));
    }

    public void center(PlacedNode node) {
        setViewport((float) (node.x() + node.width() / 2 - getContentWidth() / getScale() / 2),
                (float) (node.y() + node.height() / 2 - getContentHeight() / getScale() / 2), getScale());
    }

    public void select(int nodeId) {
        this.selectedNode = nodeId;
    }

    public void highlight(@Nullable PlacedNode node) {
        if ((node == null ? -1 : node.id()) == this.highlightedNode) return;
        this.highlightedNode = node == null ? -1 : node.id();
        rebuildHighlight(this.highlightedNode);
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (isMouseOverContent(event.x, event.y) && (event.button == 2 || event.button == 0 && nodeAt(event.x, event.y) == null)) {
            startDrag(new DragOffset(getOffsetX(), getOffsetY()), null);
        }
    }

    @Override
    protected void onMouseWheel(UIEvent event) {
        if (!isMouseOverContent(event.x, event.y)) return;
        float oldScale = getScale();
        float scale = Mth.clamp(oldScale + event.deltaY * 0.1F, 0.1F, 10F);
        var local = getLocalMouse(event.x, event.y);
        float x = local.x - getPositionX();
        float y = local.y - getPositionY();
        setViewport(getOffsetX() + x / oldScale - x / scale, getOffsetY() + y / oldScale - y / scale, scale);
        event.stopPropagation();
    }

    /** Only the viewport is an exclusion area; offscreen graph bounds must not consume EMI sidebar space. */
    @Override
    public void appendExtraAreas(List<Rect2i> areas) {
        areas.add(new Rect2i((int) getPositionX(), (int) getPositionY(), (int) getSizeWidth(), (int) getSizeHeight()));
    }

    private final class Surface extends UIElement {

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            if (renderer == null || graphLayout == null) return;
            context.graphics.pose().pushPose();
            context.graphics.pose().translate(getPositionX(), getPositionY(), 0);
            renderer.draw(context.graphics, graphLayout, getLod(), true, selectedNode, highlighted, highlightedSegments,
                    new Bounds(getOffsetX(), getOffsetY(), CraftingPlanGraphCanvas.this.getContentWidth() / getScale(), CraftingPlanGraphCanvas.this.getContentHeight() / getScale()),
                    getPixelScale());
            context.graphics.pose().popPose();
        }

        @Override
        public void appendExtraAreas(List<Rect2i> areas) {
            // This logical surface can be much larger than the screen; its parent owns the exclusion viewport.
        }
    }

    private void indexRoutes(Layout layout) {
        this.incidentRoutes.clear();
        for (int routeId = 0; routeId < layout.edges().size(); routeId++) {
            var route = layout.edges().get(routeId);
            this.incidentRoutes.computeIfAbsent(route.source(), unused -> new IntArrayList()).add(routeId);
            if (route.target() != route.source()) {
                this.incidentRoutes.computeIfAbsent(route.target(), unused -> new IntArrayList()).add(routeId);
            }
        }
    }

    private void rebuildHighlight(int nodeId) {
        this.highlighted.clear();
        this.highlightedRoutes.clear();
        this.highlightedSegments.clear();
        if (nodeId < 0 || this.graph == null || this.graphLayout == null) return;
        for (var cycle : this.graph.cycles()) {
            if (cycle.nodeIds().contains(nodeId)) this.highlighted.addAll(cycle.nodeIds());
        }
        if (this.highlighted.isEmpty()) {
            IntArrayList routes = this.incidentRoutes.get(nodeId);
            if (routes != null) this.highlightedRoutes.addAll(routes);
        } else {
            for (int routeId = 0; routeId < this.graphLayout.edges().size(); routeId++) {
                var route = this.graphLayout.edges().get(routeId);
                if (this.highlighted.contains(route.source()) && this.highlighted.contains(route.target())) {
                    this.highlightedRoutes.add(routeId);
                }
            }
        }
        for (int routeId : this.highlightedRoutes) {
            for (var range : this.graphLayout.edges().get(routeId).segmentRanges()) this.highlightedSegments.add(range);
        }
    }
}
