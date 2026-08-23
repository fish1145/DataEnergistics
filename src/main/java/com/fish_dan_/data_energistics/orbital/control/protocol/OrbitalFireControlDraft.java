package com.fish_dan_.data_energistics.orbital.control.protocol;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Immutable, typed target draft shared by the terminal form, map handoff and one preview intent. */
public record OrbitalFireControlDraft(
                                      OrbitalAttackMode mode,
                                      ResourceLocation dimensionId,
                                      int targetX,
                                      int targetZ,
                                      OrbitalTargetYMode targetYMode,
                                      int targetYValue,
                                      int directedRadius,
                                      @Nullable OrbitalDirectedEnergyDepth directedDepth) {

    private static final Codec<OrbitalAttackMode> MODE_CODEC = Codec.STRING.xmap(
            OrbitalAttackMode::valueOf,
            OrbitalAttackMode::name);
    private static final Codec<OrbitalTargetYMode> Y_MODE_CODEC = Codec.STRING.xmap(
            OrbitalTargetYMode::valueOf,
            OrbitalTargetYMode::name);
    private static final Codec<OrbitalDirectedEnergyDepth> DEPTH_CODEC = Codec.STRING.xmap(
            OrbitalDirectedEnergyDepth::valueOf,
            OrbitalDirectedEnergyDepth::name);

    public static final Codec<OrbitalFireControlDraft> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    MODE_CODEC.fieldOf("mode").forGetter(OrbitalFireControlDraft::mode),
                    ResourceLocation.CODEC.fieldOf("dimension_id").forGetter(OrbitalFireControlDraft::dimensionId),
                    Codec.INT.fieldOf("target_x").forGetter(OrbitalFireControlDraft::targetX),
                    Codec.INT.fieldOf("target_z").forGetter(OrbitalFireControlDraft::targetZ),
                    Y_MODE_CODEC.fieldOf("target_y_mode").forGetter(OrbitalFireControlDraft::targetYMode),
                    Codec.INT.fieldOf("target_y_value").forGetter(OrbitalFireControlDraft::targetYValue),
                    Codec.INT.fieldOf("directed_radius").forGetter(OrbitalFireControlDraft::directedRadius),
                    DEPTH_CODEC.optionalFieldOf("directed_depth").forGetter(draft -> Optional.ofNullable(draft.directedDepth)))
            .apply(instance, (mode, dimensionId, targetX, targetZ, targetYMode, targetYValue,
                              directedRadius, directedDepth) -> new OrbitalFireControlDraft(
                                      mode,
                                      dimensionId,
                                      targetX,
                                      targetZ,
                                      targetYMode,
                                      targetYValue,
                                      directedRadius,
                                      directedDepth.orElse(null))));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalFireControlDraft> STREAM_CODEC = StreamCodec.of(
            OrbitalFireControlDraft::encode,
            OrbitalFireControlDraft::decode);

    public OrbitalFireControlDraft {
        if (Math.abs((long) targetX) > 30_000_000L || Math.abs((long) targetZ) > 30_000_000L) {
            throw new IllegalArgumentException("Orbital target coordinates exceed the supported world boundary");
        }
        if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
            if (directedRadius < 1 || directedDepth == null) {
                throw new IllegalArgumentException("Directed-energy drafts require a positive radius and depth");
            }
        } else if (directedRadius != 0 || directedDepth != null) {
            throw new IllegalArgumentException("Non-directed orbital drafts cannot carry directed-energy geometry");
        }
    }

    /** Replaces only the fields owned by a tactical map selection. */
    public OrbitalFireControlDraft withMapTarget(ResourceLocation dimensionId, int targetX, int targetZ) {
        return new OrbitalFireControlDraft(
                this.mode,
                dimensionId,
                targetX,
                targetZ,
                this.targetYMode,
                this.targetYValue,
                this.directedRadius,
                this.directedDepth);
    }

    /** Creates the conservative kinetic draft exposed by a third-party map context action. */
    public static OrbitalFireControlDraft directKineticTarget(
                                                               ResourceLocation dimensionId,
                                                               int targetX,
                                                               int targetZ) {
        return new OrbitalFireControlDraft(
                OrbitalAttackMode.KINETIC,
                dimensionId,
                targetX,
                targetZ,
                OrbitalTargetYMode.SURFACE_OFFSET,
                0,
                0,
                null);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OrbitalFireControlDraft draft) {
        buffer.writeVarInt(draft.mode.wireCode());
        buffer.writeResourceLocation(draft.dimensionId);
        buffer.writeInt(draft.targetX);
        buffer.writeInt(draft.targetZ);
        buffer.writeVarInt(draft.targetYMode.wireCode());
        buffer.writeInt(draft.targetYValue);
        buffer.writeVarInt(draft.directedRadius);
        buffer.writeBoolean(draft.directedDepth != null);
        if (draft.directedDepth != null) {
            buffer.writeVarInt(draft.directedDepth.wireCode());
        }
    }

    private static OrbitalFireControlDraft decode(RegistryFriendlyByteBuf buffer) {
        OrbitalAttackMode mode = OrbitalAttackMode.fromWireCode(buffer.readVarInt());
        ResourceLocation dimensionId = buffer.readResourceLocation();
        int targetX = buffer.readInt();
        int targetZ = buffer.readInt();
        OrbitalTargetYMode targetYMode = OrbitalTargetYMode.fromWireCode(buffer.readVarInt());
        int targetYValue = buffer.readInt();
        int directedRadius = buffer.readVarInt();
        OrbitalDirectedEnergyDepth directedDepth = buffer.readBoolean() ?
                OrbitalDirectedEnergyDepth.fromWireCode(buffer.readVarInt()) : null;
        return new OrbitalFireControlDraft(
                mode,
                dimensionId,
                targetX,
                targetZ,
                targetYMode,
                targetYValue,
                directedRadius,
                directedDepth);
    }
}
