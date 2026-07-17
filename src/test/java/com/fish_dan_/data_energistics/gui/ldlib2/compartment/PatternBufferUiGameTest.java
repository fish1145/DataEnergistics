package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.MePatternBufferMenu;
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

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class PatternBufferUiGameTest {

    private static final int PATTERN_FIRST_MENU_INDEX = 0;
    private static final int DISPLAY_FIRST_MENU_INDEX = PATTERN_FIRST_MENU_INDEX +
            MePatternBufferBlockEntity.PATTERN_SLOT_COUNT;
    private static final int CATALYST_FIRST_MENU_INDEX = DISPLAY_FIRST_MENU_INDEX +
            CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT;
    private static final int FLUID_MENU_INDEX = CATALYST_FIRST_MENU_INDEX +
            CompartmentMenu.SHARED_CATALYST_SLOT_COUNT;
    private static final int KEY_MENU_INDEX = FLUID_MENU_INDEX + 1;
    private static final int EXTRA_FLUID_MENU_INDEX = KEY_MENU_INDEX + 1;
    private static final int PLAYER_HOTBAR_FIRST_MENU_INDEX = EXTRA_FLUID_MENU_INDEX + 1;
    private static final int PLAYER_INVENTORY_FIRST_MENU_INDEX = PLAYER_HOTBAR_FIRST_MENU_INDEX + 9;
    private static final int TOTAL_SLOT_COUNT = PLAYER_INVENTORY_FIRST_MENU_INDEX + 27;

    private PatternBufferUiGameTest() {}

    @TestHolder("pattern_buffer_mounts_complete_ldlib2_surface")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountsCompleteLdlib2Surface(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MePatternBufferMenu menu = new MePatternBufferMenu(1, player.getInventory(), patternBuffer());

        assertCompleteSurface(menu);
        helper.succeed();
    }

    @TestHolder("pattern_buffer_preserves_slot_protocols_and_empty_pattern_visual")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesSlotProtocolsAndEmptyPatternVisual(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MePatternBufferMenu menu = new MePatternBufferMenu(2, player.getInventory(), patternBuffer());
        IModularUIHolderMenu holder = holder(menu);

        List<Slot> patterns = menu.getSlots(CompartmentMenu.COMPARTMENT_PATTERN);
        assertEquals(MePatternBufferBlockEntity.PATTERN_SLOT_COUNT, menu.unlockedSlotCount);
        for (Slot slot : patterns) {
            assertTrue(slot.isActive(), "All 54 fixed pattern slots must remain active");
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                throw new GameTestAssertException("Pattern semantic lost its AppEngSlot");
            }
            assertTrue(appEngSlot.isSlotEnabled(), "All 54 fixed pattern slots must remain enabled");
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertEquals(
                    Boolean.TRUE,
                    wrapper.getSlotStyle().getImportant(PropertyRegistry.SHOW_SLOT_OVERLAY_ONLY_EMPTY));
            assertTrue(wrapper.getSlotStyle().getImportant(PropertyRegistry.SLOT_OVERLAY) instanceof SpriteTexture,
                    "LDLib2 must own the blank-pattern overlay texture");
            assertTrue(wrapper.getSlotStyle().showSlotOverlayOnlyEmpty(),
                    "The blank-pattern overlay must only render for an empty slot");
        }
        assertTrue(patterns.getFirst().mayPlace(encodedProcessingPattern()),
                "Pattern slots must continue accepting encoded processing patterns");
        assertTrue(!patterns.getFirst().mayPlace(AEItems.BLANK_PATTERN.stack()),
                "Pattern slots must continue rejecting blank patterns");
        assertTrue(!patterns.getFirst().mayPlace(new ItemStack(Items.DIAMOND)),
                "Pattern slots must continue rejecting ordinary items");

        for (Slot slot : menu.getSlots(CompartmentMenu.COMPARTMENT_PATTERN_BUFFER)) {
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                throw new GameTestAssertException("Aggregate display semantic lost its AppEngSlot");
            }
            assertTrue(!appEngSlot.isDraggable(), "Aggregate display slots must remain non-draggable");
        }
        assertFakeSlotProtocol(menu, holder, CompartmentMenu.COMPARTMENT_FLUID);
        assertFakeSlotProtocol(menu, holder, CompartmentMenu.COMPARTMENT_KEY);
        assertFakeSlotProtocol(menu, holder, CompartmentMenu.COMPARTMENT_EXTRA_FLUID);
        AeItemSlot fakeWrapper = requireAeSlot(holder, menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID).getFirst());
        assertThrows(() -> fakeWrapper.setValue(new ItemStack(Items.WATER_BUCKET), true));
        assertThrows(fakeWrapper::xeiPhantom);
        helper.succeed();
    }

    @TestHolder("pattern_buffer_mount_failure_cleans_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountFailureCleansOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TestCompartmentMenu menu = new TestCompartmentMenu(player, patternBuffer());
        MountLifecycleProbe lifecycleProbe = new MountLifecycleProbe();

        assertThrows(() -> CompartmentHostUi.mountPatternBuffer(
                menu,
                bridge -> failingPanel(menu, bridge, lifecycleProbe)));
        assertEquals(1, lifecycleProbe.mountCount);
        assertEquals(1, lifecycleProbe.removalCount);
        helper.succeed();
    }

    private static void assertCompleteSurface(MePatternBufferMenu menu) {
        IModularUIHolderMenu holder = holder(menu);
        ModularUI modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("Pattern buffer must mount a ModularUI during construction");
        }
        assertEquals(CompartmentType.PATTERN_BUFFER, menu.getCompartmentType());
        assertEquals(TOTAL_SLOT_COUNT, menu.slots.size());
        assertAppEngRange(
                menu.getSlots(CompartmentMenu.COMPARTMENT_PATTERN),
                PATTERN_FIRST_MENU_INDEX,
                MePatternBufferBlockEntity.PATTERN_SLOT_COUNT);
        assertAppEngRange(
                menu.getSlots(CompartmentMenu.COMPARTMENT_PATTERN_BUFFER),
                DISPLAY_FIRST_MENU_INDEX,
                CompartmentMenu.PATTERN_BUFFER_DISPLAY_SLOT_COUNT);
        assertAppEngRange(
                menu.getSlots(CompartmentMenu.COMPARTMENT_CATALYST),
                CATALYST_FIRST_MENU_INDEX,
                CompartmentMenu.SHARED_CATALYST_SLOT_COUNT);
        assertFakeRange(menu.getSlots(CompartmentMenu.COMPARTMENT_FLUID), FLUID_MENU_INDEX);
        assertFakeRange(menu.getSlots(CompartmentMenu.COMPARTMENT_KEY), KEY_MENU_INDEX);
        assertFakeRange(menu.getSlots(CompartmentMenu.COMPARTMENT_EXTRA_FLUID), EXTRA_FLUID_MENU_INDEX);
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_HOTBAR), PLAYER_HOTBAR_FIRST_MENU_INDEX, 9);
        assertSlotRange(menu.getSlots(SlotSemantics.PLAYER_INVENTORY), PLAYER_INVENTORY_FIRST_MENU_INDEX, 27);

        assertEquals(TOTAL_SLOT_COUNT, modularUI.getElementsByType(AeItemSlot.class).size());
        Set<AeItemSlot> wrappers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            AeItemSlot wrapper = requireAeSlot(holder, slot);
            assertSame(slot, wrapper.getSlot());
            assertTrue(wrappers.add(wrapper), "Every menu slot must have one distinct LDLib2 wrapper");
        }
        assertEquals(TOTAL_SLOT_COUNT, wrappers.size());
        assertElement(modularUI, CompartmentHostUi.PATTERN_BUFFER_ROOT_ID);
        assertElement(modularUI, CompartmentHostUi.TITLE_ID);
        assertElement(modularUI, CompartmentHostUi.HEADER_STATUS_ID);
        assertElement(modularUI, CompartmentHostUi.PLAYER_INVENTORY_TITLE_ID);
        assertElement(modularUI, PatternBufferCompartmentPanel.PANEL_ID);
        assertElement(modularUI, PatternBufferCompartmentPanel.PATTERN_PANEL_ID);
        assertElement(modularUI, PatternBufferCompartmentPanel.DISPLAY_PANEL_ID);
        assertElement(modularUI, PatternBufferCompartmentPanel.CATALYST_PANEL_ID);
        assertElement(modularUI, PatternBufferCompartmentPanel.COMPOSITE_PANEL_ID);
        assertElement(modularUI, AePlayerInventoryPanel.PANEL_ID);
        CompartmentUiTestAssertions.assertStyledTranslation(
                modularUI,
                CompartmentHostUi.TITLE_ID,
                "screen.data_energistics.compartment.title.pattern_buffer");
        CompartmentUiTestAssertions.assertStyledTranslation(
                modularUI,
                CompartmentHostUi.HEADER_STATUS_ID,
                "screen.data_energistics.compartment.aggregation_read_only");
        CompartmentUiTestAssertions.assertStyledTranslation(
                modularUI,
                CompartmentHostUi.PLAYER_INVENTORY_TITLE_ID,
                "container.inventory");
        CompartmentUiTestAssertions.assertHeaderGeometry(CompartmentType.PATTERN_BUFFER, 14, 256);
    }

    private static void assertAppEngRange(List<Slot> slots, int firstIndex, int expectedCount) {
        assertSlotRange(slots, firstIndex, expectedCount);
        for (Slot slot : slots) {
            assertTrue(slot instanceof AppEngSlot, "Semantic must retain AppEngSlot instances");
        }
    }

    private static void assertFakeRange(List<Slot> slots, int firstIndex) {
        assertSlotRange(slots, firstIndex, 1);
        assertTrue(slots.getFirst() instanceof FakeSlot, "Composite-key semantic must retain its AE2 FakeSlot");
    }

    private static void assertFakeSlotProtocol(CompartmentMenu menu,
                                               IModularUIHolderMenu holder,
                                               SlotSemantic semantic) {
        Slot slot = menu.getSlots(semantic).getFirst();
        assertTrue(slot instanceof FakeSlot, semantic.id() + " must remain an AE2 FakeSlot");
        assertTrue(menu.canDragTo(slot), semantic.id() + " must retain AE2 fake-slot drag routing");
        assertSame(slot, requireAeSlot(holder, slot).getSlot());
    }

    private static UIElement failingPanel(CompartmentMenu menu,
                                          AeMenuBridge bridge,
                                          MountLifecycleProbe lifecycleProbe) {
        UIElement panel = PatternBufferCompartmentPanel.create(menu, bridge);
        panel.addChild(lifecycleProbe);
        panel.addEventListener(UIEvents.MUI_CHANGED, event -> {
            if (panel.getModularUI() == null) {
                return;
            }
            lifecycleProbe.mountCount++;
            throw new IllegalStateException("Injected pattern-buffer mount failure");
        });
        return panel;
    }

    private static MePatternBufferBlockEntity patternBuffer() {
        return new MePatternBufferBlockEntity(
                BlockPos.ZERO,
                ModBlocks.ME_PATTERN_BUFFER.get().defaultBlockState());
    }

    private static ItemStack encodedProcessingPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)));
    }

    private static IModularUIHolderMenu holder(CompartmentMenu menu) {
        if (menu instanceof IModularUIHolderMenu holder) {
            return holder;
        }
        throw new GameTestAssertException("LDLib2 did not enhance the pattern-buffer menu");
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

    private static final class TestCompartmentMenu extends CompartmentMenu {

        private TestCompartmentMenu(Player player, MePatternBufferBlockEntity host) {
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
