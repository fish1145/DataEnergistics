package com.fish_dan_.data_energistics.network.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * S2C acknowledgement containing the server's final menu preference values and migration mask.
 */
public record PatternEncodingPreferencesAckPayload(
                                                   int containerId,
                                                   long sequence,
                                                   int migratedMask,
                                                   boolean uploadEnabled,
                                                   boolean patternSourceEnabled,
                                                   @Nullable ResourceLocation lastWorkstation,
                                                   int previewPanelOffsetX,
                                                   int previewPanelOffsetY)
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
        if ((migratedMask & ~0x0F) != 0) {
            throw new IllegalArgumentException("Pattern preference migration mask contains unknown bits");
        }
        if (previewPanelOffsetX < -8192 || previewPanelOffsetX > 8192 || previewPanelOffsetY < -8192 || previewPanelOffsetY > 8192) {
            throw new IllegalArgumentException("Pattern preference preview offset is outside [-8192, 8192]");
        }
    }

    private PatternEncodingPreferencesAckPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarLong(), buffer.readUnsignedByte(), buffer.readBoolean(),
                buffer.readBoolean(), readNullableResourceLocation(buffer), buffer.readInt(), buffer.readInt());
        if (buffer.readableBytes() != 0) {
            throw new IllegalArgumentException("Trailing bytes in pattern preference acknowledgement");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.sequence);
        buffer.writeByte(this.migratedMask);
        buffer.writeBoolean(this.uploadEnabled);
        buffer.writeBoolean(this.patternSourceEnabled);
        buffer.writeBoolean(this.lastWorkstation != null);
        if (this.lastWorkstation != null) {
            buffer.writeResourceLocation(this.lastWorkstation);
        }
        buffer.writeInt(this.previewPanelOffsetX);
        buffer.writeInt(this.previewPanelOffsetY);
    }

    @Override
    public @NotNull Type<PatternEncodingPreferencesAckPayload> type() {
        return TYPE;
    }

    /**
     * Applies only fields marked as migrated and still absent from the local client file.
     */
    public static void handle(PatternEncodingPreferencesAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PatternEncodingPreferencesAckHandler.handle(payload, context.player()));
    }

    private static @Nullable ResourceLocation readNullableResourceLocation(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }
}
