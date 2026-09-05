package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.screen.trinity.TrinityPatternSearchMatcher;
import com.fish_dan_.data_energistics.client.screen.trinity.TrinityPatternSearchMode;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-local localized search-name cache, independent from AE2's access-terminal screen implementation.
 */
final class TrinityAggregatePatternSearchIndex {

    private static final TrinityPatternSearchMatcher MATCHER = new TrinityPatternSearchMatcher();

    private final Level level;
    private final Map<AEItemKey, PatternSearchNames> namesByDefinition = new HashMap<>();

    TrinityAggregatePatternSearchIndex(Level level) {
        this.level = level;
    }

    boolean matches(ItemStack encodedPattern, String query, TrinityPatternSearchMode mode) {
        if (!this.level.isClientSide()) {
            return false;
        }
        AEItemKey definition = AEItemKey.of(encodedPattern);
        if (definition == null) {
            return false;
        }
        PatternSearchNames names = this.namesByDefinition.computeIfAbsent(
                definition,
                ignored -> decode(encodedPattern));
        return MATCHER.matches(names.inputs(), names.outputs(), names.extraTerms(), mode, query);
    }

    void clear() {
        this.namesByDefinition.clear();
    }

    private PatternSearchNames decode(ItemStack encodedPattern) {
        try {
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(encodedPattern, this.level);
            if (pattern == null) {
                return PatternSearchNames.EMPTY;
            }

            List<String> inputs = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                GenericStack[] alternatives = input.getPossibleInputs();
                if (alternatives.length > 0) {
                    inputs.add(displayName(alternatives[0]));
                }
            }

            List<String> outputs = new ArrayList<>();
            for (GenericStack output : pattern.getOutputs()) {
                outputs.add(displayName(output));
            }

            List<String> extraTerms = new ArrayList<>();
            DataEnergisticsEntrypointLoader.snapshot().trinityPatternSearchTermRegistrations().forEach(
                    registration -> extraTerms.addAll(registration.contributor().searchTerms(encodedPattern)));
            return new PatternSearchNames(inputs, outputs, extraTerms);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.warn(
                    "Skipping malformed aggregate Trinity pattern search data for {}",
                    encodedPattern.getItem(),
                    exception);
            return PatternSearchNames.EMPTY;
        }
    }

    private static String displayName(GenericStack stack) {
        return stack.what().getDisplayName().getString();
    }

    private record PatternSearchNames(List<String> inputs, List<String> outputs, List<String> extraTerms) {

        private static final PatternSearchNames EMPTY = new PatternSearchNames(List.of(), List.of(), List.of());

        private PatternSearchNames {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            extraTerms = List.copyOf(extraTerms);
        }
    }
}
