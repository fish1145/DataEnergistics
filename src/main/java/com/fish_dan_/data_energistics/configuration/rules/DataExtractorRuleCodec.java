package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.ConfiguredStack;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.OutputRule;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.Slot;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Strictly decodes v1 rule documents and converts the supported unversioned v0 shapes. */
public final class DataExtractorRuleCodec {

    static final int SCHEMA_VERSION = 1;

    private static final int MAX_JSON_DEPTH = 64;
    private static final byte UTF8_BOM_FIRST = (byte) 0xEF;
    private static final byte UTF8_BOM_SECOND = (byte) 0xBB;
    private static final byte UTF8_BOM_THIRD = (byte) 0xBF;

    private static final Gson PRETTY_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final Set<String> V1_ROOT_FIELDS = Set.of("schema_version", "carrier_rules", "output_rules");
    private static final Set<String> V1_CARRIER_FIELDS = Set.of(
            "slot",
            "data_type",
            "input_item",
            "recorded_item",
            "progress_per_item",
            "required_amount");
    private static final Set<String> V1_OUTPUT_RULE_FIELDS = Set.of("data_type", "recorded_item", "outputs");
    private static final Set<String> OUTPUT_STACK_FIELDS = Set.of("item", "count");
    private static final Set<String> V0_ROOT_FIELDS = Set.of("carrier_rules", "input_rules", "rules", "output_rules");
    private static final Set<String> V0_CARRIER_FIELDS = Set.of(
            "slot",
            "data_type",
            "final_carrier",
            "final_carrier_item",
            "input_item",
            "recorded_item",
            "progress_per_item",
            "required_amount",
            "mimetic_outputs",
            "outputs");
    private static final Set<String> V0_OUTPUT_RULE_FIELDS = Set.of(
            "data_type",
            "recorded_id",
            "recorded_item",
            "outputs");

    private DataExtractorRuleCodec() {}

    static DecodedDocument decode(byte[] content, Path source) throws RuleFormatException {
        JsonElement parsed = parseStrictJson(content, source);
        if (!parsed.isJsonObject()) {
            throw failure(source, "$", "the document root must be an object", parsed, "wrap the rule fields in a JSON object");
        }

        JsonObject root = parsed.getAsJsonObject();
        if (root.has("schema_version")) {
            return new DecodedDocument(SCHEMA_VERSION, root, decodeV1(root, source));
        }

        JsonObject migrated = migrateV0(root, source);
        return new DecodedDocument(0, migrated, decodeV1(migrated, source));
    }

    static byte[] encode(JsonObject root) {
        return (PRETTY_GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static JsonObject createDefault(DefaultRuleValues values, Path source) throws RuleFormatException {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty(
                "_comment",
                "carrier_rules controls extractor inputs; output_rules controls mimetic outputs.\n" + "carrier_rules \u63A7\u5236\u63D0\u53D6\u4EEA\u8F93\u5165\uFF1Boutput_rules " + "\u63A7\u5236\u62DF\u6001\u8F93\u51FA\u3002");

        JsonArray carriers = new JsonArray();
        Set<ResourceLocation> configuredInputs = new HashSet<>();
        String[] mappings = values.cropInputMappings().split(",", -1);
        for (int index = 0; index < mappings.length; index++) {
            String mapping = mappings[index].trim();
            if (mapping.isEmpty()) {
                continue;
            }
            JsonObject carrier = parseDefaultCropMapping(mapping, values.cropRequiredAmount(), source, index);
            ResourceLocation inputId = parseResourceLocation(
                    carrier.get("input_item"),
                    source,
                    "$default.cropInputMappings[" + index + "]",
                    "input item");
            if (!configuredInputs.add(inputId)) {
                throw new RuleFormatException(
                        source,
                        "$default.cropInputMappings[" + index + "]",
                        "the input item is mapped more than once",
                        inputId.toString(),
                        "keep exactly one mapping for each input item");
            }
            carriers.add(carrier);
        }

        ResourceLocation oakSapling = ResourceLocation.parse("minecraft:oak_sapling");
        if (configuredInputs.add(oakSapling)) {
            carriers.add(carrierJson(
                    "crop",
                    "crop",
                    oakSapling.toString(),
                    oakSapling.toString(),
                    1.0F,
                    values.cropRequiredAmount()));
        }

        ResourceLocation rawGold = ResourceLocation.parse("minecraft:raw_gold");
        if (configuredInputs.add(rawGold)) {
            carriers.add(carrierJson(
                    "ore",
                    "ore",
                    rawGold.toString(),
                    "minecraft:gold_ore",
                    1.0F,
                    values.oreRequiredAmount()));
        }
        root.add("carrier_rules", carriers);

        JsonArray outputRules = new JsonArray();
        outputRules.add(outputRuleJson(
                "crop",
                oakSapling.toString(),
                List.of(
                        configuredStackJson("minecraft:oak_log", 4),
                        configuredStackJson("minecraft:oak_leaves", 2),
                        configuredStackJson("minecraft:stick", 2),
                        configuredStackJson("minecraft:apple", 1))));
        outputRules.add(outputRuleJson(
                "ore",
                "minecraft:gold_ore",
                List.of(configuredStackJson("minecraft:gold_ore", 1))));
        root.add("output_rules", outputRules);

        JsonArray mobExamples = new JsonArray();
        mobExamples.add(mobExample(
                "minecraft:zombie",
                List.of(
                        configuredStackJson("minecraft:rotten_flesh", 2),
                        configuredStackJson("minecraft:iron_ingot", 1))));
        mobExamples.add(mobExample(
                "minecraft:skeleton",
                List.of(
                        configuredStackJson("minecraft:bone", 2),
                        configuredStackJson("minecraft:arrow", 2))));
        root.add("_mob_rule_examples", mobExamples);

        decodeV1(root, source);
        return root;
    }

    private static LoadedRules decodeV1(JsonObject root, Path source) throws RuleFormatException {
        validateFields(root, V1_ROOT_FIELDS, source, "$", "v1 root");
        int version = requireInteger(root, "schema_version", source, "$");
        if (version != SCHEMA_VERSION) {
            throw new RuleFormatException(
                    source,
                    "$.schema_version",
                    version > SCHEMA_VERSION ? "the schema version is newer than this mod supports" : "unsupported schema version",
                    Integer.toString(version),
                    version > SCHEMA_VERSION ? "update Data Energistics before loading this file" : "migrate the document to schema_version 1");
        }

        JsonArray carrierArray = requireArray(root, "carrier_rules", source, "$");
        List<ItemRule> inputRules = new ArrayList<>();
        Map<CarrierKey, Integer> carrierIndexes = new HashMap<>();
        for (int index = 0; index < carrierArray.size(); index++) {
            String path = "$.carrier_rules[" + index + "]";
            JsonObject object = requireObject(carrierArray.get(index), source, path);
            validateFields(object, V1_CARRIER_FIELDS, source, path, "carrier rule");

            Slot slot = parseSlot(requireString(object, "slot", source, path), source, path + ".slot");
            DataType dataType = parseV1DataType(
                    requireString(object, "data_type", source, path), source, path + ".data_type");
            ResourceLocation inputItem = parseResourceLocation(
                    require(object, "input_item", source, path), source, path + ".input_item", "input item");
            ResourceLocation recordedItem = parseResourceLocation(
                    require(object, "recorded_item", source, path), source, path + ".recorded_item", "recorded item");
            float progressPerItem = requirePositiveFloat(object, "progress_per_item", source, path);
            float requiredAmount = requirePositiveFloat(object, "required_amount", source, path);

            CarrierKey key = new CarrierKey(slot, inputItem);
            Integer previous = carrierIndexes.putIfAbsent(key, index);
            if (previous != null) {
                throw new RuleFormatException(
                        source,
                        path,
                        "duplicate carrier mapping; the same slot and input item first appear at $.carrier_rules[" + previous + "]",
                        inputItem.toString(),
                        "keep exactly one carrier rule for this slot and input item");
            }
            inputRules.add(new ItemRule(slot, dataType, inputItem, recordedItem, progressPerItem, requiredAmount));
        }

        JsonArray outputArray = requireArray(root, "output_rules", source, "$");
        List<OutputRule> outputRules = new ArrayList<>();
        Map<OutputKey, IndexedOutput> indexedOutputs = new LinkedHashMap<>();
        for (int index = 0; index < outputArray.size(); index++) {
            String path = "$.output_rules[" + index + "]";
            JsonObject object = requireObject(outputArray.get(index), source, path);
            validateFields(object, V1_OUTPUT_RULE_FIELDS, source, path, "output rule");

            DataType dataType = parseV1DataType(
                    requireString(object, "data_type", source, path), source, path + ".data_type");
            ResourceLocation recordedItem = parseResourceLocation(
                    require(object, "recorded_item", source, path), source, path + ".recorded_item", "recorded id");
            List<ConfiguredStack> outputs = parseOutputs(
                    requireArray(object, "outputs", source, path), source, path + ".outputs");
            if (outputs.isEmpty()) {
                throw new RuleFormatException(
                        source,
                        path + ".outputs",
                        "an output rule must contain at least one output",
                        "[]",
                        "add one or more {item, count} entries or remove the output rule");
            }

            OutputKey key = new OutputKey(dataType, recordedItem);
            IndexedOutput previous = indexedOutputs.get(key);
            OutputRule rule = new OutputRule(dataType, recordedItem, outputs);
            if (previous == null) {
                indexedOutputs.put(key, new IndexedOutput(index, rule));
                outputRules.add(rule);
            } else if (!sameOutputs(previous.rule().outputs(), outputs)) {
                throw new RuleFormatException(
                        source,
                        path,
                        "conflicting output rule; this data type and recorded id first appear at $.output_rules[" + previous.index() + "]",
                        dataType.name() + ":" + recordedItem,
                        "merge the outputs into one rule or make duplicate rules identical");
            }
        }

        return new LoadedRules(inputRules, outputRules);
    }

    private static JsonObject migrateV0(JsonObject root, Path source) throws RuleFormatException {
        validateFields(root, V0_ROOT_FIELDS, source, "$", "unversioned v0 root");

        List<String> carrierAliases = new ArrayList<>();
        for (String alias : List.of("carrier_rules", "input_rules", "rules")) {
            if (root.has(alias)) {
                carrierAliases.add(alias);
            }
        }
        if (carrierAliases.size() > 1) {
            throw new RuleFormatException(
                    source,
                    "$",
                    "multiple v0 carrier array aliases are present",
                    carrierAliases.toString(),
                    "keep only one of carrier_rules, input_rules, or rules");
        }

        JsonObject migrated = new JsonObject();
        migrated.addProperty("schema_version", SCHEMA_VERSION);
        copyMetadata(root, migrated);
        JsonArray migratedCarriers = new JsonArray();
        JsonArray migratedOutputs = new JsonArray();

        if (!carrierAliases.isEmpty()) {
            String alias = carrierAliases.getFirst();
            JsonArray carriers = requireArray(root, alias, source, "$");
            for (int index = 0; index < carriers.size(); index++) {
                String path = "$." + alias + "[" + index + "]";
                MigratedCarrier carrier = migrateV0Carrier(
                        requireObject(carriers.get(index), source, path), source, path);
                migratedCarriers.add(carrier.carrier());
                carrier.outputRule().ifPresent(migratedOutputs::add);
            }
        }

        if (root.has("output_rules")) {
            JsonArray outputs = requireArray(root, "output_rules", source, "$");
            for (int index = 0; index < outputs.size(); index++) {
                String path = "$.output_rules[" + index + "]";
                migratedOutputs.add(migrateV0OutputRule(
                        requireObject(outputs.get(index), source, path), source, path));
            }
        }

        migrated.add("carrier_rules", migratedCarriers);
        migrated.add("output_rules", migratedOutputs);
        return migrated;
    }

    private static MigratedCarrier migrateV0Carrier(JsonObject sourceObject, Path source, String path)
                                                                                                       throws RuleFormatException {
        validateFields(sourceObject, V0_CARRIER_FIELDS, source, path, "v0 carrier rule");
        String slot = requireString(sourceObject, "slot", source, path);
        parseSlot(slot, source, path + ".slot");
        DataType dataType = resolveV0DataType(sourceObject, source, path);
        String inputItem = requireString(sourceObject, "input_item", source, path);
        parseResourceLocation(sourceObject.get("input_item"), source, path + ".input_item", "input item");
        String recordedItem = requireString(sourceObject, "recorded_item", source, path);
        parseResourceLocation(sourceObject.get("recorded_item"), source, path + ".recorded_item", "recorded item");
        float progressPerItem = requirePositiveFloat(sourceObject, "progress_per_item", source, path);
        float requiredAmount = requirePositiveFloat(sourceObject, "required_amount", source, path);

        JsonObject carrier = carrierJson(
                slot,
                serializedName(dataType),
                inputItem,
                recordedItem,
                progressPerItem,
                requiredAmount);
        copyMetadata(sourceObject, carrier);

        boolean hasMimeticOutputs = sourceObject.has("mimetic_outputs");
        boolean hasOutputs = sourceObject.has("outputs");
        if (hasMimeticOutputs && hasOutputs) {
            throw new RuleFormatException(
                    source,
                    path,
                    "both v0 embedded output aliases are present",
                    "mimetic_outputs and outputs",
                    "keep only mimetic_outputs or outputs");
        }

        Optional<JsonObject> outputRule = Optional.empty();
        if (hasMimeticOutputs || hasOutputs) {
            String field = hasMimeticOutputs ? "mimetic_outputs" : "outputs";
            JsonArray legacyOutputs = requireArray(sourceObject, field, source, path);
            if (!legacyOutputs.isEmpty()) {
                JsonObject migratedOutput = new JsonObject();
                migratedOutput.addProperty("data_type", serializedName(dataType));
                migratedOutput.addProperty("recorded_item", recordedItem);
                migratedOutput.add("outputs", migrateV0Outputs(legacyOutputs, source, path + "." + field));
                outputRule = Optional.of(migratedOutput);
            }
        }
        return new MigratedCarrier(carrier, outputRule);
    }

    private static JsonObject migrateV0OutputRule(JsonObject sourceObject, Path source, String path)
                                                                                                     throws RuleFormatException {
        validateFields(sourceObject, V0_OUTPUT_RULE_FIELDS, source, path, "v0 output rule");
        DataType dataType = parseLegacyDataType(
                requireString(sourceObject, "data_type", source, path), source, path + ".data_type");
        String recordedItem = resolveRecordedItemAlias(sourceObject, source, path);
        JsonArray outputs = requireArray(sourceObject, "outputs", source, path);
        if (outputs.isEmpty()) {
            throw new RuleFormatException(
                    source,
                    path + ".outputs",
                    "an output rule must contain at least one output",
                    "[]",
                    "add an output or remove this legacy output rule");
        }

        JsonObject migrated = new JsonObject();
        migrated.addProperty("data_type", serializedName(dataType));
        migrated.addProperty("recorded_item", recordedItem);
        migrated.add("outputs", migrateV0Outputs(outputs, source, path + ".outputs"));
        copyMetadata(sourceObject, migrated);
        return migrated;
    }

    private static JsonArray migrateV0Outputs(JsonArray outputs, Path source, String path)
                                                                                           throws RuleFormatException {
        JsonArray migrated = new JsonArray();
        for (int index = 0; index < outputs.size(); index++) {
            String outputPath = path + "[" + index + "]";
            JsonObject object = requireObject(outputs.get(index), source, outputPath);
            validateFields(object, OUTPUT_STACK_FIELDS, source, outputPath, "configured output");
            String item = requireString(object, "item", source, outputPath);
            parseResourceLocation(object.get("item"), source, outputPath + ".item", "output item");
            int count = requirePositiveInteger(object, "count", source, outputPath);

            JsonObject migratedOutput = configuredStackJson(item, count);
            copyMetadata(object, migratedOutput);
            migrated.add(migratedOutput);
        }
        return migrated;
    }

    private static DataType resolveV0DataType(JsonObject object, Path source, String path)
                                                                                           throws RuleFormatException {
        List<DataType> resolved = new ArrayList<>();
        List<String> declarations = new ArrayList<>();
        if (object.has("data_type")) {
            String value = requireString(object, "data_type", source, path);
            resolved.add(parseLegacyDataType(value, source, path + ".data_type"));
            declarations.add("data_type=" + value);
        }
        if (object.has("final_carrier")) {
            String value = requireString(object, "final_carrier", source, path);
            resolved.add(parseLegacyDataType(value, source, path + ".final_carrier"));
            declarations.add("final_carrier=" + value);
        }
        if (object.has("final_carrier_item")) {
            String value = requireString(object, "final_carrier_item", source, path);
            resolved.add(parseCarrierItemType(value, source, path + ".final_carrier_item"));
            declarations.add("final_carrier_item=" + value);
        }
        if (resolved.isEmpty()) {
            throw new RuleFormatException(
                    source,
                    path,
                    "the carrier data type is missing",
                    "<missing>",
                    "add data_type, final_carrier, or final_carrier_item");
        }
        DataType first = resolved.getFirst();
        for (DataType value : resolved) {
            if (value != first) {
                throw new RuleFormatException(
                        source,
                        path,
                        "the legacy carrier type declarations conflict",
                        declarations.toString(),
                        "make all provided carrier type fields describe the same type");
            }
        }
        return first;
    }

    private static String resolveRecordedItemAlias(JsonObject object, Path source, String path)
                                                                                                throws RuleFormatException {
        boolean hasRecordedItem = object.has("recorded_item");
        boolean hasRecordedId = object.has("recorded_id");
        if (!hasRecordedItem && !hasRecordedId) {
            throw new RuleFormatException(
                    source,
                    path,
                    "the recorded id is missing",
                    "<missing>",
                    "add recorded_item (or recorded_id in the legacy document)");
        }
        if (hasRecordedItem && hasRecordedId) {
            String item = requireString(object, "recorded_item", source, path);
            String id = requireString(object, "recorded_id", source, path);
            if (item.equals(id)) {
                parseResourceLocation(new JsonPrimitive(item), source, path + ".recorded_item", "recorded id");
                return item;
            }
            throw new RuleFormatException(
                    source,
                    path,
                    "recorded_item and recorded_id conflict",
                    "recorded_item=" + item + ", recorded_id=" + id,
                    "keep one recorded id or make both aliases identical");
        }
        String resolved = hasRecordedItem ? requireString(object, "recorded_item", source, path) : requireString(object, "recorded_id", source, path);
        parseResourceLocation(new JsonPrimitive(resolved), source, path + ".recorded_item", "recorded id");
        return resolved;
    }

    private static List<ConfiguredStack> parseOutputs(JsonArray outputArray, Path source, String path)
                                                                                                       throws RuleFormatException {
        List<ConfiguredStack> outputs = new ArrayList<>();
        Map<ResourceLocation, IndexedStack> indexedStacks = new LinkedHashMap<>();
        for (int index = 0; index < outputArray.size(); index++) {
            String outputPath = path + "[" + index + "]";
            JsonObject object = requireObject(outputArray.get(index), source, outputPath);
            validateFields(object, OUTPUT_STACK_FIELDS, source, outputPath, "configured output");
            ResourceLocation item = parseResourceLocation(
                    require(object, "item", source, outputPath), source, outputPath + ".item", "output item");
            int count = requirePositiveInteger(object, "count", source, outputPath);
            IndexedStack previous = indexedStacks.get(item);
            if (previous == null) {
                ConfiguredStack stack = new ConfiguredStack(item, count);
                indexedStacks.put(item, new IndexedStack(index, stack));
                outputs.add(stack);
            } else if (previous.stack().count() != count) {
                throw new RuleFormatException(
                        source,
                        outputPath,
                        "conflicting duplicate output; this item first appears at " + path + "[" + previous.index() + "] with count " + previous.stack().count(),
                        Integer.toString(count),
                        "keep one count for each output item");
            }
        }
        return List.copyOf(outputs);
    }

    private static boolean sameOutputs(List<ConfiguredStack> first, List<ConfiguredStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (ConfiguredStack stack : first) {
            counts.put(stack.itemId(), stack.count());
        }
        for (ConfiguredStack stack : second) {
            if (!Integer.valueOf(stack.count()).equals(counts.get(stack.itemId()))) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject parseDefaultCropMapping(
                                                      String mapping,
                                                      float requiredAmount,
                                                      Path source,
                                                      int index) throws RuleFormatException {
        String path = "$default.cropInputMappings[" + index + "]";
        int equalsIndex = mapping.indexOf('=');
        int atIndex = mapping.indexOf('@', equalsIndex + 1);
        if (equalsIndex <= 0 || atIndex <= equalsIndex + 1 || atIndex >= mapping.length() - 1 || mapping.indexOf('=', equalsIndex + 1) >= 0 || mapping.indexOf('@', atIndex + 1) >= 0) {
            throw new RuleFormatException(
                    source,
                    path,
                    "invalid crop mapping syntax",
                    mapping,
                    "use input_item=recorded_item@positive_progress");
        }
        String inputItem = mapping.substring(0, equalsIndex).trim();
        String recordedItem = mapping.substring(equalsIndex + 1, atIndex).trim();
        parseResourceLocation(new JsonPrimitive(inputItem), source, path + ".input_item", "input item");
        parseResourceLocation(new JsonPrimitive(recordedItem), source, path + ".recorded_item", "recorded item");
        float progress;
        try {
            progress = Float.parseFloat(mapping.substring(atIndex + 1).trim());
        } catch (NumberFormatException exception) {
            throw new RuleFormatException(
                    source,
                    path + ".progress_per_item",
                    "progress must be a finite positive number",
                    mapping.substring(atIndex + 1).trim(),
                    "replace it with a finite number greater than zero",
                    exception);
        }
        if (!Float.isFinite(progress) || progress <= 0.0F) {
            throw new RuleFormatException(
                    source,
                    path + ".progress_per_item",
                    "progress must be a finite positive number",
                    Float.toString(progress),
                    "replace it with a finite number greater than zero");
        }
        return carrierJson("crop", "crop", inputItem, recordedItem, progress, requiredAmount);
    }

    private static JsonObject carrierJson(
                                          String slot,
                                          String dataType,
                                          String inputItem,
                                          String recordedItem,
                                          float progressPerItem,
                                          float requiredAmount) {
        JsonObject object = new JsonObject();
        object.addProperty("slot", slot);
        object.addProperty("data_type", dataType);
        object.addProperty("input_item", inputItem);
        object.addProperty("recorded_item", recordedItem);
        object.addProperty("progress_per_item", progressPerItem);
        object.addProperty("required_amount", requiredAmount);
        return object;
    }

    private static JsonObject outputRuleJson(String dataType, String recordedItem, List<JsonObject> outputs) {
        JsonObject object = new JsonObject();
        object.addProperty("data_type", dataType);
        object.addProperty("recorded_item", recordedItem);
        JsonArray array = new JsonArray();
        outputs.forEach(array::add);
        object.add("outputs", array);
        return object;
    }

    private static JsonObject configuredStackJson(String itemId, int count) {
        JsonObject object = new JsonObject();
        object.addProperty("item", itemId);
        object.addProperty("count", count);
        return object;
    }

    private static JsonObject mobExample(String recordedItem, List<JsonObject> outputs) {
        JsonObject object = new JsonObject();
        object.addProperty(
                "_note",
                "Template only; _mob_rule_examples is never executed.\n" + "\u4EC5\u4F5C\u793A\u4F8B\uFF1B_mob_rule_examples " + "\u6C38\u4E0D\u53C2\u4E0E\u6267\u884C\u3002");
        object.addProperty("slot", "mob");
        object.addProperty("final_carrier", "mob");
        object.addProperty("final_carrier_item", "data_energistics:mob_data_carrier");
        object.addProperty("input_item", "data_energistics:data_capture_ball");
        object.addProperty("recorded_item", recordedItem);
        object.addProperty("progress_per_item", 4.0F);
        object.addProperty("required_amount", 4096.0F);
        JsonArray array = new JsonArray();
        outputs.forEach(array::add);
        object.add("mimetic_outputs", array);
        return object;
    }

    private static DataType parseCarrierItemType(String value, Path source, String path)
                                                                                         throws RuleFormatException {
        ResourceLocation item = parseResourceLocation(new JsonPrimitive(value), source, path, "final carrier item");
        if (!item.getNamespace().equals("data_energistics")) {
            throw new RuleFormatException(
                    source,
                    path,
                    "unknown legacy final carrier item",
                    value,
                    "use a Data Energistics mob, ore, or crop data carrier id");
        }
        return switch (item.getPath()) {
            case "data_carrier", "mob_data_carrier" -> DataType.MOB;
            case "ore_data_carrier" -> DataType.ORE;
            case "crop_data_carrier" -> DataType.CROP;
            default -> throw new RuleFormatException(
                    source,
                    path,
                    "unknown legacy final carrier item",
                    value,
                    "use data_energistics:mob_data_carrier, ore_data_carrier, or crop_data_carrier");
        };
    }

    private static Slot parseSlot(String value, Path source, String path) throws RuleFormatException {
        return switch (value) {
            case "ore" -> Slot.ORE;
            case "crop" -> Slot.CROP;
            default -> throw new RuleFormatException(
                    source,
                    path,
                    "unsupported extractor slot",
                    value,
                    "use ore or crop; mob examples belong under _mob_rule_examples");
        };
    }

    private static DataType parseV1DataType(String value, Path source, String path) throws RuleFormatException {
        return switch (value) {
            case "mob" -> DataType.MOB;
            case "ore" -> DataType.ORE;
            case "crop" -> DataType.CROP;
            default -> throw new RuleFormatException(
                    source,
                    path,
                    "unsupported data type",
                    value,
                    "use mob, ore, or crop");
        };
    }

    private static DataType parseLegacyDataType(String value, Path source, String path)
                                                                                        throws RuleFormatException {
        return switch (value) {
            case "mob", "mob_data_carrier", "data_carrier" -> DataType.MOB;
            case "ore", "ore_data_carrier" -> DataType.ORE;
            case "crop", "crop_data_carrier" -> DataType.CROP;
            default -> throw new RuleFormatException(
                    source,
                    path,
                    "unsupported legacy data type",
                    value,
                    "use mob, ore, crop, or the corresponding legacy carrier name");
        };
    }

    private static String serializedName(DataType dataType) {
        return switch (dataType) {
            case MOB -> "mob";
            case ORE -> "ore";
            case CROP -> "crop";
        };
    }

    private static ResourceLocation parseResourceLocation(
                                                          JsonElement element,
                                                          Path source,
                                                          String path,
                                                          String description) throws RuleFormatException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw failure(
                    source,
                    path,
                    description + " id must be a JSON string",
                    element,
                    "use a registry id such as minecraft:stone");
        }
        String value = element.getAsString();
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new RuleFormatException(
                    source,
                    path,
                    description + " is not a valid registry id",
                    value,
                    "use lowercase namespace:path syntax");
        }
        return parsed;
    }

    private static float requirePositiveFloat(JsonObject object, String field, Path source, String parentPath)
                                                                                                               throws RuleFormatException {
        JsonElement element = require(object, field, source, parentPath);
        BigDecimal value = requireNumber(element, source, parentPath + "." + field);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(Float.MAX_VALUE)) > 0) {
            throw failure(
                    source,
                    parentPath + "." + field,
                    "the value must be finite, positive, and representable as a float",
                    element,
                    "use a number greater than zero and no larger than " + Float.MAX_VALUE);
        }
        float narrowed = value.floatValue();
        if (!Float.isFinite(narrowed) || narrowed <= 0.0F) {
            throw failure(
                    source,
                    parentPath + "." + field,
                    "the value underflows or overflows a positive float",
                    element,
                    "use a finite positive float value");
        }
        return narrowed;
    }

    private static int requirePositiveInteger(JsonObject object, String field, Path source, String parentPath)
                                                                                                               throws RuleFormatException {
        JsonElement element = require(object, field, source, parentPath);
        BigDecimal decimal = requireNumber(element, source, parentPath + "." + field);
        BigInteger integer;
        try {
            integer = decimal.toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new RuleFormatException(
                    source,
                    parentPath + "." + field,
                    "the output count must be an integer",
                    element.toString(),
                    "replace it with a positive whole number",
                    exception);
        }
        if (integer.signum() <= 0 || integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw failure(
                    source,
                    parentPath + "." + field,
                    "the output count must be between 1 and " + Integer.MAX_VALUE,
                    element,
                    "replace it with a positive 32-bit integer");
        }
        return integer.intValue();
    }

    private static int requireInteger(JsonObject object, String field, Path source, String parentPath)
                                                                                                       throws RuleFormatException {
        JsonElement element = require(object, field, source, parentPath);
        BigDecimal decimal = requireNumber(element, source, parentPath + "." + field);
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException exception) {
            throw new RuleFormatException(
                    source,
                    parentPath + "." + field,
                    "the schema version must be a 32-bit integer",
                    element.toString(),
                    "use schema_version: 1",
                    exception);
        }
    }

    private static BigDecimal requireNumber(JsonElement element, Path source, String path)
                                                                                           throws RuleFormatException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw failure(source, path, "the value must be a JSON number", element, "replace it with an unquoted number");
        }
        try {
            return new BigDecimal(element.getAsString());
        } catch (NumberFormatException exception) {
            throw new RuleFormatException(
                    source,
                    path,
                    "the value is not a finite JSON number",
                    element.toString(),
                    "replace NaN or infinity with a finite number",
                    exception);
        }
    }

    private static String requireString(JsonObject object, String field, Path source, String parentPath)
                                                                                                         throws RuleFormatException {
        JsonElement element = require(object, field, source, parentPath);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw failure(
                    source,
                    parentPath + "." + field,
                    "the value must be a JSON string",
                    element,
                    "quote the textual value");
        }
        String value = element.getAsString();
        if (value.isEmpty()) {
            throw new RuleFormatException(
                    source,
                    parentPath + "." + field,
                    "the string must not be empty",
                    "\"\"",
                    "provide the required value");
        }
        return value;
    }

    private static JsonElement require(JsonObject object, String field, Path source, String parentPath)
                                                                                                        throws RuleFormatException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new RuleFormatException(
                    source,
                    parentPath + "." + field,
                    "required field is missing or null",
                    value == null ? "<missing>" : "null",
                    "add a non-null " + field + " value");
        }
        return value;
    }

    private static JsonArray requireArray(JsonObject object, String field, Path source, String parentPath)
                                                                                                           throws RuleFormatException {
        JsonElement element = require(object, field, source, parentPath);
        if (!element.isJsonArray()) {
            throw failure(
                    source,
                    parentPath + "." + field,
                    "the value must be an array",
                    element,
                    "replace it with a JSON array");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonElement element, Path source, String path)
                                                                                           throws RuleFormatException {
        if (!element.isJsonObject()) {
            throw failure(source, path, "the array entry must be an object", element, "replace it with a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static void validateFields(
                                       JsonObject object,
                                       Set<String> permitted,
                                       Path source,
                                       String path,
                                       String description) throws RuleFormatException {
        for (String field : object.keySet()) {
            if (!field.startsWith("_") && !permitted.contains(field)) {
                throw new RuleFormatException(
                        source,
                        path + "." + field,
                        "unknown field in " + description,
                        object.get(field).toString(),
                        "remove the field or prefix intentional metadata with an underscore");
            }
        }
    }

    private static void copyMetadata(JsonObject source, JsonObject target) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    private static JsonElement parseStrictJson(byte[] content, Path source) throws RuleFormatException {
        if (hasUtf8Bom(content)) {
            throw new RuleFormatException(
                    source,
                    "$",
                    "UTF-8 BOM is not permitted",
                    "EF BB BF",
                    "save the file as UTF-8 without BOM");
        }
        try (Reader decoded = new InputStreamReader(
                new ByteArrayInputStream(content),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT));
                JsonReader reader = new JsonReader(decoded)) {
            reader.setLenient(false);
            JsonElement value = readElement(reader, source, "$", 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new RuleFormatException(
                        source,
                        "$",
                        "trailing content follows the JSON document",
                        reader.peek().name(),
                        "remove all content after the root value");
            }
            return value;
        } catch (RuleFormatException exception) {
            throw exception;
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new RuleFormatException(
                    source,
                    "$",
                    "malformed JSON or invalid UTF-8",
                    exception.getMessage(),
                    "fix the JSON syntax and save it as UTF-8 without BOM",
                    exception);
        }
    }

    private static JsonElement readElement(JsonReader reader, Path source, String path, int depth)
                                                                                                   throws IOException, RuleFormatException {
        if (depth > MAX_JSON_DEPTH) {
            throw new RuleFormatException(
                    source,
                    path,
                    "JSON nesting exceeds " + MAX_JSON_DEPTH + " levels",
                    Integer.toString(depth),
                    "flatten deeply nested metadata");
        }
        JsonToken token = reader.peek();
        return switch (token) {
            case BEGIN_OBJECT -> readObject(reader, source, path, depth);
            case BEGIN_ARRAY -> readArray(reader, source, path, depth);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new RuleFormatException(
                    source,
                    path,
                    "a JSON value was expected",
                    token.name(),
                    "add a valid JSON value");
        };
    }

    private static JsonObject readObject(JsonReader reader, Path source, String path, int depth)
                                                                                                 throws IOException, RuleFormatException {
        reader.beginObject();
        JsonObject object = new JsonObject();
        Set<String> fields = new HashSet<>();
        while (reader.hasNext()) {
            String field = reader.nextName();
            String fieldPath = path + "." + field;
            if (!fields.add(field)) {
                throw new RuleFormatException(
                        source,
                        fieldPath,
                        "duplicate object key",
                        field,
                        "remove the duplicate key and keep exactly one value");
            }
            object.add(field, readElement(reader, source, fieldPath, depth + 1));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader, Path source, String path, int depth)
                                                                                               throws IOException, RuleFormatException {
        reader.beginArray();
        JsonArray array = new JsonArray();
        int index = 0;
        while (reader.hasNext()) {
            array.add(readElement(reader, source, path + "[" + index + "]", depth + 1));
            index++;
        }
        reader.endArray();
        return array;
    }

    private static boolean hasUtf8Bom(byte[] content) {
        return content.length >= 3 && content[0] == UTF8_BOM_FIRST && content[1] == UTF8_BOM_SECOND && content[2] == UTF8_BOM_THIRD;
    }

    private static RuleFormatException failure(
                                               Path source,
                                               String path,
                                               String violation,
                                               JsonElement actual,
                                               String repair) {
        return new RuleFormatException(
                source,
                path,
                violation,
                actual == null ? "<missing>" : actual.toString(),
                repair);
    }

    record DecodedDocument(int sourceVersion, JsonObject v1Document, LoadedRules loadedRules) {}

    private record CarrierKey(Slot slot, ResourceLocation inputItem) {}

    private record OutputKey(DataType dataType, ResourceLocation recordedItem) {}

    private record IndexedOutput(int index, OutputRule rule) {}

    private record IndexedStack(int index, ConfiguredStack stack) {}

    private record MigratedCarrier(JsonObject carrier, Optional<JsonObject> outputRule) {}
}
