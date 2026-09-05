package com.fish_dan_.data_energistics.orbital.control.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.TaffyPosition;

/** Shared LDLib2 palette and component construction for the terminal, console and compact HUD. */
public final class OrbitalControlUiTheme {

    public static final int TEXT = 0xFF173641;
    public static final int MUTED_TEXT = 0xFF506A76;
    public static final int ACCENT_TEXT = 0xFF006F84;
    private static final int ERROR_TEXT = 0xFFB4233D;

    private static final IGuiTexture SHELL = framed(0xFFF3FAFC, 0xFF35AFC4);
    private static final IGuiTexture SHELL_HOVER = framed(0xFFFFFFFF, 0xFF159DB7);
    private static final IGuiTexture SHELL_PRESSED = framed(0xFFE1F1F4, 0xFF178BA1);
    private static final IGuiTexture PANEL = framed(0xFFFBFDFE, 0xFF97C5CE);
    private static final IGuiTexture PANEL_HOVER = framed(0xFFFFFFFF, 0xFF46AEC0);
    private static final IGuiTexture PANEL_PRESSED = framed(0xFFE5F2F5, 0xFF4697A8);
    private static final IGuiTexture PANEL_ALT = framed(0xFFE8F5F7, 0xFF78B4C0);
    private static final IGuiTexture PANEL_ALT_HOVER = framed(0xFFF5FBFC, 0xFF33A6BA);
    private static final IGuiTexture PANEL_ALT_PRESSED = framed(0xFFD8EBEF, 0xFF4D96A5);
    private static final IGuiTexture ACCENT = framed(0xFFD5F3F7, 0xFF24A9BF);
    private static final IGuiTexture ACCENT_HOVER = framed(0xFFE8FBFC, 0xFF008DA7);
    private static final IGuiTexture ACCENT_PRESSED = framed(0xFFBCE5EB, 0xFF087C91);
    private static final IGuiTexture WARNING = framed(0xFFFFF1C7, 0xFFD39A2D);
    private static final IGuiTexture WARNING_HOVER = framed(0xFFFFF8E5, 0xFFB97700);
    private static final IGuiTexture WARNING_PRESSED = framed(0xFFFFE1A0, 0xFFB97700);
    private static final IGuiTexture DANGER = framed(0xFFFFE1E7, 0xFFD25A70);
    private static final IGuiTexture DANGER_HOVER = framed(0xFFFFF0F3, 0xFFB83B55);
    private static final IGuiTexture DANGER_PRESSED = framed(0xFFFFC7D1, 0xFFB83B55);
    private static final IGuiTexture INPUT = framed(0xFFFFFFFF, 0xFF8ABBC5);
    private static final IGuiTexture INPUT_FOCUS = framed(0x1824A9BF, 0xFF10A2B9);

    private OrbitalControlUiTheme() {}

    public static UIElement panel(
                                  String id,
                                  int left,
                                  int top,
                                  int width,
                                  int height,
                                  Tone tone) {
        UIElement panel = new UIElement();
        panel.setId(id);
        panel.style(style -> style.backgroundTexture(texture(tone)));
        place(panel, left, top, width, height);
        return panel;
    }

    public static Label label(
                              String id,
                              Component value,
                              int left,
                              int top,
                              int width,
                              int height,
                              int color,
                              int fontSize,
                              TextWrap textWrap) {
        Label label = new Label();
        label.setId(id);
        label.setValue(value);
        label.setAllowHitTest(false);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(fontSize)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(textWrap == TextWrap.WRAP ? Vertical.TOP : Vertical.CENTER)
                .textWrap(textWrap)
                .textColor(color)
                .textShadow(false));
        place(label, left, top, width, height);
        return label;
    }

    public static Button button(
                                String id,
                                Component text,
                                int left,
                                int top,
                                int width,
                                int height,
                                Tone tone) {
        Button button = new Button();
        button.setId(id);
        button.setText(text);
        styleButton(button, tone);
        button.text.layout(layout -> layout.flex(1).heightPercent(100));
        button.text.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(9)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textColor(TEXT)
                .textShadow(false));
        place(button, left, top, width, height);
        return button;
    }

    public static void stylePanel(UIElement element, Tone tone) {
        element.style(style -> style.backgroundTexture(texture(tone)));
    }

    public static void styleButton(Button button, Tone tone) {
        button.style(style -> style.backgroundTexture(texture(tone)));
        button.buttonStyle(style -> style
                .baseTexture(texture(tone))
                .hoverTexture(hoverTexture(tone))
                .pressedTexture(pressedTexture(tone)));
        button.text.textStyle(style -> style.textColor(TEXT).textShadow(false));
    }

    public static void styleTextField(TextField field) {
        field.style(style -> style.backgroundTexture(INPUT));
        field.textFieldStyle(style -> style
                .focusOverlay(INPUT_FOCUS)
                .fontSize(9)
                .textColor(TEXT)
                .errorColor(ERROR_TEXT)
                .cursorColor(ACCENT_TEXT)
                .textShadow(false));
    }

    public static void styleSelector(Selector<?> selector) {
        selector.style(style -> style.backgroundTexture(INPUT));
        selector.selectorStyle(style -> style.focusOverlay(INPUT_FOCUS));
        selector.buttonIcon.style(style -> style.backgroundTexture(Icons.DOWN_ARROW_NO_BAR_S));
        selector.dialog.style(style -> style.backgroundTexture(PANEL));
        selector.listView.style(style -> style.backgroundTexture(PANEL));
        selector.scrollerView.style(style -> style.backgroundTexture(PANEL));
        selector.scrollerView.verticalScroller.scrollContainer.style(style -> style.backgroundTexture(PANEL_ALT));
        selector.scrollerView.horizontalScroller.scrollContainer.style(style -> style.backgroundTexture(PANEL_ALT));
        styleButton(selector.scrollerView.verticalScroller.scrollBar, Tone.ACCENT);
        styleButton(selector.scrollerView.horizontalScroller.scrollBar, Tone.ACCENT);
    }

    public static void place(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(height));
    }

    private static IGuiTexture texture(Tone tone) {
        return switch (tone) {
            case SHELL -> SHELL;
            case PANEL -> PANEL;
            case PANEL_ALT -> PANEL_ALT;
            case ACCENT -> ACCENT;
            case WARNING -> WARNING;
            case DANGER -> DANGER;
        };
    }

    private static IGuiTexture hoverTexture(Tone tone) {
        return switch (tone) {
            case SHELL -> SHELL_HOVER;
            case PANEL -> PANEL_HOVER;
            case PANEL_ALT -> PANEL_ALT_HOVER;
            case ACCENT -> ACCENT_HOVER;
            case WARNING -> WARNING_HOVER;
            case DANGER -> DANGER_HOVER;
        };
    }

    private static IGuiTexture pressedTexture(Tone tone) {
        return switch (tone) {
            case SHELL -> SHELL_PRESSED;
            case PANEL -> PANEL_PRESSED;
            case PANEL_ALT -> PANEL_ALT_PRESSED;
            case ACCENT -> ACCENT_PRESSED;
            case WARNING -> WARNING_PRESSED;
            case DANGER -> DANGER_PRESSED;
        };
    }

    private static IGuiTexture framed(int background, int border) {
        return GuiTextureGroup.of(
                new ColorRectTexture(background),
                new ColorBorderTexture(-1, border));
    }

    public enum Tone {
        SHELL,
        PANEL,
        PANEL_ALT,
        ACCENT,
        WARNING,
        DANGER
    }
}
