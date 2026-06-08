package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.util.ServerTickDelayQueue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class UniversalTerminalMenuSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private UniversalTerminalMenuSupport() {}

    public static void switchTerminal(UniversalTerminalPart host, Player player) {
        if (host.switchToNextTerminal(player, false)) {
            reopenTerminal(host, player);
        }
    }

    public static void switchTerminal(UniversalTerminalPart host, Player player, String terminalName) {
        if (host.switchToTerminal(terminalName, player, false)) {
            reopenTerminal(host, player);
        }
    }

    public static void cycleTerminal(UniversalTerminalPart host, Player player, boolean reverse) {
        LOGGER.info("UniversalTerminalMenuSupport.cycleTerminal called reverse={} player={}", reverse, player.getName().getString());
        if (host.cycleTerminal(player, reverse, false)) {
            LOGGER.info("UniversalTerminalMenuSupport.cycleTerminal reopening active terminal");
            reopenTerminal(host, player);
        } else {
            LOGGER.info("UniversalTerminalMenuSupport.cycleTerminal no switch performed");
        }
    }

    private static void reopenTerminal(UniversalTerminalPart host, Player player) {
        player.closeContainer();
        if (player instanceof ServerPlayer serverPlayer) {
            ServerTickDelayQueue.runNextServerTick(serverPlayer.server, () -> {
                if (!serverPlayer.hasDisconnected() && !serverPlayer.isRemoved() && host.getLevel() != null) {
                    host.openActiveTerminal(serverPlayer, false);
                }
            });
            return;
        }

        host.openActiveTerminal(player, false);
    }
}
