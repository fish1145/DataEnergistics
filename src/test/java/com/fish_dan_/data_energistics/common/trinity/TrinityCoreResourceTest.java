package com.fish_dan_.data_energistics.common.trinity;

import com.google.gson.JsonArray;
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
    private static final String ACCESS_HATCH_MODEL = "data_energistics:block/trinity_core/access_hatch/trinity_access_hatch";
    private static final String TIERED_CORE_MODEL = "data_energistics:block/trinity_core/common/tiered_core";
    private static final String STORAGE_DIRECTORY = "storage";
    private static final String MERGED_STORAGE_DIRECTORY = "merged_storage";
    private static final String PATTERN_PROCESSING_DIRECTORY = "pattern_processing";
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
    void accessHatchUsesItsDedicatedModelDirectoryAndLocalTextures() {
        JsonObject blockstate = readJson(BLOCKSTATE_ROOT + "trinity_access_hatch.json");
        JsonObject variants = object(blockstate, "variants");
        assertEquals(8, variants.size(), "The access hatch should define every facing and active state");
        for (Map.Entry<String, Integer> facing : Map.of(
                "north", 0,
                "east", 90,
                "south", 180,
                "west", 270).entrySet()) {
            for (boolean active : List.of(false, true)) {
                JsonObject variant = object(variants, "facing=" + facing.getKey() + ",active=" + active);
                assertEquals(ACCESS_HATCH_MODEL, string(variant, "model"));
                if (facing.getValue() == 0) {
                    assertFalse(variant.has("y"), "North-facing access hatch variants should not rotate the model");
                } else {
                    assertEquals(facing.getValue(), variant.get("y").getAsInt());
                }
            }
        }

        JsonObject root = readJson(BLOCK_MODEL_ROOT + "access_hatch/trinity_access_hatch.json");
        assertEquals("cutout", string(root, "render_type"));
        assertModelHasNoTemporaryReferences(root, "trinity_access_hatch");

        JsonObject textures = object(root, "textures");
        assertLocalTextureReference(string(textures, "1"), "trinity_access_hatch core texture");
        assertLocalTextureReference(string(textures, "4"), "trinity_access_hatch front texture");
        assertLocalTextureReference(string(textures, "6"), "trinity_access_hatch top texture");

        JsonObject item = readJson(ITEM_MODEL_ROOT + "trinity_access_hatch.json");
        assertEquals(ACCESS_HATCH_MODEL, string(item, "parent"));
    }

    @Test
    void tieredCoreModelUsesTwoCutoutLayersAcrossAllFaces() {
        JsonObject root = readJson(BLOCK_MODEL_ROOT + "common/tiered_core.json");
        assertEquals("cutout", string(root, "render_type"));
        JsonArray elements = array(root, "elements");
        assertEquals(2, elements.size(), "The tiered core should contain a base and overlay layer");
        assertCubeLayer(elements.get(0).getAsJsonObject(), "#0", "base");
        assertCubeLayer(elements.get(1).getAsJsonObject(), "#1", "overlay");
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
        assertEquals(core.modelId(), string(variant, "model"));
    }

    private static void assertBlockModel(CoreResource core) {
        JsonObject root = readJson(core.blockModelPath());
        assertEquals(TIERED_CORE_MODEL, string(root, "parent"));
        assertEquals("cutout", string(root, "render_type"));
        assertModelHasNoTemporaryReferences(root, core.id());
        JsonObject textures = object(root, "textures");
        assertEquals(core.baseTexture(), string(textures, "0"));
        assertEquals(core.tierTexture(), string(textures, "1"));
        assertEquals(core.baseTexture(), string(textures, "particle"));
        assertLocalTextureReference(core.baseTexture(), core.id() + " base texture");
        assertTierTextureReference(core.tierTexture(), core.id() + " tier texture");
    }

    private static void assertItemModel(CoreResource core) {
        JsonObject root = readJson(ITEM_MODEL_ROOT + core.id() + ".json");
        assertEquals(core.modelId(), string(root, "parent"));
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

    private static void assertLocalTextureReference(String textureId, String message) {
        assertTrue(textureId.startsWith("data_energistics:block/trinity_core/"), message);
        assertResourceExists(textureResourcePath(textureId), message + " should exist");
    }

    private static void assertTierTextureReference(String textureId, String message) {
        if (textureId.startsWith("data_energistics:")) {
            assertLocalTextureReference(textureId, message);
            return;
        }
        assertTrue(textureId.startsWith("ae2:block/crafting/"), message + " should be a known AE2 crafting light");
    }

    private static void assertCubeLayer(JsonObject layer, String textureReference, String layerName) {
        assertCoordinates(layer, "from", 0, 0, 0);
        assertCoordinates(layer, "to", 16, 16, 16);
        JsonObject faces = object(layer, "faces");
        assertEquals(6, faces.size(), "The " + layerName + " tiered-core layer should cover every face");
        for (String face : List.of("north", "east", "south", "west", "up", "down")) {
            assertEquals(textureReference, string(object(faces, face), "texture"));
        }
    }

    private static void assertCoordinates(JsonObject root, String property, int x, int y, int z) {
        JsonArray coordinates = array(root, property);
        assertEquals(3, coordinates.size(), property + " should contain three coordinates");
        assertEquals(x, coordinates.get(0).getAsInt());
        assertEquals(y, coordinates.get(1).getAsInt());
        assertEquals(z, coordinates.get(2).getAsInt());
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

    private static JsonArray array(JsonObject root, String property) {
        JsonElement element = root.get(property);
        assertNotNull(element, "Missing JSON array property " + property);
        assertTrue(element.isJsonArray(), property + " should be a JSON array");
        return element.getAsJsonArray();
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
            String storageId = "me_digital_storage_core_" + suffix;
            resources.add(new CoreResource(
                    storageId,
                    STORAGE_DIRECTORY,
                    localTexture(STORAGE_DIRECTORY, "me_digital_storage_core"),
                    localTexture(STORAGE_DIRECTORY, storageId)));

            String mergedStorageId = "me_digital_merged_storage_core_" + suffix;
            resources.add(new CoreResource(
                    mergedStorageId,
                    MERGED_STORAGE_DIRECTORY,
                    localTexture(MERGED_STORAGE_DIRECTORY, "me_digital_merged_storage_core"),
                    mergedTierTexture(suffix)));
        }
        String patternBaseTexture = localTexture(PATTERN_PROCESSING_DIRECTORY, "me_digital_pattern_processing_core");
        resources.add(new CoreResource(
                "me_digital_pattern_processing_core",
                PATTERN_PROCESSING_DIRECTORY,
                patternBaseTexture,
                localTexture(PATTERN_PROCESSING_DIRECTORY, "me_digital_pattern_processing_core_1")));
        resources.add(new CoreResource(
                "extended_me_digital_pattern_processing_core",
                PATTERN_PROCESSING_DIRECTORY,
                patternBaseTexture,
                localTexture(PATTERN_PROCESSING_DIRECTORY, "me_digital_pattern_processing_core_2")));
        resources.add(new CoreResource(
                "overlimit_me_digital_pattern_processing_core",
                PATTERN_PROCESSING_DIRECTORY,
                patternBaseTexture,
                localTexture(PATTERN_PROCESSING_DIRECTORY, "me_digital_pattern_processing_core_3")));
        return resources;
    }

    private static String localTexture(String directory, String textureName) {
        return "data_energistics:block/trinity_core/" + directory + "/" + textureName;
    }

    private static String mergedTierTexture(String suffix) {
        return switch (suffix) {
            case "1m" -> "ae2:block/crafting/1k_storage_light";
            case "4m" -> "ae2:block/crafting/4k_storage_light";
            case "16m" -> "ae2:block/crafting/16k_storage_light";
            case "64m" -> "ae2:block/crafting/64k_storage_light";
            case "256m" -> "ae2:block/crafting/256k_storage_light";
            default -> localTexture(MERGED_STORAGE_DIRECTORY, "me_digital_merged_storage_core_" + suffix);
        };
    }

    private record CoreResource(String id, String directory, String baseTexture, String tierTexture) {

        private String modelId() {
            return "data_energistics:block/trinity_core/" + this.directory + "/" + this.id;
        }

        private String blockModelPath() {
            return BLOCK_MODEL_ROOT + this.directory + "/" + this.id + ".json";
        }
    }
}
