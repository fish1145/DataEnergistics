package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.network.orbital.projection.OrbitalProjectionVisualsPayload;
import com.fish_dan_.data_energistics.network.orbital.visual.OrbitalAttackVisualsPayload;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Publishes a complete public visual baseline to each online player's current dimension at a bounded cadence. */
public final class OrbitalAttackVisualTicker {

    private static final long PUBLISH_INTERVAL = 5L;

    public OrbitalAttackVisualTicker() {}

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        if (gameTime % PUBLISH_INTERVAL != 0L) {
            return;
        }
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        Map<ResourceLocation, List<OrbitalAttackVisualsPayload>> attackBatches = new HashMap<>();
        Map<ResourceLocation, List<OrbitalProjectionVisualsPayload>> projectionBatches = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceLocation dimensionId = player.level().dimension().location();
            List<OrbitalAttackVisualsPayload> dimensionAttacks = attackBatches.computeIfAbsent(
                    dimensionId,
                    ignored -> OrbitalAttackVisualsPayload.batches(
                            gameTime,
                            dimensionId,
                            attacks.publicVisuals(player.serverLevel(), gameTime)));
            List<OrbitalProjectionVisualsPayload> dimensionProjections = projectionBatches.computeIfAbsent(
                    dimensionId,
                    ignored -> OrbitalProjectionVisualsPayload.batches(
                            gameTime,
                            dimensionId,
                            weapons.publicVisualProjections(player.serverLevel(), gameTime)));
            for (OrbitalAttackVisualsPayload payload : dimensionAttacks) {
                PacketDistributor.sendToPlayer(player, payload);
            }
            for (OrbitalProjectionVisualsPayload payload : dimensionProjections) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
