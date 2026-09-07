package com.fish_dan_.data_energistics.network.trinity.crafting.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.crafting.status.TrinityCraftingStatusAccess;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftingStatusPayload;

import appeng.client.gui.me.crafting.CraftingCPUScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Active-screen boundary for exact status updates; no static inventory or menu references are retained. */
public final class TrinityCraftingStatusClientHandler {

    private TrinityCraftingStatusClientHandler() {}

    /** Receives on the client thread and leaves the last complete table intact if a malformed update is rejected. */
    public static void receive(TrinityCraftingStatusPayload payload, Player player) {
        if (!(Minecraft.getInstance().screen instanceof CraftingCPUScreen<?> screen) ||
                player.containerMenu != screen.getMenu() || player.containerMenu.containerId != payload.containerId()) {
            return;
        }
        var state = ((TrinityCraftingStatusAccess) screen).data_energistics$craftingStatusState();
        try {
            state.receive(payload, screen::postUpdate);
        } catch (IllegalArgumentException exception) {
            state.onNativeUpdate();
            Data_Energistics.LOGGER.error("Rejected Trinity CPU status update for container {} sequence {}",
                    payload.containerId(), payload.sequence(), exception);
        }
    }
}
