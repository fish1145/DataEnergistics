package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.core.definitions.AEBlocks;

/**
 * Verifies the black-hole mode's real entity and block output paths.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataSanctumBlackHoleOutputGameTest {

    private static final BlockPos SANCTUM_MAIN_POS = new BlockPos(25, 4, 25);
    private static final BlockPos CONSUMED_BLOCK_POS = SANCTUM_MAIN_POS.offset(3, 2, 0);
    private static final BlockPos ENERGY_CELL_POS = SANCTUM_MAIN_POS.relative(Direction.SOUTH, 3);
    private static final Direction SANCTUM_FACING = Direction.NORTH;
    private static final int BLACK_HOLE_MODE = 1;
    private static final long DATA_FLOW_PER_SUCCESS = 1_000L;

    private DataSanctumBlackHoleOutputGameTest() {}

    @TestHolder("data_sanctum_black_hole_outputs_one_thousand_per_entity_and_block_cycle")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 100)
    public static void outputsOneThousandPerEntityAndBlockCycle(GameTestHelper helper) {
        DataSanctumBlockEntity sanctum = placeSanctum(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(ENERGY_CELL_POS, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        sanctum.isOnline(),
                        "The data sanctum must be online through its external network port"))
                .thenExecute(() -> {
                    sanctum.setMode(BLACK_HOLE_MODE);
                    ArmorStand entity = EntityType.ARMOR_STAND.create(level);
                    if (entity == null) {
                        throw new GameTestAssertException("Failed to create the black-hole test entity");
                    }
                    entity.setPos(new Vec3(
                            sanctum.getBlockPos().getX() + 0.5D,
                            sanctum.getBlockPos().getY() + 2.5D,
                            sanctum.getBlockPos().getZ() + 0.5D));
                    helper.assertTrue(level.addFreshEntity(entity), "The black-hole test entity must enter the world");

                    sanctum.serverTick();

                    helper.assertTrue(entity.isRemoved(), "The black hole must consume the entity at its center");
                    helper.assertValueEqual(
                            storedDataFlow(sanctum),
                            DATA_FLOW_PER_SUCCESS,
                            "One consumed entity must produce exactly 1000 data flow");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONSUMED_BLOCK_POS, Blocks.STONE);
                    for (int tick = 0; tick < 40; tick++) {
                        sanctum.serverTick();
                    }

                    helper.assertBlockPresent(Blocks.AIR, CONSUMED_BLOCK_POS);
                    helper.assertValueEqual(
                            storedDataFlow(sanctum),
                            DATA_FLOW_PER_SUCCESS * 2,
                            "One successful block cycle must add exactly 1000 data flow");
                })
                .thenSucceed();
    }

    private static long storedDataFlow(DataSanctumBlockEntity sanctum) {
        DataSanctumReturnInventory inventory = sanctum.getReturnInventory();
        long amount = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (DataFlowKey.of().equals(inventory.getKey(slot))) {
                amount += inventory.getAmount(slot);
            }
        }
        return amount;
    }

    private static DataSanctumBlockEntity placeSanctum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeSanctumPart(level, helper, 0, 0, 0);
        for (int offsetY = 0; offsetY <= 3; offsetY++) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0 && offsetY == 0) {
                        continue;
                    }
                    placeSanctumPart(level, helper, offsetX, offsetZ, offsetY);
                }
            }
        }

        BlockEntity blockEntity = helper.getBlockEntity(SANCTUM_MAIN_POS);
        if (blockEntity instanceof DataSanctumBlockEntity sanctum) {
            return sanctum;
        }
        throw new GameTestAssertException("Placed data sanctum has no matching block entity");
    }

    private static void placeSanctumPart(
                                         ServerLevel level,
                                         GameTestHelper helper,
                                         int offsetX,
                                         int offsetZ,
                                         int offsetY) {
        BlockPos localPartPos = DataSanctumBlockEntity.getPartPos(
                SANCTUM_MAIN_POS, SANCTUM_FACING, offsetX, offsetZ, offsetY);
        BlockState state = DEBlocks.DATA_SANCTUM.get()
                .defaultBlockState()
                .setValue(DataSanctumBlock.FACING, SANCTUM_FACING)
                .setValue(DataSanctumBlock.OFFSET_X, DataSanctumBlockEntity.encodeOffsetX(offsetX))
                .setValue(DataSanctumBlock.OFFSET_Z, DataSanctumBlockEntity.encodeOffsetZ(offsetZ))
                .setValue(DataSanctumBlock.OFFSET_Y, offsetY)
                .setValue(DataSanctumBlock.ACTIVE, false)
                .setValue(DataSanctumBlock.MODE, 0);
        level.setBlock(helper.absolutePos(localPartPos), state, Block.UPDATE_CLIENTS);
    }
}
