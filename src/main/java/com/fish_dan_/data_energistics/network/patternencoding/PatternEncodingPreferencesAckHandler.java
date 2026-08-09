package com.fish_dan_.data_energistics.network.patternencoding;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Routes preference acknowledgements through the registered client bridge without loading client classes on a server.
 */
public final class PatternEncodingPreferencesAckHandler {

    private PatternEncodingPreferencesAckHandler() {}

    /**
     * Dispatches one acknowledgement only on a physical client.
     */
    public static void handle(PatternEncodingPreferencesAckPayload payload, Player player) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        DataEnergisticsClientBridgeAccess.get().handlePatternEncodingPreferencesAck(payload, player);
    }
}
