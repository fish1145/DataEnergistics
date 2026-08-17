package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.network.orbital.visual.OrbitalAttackVisualsPayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (OrbitalAttackVisualsPayload payload : OrbitalAttackVisualsPayload.batches(
                    gameTime,
                    player.level().dimension().location(),
                    attacks.publicVisuals(player.serverLevel(), gameTime))) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
