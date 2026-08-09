package com.fish_dan_.data_energistics.accessor;

import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;

public interface RedstoneTuningAwareHost {

    boolean dataEnergistics$hasRedstoneTuningCard();

    RedstoneTuningMode dataEnergistics$getRedstoneTuningMode();

    boolean dataEnergistics$setRedstoneTuningMode(RedstoneTuningMode mode);

    void dataEnergistics$onRedstoneTuningDispatch();

    void dataEnergistics$serverTick();

    void dataEnergistics$scheduleRedstoneInputCheck();

    boolean dataEnergistics$consumeRedstoneInputPulse();

    default boolean dataEnergistics$isRedstoneTuningPulseActive() {
        return false;
    }
}
