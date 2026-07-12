package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** Verifies that crafting routes use an identity distinct from main-storage ownership. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternHostIdentityTest {

    private TrinityPatternHostIdentityTest() {}

    @TestHolder("trinity_pattern_host_identity_survives_item_movement")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void hostIdentitySurvivesItemMovement(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        helper.setBlock(sourcePos, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        helper.assertTrue(
                !source.getHostId().equals(source.getStorageId()),
                "Trinity crafting host UUID must be independent from the main-storage UUID");

        ItemStack movedHost = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        source.saveIdentityToItem(movedHost);
        helper.assertValueEqual(
                movedHost.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                source.getStorageId(),
                "Moved host item should carry the storage UUID as a typed component");
        helper.assertValueEqual(
                movedHost.get(ModDataComponents.TRINITY_DATA_CORE_HOST_ID),
                source.getHostId(),
                "Moved host item should carry the route UUID as a typed component");

        helper.setBlock(destinationPos, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        TrinityDataCoreBlockEntity destination = requireHost(helper, destinationPos);
        destination.restoreIdentityFromItem(movedHost);

        helper.assertValueEqual(destination.getStorageId(), source.getStorageId(),
                "Moved host should retain its main-storage UUID");
        helper.assertValueEqual(destination.getHostId(), source.getHostId(),
                "Moved host should retain its independent crafting route UUID");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_stateful_drop_restores_saved_data_storage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void statefulDropRestoresSavedDataStorage(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        helper.setBlock(sourcePos, ModBlocks.TRINITY_DATA_CORE.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.SOUTH));
        TrinityDataCoreBlockEntity source = requireHost(helper, sourcePos);
        UUID storageId = source.getStorageId();
        UUID hostId = source.getHostId();
        AEItemKey storedKey = AEItemKey.of(Items.DIAMOND);
        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer());
        helper.assertValueEqual(
                storage.insert(storageId, storedKey, 37L, Actionable.MODULATE),
                37L,
                "Source host should seed its SavedData storage");

        List<ItemStack> drops = Block.getDrops(
                source.getBlockState(),
                helper.getLevel(),
                helper.absolutePos(sourcePos),
                source);
        helper.assertValueEqual(drops.size(), 1, "A stateful Trinity host should create exactly one drop");
        ItemStack movedHost = drops.getFirst();
        helper.assertValueEqual(
                movedHost.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID),
                storageId,
                "Stateful host drop should carry the storage UUID");
        helper.assertValueEqual(
                movedHost.get(ModDataComponents.TRINITY_DATA_CORE_HOST_ID),
                hostId,
                "Stateful host drop should carry the crafting UUID");

        helper.setBlock(sourcePos, Blocks.AIR);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, movedHost);
        helper.placeAt(player, movedHost, destinationPos.below(), Direction.UP);

        TrinityDataCoreBlockEntity restored = requireHost(helper, destinationPos);
        helper.assertValueEqual(restored.getStorageId(), storageId,
                "Placed stateful host should restore the original storage UUID");
        helper.assertValueEqual(restored.getHostId(), hostId,
                "Placed stateful host should restore the original crafting UUID");
        helper.assertValueEqual(
                storage.amount(restored.getStorageId(), storedKey),
                BigInteger.valueOf(37L),
                "Placed stateful host should resolve the original SavedData contents");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_rejects_partial_item_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsPartialItemIdentity(GameTestHelper helper) {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(
                BlockPos.ZERO,
                ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        UUID originalStorageId = host.getStorageId();
        UUID originalHostId = host.getHostId();
        ItemStack malformed = new ItemStack(ModBlocks.TRINITY_DATA_CORE.get());
        malformed.set(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID, UUID.randomUUID());

        helper.assertFalse(host.restoreIdentityFromItem(malformed),
                "A host item with only one identity component must be rejected");
        helper.assertValueEqual(host.getStorageId(), originalStorageId,
                "Rejected partial identity must not replace the storage UUID");
        helper.assertValueEqual(host.getHostId(), originalHostId,
                "Rejected partial identity must not replace the crafting UUID");
        helper.succeed();
    }

    private static TrinityDataCoreBlockEntity requireHost(GameTestHelper helper, BlockPos position) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof TrinityDataCoreBlockEntity host) {
            return host;
        }
        helper.fail("Expected a Trinity host block entity", position);
        throw new IllegalStateException("Placed Trinity host has no matching block entity");
    }
}
