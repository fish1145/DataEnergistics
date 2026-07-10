package com.fish_dan_.data_energistics.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDataCoreResourceTest {

    private static final String MODEL_ROOT = "assets/data_energistics/models/block/";
    private static final String DATA_TEXTURE_ROOT = "assets/data_energistics/textures/";
    private static final String LANG_ROOT = "assets/data_energistics/lang/";
    private static final String SCREEN_ROOT = "assets/ae2/screens/";
    private static final String GUI_TEXTURE_ROOT = "assets/ae2/textures/";
    private static final String MULTIBLOCK_ROOT = "data/data_energistics/multiblock/";
    private static final String TRINITY_DATA_CORE_TEXTURE_PREFIX = "data_energistics:block/trinity_data_core/";
    private static final Set<String> MODEL_TEXTURE_KEYS = Set.of("2", "3", "top_light", "5", "screen");

    @Test
    void trinityDataCoreBlockModelsUseRenamedTextureFolder() {
        for (String model : Set.of("trinity_data_core_off.json", "trinity_data_core_on.json")) {
            JsonObject root = readJson(MODEL_ROOT + model);
            assertNoAbsolutePaths(root, model);

            JsonObject textures = object(root, "textures");
            for (String textureKey : MODEL_TEXTURE_KEYS) {
                String textureId = string(textures, textureKey);
                assertTrue(
                        textureId.startsWith(TRINITY_DATA_CORE_TEXTURE_PREFIX),
                        model + " texture " + textureKey + " should use the trinity_data_core texture folder");
                assertFalse(textureId.contains("data_reassembler"), model + " should not reuse data_reassembler textures");
                assertFalse(textureId.contains("ae2:block/generics/bottom"), model + " should not reference the old AE2 bottom texture");
                assertResourceExists(textureResourcePath(textureId), model + " texture " + textureId + " should exist");
            }
        }
    }

    @Test
    void trinityDataCoreScreenUsesRenamedGuiTextureFolder() {
        JsonObject root = readJson(SCREEN_ROOT + "trinity_data_core.json");
        assertNoAbsolutePaths(root, "trinity_data_core screen");

        JsonObject background = object(root, "background");
        String backgroundTexture = string(background, "texture");
        assertEquals("guis/trinity_data_core/gui.png", backgroundTexture);
        assertSourceRect(background, 256, 212);
        assertResourceExists(GUI_TEXTURE_ROOT + backgroundTexture, "Trinity Data Core GUI texture should exist");
        assertPngDimensions(GUI_TEXTURE_ROOT + backgroundTexture, 256, 256);

        JsonObject text = object(root, "text");
        assertEquals(
                Set.of("dialog_title", "player_inventory_title"),
                text.keySet(),
                "Dynamic host status text should be drawn by TrinityDataCoreScreen");

        assertResourceExists(
                GUI_TEXTURE_ROOT + "guis/trinity_data_core/cpu_idle.png",
                "Trinity CPU list idle overlay should exist");
        assertResourceExists(
                GUI_TEXTURE_ROOT + "guis/trinity_data_core/cpu_task_overlay.png",
                "Trinity CPU list task overlay should exist");
        assertPngDimensions(GUI_TEXTURE_ROOT + "guis/trinity_data_core/cpu_idle.png", 67, 22);
        assertPngDimensions(GUI_TEXTURE_ROOT + "guis/trinity_data_core/cpu_task_overlay.png", 67, 22);
    }

    @Test
    void trinityDataCoreMultiblockUsesRenamedStructure() {
        JsonObject root = readJson(MULTIBLOCK_ROOT + "trinity_data_core/main.json");
        JsonObject metadata = object(root, "metadata");
        assertEquals(
                "multiblock.data_energistics.trinity_data_core",
                string(metadata, "display_name"),
                "Trinity Data Core should expose its renamed multiblock display key");

        JsonObject predicates = object(root, "predicates");
        Set<String> values = new HashSet<>();
        collectStringValues(predicates, values);

        assertFalse(
                values.contains("ae2:crafting_unit"),
                "Trinity Data Core storage core positions should not keep the exported AE2 crafting unit placeholder");
        assertFalse(
                values.contains("ae2:drive"),
                "Trinity Data Core storage core positions should not keep the exported AE2 drive placeholder");
        assertStorageCorePredicate(predicates, "Z");
        assertFalse(
                predicates.has("d"),
                "Trinity Data Core storage core positions should use Z instead of duplicate d");
        assertTrue(
                values.contains("ae2:controller"),
                "Trinity Data Core should reference the exported AE2 controller body");
        assertTrue(
                values.contains("data_energistics:data_framework"),
                "Trinity Data Core should preserve the exported data framework shell");
        assertFalse(
                values.contains("expatternprovider:assembler_matrix_pattern"),
                "Trinity Data Core should no longer reference the previous assembler matrix pattern block id");
        assertFalse(
                values.contains("extendedae:assembler_matrix_pattern"),
                "Trinity Data Core should not reference the missing ExtendedAE namespace");
        JsonObject cableBusPredicate = object(predicates, "#");
        assertEquals("data_energistics:placement_items", string(cableBusPredicate, "type"));
        assertEquals("ae2:fluix_covered_cable", string(cableBusPredicate, "item"));
    }

    @Test
    void trinityDataCoreLanguageUsesRenamedDisplayNames() {
        JsonObject zhCn = readJson(LANG_ROOT + "zh_cn.json");
        assertEquals(
                "三位一体数位化核心",
                string(zhCn, "block.data_energistics.trinity_data_core"),
                "Controller block name should use the renamed Trinity Data Core display name");
        assertEquals(
                "结构方块不匹配",
                string(zhCn, "text.data_energistics.multiblock.failure.block_predicate"),
                "Known MDLib predicate diagnostics should be localized for Jade and the host screen");

        JsonObject enUs = readJson(LANG_ROOT + "en_us.json");
        assertEquals(
                "Trinity Data Core",
                string(enUs, "block.data_energistics.trinity_data_core"),
                "English controller block name should match the renamed structure");
        assertEquals(
                "Structure block did not match",
                string(enUs, "text.data_energistics.multiblock.failure.block_predicate"),
                "English predicate diagnostic should avoid exposing the raw MDLib message");
    }

    private static void assertStorageCorePredicate(JsonObject predicates, String symbol) {
        JsonObject predicate = object(predicates, symbol);
        JsonElement blocksElement = predicate.get("blocks");
        assertNotNull(blocksElement, symbol + " should use the storage core block list");
        assertTrue(blocksElement.isJsonArray(), symbol + " storage core predicate should list accepted blocks");

        Set<String> blocks = new HashSet<>();
        for (JsonElement blockElement : blocksElement.getAsJsonArray()) {
            assertTrue(blockElement.isJsonPrimitive(), symbol + " storage core entry should be a block id");
            blocks.add(blockElement.getAsString());
        }

        assertEquals(10, blocks.size(), symbol + " should accept every ordinary digital storage core tier");
        assertTrue(blocks.contains("data_energistics:me_digital_storage_core_1m"), symbol + " should accept the 1M storage core");
        assertTrue(blocks.contains("data_energistics:me_digital_storage_core_256g"), symbol + " should accept the 256G storage core");
    }

    private static void assertSourceRect(JsonObject owner, int expectedWidth, int expectedHeight) {
        JsonElement element = owner.get("srcRect");
        assertNotNull(element, "srcRect should be present");
        assertTrue(element.isJsonArray(), "srcRect should be an array");
        JsonArray srcRect = element.getAsJsonArray();
        assertEquals(4, srcRect.size(), "srcRect should contain x, y, width and height");
        assertEquals(expectedWidth, srcRect.get(2).getAsInt(), "srcRect width should match the source texture");
        assertEquals(expectedHeight, srcRect.get(3).getAsInt(), "srcRect height should match the source texture");
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = TrinityDataCoreResourceTest.class.getClassLoader().getResourceAsStream(path)) {
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

    private static String textureResourcePath(String textureId) {
        int separator = textureId.indexOf(':');
        assertTrue(separator > 0, "Texture id should include a namespace: " + textureId);
        String namespace = textureId.substring(0, separator);
        String path = textureId.substring(separator + 1);
        assertEquals("data_energistics", namespace, "Trinity Data Core texture namespace should be local");
        assertTrue(
                path.startsWith("block/trinity_data_core/"),
                textureId + " should be under block/trinity_data_core");
        return DATA_TEXTURE_ROOT + path + ".png";
    }

    private static void assertResourceExists(String path, String message) {
        try (InputStream stream = TrinityDataCoreResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, message + ": " + path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + path, exception);
        }
    }

    private static void assertPngDimensions(String path, int expectedWidth, int expectedHeight) {
        try (InputStream stream = TrinityDataCoreResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing PNG resource " + path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "Could not decode PNG resource " + path);
            assertEquals(expectedWidth, image.getWidth(), path + " width should match the source texture");
            assertEquals(expectedHeight, image.getHeight(), path + " height should match the source texture");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read PNG resource " + path, exception);
        }
    }

    private static void assertNoAbsolutePaths(JsonElement element, String owner) {
        Set<String> values = new HashSet<>();
        collectStringValues(element, values);
        for (String value : values) {
            assertFalse(isAbsolutePath(value), owner + " should not contain absolute paths: " + value);
        }
    }

    private static boolean isAbsolutePath(String value) {
        return value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("/") || value.startsWith("\\\\");
    }

    private static void collectStringValues(JsonElement element, Set<String> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            values.add(element.getAsString());
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStringValues(child, values);
            }
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectStringValues(entry.getValue(), values);
            }
        }
    }
}
