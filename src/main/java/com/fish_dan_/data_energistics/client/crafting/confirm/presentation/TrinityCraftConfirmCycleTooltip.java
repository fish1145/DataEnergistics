package com.fish_dan_.data_energistics.client.crafting.confirm.presentation;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
        List<TrinityCraftingCycleMaterialContribution> contributions = summary.contributionsFor(key);
        var selectedContribution = contributions
                .stream()
                .filter(contribution -> contribution.displayOrdinal() == selectedCycleOrdinal)
                .findFirst();
        if (inventoryUsage.isEmpty() && exactShortage.isEmpty() && unresolvedDemand.isEmpty() &&
                selectedContribution.isEmpty()) {
            return original;
        }

        ArrayList<Component> lines = new ArrayList<>(original);
        if (inventoryUsage.isPresent()) {
            lines.add(detail("inventory_usage", formatPercentage(inventoryUsage.getAsInt())));
        }
        exactShortage.ifPresent(shortage -> {
            lines.add(Component.translatable(
                    KEY_PREFIX + "shortage_required",
                    TrinityAmountFormatter.format(shortage.required()))
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable(
                    KEY_PREFIX + "shortage_available",
                    TrinityAmountFormatter.format(shortage.available()))
                    .withStyle(ChatFormatting.RED));
            lines.add(Component.translatable(
                    KEY_PREFIX + "shortage_missing",
                    TrinityAmountFormatter.format(shortage.missing()))
                    .withStyle(ChatFormatting.RED));
        });
        unresolvedDemand.ifPresent(unresolved -> lines.add(Component.translatable(
                KEY_PREFIX + "unresolved_demand",
                TrinityAmountFormatter.format(unresolved.amount())).withStyle(ChatFormatting.YELLOW)));
        selectedContribution.ifPresent(contribution -> {
            TrinityCraftingCycleHeader cycle = summary.cycles().get(contribution.displayOrdinal() - 1);
            int relatedPage = contributions.indexOf(contribution) + 1;
            lines.add(Component.translatable(
                    KEY_PREFIX + "current_related",
                    relatedPage,
                    contributions.size(),
                    cycle.displayOrdinal()).withStyle(
                            style -> style.withColor(
                                    TrinityCraftConfirmCyclePalette.rgb(cycle.displayOrdinal()))));
            if (contributions.size() > 1) {
                lines.add(Component.translatable(
                        KEY_PREFIX + "switch_hint",
                        DEKeyMappings.PREVIOUS_TRINITY_CYCLE.getTranslatedKeyMessage(),
                        DEKeyMappings.NEXT_TRINITY_CYCLE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
            }
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
        return lines;
    }

    private static Component detail(String suffix, String value) {
        return Component.translatable(KEY_PREFIX + suffix, value).withStyle(ChatFormatting.GRAY);
    }

    private static Component detail(String suffix, BigInteger value) {
        return detail(suffix, TrinityAmountFormatter.format(value));
    }

    private static Component detail(String suffix, int value) {
        return detail(suffix, TrinityAmountFormatter.format(value));
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
