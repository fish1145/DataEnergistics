package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture.WrapMode;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
        assertEquals(
                ScrollerMode.VERTICAL,
                cpuList.getScrollerView().getScrollerViewStyle().getDefault(PropertyRegistry.SCROLLER_VIEW_MODE));
        assertEquals(
                ScrollDisplay.ALWAYS,
                cpuList.getScrollerView().getScrollerViewStyle().getDefault(PropertyRegistry.SCROLLER_VERTICAL_DISPLAY));
        assertEquals(
                ScrollDisplay.NEVER,
                cpuList.getScrollerView().getScrollerViewStyle().getDefault(PropertyRegistry.SCROLLER_HORIZONTAL_DISPLAY));
        Scroller verticalScroller = cpuList.getScrollerView().verticalScroller;
        assertSame(verticalScroller, assertElement(modularUI, TrinityCpuStatusList.SCROLLBAR_ID));
        assertSame(verticalScroller.headButton, assertElement(modularUI, TrinityCpuStatusList.SCROLL_HEAD_ID));
        assertSame(verticalScroller.scrollContainer, assertElement(modularUI, TrinityCpuStatusList.SCROLL_TRACK_ID));
        assertSame(verticalScroller.scrollBar, assertElement(modularUI, TrinityCpuStatusList.SCROLL_THUMB_ID));
        assertSame(verticalScroller.tailButton, assertElement(modularUI, TrinityCpuStatusList.SCROLL_TAIL_ID));
        assertSame(cpuList.getScrollerView().verticalContainer, verticalScroller.getParent());
        assertSame(verticalScroller, verticalScroller.headButton.getParent());
        assertSame(verticalScroller, verticalScroller.scrollContainer.getParent());
        assertTrue(verticalScroller.scrollContainer.isAncestorOf(verticalScroller.scrollBar));
        assertSame(verticalScroller, verticalScroller.tailButton.getParent());
        assertTrue(verticalScroller.selfAndAllChildren().noneMatch(UIElement::isAllowHitTest));

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

    @TestHolder("trinity_data_core_cpu_list_uses_ae_information_hierarchy_and_native_scrollbar")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuListUsesAeInformationHierarchyAndNativeScrollbar(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TrinityDataCoreMenu menu = new TrinityDataCoreMenu(3, player.getInventory(), null);
        ModularUI modularUI = holder(menu).getModularUI();
        TrinityCpuStatusList cpuList = singleElement(modularUI, TrinityCpuStatusList.class);
        TrinityCpuStatus idle = new TrinityCpuStatus(
                2,
                1_572_864L,
                4,
                Component.literal("Idle CPU"),
                CpuSelectionMode.PLAYER_ONLY,
                null,
                0.0F,
                0L);
        TrinityCpuStatus busy = new TrinityCpuStatus(
                5,
                1_048_576L,
                2,
                Component.literal("Busy CPU"),
                CpuSelectionMode.MACHINE_ONLY,
                new GenericStack(AEItemKey.of(Items.DIAMOND), 64L),
                0.5F,
                1_000_000_000L);
        AtomicInteger selectedCpu = new AtomicInteger(-1);
        cpuList.setOnCpuSelected(selectedCpu::set);

        cpuList.setValue(new TrinityCpuListStatus(List.of(busy, idle)), false);

        assertEquals(2, cpuList.getScrollerView().getItemCount());
        assertEquals(
                2.0F * TrinityCpuStatusList.ROW_STRIDE,
                cpuList.getScrollerView().getTotalVirtualHeight());
        Label idleName = label(modularUI, "trinity_cpu_status_2_name");
        assertComponent(Component.literal("Idle CPU"), idleName.getText());
        assertStaticCpuLabel(idleName);
        Label processorCount = label(modularUI, "trinity_cpu_status_2_processor_count");
        assertComponent(Component.literal("4"), processorCount.getText());
        assertStaticCpuLabel(processorCount);
        Label storageAmount = label(modularUI, "trinity_cpu_status_2_storage_amount");
        assertComponent(Component.literal("1M"), storageAmount.getText());
        assertStaticCpuLabel(storageAmount);
        assertElement(modularUI, "trinity_cpu_status_2_processor_icon");
        assertElement(modularUI, "trinity_cpu_status_2_storage_icon");
        assertElement(modularUI, "trinity_cpu_status_2_mode_icon");
        assertMissingElement(modularUI, "trinity_cpu_status_2_craft_icon");

        Label busyName = label(modularUI, "trinity_cpu_status_5_name");
        assertComponent(Component.literal("Busy CPU"), busyName.getText());
        assertStaticCpuLabel(busyName);
        Label craftAmount = label(modularUI, "trinity_cpu_status_5_craft_amount");
        assertComponent(Component.literal("64"), craftAmount.getText());
        assertStaticCpuLabel(craftAmount);
        assertElement(modularUI, "trinity_cpu_status_5_craft_icon");
        assertElement(modularUI, "trinity_cpu_status_5_target_icon");
        assertElement(modularUI, "trinity_cpu_status_5_progress");
        assertElement(modularUI, "trinity_cpu_status_5_task_overlay");
        assertMissingElement(modularUI, "trinity_cpu_status_5_storage_icon");

        TrinityCpuStatus unlimited = new TrinityCpuStatus(
                7,
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                Component.literal("Unlimited CPU"),
                CpuSelectionMode.ANY,
                null,
                0.0F,
                0L);
        cpuList.setValue(new TrinityCpuListStatus(List.of(unlimited)), false);
        assertComponent(Component.literal("MAX"), label(modularUI, "trinity_cpu_status_7_processor_count").getText());
        assertComponent(Component.literal("MAX"), label(modularUI, "trinity_cpu_status_7_storage_amount").getText());
        cpuList.setValue(new TrinityCpuListStatus(List.of(busy, idle)), false);

        assertTrue(cpuList.activateCpu(5));
        assertEquals(5, selectedCpu.get());
        cpuList.setValue(TrinityCpuListStatus.EMPTY, false);
        assertTrue(!cpuList.activateCpu(5));
        assertEquals(5, selectedCpu.get());

        cpuList.setValue(new TrinityCpuListStatus(List.of(busy, idle)), false);
        dispatchMouseWheel(cpuList.getScrollerView().viewPort, -1.0F);
        assertFloatEquals(0.0F, cpuList.getScrollerView().verticalScroller.getNormalizedValue());

        List<TrinityCpuStatus> cpus = IntStream.range(0, 6)
                .mapToObj(TrinityDataCoreHostUiGameTest::idleCpu)
                .toList();
        cpuList.setValue(new TrinityCpuListStatus(cpus), false);
        assertEquals(0, cpuList.getScrollerView().getFirstMountedIndex());
        assertEquals(
                6.0F * TrinityCpuStatusList.ROW_STRIDE,
                cpuList.getScrollerView().getTotalVirtualHeight());
        assertTrue(cpuList.getScrollerView().verticalScroller
                .selfAndAllChildren()
                .allMatch(UIElement::isAllowHitTest));
        cpuList.getScrollerView().refreshVisibleItems(0.0F, TrinityCpuStatusList.VIEWPORT_HEIGHT);
        assertEquals(0, cpuList.getScrollerView().getFirstMountedIndex());
        float oneRowScroll = (float) TrinityCpuStatusList.ROW_STRIDE /
                (cpuList.getScrollerView().getTotalVirtualHeight() - TrinityCpuStatusList.VIEWPORT_HEIGHT);
        cpuList.getScrollerView().verticalScroller.setNormalizedValue(oneRowScroll, true);
        assertFloatEquals(
                oneRowScroll,
                cpuList.getScrollerView().verticalScroller.getNormalizedValue());
        cpuList.getScrollerView().verticalScroller.setNormalizedValue(0.0F, true);
        assertFloatEquals(0.0F, cpuList.getScrollerView().verticalScroller.getNormalizedValue());
        cpuList.getScrollerView().verticalScroller.setNormalizedValue(1.0F, true);
        assertTrue(cpuList.getScrollerView().getFirstMountedIndex() > 0);
        assertEquals(5, cpuList.getScrollerView().getLastMountedIndex());
        assertElement(modularUI, "trinity_cpu_status_5");
        cpuList.getScrollerView().scrollToTop();
        assertEquals(0, cpuList.getScrollerView().getFirstMountedIndex());

        cpuList.getScrollerView().verticalScroller.setNormalizedValue(1.0F, true);
        cpuList.setValue(new TrinityCpuListStatus(cpus.subList(0, 4)), false);
        assertFloatEquals(0.0F, cpuList.getScrollerView().verticalScroller.getNormalizedValue());
        assertEquals(0, cpuList.getScrollerView().getFirstMountedIndex());
        assertTrue(cpuList.getScrollerView().verticalScroller
                .selfAndAllChildren()
                .allMatch(UIElement::isAllowHitTest));
        cpuList.getScrollerView().verticalScroller.setNormalizedValue(1.0F, true);
        assertFloatEquals(1.0F, cpuList.getScrollerView().verticalScroller.getNormalizedValue());

        cpuList.setValue(new TrinityCpuListStatus(cpus.subList(0, 2)), false);
        assertFloatEquals(0.0F, cpuList.getScrollerView().verticalScroller.getNormalizedValue());
        assertEquals(0, cpuList.getScrollerView().getFirstMountedIndex());
        assertTrue(cpuList.getScrollerView().verticalScroller
                .selfAndAllChildren()
                .noneMatch(UIElement::isAllowHitTest));

        menu.removed(player);
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

    private static void assertMissingElement(ModularUI modularUI, String id) {
        if (modularUI.getElementById(id) != null) {
            throw new GameTestAssertException("Unexpected LDLib2 element " + id);
        }
    }

    private static TrinityCpuStatus idleCpu(int number) {
        return new TrinityCpuStatus(
                number,
                1_024L,
                0,
                Component.literal("CPU " + number),
                CpuSelectionMode.ANY,
                null,
                0.0F,
                0L);
    }

    private static void dispatchMouseWheel(UIElement target, float deltaY) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_WHEEL);
        event.target = target;
        event.deltaY = deltaY;
        UIEventDispatcher.dispatchEvent(event);
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

    private static void assertStaticCpuLabel(Label label) {
        if (label.getTextStyle().textWrap() != TextWrap.NONE) {
            throw new GameTestAssertException("CPU label " + label.getId() + " must not roll or wrap");
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

    private static void assertFloatEquals(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.0001F) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
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
