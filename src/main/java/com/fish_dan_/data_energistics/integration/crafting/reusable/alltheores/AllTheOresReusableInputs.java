package com.fish_dan_.data_energistics.integration.crafting.reusable.alltheores;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

import net.allthemods.alltheores.content.items.OreHammer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** ATO 3.2.0 hammers deterministically damage the input once and break when the new damage reaches maxDamage. */
@DataEnergisticsEntrypoint(requiredMods = "alltheores")
public final class AllTheOresReusableInputs implements DataEnergisticsPlugin, ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("all_the_ores_hammer");

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.reusableInputs().register(this);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        if (!NativeReusableCrafting.usesNativeRecipeValidation(pattern, recipeId)) {
            return false;
        }
        for (var input : pattern.getInputs()) {
            for (var candidate : input.getPossibleInputs()) {
                if (candidate.what() instanceof AEItemKey key && key.getItem().getClass() == OreHammer.class) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        AEItemKey key = (AEItemKey) context.actualInput().what();
        if (key.getItem().getClass() != OreHammer.class ||
                !NativeReusableCrafting.usesStandardItemRemainders(context.pattern(), context.recipeId(), context.level())) {
            return Optional.empty();
        }
        var stack = key.toStack();
        if (stack.getMaxDamage() <= 0 || stack.getDamageValue() >= stack.getMaxDamage()) {
            return Optional.empty();
        }
        return Optional.of(ReusableInputRule.fixedDamage(ID, 1L, key, 1, stack.getMaxDamage(), List.of()));
    }
}
