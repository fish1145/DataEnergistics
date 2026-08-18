package com.fish_dan_.data_energistics.integration.ae2wtlib.client;

import com.fish_dan_.data_energistics.client.screen.patternencoding.WirelessPatternEncodingTermScreen;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import appeng.client.gui.style.StyleManager;
import de.mari_023.ae2wtlib.wet.WETMenu;
import de.mari_023.ae2wtlib.wet.WETScreen;

public final class Ae2WtLibClientCompat {

    private Ae2WtLibClientCompat() {}

    public static Object maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (!(currentScreen instanceof Screen screen)) {
            return null;
        }

        if (screen instanceof WirelessPatternEncodingTermScreen) {
            return null;
        }

        if (!(screen instanceof WETScreen wetScreen)) {
            return null;
        }

        WETMenu wetMenu = wetScreen.getMenu();
        if (!(wetMenu instanceof PatternEncodingPreviewMenu)) {
            return null;
        }

        Screen replacement = new WirelessPatternEncodingTermScreen(
                wetMenu,
                wetMenu.getPlayerInventory(),
                screen.getTitle(),
                StyleManager.loadStyleDoc("/screens/wtlib/wireless_pattern_encoding_terminal.json"));

        if (applyImmediately) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == screen) {
                minecraft.setScreen(replacement);
            }
        }

        return replacement;
    }
}
