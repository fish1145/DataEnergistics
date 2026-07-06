package com.fish_dan_.data_energistics.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataCircuitBoardRecipeTest {

    @Test
    void dataCircuitBoardUsesNormalPrintedProcessorRecipeShape() {
        JsonObject root = readJson("data/data_energistics/recipe/ae2/inscriber/data_circuit_board.json");

        assertEquals("ae2:inscriber", string(root, "type"));
        assertEquals("inscribe", string(root, "mode"));

        JsonObject ingredients = object(root, "ingredients");
        assertEquals(
                "data_energistics:data_inscriber_template",
                string(object(ingredients, "top"), "item"),
                "Data circuit boards should use the data inscriber template as the top press");
        assertEquals(
                "c:gems/data_crystal",
                string(object(ingredients, "middle"), "tag"),
                "Data circuit boards should be pressed from data crystals");
        assertFalse(ingredients.has("bottom"), "Printed circuit board recipes should not use a bottom input");

        JsonObject result = object(root, "result");
        assertEquals("data_energistics:data_circuit_board", string(result, "id"));
        assertEquals(1, result.get("count").getAsInt());
    }

    @Test
    void ae2MysteriousCubeDropsDataInscriberTemplate() {
        JsonObject root = readJson("data/ae2/loot_table/blocks/mysterious_cube.json");
        Set<String> itemNames = new HashSet<>();
        collectItemNames(root, itemNames);

        assertTrue(
                itemNames.contains("data_energistics:data_inscriber_template"),
                "Digitalized meteorite mysterious cubes should include the data inscriber template");
        assertTrue(
                itemNames.contains("ae2:calculation_processor_press"),
                "The overridden mysterious cube loot should preserve AE2 processor presses");
        assertTrue(
                itemNames.contains("ae2:engineering_processor_press"),
                "The overridden mysterious cube loot should preserve AE2 processor presses");
        assertTrue(
                itemNames.contains("ae2:logic_processor_press"),
                "The overridden mysterious cube loot should preserve AE2 processor presses");
        assertTrue(
                itemNames.contains("ae2:silicon_press"),
                "The overridden mysterious cube loot should preserve AE2 processor presses");
        assertTrue(
                itemNames.contains("ae2:guide"),
                "The overridden mysterious cube loot should preserve the guide drop");
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = DataCircuitBoardRecipeTest.class.getClassLoader().getResourceAsStream(path)) {
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

    private static void collectItemNames(JsonElement element, Set<String> itemNames) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement type = object.get("type");
            JsonElement name = object.get("name");
            if (type != null && name != null && "minecraft:item".equals(type.getAsString())) {
                itemNames.add(name.getAsString());
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectItemNames(entry.getValue(), itemNames);
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                collectItemNames(child, itemNames);
            }
        }
    }
}
