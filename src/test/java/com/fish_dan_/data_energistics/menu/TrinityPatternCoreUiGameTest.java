package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityPatternCoreUi;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternCoreUiGameTest {

    private TrinityPatternCoreUiGameTest() {}

    @TestHolder("trinity_pattern_core_ldlib2_maps_existing_slots_and_tracks_page_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsExistingSlotsAndTracksPageState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TrinityPatternCoreMenu menu = new TrinityPatternCoreMenu(1, player.getInventory(), core);
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("Trinity Pattern Core must mount a ModularUI during construction");
        }

        List<Slot> patternSlots = menu.pagePatternSlots();
        List<Slot> playerInventorySlots = menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
        List<Slot> hotbarSlots = menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
        assertSame(menu, modularUI.getMenu());
        assertEquals(100, menu.slots.size());
        assertEquals(64, patternSlots.size());
        assertEquals(27, playerInventorySlots.size());
        assertEquals(9, hotbarSlots.size());
        List<AeItemSlot> wrappers = modularUI.getElementsByType(AeItemSlot.class);
        assertEquals(100, wrappers.size());
        assertMappedSlots(holder, menu, patternSlots);
        assertMappedSlots(holder, menu, playerInventorySlots);
        assertMappedSlots(holder, menu, hotbarSlots);
        assertWrapperOrder(wrappers, patternSlots, 0);
        assertWrapperOrder(wrappers, playerInventorySlots, 64);
        assertWrapperOrder(wrappers, hotbarSlots, 91);
        assertElement(modularUI, TrinityPatternCoreUi.ROOT_ID);
        assertElement(modularUI, TrinityPatternCoreUi.PANEL_ID);
        assertElement(modularUI, TrinityPatternCoreUi.PATTERN_GRID_ID);
        assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);

        Button previous = button(modularUI, TrinityPatternCoreUi.PREVIOUS_PAGE_ID);
        Button next = button(modularUI, TrinityPatternCoreUi.NEXT_PAGE_ID);
        Button refund = button(modularUI, TrinityPatternCoreUi.REFUND_ALL_ID);
        Label pageInfo = pageInfo(modularUI);
        modularUI.ui.rootElement.screenTick();
        assertFalse(previous.isActive());
        assertTrue(next.isActive());
        assertFalse(refund.isActive());
        assertComponent(Component.translatable("screen.data_energistics.page.previous"), previous.text.getText());
        assertComponent(Component.translatable("screen.data_energistics.page.next"), next.text.getText());
        assertComponent(
                Component.translatable("button.data_energistics.trinity_pattern_core.refund"),
                refund.text.getText());
        assertComponent(Component.translatable("screen.data_energistics.page", 1, 2), pageInfo.getText());

        menu.hasRefundableState = true;
        modularUI.ui.rootElement.screenTick();
        assertTrue(refund.isActive());

        menu.setPage(1);
        menu.hasRefundableState = true;
        modularUI.ui.rootElement.screenTick();
        assertFalse(menu.isPageSelectionConfirmed());
        assertFalse(previous.isActive());
        assertFalse(next.isActive());
        assertFalse(refund.isActive());
        assertComponent(Component.translatable("screen.data_energistics.page", 2, 2), pageInfo.getText());
        for (Slot slot : patternSlots) {
            assertFalse(slot.isActive());
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertFalse(wrapper.isVisible());
            assertFalse(wrapper.isAllowHitTest());
        }

        menu.confirmPage(1);
        menu.hasRefundableState = true;
        modularUI.ui.rootElement.screenTick();
        assertTrue(menu.isPageSelectionConfirmed());
        assertTrue(previous.isActive());
        assertFalse(next.isActive());
        assertTrue(refund.isActive());
        for (Slot slot : patternSlots) {
            assertTrue(slot.isActive());
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertTrue(wrapper.isVisible());
            assertTrue(wrapper.isAllowHitTest());
        }
        helper.succeed();
    }

    private static IModularUIHolderMenu holder(AEBaseMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the Trinity Pattern Core menu");
    }

    private static void assertMappedSlots(IModularUIHolderMenu holder,
                                          TrinityPatternCoreMenu menu,
                                          List<Slot> slots) {
        for (Slot slot : slots) {
            assertSame(slot, menu.slots.get(slot.index));
            assertSame(slot, requireAeSlot(holder, slot).getSlot());
        }
    }

    private static void assertWrapperOrder(List<AeItemSlot> wrappers, List<Slot> slots, int firstWrapperIndex) {
        for (int index = 0; index < slots.size(); index++) {
            assertSame(slots.get(index), wrappers.get(firstWrapperIndex + index).getSlot());
        }
    }

    private static AeItemSlot requireAeSlot(IModularUIHolderMenu holder, Slot slot) {
        if (holder.getItemSlot(slot) instanceof AeItemSlot wrapper) {
            return wrapper;
        }
        throw new GameTestAssertException("Existing menu slot " + slot.index + " has no AeItemSlot wrapper");
    }

    private static void assertElement(ModularUI modularUI, String id) {
        if (modularUI.getElementById(id) == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
    }

    private static Button button(ModularUI modularUI, String id) {
        if (modularUI.getElementById(id) instanceof Button button) {
            return button;
        }
        throw new GameTestAssertException("Missing LDLib2 button " + id);
    }

    private static Label pageInfo(ModularUI modularUI) {
        if (modularUI.getElementById(TrinityPatternCoreUi.PAGE_INFO_ID) instanceof Label label) {
            return label;
        }
        throw new GameTestAssertException("Missing LDLib2 label " + TrinityPatternCoreUi.PAGE_INFO_ID);
    }

    private static void assertComponent(Component expected, Component actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected component " + expected + ", got " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected identical objects");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new GameTestAssertException("Expected condition to be false");
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
