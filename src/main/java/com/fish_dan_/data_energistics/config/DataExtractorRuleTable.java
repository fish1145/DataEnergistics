package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleMigrator;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DataExtractorRuleTable {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private static volatile LoadedRules loadedRules = LoadedRules.empty();

    private DataExtractorRuleTable() {}

    public static void load() {
        Path filePath = FMLPaths.CONFIGDIR.get().resolve("data_energistics-data_extractor_rules.json");
        try {
            load(filePath);
        } catch (IOException exception) {
            LOGGER.error(
                    "Failed to load Data Extractor rule table from {}; the previous complete snapshot remains active",
                    filePath,
                    exception);
        }
    }

    /** Loads and atomically publishes one complete snapshot from an explicit path. */
    public static void load(Path path) throws IOException {
        load(
                path,
                new DefaultRuleValues(
                        DataExtractorConfig.cropInputMappings,
                        DataExtractorConfig.cropRequiredAmount,
                        DataExtractorConfig.oreRequiredAmount));
    }

    /** Loads with defaults from the already published main configuration snapshot. */
    public static void load(Path path, DefaultRuleValues defaults) throws IOException {
        LoadedRules candidate = DataExtractorRuleMigrator.load(
                path,
                defaults);
        loadedRules = candidate;
    }

    /** Returns the currently published immutable rule snapshot. */
    public static LoadedRules snapshot() {
        return loadedRules;
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
        for (ItemRule rule : loadedRules.inputRules()) {
            if (rule.slot() == slot && rule.inputItemId().equals(itemId)) {
                return rule;
            }
        }
        return null;
    }

    public static List<ItemStack> getConfiguredOutputs(DataType dataType, ResourceLocation recordedId) {
        for (OutputRule rule : loadedRules.outputRules()) {
            if (rule.dataType() == dataType && rule.recordedId().equals(recordedId)) {
                return rule.createStacks();
            }
        }
        return List.of();
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
