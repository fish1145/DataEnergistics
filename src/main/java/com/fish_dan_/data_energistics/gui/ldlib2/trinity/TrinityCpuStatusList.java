package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuStatus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * DataEnergistics-owned LDLib2 CPU list. The component emits only stable Trinity CPU numbers on activation.
 */
@LDLRegister(
             name = "data_energistics:trinity_cpu_status_list",
             group = "data_energistics",
             registry = "ldlib2:ui_element")
public final class TrinityCpuStatusList extends BindableUIElement<TrinityCpuListStatus> {

    public static final int ROW_WIDTH = 67;
    public static final int ROW_HEIGHT = 22;
    public static final int VISIBLE_ROW_COUNT = 3;
    public static final int DEFAULT_WIDTH = 84;
    public static final int DEFAULT_HEIGHT = 76;
    public static final String SCROLLER_ID = "trinity_cpu_status_scroller";
    public static final String SCROLLBAR_ID = "trinity_cpu_status_scrollbar";
    public static final String SCROLL_HEAD_ID = "trinity_cpu_status_scroll_head";
    public static final String SCROLL_TRACK_ID = "trinity_cpu_status_scroll_track";
    public static final String SCROLL_THUMB_ID = "trinity_cpu_status_scroll_thumb";
    public static final String SCROLL_TAIL_ID = "trinity_cpu_status_scroll_tail";

    static final int ROW_STRIDE = ROW_HEIGHT + 1;
    static final int VIEWPORT_HEIGHT = ROW_STRIDE * VISIBLE_ROW_COUNT;
    private static final int VIEWPORT_LEFT = 4;
    private static final int VIEWPORT_TOP = 4;
    private static final int VIEWPORT_WIDTH = DEFAULT_WIDTH - VIEWPORT_LEFT * 2;
    private static final int NAME_LEFT = 3;
    private static final int NAME_TOP = 2;
    private static final int NAME_WIDTH = ROW_WIDTH - NAME_LEFT - 2;
    private static final int ICON_TOP = 9;
    private static final int ICON_SIZE = 10;
    private static final int DETAIL_TEXT_TOP = 13;
    private static final int TEXT_HEIGHT = 6;
    private static final int PROCESSOR_ICON_LEFT = 2;
    private static final int PROCESSOR_TEXT_LEFT = 14;
    private static final int STORAGE_ICON_LEFT = 27;
    private static final int STORAGE_TEXT_LEFT = 39;
    private static final int MODE_ICON_LEFT = 55;
    private static final int CRAFT_ICON_LEFT = 2;
    private static final int CRAFT_TEXT_LEFT = 14;
    private static final int TARGET_ICON_LEFT = 55;
    private static final int TARGET_ICON_TOP = ICON_TOP;
    private static final int TARGET_ICON_SIZE = 11;
    private static final float TARGET_RENDER_SCALE = 0.666F;
    private static final float TARGET_RENDER_DEPTH = 0.0F;
    private static final String UNLIMITED_TEXT = "MAX";
    private static final int TEXT_COLOR = 0xFF413F54;
    private static final int PROGRESS_FILL_COLOR = 0xFFACE9FF;
    private static final int SCROLL_TRACK_COLOR = 0xFF4D4D67;
    private static final int SCROLL_THUMB_COLOR = 0xFF9A9FB4;
    private static final int SCROLL_THUMB_HOVER_COLOR = 0xFFDAFFFF;
    private static final int SCROLL_THUMB_PRESSED_COLOR = 0xFF9CD3FF;
    private static final int SCROLL_THUMB_DISABLED_COLOR = 0xFF70758A;

    private static final SpriteTexture PANEL_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/cpu_panel.png")
            .setSprite(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private static final SpriteTexture ROW_TEXTURE = rowTexture("cpu_entry.png");
    private static final SpriteTexture ROW_SELECTED_TEXTURE = rowTexture("cpu_entry_selected.png");
    private static final SpriteTexture IDLE_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/cpu_idle.png")
            .setSprite(0, 0, ROW_WIDTH, ROW_HEIGHT);
    private static final SpriteTexture TASK_OVERLAY_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/cpu_task_overlay.png")
            .setSprite(0, 0, ROW_WIDTH, ROW_HEIGHT);
    private static final SpriteTexture STORAGE_ICON_TEXTURE = iconTexture("cpu_icon_storage.png");
    private static final SpriteTexture PROCESSOR_ICON_TEXTURE = iconTexture("cpu_icon_processor.png");
    private static final SpriteTexture CRAFT_ICON_TEXTURE = iconTexture("cpu_icon_craft.png");
    private static final SpriteTexture TERMINAL_ICON_TEXTURE = iconTexture("cpu_icon_terminal.png");
    private static final SpriteTexture MACHINE_ICON_TEXTURE = iconTexture("cpu_icon_machine.png");

    private final TrinityCpuScrollerView scrollerView;
    private TrinityCpuListStatus value = TrinityCpuListStatus.EMPTY;
    private IntConsumer cpuSelection = ignored -> {};

    /** Public no-argument constructor required by the LDLib2 UI element registry. */
    public TrinityCpuStatusList() {
        setId("trinity_cpu_status_list");
        setOverflowVisible(false);
        layout(layout -> layout.width(DEFAULT_WIDTH).height(DEFAULT_HEIGHT));
        style(style -> style.backgroundTexture(PANEL_TEXTURE));

        this.scrollerView = new TrinityCpuScrollerView();
        this.scrollerView.setId(SCROLLER_ID);
        this.scrollerView.setOverflowVisible(false);
        this.scrollerView.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(VIEWPORT_LEFT)
                .top(VIEWPORT_TOP)
                .width(VIEWPORT_WIDTH)
                .height(VIEWPORT_HEIGHT));
        this.scrollerView.virtualScrollerViewStyle(style -> style
                .itemHeightMode(VirtualItemHeightMode.FIXED)
                .estimatedItemHeight(ROW_STRIDE)
                .overscanPixels(ROW_STRIDE));
        this.scrollerView.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .minScrollPixel(ROW_STRIDE)
                .maxScrollPixel(ROW_STRIDE)
                .scrollerViewStyle(0));
        this.scrollerView.verticalScroller(TrinityCpuStatusList::configureVerticalScroller);
        this.scrollerView.viewPort(viewPort -> {
            viewPort.layout(layout -> layout.paddingAll(0));
            viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        });
        this.scrollerView.setItemUIProvider(this::createRow);
        this.scrollerView.setItems(List.of());
        addChild(this.scrollerView);
        internalSetup();
    }

    /** Installs the client action boundary used later by the menu's packet sender. */
    public TrinityCpuStatusList setOnCpuSelected(IntConsumer cpuSelection) {
        if (cpuSelection == null) {
            throw new IllegalArgumentException("Trinity CPU selection callback is required");
        }
        this.cpuSelection = cpuSelection;
        return this;
    }

    /**
     * Activates a currently visible logical CPU by stable number.
     *
     * @return false when the number no longer belongs to the latest synchronized snapshot
     */
    public boolean activateCpu(int cpuNumber) {
        for (TrinityCpuStatus cpu : this.value.cpus()) {
            if (cpu.number() == cpuNumber) {
                this.cpuSelection.accept(cpuNumber);
                return true;
            }
        }
        return false;
    }

    /** Exposes the owned virtual scroller for layout integration and focused non-rendering tests. */
    public VirtualScrollerView<TrinityCpuStatus> getScrollerView() {
        return this.scrollerView;
    }

    @Override
    public TrinityCpuListStatus getValue() {
        return this.value;
    }

    @Override
    public TrinityCpuStatusList setValue(@Nullable TrinityCpuListStatus value, boolean notify) {
        TrinityCpuListStatus next = value == null ? TrinityCpuListStatus.EMPTY : value;
        if (this.value.equals(next)) {
            return this;
        }
        this.value = next;
        this.scrollerView.setItems(next.cpus());
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    private UIElement createRow(TrinityCpuStatus cpu) {
        Button row = new Button();
        row.setId("trinity_cpu_status_" + cpu.number());
        row.noText();
        row.layout(layout -> layout.width(ROW_WIDTH).height(ROW_HEIGHT).paddingAll(0));
        row.buttonStyle(style -> style
                .baseTexture(rowStateTexture(false))
                .hoverTexture(rowStateTexture(true))
                .pressedTexture(rowStateTexture(true)));
        row.setOnClick(event -> activateCpu(cpu.number()));
        row.addChild(nameLabel(cpu));
        if (cpu.busy()) {
            addBusyDetails(row, cpu);
        } else {
            addIdleDetails(row, cpu);
        }
        row.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(tooltip(cpu), null, null, null));
        return row;
    }

    private static Label nameLabel(TrinityCpuStatus cpu) {
        return label(
                rowPartId(cpu, "name"),
                displayName(cpu),
                NAME_LEFT,
                NAME_TOP,
                NAME_WIDTH);
    }

    private static void addIdleDetails(Button row, TrinityCpuStatus cpu) {
        if (cpu.coProcessors() > 0) {
            row.addChildren(
                    statusIcon(cpu, "processor", PROCESSOR_ICON_TEXTURE, PROCESSOR_ICON_LEFT),
                    label(
                            rowPartId(cpu, "processor_count"),
                            Component.literal(formatCoProcessors(cpu.coProcessors())),
                            PROCESSOR_TEXT_LEFT,
                            DETAIL_TEXT_TOP,
                            STORAGE_ICON_LEFT - PROCESSOR_TEXT_LEFT));
        }
        row.addChildren(
                statusIcon(cpu, "storage", STORAGE_ICON_TEXTURE, STORAGE_ICON_LEFT),
                label(
                        rowPartId(cpu, "storage_amount"),
                        Component.literal(formatStorage(cpu.storage())),
                        STORAGE_TEXT_LEFT,
                        DETAIL_TEXT_TOP,
                        MODE_ICON_LEFT - STORAGE_TEXT_LEFT));
        switch (cpu.mode()) {
            case PLAYER_ONLY -> row.addChild(statusIcon(cpu, "mode", TERMINAL_ICON_TEXTURE, MODE_ICON_LEFT));
            case MACHINE_ONLY -> row.addChild(statusIcon(cpu, "mode", MACHINE_ICON_TEXTURE, MODE_ICON_LEFT));
            case ANY -> {}
        }
    }

    private static void addBusyDetails(Button row, TrinityCpuStatus cpu) {
        GenericStack job = cpu.currentJob();
        if (job == null) {
            throw new IllegalArgumentException("Busy Trinity CPU row requires a crafting target");
        }
        row.addChildren(
                statusIcon(cpu, "craft", CRAFT_ICON_TEXTURE, CRAFT_ICON_LEFT),
                label(
                        rowPartId(cpu, "craft_amount"),
                        Component.literal(job.what().formatAmount(job.amount(), AmountFormat.SLOT)),
                        CRAFT_TEXT_LEFT,
                        DETAIL_TEXT_TOP,
                        TARGET_ICON_LEFT - CRAFT_TEXT_LEFT),
                targetIcon(cpu, job),
                progressBar(cpu),
                taskOverlay(cpu));
    }

    private static Label label(String id, Component text, int left, int top, int width) {
        Label label = new Label();
        label.setId(id);
        label.setText(text);
        label.setOverflowVisible(false);
        label.setAllowHitTest(false);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(6.0F)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.TOP)
                .textWrap(TextWrap.NONE)
                .textColor(TEXT_COLOR)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(TEXT_HEIGHT));
        return label;
    }

    private static UIElement statusIcon(TrinityCpuStatus cpu, String part, IGuiTexture texture, int left) {
        UIElement icon = new UIElement();
        icon.setId(rowPartId(cpu, part + "_icon"));
        icon.setAllowHitTest(false);
        icon.style(style -> style.backgroundTexture(texture));
        icon.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(ICON_TOP)
                .width(ICON_SIZE)
                .height(ICON_SIZE));
        return icon;
    }

    private static UIElement targetIcon(TrinityCpuStatus cpu, GenericStack currentJob) {
        CpuTargetIcon icon = new CpuTargetIcon(currentJob.what());
        icon.setId(rowPartId(cpu, "target_icon"));
        icon.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(TARGET_ICON_LEFT)
                .top(TARGET_ICON_TOP)
                .width(TARGET_ICON_SIZE)
                .height(TARGET_ICON_SIZE));
        return icon;
    }

    private static UIElement progressBar(TrinityCpuStatus cpu) {
        UIElement fill = new UIElement();
        fill.setId(rowPartId(cpu, "progress"));
        fill.setAllowHitTest(false);
        fill.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(19)
                .width(TrinityCpuListGeometry.progressWidth(cpu.progress()))
                .height(1));
        fill.style(style -> style.backgroundTexture(new ColorRectTexture(PROGRESS_FILL_COLOR)));
        return fill;
    }

    private static UIElement taskOverlay(TrinityCpuStatus cpu) {
        UIElement overlay = new UIElement();
        overlay.setId(rowPartId(cpu, "task_overlay"));
        overlay.setAllowHitTest(false);
        overlay.style(style -> style.backgroundTexture(TASK_OVERLAY_TEXTURE));
        overlay.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(ROW_WIDTH)
                .height(ROW_HEIGHT));
        return overlay;
    }

    private static IGuiTexture rowStateTexture(boolean selected) {
        IGuiTexture background = selected ? ROW_SELECTED_TEXTURE : ROW_TEXTURE;
        return IGuiTexture.group(background, IDLE_TEXTURE);
    }

    private static List<Component> tooltip(TrinityCpuStatus cpu) {
        List<Component> lines = new ArrayList<>();
        lines.add(displayName(cpu));
        if (cpu.coProcessors() > 0) {
            String key = cpu.coProcessors() == 1 ?
                    "gui.tooltips.ae2.CpuStatusCoProcessor" :
                    "gui.tooltips.ae2.CpuStatusCoProcessors";
            lines.add(Component.translatable(key, Integer.toString(cpu.coProcessors())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(
                "gui.tooltips.ae2.CpuStatusStorage",
                TrinityAmountFormatter.format(cpu.storage())).withStyle(ChatFormatting.GRAY));
        if (cpu.mode() != CpuSelectionMode.ANY) {
            lines.add(modeText(cpu.mode()).copy().withStyle(ChatFormatting.GRAY));
        }

        GenericStack job = cpu.currentJob();
        if (job != null) {
            lines.add(Component.translatable(
                    "gui.tooltips.ae2.CpuStatusCrafting",
                    job.what().formatAmount(job.amount(), AmountFormat.FULL))
                    .append(" ")
                    .append(job.what().getDisplayName()));
            lines.add(Component.translatable(
                    "gui.tooltips.ae2.CpuStatusCraftedIn",
                    Math.round(cpu.progress() * 100.0F) + "%",
                    formatElapsed(cpu.elapsedTimeNanos())).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static Component displayName(TrinityCpuStatus cpu) {
        return cpu.name() != null ? cpu.name() :
                Component.translatable("gui.ae2.CPUs").append(" #").append(Integer.toString(cpu.number()));
    }

    private static Component modeText(CpuSelectionMode mode) {
        return Component.translatable(switch (mode) {
            case ANY -> "gui.tooltips.ae2.CpuSelectionModeAny";
            case PLAYER_ONLY -> "gui.tooltips.ae2.CpuSelectionModePlayersOnly";
            case MACHINE_ONLY -> "gui.tooltips.ae2.CpuSelectionModeAutomationOnly";
        });
    }

    private static String formatStorage(long storage) {
        if (storage == Long.MAX_VALUE) {
            return UNLIMITED_TEXT;
        }
        if (storage >= 1024L * 1024L) {
            return storage / (1024L * 1024L) + "M";
        }
        return storage / 1024L + "k";
    }

    private static String formatCoProcessors(int coProcessors) {
        return coProcessors == Integer.MAX_VALUE ? UNLIMITED_TEXT : Integer.toString(coProcessors);
    }

    private static String formatElapsed(long elapsedTimeNanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTimeNanos);
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private static void configureVerticalScroller(Scroller scroller) {
        scroller.setId(SCROLLBAR_ID);
        scroller.layout(layout -> layout.width(5));
        scroller.headButton(button -> {
            button.setId(SCROLL_HEAD_ID);
            button.setOnClick(event -> scroller.scrollValue(-1.0F));
        });
        scroller.scrollContainer(container -> {
            container.setId(SCROLL_TRACK_ID);
            container.style(style -> style.backgroundTexture(new ColorRectTexture(SCROLL_TRACK_COLOR)));
        });
        scroller.scrollBar(button -> {
            button.setId(SCROLL_THUMB_ID);
            setThumbTextures(button, false);
        });
        scroller.tailButton(button -> {
            button.setId(SCROLL_TAIL_ID);
            button.setOnClick(event -> scroller.scrollValue(1.0F));
        });
    }

    private static void setThumbTextures(Button thumb, boolean enabled) {
        if (enabled) {
            thumb.buttonStyle(style -> style
                    .baseTexture(new ColorRectTexture(SCROLL_THUMB_COLOR))
                    .hoverTexture(new ColorRectTexture(SCROLL_THUMB_HOVER_COLOR))
                    .pressedTexture(new ColorRectTexture(SCROLL_THUMB_PRESSED_COLOR)));
        } else {
            ColorRectTexture disabled = new ColorRectTexture(SCROLL_THUMB_DISABLED_COLOR);
            thumb.buttonStyle(style -> style
                    .baseTexture(disabled)
                    .hoverTexture(disabled)
                    .pressedTexture(disabled));
        }
    }

    private static String rowPartId(TrinityCpuStatus cpu, String part) {
        return "trinity_cpu_status_" + cpu.number() + "_" + part;
    }

    private static SpriteTexture rowTexture(String fileName) {
        return SpriteTexture.of("data_energistics:textures/guis/trinity_data_core/" + fileName)
                .setSprite(0, 0, ROW_WIDTH, ROW_HEIGHT);
    }

    private static SpriteTexture iconTexture(String fileName) {
        return SpriteTexture.of("data_energistics:textures/guis/trinity_data_core/" + fileName)
                .setSprite(0, 0, ICON_SIZE, ICON_SIZE);
    }

    private static final class TrinityCpuScrollerView extends VirtualScrollerView<TrinityCpuStatus> {

        private boolean overflowing;

        @Override
        public TrinityCpuScrollerView setItems(List<TrinityCpuStatus> items) {
            int previousItemCount = getItemCount();
            this.overflowing = items.size() > VISIBLE_ROW_COUNT;
            super.setItems(items);
            float normalizedValue = this.overflowing && items.size() >= previousItemCount ?
                    this.verticalScroller.getNormalizedValue() :
                    0.0F;
            this.verticalScroller.setNormalizedValue(normalizedValue, false);
            refreshVisibleItems(
                    normalizedValue * Math.max(0.0F, getTotalVirtualHeight() - VIEWPORT_HEIGHT),
                    VIEWPORT_HEIGHT);
            setScrollInteractionEnabled(this.overflowing);
            setThumbTextures(this.verticalScroller.scrollBar, this.overflowing);
            updateThumbSize();
            return this;
        }

        @Override
        protected void onViewPortLayoutChanged(UIEvent event) {
            super.onViewPortLayoutChanged(event);
            updateThumbSize();
        }

        @Override
        protected void onContainerLayoutChanged(UIEvent event) {
            super.onContainerLayoutChanged(event);
            updateThumbSize();
        }

        @Override
        protected void onScrollWheel(UIEvent event) {
            if (this.overflowing) {
                super.onScrollWheel(event);
            }
        }

        private void updateThumbSize() {
            float trackHeight = this.verticalScroller.scrollContainer.getContentHeight();
            float naturalPercent = this.verticalScroller.getScrollerStyle().scrollBarSize();
            this.verticalScroller.setScrollBarSize(
                    TrinityCpuListGeometry.thumbPercent(naturalPercent, trackHeight, this.overflowing));
        }

        private void setScrollInteractionEnabled(boolean enabled) {
            this.verticalScroller.selfAndAllChildren()
                    .forEach(element -> element.setAllowHitTest(enabled));
        }
    }

    /** Draws one synchronized crafting target through AE2's public key-rendering registry. */
    private static final class CpuTargetIcon extends UIElement {

        private final AEKey target;

        private CpuTargetIcon(AEKey target) {
            if (target == null) {
                throw new IllegalArgumentException("Trinity CPU target key is required");
            }
            this.target = target;
            setAllowHitTest(false);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext guiContext) {
            guiContext.graphics.flush();
            guiContext.pose.pushPose();
            guiContext.pose.translate(getContentX(), getContentY(), TARGET_RENDER_DEPTH);
            guiContext.pose.scale(TARGET_RENDER_SCALE, TARGET_RENDER_SCALE, 1.0F);
            AEKeyRendering.drawInGui(guiContext.mc, guiContext.graphics, 0, 0, this.target);
            guiContext.pose.popPose();
        }
    }
}
