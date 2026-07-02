package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.menu.SlotSemantics;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ScreenSlotStylePatchImplTest {

    private static final String UPGRADE_SLOT = SlotSemantics.UPGRADE.id();
    private static final String ENCODED_PATTERN_SLOT = SlotSemantics.ENCODED_PATTERN.id();
    private static final String STORAGE_SLOT = SlotSemantics.STORAGE.id();

    private ScreenSlotStylePatchImplTest() {}

    @TestHolder("screen_slot_style_patch_keeps_existing_upgrade_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsExistingUpgradeSlot(GameTestHelper helper) {
        ScreenStyle style = new ScreenStyle();
        SlotPosition existingUpgrade = position(11, 13);
        style.getSlots().put(UPGRADE_SLOT, existingUpgrade);

        ScreenSlotStylePatchImpl.inlineSingleUpgrade("test:inline").apply(style);

        helper.assertValueEqual(style.getSlots().get(UPGRADE_SLOT), existingUpgrade, "Existing UPGRADE slot style should not be replaced");
        helper.succeed();
    }

    @TestHolder("screen_slot_style_patch_derives_inline_upgrade_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void derivesInlineUpgradeSlot(GameTestHelper helper) {
        ScreenStyle style = new ScreenStyle();
        style.getSlots().put(ENCODED_PATTERN_SLOT, position(8, 45));
        style.getSlots().put(STORAGE_SLOT, position(8, 97));

        ScreenSlotStylePatchImpl.inlineSingleUpgrade("test:inline").apply(style);

        SlotPosition upgrade = style.getSlots().get(UPGRADE_SLOT);
        helper.assertTrue(upgrade != null, "Inline patch should add an UPGRADE slot style");
        helper.assertValueEqual(upgrade.getLeft(), 8, "Inline UPGRADE left should follow ENCODED_PATTERN left");
        helper.assertValueEqual(upgrade.getTop(), 67, "Inline UPGRADE top should sit below ENCODED_PATTERN");
        helper.assertValueEqual(upgrade.getGrid(), null, "Inline UPGRADE should not define a grid layout");
        helper.succeed();
    }

    @TestHolder("screen_slot_style_patch_adds_right_panel_upgrade_slot")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void addsRightPanelUpgradeSlot(GameTestHelper helper) {
        ScreenStyle style = new ScreenStyle();

        ScreenSlotStylePatchImpl.rightPanelVerticalUpgrade("test:right_panel").apply(style);

        SlotPosition upgrade = style.getSlots().get(UPGRADE_SLOT);
        helper.assertTrue(upgrade != null, "Right panel patch should add an UPGRADE slot style");
        helper.assertValueEqual(upgrade.getRight(), 0, "Right panel UPGRADE should be anchored to the right edge");
        helper.assertValueEqual(upgrade.getTop(), 0, "Right panel UPGRADE should start at the top edge");
        helper.assertValueEqual(upgrade.getGrid(), SlotGridLayout.VERTICAL, "Right panel UPGRADE should use vertical layout");
        helper.succeed();
    }

    @TestHolder("screen_slot_style_patch_missing_inline_anchor_fails")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void missingInlineAnchorFails(GameTestHelper helper) {
        ScreenStyle style = new ScreenStyle();
        style.getSlots().put(STORAGE_SLOT, position(8, 97));

        assertThrows(
                helper,
                IllegalStateException.class,
                () -> ScreenSlotStylePatchImpl.inlineSingleUpgrade("test:inline").apply(style),
                "Inline patch should fail when ENCODED_PATTERN is missing");
        helper.succeed();
    }

    private static SlotPosition position(int left, int top) {
        SlotPosition position = new SlotPosition();
        position.setLeft(left);
        position.setTop(top);
        return position;
    }

    private static <T extends Throwable> void assertThrows(GameTestHelper helper,
                                                           Class<T> expectedType,
                                                           Runnable action,
                                                           String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            helper.fail(message + ": expected " + expectedType.getSimpleName() + " but caught " +
                    thrown.getClass().getSimpleName() + " (" + thrown.getMessage() + ")");
        }
        helper.fail(message + ": expected " + expectedType.getSimpleName() + " but no exception was thrown");
    }
}
