package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.menu.SlotSemantics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ScreenSlotStylePatchImplTest {

    private static final String UPGRADE_SLOT = SlotSemantics.UPGRADE.id();
    private static final String ENCODED_PATTERN_SLOT = SlotSemantics.ENCODED_PATTERN.id();
    private static final String STORAGE_SLOT = SlotSemantics.STORAGE.id();

    @Test
    void keepsExistingUpgradeSlot() {
        ScreenStyle style = new ScreenStyle();
        SlotPosition existingUpgrade = position(11, 13);
        style.getSlots().put(UPGRADE_SLOT, existingUpgrade);

        ScreenSlotStylePatchImpl.inlineSingleUpgrade("test:inline").apply(style);

        assertSame(existingUpgrade, style.getSlots().get(UPGRADE_SLOT), "Existing UPGRADE slot style should not be replaced");
    }

    @Test
    void derivesInlineUpgradeSlot() {
        ScreenStyle style = new ScreenStyle();
        style.getSlots().put(ENCODED_PATTERN_SLOT, position(8, 45));
        style.getSlots().put(STORAGE_SLOT, position(8, 97));

        ScreenSlotStylePatchImpl.inlineSingleUpgrade("test:inline").apply(style);

        SlotPosition upgrade = style.getSlots().get(UPGRADE_SLOT);
        assertNotNull(upgrade, "Inline patch should add an UPGRADE slot style");
        assertEquals(8, upgrade.getLeft(), "Inline UPGRADE left should follow ENCODED_PATTERN left");
        assertEquals(67, upgrade.getTop(), "Inline UPGRADE top should sit below ENCODED_PATTERN");
        assertNull(upgrade.getGrid(), "Inline UPGRADE should not define a grid layout");
    }

    @Test
    void addsRightPanelUpgradeSlot() {
        ScreenStyle style = new ScreenStyle();

        ScreenSlotStylePatchImpl.rightPanelVerticalUpgrade("test:right_panel").apply(style);

        SlotPosition upgrade = style.getSlots().get(UPGRADE_SLOT);
        assertNotNull(upgrade, "Right panel patch should add an UPGRADE slot style");
        assertEquals(0, upgrade.getRight(), "Right panel UPGRADE should be anchored to the right edge");
        assertEquals(0, upgrade.getTop(), "Right panel UPGRADE should start at the top edge");
        assertEquals(SlotGridLayout.VERTICAL, upgrade.getGrid(), "Right panel UPGRADE should use vertical layout");
    }

    @Test
    void missingInlineAnchorFails() {
        ScreenStyle style = new ScreenStyle();
        style.getSlots().put(STORAGE_SLOT, position(8, 97));

        assertThrows(
                IllegalStateException.class,
                () -> ScreenSlotStylePatchImpl.inlineSingleUpgrade("test:inline").apply(style),
                "Inline patch should fail when ENCODED_PATTERN is missing");
    }

    private static SlotPosition position(int left, int top) {
        SlotPosition position = new SlotPosition();
        position.setLeft(left);
        position.setTop(top);
        return position;
    }
}
