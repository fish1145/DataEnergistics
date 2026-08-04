package com.fish_dan_.data_energistics.configuration.rules;

/** Supplies the legacy configurable values used only when a new default rule file is created. */
public record DefaultRuleValues(String cropInputMappings, float cropRequiredAmount, float oreRequiredAmount) {

    public DefaultRuleValues {
        requirePositiveFinite(cropRequiredAmount, "cropRequiredAmount");
        requirePositiveFinite(oreRequiredAmount, "oreRequiredAmount");
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
