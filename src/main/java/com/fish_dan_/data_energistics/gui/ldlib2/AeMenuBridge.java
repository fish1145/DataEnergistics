package com.fish_dan_.data_energistics.gui.ldlib2;

import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/**
 * Mounts an LDLib2 UI on an existing AE2 menu without rebuilding or reindexing its slots.
 *
 * <p>
 * The bridge owns the exceptional existing-slot registration path required by LDLib2 2.2.8. Callers create every
 * wrapper through {@link #wrap(Slot)}, add those wrappers to the UI tree, and mount the completed tree exactly once.
 */
public interface AeMenuBridge {

    /**
     * Creates a bridge for one menu construction.
     *
     * @param menu AE2 menu whose existing slots and synchronization contract must be preserved
     * @return a new unmounted bridge
     */
    static AeMenuBridge create(AEBaseMenu menu) {
        return AeMenuBridgeImpl.create(menu);
    }

    /**
     * Creates the sole LDLib2 wrapper allowed to represent the supplied existing menu slot.
     *
     * @param slot slot already owned by the bridged menu
     * @return wrapper that must be inserted into the UI tree before mounting
     */
    AeItemSlot wrap(Slot slot);

    /**
     * Attaches a completed UI and registers all wrapped slots without adding them to the menu again.
     *
     * <p>
     * An exception raised after LDLib2 starts attaching the tree leaves this bridge terminal. The bridge invokes the
     * complete ModularUI removal lifecycle before preserving that failure; the caller must still abort opening and
     * discard both the menu and UI instead of attempting to reuse their partially initialized ownership references.
     *
     * @param modularUI completed UI created for the bridged menu player
     */
    void mount(ModularUI modularUI);
}
