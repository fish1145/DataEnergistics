package com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal implementation that contains every direct use of LDLib2's existing-slot mapping hook.
 */
final class LdlibAeMenuBridge implements AeMenuBridge {

    private static final String FAILURE_PREFIX = "AE/LDLib2 menu bridge invariant failed: ";

    private final AEBaseMenu menu;
    private final IModularUIHolderMenu holder;
    private final Map<Slot, AeItemSlot> wrappersBySlot = new IdentityHashMap<>();
    private final Set<AeItemSlot> wrappers = Collections.newSetFromMap(new IdentityHashMap<>());
    private MountState mountState = MountState.NEW;

    private LdlibAeMenuBridge(AEBaseMenu menu, IModularUIHolderMenu holder) {
        this.menu = menu;
        this.holder = holder;
    }

    /**
     * Creates the implementation only after proving that LDLib2 enhanced the target menu.
     */
    static AeMenuBridge create(AEBaseMenu menu) {
        if (menu == null) {
            throw violation("menu must not be null");
        }
        if (!(menu instanceof IModularUIHolderMenu holder)) {
            throw violation("menu is not an IModularUIHolderMenu: " + menu.getClass().getName());
        }
        if (holder.getModularUI() != null) {
            throw violation("menu already has a ModularUI: " + menu.getClass().getName());
        }
        return new LdlibAeMenuBridge(menu, holder);
    }

    @Override
    public AeItemSlot wrap(Slot slot) {
        ensureNotMounted();
        int menuIndex = validateExactMenuMembership(slot);
        if (this.holder.getItemSlot(slot) != null) {
            throw violation("slot " + menuIndex + " already has an LDLib2 mapping");
        }
        if (this.wrappersBySlot.containsKey(slot)) {
            throw violation("slot " + menuIndex + " was wrapped more than once");
        }

        AeItemSlot wrapper = new AeItemSlot(slot);
        this.wrappersBySlot.put(slot, wrapper);
        this.wrappers.add(wrapper);
        return wrapper;
    }

    @Override
    public void mount(ModularUI modularUI) {
        ensureNotMounted();
        validateMountTarget(modularUI);
        List<AeItemSlot> wrappersInMenuOrder = validateUiTree(modularUI.ui.rootElement);
        List<Slot> originalSlots = List.copyOf(this.menu.slots);
        this.mountState = MountState.MOUNTING;

        try {
            this.holder.setModularUI(modularUI);
            validateMountedReferences(modularUI);
            validateSlotListUnchanged(originalSlots);
            for (AeItemSlot wrapper : wrappersInMenuOrder) {
                this.holder.ldlib2$addSlot(wrapper);
                if (this.holder.getItemSlot(wrapper.getSlot()) != wrapper) {
                    throw violation("LDLib2 did not retain the wrapper for slot " + wrapper.getSlot().index);
                }
            }
            this.mountState = MountState.MOUNTED;
        } catch (RuntimeException | Error exception) {
            this.mountState = MountState.FAILED;
            Data_Energistics.LOGGER.error(
                    "Failed to mount LDLib2 UI on AE menu {}; discard this menu and UI instance",
                    this.menu.getClass().getName(),
                    exception);
            try {
                modularUI.onRemoved();
            } catch (RuntimeException | Error cleanupFailure) {
                Data_Energistics.LOGGER.error(
                        "Failed to release the incomplete LDLib2 UI mounted on AE menu {}",
                        this.menu.getClass().getName(),
                        cleanupFailure);
                if (cleanupFailure != exception) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    /**
     * Rejects operations that would replace or mutate an already mounted UI.
     */
    private void ensureNotMounted() {
        if (this.mountState != MountState.NEW || this.holder.getModularUI() != null) {
            throw violation("menu bridge is not reusable in state " + this.mountState);
        }
    }

    /**
     * Validates the bidirectional ownership prerequisites before LDLib2 mutates either object.
     */
    private void validateMountTarget(ModularUI modularUI) {
        if (modularUI == null) {
            throw violation("ModularUI must not be null");
        }
        if (modularUI.getMenu() != null) {
            throw violation("ModularUI is already attached to a menu");
        }
        if (modularUI.player != this.menu.getPlayer()) {
            throw violation("ModularUI player does not own the target AE menu");
        }
    }

    /**
     * Proves that every non-local item element is one of this bridge's wrappers and returns them in menu slot order.
     */
    private List<AeItemSlot> validateUiTree(UIElement root) {
        Set<UIElement> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AeItemSlot> discoveredWrappers = Collections.newSetFromMap(new IdentityHashMap<>());
        inspectElement(root, visited, discoveredWrappers);
        if (discoveredWrappers.size() != this.wrappers.size() || !discoveredWrappers.containsAll(this.wrappers)) {
            throw violation("every wrapped slot must occur exactly once in the mounted UI tree");
        }

        List<AeItemSlot> ordered = new ArrayList<>(this.wrappers.size());
        for (Slot slot : this.menu.slots) {
            AeItemSlot wrapper = this.wrappersBySlot.get(slot);
            if (wrapper != null) {
                validateExactMenuMembership(slot);
                if (this.holder.getItemSlot(slot) != null) {
                    throw violation("slot " + slot.index + " acquired an LDLib2 mapping before mount");
                }
                ordered.add(wrapper);
            }
        }
        return ordered;
    }

    /**
     * Recursively rejects duplicate elements and ordinary ItemSlots that would append a new menu slot during mount.
     */
    private void inspectElement(UIElement element, Set<UIElement> visited, Set<AeItemSlot> discoveredWrappers) {
        if (!visited.add(element)) {
            throw violation("the mounted UI tree contains the same element more than once");
        }
        if (element instanceof AeItemSlot wrapper) {
            if (!this.wrappers.contains(wrapper) || !discoveredWrappers.add(wrapper)) {
                throw violation("the UI tree contains an AeItemSlot owned by another bridge");
            }
        } else if (element instanceof ItemSlot itemSlot && !(itemSlot.getSlot() instanceof LocalSlot)) {
            throw violation("non-local ItemSlot must be created through AeMenuBridge.wrap");
        }
        for (UIElement child : element.getChildren()) {
            inspectElement(child, visited, discoveredWrappers);
        }
    }

    /**
     * Ensures the slot has one exact-identity position and that its index still names that position.
     */
    private int validateExactMenuMembership(Slot slot) {
        if (slot == null) {
            throw violation("slot must not be null");
        }
        int foundIndex = -1;
        int occurrences = 0;
        for (int index = 0; index < this.menu.slots.size(); index++) {
            if (this.menu.slots.get(index) == slot) {
                foundIndex = index;
                occurrences++;
            }
        }
        if (occurrences != 1) {
            throw violation("slot must occur by identity exactly once in the target menu; found " + occurrences);
        }
        if (slot.index != foundIndex) {
            throw violation("slot index " + slot.index + " does not match menu position " + foundIndex);
        }
        return foundIndex;
    }

    /**
     * Confirms that LDLib2 established the intended two-way menu relationship.
     */
    private void validateMountedReferences(ModularUI modularUI) {
        if (this.holder.getModularUI() != modularUI || modularUI.getMenu() != this.menu) {
            throw violation("LDLib2 did not establish the expected menu/UI references");
        }
    }

    /**
     * Confirms that mounting did not append, reorder, or replace any AE2 slot.
     */
    private void validateSlotListUnchanged(List<Slot> originalSlots) {
        if (this.menu.slots.size() != originalSlots.size()) {
            throw violation("mount changed menu slot count from " + originalSlots.size() + " to " +
                    this.menu.slots.size());
        }
        for (int index = 0; index < originalSlots.size(); index++) {
            if (this.menu.slots.get(index) != originalSlots.get(index)) {
                throw violation("mount replaced or reordered menu slot " + index);
            }
        }
    }

    /**
     * Logs each rejected invariant before returning its fail-fast exception.
     */
    private static IllegalStateException violation(String message) {
        Data_Energistics.LOGGER.error("{}{}", FAILURE_PREFIX, message);
        return new IllegalStateException(message);
    }

    /**
     * Lifecycle states make a failed, partly attached LDLib2 tree explicitly terminal.
     */
    private enum MountState {
        NEW,
        MOUNTING,
        MOUNTED,
        FAILED
    }
}
