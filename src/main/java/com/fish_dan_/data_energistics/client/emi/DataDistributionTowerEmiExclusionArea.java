package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.client.screen.DataDistributionTowerScreen;

import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiExclusionArea;
import dev.emi.emi.api.widget.Bounds;

import java.util.function.Consumer;

final class DataDistributionTowerEmiExclusionArea implements EmiExclusionArea<Screen> {

    @Override
    public void addExclusionArea(Screen screen, Consumer<Bounds> consumer) {
        if (!(screen instanceof DataDistributionTowerScreen towerScreen)) {
            return;
        }

        var area = towerScreen.getTargetPopupExclusionArea();
        if (area == null) {
            return;
        }

        consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
    }
}
