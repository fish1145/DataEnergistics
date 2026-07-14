package com.fish_dan_.data_energistics.gui.ldlib2;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import org.jetbrains.annotations.Nullable;

/** ModularUI variant that closes hosted windows before LDLib2 recursively releases the root tree. */
public final class HostModularUI extends ModularUI {

    private final HostUiExtension hostUi;
    private boolean removed;

    /**
     * Creates a ModularUI whose host extension shares the same root tree and lifetime.
     *
     * @param ui     complete LDLib2 UI
     * @param player owning player, when this UI is attached to a menu
     * @param hostUi child UI extension created for the supplied UI root
     */
    HostModularUI(UI ui, @Nullable Player player, HostUiExtension hostUi) {
        super(ui, player);
        this.hostUi = hostUi;
    }

    /**
     * Returns the child UI extension that Screen input and removal hooks must route through.
     *
     * @return extension owned by this ModularUI lifetime
     */
    public HostUiExtension hostUi() {
        return this.hostUi;
    }

    /**
     * Routes Screen keyboard input to the hosted-window stack before a legacy screen consumes it.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  platform scan code
     * @param modifiers active modifier mask
     * @return whether one hosted window consumed the key
     */
    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        return this.hostUi.handleKeyPressed(keyCode, scanCode, modifiers);
    }

    /** Closes child trees once before the first LDLib2 root removal and ignores duplicate mixin callbacks. */
    @Override
    public void onRemoved() {
        if (this.removed) {
            return;
        }
        this.removed = true;
        Throwable failure = null;
        try {
            this.hostUi.dispose();
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to dispose hosted LDLib2 windows", exception);
            failure = exception;
        }
        try {
            super.onRemoved();
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to release the LDLib2 host root", exception);
            failure = mergeFailures(failure, exception);
        }
        rethrow(failure);
    }

    /** Preserves the first removal failure and reports later cleanup failures as suppressed context. */
    private static Throwable mergeFailures(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** Rethrows only the unchecked failure types collected by {@link #onRemoved()}. */
    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
