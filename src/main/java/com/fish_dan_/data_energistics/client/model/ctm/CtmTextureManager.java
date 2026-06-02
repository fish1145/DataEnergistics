package com.fish_dan_.data_energistics.client.model.ctm;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CtmTextureManager {

    public static final Map<ResourceLocation, TextureAtlasSprite> CTM_SPRITE_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Optional<DataTextureMetadata>> METADATA_CACHE = new ConcurrentHashMap<>();

    private CtmTextureManager() {}

    public static void registerConnection(TextureAtlasSprite baseSprite, TextureAtlasSprite connectionSprite) {
        CTM_SPRITE_CACHE.put(baseSprite.contents().name(), connectionSprite);
    }

    public static void onAtlasStitched(TextureAtlasStitchedEvent event) {
        if (!event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            return;
        }

        CTM_SPRITE_CACHE.clear();
        METADATA_CACHE.clear();

        TextureAtlas atlas = event.getAtlas();
        for (ResourceLocation location : atlas.getTextures().keySet()) {
            if (!location.getNamespace().equals(Data_Energistics.MODID)) {
                continue;
            }
            getMetadataFromRelativeLocation(location)
                    .map(DataTextureMetadata::connectionTexture)
                    .ifPresent(connectionTexture -> CTM_SPRITE_CACHE.put(location, atlas.getSprite(connectionTexture)));
        }
    }

    private static Optional<DataTextureMetadata> getMetadataFromRelativeLocation(ResourceLocation relativeLocation) {
        return getMetadata(spriteToAbsolute(relativeLocation));
    }

    private static Optional<DataTextureMetadata> getMetadata(ResourceLocation resourceLocation) {
        return METADATA_CACHE.computeIfAbsent(resourceLocation, location -> {
            try {
                return Minecraft.getInstance().getResourceManager().getResource(location)
                        .flatMap(resource -> {
                            try {
                                return DataTextureMetadata.getForResource(resource);
                            } catch (Exception ignored) {
                                return Optional.empty();
                            }
                        });
            } catch (Exception ignored) {
                return Optional.empty();
            }
        });
    }

    private static ResourceLocation spriteToAbsolute(ResourceLocation sprite) {
        if (!sprite.getPath().startsWith("textures/")) {
            sprite = sprite.withPrefix("textures/");
        }
        if (!sprite.getPath().endsWith(".png")) {
            sprite = sprite.withSuffix(".png");
        }
        return sprite;
    }
}
