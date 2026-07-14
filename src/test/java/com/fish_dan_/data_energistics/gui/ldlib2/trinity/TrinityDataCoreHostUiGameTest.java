package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

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

import appeng.menu.SlotSemantics;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityDataCoreHostUiGameTest {

    private TrinityDataCoreHostUiGameTest() {}

    @TestHolder("trinity_data_core_mounts_ldlib2_ui_with_existing_player_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountsLdlib2UiWithExistingPlayerSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TrinityDataCoreMenu menu = new TrinityDataCoreMenu(1, player.getInventory(), null);
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (!(modularUI instanceof HostModularUI hostModularUI)) {
            throw new GameTestAssertException("Trinity Data Core must mount a HostModularUI during construction");
        }

        assertSame(menu, modularUI.getMenu());
        assertSame(menu.getHostUiExtension(), hostModularUI.hostUi());
        assertEquals(36, menu.slots.size());
        assertEquals(27, menu.getSlots(SlotSemantics.PLAYER_INVENTORY).size());
        assertEquals(9, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).size());
        assertEquals(36, modularUI.getElementsByType(AeItemSlot.class).size());
        for (Slot slot : menu.slots) {
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertSame(slot, wrapper.getSlot());
        }

        assertElement(modularUI, TrinityDataCoreHostUi.ROOT_ID);
        assertElement(modularUI, TrinityDataCoreStatusPanel.PANEL_ID);
        assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);
        helper.succeed();
    }

    @TestHolder("trinity_data_core_ldlib2_status_tracks_synced_menu_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void ldlib2StatusTracksSyncedMenuState(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TrinityDataCoreMenu menu = new TrinityDataCoreMenu(2, player.getInventory(), null);
        ModularUI modularUI = holder(menu).getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("Expected mounted Trinity Data Core ModularUI");
        }

        menu.online = true;
        menu.structureFormed = true;
        menu.busyCpuPartitionCount = 3;
        menu.cpuPartitionCount = 8;
        menu.storedAmountText = "1536";
        menu.storedAmountCapacityText = "4096";
        menu.craftingStructureFormed = true;
        menu.craftingPatternCapacity = 64;
        modularUI.ui.rootElement.screenTick();

        assertComponent(
                TrinityDataCoreStatusPanel.onlineLine(menu),
                label(modularUI, TrinityDataCoreStatusPanel.ONLINE_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.mainStructureLine(menu),
                label(modularUI, TrinityDataCoreStatusPanel.MAIN_STRUCTURE_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.cpuPartitionLine(menu),
                label(modularUI, TrinityDataCoreStatusPanel.CPU_PARTITIONS_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.storageAmountLine(menu),
                label(modularUI, TrinityDataCoreStatusPanel.STORAGE_AMOUNT_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.craftingLine(menu),
                label(modularUI, TrinityDataCoreStatusPanel.CRAFTING_ID).getText());
        helper.succeed();
    }

    private static IModularUIHolderMenu holder(TrinityDataCoreMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the Trinity Data Core menu");
    }

    private static AeItemSlot requireAeSlot(IModularUIHolderMenu holder, Slot slot) {
        if (holder.getItemSlot(slot) instanceof AeItemSlot wrapper) {
            return wrapper;
        }
        throw new GameTestAssertException("Existing menu slot " + slot.index + " has no AeItemSlot wrapper");
    }

    private static void assertElement(ModularUI modularUI, String id) {
        UIElement element = modularUI.getElementById(id);
        if (element == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
    }

    private static Label label(ModularUI modularUI, String id) {
        if (modularUI.getElementById(id) instanceof Label label) {
            return label;
        }
        throw new GameTestAssertException("Missing LDLib2 label " + id);
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

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
