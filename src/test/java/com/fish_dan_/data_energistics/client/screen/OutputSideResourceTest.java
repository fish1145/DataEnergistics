package com.fish_dan_.data_energistics.client.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OutputSideResourceTest {

    private static final String SCREEN_ROOT = "assets/ae2/screens/";
    private static final List<String> SIDE_SCREENS = List.of(
            "data_ripper_output_sides.json",
            "data_sanctum_active_pull_sides.json");

    @Test
    void sideButtonsMatchTheirVisualDirection() {
        for (String screen : SIDE_SCREENS) {
            JsonObject widgets = readJson(SCREEN_ROOT + screen).getAsJsonObject("widgets");
            JsonObject left = widgets.getAsJsonObject("left");
            JsonObject front = widgets.getAsJsonObject("front");
            JsonObject right = widgets.getAsJsonObject("right");

            assertEquals(front.get("top").getAsInt(), left.get("top").getAsInt(), screen);
            assertEquals(front.get("top").getAsInt(), right.get("top").getAsInt(), screen);
            assertTrue(left.get("left").getAsInt() < front.get("left").getAsInt(), screen);
            assertTrue(front.get("left").getAsInt() < right.get("left").getAsInt(), screen);
        }
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = OutputSideResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing resource: " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new AssertionError("Failed to read resource: " + path, e);
        }
    }
}
