package com.fish_dan_.data_energistics.network.trinity.crafting.progress;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressMeasure;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressSnapshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded latest-state confirmation-menu planning progress sent only from the server menu thread. */
public record TrinityCraftConfirmPlanningProgressPayload(int containerId,
                                                         long planRevision,
                                                         long sequence,
                                                         TrinityPlanningProgressSnapshot snapshot)
        implements CustomPacketPayload {

    public static final Type<TrinityCraftConfirmPlanningProgressPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_craft_confirm_planning_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityCraftConfirmPlanningProgressPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityCraftConfirmPlanningProgressPayload::write,
            TrinityCraftConfirmPlanningProgressPayload::new);

    public TrinityCraftConfirmPlanningProgressPayload {
        if (containerId < 0 || planRevision < 0L || sequence <= 0L) {
            throw new IllegalArgumentException("Invalid Trinity crafting confirmation planning progress envelope");
        }
    }

    private TrinityCraftConfirmPlanningProgressPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                new TrinityPlanningProgressSnapshot(
                        buffer.readEnum(TrinityPlanningProgressPhase.class),
                        buffer.readEnum(TrinityPlanningProgressMeasure.class),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarLong()));
    }

    @Override
    public Type<TrinityCraftConfirmPlanningProgressPayload> type() {
        return TYPE;
    }

    /** Delivers the envelope only after moving onto the client game thread. */
    public static void handle(TrinityCraftConfirmPlanningProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityCraftConfirmPlanningProgressClientHandler.receive(payload, context.player()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.planRevision);
        buffer.writeVarLong(this.sequence);
        buffer.writeEnum(this.snapshot.phase());
        buffer.writeEnum(this.snapshot.measure());
        buffer.writeVarInt(this.snapshot.completedUnits());
        buffer.writeVarInt(this.snapshot.totalUnits());
        buffer.writeVarInt(this.snapshot.routeStates());
        buffer.writeVarInt(this.snapshot.routeStateLimit());
        buffer.writeVarInt(this.snapshot.solverPasses());
        buffer.writeVarInt(this.snapshot.solverModels());
        buffer.writeVarInt(this.snapshot.jointStates());
        buffer.writeVarLong(this.snapshot.solverNanos());
    }
}
