package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * Bounded wire codec shared by the preference and upload-success payloads.
 */
final class PatternEncodingRankingContextCodec {

    private PatternEncodingRankingContextCodec() {
    }

    static void writeNullable(RegistryFriendlyByteBuf buffer,
                              @Nullable PatternEncodingRankingContext context) {
        buffer.writeBoolean(context != null);
        if (context == null) {
            return;
        }
        writeResourceLocation(buffer, context.recipeTypeId());
    }

    @Nullable
    static PatternEncodingRankingContext readNullable(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        return new PatternEncodingRankingContext(readResourceLocation(buffer, "recipe type id"));
    }

    private static void writeResourceLocation(RegistryFriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), PatternEncodingRankingContext.MAX_RESOURCE_LOCATION_BYTES);
    }

    private static ResourceLocation readResourceLocation(RegistryFriendlyByteBuf buffer, String label) {
        String encoded = buffer.readUtf(PatternEncodingRankingContext.MAX_RESOURCE_LOCATION_BYTES);
        ResourceLocation id = ResourceLocation.tryParse(encoded);
        if (id == null) {
            throw new IllegalArgumentException("Invalid pattern ranking " + label + ": " + encoded);
        }
        return id;
    }
}
