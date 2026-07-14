package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.MeCompositeOutputWarehouseMenu;
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
import appeng.api.stacks.GenericStack;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class MeCompositeOutputWarehouseUiGameTest {

    private MeCompositeOutputWarehouseUiGameTest() {}

    @TestHolder("me_output_compartment_mounts_ldlib2_with_existing_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountsLdlib2WithExistingSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MeCompositeOutputWarehouseMenu menu = new MeCompositeOutputWarehouseMenu(
                1,
                player.getInventory(),
                outputHost());
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("ME output compartment must mount a ModularUI during construction");
        }

        assertEquals(72, menu.slots.size());
        assertSlotRange(menu.getSlots(CompartmentMenu.COMPARTMENT_BUFFER), 0, 36);
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_HOTBAR), 36, 9);
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_INVENTORY), 45, 27);
        assertEquals(72, modularUI.getElementsByType(AeItemSlot.class).size());
        for (Slot slot : menu.slots) {
            assertSame(slot, requireAeSlot(holder, slot).getSlot());
        }
        assertElement(modularUI, CompartmentHostUi.ME_OUTPUT_ROOT_ID);
        assertElement(modularUI, MeOutputCompartmentPanel.PANEL_ID);
        assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);
        helper.succeed();
    }

    @TestHolder("me_output_compartment_ldlib2_slots_remain_read_only")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void ldlib2SlotsRemainReadOnly(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MeCompositeOutputWarehouseBlockEntity host = outputHost();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        helper.assertValueEqual(
                host.storage().insert(iron, 64L, false),
                64L,
                "ME output storage should accept the display fixture");
        MeCompositeOutputWarehouseMenu menu = new MeCompositeOutputWarehouseMenu(2, player.getInventory(), host);
        List<Slot> displaySlots = menu.getSlots(CompartmentMenu.COMPARTMENT_BUFFER);

        for (Slot slot : displaySlots) {
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                throw new GameTestAssertException("ME output display must retain AppEngSlot instances");
            }
            helper.assertFalse(slot.mayPlace(new ItemStack(Items.DIAMOND)), "Display slots must reject inserted items");
            helper.assertFalse(slot.mayPickup(player), "Display slots must reject item pickup");
            helper.assertFalse(menu.canDragTo(slot), "Display slots must reject drag distribution");
            helper.assertFalse(appEngSlot.isDraggable(), "Display slots must retain the AE2 non-draggable flag");
            helper.assertTrue(slot.remove(1).isEmpty(), "Display slots must not extract compartment contents");
        }

        menu.quickMoveStack(player, displaySlots.getFirst().index);
        helper.assertValueEqual(
                host.storage().amount(iron),
                64L,
                "Quick move from the display must not extract storage contents");

        Slot firstHotbarSlot = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst();
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 8);
        player.getInventory().setItem(0, diamonds.copy());
        menu.quickMoveStack(player, firstHotbarSlot.index);
        assertEquals(8, firstHotbarSlot.getItem().getCount());
        helper.assertTrue(
                ItemStack.isSameItemSameComponents(diamonds, firstHotbarSlot.getItem()),
                "Shift-click must not insert player items into the read-only display");

        GenericStack displayed = GenericStack.unwrapItemStack(
                requireAeSlot(holder(menu), displaySlots.getFirst()).getValue());
        if (displayed == null) {
            throw new GameTestAssertException("The first LDLib2 display slot must expose the stored key");
        }
        helper.assertTrue(displayed.what().equals(iron), "The LDLib2 display must preserve the stored AE key");
        helper.assertValueEqual(displayed.amount(), 64L, "The LDLib2 display must preserve the stored amount");
        helper.succeed();
    }

    @TestHolder("me_output_compartment_mount_failure_cleans_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountFailureCleansOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestCompartmentMenu menu = new TestCompartmentMenu(player, outputHost());
        MountLifecycleProbe lifecycleProbe = new MountLifecycleProbe();

        assertThrows(() -> CompartmentHostUi.mountMeOutput(
                menu,
                bridge -> failingPanel(menu, bridge, lifecycleProbe)));
        assertEquals(1, lifecycleProbe.mountCount);
        assertEquals(1, lifecycleProbe.removalCount);
        helper.succeed();
    }

    private static UIElement failingPanel(CompartmentMenu menu,
                                          AeMenuBridge bridge,
                                          MountLifecycleProbe lifecycleProbe) {
        UIElement panel = MeOutputCompartmentPanel.create(menu, bridge);
        panel.addChild(lifecycleProbe);
        panel.addEventListener(UIEvents.MUI_CHANGED, event -> {
            if (panel.getModularUI() == null) {
                return;
            }
            lifecycleProbe.mountCount++;
            throw new IllegalStateException("Injected compartment mount failure");
        });
        return panel;
    }

    private static MeCompositeOutputWarehouseBlockEntity outputHost() {
        return new MeCompositeOutputWarehouseBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().defaultBlockState());
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

        private TestCompartmentMenu(Player player, MeCompositeOutputWarehouseBlockEntity host) {
            super(null, 3, player.getInventory(), host);
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
