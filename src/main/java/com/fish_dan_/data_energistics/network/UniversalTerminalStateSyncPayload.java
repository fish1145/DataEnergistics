package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record UniversalTerminalStateSyncPayload(List<String> installedTerminalNames,
                                                @Nullable String activeTerminalName)
        implements CustomPacketPayload {

    public static final Type<UniversalTerminalStateSyncPayload> TYPE = new Type<>(Data_Energistics.id("universal_terminal_state_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UniversalTerminalStateSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(UniversalTerminalStateSyncPayload::write, UniversalTerminalStateSyncPayload::new);

    private UniversalTerminalStateSyncPayload(RegistryFriendlyByteBuf buf) {
        this(readInstalledTerminalNames(buf), buf.readBoolean() ? buf.readUtf() : null);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.installedTerminalNames.size());
        for (String terminalName : this.installedTerminalNames) {
            buf.writeUtf(terminalName);
        }
        buf.writeBoolean(this.activeTerminalName != null);
        if (this.activeTerminalName != null) {
            buf.writeUtf(this.activeTerminalName);
        }
    }

    @Override
    public Type<UniversalTerminalStateSyncPayload> type() {
        return TYPE;
    }

    public static void handle(UniversalTerminalStateSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> UniversalTerminalStateSyncHandler.cacheSyncedTerminalState(payload));
    }

    private static List<String> readInstalledTerminalNames(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        ArrayList<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            names.add(buf.readUtf());
        }
        return List.copyOf(names);
    }
}
