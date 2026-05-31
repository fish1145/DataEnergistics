package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.StyleManager;
import appeng.menu.AEBaseMenu;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class Ae2WtLibCompat {

    private static final String WET_SCREEN_CLASS = "de.mari_023.ae2wtlib.wet.WETScreen";
    private static final String WET_MENU_CLASS = "de.mari_023.ae2wtlib.wet.WETMenu";
    private static final String WIRELESS_SCREEN_CLASS = "com.fish_dan_.data_energistics.client.screen.WirelessPatternEncodingTermScreen";

    private Ae2WtLibCompat() {}

    public static Screen maybeReplaceWirelessPatternEncodingScreen(Screen currentScreen, boolean applyImmediately) {
        if (!Data_Energistics.Mods.isAe2WtLibLoaded() || currentScreen == null) {
            return null;
        }

        try {
            Class<?> wirelessScreenClass = Class.forName(WIRELESS_SCREEN_CLASS);
            if (wirelessScreenClass.isInstance(currentScreen)) {
                return null;
            }

            Class<?> wetScreenClass = Class.forName(WET_SCREEN_CLASS);
            if (!wetScreenClass.isInstance(currentScreen)) {
                return null;
            }

            Method getMenu = wetScreenClass.getMethod("getMenu");
            Object rawMenu = getMenu.invoke(currentScreen);
            if (!(rawMenu instanceof PatternEncodingPreviewMenu) || !(rawMenu instanceof AEBaseMenu baseMenu)) {
                return null;
            }

            Class<?> wetMenuClass = Class.forName(WET_MENU_CLASS);
            if (!wetMenuClass.isInstance(rawMenu)) {
                return null;
            }

            Constructor<?> constructor = wirelessScreenClass.getConstructor(
                    wetMenuClass,
                    Inventory.class,
                    Component.class,
                    appeng.client.gui.style.ScreenStyle.class);
            Screen replacement = (Screen) constructor.newInstance(
                    wetMenuClass.cast(rawMenu),
                    baseMenu.getPlayerInventory(),
                    currentScreen.getTitle(),
                    StyleManager.loadStyleDoc("/screens/wtlib/wireless_pattern_encoding_terminal.json"));

            if (applyImmediately) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen == currentScreen) {
                    minecraft.setScreen(replacement);
                }
            }

            return replacement;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
