package com.fish_dan_.data_energistics.network.trinity.crafting.client;

import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.network.trinity.crafting.assembly.TrinityCraftConfirmCycleAssembler;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCyclePayload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

/** Client-side container/revision boundary for complete Trinity confirmation summaries. */
public final class TrinityCraftConfirmCycleClientHandler {

    private static final TrinityCraftConfirmCycleAssembler ASSEMBLER = new TrinityCraftConfirmCycleAssembler();
    private static WeakReference<@Nullable TrinityCraftConfirmMenuState> activeState = new WeakReference<>(null);

    private TrinityCraftConfirmCycleClientHandler() {}

    /** Delivers matching batches to the current menu and atomically publishes a complete revision. */
    public static synchronized void receive(TrinityCraftConfirmCyclePayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || !(menu instanceof TrinityCraftConfirmMenuState state) || state.data_energistics$planRevision() != payload.revision()) {
            return;
        }

        if (activeState.get() != state) {
            ASSEMBLER.clear();
            activeState = new WeakReference<>(state);
        }
        ASSEMBLER.accept(payload).ifPresent(snapshot -> state.data_energistics$receiveCycleSummary(snapshot.revision(), snapshot.summary()));
    }
}
