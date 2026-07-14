package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.transfer.MultiblockPatternTransferRequest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S request to fill, but not encode, one current multiblock recipe in an exact pattern terminal menu.
 */
public record MultiblockPatternTransferPayload(int containerId,
                                               ResourceLocation registeredRecipeId,
                                               ProjectionFingerprint projectionFingerprint)
        implements CustomPacketPayload {

    public static final Type<MultiblockPatternTransferPayload> TYPE = new Type<>(
            Data_Energistics.id("multiblock_pattern_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockPatternTransferPayload> STREAM_CODEC = CustomPacketPayload.codec(MultiblockPatternTransferPayload::write, MultiblockPatternTransferPayload::new);

    /**
     * Validates the same bounded schema used by the decoder before an outgoing payload can be created.
     */
    public MultiblockPatternTransferPayload {
        MultiblockPatternTransferPayloadCodec.validateRequest(requestOf(
                containerId,
                registeredRecipeId,
                projectionFingerprint));
    }

    /**
     * Creates the transport representation of a typed transfer request.
     */
    public MultiblockPatternTransferPayload(MultiblockPatternTransferRequest request) {
        this(request.containerId(), request.registeredRecipeId(), request.projectionFingerprint());
    }

    private MultiblockPatternTransferPayload(RegistryFriendlyByteBuf buffer) {
        this(MultiblockPatternTransferPayloadCodec.readRequest(buffer));
        MultiblockPatternTransferPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        MultiblockPatternTransferPayloadCodec.writeRequest(buffer, request());
    }

    /**
     * Returns the common-layer typed request consumed by server reconstruction.
     */
    public MultiblockPatternTransferRequest request() {
        return requestOf(this.containerId, this.registeredRecipeId, this.projectionFingerprint);
    }

    @Override
    public Type<MultiblockPatternTransferPayload> type() {
        return TYPE;
    }

    /**
     * Defers exact-menu routing and catalog reconstruction to the server main thread.
     */
    public static void handle(MultiblockPatternTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MultiblockPatternTransferPayloadHandler.handle(payload, context.player()));
    }

    private static MultiblockPatternTransferRequest requestOf(int containerId,
                                                              ResourceLocation registeredRecipeId,
                                                              ProjectionFingerprint projectionFingerprint) {
        return new MultiblockPatternTransferRequest(containerId, registeredRecipeId, projectionFingerprint);
    }
}
