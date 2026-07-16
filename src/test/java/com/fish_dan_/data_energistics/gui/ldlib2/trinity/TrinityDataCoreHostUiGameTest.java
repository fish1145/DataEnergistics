package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
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

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture.WrapMode;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityDataCoreHostUiGameTest {

    private TrinityDataCoreHostUiGameTest() {}

    @TestHolder("trinity_data_core_mounts_ldlib2_ui_with_native_player_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountsLdlib2UiWithNativePlayerSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TrinityDataCoreMenu menu = new TrinityDataCoreMenu(1, player.getInventory(), null);
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (!(modularUI instanceof HostModularUI hostModularUI)) {
            throw new GameTestAssertException("Trinity Data Core must mount a HostModularUI during construction");
        }

        assertSame(menu, modularUI.getMenu());
        assertSame(menu.getHostUiExtension(), hostModularUI.hostUi());
        assertEquals(List.of(TrinityDataCoreHostUiKeys.AUTO_BUILD), TrinityDataCoreHostUiKeys.registrationOrder());
        assertEquals(
                TrinityDataCoreHostUiKeys.registrationOrder(),
                menu.getHostUiExtension().registeredKeys());
        assertEquals(36, menu.slots.size());
        assertEquals(36, modularUI.getElementsByType(ItemSlot.class).size());
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int expectedInventoryIndex = index < 27 ? index + 9 : index - 27;
            assertSame(player.getInventory(), slot.container);
            assertEquals(expectedInventoryIndex, slot.getContainerSlot());
            ItemSlot itemSlot = requireItemSlot(holder, slot);
            assertSame(slot, itemSlot.getSlot());
            assertEquals(index, slot.index);
        }

        UIElement root = assertElement(modularUI, TrinityDataCoreHostUi.ROOT_ID);
        Label title = label(modularUI, TrinityDataCoreHostUi.TITLE_ID);
        assertSame(root, title.getParent());
        assertComponent(Component.translatable("block.data_energistics.trinity_data_core"), title.getText());
        Label playerInventoryTitle = label(modularUI, TrinityDataCoreHostUi.PLAYER_INVENTORY_TITLE_ID);
        assertSame(root, playerInventoryTitle.getParent());
        assertComponent(Component.translatable("container.inventory"), playerInventoryTitle.getText());

        UIElement statusPanel = assertElement(modularUI, TrinityDataCoreStatusPanel.PANEL_ID);
        assertSame(root, statusPanel.getParent());
        UIElement leftStatusPanel = assertElement(modularUI, TrinityDataCoreStatusPanel.LEFT_PANEL_ID);
        assertSame(statusPanel, leftStatusPanel.getParent());
        UIElement rightStatusPanel = assertElement(modularUI, TrinityDataCoreStatusPanel.RIGHT_PANEL_ID);
        assertSame(statusPanel, rightStatusPanel.getParent());
        assertEquals(1, label(modularUI, TrinityDataCoreStatusPanel.ONLINE_ID).getBoundDataSources().size());

        UIElement storagePanel = assertElement(modularUI, TrinityDataCoreStoragePanel.PANEL_ID);
        assertSame(root, storagePanel.getParent());
        Label amountLabel = label(modularUI, TrinityDataCoreStoragePanel.AMOUNT_ID);
        assertEquals(1, amountLabel.getBoundDataSources().size());
        Label typesLabel = label(modularUI, TrinityDataCoreStoragePanel.TYPES_ID);
        assertEquals(1, typesLabel.getBoundDataSources().size());
        TrinityStorageCapacityBar capacityBar = singleElement(
                modularUI,
                TrinityStorageCapacityBar.class);
        assertSame(storagePanel, capacityBar.getParent());
        assertEquals(1, capacityBar.getBoundDataSources().size());
        assertSame(
                capacityBar,
                assertElement(modularUI, TrinityStorageCapacityBar.TRACK_ID).getParent());
        assertRepeatingFillTexture(modularUI, TrinityStorageCapacityBar.ITEM_SEGMENT_ID);
        assertRepeatingFillTexture(modularUI, TrinityStorageCapacityBar.FLUID_SEGMENT_ID);
        assertRepeatingFillTexture(modularUI, TrinityStorageCapacityBar.OTHER_SEGMENT_ID);

        TrinityCpuStatusList cpuList = singleElement(modularUI, TrinityCpuStatusList.class);
        assertSame(root, cpuList.getParent());
        assertEquals(1, cpuList.getBoundDataSources().size());
        assertSame(cpuList, cpuList.getScrollerView().getParent());

        InventorySlots playerInventorySlots = singleElement(modularUI, InventorySlots.class);
        assertSame(root, playerInventorySlots.getParent());
        assertEquals(TrinityDataCoreHostUi.PLAYER_INVENTORY_ID, playerInventorySlots.getId());
        assertSame(menu.slots.getFirst(), playerInventorySlots.rows[0].slots[0].getSlot());
        assertSame(menu.slots.get(27), playerInventorySlots.hotbar.slots[0].getSlot());
        for (int row = 0; row < playerInventorySlots.rows.length; row++) {
            for (int column = 0; column < playerInventorySlots.rows[row].slots.length; column++) {
                assertSame(
                        menu.slots.get(row * 9 + column),
                        playerInventorySlots.rows[row].slots[column].getSlot());
            }
        }
        for (int column = 0; column < playerInventorySlots.hotbar.slots.length; column++) {
            assertSame(menu.slots.get(27 + column), playerInventorySlots.hotbar.slots[column].getSlot());
        }

        UIElement launcherPanel = assertElement(modularUI, TrinityDataCoreHostLauncherPanel.PANEL_ID);
        assertSame(root, launcherPanel.getParent());
        UIElement autoBuildLauncher = assertElement(modularUI, TrinityDataCoreHostLauncherPanel.AUTO_BUILD_ID);
        assertSame(launcherPanel, autoBuildLauncher.getParent());
        menu.removed(player);
        assertTrue(menu.getHostUiExtension().isDisposed());
        menu.removed(player);
        assertTrue(menu.getHostUiExtension().isDisposed());
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

        modularUI.ui.rootElement.screenTick();

        TrinityDataCoreHostStatus status = TrinityDataCoreHostStatus.EMPTY;
        assertComponent(
                TrinityDataCoreStatusPanel.onlineLine(status),
                label(modularUI, TrinityDataCoreStatusPanel.ONLINE_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.mainStructureLine(status),
                label(modularUI, TrinityDataCoreStatusPanel.MAIN_STRUCTURE_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.cpuPartitionLine(status),
                label(modularUI, TrinityDataCoreStatusPanel.CPU_PARTITIONS_ID).getText());
        assertComponent(
                TrinityDataCoreStatusPanel.craftingLine(status),
                label(modularUI, TrinityDataCoreStatusPanel.CRAFTING_ID).getText());
        helper.succeed();
    }

    private static IModularUIHolderMenu holder(TrinityDataCoreMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the Trinity Data Core menu");
    }

    private static ItemSlot requireItemSlot(IModularUIHolderMenu holder, Slot slot) {
        ItemSlot itemSlot = holder.getItemSlot(slot);
        if (itemSlot != null) {
            return itemSlot;
        }
        throw new GameTestAssertException("Menu slot " + slot.index + " has no LDLib2 ItemSlot mapping");
    }

    private static UIElement assertElement(ModularUI modularUI, String id) {
        UIElement element = modularUI.getElementById(id);
        if (element == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
        return element;
    }

    private static void assertRepeatingFillTexture(ModularUI modularUI, String id) {
        IGuiTexture texture = assertElement(modularUI, id).getStyle().getDefault(PropertyRegistry.BACKGROUND);
        if (!(texture instanceof SpriteTexture spriteTexture)) {
            throw new GameTestAssertException("Capacity segment " + id + " must use a SpriteTexture");
        }
        assertEquals(WrapMode.REPEAT, spriteTexture.wrapMode);
        assertEquals(2, spriteTexture.spriteSize.getWidth());
        assertEquals(4, spriteTexture.spriteSize.getHeight());
    }

    private static <T extends UIElement> T singleElement(ModularUI modularUI, Class<T> elementType) {
        var elements = modularUI.getElementsByType(elementType);
        if (elements.size() != 1) {
            throw new GameTestAssertException(
                    "Expected one " + elementType.getSimpleName() + ", found " + elements.size());
        }
        return elements.getFirst();
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

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
