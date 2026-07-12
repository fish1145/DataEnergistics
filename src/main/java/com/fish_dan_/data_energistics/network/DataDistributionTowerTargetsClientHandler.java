package com.fish_dan_.data_energistics.network;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.lang.ref.WeakReference;

/**
 * Client-side delivery boundary for validated Data Distribution Tower target batches.
 */
public final class DataDistributionTowerTargetsClientHandler {

    /**
     * Reassembler scoped to the currently active tower menu instance.
     */
    private static final DataDistributionTowerTargetsAssembler ASSEMBLER = new DataDistributionTowerTargetsAssembler();
    /**
     * Weak menu identity used to reset revisions when Minecraft replaces the active container.
     */
    private static WeakReference<DataDistributionTowerTargetsReceiver> activeReceiver = new WeakReference<>(null);

    /**
     * Prevents construction of this static packet handler.
     */
    private DataDistributionTowerTargetsClientHandler() {}

    /**
     * Delivers a batch to the active matching menu and publishes it only after complete assembly.
     *
     * @param payload target batch received from the server
     * @param player  client player supplied by the payload context
     */
    public static synchronized void receive(DataDistributionTowerTargetsPayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || !(menu instanceof DataDistributionTowerTargetsReceiver receiver)) {
            return;
        }

        if (activeReceiver.get() != receiver) {
            ASSEMBLER.clear();
            activeReceiver = new WeakReference<>(receiver);
        }
        ASSEMBLER.accept(payload).ifPresent(receiver::receiveDataDistributionTowerTargets);
    }
}
