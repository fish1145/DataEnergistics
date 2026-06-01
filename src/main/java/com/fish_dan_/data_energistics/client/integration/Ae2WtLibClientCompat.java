package com.fish_dan_.data_energistics.client.integration;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.StyleManager;
import appeng.menu.AEBaseMenu;

import java.util.Optional;

public final class Ae2WtLibClientCompat {

    private static final String WET_SCREEN_CLASS = "de.mari_023.ae2wtlib.wet.WETScreen";
    private static final String WET_MENU_CLASS = "de.mari_023.ae2wtlib.wet.WETMenu";
    private static final String WIRELESS_SCREEN_CLASS = "com.fish_dan_.data_energistics.client.screen.WirelessPatternEncodingTermScreen";
    private static final Optional<Class<?>> WET_SCREEN_TYPE = resolveOptionalClass(WET_SCREEN_CLASS);
    private static final Optional<Class<?>> WET_MENU_TYPE = resolveOptionalClass(WET_MENU_CLASS);
    private static final Optional<Class<?>> WIRELESS_SCREEN_TYPE = resolveOptionalClass(WIRELESS_SCREEN_CLASS);

    private Ae2WtLibClientCompat() {}

    public static Object maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (!(currentScreen instanceof Screen screen)) {
            return null;
        }

        try {
            if (WIRELESS_SCREEN_TYPE.isEmpty() || WIRELESS_SCREEN_TYPE.get().isInstance(screen)) {
                return null;
            }

            if (WET_SCREEN_TYPE.isEmpty() || !WET_SCREEN_TYPE.get().isInstance(screen)) {
                return null;
            }

            Object rawMenu = ReflectionAccess.invokeNoArg(screen, "getMenu");
            if (!(rawMenu instanceof PatternEncodingPreviewMenu) || !(rawMenu instanceof AEBaseMenu baseMenu)) {
                return null;
            }

            if (WET_MENU_TYPE.isEmpty()) {
                return null;
            }

            Class<?> wetMenuClass = WET_MENU_TYPE.get();
            if (!wetMenuClass.isInstance(rawMenu)) {
                return null;
            }

            Object replacementScreen = ReflectionAccess.newInstance(
                    WIRELESS_SCREEN_CLASS,
                    new Class<?>[] {
                            wetMenuClass,
                            Inventory.class,
                            Component.class,
                            appeng.client.gui.style.ScreenStyle.class },
                    wetMenuClass.cast(rawMenu),
                    baseMenu.getPlayerInventory(),
                    screen.getTitle(),
                    StyleManager.loadStyleDoc("/screens/wtlib/wireless_pattern_encoding_terminal.json"));
            if (!(replacementScreen instanceof Screen replacement)) {
                return null;
            }

            if (applyImmediately) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen == screen) {
                    minecraft.setScreen(replacement);
                }
            }

            return replacement;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Optional<Class<?>> resolveOptionalClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
