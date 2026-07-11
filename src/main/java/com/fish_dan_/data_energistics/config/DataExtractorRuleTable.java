package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DataExtractorRuleTable {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Path FILE_PATH = FMLPaths.CONFIGDIR.get().resolve("data_energistics-data_extractor_rules.json");

    private static volatile List<ItemRule> rules = List.of();
    private static volatile List<OutputRule> outputRules = List.of();

    private DataExtractorRuleTable() {}

    public static void load() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            if (Files.notExists(FILE_PATH)) {
                writeDefaultFile();
            }
            LoadedRules loadedRules = readRules();
            rules = loadedRules.inputRules();
            outputRules = loadedRules.outputRules();
        } catch (IOException e) {
            LOGGER.error("Failed to load Data Extractor rule table from {}", FILE_PATH, e);
            rules = List.of();
            outputRules = List.of();
        }
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
        if (itemId == null) {
            return null;
        }

        for (ItemRule rule : rules) {
            if (rule.slot() == slot && rule.inputItemId().equals(itemId)) {
                return rule;
            }
        }
        return null;
    }

    public static List<ItemStack> getConfiguredOutputs(DataType dataType, ResourceLocation recordedId) {
        if (recordedId == null) {
            return List.of();
        }

        for (OutputRule rule : outputRules) {
            if (rule.dataType() == dataType && rule.recordedId().equals(recordedId)) {
                return rule.createStacks();
            }
        }

        return List.of();
    }

    private static LoadedRules readRules() throws IOException {
        try (Reader reader = Files.newBufferedReader(FILE_PATH, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return new LoadedRules(List.of(), List.of());
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonArray carrierRulesArray = root.getAsJsonArray("carrier_rules");
            if (carrierRulesArray != null) {
                return readCarrierRules(carrierRulesArray);
            }

            JsonArray inputArray = root.getAsJsonArray("input_rules");
            if (inputArray == null) {
                inputArray = root.getAsJsonArray("rules");
            }

            List<ItemRule> loadedRules = new ArrayList<>();
            if (inputArray != null) {
                for (JsonElement element : inputArray) {
                    if (!element.isJsonObject()) {
                        continue;
                    }

                    ItemRule rule = parseRule(element.getAsJsonObject());
                    if (rule != null) {
                        loadedRules.add(rule);
                    }
                }
            }

            JsonArray outputArray = root.getAsJsonArray("output_rules");
            List<OutputRule> loadedOutputRules = new ArrayList<>();
            if (outputArray != null) {
                for (JsonElement element : outputArray) {
                    if (!element.isJsonObject()) {
                        continue;
                    }

                    OutputRule rule = parseOutputRule(element.getAsJsonObject());
                    if (rule != null) {
                        loadedOutputRules.add(rule);
                    }
                }
            }

            return new LoadedRules(List.copyOf(loadedRules), List.copyOf(loadedOutputRules));
        }
    }

    private static LoadedRules readCarrierRules(JsonArray carrierRulesArray) {
        List<ItemRule> loadedRules = new ArrayList<>();
        List<OutputRule> loadedOutputRules = new ArrayList<>();

        for (JsonElement element : carrierRulesArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            ItemRule rule = parseCarrierRule(object);
            if (rule == null) {
                continue;
            }

            loadedRules.add(rule);

            JsonArray outputsArray = object.getAsJsonArray("mimetic_outputs");
            if (outputsArray == null) {
                outputsArray = object.getAsJsonArray("outputs");
            }

            if (outputsArray == null || outputsArray.isEmpty()) {
                continue;
            }

            List<ConfiguredStack> outputs = parseConfiguredStacks(outputsArray);
            if (!outputs.isEmpty()) {
                loadedOutputRules.add(new OutputRule(rule.dataType(), rule.recordedItemId(), List.copyOf(outputs)));
            }
        }

        return new LoadedRules(List.copyOf(loadedRules), List.copyOf(loadedOutputRules));
    }

    @Nullable
    private static ItemRule parseRule(JsonObject object) {
        Slot slot = Slot.byName(getAsString(object, "slot"));
        DataType dataType = DataType.byName(getAsString(object, "data_type"));
        ResourceLocation inputItemId = ResourceLocation.tryParse(getAsString(object, "input_item"));
        ResourceLocation recordedItemId = ResourceLocation.tryParse(getAsString(object, "recorded_item"));
        float progressPerItem = getAsFloat(object, "progress_per_item", 0.0F);
        float requiredAmount = getAsFloat(object, "required_amount", 0.0F);

        if (slot == null || dataType == null || inputItemId == null || recordedItemId == null || progressPerItem <= 0.0F || requiredAmount <= 0.0F) {
            return null;
        }

        if (BuiltInRegistries.ITEM.getOptional(inputItemId).isEmpty() || BuiltInRegistries.ITEM.getOptional(recordedItemId).isEmpty()) {
            return null;
        }

        return new ItemRule(slot, dataType, inputItemId, recordedItemId, progressPerItem, requiredAmount);
    }

    @Nullable
    private static ItemRule parseCarrierRule(JsonObject object) {
        Slot slot = Slot.byName(getAsString(object, "slot"));
        String carrierName = getAsString(object, "final_carrier");
        DataType dataType = DataType.byName(carrierName);
        if (dataType == null) {
            dataType = dataTypeFromCarrierItemId(getAsString(object, "final_carrier_item"));
        }
        if (dataType == null) {
            dataType = DataType.byName(getAsString(object, "data_type"));
        }
        ResourceLocation inputItemId = ResourceLocation.tryParse(getAsString(object, "input_item"));
        ResourceLocation recordedItemId = ResourceLocation.tryParse(getAsString(object, "recorded_item"));
        float progressPerItem = getAsFloat(object, "progress_per_item", 0.0F);
        float requiredAmount = getAsFloat(object, "required_amount", 0.0F);

        if (slot == null || dataType == null || inputItemId == null || recordedItemId == null || progressPerItem <= 0.0F || requiredAmount <= 0.0F) {
            return null;
        }

        if (BuiltInRegistries.ITEM.getOptional(inputItemId).isEmpty() || BuiltInRegistries.ITEM.getOptional(recordedItemId).isEmpty()) {
            return null;
        }

        return new ItemRule(slot, dataType, inputItemId, recordedItemId, progressPerItem, requiredAmount);
    }

    @Nullable
    private static OutputRule parseOutputRule(JsonObject object) {
        DataType dataType = DataType.byName(getAsString(object, "data_type"));
        ResourceLocation recordedId = ResourceLocation.tryParse(getAsString(object, "recorded_id"));
        if (dataType == null || recordedId == null) {
            return null;
        }

        JsonArray outputsArray = object.getAsJsonArray("outputs");
        if (outputsArray == null || outputsArray.isEmpty()) {
            return null;
        }

        List<ConfiguredStack> outputs = parseConfiguredStacks(outputsArray);

        return outputs.isEmpty() ? null : new OutputRule(dataType, recordedId, List.copyOf(outputs));
    }

    private static void writeDefaultFile() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "carrier_rules controls extractor input mapping, final carrier item/type, required amount, and optional mimetic outputs.");
        JsonArray carrierRulesArray = new JsonArray();

        for (String token : DataExtractorConfig.cropInputMappings.split(",")) {
            ItemRule rule = parseLegacyCropRule(token.trim());
            if (rule == null) {
                continue;
            }

            JsonObject object = new JsonObject();
            object.addProperty("slot", rule.slot().serializedName);
            object.addProperty("final_carrier", rule.dataType().serializedName);
            object.addProperty("final_carrier_item", getCarrierItemId(rule.dataType()).toString());
            object.addProperty("input_item", rule.inputItemId().toString());
            object.addProperty("recorded_item", rule.recordedItemId().toString());
            object.addProperty("progress_per_item", rule.progressPerItem());
            object.addProperty("required_amount", rule.requiredAmount());
            carrierRulesArray.add(object);
        }

        JsonObject oakSaplingExample = new JsonObject();
        oakSaplingExample.addProperty("slot", "crop");
        oakSaplingExample.addProperty("final_carrier", "crop");
        oakSaplingExample.addProperty("final_carrier_item", "data_energistics:crop_data_carrier");
        oakSaplingExample.addProperty("input_item", "minecraft:oak_sapling");
        oakSaplingExample.addProperty("recorded_item", "minecraft:oak_sapling");
        oakSaplingExample.addProperty("progress_per_item", 1.0F);
        oakSaplingExample.addProperty("required_amount", Math.max(1.0F, DataExtractorConfig.cropRequiredAmount));
        JsonArray oakOutputs = new JsonArray();
        oakOutputs.add(configuredStackJson("minecraft:oak_log", 4));
        oakOutputs.add(configuredStackJson("minecraft:oak_leaves", 2));
        oakOutputs.add(configuredStackJson("minecraft:stick", 2));
        oakOutputs.add(configuredStackJson("minecraft:apple", 1));
        oakSaplingExample.add("mimetic_outputs", oakOutputs);
        carrierRulesArray.add(oakSaplingExample);

        JsonObject rawGoldExample = new JsonObject();
        rawGoldExample.addProperty("slot", "ore");
        rawGoldExample.addProperty("final_carrier", "ore");
        rawGoldExample.addProperty("final_carrier_item", "data_energistics:ore_data_carrier");
        rawGoldExample.addProperty("input_item", "minecraft:raw_gold");
        rawGoldExample.addProperty("recorded_item", "minecraft:gold_ore");
        rawGoldExample.addProperty("progress_per_item", 1.0F);
        rawGoldExample.addProperty("required_amount", Math.max(1.0F, DataExtractorConfig.oreRequiredAmount));
        JsonArray rawGoldOutputs = new JsonArray();
        rawGoldOutputs.add(configuredStackJson("minecraft:gold_ore", 1));
        rawGoldExample.add("mimetic_outputs", rawGoldOutputs);
        carrierRulesArray.add(rawGoldExample);

        root.add("carrier_rules", carrierRulesArray);
        JsonArray mobExamplesArray = new JsonArray();

        JsonObject zombieExample = new JsonObject();
        zombieExample.addProperty("_note", "template only: mob rules are not wired into Data Extractor carrier_rules yet.");
        zombieExample.addProperty("slot", "mob");
        zombieExample.addProperty("final_carrier", "mob");
        zombieExample.addProperty("final_carrier_item", "data_energistics:mob_data_carrier");
        zombieExample.addProperty("input_item", "data_energistics:data_capture_ball");
        zombieExample.addProperty("recorded_item", "minecraft:zombie");
        zombieExample.addProperty("progress_per_item", 4.0F);
        zombieExample.addProperty("required_amount", 4096.0F);
        JsonArray zombieOutputs = new JsonArray();
        zombieOutputs.add(configuredStackJson("minecraft:rotten_flesh", 2));
        zombieOutputs.add(configuredStackJson("minecraft:iron_ingot", 1));
        zombieExample.add("mimetic_outputs", zombieOutputs);
        mobExamplesArray.add(zombieExample);

        JsonObject skeletonExample = new JsonObject();
        skeletonExample.addProperty("_note", "template only: mob rules are not wired into Data Extractor carrier_rules yet.");
        skeletonExample.addProperty("slot", "mob");
        skeletonExample.addProperty("final_carrier", "mob");
        skeletonExample.addProperty("final_carrier_item", "data_energistics:mob_data_carrier");
        skeletonExample.addProperty("input_item", "data_energistics:data_capture_ball");
        skeletonExample.addProperty("recorded_item", "minecraft:skeleton");
        skeletonExample.addProperty("progress_per_item", 4.0F);
        skeletonExample.addProperty("required_amount", 4096.0F);
        JsonArray skeletonOutputs = new JsonArray();
        skeletonOutputs.add(configuredStackJson("minecraft:bone", 2));
        skeletonOutputs.add(configuredStackJson("minecraft:arrow", 2));
        skeletonExample.add("mimetic_outputs", skeletonOutputs);
        mobExamplesArray.add(skeletonExample);

        root.add("_mob_rule_examples", mobExamplesArray);
        try (Writer writer = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    @Nullable
    private static ItemRule parseLegacyCropRule(String raw) {
        if (raw.isEmpty()) {
            return null;
        }

        int equalsIndex = raw.indexOf('=');
        int atIndex = raw.indexOf('@', equalsIndex + 1);
        if (equalsIndex <= 0 || atIndex <= equalsIndex + 1 || atIndex >= raw.length() - 1) {
            return null;
        }

        ResourceLocation inputItemId = ResourceLocation.tryParse(raw.substring(0, equalsIndex).trim());
        ResourceLocation recordedItemId = ResourceLocation.tryParse(raw.substring(equalsIndex + 1, atIndex).trim());
        if (inputItemId == null || recordedItemId == null) {
            return null;
        }

        try {
            float progressPerItem = Float.parseFloat(raw.substring(atIndex + 1).trim());
            if (progressPerItem <= 0.0F) {
                return null;
            }
            return new ItemRule(
                    Slot.CROP,
                    DataType.CROP,
                    inputItemId,
                    recordedItemId,
                    progressPerItem,
                    Math.max(1.0F, DataExtractorConfig.cropRequiredAmount));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String getAsString(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static float getAsFloat(JsonObject object, String member, float fallback) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? fallback : value.getAsFloat();
    }

    private static List<ConfiguredStack> parseConfiguredStacks(JsonArray outputsArray) {
        List<ConfiguredStack> outputs = new ArrayList<>();
        for (JsonElement element : outputsArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject output = element.getAsJsonObject();
            ResourceLocation itemId = ResourceLocation.tryParse(getAsString(output, "item"));
            int count = Math.max(1, Math.round(getAsFloat(output, "count", 1.0F)));
            if (itemId == null || BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
                continue;
            }
            outputs.add(new ConfiguredStack(itemId, count));
        }
        return outputs;
    }

    private static JsonObject configuredStackJson(String itemId, int count) {
        JsonObject object = new JsonObject();
        object.addProperty("item", itemId);
        object.addProperty("count", count);
        return object;
    }

    @Nullable
    private static DataType dataTypeFromCarrierItemId(String carrierItemId) {
        ResourceLocation itemId = ResourceLocation.tryParse(carrierItemId);
        if (itemId == null) {
            return null;
        }

        if (itemId.equals(BuiltInRegistries.ITEM.getKey(ModItems.MOB_DATA_CARRIER.get())) || itemId.equals(BuiltInRegistries.ITEM.getKey(ModItems.DATA_CARRIER.get()))) {
            return DataType.MOB;
        }
        if (itemId.equals(BuiltInRegistries.ITEM.getKey(ModItems.ORE_DATA_CARRIER.get()))) {
            return DataType.ORE;
        }
        if (itemId.equals(BuiltInRegistries.ITEM.getKey(ModItems.CROP_DATA_CARRIER.get()))) {
            return DataType.CROP;
        }
        return null;
    }

    private static ResourceLocation getCarrierItemId(DataType dataType) {
        return switch (dataType) {
            case MOB -> BuiltInRegistries.ITEM.getKey(ModItems.MOB_DATA_CARRIER.get());
            case ORE -> BuiltInRegistries.ITEM.getKey(ModItems.ORE_DATA_CARRIER.get());
            case CROP -> BuiltInRegistries.ITEM.getKey(ModItems.CROP_DATA_CARRIER.get());
        };
    }

    public enum Slot {

        ORE("ore"),
        CROP("crop");

        private final String serializedName;

        Slot(String serializedName) {
            this.serializedName = serializedName;
        }

        @Nullable
        public static Slot byName(String name) {
            for (Slot value : values()) {
                if (value.serializedName.equals(name)) {
                    return value;
                }
            }
            return null;
        }
    }

    public enum DataType {

        MOB("mob"),
        ORE("ore"),
        CROP("crop");

        private final String serializedName;

        DataType(String serializedName) {
            this.serializedName = serializedName;
        }

        @Nullable
        public static DataType byName(String name) {
            for (DataType value : values()) {
                if (value.serializedName.equals(name)) {
                    return value;
                }
            }
            return switch (name) {
                case "mob_data_carrier" -> MOB;
                case "ore_data_carrier" -> ORE;
                case "crop_data_carrier" -> CROP;
                default -> null;
            };
        }
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

    private record LoadedRules(List<ItemRule> inputRules, List<OutputRule> outputRules) {}
}
