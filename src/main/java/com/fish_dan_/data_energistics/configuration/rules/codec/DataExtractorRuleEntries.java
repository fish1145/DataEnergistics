package com.fish_dan_.data_energistics.configuration.rules.codec;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ConfiguredStack;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.OutputRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.schema.CarrierRuleSchema;
import com.fish_dan_.data_energistics.configuration.rules.schema.OutputRuleSchema;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts aligned native Configuration arrays into complete immutable rule snapshots. */
public final class DataExtractorRuleEntries {

    private DataExtractorRuleEntries() {}

    public static LoadedRules compile(
                                      CarrierRuleSchema carriers,
                                      OutputRuleSchema outputs,
                                      Path source) throws RuleFormatException {
        return new LoadedRules(parseCarriers(carriers, source), parseOutputs(outputs, source));
    }

    private static List<ItemRule> parseCarriers(CarrierRuleSchema schema, Path source) throws RuleFormatException {
        int rowCount = schema.slots.length;
        requireLength(source, "carrierRules.dataTypes", rowCount, schema.dataTypes.length);
        requireLength(source, "carrierRules.inputItems", rowCount, schema.inputItems.length);
        requireLength(source, "carrierRules.recordedItems", rowCount, schema.recordedItems.length);
        requireLength(source, "carrierRules.progressPerItems", rowCount, schema.progressPerItems.length);
        requireLength(source, "carrierRules.requiredAmounts", rowCount, schema.requiredAmounts.length);

        List<ItemRule> rules = new ArrayList<>(rowCount);
        Map<CarrierKey, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < rowCount; index++) {
            String path = "carrierRules[" + index + "]";
            Slot slot = requireEnum(source, path + ".slot", schema.slots[index]);
            DataType dataType = requireEnum(source, path + ".dataType", schema.dataTypes[index]);
            ResourceLocation inputItem = parseId(source, path + ".inputItem", schema.inputItems[index]);
            ResourceLocation recordedItem = parseId(source, path + ".recordedItem", schema.recordedItems[index]);
            float progress = positive(source, path + ".progressPerItem", schema.progressPerItems[index]);
            float required = positive(source, path + ".requiredAmount", schema.requiredAmounts[index]);

            CarrierKey key = new CarrierKey(slot, inputItem);
            Integer previous = indexes.putIfAbsent(key, index);
            if (previous != null) {
                throw invalid(
                        source,
                        path,
                        "duplicate carrier row; the same slot and input item first appear at carrierRules[" +
                                previous + "]",
                        inputItem.toString(),
                        "keep exactly one carrier row for this slot and input item");
            }
            rules.add(new ItemRule(slot, dataType, inputItem, recordedItem, progress, required));
        }
        return List.copyOf(rules);
    }

    private static List<OutputRule> parseOutputs(OutputRuleSchema schema, Path source) throws RuleFormatException {
        int rowCount = schema.dataTypes.length;
        requireLength(source, "outputRules.recordedItems", rowCount, schema.recordedItems.length);
        requireLength(source, "outputRules.items", rowCount, schema.items.length);
        requireLength(source, "outputRules.counts", rowCount, schema.counts.length);

        Map<OutputKey, OutputRows> grouped = new LinkedHashMap<>();
        for (int index = 0; index < rowCount; index++) {
            String path = "outputRules[" + index + "]";
            DataType dataType = requireEnum(source, path + ".dataType", schema.dataTypes[index]);
            ResourceLocation recordedItem = parseId(source, path + ".recordedItem", schema.recordedItems[index]);
            ResourceLocation item = parseId(source, path + ".item", schema.items[index]);
            int count = schema.counts[index];
            if (count <= 0) {
                throw invalid(
                        source,
                        path + ".count",
                        "output count must be positive",
                        Integer.toString(count),
                        "use an integer between 1 and " + Integer.MAX_VALUE);
            }
            grouped.computeIfAbsent(new OutputKey(dataType, recordedItem), ignored -> new OutputRows())
                    .add(source, path, item, count, index);
        }

        List<OutputRule> rules = new ArrayList<>(grouped.size());
        for (Map.Entry<OutputKey, OutputRows> entry : grouped.entrySet()) {
            OutputKey key = entry.getKey();
            rules.add(new OutputRule(key.dataType(), key.recordedItem(), entry.getValue().stacks()));
        }
        return List.copyOf(rules);
    }

    private static void requireLength(Path source, String path, int expected, int actual) throws RuleFormatException {
        if (actual != expected) {
            throw invalid(
                    source,
                    path,
                    "parallel rule arrays must have the same length",
                    "expected=" + expected + ", actual=" + actual,
                    "add or remove entries so every array in this rule group has the same size");
        }
    }

    private static <E extends Enum<E>> E requireEnum(Path source, String path, E value) throws RuleFormatException {
        if (value == null) {
            throw invalid(source, path, "enum value must not be null", "null", "select one supported value");
        }
        return value;
    }

    private static ResourceLocation parseId(Path source, String path, String value) throws RuleFormatException {
        if (value == null || value.isBlank()) {
            throw invalid(source, path, "registry id must not be blank", String.valueOf(value), "use namespace:path");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw invalid(source, path, "invalid registry id", value, "use lowercase namespace:path syntax");
        }
        return parsed;
    }

    private static float positive(Path source, String path, float value) throws RuleFormatException {
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw invalid(
                    source,
                    path,
                    "value must be finite and positive",
                    Float.toString(value),
                    "use a finite number greater than zero");
        }
        return value;
    }

    private static RuleFormatException invalid(
                                               Path source,
                                               String path,
                                               String violation,
                                               String actual,
                                               String repair) {
        return new RuleFormatException(source, path, violation, actual, repair);
    }

    private record CarrierKey(Slot slot, ResourceLocation inputItem) {}

    private record OutputKey(DataType dataType, ResourceLocation recordedItem) {}

    private static final class OutputRows {

        private final Map<ResourceLocation, IndexedStack> rows = new LinkedHashMap<>();

        void add(Path source, String path, ResourceLocation item, int count, int index) throws RuleFormatException {
            IndexedStack previous = this.rows.get(item);
            if (previous == null) {
                this.rows.put(item, new IndexedStack(index, new ConfiguredStack(item, count)));
            } else if (previous.stack().count() != count) {
                throw invalid(
                        source,
                        path,
                        "conflicting count for an output first declared at outputRules[" + previous.index() + "]",
                        item + "@" + count,
                        "keep one count for this output item");
            }
        }

        List<ConfiguredStack> stacks() {
            return this.rows.values().stream().map(IndexedStack::stack).toList();
        }
    }

    private record IndexedStack(int index, ConfiguredStack stack) {}
}
