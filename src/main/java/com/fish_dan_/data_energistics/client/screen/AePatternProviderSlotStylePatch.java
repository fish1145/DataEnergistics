package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.menu.SlotSemantics;

import org.apache.logging.log4j.Logger;

/**
 * Default slot style patch for external AE2 pattern provider screens.
 * <p>
 * Data Energistics injects a redstone tuning upgrade slot into those menus, while the visual layout should continue to
 * come from AE2 and addon resource packs. This implementation adds only the missing {@code UPGRADE} slot entry.
 */
public final class AePatternProviderSlotStylePatch implements ScreenSlotStylePatch {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String UPGRADE_SLOT = SlotSemantics.UPGRADE.id();
    private static final String ENCODED_PATTERN_SLOT = SlotSemantics.ENCODED_PATTERN.id();
    private static final String STORAGE_SLOT = SlotSemantics.STORAGE.id();
    private static final int INLINE_UPGRADE_TOP_OFFSET = 22;

    private final String screenName;
    private final SlotPatchLayout layout;

    private AePatternProviderSlotStylePatch(String screenName, SlotPatchLayout layout) {
        this.screenName = screenName;
        this.layout = layout;
    }

    /**
     * Creates a patch for small pattern provider layouts where the upgrade slot sits between pattern and storage rows.
     *
     * @param screenName human-readable screen id for diagnostics
     * @return patch that derives the upgrade slot from the encoded pattern slot
     */
    public static ScreenSlotStylePatch inlineSingleUpgrade(String screenName) {
        return new AePatternProviderSlotStylePatch(screenName, SlotPatchLayout.INLINE_SINGLE);
    }

    /**
     * Creates a patch for tall pattern provider layouts where upgrade slots are shown as a right-side vertical strip.
     *
     * @param screenName human-readable screen id for diagnostics
     * @return patch that adds a right-side vertical upgrade slot style
     */
    public static ScreenSlotStylePatch rightPanelVerticalUpgrade(String screenName) {
        return new AePatternProviderSlotStylePatch(screenName, SlotPatchLayout.RIGHT_PANEL_VERTICAL);
    }

    @Override
    public void apply(ScreenStyle style) {
        if (style.getSlots().containsKey(UPGRADE_SLOT)) {
            return;
        }

        SlotPosition position = switch (this.layout) {
            case INLINE_SINGLE -> createInlineSingleUpgradePosition(style);
            case RIGHT_PANEL_VERTICAL -> createRightPanelVerticalUpgradePosition();
        };
        style.getSlots().put(UPGRADE_SLOT, position);
    }

    private SlotPosition createInlineSingleUpgradePosition(ScreenStyle style) {
        SlotPosition encodedPattern = requireSlot(style, ENCODED_PATTERN_SLOT);
        SlotPosition storage = requireSlot(style, STORAGE_SLOT);

        Integer left = encodedPattern.getLeft();
        Integer top = encodedPattern.getTop();
        Integer storageTop = storage.getTop();
        if (left == null || top == null || storageTop == null) {
            throw failure("Inline upgrade patch requires left/top encoded pattern and top storage anchors");
        }

        int upgradeTop = top + INLINE_UPGRADE_TOP_OFFSET;
        if (upgradeTop >= storageTop) {
            throw failure("Inline upgrade top " + upgradeTop + " overlaps storage top " + storageTop);
        }

        SlotPosition upgrade = new SlotPosition();
        upgrade.setLeft(left);
        upgrade.setTop(upgradeTop);
        return upgrade;
    }

    private SlotPosition createRightPanelVerticalUpgradePosition() {
        SlotPosition upgrade = new SlotPosition();
        upgrade.setRight(0);
        upgrade.setTop(0);
        upgrade.setGrid(SlotGridLayout.VERTICAL);
        return upgrade;
    }

    private SlotPosition requireSlot(ScreenStyle style, String slotId) {
        SlotPosition position = style.getSlots().get(slotId);
        if (position == null) {
            throw failure("Missing required slot style " + slotId);
        }
        return position;
    }

    private IllegalStateException failure(String message) {
        String fullMessage = "Failed to patch AE screen slot style " + this.screenName + ": " + message;
        LOGGER.error(fullMessage);
        return new IllegalStateException(fullMessage);
    }

    private enum SlotPatchLayout {
        INLINE_SINGLE,
        RIGHT_PANEL_VERTICAL
    }
}
