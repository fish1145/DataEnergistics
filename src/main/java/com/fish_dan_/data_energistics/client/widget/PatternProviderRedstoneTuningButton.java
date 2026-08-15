package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;

import appeng.client.gui.Icon;

public class PatternProviderRedstoneTuningButton extends DataExtractorToggleButton {

    private final PatternProviderMenuAccessor menu;

    public PatternProviderRedstoneTuningButton(PatternProviderMenuAccessor menu) {
        super(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.pattern_provider.redstone_tuning",
                "button.data_energistics.pattern_provider.redstone_tuning.pulse_to_unlock_once",
                "button.data_energistics.pattern_provider.redstone_tuning.emit_on_dispatch",
                ignored -> {});
        this.menu = menu;
    }

    @Override
    public void onPress() {
        var mode = RedstoneTuningMode.values()[this.menu.dataEnergistics$getRedstoneTuningMode()].next();
        this.menu.dataEnergistics$setRedstoneTuningMode(mode.ordinal());
        syncFromMenu();
    }

    public void syncFromMenu() {
        setState(RedstoneTuningMode.values()[this.menu.dataEnergistics$getRedstoneTuningMode()] == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE);
        this.visible = this.menu.dataEnergistics$hasRedstoneTuningCard();
        this.active = this.visible;
    }
}
