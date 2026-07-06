package com.fish_dan_.data_energistics.common.trinity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCoreResourceTest {

    private static final String BLOCKSTATE_ROOT = "assets/data_energistics/blockstates/";
    private static final String BLOCK_MODEL_ROOT = "assets/data_energistics/models/block/trinity_core/";
    private static final String ITEM_MODEL_ROOT = "assets/data_energistics/models/item/";
    private static final String TEXTURE_ROOT = "assets/data_energistics/textures/";
    private static final String LOOT_TABLE_ROOT = "data/data_energistics/loot_table/blocks/";
    private static final String RECIPE_ROOT = "data/data_energistics/recipe/crafting/trinity_core/";
    private static final String DATA_ROOT = "data/";
    private static final String LANG_ROOT = "assets/data_energistics/lang/";
    private static final String MODEL_PARENT = "data_energistics:block/trinity_core/me_storage_access_hatch";
    private static final List<String> TIER_SUFFIXES = List.of(
            "1m", "4m", "16m", "64m", "256m", "1g", "4g", "16g", "64g", "256g");
    private static final List<CoreResource> CORE_RESOURCES = createCoreResources();

    @Test
    void allTrinityCoreBlocksHaveRequiredResources() {
        JsonObject pickaxeTag = readJson(DATA_ROOT + "minecraft/tags/block/mineable/pickaxe.json");
        JsonObject ironToolTag = readJson(DATA_ROOT + "minecraft/tags/block/needs_iron_tool.json");
        JsonObject enUs = readJson(LANG_ROOT + "en_us.json");
        JsonObject zhCn = readJson(LANG_ROOT + "zh_cn.json");

        assertEquals(23, CORE_RESOURCES.size(), "The trinity core resource set should match the planned block count");
        for (CoreResource core : CORE_RESOURCES) {
            String blockId = "data_energistics:" + core.id();
            assertBlockstate(core);
            assertBlockModel(core);
            assertItemModel(core);
            assertLootTable(core, blockId);
            assertRecipe(core, blockId);
            assertJsonArrayContains(pickaxeTag, blockId, core.id() + " should be mineable with a pickaxe");
            assertJsonArrayContains(ironToolTag, blockId, core.id() + " should require an iron-tier tool");
            assertTrue(enUs.has(langKey(core)), core.id() + " should have an English block name");
            assertTrue(zhCn.has(langKey(core)), core.id() + " should have a Chinese block name");
        }
    }

    @Test
    void baseModelUsesOnlyTrinityCoreTextureReferences() {
        JsonObject root = readJson(BLOCK_MODEL_ROOT + "me_storage_access_hatch.json");
        assertEquals("cutout", string(root, "render_type"));
        assertModelHasNoTemporaryReferences(root, "me_storage_access_hatch");

        JsonObject textures = object(root, "textures");
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            String textureId = entry.getValue().getAsString();
            assertTextureReference(textureId, "me_storage_access_hatch texture " + entry.getKey());
        }
    }

    @Test
    void tierRecipesUseExpectedProgression() {
        for (int index = 0; index < TIER_SUFFIXES.size(); index++) {
            String suffix = TIER_SUFFIXES.get(index);
            String storageId = "me_digital_storage_core_" + suffix;
            JsonObject storageRecipe = readJson(RECIPE_ROOT + storageId + ".json");
            List<String> ingredients = collectIngredientIds(storageRecipe);
            String expectedPrevious = index == 0 ? "data_energistics:data_storage_component_256k" : "data_energistics:me_digital_storage_core_" + TIER_SUFFIXES.get(index - 1);
            String expectedDust = suffix.endsWith("m") ? "c:dusts/fluix" : "c:dusts/data_dust";

            assertTrue(ingredients.contains(expectedPrevious), storageId + " should upgrade from the previous tier");
            assertTrue(ingredients.contains(expectedDust), storageId + " should use the expected M/G dust family");
            assertTrue(ingredients.contains("data_energistics:data_processor"), storageId + " should use the data processor");
            assertTrue(ingredients.contains("data_energistics:data_framework"), storageId + " should use the data framework");

            String mergedId = "me_digital_merged_storage_core_" + suffix;
            JsonObject mergedRecipe = readJson(RECIPE_ROOT + mergedId + ".json");
            List<String> mergedIngredients = collectIngredientIds(mergedRecipe);
            assertTrue(mergedIngredients.contains("data_energistics:" + storageId), mergedId + " should use the same storage tier");
            assertTrue(mergedIngredients.contains("ae2:crafting_accelerator"), mergedId + " should use an AE2 crafting accelerator");
            assertTrue(mergedIngredients.contains("ae2:engineering_processor"), mergedId + " should use an engineering processor");
            assertTrue(mergedIngredients.contains("data_energistics:data_processor"), mergedId + " should use the data processor");
        }
    }

    @Test
    void patternCoreRecipesUsePlannedUpgradeChain() {
        JsonObject ordinary = readJson(RECIPE_ROOT + "me_digital_pattern_processing_core.json");
        List<String> ordinaryIngredients = collectIngredientIds(ordinary);
        assertTrue(ordinaryIngredients.contains("data_energistics:me_pattern_buffer"));
        assertTrue(ordinaryIngredients.contains("ae2:pattern_provider"));
        assertTrue(ordinaryIngredients.contains("data_energistics:data_processor"));
        assertTrue(ordinaryIngredients.contains("data_energistics:data_framework"));

        JsonObject extended = readJson(RECIPE_ROOT + "extended_me_digital_pattern_processing_core.json");
        List<String> extendedIngredients = collectIngredientIds(extended);
        assertTrue(extendedIngredients.contains("data_energistics:me_digital_pattern_processing_core"));
        assertTrue(extendedIngredients.contains("ae2:crafting_accelerator"));

        JsonObject overlimit = readJson(RECIPE_ROOT + "overlimit_me_digital_pattern_processing_core.json");
        List<String> overlimitIngredients = collectIngredientIds(overlimit);
        assertTrue(overlimitIngredients.contains("data_energistics:extended_me_digital_pattern_processing_core"));
        assertTrue(overlimitIngredients.contains("ae2:singularity"));
    }

    private static void assertBlockstate(CoreResource core) {
        JsonObject root = readJson(BLOCKSTATE_ROOT + core.id() + ".json");
        JsonObject variant = object(object(root, "variants"), "");
        assertEquals("data_energistics:block/trinity_core/" + core.id(), string(variant, "model"));
    }

    private static void assertBlockModel(CoreResource core) {
        JsonObject root = readJson(BLOCK_MODEL_ROOT + core.id() + ".json");
        assertEquals(MODEL_PARENT, string(root, "parent"));
        assertModelHasNoTemporaryReferences(root, core.id());
        JsonObject textures = object(root, "textures");
        assertEquals("data_energistics:block/trinity_core/" + core.texture(), string(textures, "1"));
        assertTextureReference(string(textures, "1"), core.id() + " core texture");
        assertTextureReference(string(textures, "4"), core.id() + " front texture");
        assertTextureReference(string(textures, "6"), core.id() + " top texture");
    }

    private static void assertItemModel(CoreResource core) {
        JsonObject root = readJson(ITEM_MODEL_ROOT + core.id() + ".json");
        assertEquals("data_energistics:block/trinity_core/" + core.id(), string(root, "parent"));
    }

    private static void assertLootTable(CoreResource core, String blockId) {
        JsonObject root = readJson(LOOT_TABLE_ROOT + core.id() + ".json");
        JsonObject entry = root.getAsJsonArray("pools")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("entries")
                .get(0)
                .getAsJsonObject();
        assertEquals("minecraft:block", string(root, "type"));
        assertEquals(blockId, string(entry, "name"));
    }

    private static void assertRecipe(CoreResource core, String blockId) {
        JsonObject root = readJson(RECIPE_ROOT + core.id() + ".json");
        assertEquals(blockId, string(object(root, "result"), "id"));
    }

    private static String langKey(CoreResource core) {
        return "block.data_energistics." + core.id();
    }

    private static void assertTextureReference(String textureId, String message) {
        assertTrue(textureId.startsWith("data_energistics:block/trinity_core/"), message);
        assertResourceExists(textureResourcePath(textureId), message + " should exist");
    }

    private static void assertModelHasNoTemporaryReferences(JsonObject root, String model) {
        List<String> values = collectStringValues(root);
        for (String value : values) {
            assertFalse(value.contains("compartment("), model + " should not use the temporary compartment folder");
            assertFalse(value.contains("#missing"), model + " should not keep missing texture placeholders");
            assertNotEquals("front", value, model + " should not use bare front texture names");
            assertNotEquals("top", value, model + " should not use bare top texture names");
            assertNotEquals("me_interface", value, model + " should not use bare interface texture names");
            assertNotEquals("ae2:top", value, model + " should not use AE2 top directly");
        }
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = TrinityCoreResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                assertTrue(element.isJsonObject(), path + " should parse as a JSON object");
                return element.getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read resource " + path, exception);
        }
    }

    private static JsonObject object(JsonObject root, String property) {
        JsonElement element = root.get(property);
        assertNotNull(element, "Missing JSON object property " + property);
        assertTrue(element.isJsonObject(), property + " should be a JSON object");
        return element.getAsJsonObject();
    }

    private static String string(JsonObject root, String property) {
        JsonElement element = root.get(property);
        assertNotNull(element, "Missing JSON string property " + property);
        assertTrue(element.isJsonPrimitive(), property + " should be a JSON primitive");
        return element.getAsString();
    }

    private static void assertJsonArrayContains(JsonObject root, String expected, String message) {
        JsonElement valuesElement = root.get("values");
        assertNotNull(valuesElement, "Missing values array");
        assertTrue(valuesElement.isJsonArray(), "values should be an array");
        for (JsonElement element : valuesElement.getAsJsonArray()) {
            if (element.isJsonPrimitive() && expected.equals(element.getAsString())) {
                return;
            }
        }
        throw new AssertionError(message);
    }

    private static String textureResourcePath(String textureId) {
        int separator = textureId.indexOf(':');
        assertTrue(separator > 0, "Texture id should include a namespace: " + textureId);
        String namespace = textureId.substring(0, separator);
        String path = textureId.substring(separator + 1);
        assertEquals("data_energistics", namespace, "Trinity core texture namespace should be local");
        return TEXTURE_ROOT + path + ".png";
    }

    private static List<String> collectIngredientIds(JsonElement element) {
        List<String> values = new ArrayList<>();
        collectIngredientIds(element, values);
        return values;
    }

    private static void collectIngredientIds(JsonElement element, List<String> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement item = object.get("item");
            if (item != null) {
                values.add(item.getAsString());
            }
            JsonElement tag = object.get("tag");
            if (tag != null) {
                values.add(tag.getAsString());
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectIngredientIds(entry.getValue(), values);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectIngredientIds(child, values);
            }
        }
    }

    private static List<String> collectStringValues(JsonElement element) {
        List<String> values = new ArrayList<>();
        collectStringValues(element, values);
        return values;
    }

    private static void collectStringValues(JsonElement element, List<String> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            values.add(element.getAsString());
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectStringValues(entry.getValue(), values);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStringValues(child, values);
            }
        }
    }

    private static void assertResourceExists(String path, String message) {
        try (InputStream stream = TrinityCoreResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, message + ": " + path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + path, exception);
        }
    }

    private static List<CoreResource> createCoreResources() {
        List<CoreResource> resources = new ArrayList<>();
        for (String suffix : TIER_SUFFIXES) {
            resources.add(new CoreResource("me_digital_storage_core_" + suffix, "storage_core"));
            resources.add(new CoreResource("me_digital_merged_storage_core_" + suffix, "merged_storage_core"));
        }
        resources.add(new CoreResource("me_digital_pattern_processing_core", "pattern_processing_core"));
        resources.add(new CoreResource("extended_me_digital_pattern_processing_core", "pattern_processing_core"));
        resources.add(new CoreResource("overlimit_me_digital_pattern_processing_core", "pattern_processing_core"));
        return resources;
    }

    private record CoreResource(String id, String texture) {}
}
