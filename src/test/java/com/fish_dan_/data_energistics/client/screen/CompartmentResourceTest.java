package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CompartmentResourceTest {

    private static final String BLOCKSTATE_ROOT = "assets/data_energistics/blockstates/";
    private static final String MODEL_ROOT = "assets/data_energistics/models/block/compartment/";
    private static final String ITEM_MODEL_ROOT = "assets/data_energistics/models/item/";
    private static final String TEXTURE_ROOT = "assets/data_energistics/textures/";
    private static final String SCREEN_ROOT = "assets/ae2/screens/";
    private static final String GUI_ROOT = "assets/ae2/textures/guis/";
    private static final String DATA_ROOT = "data/";
    private static final String ME_INTERFACE_TEXTURE = "data_energistics:block/compartment/me_interface";
    private static final String CONTROLLER_LIGHTS_TEXTURE = "data_energistics:block/compartment/controller_lights";
    private static final String ME_PATTERN_BUFFER_MODEL = "data_energistics:block/compartment/me_pattern_buffer";
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
    private static final List<String> COMPARTMENT_FACINGS = List.of("north", "east", "south", "west");
    private static final Map<String, Integer> FACING_ROTATIONS = Map.of(
            "east", 90,
            "south", 180,
            "west", 270);
    private static final Map<String, String> ACTIVE_COMPARTMENT_MODEL_PREFIXES = Map.of(
            "composite_input_warehouse", "data_energistics:block/compartment/composite_input_warehouse",
            "composite_output_warehouse", "data_energistics:block/compartment/composite_output_warehouse",
            "me_composite_input_warehouse", "data_energistics:block/compartment/me_composite_input_warehouse",
            "me_composite_output_warehouse", "data_energistics:block/compartment/me_composite_output_warehouse");
    private static final Map<String, String> COMPARTMENT_ITEM_PARENTS = Map.of(
            "composite_input_warehouse", "data_energistics:block/compartment/composite_input_warehouse_off",
            "composite_output_warehouse", "data_energistics:block/compartment/composite_output_warehouse_off",
            "me_composite_input_warehouse", "data_energistics:block/compartment/me_composite_input_warehouse_off",
            "me_composite_output_warehouse", "data_energistics:block/compartment/me_composite_output_warehouse_off",
            "me_pattern_buffer", ME_PATTERN_BUFFER_MODEL);

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
    void activeCompartmentBlockstatesSelectStateModelsAndFacingRotations() {
        for (Map.Entry<String, String> entry : ACTIVE_COMPARTMENT_MODEL_PREFIXES.entrySet()) {
            String blockId = entry.getKey();
            JsonObject variants = variants(blockId);
            assertEquals(8, variants.size(), blockId + " should define four facings for both active states");

            for (String facing : COMPARTMENT_FACINGS) {
                assertActiveVariant(variants, blockId, facing, false, entry.getValue() + "_off");
                assertActiveVariant(variants, blockId, facing, true, entry.getValue() + "_on");
            }
        }
    }

    @Test
    void mePatternBufferBlockstateUsesSingleModelAndFacingRotations() {
        JsonObject variants = variants("me_pattern_buffer");
        assertEquals(8, variants.size(), "ME pattern buffer should define four facings for both active states");

        for (String facing : COMPARTMENT_FACINGS) {
            assertActiveVariant(variants, "me_pattern_buffer", facing, false, ME_PATTERN_BUFFER_MODEL);
            assertActiveVariant(variants, "me_pattern_buffer", facing, true, ME_PATTERN_BUFFER_MODEL);
        }
    }

    @Test
    void compartmentItemModelsReferenceExistingBlockModels() {
        for (Map.Entry<String, String> entry : COMPARTMENT_ITEM_PARENTS.entrySet()) {
            String blockId = entry.getKey();
            String expectedParent = entry.getValue();
            JsonObject itemModel = readJson(ITEM_MODEL_ROOT + blockId + ".json");

            assertEquals(expectedParent, string(itemModel, "parent"), blockId + " item model should use the block model");
            assertResourceExists(modelResourcePath(expectedParent), blockId + " item parent model should exist");
        }
    }

    @Test
    void compartmentBlockModelTexturesKeepCompartmentSemantics() {
        Map<String, String> plainModelScreens = Map.of(
                "composite_input_warehouse_off.json", "data_energistics:block/compartment/input_screen_off",
                "composite_input_warehouse_on.json", "data_energistics:block/compartment/input_screen_on",
                "composite_output_warehouse_off.json", "data_energistics:block/compartment/output_screen_off",
                "composite_output_warehouse_on.json", "data_energistics:block/compartment/output_screen_on");

        for (Map.Entry<String, String> entry : plainModelScreens.entrySet()) {
            String model = entry.getKey();
            JsonObject textures = textures(model);
            assertNoTextureReference(textures, "me_interface", model);
            assertEquals(entry.getValue(), string(textures, "2"), model + " screen texture should match its IO state");
        }

        for (String model : List.of(
                "me_composite_input_warehouse_off.json",
                "me_composite_input_warehouse_on.json",
                "me_composite_output_warehouse_off.json",
                "me_composite_output_warehouse_on.json")) {
            assertEquals(ME_INTERFACE_TEXTURE, string(textures(model), "1"), model + " should keep the ME interface");
        }

        for (String model : List.of(
                "me_composite_input_warehouse_on.json",
                "me_composite_output_warehouse_on.json")) {
            assertTextureValue(textures(model), CONTROLLER_LIGHTS_TEXTURE, model + " should light the ME controller");
        }

        assertEquals(
                ME_INTERFACE_TEXTURE,
                string(textures("me_pattern_buffer.json"), "1"),
                "ME pattern buffer should keep the ME interface");
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

        assertSlotGridAnchors(
                slots,
                image,
                "COMPARTMENT_PATTERN",
                8,
                16,
                9,
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT);
        assertSlotGridAnchors(
                slots,
                image,
                "COMPARTMENT_PATTERN_BUFFER",
                177,
                15,
                3,
                CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT);
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

    private static void assertSlotGridAnchors(JsonObject slots,
                                              BufferedImage image,
                                              String semantic,
                                              int expectedLeft,
                                              int expectedTop,
                                              int columns,
                                              int count) {
        JsonObject slot = object(slots, semantic);
        assertEquals(expectedLeft, integer(slot, "left"), semantic + " left coordinate should match the texture");
        assertEquals(expectedTop, integer(slot, "top"), semantic + " top coordinate should match the texture");

        for (int index = 0; index < count; index++) {
            int left = expectedLeft + 18 * (index % columns);
            int top = expectedTop + 18 * (index / columns);
            assertEquals(
                    SLOT_ANCHOR_COLOR,
                    image.getRGB(left, top),
                    semantic + " slot " + index + " should point at a texture slot anchor");
        }

        int nextLeft = expectedLeft + 18 * (count % columns);
        int nextTop = expectedTop + 18 * (count / columns);
        assertNotSlotAnchor(image, nextLeft, nextTop, semantic + " should not expose an extra texture slot");
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

    private static JsonObject textures(String model) {
        return object(readJson(MODEL_ROOT + model), "textures");
    }

    private static JsonObject variants(String blockId) {
        return object(readJson(BLOCKSTATE_ROOT + blockId + ".json"), "variants");
    }

    private static void assertActiveVariant(JsonObject variants,
                                            String blockId,
                                            String facing,
                                            boolean active,
                                            String expectedModel) {
        String variantKey = "facing=" + facing + ",active=" + active;
        JsonObject variant = object(variants, variantKey);
        assertEquals(expectedModel, string(variant, "model"), blockId + " " + variantKey + " should use the expected model");
        assertFacingRotation(variant, blockId, facing, variantKey);
        assertResourceExists(modelResourcePath(expectedModel), blockId + " " + variantKey + " model should exist");
    }

    private static void assertFacingRotation(JsonObject variant, String blockId, String facing, String variantKey) {
        if ("north".equals(facing)) {
            assertFalse(variant.has("y"), blockId + " " + variantKey + " should not require y rotation");
            return;
        }

        Integer expectedRotation = FACING_ROTATIONS.get(facing);
        assertNotNull(expectedRotation, blockId + " " + variantKey + " should have an expected facing rotation");
        assertEquals(expectedRotation, integer(variant, "y"), blockId + " " + variantKey + " y rotation should match facing");
    }

    private static void assertNoTextureReference(JsonObject textures, String forbidden, String model) {
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            assertFalse(
                    entry.getValue().getAsString().contains(forbidden),
                    model + " texture " + entry.getKey() + " should not reference " + forbidden);
        }
    }

    private static void assertTextureValue(JsonObject textures, String expected, String message) {
        assertTrue(textures.asMap().containsValue(jsonString(expected)), message);
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

    private static String modelResourcePath(String modelId) {
        int separator = modelId.indexOf(':');
        assertTrue(separator > 0, "Model id should include a namespace: " + modelId);
        String namespace = modelId.substring(0, separator);
        String path = modelId.substring(separator + 1);
        assertEquals("data_energistics", namespace, "Compartment model namespace should be local");
        return "assets/" + namespace + "/models/" + path + ".json";
    }

    private static void assertResourceExists(String path, String message) {
        try (InputStream stream = CompartmentResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, message + ": " + path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + path, exception);
        }
    }
}
