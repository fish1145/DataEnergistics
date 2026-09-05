package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.ae.ae2wtlib.Ae2WtLibCompat;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.StyleManager;
import org.jspecify.annotations.Nullable;

public final class PatternEncodingScreenRouter {

    private PatternEncodingScreenRouter() {}

    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        maybeReplaceNativePatternEncodingScreen(event.getScreen(), true);
        if (ModFlags.isAe2WtLibWirelessPatternEncodingSupportLoaded()) {
            Ae2WtLibCompat.maybeReplaceWirelessPatternEncodingScreen(event.getScreen(), true);
        }
    }

    public static @Nullable Screen routeOpeningScreen(@Nullable Screen currentScreen) {
        if (currentScreen == null) {
            return null;
        }
        Screen replacement = maybeReplaceNativePatternEncodingScreen(currentScreen, false);
        if (replacement == null && ModFlags.isAe2WtLibWirelessPatternEncodingSupportLoaded()) {
            replacement = Ae2WtLibCompat.maybeReplaceWirelessPatternEncodingScreen(currentScreen, false);
        }
        return replacement;
    }

    private static @Nullable Screen maybeReplaceNativePatternEncodingScreen(Screen currentScreen,
                                                                            boolean applyImmediately) {
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
