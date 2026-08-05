package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DataExtractorRuleTable {

    private DataExtractorRuleTable() {}

    /** Returns the currently published immutable rule snapshot. */
    public static LoadedRules snapshot() {
        return DataExtractorRulesConfiguration.INSTANCE.rules();
    }

    public static boolean hasRuleForSlot(Slot slot, ItemStack stack) {
        return findRule(slot, stack) != null;
    }

    @Nullable
    public static ItemRule findRule(Slot slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (ItemRule rule : DataExtractorRulesConfiguration.INSTANCE.rules().inputRules()) {
            if (rule.slot() == slot && rule.inputItemId().equals(itemId)) {
                return rule;
            }
        }
        return null;
    }

    public static List<ItemStack> getConfiguredOutputs(DataType dataType, ResourceLocation recordedId) {
        for (OutputRule rule : DataExtractorRulesConfiguration.INSTANCE.rules().outputRules()) {
            if (rule.dataType() == dataType && rule.recordedId().equals(recordedId)) {
                return rule.createStacks();
            }
        }
        return List.of();
    }

    public enum Slot {

        ORE,
        CROP
    }

    public enum DataType {

        MOB,
        ORE,
        CROP
    }

    public record ItemRule(
                           Slot slot,
                           DataType dataType,
                           ResourceLocation inputItemId,
                           ResourceLocation recordedItemId,
                           float progressPerItem,
                           float requiredAmount) {}

    public record OutputRule(
                             DataType dataType,
                             ResourceLocation recordedId,
                             List<ConfiguredStack> outputs) {

        public OutputRule {
            outputs = List.copyOf(outputs);
        }

        public List<ItemStack> createStacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (ConfiguredStack output : outputs) {
                var item = BuiltInRegistries.ITEM.getOptional(output.itemId()).orElse(Items.AIR);
                if (item != Items.AIR) {
                    stacks.add(new ItemStack(item, output.count()));
                }
            }
            return stacks;
        }
    }

    public record ConfiguredStack(ResourceLocation itemId, int count) {}
}
