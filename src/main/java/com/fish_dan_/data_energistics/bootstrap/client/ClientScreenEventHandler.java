package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.crafting.tree.CraftingPlanTreeEntry;
import com.fish_dan_.data_energistics.client.screen.crafting.confirm.TrinityCraftConfirmScreenRouter;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingPreviewRenderEvents;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingScreenRouter;
import com.fish_dan_.data_energistics.client.screen.terminal.Ae2TerminalKeyOverlay;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalTerminalScreenHook;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

final class ClientScreenEventHandler {

    private ClientScreenEventHandler() {}

    static void onScreenInitPost(ScreenEvent.Init.Post event) {
        CraftingPlanTreeEntry.onInit(event);
        PatternEncodingScreenRouter.onScreenInitPost(event);
        UniversalTerminalScreenHook.onScreenInitPost(event);
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        Screen replacement = TrinityCraftConfirmScreenRouter.routeOpeningScreen(event.getCurrentScreen());
        if (replacement == null) {
            replacement = PatternEncodingScreenRouter.routeOpeningScreen(event.getCurrentScreen());
        }
        if (replacement != null) {
            event.setNewScreen(replacement);
        }
    }

    static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        UniversalTerminalScreenHook.onScreenRenderPost(event);
        Ae2TerminalKeyOverlay.onScreenRenderPost(event);
    }

    static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        // The terminal selector must be composited after upload panels clear their covered depth.
        PatternEncodingPreviewRenderEvents.onContainerForeground(event);
        UniversalTerminalScreenHook.onContainerForeground(event);
    }

    static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (UniversalTerminalScreenHook.isRenderingSelectorTooltip()) {
            return;
        }
        UniversalTerminalScreenHook.onRenderTooltip(event);
        PatternEncodingPreviewRenderEvents.onRenderTooltip(event);
    }
}
