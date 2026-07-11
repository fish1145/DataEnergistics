package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

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
        source.saveStorageIdToItem(movedHost);
        source.saveHostIdToItem(movedHost);
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
        destination.restoreStorageIdFromItem(movedHost);
        destination.restoreHostIdFromItem(movedHost);

        helper.assertValueEqual(destination.getStorageId(), source.getStorageId(),
                "Moved host should retain its main-storage UUID");
        helper.assertValueEqual(destination.getHostId(), source.getHostId(),
                "Moved host should retain its independent crafting route UUID");
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
