package com.fish_dan_.data_energistics.client.screen;

import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;

import java.lang.reflect.Field;
import java.util.List;

public final class SingleUpgradeSlotRelocator {

    private static final Field SLOT_X_FIELD = resolveField("x");
    private static final Field SLOT_Y_FIELD = resolveField("y");

    private SingleUpgradeSlotRelocator() {}

    public static void relocateIfSingle(AEBaseScreen<?> screen, int x, int y) {
        List<Slot> upgradeSlots = screen.getMenu().getSlots(SlotSemantics.UPGRADE);
        if (upgradeSlots.size() != 1) {
            return;
        }

        Slot slot = upgradeSlots.getFirst();
        try {
            SLOT_X_FIELD.setInt(slot, x);
            SLOT_Y_FIELD.setInt(slot, y);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not relocate single upgrade slot", e);
        }
    }

    private static Field resolveField(String name) {
        try {
            Field field = Slot.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve Slot." + name, e);
        }
    }
}
