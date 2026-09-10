package com.fish_dan_.data_energistics.item.powered.cannon.rail;

import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.AmmunitionRules;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;

import appeng.api.stacks.AEKey;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.UUID;

/** Persisted escrow. No runtime entity references or client-controlled damage values are serialized. */
public record RailSession(UUID id, AEKey resource, int cards, int elapsed) {

    public static final Codec<RailSession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(RailSession::id),
            AEKey.CODEC.fieldOf("resource").forGetter(RailSession::resource),
            Codec.intRange(0, 2).fieldOf("cards").forGetter(RailSession::cards),
            Codec.intRange(0, 270).fieldOf("elapsed").forGetter(RailSession::elapsed)).apply(instance, RailSession::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailSession> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RailSession::id, AEKey.STREAM_CODEC, RailSession::resource,
            ByteBufCodecs.VAR_INT, RailSession::cards, ByteBufCodecs.VAR_INT, RailSession::elapsed, RailSession::new);

    public RailSession {
        AmmunitionRules.checkCards(cards);
        RailAmmunition ammo = RailAmmunition.fromKey(resource);
        if (ammo == null || elapsed < 0 || elapsed > ammo.duration()) throw new IllegalArgumentException("Invalid rail escrow");
    }

    public RailAmmunition ammunition() {
        return Objects.requireNonNull(RailAmmunition.fromKey(resource));
    }

    public RailSession advance() {
        return new RailSession(id, resource, cards, elapsed + 1);
    }
}
