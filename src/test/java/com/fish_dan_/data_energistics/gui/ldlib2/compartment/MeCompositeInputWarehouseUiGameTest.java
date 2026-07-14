package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.MeCompositeInputWarehouseMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class MeCompositeInputWarehouseUiGameTest {

    private static final List<SlotSemantic> CONFIG_ROWS = List.of(
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_1,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_2,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_3,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_4,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_5);
    private static final List<SlotSemantic> BUFFER_ROWS = List.of(
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_1,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_2,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_3,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_4,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_5);

    private MeCompositeInputWarehouseUiGameTest() {}

    @TestHolder("me_input_compartment_mounts_ldlib2_with_interleaved_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountsLdlib2WithInterleavedSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MeCompositeInputWarehouseMenu menu = new MeCompositeInputWarehouseMenu(
                1,
                player.getInventory(),
                inputHost());
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("ME input compartment must mount a ModularUI during construction");
        }

        assertEquals(86, menu.slots.size());
        assertEquals(CompartmentMenu.ME_COMPOSITE_INPUT_GROUP_COUNT, menu.unlockedSlotCount);
        assertTrue(menu.getSlots(SlotSemantics.UPGRADE).isEmpty(), "ME input must not acquire capacity upgrade slots");
        for (int row = 0; row < CONFIG_ROWS.size(); row++) {
            assertStridedSlotRange(menu.getSlots(CONFIG_ROWS.get(row)), row * 10, 2, 5, FakeSlot.class);
            assertStridedSlotRange(menu.getSlots(BUFFER_ROWS.get(row)), row * 10 + 1, 2, 5, AppEngSlot.class);
        }
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_HOTBAR), 50, 9);
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_INVENTORY), 59, 27);

        List<AeItemSlot> wrappers = modularUI.getElementsByType(AeItemSlot.class);
        assertEquals(86, wrappers.size());
        Set<AeItemSlot> distinctWrappers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertSame(slot, wrapper.getSlot());
            assertTrue(distinctWrappers.add(wrapper), "Every menu slot must have a unique LDLib2 wrapper");
        }
        assertEquals(86, distinctWrappers.size());
        assertElement(modularUI, CompartmentHostUi.ME_INPUT_ROOT_ID);
        assertElement(modularUI, MeInputCompartmentPanel.PANEL_ID);
        assertElement(modularUI, MeInputCompartmentPanel.CONFIG_PANEL_ID);
        assertElement(modularUI, MeInputCompartmentPanel.BUFFER_PANEL_ID);
        assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);
        assertElement(modularUI, MeInputCompartmentPanel.CONFIG_SLOT_ID_PREFIX + "0");
        assertElement(modularUI, MeInputCompartmentPanel.BUFFER_SLOT_ID_PREFIX + "0");
        helper.succeed();
    }

    @TestHolder("me_input_compartment_preserves_fake_and_buffer_protocols")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesFakeAndBufferProtocols(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MeCompositeInputWarehouseBlockEntity host = inputHost();
        MeCompositeInputWarehouseMenu menu = new MeCompositeInputWarehouseMenu(2, player.getInventory(), host);
        IModularUIHolderMenu holder = holder(menu);

        Slot configSlot = menu.getSlots(CompartmentMenu.COMPARTMENT_CONFIG_ROW_1).getFirst();
        if (!(configSlot instanceof FakeSlot fakeSlot)) {
            throw new GameTestAssertException("ME input configuration slot must remain an AE2 FakeSlot");
        }
        helper.assertTrue(fakeSlot.isHideAmount(), "Configuration slot must retain hidden-amount rendering");
        helper.assertTrue(fakeSlot.isDraggable(), "Configuration slot must retain AE2 fake-slot drag handling");
        helper.assertTrue(menu.canDragTo(fakeSlot), "Configuration drag must remain delegated to the AE2 fake slot");
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 32);
        helper.assertTrue(fakeSlot.canSetFilterTo(diamonds), "Configuration slot must accept the AE2 filter payload");
        menu.setFilter(fakeSlot.index, diamonds);
        helper.assertTrue(
                AEItemKey.of(Items.DIAMOND).equals(host.markerInventory().getKey(0)),
                "AE2 SET_FILTER must update the first marker key");
        helper.assertValueEqual(host.markerInventory().getAmount(0), 32L, "AE2 SET_FILTER must preserve marker amount");
        AeItemSlot configWrapper = requireAeSlot(holder, fakeSlot);
        assertEquals(1, configWrapper.getValue().getCount());
        assertThrows(() -> configWrapper.setValue(new ItemStack(Items.IRON_INGOT), true));
        assertThrows(configWrapper::xeiPhantom);
        helper.assertTrue(
                AEItemKey.of(Items.DIAMOND).equals(host.markerInventory().getKey(0)),
                "LDLib2 must not bypass the AE2 fake-slot protocol");
        menu.setFilter(fakeSlot.index, ItemStack.EMPTY);
        assertSame(null, host.markerInventory().getKey(0));

        Slot bufferSlot = menu.getSlots(CompartmentMenu.COMPARTMENT_BUFFER_ROW_1).getFirst();
        if (!(bufferSlot instanceof AppEngSlot appEngSlot) || bufferSlot instanceof FakeSlot) {
            throw new GameTestAssertException("ME input buffer slot must remain a non-fake AppEngSlot");
        }
        helper.assertFalse(appEngSlot.isDraggable(), "Buffer slot must retain the AE2 non-draggable flag");
        helper.assertFalse(menu.canDragTo(bufferSlot), "Buffer slot must reject drag distribution");
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 8);
        helper.assertTrue(bufferSlot.mayPlace(iron), "Buffer slot must remain writable");
        helper.assertTrue(bufferSlot.safeInsert(iron.copy()).isEmpty(), "Buffer slot must accept its original payload");
        helper.assertTrue(
                AEItemKey.of(Items.IRON_INGOT).equals(host.meInputBuffer().getKey(0)),
                "Buffer slot must update its original backing inventory");
        helper.assertValueEqual(host.meInputBuffer().getAmount(0), 8L, "Buffer slot must preserve inserted amount");
        helper.assertTrue(bufferSlot.mayPickup(player), "Buffer slot must not become a read-only display slot");
        assertEquals(3, bufferSlot.remove(3).getCount());
        helper.assertValueEqual(host.meInputBuffer().getAmount(0), 5L, "Buffer extraction must retain original behavior");
        helper.succeed();
    }

    @TestHolder("me_input_compartment_mount_failure_cleans_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountFailureCleansOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestCompartmentMenu menu = new TestCompartmentMenu(player, inputHost());
        RemovalTrackingElement cleanupProbe = new RemovalTrackingElement();

        assertThrows(() -> CompartmentHostUi.mountMeInput(
                menu,
                bridge -> failingPanel(menu, bridge, cleanupProbe)));
        assertEquals(1, cleanupProbe.removalCount);
        helper.succeed();
    }

    private static UIElement failingPanel(CompartmentMenu menu,
                                          AeMenuBridge bridge,
                                          RemovalTrackingElement cleanupProbe) {
        UIElement panel = MeInputCompartmentPanel.create(menu, bridge);
        panel.addChild(cleanupProbe);
        panel.addEventListener(UIEvents.MUI_CHANGED, event -> {
            throw new IllegalStateException("Injected ME input compartment mount failure");
        });
        return panel;
    }

    private static MeCompositeInputWarehouseBlockEntity inputHost() {
        return new MeCompositeInputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static IModularUIHolderMenu holder(CompartmentMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the compartment menu");
    }

    private static AeItemSlot requireAeSlot(IModularUIHolderMenu holder, Slot slot) {
        if (holder.getItemSlot(slot) instanceof AeItemSlot wrapper) {
            return wrapper;
        }
        throw new GameTestAssertException("Existing menu slot " + slot.index + " has no AeItemSlot wrapper");
    }

    private static void assertStridedSlotRange(List<Slot> slots,
                                               int firstIndex,
                                               int stride,
                                               int expectedCount,
                                               Class<? extends Slot> expectedType) {
        assertEquals(expectedCount, slots.size());
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            assertEquals(firstIndex + index * stride, slot.index);
            if (slot.getClass() != expectedType) {
                throw new GameTestAssertException("Unexpected slot type " + slot.getClass().getName());
            }
            if (slot instanceof AppEngSlot appEngSlot) {
                assertTrue(appEngSlot.isActive(), "All 25 fixed ME input pairs must remain active");
                assertTrue(appEngSlot.isSlotEnabled(), "All 25 fixed ME input pairs must remain enabled");
            }
        }
    }

    private static void assertSlotRange(List<Slot> slots, int firstIndex, int expectedCount) {
        assertEquals(expectedCount, slots.size());
        for (int index = 0; index < slots.size(); index++) {
            assertEquals(firstIndex + index, slots.get(index).index);
        }
    }

    private static void assertElement(ModularUI modularUI, String id) {
        if (modularUI.getElementById(id) == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
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

    private static final class TestCompartmentMenu extends CompartmentMenu {

        private TestCompartmentMenu(Player player, MeCompositeInputWarehouseBlockEntity host) {
            super(null, 3, player.getInventory(), host);
        }
    }

    private static final class RemovalTrackingElement extends UIElement {

        private int removalCount;

        @Override
        protected void onRemoved() {
            this.removalCount++;
            super.onRemoved();
        }
    }
}
