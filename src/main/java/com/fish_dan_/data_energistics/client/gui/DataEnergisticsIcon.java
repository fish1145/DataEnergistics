package com.fish_dan_.data_energistics.client.gui;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import appeng.client.gui.style.Blitter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DataEnergisticsIcon {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final ResourceLocation STATES_JSON = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/guis/states.json");
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/guis/states.png");
    private static final ResourceLocation AE2_STATES_JSON = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.json");
    private static final ResourceLocation AE2_DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");
    private static final String FALLBACK_ICON = "BACKGROUND_BLOCK";
    private static final Map<String, IconDef> CACHE = new ConcurrentHashMap<>();

    private DataEnergisticsIcon() {}

    public static Blitter getBlitter(String name) {
        var icon = CACHE.computeIfAbsent(name, DataEnergisticsIcon::loadIcon);
        return Blitter.texture(icon.texture(), icon.textureWidth(), icon.textureHeight())
                .src(icon.x(), icon.y(), icon.width(), icon.height());
    }

    private static IconDef loadIcon(String name) {
        try {
            IconDef icon = loadIconFromResource(STATES_JSON, DEFAULT_TEXTURE, name, false);
            if (icon != null) {
                return icon;
            }

            icon = loadIconFromResource(AE2_STATES_JSON, AE2_DEFAULT_TEXTURE, name, false);
            if (icon != null) {
                return icon;
            }

            LOGGER.error("Missing icon definition in {} and {}: {}, falling back to {}",
                    STATES_JSON, AE2_STATES_JSON, name, FALLBACK_ICON);
            IconDef fallback = loadIconFromResource(STATES_JSON, DEFAULT_TEXTURE, FALLBACK_ICON, true);
            if (fallback != null) {
                return fallback;
            }

            fallback = loadIconFromResource(AE2_STATES_JSON, AE2_DEFAULT_TEXTURE, FALLBACK_ICON, true);
            if (fallback != null) {
                return fallback;
            }

            return new IconDef(DEFAULT_TEXTURE, 128, 128, 0, 0, 16, 16);
        } catch (Exception e) {
            LOGGER.error("Failed to load GUI icon {} from {}, using hard-coded fallback", name, STATES_JSON, e);
            return new IconDef(DEFAULT_TEXTURE, 128, 128, 0, 0, 16, 16);
        }
    }

    private static IconDef loadIconFromResource(ResourceLocation statesJson, ResourceLocation defaultTexture,
                                                String name, boolean silent) {
        try {
            var resourceManager = Minecraft.getInstance().getResourceManager();
            Resource resource = resourceManager.getResourceOrThrow(statesJson);
            try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                ResourceLocation texture = root.has("texture") ? ResourceLocation.parse(root.get("texture").getAsString()) : defaultTexture;
                int textureWidth = root.has("width") ? root.get("width").getAsInt() : 256;
                int textureHeight = root.has("height") ? root.get("height").getAsInt() : 256;
                JsonObject icons = root.getAsJsonObject("icons");
                if (icons == null || !icons.has(name)) {
                    if (!silent) {
                        LOGGER.debug("Icon {} not found in {}", name, statesJson);
                    }
                    return null;
                }

                JsonObject icon = icons.getAsJsonObject(name);
                return new IconDef(
                        texture,
                        textureWidth,
                        textureHeight,
                        icon.get("x").getAsInt(),
                        icon.get("y").getAsInt(),
                        icon.get("width").getAsInt(),
                        icon.get("height").getAsInt());
            }
        } catch (Exception e) {
            if (!silent) {
                LOGGER.debug("Failed to load icon {} from {}", name, statesJson, e);
            }
            return null;
        }
    }

    private record IconDef(
                           ResourceLocation texture,
                           int textureWidth,
                           int textureHeight,
                           int x,
                           int y,
                           int width,
                           int height) {}
}
