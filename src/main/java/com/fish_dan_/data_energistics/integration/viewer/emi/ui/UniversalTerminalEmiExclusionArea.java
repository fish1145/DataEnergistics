package com.fish_dan_.data_energistics.integration.viewer.emi.ui;

import com.fish_dan_.data_energistics.client.screen.terminal.UniversalTerminalScreenHook;

import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiExclusionArea;
import dev.emi.emi.api.widget.Bounds;

import java.util.function.Consumer;

public final class UniversalTerminalEmiExclusionArea implements EmiExclusionArea<Screen> {

    @Override
    public void addExclusionArea(Screen screen, Consumer<Bounds> consumer) {
        var selectorPanel = UniversalTerminalScreenHook.getSelectorPanel(screen);
        if (selectorPanel == null || !selectorPanel.isOpen()) {
            return;
        }

        var area = selectorPanel.getExclusionArea();
        consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
    }
}
