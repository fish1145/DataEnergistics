package com.fish_dan_.data_energistics.integration.ae2lt;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import org.apache.maven.artifact.versioning.ArtifactVersion;

/**
 * Reports unsupported AE2 Lightning Tech installations without linking against AE2LT classes.
 */
public final class Ae2LtCompatibilityWarning {

    private static final String MESSAGE_KEY = "message.data_energistics.compatibility.ae2lt.unsupported";

    /**
     * Logs the unsupported combination once for each server start.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Ae2LtVersionPolicy.unsupportedInstalledVersion().ifPresent(version -> Data_Energistics.LOGGER.warn(
                "Detected unsupported AE2 Lightning Tech version {} above compatibility boundary {}; " + "Data Energistics AE2LT compatibility paths are disabled.",
                version,
                Ae2LtVersionPolicy.maximumSupportedVersion()));
    }

    /**
     * Warns each player as they enter a world that uses an unsupported AE2LT version.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Ae2LtVersionPolicy.unsupportedInstalledVersion().ifPresent(version -> sendWarning(player, version));
    }

    private static void sendWarning(ServerPlayer player, ArtifactVersion version) {
        player.sendSystemMessage(Component.translatable(
                MESSAGE_KEY,
                version.toString(),
                Ae2LtVersionPolicy.maximumSupportedVersion().toString()).withStyle(ChatFormatting.RED));
    }
}
