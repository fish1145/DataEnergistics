package com.fish_dan_.data_energistics.client.gui;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataEnergisticsIconTest {

    private static final ResourceLocation DATA_STATES_TEXTURE = ResourceLocation.fromNamespaceAndPath("data_energistics", "textures/guis/states.png");
    private static final ResourceLocation AE2_STATES_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");

    @Test
    void statesResourceUsesAeStyleRelativeTextureReference() {
        JsonObject root = readJson("assets/data_energistics/textures/guis/states.json");
        String texture = root.get("texture").getAsString();

        assertEquals("guis/states.png", texture);
        assertEquals(DATA_STATES_TEXTURE, DataEnergisticsIcon.resolveTexture(texture, DATA_STATES_TEXTURE));
    }

    @Test
    void relativeTextureReferenceUsesTheDefaultTextureNamespace() {
        assertEquals(AE2_STATES_TEXTURE, DataEnergisticsIcon.resolveTexture("guis/states.png", AE2_STATES_TEXTURE));
    }

    @Test
    void keepsExplicitNamespacedTextureReference() {
        assertEquals(
                DATA_STATES_TEXTURE,
                DataEnergisticsIcon.resolveTexture("data_energistics:textures/guis/states.png", AE2_STATES_TEXTURE));
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = DataEnergisticsIconTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var element = JsonParser.parseReader(reader);
                assertTrue(element.isJsonObject(), path + " should parse as a JSON object");
                return element.getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read resource " + path, exception);
        }
    }
}
