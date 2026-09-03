package com.fish_dan_.data_energistics.network.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jspecify.annotations.Nullable;

/**
 * S2C acknowledgement of an accepted preference snapshot and the server-confirmed workstation.
 */
public record PatternEncodingPreferencesAckPayload(
                                                   int containerId,
                                                   long sequence,
                                                   @Nullable ResourceLocation lastWorkstation)
        implements CustomPacketPayload {

    public static final Type<PatternEncodingPreferencesAckPayload> TYPE = new Type<>(
            Data_Energistics.id("pattern_encoding_preferences_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternEncodingPreferencesAckPayload> STREAM_CODEC = CustomPacketPayload.codec(PatternEncodingPreferencesAckPayload::write,
            PatternEncodingPreferencesAckPayload::new);

    /**
     * Validates bounded acknowledgement values.
     */
    public PatternEncodingPreferencesAckPayload {
        if (containerId < 0 || sequence <= 0L) {
            throw new IllegalArgumentException("Pattern preference acknowledgement envelope is invalid");
        }
    }

    private PatternEncodingPreferencesAckPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarLong(), readNullableResourceLocation(buffer));
        if (buffer.readableBytes() != 0) {
            throw new IllegalArgumentException("Trailing bytes in pattern preference acknowledgement");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.sequence);
        buffer.writeBoolean(this.lastWorkstation != null);
        if (this.lastWorkstation != null) {
            buffer.writeResourceLocation(this.lastWorkstation);
        }
    }

    @Override
    public Type<PatternEncodingPreferencesAckPayload> type() {
        return TYPE;
    }

    /**
     * Confirms an accepted snapshot for the client's current menu session.
     */
    public static void handle(PatternEncodingPreferencesAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PatternEncodingPreferencesAckHandler.handle(payload, context.player()));
    }

    private static @Nullable ResourceLocation readNullableResourceLocation(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }
}
