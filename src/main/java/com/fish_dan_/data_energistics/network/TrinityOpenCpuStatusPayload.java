package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuContribution;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S request to open AE's crafting-status menu on one stable Trinity CPU number. */
public record TrinityOpenCpuStatusPayload(int containerId, UUID hostId, int cpuNumber)
        implements CustomPacketPayload {

    private static final int MAX_CPU_NUMBER = TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT;

    public static final Type<TrinityOpenCpuStatusPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_open_cpu_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityOpenCpuStatusPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityOpenCpuStatusPayload::write, TrinityOpenCpuStatusPayload::new);

    /** Rejects malformed menu, host and stable CPU identities before transport. */
    public TrinityOpenCpuStatusPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID) {
            throw new IllegalArgumentException("Invalid Trinity CPU status container id: " + containerId);
        }
        if (hostId == null) {
            throw new IllegalArgumentException("Trinity CPU status host id cannot be null");
        }
        if (cpuNumber < 0 || cpuNumber > MAX_CPU_NUMBER) {
            throw new IllegalArgumentException("Invalid Trinity CPU number: " + cpuNumber);
        }
    }

    private TrinityOpenCpuStatusPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                buffer.readUUID(),
                readCpuNumber(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        buffer.writeUUID(this.hostId);
        buffer.writeVarInt(this.cpuNumber);
    }

    private static int readCpuNumber(RegistryFriendlyByteBuf buffer) {
        int cpuNumber = buffer.readVarInt();
        if (cpuNumber < 0 || cpuNumber > MAX_CPU_NUMBER) {
            throw new IllegalArgumentException("Invalid Trinity CPU number: " + cpuNumber);
        }
        return cpuNumber;
    }

    @Override
    public Type<TrinityOpenCpuStatusPayload> type() {
        return TYPE;
    }

    /** Defers all menu, host, lease and grid validation to the server main thread. */
    public static void handle(TrinityOpenCpuStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityOpenCpuStatusPayloadHandler.handle(payload, context.player()));
    }
}
