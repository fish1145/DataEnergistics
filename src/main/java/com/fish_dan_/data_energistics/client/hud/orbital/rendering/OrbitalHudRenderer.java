package com.fish_dan_.data_energistics.client.hud.orbital.rendering;

import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudPreferences.DisplayMode;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudPreferences.Preferences;
import com.fish_dan_.data_energistics.client.hud.orbital.layout.OrbitalHudPlacement;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPresentation;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** The same clipped, scaled panel is rendered in the world and in its placement editor. */
public final class OrbitalHudRenderer {

    private static final int WIDTH = 210;
    private static final int ROW_HEIGHT = 12;
    private static final String PREFIX = "screen.data_energistics.orbital_control_hud.";

    private OrbitalHudRenderer() {}

    public static OrbitalHudPlacement placement(Preferences preferences, @Nullable WeaponEntry weapon, int width, int height) {
        int rows = weapon == null ? 3 : 2 + Math.min(preferences.mode() == DisplayMode.DETAIL ? 3 : 1, weapon.attacks().size());
        boolean right = switch (preferences.anchor()) {
            case TOP_RIGHT, BOTTOM_RIGHT -> true;
            default -> false;
        };
        boolean bottom = switch (preferences.anchor()) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> true;
            default -> false;
        };
        return OrbitalHudPlacement.resolve(width, height, WIDTH, 8 + rows * ROW_HEIGHT,
                preferences.scale(), right, bottom, preferences.offsetX(), preferences.offsetY());
    }

    public static void render(GuiGraphics graphics, Font font, Preferences preferences,
                              @Nullable WeaponEntry weapon, int width, int height) {
        OrbitalHudPlacement bounds = placement(preferences, weapon, width, height);
        List<Component> rows = rows(preferences.mode(), weapon);
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height());
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x(), bounds.y(), 0);
        graphics.pose().scale(bounds.scale(), bounds.scale(), 1);
        int alpha = Math.round(preferences.opacity() * 255);
        graphics.fill(0, 0, WIDTH, 8 + rows.size() * ROW_HEIGHT, (alpha << 24) | 0x00F3FAFC);
        graphics.renderOutline(0, 0, WIDTH, 8 + rows.size() * ROW_HEIGHT, (alpha << 24) | 0x0035AFC4);
        for (int index = 0; index < rows.size(); index++) {
            String text = rows.get(index).getString();
            if (font.width(text) > WIDTH - 12) {
                text = font.plainSubstrByWidth(text, WIDTH - 12 - font.width("…")) + "…";
            }
            graphics.drawString(font, text, 6, 5 + index * ROW_HEIGHT,
                    index == 1 ? OrbitalControlUiTheme.ACCENT_TEXT : OrbitalControlUiTheme.TEXT, false);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private static List<Component> rows(DisplayMode mode, @Nullable WeaponEntry weapon) {
        if (weapon == null) {
            return List.of(Component.translatable(PREFIX + "preview_title"), Component.translatable(PREFIX + "preview_reserve"),
                    Component.translatable(PREFIX + "preview_status"));
        }
        ObjectArrayList<Component> rows = new ObjectArrayList<>(5);
        rows.add(Component.translatable(PREFIX + "title", weapon.weaponId().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .append(" · ").append(OrbitalControlPresentation.weaponState(weapon)));
        rows.add(Component.translatable(PREFIX + "reserve", amount(weapon.celestialEnergy()), amount(weapon.aeEnergy())));
        int visible = Math.min(mode == DisplayMode.DETAIL ? 3 : 1, weapon.attacks().size());
        for (int index = 0; index < visible; index++) {
            Component state = OrbitalControlPresentation.modeRail(weapon, weapon.attacks().get(index).mode());
            int more = index == visible - 1 ? weapon.attacks().size() - visible : 0;
            rows.add(more > 0 ? Component.translatable(PREFIX + "attack_more", state, more) : state);
        }
        return rows;
    }

    private static String amount(long amount) {
        if (amount >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        if (amount >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000L) return String.format(Locale.ROOT, "%.1fk", amount / 1_000.0);
        return Long.toString(amount);
    }
}
