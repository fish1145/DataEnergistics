package com.fish_dan_.data_energistics.orbital.control.protocol;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jspecify.annotations.Nullable;

/** Immutable selected-weapon state; the client chooses which rows and units to display. */
public record OrbitalHudSnapshot(@Nullable WeaponEntry weapon) {

    public static final OrbitalHudSnapshot EMPTY = new OrbitalHudSnapshot(null);
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalHudSnapshot> STREAM_CODEC = StreamCodec.of(
            (buffer, snapshot) -> {
                buffer.writeBoolean(snapshot.weapon != null);
                if (snapshot.weapon != null) {
                    WeaponEntry.STREAM_CODEC.encode(buffer, snapshot.weapon);
                }
            },
            buffer -> new OrbitalHudSnapshot(buffer.readBoolean() ? WeaponEntry.STREAM_CODEC.decode(buffer) : null));
}
