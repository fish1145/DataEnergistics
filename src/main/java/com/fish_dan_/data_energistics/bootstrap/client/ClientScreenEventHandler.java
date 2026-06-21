package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.screen.Ae2TerminalKeyOverlay;
import com.fish_dan_.data_energistics.client.screen.PatternEncodingScreenRouter;
import com.fish_dan_.data_energistics.client.screen.UniversalTerminalScreenHook;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

final class ClientScreenEventHandler {

    private ClientScreenEventHandler() {}

    static void onScreenInitPost(ScreenEvent.Init.Post event) {
        PatternEncodingScreenRouter.onScreenInitPost(event);
        UniversalTerminalScreenHook.onScreenInitPost(event);
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        Screen replacement = PatternEncodingScreenRouter.routeOpeningScreen(event.getCurrentScreen());
        if (replacement != null) {
            event.setNewScreen(replacement);
        }
    }

    static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        UniversalTerminalScreenHook.onScreenRenderPost(event);
        Ae2TerminalKeyOverlay.onScreenRenderPost(event);
    }
}
