package com.fish_dan_.data_energistics.orbital.control.protocol;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/** Bounded C2S commands accepted by one open, server-authoritative orbital control menu. */
public sealed interface OrbitalControlIntent permits
                                             OrbitalControlIntent.CycleWeapon,
                                             OrbitalControlIntent.CancelOrAbortMode,
                                             OrbitalControlIntent.RequestPreview,
                                             OrbitalControlIntent.StartHold,
                                             OrbitalControlIntent.ReleaseHold,
                                             OrbitalControlIntent.CancelHold,
                                             OrbitalControlIntent.DiscardPreview {

    Codec<OrbitalControlIntent> CODEC = Kind.CODEC.dispatch(
            "type",
            OrbitalControlIntent::kind,
            Kind::codec);
    StreamCodec<RegistryFriendlyByteBuf, OrbitalControlIntent> STREAM_CODEC = StreamCodec.of(
            OrbitalControlIntent::encode,
            OrbitalControlIntent::decode);

    private Kind kind() {
        return switch (this) {
            case CycleWeapon ignored -> Kind.CYCLE_WEAPON;
            case CancelOrAbortMode ignored -> Kind.CANCEL_OR_ABORT_MODE;
            case RequestPreview ignored -> Kind.REQUEST_PREVIEW;
            case StartHold ignored -> Kind.START_HOLD;
            case ReleaseHold ignored -> Kind.RELEASE_HOLD;
            case CancelHold ignored -> Kind.CANCEL_HOLD;
            case DiscardPreview ignored -> Kind.DISCARD_PREVIEW;
        };
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OrbitalControlIntent intent) {
        Kind kind = intent.kind();
        buffer.writeVarInt(kind.ordinal());
        switch (intent) {
            case CycleWeapon cycle -> buffer.writeBoolean(cycle.forward);
            case CancelOrAbortMode cancel -> buffer.writeVarInt(cancel.mode.wireCode());
            case RequestPreview preview -> OrbitalFireControlDraft.STREAM_CODEC.encode(buffer, preview.draft);
            case StartHold start -> buffer.writeUUID(start.nonce);
            case ReleaseHold release -> buffer.writeUUID(release.nonce);
            case CancelHold ignored -> {}
            case DiscardPreview ignored -> {}
        }
    }

    private static OrbitalControlIntent decode(RegistryFriendlyByteBuf buffer) {
        Kind kind = Kind.fromOrdinal(buffer.readVarInt());
        return switch (kind) {
            case CYCLE_WEAPON -> new CycleWeapon(buffer.readBoolean());
            case CANCEL_OR_ABORT_MODE -> new CancelOrAbortMode(
                    OrbitalAttackMode.fromWireCode(buffer.readVarInt()));
            case REQUEST_PREVIEW -> new RequestPreview(OrbitalFireControlDraft.STREAM_CODEC.decode(buffer));
            case START_HOLD -> new StartHold(buffer.readUUID());
            case RELEASE_HOLD -> new ReleaseHold(buffer.readUUID());
            case CANCEL_HOLD -> CancelHold.INSTANCE;
            case DISCARD_PREVIEW -> DiscardPreview.INSTANCE;
        };
    }

    record CycleWeapon(boolean forward) implements OrbitalControlIntent {

        private static final MapCodec<CycleWeapon> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(Codec.BOOL.fieldOf("forward").forGetter(CycleWeapon::forward))
                .apply(instance, CycleWeapon::new));
    }

    record CancelOrAbortMode(OrbitalAttackMode mode) implements OrbitalControlIntent {

        private static final MapCodec<CancelOrAbortMode> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(Codec.STRING.xmap(OrbitalAttackMode::valueOf, OrbitalAttackMode::name)
                        .fieldOf("mode")
                        .forGetter(CancelOrAbortMode::mode))
                .apply(instance, CancelOrAbortMode::new));
    }

    record RequestPreview(OrbitalFireControlDraft draft) implements OrbitalControlIntent {

        private static final MapCodec<RequestPreview> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(OrbitalFireControlDraft.CODEC.fieldOf("draft").forGetter(RequestPreview::draft))
                .apply(instance, RequestPreview::new));
    }

    record StartHold(UUID nonce) implements OrbitalControlIntent {

        private static final MapCodec<StartHold> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(UUIDUtil.CODEC.fieldOf("nonce").forGetter(StartHold::nonce))
                .apply(instance, StartHold::new));
    }

    record ReleaseHold(UUID nonce) implements OrbitalControlIntent {

        private static final MapCodec<ReleaseHold> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(UUIDUtil.CODEC.fieldOf("nonce").forGetter(ReleaseHold::nonce))
                .apply(instance, ReleaseHold::new));
    }

    enum CancelHold implements OrbitalControlIntent {

        INSTANCE;

        private static final MapCodec<CancelHold> CODEC = Codec.BOOL
                .optionalFieldOf("present", true)
                .xmap(ignored -> INSTANCE, ignored -> true);
    }

    enum DiscardPreview implements OrbitalControlIntent {

        INSTANCE;

        private static final MapCodec<DiscardPreview> CODEC = Codec.BOOL
                .optionalFieldOf("present", true)
                .xmap(ignored -> INSTANCE, ignored -> true);
    }

    enum Kind {

        CYCLE_WEAPON(CycleWeapon.CODEC),
        CANCEL_OR_ABORT_MODE(CancelOrAbortMode.CODEC),
        REQUEST_PREVIEW(RequestPreview.CODEC),
        START_HOLD(StartHold.CODEC),
        RELEASE_HOLD(ReleaseHold.CODEC),
        CANCEL_HOLD(CancelHold.CODEC),
        DISCARD_PREVIEW(DiscardPreview.CODEC);

        private static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);

        private final MapCodec<? extends OrbitalControlIntent> codec;

        Kind(MapCodec<? extends OrbitalControlIntent> codec) {
            this.codec = codec;
        }

        private MapCodec<? extends OrbitalControlIntent> codec() {
            return this.codec;
        }

        private static Kind fromOrdinal(int ordinal) {
            Kind[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IllegalArgumentException("Unknown orbital control intent ordinal: " + ordinal);
            }
            return values[ordinal];
        }
    }
}
