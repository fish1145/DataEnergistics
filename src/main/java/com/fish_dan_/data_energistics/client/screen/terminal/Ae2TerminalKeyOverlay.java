package com.fish_dan_.data_energistics.client.screen.terminal;

import net.neoforged.neoforge.client.event.ScreenEvent;

public final class Ae2TerminalKeyOverlay {

    private Ae2TerminalKeyOverlay() {}

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        // Disabled: post-render overlay draws above hover/tooltip layers and makes the key background float on top.
    }
}
