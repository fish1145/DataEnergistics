package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

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
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
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

    private static final int VIEWPORT_HEIGHT = ROW_HEIGHT * VISIBLE_ROW_COUNT;
    private static final int VIEWPORT_LEFT = 4;
    private static final int VIEWPORT_TOP = 5;
    private static final int VIEWPORT_WIDTH = DEFAULT_WIDTH - VIEWPORT_LEFT * 2;
    private static final int TEXT_LEFT = 2;
    private static final int TEXT_WIDTH = ROW_WIDTH - TEXT_LEFT * 2;
    private static final int BUSY_TEXT_WIDTH = 51;
    private static final int TARGET_ICON_LEFT = 55;
    private static final int TARGET_ICON_TOP = 9;
    private static final int TARGET_ICON_SIZE = 11;
    private static final int PROGRESS_WIDTH = ROW_WIDTH - 2;
    private static final int NAME_COLOR = 0xFFD8F3FF;
    private static final int IDLE_COLOR = 0xFF9CD3FF;
    private static final int BUSY_COLOR = 0xFFFFE066;
    private static final int ROW_BACKGROUND_COLOR = 0xFF111A24;
    private static final int ROW_HOVER_COLOR = 0x2638C9DA;
    private static final int ROW_PRESSED_COLOR = 0x4058E3E0;
    private static final int PROGRESS_TRACK_COLOR = 0xFF153642;
    private static final int PROGRESS_FILL_COLOR = 0xFF46DBC2;

    private static final SpriteTexture PANEL_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/cpu_panel.png")
            .setSprite(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private static final SpriteTexture IDLE_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/cpu_idle.png")
            .setSprite(0, 0, ROW_WIDTH, ROW_HEIGHT);
    private static final SpriteTexture TASK_OVERLAY_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/cpu_task_overlay.png")
            .setSprite(0, 0, ROW_WIDTH, ROW_HEIGHT);

    private final VirtualScrollerView<TrinityCpuStatus> scrollerView;
    private TrinityCpuListStatus value = TrinityCpuListStatus.EMPTY;
    private IntConsumer cpuSelection = ignored -> {};

    /** Public no-argument constructor required by the LDLib2 UI element registry. */
    public TrinityCpuStatusList() {
        setId("trinity_cpu_status_list");
        setOverflowVisible(false);
        layout(layout -> layout.width(DEFAULT_WIDTH).height(DEFAULT_HEIGHT));
        style(style -> style.backgroundTexture(PANEL_TEXTURE));

        this.scrollerView = new VirtualScrollerView<>();
        this.scrollerView.setId("trinity_cpu_status_scroller");
        this.scrollerView.setOverflowVisible(false);
        this.scrollerView.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(VIEWPORT_LEFT)
                .top(VIEWPORT_TOP)
                .width(VIEWPORT_WIDTH)
                .height(VIEWPORT_HEIGHT));
        this.scrollerView.virtualScrollerViewStyle(style -> style
                .itemHeightMode(VirtualItemHeightMode.FIXED)
                .estimatedItemHeight(ROW_HEIGHT)
                .overscanPixels(ROW_HEIGHT));
        this.scrollerView.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .scrollerViewStyle(0));
        this.scrollerView.viewPort(viewPort -> {
            viewPort.layout(layout -> layout.paddingAll(0));
            viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        });
        this.scrollerView.setItemUIProvider(this::createRow);
        this.scrollerView.refreshVisibleItems(0.0F, VIEWPORT_HEIGHT);
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
                .baseTexture(rowTexture(cpu.busy(), 0))
                .hoverTexture(rowTexture(cpu.busy(), ROW_HOVER_COLOR))
                .pressedTexture(rowTexture(cpu.busy(), ROW_PRESSED_COLOR)));
        row.setOnClick(event -> activateCpu(cpu.number()));
        row.addChildren(nameLabel(cpu), detailLabel(cpu));
        if (cpu.busy()) {
            row.addChildren(targetIcon(cpu.currentJob()), progressBar(cpu.progress()));
        }
        row.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(tooltip(cpu), null, null, null));
        return row;
    }

    private static Label nameLabel(TrinityCpuStatus cpu) {
        return label(displayName(cpu), 1, TEXT_WIDTH, NAME_COLOR);
    }

    private static Label detailLabel(TrinityCpuStatus cpu) {
        Component detail;
        int color;
        if (cpu.currentJob() != null) {
            GenericStack job = cpu.currentJob();
            detail = Component.literal(job.what().formatAmount(job.amount(), AmountFormat.SLOT) + " ")
                    .append(job.what().getDisplayName());
            color = BUSY_COLOR;
        } else {
            detail = Component.literal(formatStorage(cpu.storage()) + "  C" + cpu.coProcessors() + "  " +
                    modeCode(cpu.mode()));
            color = IDLE_COLOR;
        }
        return label(detail, 10, cpu.busy() ? BUSY_TEXT_WIDTH : TEXT_WIDTH, color);
    }

    private static Label label(Component text, int top, int width, int color) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.setAllowHitTest(false);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.0F)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textColor(color)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(TEXT_LEFT)
                .top(top)
                .width(width)
                .height(8));
        return label;
    }

    private static UIElement targetIcon(@Nullable GenericStack currentJob) {
        if (currentJob == null) {
            throw new IllegalArgumentException("Busy Trinity CPU row requires a crafting target");
        }
        CpuTargetIcon icon = new CpuTargetIcon(currentJob.what());
        icon.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(TARGET_ICON_LEFT)
                .top(TARGET_ICON_TOP)
                .width(TARGET_ICON_SIZE)
                .height(TARGET_ICON_SIZE));
        return icon;
    }

    private static UIElement progressBar(float progress) {
        UIElement track = new UIElement();
        track.setAllowHitTest(false);
        track.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(20)
                .width(PROGRESS_WIDTH)
                .height(1));
        track.style(style -> style.backgroundTexture(new ColorRectTexture(PROGRESS_TRACK_COLOR)));

        UIElement fill = new UIElement();
        fill.setAllowHitTest(false);
        fill.layout(layout -> layout.width(progressWidth(progress)).height(1));
        fill.style(style -> style.backgroundTexture(new ColorRectTexture(PROGRESS_FILL_COLOR)));
        track.addChild(fill);
        return track;
    }

    private static IGuiTexture rowTexture(boolean busy, int overlayColor) {
        IGuiTexture row = busy ?
                IGuiTexture.group(
                        new ColorRectTexture(ROW_BACKGROUND_COLOR),
                        IDLE_TEXTURE,
                        TASK_OVERLAY_TEXTURE) :
                IGuiTexture.group(new ColorRectTexture(ROW_BACKGROUND_COLOR), IDLE_TEXTURE);
        return overlayColor == 0 ? row : IGuiTexture.group(row, new ColorRectTexture(overlayColor));
    }

    static int progressWidth(float progress) {
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Trinity CPU progress must be between zero and one");
        }
        return Math.round(progress * PROGRESS_WIDTH);
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
                formatStorage(cpu.storage())).withStyle(ChatFormatting.GRAY));
        lines.add(modeText(cpu.mode()).copy().withStyle(ChatFormatting.GRAY));

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

    private static String modeCode(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> "A";
            case PLAYER_ONLY -> "P";
            case MACHINE_ONLY -> "M";
        };
    }

    private static String formatStorage(long storage) {
        if (storage >= 1024L * 1024L) {
            return storage / (1024L * 1024L) + "M";
        }
        if (storage >= 1024L) {
            return storage / 1024L + "K";
        }
        return storage + "B";
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
            float width = getContentWidth();
            float height = getContentHeight();
            if (width <= 0.0F || height <= 0.0F) {
                return;
            }
            guiContext.pose.pushPose();
            guiContext.pose.scale(width / 16.0F, height / 16.0F, 1.0F);
            guiContext.pose.translate(getContentX() * 16.0F / width, getContentY() * 16.0F / height, -200.0F);
            AEKeyRendering.drawInGui(guiContext.mc, guiContext.graphics, 0, 0, this.target);
            guiContext.pose.popPose();
        }
    }
}
