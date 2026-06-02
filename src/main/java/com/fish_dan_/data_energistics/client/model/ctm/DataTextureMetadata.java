package com.fish_dan_.data_energistics.client.model.ctm;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

public record DataTextureMetadata(@Nullable ResourceLocation connectionTexture) {

    public static final String SECTION_NAME = Data_Energistics.MODID;
    public static final MetadataSectionSerializer<DataTextureMetadata> SERIALIZER = new Serializer();
    public static final DataTextureMetadata EMPTY = new DataTextureMetadata(null);

    public static Optional<DataTextureMetadata> getForResource(Resource resource) throws IOException {
        return resource.metadata().getSection(SERIALIZER);
    }

    public DataTextureMetadata {
        if (connectionTexture == Serializer.EMPTY_CONNECTION) {
            connectionTexture = null;
        }
    }

    public static class Serializer implements MetadataSectionSerializer<DataTextureMetadata> {

        protected static final ResourceLocation EMPTY_CONNECTION = Data_Energistics.id("__empty_connection__");

        public static final Codec<DataTextureMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("connection_texture", EMPTY_CONNECTION)
                        .forGetter(DataTextureMetadata::connectionTexture))
                .apply(instance, DataTextureMetadata::new));

        @Override
        public DataTextureMetadata fromJson(@Nullable JsonObject json) throws JsonParseException {
            return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
        }

        @Override
        public String getMetadataSectionName() {
            return SECTION_NAME;
        }
    }
}
