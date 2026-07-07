package com.fish_dan_.data_energistics.common.crafting.flower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.stream.Stream;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DigitalConstructFlowerCraftingRuntimeTest {

    private DigitalConstructFlowerCraftingRuntimeTest() {}

    @TestHolder("digital_construct_flower_cpu_partitions_require_formed_structure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuPartitionsRequireFormedStructure(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(false);

        helper.assertValueEqual(flower.getCpuPartitions().size(), 0, "Unformed flower should not expose CPUs");

        flower.loadTag(formedTag(), HolderLookup.Provider.create(Stream.empty()));

        helper.assertValueEqual(flower.getCpuPartitions().size(), 0, "Formed main structure should not expose CPU partitions");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().storageBytes(),
                0L,
                "Formed main structure should not contribute crafting storage");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_cpu_contribution_rebuilds_partitions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuContributionRebuildsPartitions(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(true);

        flower.setCpuContribution("petal", DigitalConstructFlowerCpuContribution.of(1024L, 2, 2));

        helper.assertValueEqual(flower.getCpuPartitions().size(), 2, "Child contribution should add CPU partitions");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().coProcessors(),
                2,
                "Child contribution should add co-processors");
        flower.clearCpuContribution("petal");
        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                0,
                "Clearing child contribution should remove child CPU partitions");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_cpu_contribution_rejects_null_without_changing_partitions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuContributionRejectsNullWithoutChangingPartitions(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(true);
        int partitionCount = flower.getCpuPartitions().size();

        try {
            flower.setCpuContribution("petal", null);
            helper.fail("Null CPU contribution should be rejected");
        } catch (NullPointerException expected) {
            helper.assertValueEqual(expected.getMessage(), "contribution", "Null contribution failure message");
        }

        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                partitionCount,
                "Rejected contribution should not change CPU partitions");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().storageBytes(),
                0L,
                "Rejected contribution should not pollute the CPU profile");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_main_structure_failure_clears_cpu_child_contribution")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mainStructureFailureClearsCpuChildContribution(GameTestHelper helper) {
        BlockPos flowerPos = new BlockPos(1, 1, 1);
        helper.setBlock(flowerPos, ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(flowerPos));
        if (!(blockEntity instanceof DigitalConstructFlowerBlockEntity flower)) {
            helper.fail("Expected a placed Digital Construct Flower block entity", flowerPos);
            return;
        }
        flower.loadTag(formedTag(), helper.getLevel().registryAccess());
        flower.setCpuContribution("cpu", DigitalConstructFlowerCpuContribution.of(1024L, 1, 1));
        flower.loadTag(formedCraftingProfileTag(), helper.getLevel().registryAccess());

        helper.assertValueEqual(flower.getCpuPartitions().size(), 1, "CPU child contribution should be active before recheck");
        helper.assertTrue(flower.isCraftingStructureFormed(), "Crafting child structure should be active before recheck");
        helper.assertValueEqual(
                flower.getCraftingPatternCapacity(),
                704,
                "Crafting child profile should be active before recheck");

        flower.serverTick();

        helper.assertFalse(flower.isStructureFormed(), "Missing main structure should make the host unformed");
        helper.assertValueEqual(
                flower.getCraftingRuntime().profile().storageBytes(),
                0L,
                "Main structure failure should clear CPU child storage");
        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                0,
                "Main structure failure should clear CPU child partitions");
        helper.assertFalse(flower.isCraftingStructureFormed(), "Main structure failure should clear crafting child status");
        helper.assertValueEqual(
                flower.getCraftingPatternCapacity(),
                0,
                "Main structure failure should clear crafting child pattern capacity");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_crafting_profile_round_trips_through_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void craftingProfileRoundTripsThroughNbt(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity original = digitalConstructFlower(false);
        original.loadTag(formedCraftingProfileTag(), HolderLookup.Provider.create(Stream.empty()));

        helper.assertTrue(original.isCraftingStructureFormed(), "Loaded crafting child structure should be formed");
        helper.assertValueEqual(
                original.getCraftingStructureMatchedBlockCount(),
                314,
                "Loaded crafting child structure should preserve matched block count");
        helper.assertValueEqual(
                original.getCraftingPatternCoreCount(),
                3,
                "Loaded crafting child structure should preserve pattern core count");
        helper.assertValueEqual(
                original.getCraftingPatternCapacity(),
                704,
                "Loaded crafting child structure should preserve pattern capacity");

        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, HolderLookup.Provider.create(Stream.empty()));
        DigitalConstructFlowerBlockEntity loaded = digitalConstructFlower(false);
        loaded.loadTag(saved, HolderLookup.Provider.create(Stream.empty()));

        helper.assertTrue(loaded.isCraftingStructureFormed(), "Saved crafting child structure should remain formed");
        helper.assertValueEqual(
                loaded.getCraftingStructureMatchedBlockCount(),
                314,
                "Saved crafting child structure should round-trip matched block count");
        helper.assertValueEqual(
                loaded.getCraftingPatternCoreCount(),
                3,
                "Saved crafting child structure should round-trip pattern core count");
        helper.assertValueEqual(
                loaded.getCraftingPatternCapacity(),
                704,
                "Saved crafting child structure should round-trip pattern capacity");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_cpu_runtime_defers_partition_logic_until_level_exists")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cpuRuntimeDefersPartitionLogicUntilLevelExists(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity flower = digitalConstructFlower(true);

        CompoundTag runtimeTag = new CompoundTag();
        runtimeTag.put("contributions", new ListTag());
        ListTag partitionsTag = new ListTag();
        CompoundTag partitionTag = new CompoundTag();
        partitionTag.putInt("index", 0);
        CompoundTag logicTag = new CompoundTag();
        logicTag.put("job", new CompoundTag());
        partitionTag.put("logic", logicTag);
        partitionsTag.add(partitionTag);
        runtimeTag.put("partitions", partitionsTag);

        flower.getCraftingRuntime().readFromTag(
                runtimeTag,
                HolderLookup.Provider.create(Stream.empty()));
        helper.assertValueEqual(
                flower.getCpuPartitions().size(),
                0,
                "Pending partition logic should not create CPU partitions without a child contribution");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_storage_id_round_trips_through_item_and_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageIdRoundTripsThroughItemAndNbt(GameTestHelper helper) {
        DigitalConstructFlowerBlockEntity original = digitalConstructFlower(false);
        ItemStack stack = new ItemStack(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get());
        original.saveStorageIdToItem(stack);
        String storageId = stack.get(ModDataComponents.DIGITAL_CONSTRUCT_FLOWER_STORAGE_ID);

        DigitalConstructFlowerBlockEntity placed = digitalConstructFlower(false);
        placed.restoreStorageIdFromItem(stack);
        ItemStack placedStack = new ItemStack(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get());
        placed.saveStorageIdToItem(placedStack);
        helper.assertValueEqual(
                placedStack.get(ModDataComponents.DIGITAL_CONSTRUCT_FLOWER_STORAGE_ID),
                storageId,
                "Placed host should restore the storage id from the item component");

        CompoundTag saved = new CompoundTag();
        placed.saveAdditional(saved, HolderLookup.Provider.create(Stream.empty()));
        DigitalConstructFlowerBlockEntity loaded = digitalConstructFlower(false);
        loaded.loadTag(saved, HolderLookup.Provider.create(Stream.empty()));
        ItemStack loadedStack = new ItemStack(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get());
        loaded.saveStorageIdToItem(loadedStack);
        helper.assertValueEqual(
                loadedStack.get(ModDataComponents.DIGITAL_CONSTRUCT_FLOWER_STORAGE_ID),
                storageId,
                "Loaded host should keep the storage id from block entity NBT");
        helper.succeed();
    }

    private static DigitalConstructFlowerBlockEntity digitalConstructFlower(boolean formed) {
        DigitalConstructFlowerBlockEntity flower = new DigitalConstructFlowerBlockEntity(
                BlockPos.ZERO,
                ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get().defaultBlockState());
        if (formed) {
            flower.loadTag(formedTag(), HolderLookup.Provider.create(Stream.empty()));
        }
        return flower;
    }

    private static CompoundTag formedTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        return tag;
    }

    private static CompoundTag formedCraftingProfileTag() {
        CompoundTag tag = formedTag();
        tag.putBoolean("crafting_structure_formed", true);
        tag.putInt("crafting_structure_matched_block_count", 314);
        tag.putInt("crafting_pattern_core_count", 3);
        tag.putInt("crafting_pattern_capacity", 704);
        return tag;
    }
}
