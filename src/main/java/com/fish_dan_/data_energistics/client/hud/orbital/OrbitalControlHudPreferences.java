package com.fish_dan_.data_energistics.client.hud.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Client-only persisted placement and presentation choices for the orbital HUD. */
public final class OrbitalControlHudPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("data_energistics_orbital_hud.json");
    private static final Preferences DEFAULTS = new Preferences(Anchor.TOP_LEFT, 8, 8, 1.0F, 1.0F, DisplayMode.COMPACT);
    private static Preferences current = load();

    private OrbitalControlHudPreferences() {}

    public static synchronized Preferences current() {
        return current;
    }

    public static synchronized boolean save(Preferences preferences) {
        Preferences candidate = preferences.clamped();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(candidate.toJson()), StandardCharsets.UTF_8);
            current = candidate;
            return true;
        } catch (IOException exception) {
            Data_Energistics.LOGGER.warn("Unable to persist orbital HUD preferences to {}", FILE, exception);
            return false;
        }
    }

    public static Preferences defaults() {
        return DEFAULTS;
    }

    private static Preferences load() {
        try {
            if (Files.isRegularFile(FILE)) {
                JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                return new Preferences(
                        Anchor.from(json, "anchor", DEFAULTS.anchor),
                        jsonValue(json, "offsetX", DEFAULTS.offsetX),
                        jsonValue(json, "offsetY", DEFAULTS.offsetY),
                        floatValue(json, "scale", DEFAULTS.scale),
                        floatValue(json, "opacity", DEFAULTS.opacity),
                        DisplayMode.from(json, "mode", DEFAULTS.mode)).clamped();
            }
        } catch (RuntimeException | IOException exception) {
            Data_Energistics.LOGGER.warn("Unable to load orbital HUD preferences from {}", FILE, exception);
        }
        return DEFAULTS;
    }

    private static int jsonValue(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber() ? json.get(key).getAsInt() : fallback;
    }

    private static float floatValue(JsonObject json, String key, float fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber() ? json.get(key).getAsFloat() : fallback;
    }

    public record Preferences(Anchor anchor, int offsetX, int offsetY, float scale, float opacity, DisplayMode mode) {

        public Preferences {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(mode, "mode");
        }

        public Preferences clamped() {
            float safeScale = Float.isFinite(scale) ? scale : 1.0F;
            float safeOpacity = Float.isFinite(opacity) ? opacity : 1.0F;
            return new Preferences(anchor, Math.clamp(offsetX, 0, 4096), Math.clamp(offsetY, 0, 4096),
                    Math.max(0.75F, Math.min(1.75F, safeScale)), Math.max(0.2F, Math.min(1.0F, safeOpacity)), mode);
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("anchor", anchor.name());
            json.addProperty("offsetX", offsetX);
            json.addProperty("offsetY", offsetY);
            json.addProperty("scale", scale);
            json.addProperty("opacity", opacity);
            json.addProperty("mode", mode.name());
            return json;
        }
    }

    public enum Anchor {

        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        private static Anchor from(JsonObject json, String key, Anchor fallback) {
            return json.has(key) ? valueOf(json.get(key).getAsString()) : fallback;
        }
    }

    public enum DisplayMode {

        COMPACT,
        DETAIL,
        HIDDEN;

        private static DisplayMode from(JsonObject json, String key, DisplayMode fallback) {
            return json.has(key) ? valueOf(json.get(key).getAsString()) : fallback;
        }
    }
}
