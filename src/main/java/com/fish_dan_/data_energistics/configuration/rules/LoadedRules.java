package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.OutputRule;

import java.util.List;

/** The complete immutable Data Extractor rule snapshot published to gameplay consumers. */
public record LoadedRules(List<ItemRule> inputRules, List<OutputRule> outputRules) {

    public LoadedRules {
        inputRules = List.copyOf(inputRules);
        outputRules = List.copyOf(outputRules);
    }

    public static LoadedRules empty() {
        return new LoadedRules(List.of(), List.of());
    }
}
