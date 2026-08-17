package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DataExtractorRuleTable {

    private DataExtractorRuleTable() {}

    /** Compiles and returns the current rule data from the Configuration-owned schema. */
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
        for (ItemRule rule : snapshot().inputRules()) {
            if (rule.slot() == slot && rule.inputItemId().equals(itemId)) {
                return rule;
            }
        }
        return null;
    }

    public static List<ItemStack> getConfiguredOutputs(DataType dataType, ResourceLocation recordedId) {
        OutputRule rule = findOutputRule(dataType, recordedId);
        return rule == null ? List.of() : rule.createStacks();
    }

    public static boolean containsConfiguredId(String[] configuredIds, ResourceLocation id) {
        for (String configuredId : configuredIds) {
            ResourceLocation parsed = ResourceLocation.tryParse(configuredId);
            if (id.equals(parsed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first configured output rule for a recorded identity.
     *
     * @param dataType   carrier data type
     * @param recordedId recorded entity or item identity
     * @return configured rule, or {@code null} when configuration has no match
     */
    public static @Nullable OutputRule findOutputRule(DataType dataType, ResourceLocation recordedId) {
        for (OutputRule rule : snapshot().outputRules()) {
            if (rule.dataType() == dataType && rule.recordedId().equals(recordedId)) {
                return rule;
            }
        }
        return null;
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
