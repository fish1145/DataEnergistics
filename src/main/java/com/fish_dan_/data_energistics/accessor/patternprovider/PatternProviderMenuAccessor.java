package com.fish_dan_.data_energistics.accessor.patternprovider;

public interface PatternProviderMenuAccessor {

    boolean dataEnergistics$hasRedstoneTuningCard();

    int dataEnergistics$getRedstoneTuningMode();

    void dataEnergistics$setRedstoneTuningMode(int ordinal);

    default void dataEnergistics$syncRedstoneTuningButton() {}
}
