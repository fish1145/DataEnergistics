package com.fish_dan_.data_energistics.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CompartmentResourceTest {

    private static final String MODEL_ROOT = "assets/data_energistics/models/block/compartment/";
    private static final String TEXTURE_ROOT = "assets/data_energistics/textures/";
    private static final String SCREEN_ROOT = "assets/ae2/screens/";
    private static final String GUI_ROOT = "assets/ae2/textures/guis/";
    private static final String DATA_ROOT = "data/";
    private static final int SLOT_ANCHOR_COLOR = 0xFF9A9FB4;
    private static final List<String> COMPARTMENT_BLOCK_IDS = List.of(
            "composite_input_warehouse",
            "composite_output_warehouse",
            "me_composite_input_warehouse",
            "me_composite_output_warehouse",
            "me_pattern_buffer");
    private static final List<String> COMPARTMENT_MODELS = List.of(
            "composite_input_warehouse_off.json",
            "composite_input_warehouse_on.json",
            "composite_output_warehouse_off.json",
            "composite_output_warehouse_on.json",
            "me_composite_input_warehouse_off.json",
            "me_composite_input_warehouse_on.json",
            "me_composite_output_warehouse_off.json",
            "me_composite_output_warehouse_on.json",
            "me_pattern_buffer.json");
    private static final List<String> COMPARTMENT_SCREENS = List.of(
            "composite_warehouse.json",
            "me_composite_input_warehouse.json",
            "me_composite_output_warehouse.json",
            "me_pattern_buffer.json");

    @Test
    void compartmentBlockModelsUseLocalTextures() {
        for (String model : COMPARTMENT_MODELS) {
            JsonObject root = readJson(MODEL_ROOT + model);
            assertEquals("cutout", string(root, "render_type"), model + " should use cutout rendering");
            JsonObject textures = object(root, "textures");
            assertTrue(
                    textures.asMap().containsValue(jsonString("data_energistics:block/compartment/top")),
                    model + " should reference the local compartment top texture");

            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                String textureId = entry.getValue().getAsString();
                if ("particle".equals(entry.getKey()) || textureId.startsWith("#")) {
                    continue;
                }
                assertFalse(textureId.contains("#missing"), model + " should not reference missing textures");
                assertTrue(
                        textureId.startsWith("data_energistics:block/compartment/"),
                        model + " texture " + entry.getKey() + " should use the compartment texture namespace");
                assertResourceExists(textureResourcePath(textureId), model + " texture " + textureId + " should exist");
            }
        }
    }

    @Test
    void compartmentScreensParseAndUseExpectedWidgets() {
        for (String screen : COMPARTMENT_SCREENS) {
            JsonObject root = readJson(SCREEN_ROOT + screen);
            assertNotNull(object(root, "background"), screen + " should define a background");
            assertNotNull(object(root, "slots"), screen + " should define slots");
            JsonObject text = object(root, "text");
            assertFalse(text.has("binding_status"), screen + " should not show binding status");
            assertTrue(text.has("player_inventory_title"), screen + " should label the player inventory");
        }

        JsonObject compositeWidgets = object(readJson(SCREEN_ROOT + "composite_warehouse.json"), "widgets");
        assertTrue(compositeWidgets.has("upgrades"), "Plain composite warehouses should keep the capacity upgrade panel");

        for (String screen : List.of(
                "me_composite_input_warehouse.json",
                "me_composite_output_warehouse.json",
                "me_pattern_buffer.json")) {
            JsonObject root = readJson(SCREEN_ROOT + screen);
            JsonObject widgets = root.has("widgets") ? object(root, "widgets") : new JsonObject();
            assertFalse(widgets.has("upgrades"), screen + " should not expose upgrade widgets");
        }
    }

    @Test
    void compositeWarehouseScreenCoordinatesMatchTextureAnchors() {
        JsonObject root = readJson(SCREEN_ROOT + "composite_warehouse.json");
        JsonObject slots = object(root, "slots");
        JsonObject text = object(root, "text");
        BufferedImage image = readImage(GUI_ROOT + "composite_warehouse.png");
        int screenHeight = screenHeight(root);

        assertSlotAnchor(slots, image, "COMPARTMENT_STORAGE_ROW_1", 8, 29);
        assertSlotAnchor(slots, image, "COMPARTMENT_STORAGE_ROW_2", 8, 47);
        assertSlotAnchor(slots, image, "COMPARTMENT_STORAGE_ROW_3", 8, 65);
        assertSlotAnchor(slots, image, "COMPARTMENT_STORAGE_ROW_4", 8, 83);
        assertSlotAnchor(slots, image, "COMPARTMENT_STORAGE_ROW_5", 8, 101);
        assertSlotAnchor(slots, image, "COMPARTMENT_FLUID", 152, 29);
        assertSlotAnchor(slots, image, "COMPARTMENT_KEY", 152, 47);
        assertNotSlotAnchor(image, 152, 65, "Plain warehouse right column should not be a main item slot");
        assertBottomSlotAnchor(slots, image, screenHeight, "PLAYER_INVENTORY", 8, 84);
        assertBottomSlotAnchor(slots, image, screenHeight, "PLAYER_HOTBAR", 8, 26);
        assertTextBottom(text, "player_inventory_title", 94);
    }

    @Test
    void mePatternBufferScreenCoordinatesMatchTextureAnchors() {
        JsonObject root = readJson(SCREEN_ROOT + "me_pattern_buffer.json");
        JsonObject slots = object(root, "slots");
        JsonObject text = object(root, "text");
        BufferedImage image = readImage(GUI_ROOT + "me_pattern_buffer.png");
        int screenHeight = screenHeight(root);

        assertSlotAnchor(slots, image, "COMPARTMENT_PATTERN", 8, 16);
        assertSlotAnchor(slots, image, "COMPARTMENT_PATTERN_BUFFER", 177, 15);
        assertSlotAnchor(slots, image, "COMPARTMENT_CATALYST", 177, 160);
        assertSlotAnchor(slots, image, "COMPARTMENT_FLUID", 231, 160);
        assertSlotAnchor(slots, image, "COMPARTMENT_KEY", 231, 178);
        assertSlotAnchor(slots, image, "COMPARTMENT_EXTRA_FLUID", 231, 196);
        assertBottomSlotAnchor(slots, image, screenHeight, "PLAYER_INVENTORY", 8, 87);
        assertBottomSlotAnchor(slots, image, screenHeight, "PLAYER_HOTBAR", 8, 29);
        assertTextBottom(text, "player_inventory_title", 97);
    }

    @Test
    void compartmentBlocksHaveLootTablesAndToolTags() {
        JsonObject pickaxeTag = readJson(DATA_ROOT + "minecraft/tags/block/mineable/pickaxe.json");
        JsonObject ironToolTag = readJson(DATA_ROOT + "minecraft/tags/block/needs_iron_tool.json");

        for (String id : COMPARTMENT_BLOCK_IDS) {
            String blockId = "data_energistics:" + id;
            JsonObject lootTable = readJson(DATA_ROOT + "data_energistics/loot_table/blocks/" + id + ".json");
            JsonObject pool = lootTable.getAsJsonArray("pools").get(0).getAsJsonObject();
            JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();

            assertEquals("minecraft:block", string(lootTable, "type"), id + " should use a block loot table");
            assertEquals(blockId, string(entry, "name"), id + " loot table should drop itself");
            assertJsonArrayContains(pickaxeTag, blockId, id + " should be mineable with a pickaxe");
            assertJsonArrayContains(ironToolTag, blockId, id + " should require the same tier as iron block properties");
        }
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = CompartmentResourceTest.class.getClassLoader().getResourceAsStream(path)) {
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

    private static BufferedImage readImage(String path) {
        try (InputStream stream = CompartmentResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource " + path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "Could not parse image " + path);
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read image " + path, exception);
        }
    }

    private static JsonObject object(JsonObject root, String property) {
        JsonElement element = root.get(property);
        assertNotNull(element, "Missing JSON object property " + property);
        assertTrue(element.isJsonObject(), property + " should be a JSON object");
        return element.getAsJsonObject();
    }

    private static int integer(JsonObject root, String property) {
        JsonElement element = root.get(property);
        assertNotNull(element, "Missing JSON integer property " + property);
        assertTrue(element.isJsonPrimitive(), property + " should be a JSON primitive");
        return element.getAsInt();
    }

    private static String string(JsonObject root, String property) {
        JsonElement element = root.get(property);
        assertNotNull(element, "Missing JSON string property " + property);
        assertTrue(element.isJsonPrimitive(), property + " should be a JSON primitive");
        return element.getAsString();
    }

    private static int screenHeight(JsonObject root) {
        JsonElement sourceRectElement = object(root, "background").get("srcRect");
        assertNotNull(sourceRectElement, "Screen background should define srcRect");
        assertTrue(sourceRectElement.isJsonArray(), "Screen background srcRect should be an array");
        assertEquals(4, sourceRectElement.getAsJsonArray().size(), "Screen background srcRect should have four entries");
        return sourceRectElement.getAsJsonArray().get(3).getAsInt();
    }

    private static void assertSlotAnchor(JsonObject slots,
                                         BufferedImage image,
                                         String semantic,
                                         int expectedLeft,
                                         int expectedTop) {
        JsonObject slot = object(slots, semantic);
        assertEquals(expectedLeft, integer(slot, "left"), semantic + " left coordinate should match the texture");
        assertEquals(expectedTop, integer(slot, "top"), semantic + " top coordinate should match the texture");
        assertEquals(
                SLOT_ANCHOR_COLOR,
                image.getRGB(expectedLeft, expectedTop),
                semantic + " should point at the slot anchor pixel");
    }

    private static void assertBottomSlotAnchor(JsonObject slots,
                                               BufferedImage image,
                                               int screenHeight,
                                               String semantic,
                                               int expectedLeft,
                                               int expectedBottom) {
        JsonObject slot = object(slots, semantic);
        assertEquals(expectedLeft, integer(slot, "left"), semantic + " left coordinate should match the texture");
        assertEquals(expectedBottom, integer(slot, "bottom"), semantic + " bottom coordinate should match the texture");
        assertEquals(
                SLOT_ANCHOR_COLOR,
                image.getRGB(expectedLeft, screenHeight - expectedBottom),
                semantic + " should point at the slot anchor pixel");
    }

    private static void assertTextBottom(JsonObject text, String id, int expectedBottom) {
        JsonObject position = object(object(text, id), "position");
        assertEquals(expectedBottom, integer(position, "bottom"), id + " bottom coordinate should match the slots");
    }

    private static void assertNotSlotAnchor(BufferedImage image, int x, int y, String message) {
        assertNotEquals(SLOT_ANCHOR_COLOR, image.getRGB(x, y), message);
    }

    private static void assertJsonArrayContains(JsonObject root, String expected, String message) {
        JsonElement valuesElement = root.get("values");
        assertNotNull(valuesElement, "Missing values array");
        assertTrue(valuesElement.isJsonArray(), "values should be an array");
        boolean found = false;
        for (JsonElement element : valuesElement.getAsJsonArray()) {
            if (element.isJsonPrimitive() && expected.equals(element.getAsString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, message);
    }

    private static JsonElement jsonString(String value) {
        return JsonParser.parseString("\"" + value + "\"");
    }

    private static String textureResourcePath(String textureId) {
        int separator = textureId.indexOf(':');
        assertTrue(separator > 0, "Texture id should include a namespace: " + textureId);
        String namespace = textureId.substring(0, separator);
        String path = textureId.substring(separator + 1);
        assertEquals("data_energistics", namespace, "Compartment texture namespace should be local");
        return TEXTURE_ROOT + path + ".png";
    }

    private static void assertResourceExists(String path, String message) {
        try (InputStream stream = CompartmentResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, message + ": " + path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + path, exception);
        }
    }
}
