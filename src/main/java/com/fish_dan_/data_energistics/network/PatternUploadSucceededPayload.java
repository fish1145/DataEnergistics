package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * S2C event emitted after the server confirms that one encoded pattern entered a provider inventory.
 */
public record PatternUploadSucceededPayload(
                                            @NotNull PatternUploadSource source,
                                            @Nullable PatternEncodingRankingContext rankingContext,
                                            @Nullable ResourceLocation confirmedWorkstation,
                                            @NotNull String providerDigest,
                                            long newCount,
                                            @NotNull Component targetName,
                                            @Nullable ResourceLocation dimensionId,
                                            @Nullable BlockPos position,
                                            long epochMillis)
        implements CustomPacketPayload {

    private static final Pattern DIGEST_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final int MAX_DIGEST_LENGTH = 71;
    private static final int MAX_TARGET_COMPONENT_BYTES = 1024;
    public static final Type<PatternUploadSucceededPayload> TYPE = new Type<>(
            Data_Energistics.id("pattern_upload_succeeded"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternUploadSucceededPayload> STREAM_CODEC = CustomPacketPayload.codec(PatternUploadSucceededPayload::write,
            PatternUploadSucceededPayload::new);

    /**
     * Validates the complete authoritative success event, including optional physical location consistency.
     */
    public PatternUploadSucceededPayload {
        if (!DIGEST_PATTERN.matcher(providerDigest).matches()) {
            throw new IllegalArgumentException("Invalid pattern provider digest: " + providerDigest);
        }
        if (newCount < 0L || epochMillis < 0L) {
            throw new IllegalArgumentException("Pattern upload success count and time must not be negative");
        }
        if (dimensionId == null != (position == null)) {
            throw new IllegalArgumentException("Pattern upload success location must contain both dimension and position");
        }
        if (rankingContext == null) {
            if (newCount != 0L) {
                throw new IllegalArgumentException("Pattern upload success without context must not carry history");
            }
        }
        if (confirmedWorkstation != null && rankingContext == null) {
            throw new IllegalArgumentException("Pattern upload success workstation requires a recipe-type context");
        }
        String targetEncoding = GsonHelper.toStableString(
                ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, targetName).getOrThrow());
        if (targetEncoding.getBytes(StandardCharsets.UTF_8).length > MAX_TARGET_COMPONENT_BYTES) {
            throw new IllegalArgumentException("Pattern upload success target component is too long");
        }
        position = position == null ? null : position.immutable();
    }

    private PatternUploadSucceededPayload(RegistryFriendlyByteBuf buffer) {
        this(PatternUploadSource.fromWireId(buffer.readUnsignedByte()), readContext(buffer),
                readNullableResourceLocation(buffer), buffer.readUtf(MAX_DIGEST_LENGTH), buffer.readVarLong(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                readNullableResourceLocation(buffer), readNullableBlockPos(buffer), buffer.readVarLong());
        if (buffer.readableBytes() != 0) {
            throw new IllegalArgumentException("Trailing bytes in pattern upload success payload");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(this.source.wireId());
        writeContext(buffer, this.rankingContext);
        writeNullableResourceLocation(buffer, this.confirmedWorkstation);
        buffer.writeUtf(this.providerDigest, MAX_DIGEST_LENGTH);
        buffer.writeVarLong(this.newCount);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.targetName);
        writeNullableResourceLocation(buffer, this.dimensionId);
        writeNullableBlockPos(buffer, this.position);
        buffer.writeVarLong(this.epochMillis);
    }

    @Override
    public @NotNull Type<PatternUploadSucceededPayload> type() {
        return TYPE;
    }

    /**
     * Applies idempotent client history and displays a notification only for Data Energistics uploads.
     */
    public static void handle(PatternUploadSucceededPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PatternUploadSucceededHandler.handle(payload, context.player()));
    }

    private static void writeContext(RegistryFriendlyByteBuf buffer,
                                     @Nullable PatternEncodingRankingContext context) {
        PatternEncodingRankingContextCodec.writeNullable(buffer, context);
    }

    @Nullable
    private static PatternEncodingRankingContext readContext(RegistryFriendlyByteBuf buffer) {
        return PatternEncodingRankingContextCodec.readNullable(buffer);
    }

    private static void writeNullableResourceLocation(RegistryFriendlyByteBuf buffer,
                                                      @Nullable ResourceLocation value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeResourceLocation(value);
        }
    }

    private static @Nullable ResourceLocation readNullableResourceLocation(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }

    private static void writeNullableBlockPos(RegistryFriendlyByteBuf buffer, @Nullable BlockPos value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeInt(value.getX());
            buffer.writeInt(value.getY());
            buffer.writeInt(value.getZ());
        }
    }

    private static @Nullable BlockPos readNullableBlockPos(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt()) : null;
    }
}
