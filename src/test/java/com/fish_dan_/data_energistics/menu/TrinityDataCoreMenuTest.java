package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.pattern.MountedCorePatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.PlayerInventoryRefundDelivery;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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

import appeng.api.crafting.PatternDetailsHelper;

import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityDataCoreMenuTest {

    private TrinityDataCoreMenuTest() {}

    @TestHolder("trinity_data_core_refund_full_inventory_drops_queued_aggregate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundDropsQueuedAggregateWithoutClearingPatterns(GameTestHelper helper) {
        RefundAggregate aggregate = createAggregate(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        assertTrue(aggregate.catalog().tryRefundAll(new PlayerInventoryRefundDelivery(player, null, null)));

        assertStackMatches(aggregate.pattern(), aggregate.first().pattern(0));
        assertStackMatches(aggregate.pattern(), aggregate.second().pattern(0));
        assertFalse(aggregate.first().hasWork());
        assertFalse(aggregate.second().hasWork());
        for (ItemStack stack : inventory.items) {
            assertTrue(stack.is(Items.STONE));
            assertEquals(64, stack.getCount());
        }
        assertDroppedItem(helper, player, Items.DIAMOND, 2);
        assertDroppedItem(helper, player, Items.GOLD_INGOT, 3);
        helper.succeed();
    }

    @TestHolder("trinity_data_core_refund_returns_queued_aggregate_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundDeliversQueuedStateToPlayerInventoryWhenAvailable(GameTestHelper helper) {
        RefundAggregate aggregate = createAggregate(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.items.set(0, ItemStack.EMPTY);
        inventory.items.set(1, ItemStack.EMPTY);
        assertTrue(aggregate.catalog().tryRefundAll(new PlayerInventoryRefundDelivery(player, null, null)));

        assertStackMatches(aggregate.pattern(), aggregate.first().pattern(0));
        assertStackMatches(aggregate.pattern(), aggregate.second().pattern(0));
        assertFalse(aggregate.first().hasWork());
        assertFalse(aggregate.second().hasWork());
        assertHasStack(inventory.items, Items.DIAMOND, 2);
        assertHasStack(inventory.items, Items.GOLD_INGOT, 3);
        helper.succeed();
    }

    private static RefundAggregate createAggregate(GameTestHelper helper) {
        BlockPos firstPosition = new BlockPos(1, 1, 1);
        BlockPos secondPosition = new BlockPos(2, 1, 1);
        helper.setBlock(firstPosition, ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        helper.setBlock(secondPosition, ModBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity first = helper.getBlockEntity(firstPosition);
        TrinityPatternCoreBlockEntity second = helper.getBlockEntity(secondPosition);
        ItemStack pattern = encodedOakPlanksPattern(helper);
        assertTrue(first.trySetPattern(0, pattern));
        assertTrue(second.trySetPattern(0, pattern));
        MountedCorePatternCatalog catalog = new MountedCorePatternCatalog(UUID.randomUUID());
        first.appendPendingOutputs(
                new PatternRoute(catalog.hostId(), first.coreId(), 0),
                List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2))));
        second.appendPendingOutputs(
                new PatternRoute(catalog.hostId(), second.coreId(), 0),
                List.of(TrinityItemAmount.of(new ItemStack(Items.GOLD_INGOT, 3))));
        TrinityPatternCatalog.RebuildResult rebuilt = catalog.rebuild(List.of(
                new TrinityPatternCatalog.CoreMount(firstPosition, 64, first),
                new TrinityPatternCatalog.CoreMount(secondPosition, 64, second)));
        assertTrue(rebuilt.valid());
        return new RefundAggregate(first, second, pattern, catalog);
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

    private static void assertHasStack(List<ItemStack> stacks, ItemLike item, int count) {
        for (ItemStack stack : stacks) {
            if (stack.is(item.asItem()) && stack.getCount() == count) {
                return;
            }
        }
        throw new GameTestAssertException("Expected " + count + " of " + item);
    }

    private static void assertDroppedItem(GameTestHelper helper, Player player, ItemLike item, int count) {
        AABB searchArea = AABB.ofSize(player.position(), 32.0D, 32.0D, 32.0D);
        boolean found = helper.getLevel().getEntitiesOfClass(ItemEntity.class, searchArea).stream()
                .map(ItemEntity::getItem)
                .anyMatch(stack -> stack.is(item.asItem()) && stack.getCount() == count);
        assertTrue(found);
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

    private record RefundAggregate(TrinityPatternCoreBlockEntity first,
                                   TrinityPatternCoreBlockEntity second,
                                   ItemStack pattern,
                                   TrinityPatternCatalog catalog) {}
}
