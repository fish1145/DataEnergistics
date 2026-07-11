package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transfers one complete Trinity structure build request from the controller menu to its server-side host.
 */
public record TrinityDataCoreAutoBuildPayload(TrinityAutoBuildRequest request) implements CustomPacketPayload {

    /** Upper bound for category keys accepted from one client packet. */
    private static final int MAX_CATEGORY_KEY_LENGTH = 64;
    public static final Type<TrinityDataCoreAutoBuildPayload> TYPE = new Type<>(Data_Energistics.id("trinity_data_core_auto_build"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityDataCoreAutoBuildPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TrinityDataCoreAutoBuildPayload::write,
            TrinityDataCoreAutoBuildPayload::new);

    private TrinityDataCoreAutoBuildPayload(RegistryFriendlyByteBuf buf) {
        this(readRequest(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        writeRequest(buf, this.request);
    }

    @Override
    public Type<TrinityDataCoreAutoBuildPayload> type() {
        return TYPE;
    }

    public static void handle(TrinityDataCoreAutoBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            AbstractContainerMenu menu = serverPlayer.containerMenu;
            if (!(menu instanceof TrinityDataCoreMenu trinityMenu)) {
                return;
            }

            TrinityDataCoreMenuHost host = trinityMenu.getHost();
            if (host instanceof TrinityDataCoreBlockEntity trinityDataCore) {
                trinityDataCore.autoBuildTrinityStructure(serverPlayer, payload.request);
            }
        });
    }

    private static TrinityAutoBuildRequest readRequest(RegistryFriendlyByteBuf buf) {
        int structureIndex = buf.readVarInt();
        boolean buildRequested = buf.readBoolean();
        int repeatCount = buf.readVarInt();
        int selectionCount = buf.readVarInt();
        int maximumSelections = TrinityAutoBuildBlockMap.categories().size();
        if (selectionCount < 0 || selectionCount > maximumSelections) {
            throw new IllegalArgumentException("Trinity auto-build tier selection count is outside [0, " +
                    maximumSelections + "]: " + selectionCount);
        }

        LinkedHashMap<String, Integer> tierSelections = new LinkedHashMap<>();
        for (int index = 0; index < selectionCount; index++) {
            String category = buf.readUtf(MAX_CATEGORY_KEY_LENGTH);
            int tier = buf.readVarInt();
            if (tierSelections.putIfAbsent(category, tier) != null) {
                throw new IllegalArgumentException("Duplicate Trinity auto-build tier category: " + category);
            }
        }
        return new TrinityAutoBuildRequest(
                structureIndex,
                new TrinityAutoBuildOptions(buildRequested, repeatCount, tierSelections));
    }

    private static void writeRequest(RegistryFriendlyByteBuf buf, TrinityAutoBuildRequest request) {
        TrinityAutoBuildOptions options = request.options();
        buf.writeVarInt(request.structureIndex());
        buf.writeBoolean(options.buildRequested());
        buf.writeVarInt(options.repeatCount());
        buf.writeVarInt(options.tierSelections().size());
        for (Map.Entry<String, Integer> selection : options.tierSelections().entrySet()) {
            buf.writeUtf(selection.getKey(), MAX_CATEGORY_KEY_LENGTH);
            buf.writeVarInt(selection.getValue());
        }
    }
}
