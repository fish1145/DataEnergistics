package com.fish_dan_.data_energistics.gui.ldlib2;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.OptionalFakeSlot;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class AeMenuBridgeGameTest {

    private AeMenuBridgeGameTest() {}

    @TestHolder("ae_menu_bridge_maps_existing_slots_without_duplicates")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsExistingSlotsWithoutDuplicates(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestAeMenu menu = playerInventoryMenu(player);
        List<Slot> originalSlots = List.copyOf(menu.slots);
        AeMenuBridge bridge = AeMenuBridge.create(menu);
        UIElement root = new UIElement();
        List<AeItemSlot> wrappers = new ArrayList<>();
        for (Slot slot : originalSlots) {
            AeItemSlot wrapper = bridge.wrap(slot);
            wrappers.add(wrapper);
            root.addChild(wrapper);
        }
        ModularUI modularUI = ModularUI.of(UI.of(root), player);

        bridge.mount(modularUI);

        IModularUIHolderMenu holder = holder(menu);
        assertSame(modularUI, holder.getModularUI());
        assertSame(menu, modularUI.getMenu());
        assertEquals(originalSlots.size(), menu.slots.size());
        for (int index = 0; index < originalSlots.size(); index++) {
            Slot originalSlot = originalSlots.get(index);
            assertSame(originalSlot, menu.slots.get(index));
            assertEquals(index, originalSlot.index);
            assertSame(wrappers.get(index), holder.getItemSlot(originalSlot));
        }
        helper.succeed();
    }

    @TestHolder("ae_item_slot_preserves_appeng_presentation_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesAppEngPresentationState(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MutableInternalInventory inventory = new MutableInternalInventory(new ItemStack(Items.DIAMOND, 32));
        AppEngSlot appEngSlot = new AppEngSlot(inventory, 0);
        appEngSlot.setHideAmount(true);
        boolean[] optionalEnabled = { false };
        OptionalFakeSlot optionalSlot = new OptionalFakeSlot(
                new MutableInternalInventory(ItemStack.EMPTY),
                ignored -> optionalEnabled[0],
                0,
                0);
        TestAeMenu menu = new TestAeMenu(player, appEngSlot, optionalSlot);
        AeMenuBridge bridge = AeMenuBridge.create(menu);
        AeItemSlot itemElement = bridge.wrap(appEngSlot);
        AeItemSlot optionalElement = bridge.wrap(optionalSlot);
        UIElement root = new UIElement().addChildren(itemElement, optionalElement);
        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        bridge.mount(modularUI);

        assertEquals(32, appEngSlot.getItem().getCount());
        assertEquals(1, itemElement.getValue().getCount());
        assertTrue(itemElement.isVisible(), "active AppEngSlot must be visible");
        assertTrue(itemElement.isAllowHitTest(), "active AppEngSlot must allow hit testing");

        appEngSlot.setActive(false);
        itemElement.refreshSlotPresentation();
        assertFalse(itemElement.isVisible(), "inactive AppEngSlot must be hidden");
        assertFalse(itemElement.isAllowHitTest(), "inactive AppEngSlot must reject hit testing");

        optionalElement.refreshSlotPresentation();
        assertTrue(optionalElement.isVisible(), "disabled optional background must remain visible");
        assertFalse(optionalElement.isAllowHitTest(), "disabled optional slot must reject hit testing");
        modularUI.getStyleEngine().calculateStyle();
        assertEquals(0.2f, optionalElement.getStyle().opacity());
        optionalSlot.setRenderDisabled(false);
        optionalElement.refreshSlotPresentation();
        assertFalse(optionalElement.isVisible(), "optional slot must hide when disabled rendering is off");
        helper.succeed();
    }

    @TestHolder("ae_menu_bridge_rejects_foreign_and_duplicate_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsForeignAndDuplicateSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestAeMenu menu = playerInventoryMenu(player);
        AeMenuBridge bridge = AeMenuBridge.create(menu);
        Slot firstSlot = menu.slots.getFirst();

        AeItemSlot wrapper = bridge.wrap(firstSlot);
        assertThrows(() -> bridge.wrap(firstSlot));
        assertThrows(() -> bridge.wrap(new Slot(new SimpleContainer(1), 0, 0, 0)));
        assertThrows(() -> wrapper.bind(firstSlot));
        assertThrows(wrapper::xeiPhantom);
        helper.succeed();
    }

    @TestHolder("ae_menu_bridge_failed_mount_is_terminal")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void failedMountIsTerminal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestAeMenu menu = playerInventoryMenu(player);
        AeMenuBridge bridge = AeMenuBridge.create(menu);
        Slot slot = menu.slots.getFirst();
        AeItemSlot wrapper = bridge.wrap(slot);
        UIElement root = new UIElement().addChild(wrapper);
        RemovalTrackingElement cleanupProbe = new RemovalTrackingElement();
        root.addChild(cleanupProbe);
        root.addEventListener(UIEvents.MUI_CHANGED, event -> {
            throw new IllegalStateException("Test mount listener failure");
        });

        assertThrows(() -> bridge.mount(ModularUI.of(UI.of(root), player)));
        assertEquals(1, cleanupProbe.removalCount);
        assertThrows(() -> bridge.wrap(menu.slots.get(1)));
        assertThrows(() -> bridge.mount(ModularUI.of(UI.of(new UIElement()), player)));
        assertSame(null, holder(menu).getItemSlot(slot));
        helper.succeed();
    }

    @TestHolder("ae_menu_bridge_rejects_incomplete_and_repeated_mounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsIncompleteAndRepeatedMounts(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestAeMenu incompleteMenu = playerInventoryMenu(player);
        AeMenuBridge incompleteBridge = AeMenuBridge.create(incompleteMenu);
        incompleteBridge.wrap(incompleteMenu.slots.getFirst());
        assertThrows(() -> incompleteBridge.mount(ModularUI.of(UI.of(new UIElement()), player)));

        TestAeMenu mountedMenu = playerInventoryMenu(player);
        AeMenuBridge mountedBridge = AeMenuBridge.create(mountedMenu);
        ModularUI firstUi = ModularUI.of(UI.of(new UIElement()), player);
        mountedBridge.mount(firstUi);
        assertThrows(() -> mountedBridge.mount(ModularUI.of(UI.of(new UIElement()), player)));
        assertSame(firstUi, holder(mountedMenu).getModularUI());
        helper.succeed();
    }

    /** Returns the runtime mixin interface or reports an invalid LDLib2 GameTest environment. */
    private static IModularUIHolderMenu holder(AEBaseMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the AE menu");
    }

    /** Creates an unmounted bridge fixture with AE2's normal 27 plus 9 player semantic groups. */
    private static TestAeMenu playerInventoryMenu(Player player) {
        TestAeMenu menu = new TestAeMenu(player);
        menu.addPlayerInventory();
        return menu;
    }

    /** Requires the supplied action to reject its invalid bridge operation. */
    private static void assertThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    /** Requires exact object identity. */
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected identical objects");
        }
    }

    /** Requires equal integer values. */
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    /** Requires equal floating-point values for exact UI opacity constants. */
    private static void assertEquals(float expected, float actual) {
        if (Float.compare(expected, actual) != 0) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    /** Requires a true condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Requires a false condition. */
    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Minimal AE menu that adds supplied AppEng slots through AE2's normal semantic path. */
    private static final class TestAeMenu extends AEBaseMenu {

        private TestAeMenu(Player player, Slot... slots) {
            super(null, 1, player.getInventory(), null);
            for (Slot slot : slots) {
                addSlot(slot, SlotSemantics.STORAGE);
            }
        }

        private void addPlayerInventory() {
            createPlayerInventorySlots(getPlayer().getInventory());
        }
    }

    /** Element whose callback proves that a failed mount still releases the complete ModularUI tree. */
    private static final class RemovalTrackingElement extends UIElement {

        private int removalCount;

        @Override
        protected void onRemoved() {
            this.removalCount++;
            super.onRemoved();
        }
    }

    /** One-slot inventory whose contents make AppEngSlot presentation changes directly observable. */
    private static final class MutableInternalInventory implements InternalInventory {

        private ItemStack stack;

        private MutableInternalInventory(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return this.stack;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            this.stack = stack;
        }
    }
}
