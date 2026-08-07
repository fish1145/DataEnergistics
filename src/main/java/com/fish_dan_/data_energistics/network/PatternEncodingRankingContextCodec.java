package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Bounded wire codec shared by the preference and upload-success payloads. */
final class PatternEncodingRankingContextCodec {

    private PatternEncodingRankingContextCodec() {}

    static void writeNullable(RegistryFriendlyByteBuf buffer,
                              @Nullable PatternEncodingRankingContext context) {
        buffer.writeBoolean(context != null);
        if (context == null) {
            return;
        }
        writeResourceLocation(buffer, context.categoryId());
        buffer.writeVarInt(context.workstationIds().size());
        for (ResourceLocation workstationId : context.workstationIds()) {
            writeResourceLocation(buffer, workstationId);
        }
    }

    @Nullable
    static PatternEncodingRankingContext readNullable(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        ResourceLocation categoryId = readResourceLocation(buffer, "category id");
        int workstationCount = buffer.readVarInt();
        if (workstationCount < 0 || workstationCount > PatternEncodingRankingContext.MAX_WORKSTATION_IDS) {
            throw new IllegalArgumentException("Pattern ranking workstation ids exceed "
                    + PatternEncodingRankingContext.MAX_WORKSTATION_IDS);
        }
        List<ResourceLocation> workstationIds = new ArrayList<>(workstationCount);
        for (int index = 0; index < workstationCount; index++) {
            workstationIds.add(readResourceLocation(buffer, "workstation id"));
        }
        return new PatternEncodingRankingContext(categoryId, workstationIds);
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
