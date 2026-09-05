package com.fish_dan_.data_energistics.orbital.control.protocol;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One atomic S2C state for the complete orbital control menu. */
public record OrbitalControlMenuSnapshot(
                                         OrbitalControlTerminalSnapshot terminal,
                                         OrbitalFireControlSessionSnapshot fireControl,
                                         OrbitalControlFeedback feedback) {

    public static final OrbitalControlMenuSnapshot EMPTY = new OrbitalControlMenuSnapshot(
            OrbitalControlTerminalSnapshot.EMPTY,
            OrbitalFireControlSessionSnapshot.IDLE,
            OrbitalControlFeedback.NONE);
    public static final Codec<OrbitalControlMenuSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    OrbitalControlTerminalSnapshot.CODEC.fieldOf("terminal").forGetter(OrbitalControlMenuSnapshot::terminal),
                    OrbitalFireControlSessionSnapshot.CODEC.fieldOf("fire_control").forGetter(OrbitalControlMenuSnapshot::fireControl),
                    OrbitalControlFeedback.CODEC.fieldOf("feedback").forGetter(OrbitalControlMenuSnapshot::feedback))
            .apply(instance, OrbitalControlMenuSnapshot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalControlMenuSnapshot> STREAM_CODEC = StreamCodec.of(
            (buffer, snapshot) -> {
                OrbitalControlTerminalSnapshot.STREAM_CODEC.encode(buffer, snapshot.terminal);
                OrbitalFireControlSessionSnapshot.STREAM_CODEC.encode(buffer, snapshot.fireControl);
                buffer.writeVarInt(snapshot.feedback.ordinal());
            },
            buffer -> new OrbitalControlMenuSnapshot(
                    OrbitalControlTerminalSnapshot.STREAM_CODEC.decode(buffer),
                    OrbitalFireControlSessionSnapshot.STREAM_CODEC.decode(buffer),
                    OrbitalControlFeedback.fromOrdinal(buffer.readVarInt())));
}
