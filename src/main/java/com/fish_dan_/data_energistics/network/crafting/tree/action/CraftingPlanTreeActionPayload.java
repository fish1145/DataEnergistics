package com.fish_dan_.data_energistics.network.crafting.tree.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.crafting.tree.CraftingPlanTreeMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** No plan or CPU handle crosses this client-to-server action boundary. */
public record CraftingPlanTreeActionPayload(int containerId, UUID sessionId, long revision, Action action)
        implements CustomPacketPayload {

    public static final Type<CraftingPlanTreeActionPayload> TYPE = new Type<>(Data_Energistics.id("plan_tree_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingPlanTreeActionPayload> STREAM_CODEC = CustomPacketPayload.codec(CraftingPlanTreeActionPayload::write, CraftingPlanTreeActionPayload::new);

    public enum Action {
        START,
        CANCEL,
        REPLAN,
        RETURN_LIST,
        NEXT_CPU,
        PREVIOUS_CPU
    }

    public CraftingPlanTreeActionPayload {
        if (containerId < 0 || revision < 0) throw new IllegalArgumentException("Invalid plan-tree action envelope");
    }

    private CraftingPlanTreeActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID(), buffer.readVarLong(), buffer.readEnum(Action.class));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeUUID(this.sessionId);
        buffer.writeVarLong(this.revision);
        buffer.writeEnum(this.action);
    }

    @Override
    public Type<CraftingPlanTreeActionPayload> type() {
        return TYPE;
    }

    public static void handle(CraftingPlanTreeActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof CraftingPlanTreeMenu menu) menu.handleAction(payload);
        });
    }
}
