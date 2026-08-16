package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataExtractorRuleTable {

    private static volatile IndexedRules indexedRules = IndexedRules.empty();

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
        return indexedRules().inputRules().get(new InputKey(slot, itemId));
    }

    public static List<ItemStack> getConfiguredOutputs(DataType dataType, ResourceLocation recordedId) {
        OutputRule rule = findOutputRule(dataType, recordedId);
        return rule == null ? List.of() : rule.createStacks();
    }

    /**
     * Finds the first configured output rule for a recorded identity in constant time.
     *
     * @param dataType   carrier data type
     * @param recordedId recorded entity or item identity
     * @return published rule, or {@code null} when configuration has no match
     */
    public static @Nullable OutputRule findOutputRule(DataType dataType, ResourceLocation recordedId) {
        return indexedRules().outputRules().get(new OutputKey(dataType, recordedId));
    }

    private static IndexedRules indexedRules() {
        LoadedRules published = snapshot();
        IndexedRules current = indexedRules;
        if (current.source() == published) {
            return current;
        }

        synchronized (DataExtractorRuleTable.class) {
            current = indexedRules;
            if (current.source() != published) {
                current = IndexedRules.create(published);
                indexedRules = current;
            }
            return current;
        }
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

    private record InputKey(Slot slot, ResourceLocation itemId) {}

    private record OutputKey(DataType dataType, ResourceLocation recordedId) {}

    /** Immutable lookup indexes bound by identity to one published rule snapshot. */
    private record IndexedRules(
                                LoadedRules source,
                                Map<InputKey, ItemRule> inputRules,
                                Map<OutputKey, OutputRule> outputRules) {

        private static IndexedRules empty() {
            return new IndexedRules(LoadedRules.empty(), Map.of(), Map.of());
        }

        private static IndexedRules create(LoadedRules source) {
            Map<InputKey, ItemRule> inputs = new HashMap<>();
            for (ItemRule rule : source.inputRules()) {
                inputs.putIfAbsent(new InputKey(rule.slot(), rule.inputItemId()), rule);
            }

            Map<OutputKey, OutputRule> outputs = new HashMap<>();
            for (OutputRule rule : source.outputRules()) {
                outputs.putIfAbsent(new OutputKey(rule.dataType(), rule.recordedId()), rule);
            }
            return new IndexedRules(source, Map.copyOf(inputs), Map.copyOf(outputs));
        }
    }
}
