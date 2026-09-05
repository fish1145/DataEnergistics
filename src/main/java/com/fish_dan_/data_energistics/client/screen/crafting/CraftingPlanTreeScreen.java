package com.fish_dan_.data_energistics.client.screen.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.crafting.tree.export.CraftingPlanGraphPngExport;
import com.fish_dan_.data_energistics.client.crafting.tree.export.CraftingPlanGraphSvgExport;
import com.fish_dan_.data_energistics.client.crafting.tree.preferences.CraftingPlanTreePreferences;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphCanvas;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphPalette;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRenderer;
import com.fish_dan_.data_energistics.client.crafting.tree.tooltip.CraftingPlanNodeTooltip;
import com.fish_dan_.data_energistics.client.crafting.tree.viewer.CraftingPlanIngredientViewers;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsModularTexture;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.client.util.TrinityDurationFormatter;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanLayoutMode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRadialLayout;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.Expansion;
import com.fish_dan_.data_energistics.gui.ldlib2.crafting.tree.CraftingPlanTreeUi;
import com.fish_dan_.data_energistics.menu.crafting.tree.CraftingPlanTreeMenu;
import com.fish_dan_.data_energistics.network.crafting.tree.action.CraftingPlanTreeActionPayload.Action;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/** New Minecraft container screen with a private LDLib2 root; it neither extends nor wraps an AE2 screen. */
public final class CraftingPlanTreeScreen extends AbstractContainerScreen<CraftingPlanTreeMenu> implements GenericStackLookupScreen {

    private CraftingPlanTreePreferences preferences = CraftingPlanTreePreferences.load();
    private boolean missingOnly = this.preferences.missingOnly();
    private boolean compact = this.preferences.compact();
    private CraftingPlanLayoutMode layoutMode = this.preferences.layoutMode();
    private final Map<String, Button> buttons = new Object2ObjectLinkedOpenHashMap<>();
    private @Nullable ModularUI modularUI;
    private @Nullable CraftingPlanGraphCanvas canvas;
    private @Nullable Label titleLabel;
    private @Nullable Label statusLabel;
    private @Nullable ExecutorService layoutExecutor;
    private @Nullable CompletableFuture<Prepared> pending;
    private @Nullable CompletableFuture<Selection> requestedSelection;
    private @Nullable CraftingPlanGraph graph;
    private @Nullable CraftingPlanNodeTooltip nodeTooltip;
    private @Nullable Prepared prepared;
    private @Nullable Layout exportLayout;
    private Component localStatus = Component.empty();
    private int selected = -1;
    private int anchorId = -1;
    private @Nullable Vector2f anchorPosition;
    private boolean fitPending;
    private boolean exportingFull;
    private boolean exportingSvg;
    private boolean preferencesOpen;
    private boolean screenshotOpen;
    private double middleStartX;
    private double middleStartY;
    private boolean middleDown;
    private boolean middleDragged;
    private long observedRevision = -1;
    private boolean savedViewport;
    private float savedOffsetX;
    private float savedOffsetY;
    private float savedScale = 1;
    private boolean graphHasMissing;

    public CraftingPlanTreeScreen(CraftingPlanTreeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        saveViewport();
        if (this.modularUI != null) this.modularUI.onRemoved();
        this.imageWidth = this.width >= 420 ? Math.min(720, this.width - 160) : Math.max(180, this.width - 20);
        this.imageHeight = Math.clamp(this.height - 20, 180, 560);
        super.init();
        if (this.layoutExecutor == null || this.layoutExecutor.isShutdown()) this.layoutExecutor = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "data-energistics-plan-tree-layout");
                    thread.setDaemon(true);
                    return thread;
                });
        try {
            mountUi();
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error("Cannot create plan-tree UI for session {}", this.menu.sessionId(), failure);
            feedback(text("graph_failed"));
            this.menu.request(Action.RETURN_LIST);
        }
    }

    private void mountUi() {
        this.buttons.clear();
        UI ui = CraftingPlanTreeUi.load();
        ui.rootElement.layout(layout -> layout.width(this.imageWidth).height(this.imageHeight));
        ui.rootElement.style(style -> style.backgroundTexture(new DataEnergisticsModularTexture()));
        this.titleLabel = CraftingPlanTreeUi.element(ui, "title", Label.class);
        this.statusLabel = CraftingPlanTreeUi.element(ui, "status", Label.class);
        place(this.titleLabel, 7, 6, this.imageWidth - 14, 14);
        place(this.statusLabel, 7, 47, this.imageWidth - 14, 25);
        Label help = CraftingPlanTreeUi.element(ui, "help", Label.class);
        help.setText(text("help"));
        place(help, 7, this.imageHeight - 68, this.imageWidth - 14, 12);
        String[] toolbar = { "missing", "expand", "collapse", "fit", "density", "preferences", "screenshot", "list" };
        float buttonWidth = (this.imageWidth - 12F) / toolbar.length;
        for (int i = 0; i < toolbar.length; i++) place(button(ui, toolbar[i]), 6 + i * buttonWidth, 24, buttonWidth - 2, 18);
        place(button(ui, "cpu"), 6, this.imageHeight - 53, this.imageWidth - 12, 20);
        place(button(ui, "cancel"), 6, this.imageHeight - 27, 80, 20);
        place(button(ui, "replan"), (this.imageWidth - 80) / 2F, this.imageHeight - 27, 80, 20);
        place(button(ui, "start"), this.imageWidth - 86, this.imageHeight - 27, 80, 20);
        int popupX = Math.max(6, this.imageWidth - 190);
        String[] popup = { "export_visible", "export_full", "export_svg_visible", "export_svg_full",
                "pref_missing", "pref_amounts", "pref_budget", "pref_layout" };
        for (int i = 0; i < popup.length; i++) {
            Button button = button(ui, popup[i]);
            place(button, popupX, 44 + (i < 4 ? i : i - 4) * 22, 180, 20);
            button.setDisplay(false);
        }
        this.canvas = new CraftingPlanGraphCanvas();
        place(this.canvas, 6, 77, this.imageWidth - 12, this.imageHeight - 151);
        ui.rootElement.addChildAt(this.canvas, 0);
        CraftingPlanIngredientViewers.bind(this.canvas.surface(), this::hoveredIngredient);
        bindButtons();
        this.modularUI = new ModularUI(ui, this.menu.getPlayer());
        this.modularUI.shouldCloseOnEsc(false).shouldCloseOnKeyInventory(false);
        this.modularUI.setScreenAndInit(this);
        addRenderableWidget(this.modularUI.getWidget());
        setFocused(this.modularUI.getWidget());
        if (this.prepared != null && this.graph != null) {
            this.canvas.show(this.graph, this.prepared.layout());
            if (this.savedViewport) this.canvas.setViewport(this.savedOffsetX, this.savedOffsetY, this.savedScale);
            else this.fitPending = true;
        }
        updateLabels();
    }

    private Button button(UI ui, String id) {
        Button button = CraftingPlanTreeUi.element(ui, id, Button.class);
        button.setText(text(id));
        button.style(style -> style.tooltips(text(id + ".tooltip")));
        button.buttonStyle(style -> style.baseTexture(GuiTextureGroup.of(
                new ColorRectTexture(CraftingPlanGraphPalette.BUTTON), new ColorBorderTexture(-1, CraftingPlanGraphPalette.FRAME)))
                .hoverTexture(GuiTextureGroup.of(new ColorRectTexture(CraftingPlanGraphPalette.BUTTON_HOVER),
                        new ColorBorderTexture(-1, CraftingPlanGraphPalette.ACCENT)))
                .pressedTexture(GuiTextureGroup.of(new ColorRectTexture(CraftingPlanGraphPalette.SELECTED),
                        new ColorBorderTexture(-1, CraftingPlanGraphPalette.ACCENT))));
        this.buttons.put(id, button);
        return button;
    }

    private static void place(UIElement element, float x, float y, float width, float height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
    }

    private void bindButtons() {
        this.buttons.get("cancel").setOnClick(event -> this.menu.request(Action.CANCEL));
        this.buttons.get("start").setOnClick(event -> { if (this.menu.startable) this.menu.request(Action.START); });
        this.buttons.get("replan").setOnClick(event -> this.menu.request(Action.REPLAN));
        this.buttons.get("list").setOnClick(event -> this.menu.request(Action.RETURN_LIST));
        this.buttons.get("cpu").setOnClick(event -> this.menu.request(Action.NEXT_CPU));
        this.buttons.get("cpu").addEventListener(UIEvents.MOUSE_DOWN, event -> { if (event.button == 1) this.menu.request(Action.PREVIOUS_CPU); });
        this.buttons.get("missing").setOnClick(event -> {
            this.missingOnly = !this.missingOnly;
            schedule(UnaryOperator.identity(), false);
        });
        this.buttons.get("density").setOnClick(event -> {
            this.compact = !this.compact;
            savePreferences();
            schedule(UnaryOperator.identity(), false);
        });
        this.buttons.get("expand").setOnClick(event -> schedule(selection -> new Selection(selection.projection(), Expansion.empty()), false));
        this.buttons.get("collapse").setOnClick(event -> schedule(selection -> new Selection(selection.projection(), selection.projection().recursiveCollapsed(
                selection.expansion(), selection.projection().graph().rootId(), true)), false));
        this.buttons.get("fit").setOnClick(event -> { if (this.canvas != null) this.canvas.fitGraph(); });
        this.buttons.get("preferences").setOnClick(event -> {
            this.preferencesOpen = !this.preferencesOpen;
            this.screenshotOpen = false;
            updatePopups();
        });
        this.buttons.get("screenshot").setOnClick(event -> {
            this.screenshotOpen = !this.screenshotOpen;
            this.preferencesOpen = false;
            updatePopups();
        });
        this.buttons.get("export_visible").setOnClick(event -> export(false, false));
        this.buttons.get("export_full").setOnClick(event -> export(true, false));
        this.buttons.get("export_svg_visible").setOnClick(event -> export(false, true));
        this.buttons.get("export_svg_full").setOnClick(event -> export(true, true));
        this.buttons.get("pref_missing").setOnClick(event -> {
            this.preferences = new CraftingPlanTreePreferences(this.preferences.autoExpandBudget(), this.compact,
                    !this.preferences.missingOnly(), this.preferences.screenshotAmounts(), this.layoutMode);
            this.preferences.save();
            updateLabels();
        });
        this.buttons.get("pref_amounts").setOnClick(event -> {
            this.preferences = new CraftingPlanTreePreferences(this.preferences.autoExpandBudget(), this.compact,
                    this.preferences.missingOnly(), !this.preferences.screenshotAmounts(), this.layoutMode);
            this.preferences.save();
            updateLabels();
        });
        this.buttons.get("pref_budget").setOnClick(event -> {
            int budget = this.preferences.autoExpandBudget() >= 4096 ? 64 : this.preferences.autoExpandBudget() * 2;
            this.preferences = new CraftingPlanTreePreferences(budget, this.compact,
                    this.preferences.missingOnly(), this.preferences.screenshotAmounts(), this.layoutMode);
            this.preferences.save();
            updateLabels();
        });
        this.buttons.get("pref_layout").setOnClick(event -> {
            this.layoutMode = this.layoutMode == CraftingPlanLayoutMode.LAYERED ? CraftingPlanLayoutMode.RADIAL : CraftingPlanLayoutMode.LAYERED;
            savePreferences();
            this.exportLayout = null;
            this.fitPending = true;
            schedule(UnaryOperator.identity(), false);
            updateLabels();
        });
    }

    private void savePreferences() {
        this.preferences = new CraftingPlanTreePreferences(this.preferences.autoExpandBudget(), this.compact,
                this.preferences.missingOnly(), this.preferences.screenshotAmounts(), this.layoutMode);
        this.preferences.save();
    }

    private void updatePopups() {
        this.buttons.get("export_visible").setDisplay(this.screenshotOpen);
        this.buttons.get("export_full").setDisplay(this.screenshotOpen);
        this.buttons.get("export_svg_visible").setDisplay(this.screenshotOpen);
        this.buttons.get("export_svg_full").setDisplay(this.screenshotOpen);
        this.buttons.get("pref_missing").setDisplay(this.preferencesOpen);
        this.buttons.get("pref_amounts").setDisplay(this.preferencesOpen);
        this.buttons.get("pref_budget").setDisplay(this.preferencesOpen);
        this.buttons.get("pref_layout").setDisplay(this.preferencesOpen);
    }

    private void schedule(UnaryOperator<Selection> fold, boolean reset) {
        if (this.graph == null || this.layoutExecutor == null) return;
        if (this.pending != null) this.pending.cancel(true);
        this.exportingFull = false;
        CraftingPlanGraph graph = this.graph;
        boolean missing = this.missingOnly;
        boolean dense = this.compact;
        CraftingPlanLayoutMode mode = this.layoutMode;
        int budget = this.preferences.autoExpandBudget();
        this.localStatus = text("layout_loading");
        if (reset || this.requestedSelection == null) {
            if (this.requestedSelection != null) this.requestedSelection.cancel(true);
            this.requestedSelection = CompletableFuture.supplyAsync(() -> {
                CraftingPlanGraphView view = new CraftingPlanGraphView(graph);
                return new Selection(view, view.initialExpansion(budget));
            }, this.layoutExecutor);
        }
        // User intent is sequenced independently of the last displayed layout. Cancelling an obsolete layout
        // must not discard a preceding expand action, even while the initial projection is still being built.
        this.requestedSelection = this.requestedSelection.thenApplyAsync(fold, this.layoutExecutor);
        this.pending = this.requestedSelection.thenApplyAsync(selection -> new Prepared(selection.projection(),
                layout(selection.projection().visible(selection.expansion(), missing), dense, mode)),
                this.layoutExecutor);
    }

    private void refreshGraph() {
        if (this.observedRevision != this.menu.planRevision) {
            this.observedRevision = this.menu.planRevision;
            this.graph = null;
            this.nodeTooltip = null;
            this.prepared = null;
            if (this.canvas != null) this.canvas.clearGraph();
            this.savedViewport = false;
            this.exportLayout = null;
            this.exportingFull = false;
            if (this.pending != null) this.pending.cancel(true);
            this.pending = null;
            if (this.requestedSelection != null) this.requestedSelection.cancel(true);
            this.requestedSelection = null;
            this.anchorId = -1;
            this.selected = -1;
        }
        CraftingPlanGraph received = this.menu.graph();
        if (received != null && received != this.graph) {
            this.graph = received;
            this.nodeTooltip = new CraftingPlanNodeTooltip(received);
            this.graphHasMissing = received.nodes().stream().anyMatch(node -> node instanceof Material material && (material.missing().signum() > 0 || material.unresolved().signum() > 0));
            this.fitPending = true;
            schedule(UnaryOperator.identity(), true);
        }
        CompletableFuture<Prepared> pending = this.pending;
        if (pending != null && pending.isDone()) {
            this.pending = null;
            try {
                Prepared next = pending.join();
                if (this.exportingFull) {
                    this.exportingFull = false;
                    this.exportLayout = next.layout();
                } else {
                    this.prepared = next;
                    if (this.canvas != null && this.graph != null) {
                        this.canvas.show(this.graph, next.layout());
                        if (this.anchorPosition != null && this.anchorId >= 0) {
                            for (PlacedNode node : next.layout().nodes()) if (node.id() == this.anchorId) {
                                Vector2f position = this.canvas.screenPosition(node);
                                this.canvas.setViewport(this.canvas.getOffsetX() + (position.x - this.anchorPosition.x) / this.canvas.getScale(),
                                        this.canvas.getOffsetY() + (position.y - this.anchorPosition.y) / this.canvas.getScale(), this.canvas.getScale());
                                break;
                            }
                        }
                    }
                }
                this.anchorPosition = null;
                this.anchorId = -1;
                this.localStatus = Component.empty();
            } catch (CompletionException failure) {
                Data_Energistics.LOGGER.error("Plan-tree layout failed for session {} revision {}", this.menu.sessionId(), this.menu.planRevision, failure.getCause());
                this.localStatus = text("layout_failed");
                this.exportingFull = false;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshGraph();
        updateLabels();
        if (this.canvas != null) {
            if (this.fitPending && this.prepared != null && this.canvas.getContentWidth() > 0) {
                this.canvas.fitGraph();
                this.fitPending = false;
            }
            this.canvas.highlight(this.canvas.nodeAt(mouseX, mouseY));
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.nodeTooltip != null) {
            PlacedNode hovered = this.canvas != null && !this.preferencesOpen && !this.screenshotOpen && !this.middleDown && (this.modularUI == null || !this.modularUI.getDragHandler().isDragging()) ? this.canvas.nodeAt(mouseX, mouseY) : null;
            if (hovered == null) this.nodeTooltip.hide();
            else this.nodeTooltip.render(graphics, this.font, hovered, mouseX, mouseY);
        }
        Layout export = this.exportLayout;
        if (export != null && this.graph != null) {
            this.exportLayout = null;
            if (this.exportingSvg) CraftingPlanGraphSvgExport.export(this.graph, export, this.preferences.screenshotAmounts(), this::feedback);
            else CraftingPlanGraphPngExport.export(this.graph, export, this.preferences.screenshotAmounts(), this::feedback);
        }
    }

    private void updateLabels() {
        if (this.titleLabel == null || this.statusLabel == null) return;
        CraftingPlanGraph graph = this.graph;
        this.titleLabel.setText(graph == null ? text("title") : Component.translatable("gui.data_energistics.plan_tree.heading",
                graph.header().target().getDisplayName(), TrinityAmountFormatter.format(graph.header().requested()),
                TrinityAmountFormatter.format(graph.header().bytes()), TrinityDurationFormatter.formatNanos(graph.header().planningNanos())));
        Component message = this.localStatus;
        if (this.menu.planning) message = text("loading");
        else if (!this.menu.graphError.getString().isEmpty()) message = this.menu.graphError;
        else if (!this.menu.status.getString().isEmpty()) message = this.menu.status;
        else if (this.menu.submitError.result() != null && !this.menu.submitError.result().successful()) {
            var error = this.menu.submitError.result();
            message = Component.translatable("gui.data_energistics.plan_tree.submit_failed", String.valueOf(error.errorCode()), String.valueOf(error.errorDetail()));
        } else if (message.getString().isEmpty() && graph != null) {
            message = Component.translatable("gui.data_energistics.plan_tree.summary", text("kind." + graph.header().kind().name().toLowerCase(Locale.ROOT)),
                    this.prepared == null ? 0 : this.prepared.layout().nodes().size(), graph.nodes().size());
            if (!graph.header().diagnostic().getString().isEmpty()) message = message.copy().append(" · ").append(graph.header().diagnostic());
            if (this.missingOnly && !this.graphHasMissing) message = message.copy().append(" · ").append(text("no_missing"));
        }
        this.statusLabel.setText(message);
        this.buttons.get("start").setActive(this.menu.startable);
        this.buttons.get("list").setActive(this.menu.resultReady && !this.menu.planning);
        this.buttons.get("replan").setActive(!this.menu.planning);
        this.buttons.get("cpu").setActive(this.menu.resultReady && !this.menu.planning);
        this.buttons.get("cpu").setText(Component.translatable("gui.data_energistics.plan_tree.cpu_value", this.menu.cpuName,
                TrinityAmountFormatter.format(this.menu.cpuBytes), this.menu.cpuCoProcessors));
        this.buttons.get("missing").setText(text(this.missingOnly ? "missing_on" : "missing"));
        this.buttons.get("density").setText(text(this.compact ? "compact" : "loose"));
        this.buttons.get("pref_missing").setText(Component.translatable("gui.data_energistics.plan_tree.pref_missing_value", text(this.preferences.missingOnly() ? "enabled" : "disabled")));
        this.buttons.get("pref_amounts").setText(Component.translatable("gui.data_energistics.plan_tree.pref_amounts_value", text(this.preferences.screenshotAmounts() ? "enabled" : "disabled")));
        this.buttons.get("pref_budget").setText(Component.translatable("gui.data_energistics.plan_tree.pref_budget_value", this.preferences.autoExpandBudget()));
        this.buttons.get("pref_layout").setText(Component.translatable(
                "gui.data_energistics.plan_tree.pref_layout_value", text("layout." + this.layoutMode.name().toLowerCase(Locale.ROOT))));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.canvas != null && !this.preferencesOpen && !this.screenshotOpen) {
            if (button == 2 && this.canvas.isMouseOverContent((float) mouseX, (float) mouseY)) {
                this.middleDown = true;
                this.middleDragged = false;
                this.middleStartX = mouseX;
                this.middleStartY = mouseY;
            }
            PlacedNode node = this.canvas.nodeAt(mouseX, mouseY);
            if (node != null && (button == 0 || button == 1)) {
                this.selected = node.id();
                this.canvas.select(this.selected);
                if (hasShiftDown()) {
                    if (this.prepared != null && node.viewNode().expandable()) {
                        this.anchorId = node.id();
                        this.anchorPosition = this.canvas.screenPosition(node);
                        boolean recursive = hasControlDown();
                        schedule(selection -> new Selection(selection.projection(), recursive ? selection.projection().recursiveCollapsed(selection.expansion(), node.id(), button == 1) : selection.projection().setCollapsed(selection.expansion(), node.id(), button == 1)), false);
                    }
                    return true;
                }
                if (this.canvas.iconBounds(node).contains((int) mouseX, (int) mouseY)) {
                    if (!CraftingPlanIngredientViewers.show(new GenericStack(CraftingPlanGraphRenderer.key(node), 1), button == 0)) feedback(text("viewer_unavailable"));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 2 && this.middleDown && Math.hypot(mouseX - this.middleStartX, mouseY - this.middleStartY) > 3) this.middleDragged = true;
        // This screen owns its UI, so LDLib2's menu-holder drag forwarding does not apply.
        if (this.modularUI != null && this.modularUI.getWidget().mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled;
        if (this.modularUI != null && this.modularUI.getDragHandler().isDragging()) {
            // A captured drag must end even when the pointer has left the UI window.
            this.modularUI.getWidget().mouseReleased(mouseX, mouseY, button);
            setDragging(false);
            handled = true;
        } else {
            handled = super.mouseReleased(mouseX, mouseY, button);
        }
        if (button == 2 && this.middleDown) {
            if (!this.middleDragged && this.canvas != null && this.canvas.isMouseOverElement((float) mouseX, (float) mouseY)) this.canvas.fitGraph();
            this.middleDown = false;
            return true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (this.nodeTooltip != null && this.nodeTooltip.keyPressed(key, scan)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE || this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scan)) {
            if (this.preferencesOpen || this.screenshotOpen) {
                this.preferencesOpen = false;
                this.screenshotOpen = false;
                updatePopups();
            } else this.menu.request(Action.CANCEL);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.menu.startable) this.menu.request(Action.START);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_RIGHT && key <= GLFW.GLFW_KEY_UP && this.prepared != null && this.canvas != null) {
            navigate(key);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void navigate(int key) {
        if (this.prepared == null || this.canvas == null || this.graph == null) return;
        var nodes = this.prepared.layout().nodes();
        PlacedNode current = nodes.stream().filter(node -> node.id() == this.selected).findFirst()
                .orElseGet(() -> nodes.stream().filter(node -> node.id() == this.graph.rootId()).findFirst().orElse(nodes.getFirst()));
        PlacedNode best = current;
        double score = Double.POSITIVE_INFINITY;
        for (PlacedNode candidate : nodes) {
            double dx = candidate.x() - current.x();
            double dy = candidate.y() - current.y();
            boolean direction = switch (key) {
                case GLFW.GLFW_KEY_LEFT -> dx < 0;
                case GLFW.GLFW_KEY_RIGHT -> dx > 0;
                case GLFW.GLFW_KEY_UP -> dy < 0;
                default -> dy > 0;
            };
            double distance = dx * dx + dy * dy;
            if (direction && distance < score) {
                score = distance;
                best = candidate;
            }
        }
        this.selected = best.id();
        this.canvas.select(this.selected);
        this.canvas.center(best);
    }

    private void export(boolean full, boolean svg) {
        this.screenshotOpen = false;
        updatePopups();
        if (this.graph == null || this.prepared == null || this.pending != null || this.layoutExecutor == null) return;
        this.exportingSvg = svg;
        if (!full) {
            this.exportLayout = this.prepared.layout();
            return;
        }
        Prepared prepared = this.prepared;
        boolean dense = this.compact;
        CraftingPlanLayoutMode mode = this.layoutMode;
        this.exportingFull = true;
        this.localStatus = text("export_loading");
        this.pending = CompletableFuture.supplyAsync(() -> new Prepared(prepared.projection(),
                layout(prepared.projection().visible(Expansion.empty(), false), dense, mode)), this.layoutExecutor);
    }

    public Rect2i panelBounds() {
        return new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    public @Nullable GenericStack hoveredIngredient() {
        if (this.minecraft == null) return null;
        var window = this.minecraft.getWindow();
        double mouseX = this.minecraft.mouseHandler.xpos() * this.width / window.getScreenWidth();
        double mouseY = this.minecraft.mouseHandler.ypos() * this.height / window.getScreenHeight();
        StackWithBounds stack = dataEnergistics$getGenericStackUnderMouse(mouseX, mouseY);
        return stack == null ? null : stack.stack();
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY) {
        if (this.canvas == null || hasShiftDown() || this.preferencesOpen || this.screenshotOpen) return null;
        PlacedNode node = this.canvas.nodeAt(mouseX, mouseY);
        if (node == null) return null;
        Rect2i bounds = this.canvas.iconBounds(node);
        return bounds.contains((int) mouseX, (int) mouseY) ? new StackWithBounds(new GenericStack(CraftingPlanGraphRenderer.key(node), 1), bounds) : null;
    }

    private void feedback(Component message) {
        this.localStatus = message;
        if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.sendSystemMessage(message);
    }

    @Override
    public void onClose() {
        this.menu.request(Action.CANCEL);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float tick, int x, int y) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {}

    @Override
    public void removed() {
        saveViewport();
        if (this.pending != null) this.pending.cancel(true);
        this.pending = null;
        if (this.requestedSelection != null) this.requestedSelection.cancel(true);
        this.requestedSelection = null;
        if (this.layoutExecutor != null) this.layoutExecutor.shutdownNow();
        this.exportingFull = false;
        super.removed();
    }

    private void saveViewport() {
        if (this.canvas != null && this.prepared != null) {
            this.savedOffsetX = this.canvas.getOffsetX();
            this.savedOffsetY = this.canvas.getOffsetY();
            this.savedScale = this.canvas.getScale();
            this.savedViewport = true;
        }
    }

    private static Component text(String suffix) {
        return Component.translatable("gui.data_energistics.plan_tree." + suffix);
    }

    private static Layout layout(CraftingPlanGraphView.ViewGraph graph, boolean compact,
                                 CraftingPlanLayoutMode mode) {
        return mode == CraftingPlanLayoutMode.RADIAL ? CraftingPlanRadialLayout.layout(graph, compact) : CraftingPlanGraphLayout.layout(graph, compact);
    }

    private record Selection(CraftingPlanGraphView projection, Expansion expansion) {}

    private record Prepared(CraftingPlanGraphView projection, Layout layout) {}
}
