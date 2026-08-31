package com.fish_dan_.data_energistics.client.crafting.confirm.presentation;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Appends plan-wide inventory usage, typed diagnostic amounts and the selected cycle after AE2's native tooltip.
 */
public final class TrinityCraftConfirmCycleTooltip {

    private static final String KEY_PREFIX = "gui.data_energistics.trinity_planning.cycle.";

    private TrinityCraftConfirmCycleTooltip() {}

    /**
     * Preserves every native line and adds only statistics carried by the current Trinity summary.
     *
     * @param original AE2 tooltip lines
     * @param key      material represented by the hovered table cell
     * @param summary  complete current-revision summary
     * @return the original list when no Trinity detail exists, otherwise a new augmented list
     */
    public static List<Component> append(List<Component> original,
                                         AEKey key,
                                         TrinityCraftingCycleSummary summary,
                                         int selectedCycleOrdinal) {
        OptionalInt inventoryUsage = summary.inventoryUsage(key);
        var exactShortage = summary.exactShortage(key);
        var unresolvedDemand = summary.unresolvedDemand(key);
        Optional<TrinityCraftingCycleHeader> selectedCycle = selectedCycleOrdinal > 0 &&
                selectedCycleOrdinal <= summary.cycles().size() ?
                        Optional.of(summary.cycles().get(selectedCycleOrdinal - 1)) :
                        Optional.empty();
        Optional<TrinityCraftingCycleMaterialContribution> selectedContribution = summary.contributionsFor(key)
                .stream()
                .filter(contribution -> contribution.displayOrdinal() == selectedCycleOrdinal)
                .findFirst();
        if (inventoryUsage.isEmpty() && exactShortage.isEmpty() && unresolvedDemand.isEmpty() &&
                selectedCycle.isEmpty()) {
            return original;
        }

        ArrayList<Component> lines = new ArrayList<>(original);
        if (inventoryUsage.isPresent()) {
            lines.add(detail("inventory_usage", formatPercentage(inventoryUsage.getAsInt())));
        }
        exactShortage.ifPresent(shortage -> {
            lines.add(Component.translatable(KEY_PREFIX + "shortage_required", shortage.required().toString())
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable(KEY_PREFIX + "shortage_available", shortage.available().toString())
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable(KEY_PREFIX + "shortage_missing", shortage.missing().toString())
                    .withStyle(ChatFormatting.RED));
        });
        unresolvedDemand.ifPresent(unresolved -> lines.add(Component.translatable(
                KEY_PREFIX + "unresolved_demand",
                unresolved.amount().toString()).withStyle(ChatFormatting.YELLOW)));
        selectedCycle.ifPresent(cycle -> {
            lines.add(Component.translatable(
                    KEY_PREFIX + "current",
                    cycle.displayOrdinal(),
                    summary.cycles().size()).withStyle(
                            style -> style.withColor(
                                    TrinityCraftConfirmCyclePalette.rgb(cycle.displayOrdinal()))));
            if (summary.cycles().size() > 1) {
                lines.add(Component.translatable(
                        KEY_PREFIX + "switch_hint",
                        DEKeyMappings.PREVIOUS_TRINITY_CYCLE.getTranslatedKeyMessage(),
                        DEKeyMappings.NEXT_TRINITY_CYCLE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        selectedContribution.ifPresent(contribution -> {
            TrinityCraftingCycleHeader cycle = summary.cycles().get(contribution.displayOrdinal() - 1);
            if (contribution.input() && contribution.output()) {
                lines.add(Component.translatable(KEY_PREFIX + "role_input_output").withStyle(ChatFormatting.GRAY));
            } else if (contribution.input()) {
                lines.add(Component.translatable(KEY_PREFIX + "role_input").withStyle(ChatFormatting.GRAY));
            } else if (contribution.output()) {
                lines.add(Component.translatable(KEY_PREFIX + "role_output").withStyle(ChatFormatting.GRAY));
            }
            if (contribution.minimumSeed().signum() > 0) {
                lines.add(detail("minimum_seed", contribution.minimumSeed()));
            }
            int netSignum = contribution.netChange().signum();
            if (netSignum < 0) {
                lines.add(detail("net_consumed", contribution.netChange().negate()));
            } else if (netSignum > 0) {
                lines.add(detail("net_produced", contribution.netChange()));
            } else if (contribution.reused()) {
                lines.add(Component.translatable(KEY_PREFIX + "reused").withStyle(ChatFormatting.GRAY));
            }

            lines.add(detail("repetitions", cycle.repetitions()));
            lines.add(detail("pattern_executions", cycle.patternExecutions()));
            lines.add(detail("stage_count", cycle.stageCount()));
            lines.add(detail("pattern_type_count", cycle.patternTypeCount()));
        });
        return List.copyOf(lines);
    }

    private static Component detail(String suffix, Object value) {
        return Component.translatable(KEY_PREFIX + suffix, value.toString()).withStyle(ChatFormatting.GRAY);
    }

    private static String formatPercentage(int basisPoints) {
        if (basisPoints == 0) {
            return "<0.01%";
        }
        int whole = basisPoints / 100;
        int fraction = basisPoints % 100;
        if (fraction == 0) {
            return whole + "%";
        }
        if (fraction % 10 == 0) {
            return whole + "." + fraction / 10 + "%";
        }
        return whole + "." + (fraction < 10 ? "0" : "") + fraction + "%";
    }
}
