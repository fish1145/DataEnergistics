package com.fish_dan_.data_energistics.orbital.control.ui;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** Shared LDLib2 palette and component construction for the terminal, console and compact HUD. */
public final class OrbitalControlUiTheme {

    public static final int TEXT = 0xFFEAF8FF;
    public static final int MUTED_TEXT = 0xFF9DB8C7;
    public static final int ACCENT_TEXT = 0xFF66E1F2;

    private static final IGuiTexture SHELL = framed(0xF00A121C, 0xFF326278);
    private static final IGuiTexture PANEL = framed(0xEC122231, 0xFF28495B);
    private static final IGuiTexture PANEL_ALT = framed(0xEC0E1B27, 0xFF243C4B);
    private static final IGuiTexture ACCENT = framed(0xEE123442, 0xFF4DBED0);
    private static final IGuiTexture WARNING = framed(0xEE3B2B17, 0xFFD69A4B);
    private static final IGuiTexture DANGER = framed(0xEE3A1B24, 0xFFD35D70);

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
                .textAlignVertical(Vertical.TOP)
                .textWrap(textWrap)
                .textColor(color)
                .textShadow(true));
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
        button.style(style -> style.backgroundTexture(texture(tone)));
        button.text.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(9)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textColor(TEXT)
                .textShadow(true));
        place(button, left, top, width, height);
        return button;
    }

    public static void stylePanel(UIElement element, Tone tone) {
        element.style(style -> style.backgroundTexture(texture(tone)));
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
