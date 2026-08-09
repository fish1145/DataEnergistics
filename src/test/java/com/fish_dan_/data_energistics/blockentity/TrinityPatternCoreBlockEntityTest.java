package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.TrinityPatternCoreBlock;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternCoreReloadEpoch;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternCoreBlockEntityTest {

    private static final UUID HOST_ID = UUID.fromString("f14921fa-5649-4f5f-98c3-41af0ea28b12");

    private TrinityPatternCoreBlockEntityTest() {}

    @TestHolder("trinity_pattern_core_mining_drops_separate_patterns_and_retain_work")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void miningDropsSeparatePatternsAndRetainQueuedWorkAcrossPhysicalTiers(GameTestHelper helper) {
        ItemStack oakPattern = encodedOakPlanksPattern(helper);
        ItemStack cakePattern = encodedCakePattern(helper);
        List<TrinityPatternCoreBlock> blocks = List.of(
                DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get(),
                DEBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE.get(),
                DEBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE.get());
        int[] capacities = { 64, 128, 512 };

        for (int index = 0; index < blocks.size(); index++) {
            TrinityPatternCoreBlock block = blocks.get(index);
            BlockPos sourcePos = new BlockPos(index + 1, 1, 1);
            helper.setBlock(sourcePos, block.defaultBlockState());
            TrinityPatternCoreBlockEntity source = helper.getBlockEntity(sourcePos);
            assertEquals(capacities[index], source.patternCapacity());

            int separatePatternSlot = capacities[index] - 2;
            int retainedWorkSlot = capacities[index] - 1;
            PatternRoute route = new PatternRoute(HOST_ID, source.coreId(), retainedWorkSlot);
            assertTrue(source.trySetPattern(separatePatternSlot, cakePattern));
            assertTrue(source.trySetPattern(retainedWorkSlot, oakPattern));
            assertTrue(source.enqueueBatch(route, oakPattern, oakLogInputs(), 40L + index));
            source.appendPendingOutputs(
                    route,
                    List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, index + 1))));
            List<ItemStack> drops = Block.getDrops(
                    source.getBlockState(),
                    helper.getLevel(),
                    helper.absolutePos(sourcePos),
                    source);

            assertEquals(3, drops.size());
            ItemStack coreDrop = drops.getFirst();
            assertTrue(coreDrop.is(block.asItem()));
            assertTrue(ItemStack.isSameItemSameComponents(cakePattern, drops.get(1)));
            assertTrue(ItemStack.isSameItemSameComponents(oakPattern, drops.get(2)));
            CustomData blockEntityData = coreDrop.get(DataComponents.BLOCK_ENTITY_DATA);
            assertTrue(blockEntityData != null);

            BlockPos restoredPos = new BlockPos(index + 1, 1, 3);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, coreDrop);
            helper.placeAt(player, coreDrop, restoredPos.below(), Direction.UP);
            TrinityPatternCoreBlockEntity restored = helper.getBlockEntity(restoredPos);
            assertEquals(source.coreId(), restored.coreId());
            assertEquals(0, restored.occupiedPatternSlots().size());
            assertTrue(restored.pattern(separatePatternSlot).isEmpty());
            assertTrue(restored.pattern(retainedWorkSlot).isEmpty());
            assertEquals(1, restored.queuedBatchCount(retainedWorkSlot));
            assertEquals(route, restored.queuedBatches(retainedWorkSlot).getFirst().route());
            assertTrue(restored.queuedBatches(retainedWorkSlot).getFirst().inputs().getFirst().is(Items.OAK_LOG));
            assertEquals(index + 1L, restored.pendingOutputs(route).getFirst().amount());
            assertEquals(0, restored.executeOwnedBatches(HOST_ID, 100L + index));
            assertEquals(1, restored.queuedBatchCount(retainedWorkSlot));
            assertEquals(index + 1L, restored.pendingOutputs(route).getFirst().amount());
        }
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_legacy_v2_break_round_trip")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void migratedLegacyV2CoreBreaksAndRetainsWorkThroughItsRealDrop(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos migratedPos = new BlockPos(3, 1, 1);
        BlockPos restoredPos = new BlockPos(3, 1, 3);
        TrinityPatternCoreBlock block = DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get();
        helper.setBlock(sourcePos, block.defaultBlockState());
        TrinityPatternCoreBlockEntity source = helper.getBlockEntity(sourcePos);
        ItemStack oakPattern = encodedOakPlanksPattern(helper);
        ItemStack cakePattern = encodedCakePattern(helper);
        int cakeSlot = 62;
        int workSlot = 63;
        PatternRoute route = new PatternRoute(HOST_ID, source.coreId(), workSlot);
        assertTrue(source.trySetPattern(cakeSlot, cakePattern));
        assertTrue(source.trySetPattern(workSlot, oakPattern));
        assertTrue(source.enqueueBatch(route, oakPattern, oakLogInputs(), 31L));
        source.appendPendingOutputs(route, List.of(TrinityItemAmount.of(new ItemStack(Items.DIAMOND, 2))));
        CompoundTag currentState = new CompoundTag();
        source.writeToTag(currentState, helper.getLevel().registryAccess());

        helper.setBlock(migratedPos, block.defaultBlockState());
        TrinityPatternCoreBlockEntity migrated = helper.getBlockEntity(migratedPos);
        migrated.loadTag(asLegacyV2State(currentState), helper.getLevel().registryAccess());
        assertTrue(migrated.isCoreStateReady());
        assertEquals(source.coreId(), migrated.coreId());
        assertEquals(1, migrated.queuedBatchCount(workSlot));
        assertEquals(2L, migrated.pendingOutputs(route).getFirst().amount());
        CompoundTag rewritten = new CompoundTag();
        migrated.saveAdditional(rewritten, helper.getLevel().registryAccess());
        assertEquals(3, rewritten.getInt("version"));
        assertTrue(rewritten.contains("refund_outbox", Tag.TAG_COMPOUND));

        ServerPlayer player = makeServerPlayer(helper);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(Items.NETHERITE_PICKAXE));
        ServerPlayerGameMode gameMode = new DirectDestroyGameMode(player, GameType.SURVIVAL);
        assertTrue(migrated.getBlockState().canHarvestBlock(
                helper.getLevel(),
                helper.absolutePos(migratedPos),
                player));
        assertTrue(gameMode.destroyBlock(helper.absolutePos(migratedPos)));
        assertTrue(helper.getBlockState(migratedPos).isAir());

        List<ItemEntity> itemDrops = helper.getEntities(EntityType.ITEM);
        assertEquals(3, itemDrops.size());
        ItemStack coreDrop = ItemStack.EMPTY;
        boolean droppedOakPattern = false;
        boolean droppedCakePattern = false;
        for (ItemEntity itemDrop : itemDrops) {
            ItemStack dropped = itemDrop.getItem();
            if (dropped.is(block.asItem())) {
                assertTrue(coreDrop.isEmpty());
                coreDrop = dropped.copy();
            } else if (ItemStack.isSameItemSameComponents(oakPattern, dropped)) {
                assertFalse(droppedOakPattern);
                droppedOakPattern = true;
            } else if (ItemStack.isSameItemSameComponents(cakePattern, dropped)) {
                assertFalse(droppedCakePattern);
                droppedCakePattern = true;
            }
        }
        assertFalse(coreDrop.isEmpty());
        assertTrue(droppedOakPattern);
        assertTrue(droppedCakePattern);

        Player placementPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        placementPlayer.setItemInHand(InteractionHand.MAIN_HAND, coreDrop);
        helper.placeAt(placementPlayer, coreDrop, restoredPos.below(), Direction.UP);
        TrinityPatternCoreBlockEntity restored = helper.getBlockEntity(restoredPos);
        assertTrue(restored.isCoreStateReady());
        assertEquals(source.coreId(), restored.coreId());
        assertTrue(restored.pattern(cakeSlot).isEmpty());
        assertTrue(restored.pattern(workSlot).isEmpty());
        assertEquals(1, restored.queuedBatchCount(workSlot));
        assertEquals(route, restored.queuedBatches(workSlot).getFirst().route());
        assertEquals(2L, restored.pendingOutputs(route).getFirst().amount());
        assertFalse(restored.hasPendingRefund(HOST_ID));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_reload_epoch_refreshes_cache")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void dataReloadEpochRefreshesPatternCache(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        assertTrue(core.trySetPattern(0, encodedOakPlanksPattern(helper)));
        long revisionBeforeReload = core.revision();
        var directoryBeforeReload = core.patternCacheSnapshot();
        var cachedBeforeReload = core.cachedPattern(0);
        assertTrue(cachedBeforeReload != null);

        TrinityPatternCoreReloadEpoch.advance();
        core.serverTick();

        assertEquals(revisionBeforeReload, core.revision());
        assertTrue(directoryBeforeReload == core.patternCacheSnapshot());
        assertTrue(cachedBeforeReload == core.cachedPattern(0));
        assertEquals(0L, cachedBeforeReload.runtimeBindingRevision());
        assertTrue(core.decodedPattern(0) != null);
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_defers_level_dependent_state_validation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void defersStateValidationUntilLevelAndRejectsInvalidInitialState(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        TrinityPatternCoreBlock block = DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get();
        helper.setBlock(sourcePos, block.defaultBlockState());
        TrinityPatternCoreBlockEntity source = helper.getBlockEntity(sourcePos);
        ItemStack encodedPattern = encodedOakPlanksPattern(helper);
        assertTrue(source.trySetPattern(0, encodedPattern));

        CompoundTag validState = new CompoundTag();
        source.writeToTag(validState, helper.getLevel().registryAccess());
        TrinityPatternCoreBlockEntity deferred = new TrinityPatternCoreBlockEntity(
                helper.absolutePos(new BlockPos(2, 1, 1)), block.defaultBlockState());
        deferred.loadTag(validState, helper.getLevel().registryAccess());
        assertFalse(deferred.isCoreStateReady());
        deferred.setLevel(helper.getLevel());
        assertTrue(deferred.isCoreStateReady());
        assertEquals(source.coreId(), deferred.coreId());
        assertTrue(ItemStack.isSameItemSameComponents(encodedPattern, deferred.pattern(0)));
        long loadedRevision = deferred.revision();
        deferred.setLevel(helper.getLevel());
        assertEquals(loadedRevision, deferred.revision());

        CompoundTag invalidState = validState.copy();
        setFirstDefinitionRecipeId(invalidState, "minecraft:crafting_table");
        TrinityPatternCoreBlockEntity rejected = new TrinityPatternCoreBlockEntity(
                helper.absolutePos(new BlockPos(3, 1, 1)), block.defaultBlockState());
        rejected.loadTag(invalidState, helper.getLevel().registryAccess());
        rejected.setLevel(helper.getLevel());
        assertFalse(rejected.isCoreStateReady());
        CompoundTag retainedState = new CompoundTag();
        rejected.saveAdditional(retainedState, helper.getLevel().registryAccess());
        assertEquals("minecraft:crafting_table", firstDefinitionRecipeId(retainedState));
        assertEquals(validState.getUUID("core_id"), retainedState.getUUID("core_id"));
        CompoundTag directRetainedState = new CompoundTag();
        rejected.writeToTag(directRetainedState, helper.getLevel().registryAccess());
        assertEquals("minecraft:crafting_table", firstDefinitionRecipeId(directRetainedState));
        assertIllegalState(() -> rejected.trySetPattern(0, encodedPattern));

        ItemStack invalidItem = new ItemStack(block);
        source.saveToItem(invalidItem, helper.getLevel().registryAccess());
        CompoundTag invalidItemState = invalidItem.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
        setFirstDefinitionRecipeId(invalidItemState, "minecraft:crafting_table");
        invalidItem.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(invalidItemState));
        BlockPos invalidItemPos = new BlockPos(4, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, invalidItem);
        helper.placeAt(player, invalidItem, invalidItemPos.below(), Direction.UP);
        TrinityPatternCoreBlockEntity invalidItemCore = helper.getBlockEntity(invalidItemPos);
        assertFalse(invalidItemCore.isCoreStateReady());
        CompoundTag retainedItemState = new CompoundTag();
        invalidItemCore.saveAdditional(retainedItemState, helper.getLevel().registryAccess());
        assertEquals("minecraft:crafting_table", firstDefinitionRecipeId(retainedItemState));

        source.serverTick();
        UUID committedId = source.coreId();
        ItemStack committedPattern = source.pattern(0);
        source.loadTag(invalidState, helper.getLevel().registryAccess());
        assertTrue(source.isCoreStateReady());
        assertEquals(committedId, source.coreId());
        assertTrue(ItemStack.matches(committedPattern, source.pattern(0)));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_rejects_non_crafting_patterns")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsBlankProcessingAndOrdinaryItemsUsingRealDecoder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        ItemStack processingPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)));

        assertFalse(core.trySetPattern(0, AEItems.BLANK_PATTERN.stack()));
        assertFalse(core.trySetPattern(0, processingPattern));
        assertFalse(core.trySetPattern(0, new ItemStack(Items.STICK)));
        assertTrue(core.pattern(0).isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_core_routes_container_remainders_before_primary_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void routesContainerRemaindersBeforePrimaryOutput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE.get().defaultBlockState());
        TrinityPatternCoreBlockEntity core = helper.getBlockEntity(pos);
        ItemStack encodedPattern = encodedCakePattern(helper);
        PatternRoute route = new PatternRoute(HOST_ID, core.coreId(), 0);

        assertTrue(core.trySetPattern(0, encodedPattern));
        assertTrue(core.enqueueBatch(route, encodedPattern, cakeInputs(), 10L));
        assertEquals(1, core.executeOwnedBatches(HOST_ID, 11L));

        List<TrinityItemAmount> outputs = core.pendingOutputs(route);
        assertEquals(2, outputs.size());
        assertTrue(outputs.get(0).key().is(Items.BUCKET));
        assertEquals(3L, outputs.get(0).amount());
        assertTrue(outputs.get(1).key().is(Items.CAKE));
        assertEquals(1L, outputs.get(1).amount());
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
        return PatternDetailsHelper.encodeCraftingPattern(
                craftingRecipeHolder,
                oakLogInputs().toArray(ItemStack[]::new),
                new ItemStack(Items.OAK_PLANKS, 4),
                false,
                false);
    }

    private static ItemStack encodedCakePattern(GameTestHelper helper) {
        RecipeHolder<?> recipe = helper.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("cake"))
                .orElseThrow();
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            throw new GameTestAssertException("Expected minecraft:cake to be a crafting recipe");
        }
        RecipeHolder<CraftingRecipe> craftingRecipeHolder = new RecipeHolder<>(recipe.id(), craftingRecipe);
        return PatternDetailsHelper.encodeCraftingPattern(
                craftingRecipeHolder,
                cakeInputs().toArray(ItemStack[]::new),
                new ItemStack(Items.CAKE),
                false,
                false);
    }

    private static List<ItemStack> oakLogInputs() {
        ArrayList<ItemStack> inputs = new ArrayList<>(9);
        inputs.add(new ItemStack(Items.OAK_LOG));
        for (int slot = 1; slot < 9; slot++) {
            inputs.add(ItemStack.EMPTY);
        }
        return inputs;
    }

    private static List<ItemStack> cakeInputs() {
        return List.of(
                new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR),
                new ItemStack(Items.EGG),
                new ItemStack(Items.SUGAR),
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.WHEAT));
    }

    private static CompoundTag asLegacyV2State(CompoundTag currentState) {
        CompoundTag legacyState = currentState.copy();
        legacyState.putInt("version", 2);
        legacyState.remove("refund_outbox");
        ListTag slots = legacyState.getList("slots", Tag.TAG_COMPOUND);
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            ListTag definitions = slots.getCompound(slotIndex).getList("definitions", Tag.TAG_COMPOUND);
            for (int definitionIndex = 0; definitionIndex < definitions.size(); definitionIndex++) {
                definitions.getCompound(definitionIndex).putBoolean("resolved", true);
            }
        }
        return legacyState;
    }

    private static ServerPlayer makeServerPlayer(GameTestHelper helper) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pattern-core-breaker"),
                ClientInformation.createDefault());
    }

    private static void setFirstDefinitionRecipeId(CompoundTag state, String recipeId) {
        ListTag slots = state.getList("slots", Tag.TAG_COMPOUND);
        CompoundTag slot = slots.getCompound(0);
        ListTag definitions = slot.getList("definitions", Tag.TAG_COMPOUND);
        definitions.getCompound(0).putString("recipe_id", recipeId);
    }

    private static String firstDefinitionRecipeId(CompoundTag state) {
        ListTag slots = state.getList("slots", Tag.TAG_COMPOUND);
        ListTag definitions = slots.getCompound(0).getList("definitions", Tag.TAG_COMPOUND);
        return definitions.getCompound(0).getString("recipe_id");
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

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    private static final class DirectDestroyGameMode extends ServerPlayerGameMode {

        private DirectDestroyGameMode(ServerPlayer player, GameType gameType) {
            super(player);
            setGameModeForPlayer(gameType, null);
        }
    }
}
