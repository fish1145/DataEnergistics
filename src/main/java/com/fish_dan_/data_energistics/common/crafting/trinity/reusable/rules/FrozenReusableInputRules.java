package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.Optional;

/** Frozen callback lookup owned by the server lifecycle; plugin registration freezes elsewhere. */
public final class FrozenReusableInputRules implements ReusableInputRules {

    private final List<ReusableInputRuleAdapter> adapters;

    /**
     * Builds one frozen lookup from an already frozen plugin snapshot, rejecting duplicate identities.
     * The list is copied; callbacks are not invoked until a server-thread query is made.
     */
    public FrozenReusableInputRules(List<ReusableInputRuleAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
        ObjectOpenHashSet<ResourceLocation> ids = new ObjectOpenHashSet<>();
        for (ReusableInputRuleAdapter adapter : this.adapters) {
            if (!ids.add(adapter.id())) {
                throw new IllegalStateException("Duplicate reusable input adapter: " + adapter.id());
            }
        }
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return adapters.stream().anyMatch(adapter -> adapter.mayMatch(pattern, recipeId));
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        ReusableInputRule result = null;
        for (ReusableInputRuleAdapter adapter : adapters) {
            Optional<ReusableInputRule> candidate = adapter.resolve(context);
            if (candidate.isEmpty()) {
                continue;
            }
            ReusableInputRule rule = candidate.orElseThrow();
            if (!adapter.id().equals(rule.id()) || !rule.initialKey().equals(context.actualInput().what())) {
                throw new IllegalStateException("Reusable input adapter returned a mismatched identity: " + adapter.id());
            }
            if (result != null) {
                throw new IllegalStateException("Multiple reusable input adapters claim the same input slot: " +
                        result.id() + " and " + rule.id());
            }
            result = rule;
        }
        return Optional.ofNullable(result);
    }
}
