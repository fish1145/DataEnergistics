package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.TrinityPatternCoreBlock;
import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.SlotSemantic;

import java.util.List;

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

    @TestHolder("trinity_pattern_core_menu_refund_is_atomic")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundIsAtomic(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        assertTrue(core.trySetPattern(0, encodedPattern));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        TrinityPatternCoreMenu menu = new TrinityPatternCoreMenu(1, inventory, core);

        menu.refundAll();
        assertStackMatches(encodedPattern, core.pattern(0));
        for (ItemStack stack : inventory.items) {
            assertTrue(stack.is(Items.STONE));
            assertEquals(64, stack.getCount());
        }

        inventory.items.set(0, ItemStack.EMPTY);
        menu.refundAll();
        assertTrue(core.pattern(0).isEmpty());
        assertStackMatches(encodedPattern, inventory.items.getFirst());
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
}
