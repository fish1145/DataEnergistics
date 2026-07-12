package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.TrinityPatternCoreBlock;
import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternSlot;
import com.fish_dan_.data_energistics.common.trinity.TrinityRefundDeliveryImpl;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.menu.SlotSemantic;

import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternCoreMenuTest {

    private TrinityPatternCoreMenuTest() {}

    @TestHolder("trinity_pattern_core_menu_pages_map_exact_backing_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void pagesMapExactBackingSlots(GameTestHelper helper) {
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        List<TrinityPatternCoreBlock> blocks = List.of(
                ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get(),
                ModBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE.get(),
                ModBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE.get());
        int[] capacities = { 64, 128, 512 };
        int[] pages = { 1, 2, 8 };
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        for (int tier = 0; tier < blocks.size(); tier++) {
            BlockPos pos = new BlockPos(tier + 1, 1, 1);
            helper.setBlock(pos, blocks.get(tier).defaultBlockState());
            TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
            int lastPage = pages[tier] - 1;
            int firstBackingSlot = lastPage * TrinityPatternCoreMenu.SLOTS_PER_PAGE;
            int lastBackingSlot = capacities[tier] - 1;
            assertTrue(core.trySetPattern(firstBackingSlot, encodedPattern));
            if (lastBackingSlot != firstBackingSlot) {
                assertTrue(core.trySetPattern(lastBackingSlot, encodedPattern));
            }

            TrinityPatternCoreMenu menu = new TrinityPatternCoreMenu(tier + 1, player.getInventory(), core);
            assertEquals(TrinityPatternCoreMenu.SLOTS_PER_PAGE, menu.pagePatternSlots().size());
            assertEquals(pages[tier], menu.totalPages);
            for (SlotSemantic row : TrinityPatternCoreMenu.PAGE_PATTERN_ROWS) {
                assertEquals(8, menu.getSlots(row).size());
            }

            if (lastPage > 0) {
                menu.setPage(lastPage);
                assertFalse(menu.isPageSelectionConfirmed());
                for (Slot slot : menu.pagePatternSlots()) {
                    assertFalse(slot.isActive());
                    assertFalse(slot.mayPlace(encodedPattern));
                    assertFalse(slot.mayPickup(player));
                }
                menu.confirmPage(lastPage);
            }

            assertTrue(menu.isPageSelectionConfirmed());
            assertStackMatches(encodedPattern, menu.pagePatternSlots().getFirst().getItem());
            assertStackMatches(encodedPattern, menu.pagePatternSlots().getLast().getItem());
        }
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_menu_refund_full_inventory_drops_queued_state")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundDropsQueuedStateWhenPlayerInventoryIsFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        assertTrue(core.trySetPattern(0, encodedPattern));
        core.appendPendingOutputs(
                new PatternRoute(UUID.randomUUID(), core.coreId(), 0),
                List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2))));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        TrinityPatternCoreMenu menu = new TrinityPatternCoreMenu(1, inventory, core);

        menu.refundAll();
        assertStackMatches(encodedPattern, core.pattern(0));
        assertFalse(core.hasWork());
        for (ItemStack stack : inventory.items) {
            assertTrue(stack.is(Items.STONE));
            assertEquals(64, stack.getCount());
        }
        assertDroppedItem(helper, player, Items.DIAMOND, 2);
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_menu_refund_prefers_mounted_host_ae")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundUsesMountedHostAeBeforePlayerInventory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        assertTrue(core.trySetPattern(0, encodedPattern));
        core.appendPendingOutputs(
                new PatternRoute(UUID.randomUUID(), core.coreId(), 0),
                List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2))));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        RecordingStorage storage = new RecordingStorage();
        RecordingPatternHost patternHost = new RecordingPatternHost(core, storage);
        assertTrue(core.bindPatternHost(patternHost));

        TrinityPatternCoreMenu menu = new TrinityPatternCoreMenu(1, inventory, core);
        menu.refundAll();

        assertEquals(2L, storage.insertedAmount);
        assertStackMatches(encodedPattern, core.pattern(0));
        assertFalse(core.hasWork());
        for (ItemStack stack : inventory.items) {
            assertTrue(stack.is(Items.STONE));
            assertEquals(64, stack.getCount());
        }
        helper.succeed();
    }

    private static ItemStack encodedOakPlanksPattern(GameTestHelper helper) {
        RecipeHolder<?> recipe = helper.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("oak_planks"))
                .orElseThrow();
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            throw new GameTestAssertException("Expected minecraft:oak_planks to be a crafting recipe");
        }
        RecipeHolder<CraftingRecipe> craftingRecipeHolder = new RecipeHolder<>(recipe.id(), craftingRecipe);
        ItemStack[] inputs = new ItemStack[9];
        inputs[0] = new ItemStack(Items.OAK_LOG);
        for (int slot = 1; slot < inputs.length; slot++) {
            inputs[slot] = ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeCraftingPattern(
                craftingRecipeHolder,
                inputs,
                new ItemStack(Items.OAK_PLANKS, 4),
                false,
                false);
    }

    private static void assertStackMatches(ItemStack expected, ItemStack actual) {
        assertTrue(ItemStack.isSameItemSameComponents(expected, actual));
        assertEquals(expected.getCount(), actual.getCount());
    }

    private static void assertDroppedItem(GameTestHelper helper, Player player, ItemLike item, int count) {
        AABB searchArea = AABB.ofSize(player.position(), 32.0D, 32.0D, 32.0D);
        boolean found = helper.getLevel().getEntitiesOfClass(ItemEntity.class, searchArea).stream()
                .map(ItemEntity::getItem)
                .anyMatch(stack -> stack.is(item.asItem()) && stack.getCount() == count);
        assertTrue(found);
    }

    /** Active host stub that exercises the same refund destination contract used by the real Trinity host. */
    private static final class RecordingPatternHost implements TrinityPatternCoreHost {

        private final TrinityPatternCore mountedCore;
        private final MEStorage storage;
        private boolean available = true;

        private RecordingPatternHost(TrinityPatternCore mountedCore, MEStorage storage) {
            this.mountedCore = mountedCore;
            this.storage = storage;
        }

        @Override
        public boolean isPatternCoreMounted(TrinityPatternCore core) {
            return this.available && this.mountedCore == core;
        }

        @Override
        public boolean tryRefundPatternCore(TrinityPatternCore core, Player player) {
            if (!isPatternCoreMounted(core)) {
                return false;
            }
            return core.tryRefundAll(new TrinityRefundDeliveryImpl(player, this.storage, IActionSource.empty()));
        }

        @Override
        public void onPatternCoreChanged(TrinityPatternCore core, TrinityPatternSlot.Change change) {}

        @Override
        public void onPatternCoreUnavailable(TrinityPatternCore core) {
            if (this.mountedCore == core) {
                this.available = false;
            }
        }
    }

    /** Storage sink that makes the menu's first refund destination directly observable. */
    private static final class RecordingStorage implements MEStorage {

        private long insertedAmount;

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode != Actionable.MODULATE) {
                return 0L;
            }
            this.insertedAmount += amount;
            return amount;
        }

        @Override
        public Component getDescription() {
            return Component.literal("Trinity pattern menu refund test storage");
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

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
