package com.fish_dan_.data_energistics.integration.crafting.reusable.mysticalagriculture;

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

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;

import com.blakebr0.mysticalagriculture.item.InfusionCrystalItem;
import com.blakebr0.mysticalagriculture.item.MasterInfusionCrystalItem;

import java.util.List;
import java.util.Optional;

/** Exact Cucumber remainder contracts for MA crystals, loaded only when both optional mods are present. */
@DataEnergisticsEntrypoint(requiredMods = { "mysticalagriculture", "cucumber" })
public final class MysticalAgricultureReusableInputs implements DataEnergisticsPlugin, ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("mystical_agriculture_infusion_crystal");

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
                if (candidate.what() instanceof AEItemKey key && isCrystal(key.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        AEItemKey key = (AEItemKey) context.actualInput().what();
        if (!isCrystal(key.getItem()) || !NativeReusableCrafting.usesStandardItemRemainders(context.pattern(), context.recipeId(), context.level())) {
            // A custom recipe may override remainders; its behavior needs its own authoritative adapter.
            return Optional.empty();
        }
        if (key.getItem().getClass() == MasterInfusionCrystalItem.class) {
            return Optional.of(ReusableInputRule.unchanged(ID, 1L, key));
        }
        var stack = key.toStack();
        var unbreaking = context.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        if (stack.getEnchantmentLevel(unbreaking) != 0) {
            return Optional.empty();
        }
        int exhaustion = Math.addExact(stack.getMaxDamage(), 1);
        return Optional.of(ReusableInputRule.fixedDamage(ID, 1L, key, 1, exhaustion, List.of()));
    }

    private static boolean isCrystal(Item item) {
        return item.getClass() == InfusionCrystalItem.class || item.getClass() == MasterInfusionCrystalItem.class;
    }
}
