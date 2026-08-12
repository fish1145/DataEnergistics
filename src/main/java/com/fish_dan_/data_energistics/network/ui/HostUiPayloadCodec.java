package com.fish_dan_.data_energistics.network.ui;

import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiResponseStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Shared, bounded field codec for the two directions of the host UI lifecycle protocol. */
final class HostUiPayloadCodec {

    private HostUiPayloadCodec() {}

    /** Reads and validates one ordered operation request. */
    static HostUiRequest readRequest(RegistryFriendlyByteBuf buffer) {
        HostUiOperation operation = HostUiOperation.fromNetworkId(buffer.readVarInt());
        HostUiKey key = new HostUiKey(ResourceLocation.parse(buffer.readUtf(HostUiKey.MAX_NETWORK_LENGTH)));
        long sequence = buffer.readVarLong();
        return new HostUiRequest(operation, key, sequence);
    }

    /** Writes one request without adding transport-envelope fields. */
    static void writeRequest(RegistryFriendlyByteBuf buffer, HostUiRequest request) {
        buffer.writeVarInt(request.operation().networkId());
        buffer.writeUtf(request.key().id().toString(), HostUiKey.MAX_NETWORK_LENGTH);
        buffer.writeVarLong(request.sequence());
    }

    /** Reads one response and its exact echoed request identity. */
    static HostUiResponse readResponse(RegistryFriendlyByteBuf buffer) {
        HostUiRequest request = readRequest(buffer);
        HostUiResponseStatus status = HostUiResponseStatus.fromNetworkId(buffer.readVarInt());
        return new HostUiResponse(request, status);
    }

    /** Writes one response after its echoed request fields. */
    static void writeResponse(RegistryFriendlyByteBuf buffer, HostUiResponse response) {
        writeRequest(buffer, response.request());
        buffer.writeVarInt(response.status().networkId());
    }
}
