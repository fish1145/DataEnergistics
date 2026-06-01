package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Optional;

public final class SingleUpgradeSlotRelocator {

    private static final Optional<VarHandle> SLOT_X_FIELD = resolveField("x");
    private static final Optional<VarHandle> SLOT_Y_FIELD = resolveField("y");

    private SingleUpgradeSlotRelocator() {}

    public static void relocateIfSingle(AEBaseScreen<?> screen, int x, int y) {
        List<Slot> upgradeSlots = screen.getMenu().getSlots(SlotSemantics.UPGRADE);
        if (upgradeSlots.size() != 1) {
            return;
        }

        Slot slot = upgradeSlots.getFirst();
        if (!ReflectionAccess.setField(SLOT_X_FIELD, slot, x) || !ReflectionAccess.setField(SLOT_Y_FIELD, slot, y)) {
            throw new IllegalStateException("Could not relocate single upgrade slot");
        }
    }

    private static Optional<VarHandle> resolveField(String name) {
        Optional<VarHandle> field = ReflectionAccess.findField(Slot.class, name);
        if (field.isEmpty()) {
            throw new IllegalStateException("Could not resolve Slot." + name);
        }
        return field;
    }
}
