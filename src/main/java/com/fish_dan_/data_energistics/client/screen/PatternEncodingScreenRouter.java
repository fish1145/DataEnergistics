package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.integration.Ae2WtLibCompat;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.StyleManager;

public final class PatternEncodingScreenRouter {

    private PatternEncodingScreenRouter() {}

    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        maybeReplaceNativePatternEncodingScreen(event.getScreen(), true);
        Ae2WtLibCompat.maybeReplaceWirelessPatternEncodingScreen(event.getScreen(), true);
    }

    public static Screen routeOpeningScreen(Screen currentScreen) {
        Screen replacement = maybeReplaceNativePatternEncodingScreen(currentScreen, false);
        if (replacement == null) {
            replacement = Ae2WtLibCompat.<Screen>maybeReplaceWirelessPatternEncodingScreen(currentScreen, false);
        }
        return replacement;
    }

    private static Screen maybeReplaceNativePatternEncodingScreen(Screen currentScreen, boolean applyImmediately) {
        if (!(currentScreen instanceof PatternEncodingTermScreen<?> screen)) {
            return null;
        }
        if (currentScreen instanceof PatternEncodingPreviewScreen<?>) {
            return null;
        }
        if (!(screen.getMenu() instanceof PatternEncodingPreviewMenu)) {
            return null;
        }
        if (currentScreen.getClass() != PatternEncodingTermScreen.class) {
            return null;
        }

        Screen replacement = new NativePatternEncodingTermScreen(
                screen.getMenu(),
                screen.getMenu().getPlayerInventory(),
                screen.getTitle(),
                StyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"));

        if (applyImmediately) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == currentScreen) {
                minecraft.setScreen(replacement);
            }
        }

        return replacement;
    }
}
