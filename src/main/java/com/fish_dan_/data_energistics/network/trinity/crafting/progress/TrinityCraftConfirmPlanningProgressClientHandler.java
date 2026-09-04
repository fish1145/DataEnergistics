package com.fish_dan_.data_energistics.network.trinity.crafting.progress;

import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Client container/revision gate for confirmation-menu progress snapshots. */
public final class TrinityCraftConfirmPlanningProgressClientHandler {

    private TrinityCraftConfirmPlanningProgressClientHandler() {}

    /** Rejects packets for another menu or planning revision before they reach screen-local presentation state. */
    public static void receive(TrinityCraftConfirmPlanningProgressPayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || !(menu instanceof TrinityCraftConfirmMenuState state) ||
                state.data_energistics$planRevision() != payload.planRevision()) {
            return;
        }
        state.data_energistics$receivePlanningProgress(payload.planRevision(), payload.sequence(), payload.snapshot());
    }
}
