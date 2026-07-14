package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.CompartmentSlotLabel;
import com.fish_dan_.data_energistics.menu.CompositeWarehouseMenu;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.IOptionalSlot;
import appeng.menu.slot.RestrictedInputSlot;
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
public final class CompositeWarehouseUiGameTest {

    private static final int UPGRADE_SLOT_COUNT = 5;
    private static final int STORAGE_FIRST_MENU_INDEX = UPGRADE_SLOT_COUNT;
    private static final int FLUID_FIRST_MENU_INDEX = STORAGE_FIRST_MENU_INDEX +
            CompartmentMenu.COMPOSITE_WAREHOUSE_SLOT_COUNT;
    private static final int PLAYER_HOTBAR_FIRST_MENU_INDEX = FLUID_FIRST_MENU_INDEX +
            CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_COUNT * 2;
    private static final int PLAYER_INVENTORY_FIRST_MENU_INDEX = PLAYER_HOTBAR_FIRST_MENU_INDEX + 9;
    private static final int TOTAL_SLOT_COUNT = PLAYER_INVENTORY_FIRST_MENU_INDEX + 27;
    private static final List<SlotSemantic> STORAGE_ROWS = List.of(
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_1,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_2,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_3,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_4,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_5,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_6,
            CompartmentMenu.COMPARTMENT_STORAGE_ROW_7);

    private CompositeWarehouseUiGameTest() {}

    @TestHolder("composite_input_warehouse_mounts_complete_ldlib2_surface")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void inputMountsCompleteLdlib2Surface(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompositeWarehouseMenu menu = new CompositeWarehouseMenu(1, player.getInventory(), inputHost());
        assertCompleteSurface(menu, CompartmentType.INPUT);
        helper.succeed();
    }

    @TestHolder("composite_output_warehouse_mounts_complete_ldlib2_surface")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void outputMountsCompleteLdlib2Surface(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompositeWarehouseMenu menu = new CompositeWarehouseMenu(2, player.getInventory(), outputHost());
        assertCompleteSurface(menu, CompartmentType.OUTPUT);
        helper.succeed();
    }

    @TestHolder("composite_warehouse_capacity_cards_update_existing_ldlib2_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capacityCardsUpdateExistingLdlib2Slots(GameTestHelper helper) {
        List<CompositeWarehouseBlockEntity> hosts = List.of(
                placedWarehouse(
                        helper,
                        new BlockPos(1, 1, 1),
                        ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState()),
                placedWarehouse(
                        helper,
                        new BlockPos(2, 1, 1),
                        ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState()));
        for (CompositeWarehouseBlockEntity host : hosts) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            CompositeWarehouseMenu menu = new CompositeWarehouseMenu(3, player.getInventory(), host);
            IModularUIHolderMenu holder = holder(menu);

            assertRowState(menu, holder, 2, false);
            assertRowState(menu, holder, 3, false);
            Slot upgradeSlot = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
            ItemStack remainder = upgradeSlot.safeInsert(AEItems.CAPACITY_CARD.stack());
            assertTrue(remainder.isEmpty(), "Capacity card must enter the original protected upgrade slot");
            assertEquals(1, host.installedCapacityCards());
            menu.broadcastChanges();
            assertEquals(3, menu.unlockedRowCount);
            assertEquals(27, menu.unlockedSlotCount);
            assertRowState(menu, holder, 2, true);
            assertRowState(menu, holder, 3, false);
        }
        helper.succeed();
    }

    @TestHolder("composite_warehouse_preserves_upgrade_and_fake_slot_protocols")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesUpgradeAndFakeSlotProtocols(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompositeWarehouseBlockEntity host = inputHost();
        CompositeWarehouseMenu menu = new CompositeWarehouseMenu(4, player.getInventory(), host);
        IModularUIHolderMenu holder = holder(menu);

        Slot upgradeSlot = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
        assertTrue(upgradeSlot instanceof RestrictedInputSlot,
                "Plain warehouse upgrades must retain RestrictedInputSlot");
        assertTrue(upgradeSlot.mayPlace(AEItems.CAPACITY_CARD.stack()),
                "Capacity card must remain valid in the upgrade slot");
        assertTrue(!upgradeSlot.mayPlace(new ItemStack(Items.DIAMOND)),
                "Ordinary items must remain invalid in the upgrade slot");
        assertSame(upgradeSlot, requireAeSlot(holder, upgradeSlot).getSlot());

        ItemStack remainder = upgradeSlot.safeInsert(AEItems.CAPACITY_CARD.stack());
        assertTrue(remainder.isEmpty(), "Capacity card insertion must use the original upgrade inventory");
        int firstExpansionStorageSlot = CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS *
                CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_COLUMNS;
        helper.assertValueEqual(
                host.slotStorage().insert(
                        firstExpansionStorageSlot,
                        AEItemKey.of(Items.IRON_INGOT),
                        1L,
                        Actionable.MODULATE),
                1L,
                "The first capacity card must unlock the third storage row");
        helper.assertFalse(
                upgradeSlot.mayPickup(player),
                "The original protected slot must block capacity-card removal while expansion content exists");

        assertFakeColumnProtocol(menu, holder, CompartmentMenu.COMPARTMENT_FLUID, 0);
        assertFakeColumnProtocol(menu, holder, CompartmentMenu.COMPARTMENT_KEY, 1);
        AeItemSlot fakeWrapper = requireAeSlot(holder, menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID).getFirst());
        assertThrows(() -> fakeWrapper.setValue(new ItemStack(Items.WATER_BUCKET), true));
        assertThrows(fakeWrapper::xeiPhantom);
        helper.succeed();
    }

    @TestHolder("composite_warehouse_mount_failure_cleans_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountFailureCleansOnce(GameTestHelper helper) {
        for (CompositeWarehouseBlockEntity host : List.of(inputHost(), outputHost())) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            TestCompartmentMenu menu = new TestCompartmentMenu(player, host);
            MountLifecycleProbe lifecycleProbe = new MountLifecycleProbe();

            assertThrows(() -> CompartmentHostUi.mountCompositeWarehouse(
                    menu,
                    bridge -> failingPanel(menu, bridge, lifecycleProbe)));
            assertEquals(1, lifecycleProbe.mountCount);
            assertEquals(1, lifecycleProbe.removalCount);
        }
        helper.succeed();
    }

    private static void assertCompleteSurface(CompositeWarehouseMenu menu, CompartmentType expectedType) {
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("Plain warehouse must mount a ModularUI during construction");
        }
        assertEquals(expectedType, menu.getCompartmentType());
        assertEquals(TOTAL_SLOT_COUNT, menu.slots.size());
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        assertSlotRange(upgradeSlots, 0, UPGRADE_SLOT_COUNT);
        for (Slot slot : upgradeSlots) {
            assertTrue(slot instanceof RestrictedInputSlot,
                    "Upgrade semantic must contain only RestrictedInputSlot instances");
        }
        for (int row = 0; row < STORAGE_ROWS.size(); row++) {
            List<Slot> rowSlots = menu.getSlots(STORAGE_ROWS.get(row));
            assertSlotRange(
                    rowSlots,
                    STORAGE_FIRST_MENU_INDEX + row * CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT,
                    CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT);
            for (Slot slot : rowSlots) {
                assertTrue(slot instanceof AppEngSlot,
                        "Storage row semantic must contain only AppEngSlot instances");
                assertEquals(
                        row >= CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS,
                        slot instanceof IOptionalSlot);
            }
        }
        assertStridedFakeSlots(menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID), FLUID_FIRST_MENU_INDEX, 0);
        assertStridedFakeSlots(menu.getSlots(CompartmentMenu.COMPARTMENT_KEY), FLUID_FIRST_MENU_INDEX + 1, 1);
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_HOTBAR), PLAYER_HOTBAR_FIRST_MENU_INDEX, 9);
        assertSlotRange(
                menu.getSlots(SlotSemantics.PLAYER_INVENTORY),
                PLAYER_INVENTORY_FIRST_MENU_INDEX,
                27);

        assertEquals(TOTAL_SLOT_COUNT, modularUI.getElementsByType(AeItemSlot.class).size());
        Set<AeItemSlot> wrappers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertSame(slot, wrapper.getSlot());
            assertTrue(wrappers.add(wrapper), "Every menu slot must have one distinct LDLib2 wrapper");
        }
        assertEquals(TOTAL_SLOT_COUNT, wrappers.size());
        assertElement(modularUI, CompartmentHostUi.COMPOSITE_WAREHOUSE_ROOT_ID);
        assertElement(modularUI, CompartmentHostUi.TITLE_ID);
        assertElement(modularUI, CompartmentHostUi.PLAYER_INVENTORY_TITLE_ID);
        assertElement(modularUI, CompositeWarehousePanel.PANEL_ID);
        assertElement(modularUI, CompositeWarehousePanel.STORAGE_PANEL_ID);
        assertElement(modularUI, CompositeWarehousePanel.FLUID_PANEL_ID);
        assertElement(modularUI, CompositeWarehousePanel.KEY_PANEL_ID);
        assertElement(modularUI, CompositeWarehousePanel.UPGRADE_PANEL_ID);
        assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);
    }

    private static void assertRowState(CompartmentMenu menu,
                                       IModularUIHolderMenu holder,
                                       int row,
                                       boolean enabled) {
        List<Slot> rowSlots = menu.getSlots(STORAGE_ROWS.get(row));
        assertOptionalState(holder, rowSlots.getFirst(), enabled);
        assertOptionalState(holder, menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID).get(row), enabled);
        assertOptionalState(holder, menu.getSlots(CompartmentMenu.COMPARTMENT_KEY).get(row), enabled);
    }

    private static void assertOptionalState(IModularUIHolderMenu holder, Slot slot, boolean enabled) {
        if (!(slot instanceof IOptionalSlot optionalSlot)) {
            throw new GameTestAssertException("Expansion slot must retain IOptionalSlot");
        }
        assertEquals(enabled, optionalSlot.isSlotEnabled());
        assertEquals(enabled, slot.isActive());
        AeItemSlot wrapper = requireAeSlot(holder, slot);
        wrapper.screenTick();
        assertTrue(wrapper.isVisible(), "Locked optional slot background must remain visible");
        assertEquals(enabled, wrapper.isAllowHitTest());
    }

    private static void assertFakeColumnProtocol(CompartmentMenu menu,
                                                 IModularUIHolderMenu holder,
                                                 SlotSemantic semantic,
                                                 int textureColumn) {
        List<Slot> slots = menu.getSlots(semantic);
        for (int row = 0; row < slots.size(); row++) {
            Slot slot = slots.get(row);
            assertTrue(slot instanceof FakeSlot, semantic.id() + " must remain an AE2 FakeSlot");
            assertTrue(menu.canDragTo(slot), semantic.id() + " must retain AE2 fake-slot drag routing");
            assertSame(slot, requireAeSlot(holder, slot).getSlot());
            if (row >= CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS) {
                if (!(slot instanceof CompartmentSlotLabel label)) {
                    throw new GameTestAssertException("Optional F/K slot lost its semantic label");
                }
                assertEquals(textureColumn, label.slotTextureColumn());
            }
        }
    }

    private static void assertStridedFakeSlots(List<Slot> slots, int firstIndex, int textureColumn) {
        assertEquals(CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_COUNT, slots.size());
        for (int row = 0; row < slots.size(); row++) {
            Slot slot = slots.get(row);
            assertEquals(firstIndex + row * 2, slot.index);
            assertTrue(slot instanceof FakeSlot, "F/K semantic must contain only FakeSlot instances");
            boolean optional = row >= CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS;
            assertEquals(optional, slot instanceof IOptionalSlot);
            if (optional) {
                if (!(slot instanceof CompartmentSlotLabel label)) {
                    throw new GameTestAssertException("Optional F/K slot lost its semantic label");
                }
                assertEquals(textureColumn, label.slotTextureColumn());
            }
        }
    }

    private static UIElement failingPanel(CompartmentMenu menu,
                                          AeMenuBridge bridge,
                                          MountLifecycleProbe lifecycleProbe) {
        UIElement panel = CompositeWarehousePanel.create(menu, bridge);
        panel.addChild(lifecycleProbe);
        panel.addEventListener(UIEvents.MUI_CHANGED, event -> {
            if (panel.getModularUI() == null) {
                return;
            }
            lifecycleProbe.mountCount++;
            throw new IllegalStateException("Injected plain warehouse mount failure");
        });
        return panel;
    }

    private static CompositeWarehouseBlockEntity inputHost() {
        return warehouse(ModBlocks.COMPOSITE_INPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static CompositeWarehouseBlockEntity outputHost() {
        return warehouse(ModBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
    }

    private static CompositeWarehouseBlockEntity warehouse(BlockState state) {
        return new CompositeWarehouseBlockEntity(BlockPos.ZERO, state);
    }

    private static CompositeWarehouseBlockEntity placedWarehouse(GameTestHelper helper,
                                                                 BlockPos position,
                                                                 BlockState state) {
        helper.setBlock(position, state);
        if (helper.getBlockEntity(position) instanceof CompositeWarehouseBlockEntity warehouse) {
            return warehouse;
        }
        throw new GameTestAssertException("Expected a placed composite warehouse at " + position);
    }

    private static IModularUIHolderMenu holder(CompartmentMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the plain warehouse menu");
    }

    private static AeItemSlot requireAeSlot(IModularUIHolderMenu holder, Slot slot) {
        if (holder.getItemSlot(slot) instanceof AeItemSlot wrapper) {
            return wrapper;
        }
        throw new GameTestAssertException("Existing menu slot " + slot.index + " has no AeItemSlot wrapper");
    }

    private static void assertSlotRange(List<Slot> slots,
                                        int firstIndex,
                                        int expectedCount) {
        assertEquals(expectedCount, slots.size());
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            assertEquals(firstIndex + index, slot.index);
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

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static final class TestCompartmentMenu extends CompartmentMenu {

        private TestCompartmentMenu(Player player, CompositeWarehouseBlockEntity host) {
            super(null, 5, player.getInventory(), host);
        }
    }

    private static final class MountLifecycleProbe extends UIElement {

        private int mountCount;
        private int removalCount;

        @Override
        protected void onRemoved() {
            this.removalCount++;
            super.onRemoved();
        }
    }
}
