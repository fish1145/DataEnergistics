package com.fish_dan_.data_energistics.client.screen.crafting.confirm;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmPresentationState;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.client.util.TrinityDurationFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanSessionTransfer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.stacks.AEKey;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftErrorScreen;
import appeng.client.gui.style.StyleManager;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

/** NBT-authored crafting confirmation page used while the current Grid publishes a Trinity CPU. */
final class TrinityCraftConfirmScreen extends AbstractContainerScreen<CraftConfirmMenu>
                                      implements GenericStackLookupScreen, TrinityCraftConfirmPresentationState {

    private static final int AUTHORED_WIDTH = 256;
    private static final int AUTHORED_HEIGHT = 256;
    private static final float HEADER_FONT_SIZE = 7.0F;
    private static final String TRANSLATION_PREFIX = "gui.data_energistics.craft_confirm.";

    private @Nullable Authored pendingAuthored;
    private TrinityCraftConfirmLayout.@Nullable Layout layout;
    private @Nullable ModularUI modularUI;
    private @Nullable TrinityCraftConfirmMaterialTable materialTable;
    private long observedRevision = -1L;
    private @Nullable AEKey hoveredCycleKey;
    private int selectedRelatedCycleIndex;
    private int savedFirstVisibleRow;
    private boolean restoreMaterialScroll;
    private Component statusTooltip = Component.empty();
    private Component cpuStatsTooltip = Component.empty();
    private Component diagnosticTooltip = Component.empty();

    TrinityCraftConfirmScreen(CraftConfirmMenu menu,
                              Inventory inventory,
                              Component title) {
        super(menu, inventory, title);
        this.pendingAuthored = loadAuthored();
    }

    @Override
    protected void init() {
        releaseModularUi();
        this.imageWidth = AUTHORED_WIDTH;
        this.imageHeight = AUTHORED_HEIGHT;
        super.init();
        mountAuthoredUi();
    }

    private void mountAuthoredUi() {
        Authored authored = this.pendingAuthored == null ? loadAuthored() : this.pendingAuthored;
        this.pendingAuthored = null;
        this.layout = authored.layout();
        this.layout.heading().textStyle(style -> style.fontSize(HEADER_FONT_SIZE));
        this.layout.metrics().textStyle(style -> style.fontSize(HEADER_FONT_SIZE));
        this.layout.heading().setOverflowVisible(false);
        this.layout.metrics().setOverflowVisible(false);
        this.layout.status().setOverflowVisible(false);
        this.layout.cpuStats().setOverflowVisible(false);
        this.layout.diagnostic().setOverflowVisible(false);
        configureButton(this.layout.cancel(), text("cancel"), event -> this.menu.goBack());
        configureButton(this.layout.tree(), text("tree"), event -> ((CraftingPlanSessionTransfer) this.menu).data_energistics$openPlanTree());
        configureButton(this.layout.start(), text("start"), event -> {
            if (this.layout != null && this.layout.start().isActive()) {
                this.menu.startJob();
            }
        });
        this.layout.cpu().setOnClick(event -> this.menu.cycleSelectedCPU(true));
        this.layout.cpu().addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1 && this.layout != null && this.layout.cpu().isActive()) {
                this.menu.cycleSelectedCPU(false);
                event.stopPropagation();
            }
        });
        setTooltip(this.layout.cpu(), Component.translatable("gui.data_energistics.plan_tree.cpu.tooltip"));
        setTooltip(this.layout.cancel(), Component.translatable("gui.data_energistics.plan_tree.cancel.tooltip"));
        setTooltip(this.layout.tree(), Component.translatable("gui.data_energistics.plan_tree.open"));
        setTooltip(this.layout.start(), Component.translatable("gui.data_energistics.plan_tree.start.tooltip"));

        this.materialTable = new TrinityCraftConfirmMaterialTable(
                this.layout.scrollbar(),
                this::data_energistics$selectedCycleOrdinal);
        authored.ui().rootElement.addChildAt(this.materialTable, 0);
        this.modularUI = new ModularUI(authored.ui(), this.menu.getPlayer());
        this.modularUI.shouldCloseOnEsc(false).shouldCloseOnKeyInventory(false);
        this.modularUI.setScreenAndInit(this);
        addRenderableWidget(this.modularUI.getWidget());
        setFocused(this.modularUI.getWidget());
        updateAuthoredPresentation();
    }

    private static void configureButton(Button button,
                                        Component text,
                                        UIEventListener action) {
        button.setText(text);
        button.setOnClick(action);
    }

    private static void setTooltip(Button button, Component tooltip) {
        button.text.style(style -> style.tooltips(tooltip));
        button.style(style -> style.tooltips(tooltip));
    }

    private boolean refreshBeforeRender() {
        var submitResult = this.menu.submitError.result();
        var errorCode = submitResult == null ? null : submitResult.errorCode();
        if (errorCode != null) {
            CraftConfirmScreen parent = new CraftConfirmScreen(
                    this.menu,
                    this.menu.getPlayerInventory(),
                    this.title,
                    StyleManager.loadStyleDoc("/screens/craft_confirm.json"));
            Minecraft.getInstance().setScreen(new CraftErrorScreen(parent, errorCode, submitResult.errorDetail()));
            return false;
        }
        updateAuthoredPresentation();
        return true;
    }

    private void updateAuthoredPresentation() {
        TrinityCraftConfirmLayout.Layout currentLayout = this.layout;
        TrinityCraftConfirmMaterialTable currentTable = this.materialTable;
        if (currentLayout == null || currentTable == null) {
            return;
        }
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        CraftingPlanSessionTransfer session = (CraftingPlanSessionTransfer) this.menu;
        boolean hasTrinityCpu = session.data_energistics$hasTrinityCpu();
        CraftingPlanSummary plan = this.menu.getPlan();
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        refreshRevision(state, summary);
        currentTable.setPlan(plan, summary);
        if (this.restoreMaterialScroll) {
            currentTable.restoreFirstVisibleRow(this.savedFirstVisibleRow);
            this.restoreMaterialScroll = false;
        }

        currentLayout.heading().setText(heading(state, plan));
        currentLayout.metrics().setText(metrics(state, plan, summary));
        Component cpuStatistics = cpuStatistics(hasTrinityCpu);
        Component status;
        Component statusTooltip;
        Component cpuStats;
        Component cpuStatsTooltip;
        Component diagnostic;
        if (state.data_energistics$hasDiagnostic()) {
            status = cpuStatistics;
            statusTooltip = cpuStatistics;
            cpuStats = Component.translatable(TRANSLATION_PREFIX + "diagnostic", state.data_energistics$diagnostic());
            cpuStatsTooltip = cpuStats;
            diagnostic = state.data_energistics$diagnosticDetail();
        } else {
            status = TrinityCraftConfirmProgressText.status(state, plan, hasTrinityCpu);
            statusTooltip = plan == null ? TrinityCraftConfirmProgressText.tooltip(state) : status;
            cpuStats = cpuStatistics;
            cpuStatsTooltip = cpuStatistics;
            diagnostic = Component.translatable(TRANSLATION_PREFIX + "diagnostic.none");
        }
        currentLayout.status().setText(status);
        if (!Objects.equals(this.statusTooltip, statusTooltip)) {
            this.statusTooltip = statusTooltip;
            currentLayout.status().style(style -> style.tooltips(statusTooltip));
        }
        currentLayout.cpu().setText(cpuButtonText(hasTrinityCpu));
        currentLayout.cpuStats().setText(cpuStats);
        if (!Objects.equals(this.cpuStatsTooltip, cpuStatsTooltip)) {
            this.cpuStatsTooltip = cpuStatsTooltip;
            currentLayout.cpuStats().style(style -> style.tooltips(cpuStatsTooltip));
        }
        currentLayout.diagnostic().setText(diagnostic);
        if (!Objects.equals(this.diagnosticTooltip, diagnostic)) {
            this.diagnosticTooltip = diagnostic;
            currentLayout.diagnostic().style(style -> style.tooltips(diagnostic));
        }

        boolean planIsStartable = plan != null && !plan.isSimulation();
        currentLayout.cpu().setActive(hasTrinityCpu && planIsStartable && !this.menu.hasNoCPU());
        currentLayout.tree().setActive(hasTrinityCpu && session.data_energistics$isTreeReady());
        currentLayout.start().setActive(
                hasTrinityCpu && state.data_energistics$isPlanReady() && planIsStartable && !this.menu.hasNoCPU());
    }

    private static Component heading(TrinityCraftConfirmMenuState state,
                                     @Nullable CraftingPlanSummary plan) {
        var progress = state.data_energistics$planningProgress();
        boolean delegatedToAe2 = progress != null && progress.phase() == TrinityPlanningProgressPhase.DELEGATED_TO_AE2;
        Component family = state.data_energistics$isAe2FallbackEstimate() || delegatedToAe2 ?
                Component.literal("AE2") : Component.literal("Trinity");
        String feature;
        if (plan == null) {
            feature = "planning";
        } else if (state.data_energistics$isAe2FallbackEstimate() || delegatedToAe2) {
            feature = "fallback";
        } else if (state.data_energistics$hasDiagnostic()) {
            feature = "diagnostic";
        } else if (state.data_energistics$hasDynamicMaterialWarning()) {
            feature = "cycle";
        } else {
            feature = "standard";
        }
        Component featureText = Component.translatable(TRANSLATION_PREFIX + "feature." + feature);
        if (state.data_energistics$hasDiagnostic()) {
            return Component.translatable(TRANSLATION_PREFIX + "heading.diagnostic", family, featureText);
        }
        Component mode = Component.translatable(state.data_energistics$quantityMode() == CraftingQuantityMode.NET_NEW ?
                "gui.data_energistics.trinity_quantity.net_new" :
                "gui.data_energistics.trinity_quantity.final_total");
        return Component.translatable(
                TRANSLATION_PREFIX + "heading",
                family,
                mode,
                featureText);
    }

    private static Component metrics(TrinityCraftConfirmMenuState state,
                                     @Nullable CraftingPlanSummary plan,
                                     @Nullable TrinityCraftingCycleSummary summary) {
        if (plan == null) {
            return Component.translatable(TRANSLATION_PREFIX + "metrics.planning");
        }
        String bytes = summary == null ?
                TrinityAmountFormatter.format(plan.getUsedBytes()) :
                summary.exactBytes()
                        .map(TrinityAmountFormatter::format)
                        .orElseGet(() -> TrinityAmountFormatter.format(plan.getUsedBytes()));
        return Component.translatable(
                TRANSLATION_PREFIX + "metrics",
                bytes,
                TrinityDurationFormatter.formatNanos(state.data_energistics$planningNanos()));
    }

    private Component cpuButtonText(boolean hasTrinityCpu) {
        Component cpu;
        if (!hasTrinityCpu || this.menu.hasNoCPU()) {
            cpu = GuiText.NoCraftingCPUs.text();
        } else if (this.menu.cpuName == null) {
            cpu = GuiText.Automatic.text();
        } else {
            cpu = this.menu.cpuName;
        }
        return Component.translatable(TRANSLATION_PREFIX + "cpu", cpu);
    }

    private Component cpuStatistics(boolean hasTrinityCpu) {
        if (!hasTrinityCpu || this.menu.hasNoCPU() || this.menu.cpuName == null) {
            return Component.translatable(TRANSLATION_PREFIX + "cpu_stats", "N/A", "N/A");
        }
        return Component.translatable(
                TRANSLATION_PREFIX + "cpu_stats",
                TrinityAmountFormatter.format(this.menu.getCpuAvailableBytes()),
                TrinityAmountFormatter.format(this.menu.getCpuCoProcessors()));
    }

    private void refreshRevision(TrinityCraftConfirmMenuState state,
                                 @Nullable TrinityCraftingCycleSummary summary) {
        long revision = state.data_energistics$planRevision();
        if (revision != this.observedRevision || summary == null) {
            if (revision != this.observedRevision && this.materialTable != null) {
                this.materialTable.resetRevision();
            }
            if (revision != this.observedRevision) {
                this.savedFirstVisibleRow = 0;
                this.restoreMaterialScroll = false;
            }
            this.observedRevision = revision;
            this.hoveredCycleKey = null;
            this.selectedRelatedCycleIndex = 0;
        } else if (this.hoveredCycleKey != null) {
            int relatedCycleCount = summary.contributionsFor(this.hoveredCycleKey).size();
            if (relatedCycleCount == 0) {
                this.hoveredCycleKey = null;
                this.selectedRelatedCycleIndex = 0;
            } else if (this.selectedRelatedCycleIndex >= relatedCycleCount) {
                this.selectedRelatedCycleIndex = 0;
            }
        }
    }

    private void setHoveredCycleKey(@Nullable AEKey key) {
        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) this.menu;
        TrinityCraftingCycleSummary summary = state.data_energistics$cycleSummary();
        AEKey nextKey = summary == null || key == null || summary.contributionsFor(key).isEmpty() ? null : key;
        if (!Objects.equals(this.hoveredCycleKey, nextKey)) {
            this.hoveredCycleKey = nextKey;
            this.selectedRelatedCycleIndex = 0;
        }
    }

    @Override
    public int data_energistics$selectedCycleOrdinal(AEKey key) {
        setHoveredCycleKey(key);
        TrinityCraftingCycleSummary summary = ((TrinityCraftConfirmMenuState) this.menu)
                .data_energistics$cycleSummary();
        if (summary == null) {
            return 0;
        }
        var contributions = summary.contributionsFor(key);
        if (contributions.isEmpty()) {
            return 0;
        }
        if (this.selectedRelatedCycleIndex >= contributions.size()) {
            this.selectedRelatedCycleIndex = 0;
        }
        return contributions.get(this.selectedRelatedCycleIndex).displayOrdinal();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!refreshBeforeRender()) {
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        TrinityCraftConfirmMaterialTable currentTable = this.materialTable;
        if (currentTable == null) {
            return;
        }
        CraftingPlanSummaryEntry hovered = currentTable.entryAt(mouseX, mouseY);
        setHoveredCycleKey(hovered == null ? null : hovered.getWhat());
        if (hovered != null) {
            renderMaterialTooltip(graphics, currentTable.tooltip(hovered), mouseX, mouseY);
        }
    }

    private void renderMaterialTooltip(GuiGraphics graphics,
                                       List<Component> lines,
                                       int mouseX,
                                       int mouseY) {
        int maxWidth = Math.max(40, graphics.guiWidth() / 2 - 40);
        ObjectArrayList<FormattedCharSequence> wrapped = new ObjectArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index).copy();
            if (index == 0) {
                line.withStyle(ChatFormatting.WHITE);
            } else if (line.getStyle().getColor() == null) {
                line.withStyle(ChatFormatting.GRAY);
            }
            wrapped.addAll(ComponentRenderUtils.wrapComponents(line, maxWidth, this.font));
        }
        graphics.renderTooltip(this.font, wrapped, mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX,
                                double mouseY,
                                int button,
                                double dragX,
                                double dragY) {
        if (this.modularUI != null &&
                this.modularUI.getWidget().mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.modularUI != null && this.modularUI.getDragHandler().isDragging()) {
            this.modularUI.getWidget().mouseReleased(mouseX, mouseY, button);
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        TrinityCraftingCycleSummary summary = ((TrinityCraftConfirmMenuState) this.menu)
                .data_energistics$cycleSummary();
        AEKey hoveredKey = this.hoveredCycleKey;
        if (summary != null && hoveredKey != null) {
            int cycleCount = summary.contributionsFor(hoveredKey).size();
            if (cycleCount > 1) {
                if (DEKeyMappings.PREVIOUS_TRINITY_CYCLE.matches(keyCode, scanCode)) {
                    this.selectedRelatedCycleIndex = this.selectedRelatedCycleIndex <= 0 ?
                            cycleCount - 1 : this.selectedRelatedCycleIndex - 1;
                    return true;
                }
                if (DEKeyMappings.NEXT_TRINITY_CYCLE.matches(keyCode, scanCode)) {
                    this.selectedRelatedCycleIndex = (this.selectedRelatedCycleIndex + 1) % cycleCount;
                    return true;
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.layout != null && this.layout.start().isActive()) {
                this.menu.startJob();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE ||
                this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.menu.goBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Nullable
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        return this.materialTable == null ? null : this.materialTable.stackAt(mouseX, mouseY);
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY) {
        return getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void onClose() {
        this.menu.goBack();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        releaseModularUi();
        super.removed();
    }

    private void releaseModularUi() {
        if (this.materialTable != null) {
            this.savedFirstVisibleRow = this.materialTable.firstVisibleRow();
            this.restoreMaterialScroll = true;
        }
        if (this.modularUI != null) {
            this.modularUI.onRemoved();
        }
        this.modularUI = null;
        this.layout = null;
        this.materialTable = null;
    }

    private static Authored loadAuthored() {
        UI ui = TrinityUiNbtLayouts.load("craft_confirm");
        return new Authored(ui, TrinityCraftConfirmLayout.require(ui.rootElement));
    }

    private static Component text(String suffix) {
        return Component.translatable(TRANSLATION_PREFIX + suffix);
    }

    private record Authored(UI ui, TrinityCraftConfirmLayout.Layout layout) {}
}
