package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
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
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;

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
        assertEquals(List.of(TrinityDataCoreHostUiKeys.AUTO_BUILD), TrinityDataCoreHostUiKeys.registrationOrder());
        assertEquals(
                TrinityDataCoreHostUiKeys.registrationOrder(),
                menu.getHostUiExtension().registeredKeys());
        assertEquals(36, menu.slots.size());
        assertEquals(27, menu.getSlots(SlotSemantics.PLAYER_INVENTORY).size());
        assertEquals(9, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).size());
        assertEquals(36, modularUI.getElementsByType(AeItemSlot.class).size());
        for (Slot slot : menu.slots) {
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertSame(slot, wrapper.getSlot());
        }

        UIElement root = assertElement(modularUI, TrinityDataCoreHostUi.ROOT_ID);
        assertDimensions(root, 256, 212);
        Label title = label(modularUI, TrinityDataCoreHostUi.TITLE_ID);
        assertSame(root, title.getParent());
        assertComponent(Component.translatable("block.data_energistics.trinity_data_core"), title.getText());
        Label playerInventoryTitle = label(modularUI, TrinityDataCoreHostUi.PLAYER_INVENTORY_TITLE_ID);
        assertSame(root, playerInventoryTitle.getParent());
        assertComponent(Component.translatable("container.inventory"), playerInventoryTitle.getText());

        UIElement statusPanel = assertElement(modularUI, TrinityDataCoreStatusPanel.PANEL_ID);
        assertSame(root, statusPanel.getParent());
        assertAbsoluteBox(statusPanel, 0, 0, 256, 114);
        UIElement leftStatusPanel = assertElement(modularUI, TrinityDataCoreStatusPanel.LEFT_PANEL_ID);
        assertSame(statusPanel, leftStatusPanel.getParent());
        assertAbsoluteBox(leftStatusPanel, 5, 15, 128, 99);
        UIElement rightStatusPanel = assertElement(modularUI, TrinityDataCoreStatusPanel.RIGHT_PANEL_ID);
        assertSame(statusPanel, rightStatusPanel.getParent());
        assertAbsoluteBox(rightStatusPanel, 134, 15, 117, 64);
        assertEquals(1, label(modularUI, TrinityDataCoreStatusPanel.ONLINE_ID).getBoundDataSources().size());

        UIElement storagePanel = assertElement(modularUI, TrinityDataCoreStoragePanel.PANEL_ID);
        assertSame(root, storagePanel.getParent());
        assertAbsoluteBox(storagePanel, 134, 82, 117, 32);
        Label amountLabel = label(modularUI, TrinityDataCoreStoragePanel.AMOUNT_ID);
        assertAbsolutePosition(amountLabel, 2, 2);
        assertEquals(1, amountLabel.getBoundDataSources().size());
        Label typesLabel = label(modularUI, TrinityDataCoreStoragePanel.TYPES_ID);
        assertAbsolutePosition(typesLabel, 2, 12);
        assertEquals(1, typesLabel.getBoundDataSources().size());
        TrinityStorageCapacityBar capacityBar = singleElement(
                modularUI,
                TrinityStorageCapacityBar.class);
        assertSame(storagePanel, capacityBar.getParent());
        assertAbsoluteBox(capacityBar, 0, 24, 116, 6);
        assertEquals(1, capacityBar.getBoundDataSources().size());
        assertSame(
                capacityBar,
                assertElement(modularUI, TrinityStorageCapacityBar.TRACK_ID).getParent());

        TrinityCpuStatusList cpuList = singleElement(modularUI, TrinityCpuStatusList.class);
        assertSame(root, cpuList.getParent());
        assertAbsoluteBox(cpuList, 168, 129, 84, 76);
        assertEquals(1, cpuList.getBoundDataSources().size());
        assertSame(cpuList, cpuList.getScrollerView().getParent());

        UIElement playerInventoryPanel = assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);
        assertSame(root, playerInventoryPanel.getParent());
        assertAbsoluteBox(playerInventoryPanel, 5, 129, 162, 76);
        assertEquals(6, TrinityDataCoreHostUi.PLAYER_INVENTORY_LAYOUT.slotLeft());
        assertEquals(130, TrinityDataCoreHostUi.PLAYER_INVENTORY_LAYOUT.inventoryTop());
        assertEquals(188, TrinityDataCoreHostUi.PLAYER_INVENTORY_LAYOUT.hotbarTop());
        AeItemSlot firstInventorySlot = requireAeSlot(
                holder,
                menu.getSlots(SlotSemantics.PLAYER_INVENTORY).getFirst());
        assertSame(playerInventoryPanel, firstInventorySlot.getParent());
        assertAbsolutePosition(firstInventorySlot, 0, 0);
        AeItemSlot firstHotbarSlot = requireAeSlot(
                holder,
                menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst());
        assertSame(playerInventoryPanel, firstHotbarSlot.getParent());
        assertAbsolutePosition(firstHotbarSlot, 0, 58);

        UIElement launcherPanel = assertElement(modularUI, TrinityDataCoreHostLauncherPanel.PANEL_ID);
        assertSame(root, launcherPanel.getParent());
        assertAbsoluteBox(launcherPanel, 238, 1, 14, 14);
        assertEquals(
                Integer.valueOf(TrinityDataCoreHostLauncherPanel.PANEL_Z_INDEX),
                launcherPanel.getStyle().getImportant(PropertyRegistry.Z_INDEX));
        UIElement autoBuildLauncher = assertElement(modularUI, TrinityDataCoreHostLauncherPanel.AUTO_BUILD_ID);
        assertSame(launcherPanel, autoBuildLauncher.getParent());
        assertAbsoluteBox(autoBuildLauncher, 0, 0, 14, 14);
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

    private static AeItemSlot requireAeSlot(IModularUIHolderMenu holder, Slot slot) {
        if (holder.getItemSlot(slot) instanceof AeItemSlot wrapper) {
            return wrapper;
        }
        throw new GameTestAssertException("Existing menu slot " + slot.index + " has no AeItemSlot wrapper");
    }

    private static UIElement assertElement(ModularUI modularUI, String id) {
        UIElement element = modularUI.getElementById(id);
        if (element == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
        return element;
    }

    private static <T extends UIElement> T singleElement(ModularUI modularUI, Class<T> elementType) {
        var elements = modularUI.getElementsByType(elementType);
        if (elements.size() != 1) {
            throw new GameTestAssertException(
                    "Expected one " + elementType.getSimpleName() + ", found " + elements.size());
        }
        return elements.getFirst();
    }

    private static void assertAbsoluteBox(UIElement element, int left, int top, int width, int height) {
        assertAbsolutePosition(element, left, top);
        assertDimensions(element, width, height);
    }

    private static void assertAbsolutePosition(UIElement element, int left, int top) {
        assertEquals(TaffyPosition.ABSOLUTE, element.getLayout().getInline(LayoutProperties.POSITION));
        assertEquals(LengthPercentageAuto.length(left), element.getLayout().getInline(LayoutProperties.LEFT));
        assertEquals(LengthPercentageAuto.length(top), element.getLayout().getInline(LayoutProperties.TOP));
    }

    private static void assertDimensions(UIElement element, int width, int height) {
        assertEquals(TaffyDimension.length(width), element.getLayout().getInline(LayoutProperties.WIDTH));
        assertEquals(TaffyDimension.length(height), element.getLayout().getInline(LayoutProperties.HEIGHT));
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

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
