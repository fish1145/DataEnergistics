package com.fish_dan_.data_energistics.gui.ldlib2;

import com.fish_dan_.data_energistics.Data_Energistics;

/**
 * Places the existing AE2 player inventory and hotbar slot contents inside one LDLib2 host UI.
 *
 * @param slotLeft     top-left X coordinate of the first slot's 16 by 16 item content
 * @param inventoryTop top-left Y coordinate of the first main-inventory slot's item content
 * @param hotbarTop    top-left Y coordinate of the first hotbar slot's item content
 */
public record AePlayerInventoryLayout(int slotLeft, int inventoryTop, int hotbarTop) {

    private static final int INVENTORY_ROW_COUNT = 3;
    private static final int SLOT_PITCH = 18;

    /**
     * Rejects layouts that would place the two semantic groups outside one coherent slot panel.
     */
    public AePlayerInventoryLayout {
        if (slotLeft < 1 || inventoryTop < 1) {
            throw invalid("slot content coordinates must leave room for their one-pixel border");
        }
        int minimumHotbarTop = inventoryTop + INVENTORY_ROW_COUNT * SLOT_PITCH;
        if (hotbarTop < minimumHotbarTop) {
            throw invalid("hotbar top must not overlap the three-row player inventory");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error("Invalid AE player inventory LDLib2 layout: {}", message);
        return new IllegalArgumentException(message);
    }
}
