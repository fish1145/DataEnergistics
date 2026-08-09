package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ResonanceGameplayGameTest {

    private static final BlockPos WARDEN_POS = new BlockPos(0, 1, 1);
    private static final BlockPos TARGET_POS = new BlockPos(4, 1, 1);
    private static final BlockPos FIRST_WARDEN_FORK_POS = new BlockPos(1, 2, 1);
    private static final BlockPos SECOND_WARDEN_FORK_POS = new BlockPos(2, 2, 1);
    private static final BlockPos WARDEN_CRYSTAL_POS = new BlockPos(3, 2, 1);
    private static final BlockPos VIBRATION_SOURCE_POS = new BlockPos(0, 2, 4);
    private static final BlockPos VIBRATION_FORK_POS = new BlockPos(1, 2, 4);
    private static final BlockPos VIBRATION_CRYSTAL_POS = new BlockPos(2, 2, 4);
    private static final BlockPos SENSOR_POS = new BlockPos(4, 2, 4);

    private ResonanceGameplayGameTest() {}

    @TestHolder("resonance_paths_handle_first_targets_and_failed_interception")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void handlesFirstTargetsAndFailedInterception(GameTestHelper helper) {
        TuningForkBlockEntity firstWardenFork = placeFork(helper, FIRST_WARDEN_FORK_POS);
        TuningForkBlockEntity secondWardenFork = placeFork(helper, SECOND_WARDEN_FORK_POS);
        BlockState dataCrystal = DEBlocks.DATA_CRYSTAL_CLUSTER.get().defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.NORTH)
                .setValue(AmethystClusterBlock.WATERLOGGED, true);
        helper.setBlock(WARDEN_CRYSTAL_POS, dataCrystal);

        ServerLevel level = helper.getLevel();
        Warden warden = EntityType.WARDEN.create(level);
        LivingEntity target = EntityType.ARMOR_STAND.create(level);
        if (warden == null || target == null) {
            throw new GameTestAssertException("Failed to create Warden sonic-boom test entities");
        }
        warden.setPos(Vec3.atBottomCenterOf(helper.absolutePos(WARDEN_POS)));
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(TARGET_POS)));
        WardenSonicBoomResonance.process(level, warden, target);

        helper.assertValueEqual(firstWardenFork.getDamage(), 1, "The first Warden fork must lose one durability");
        helper.assertValueEqual(secondWardenFork.getDamage(), 0, "A Warden wave must not process a second fork");
        BlockState transformedCrystal = helper.getBlockState(WARDEN_CRYSTAL_POS);
        helper.assertTrue(
                transformedCrystal.is(DEBlocks.MEDIUM_RESONANCE_CRYSTAL_BUD.get()),
                "The first changeable crystal must become a medium resonance bud");
        helper.assertValueEqual(
                transformedCrystal.getValue(AmethystClusterBlock.FACING),
                Direction.NORTH,
                "Crystal conversion must preserve facing");
        helper.assertTrue(
                transformedCrystal.getValue(AmethystClusterBlock.WATERLOGGED),
                "Crystal conversion must preserve waterlogging");

        TuningForkBlockEntity vibrationFork = placeFork(helper, VIBRATION_FORK_POS);
        helper.setBlock(VIBRATION_CRYSTAL_POS, DEBlocks.SMALL_RESONANCE_CRYSTAL_BUD.get());
        boolean intercepted = SculkVibrationResonance.intercept(
                level,
                GameEvent.STEP,
                Vec3.atCenterOf(helper.absolutePos(VIBRATION_SOURCE_POS)),
                helper.absolutePos(SENSOR_POS));

        helper.assertTrue(intercepted, "A later successful crystal must intercept after an offline fork fails");
        helper.assertValueEqual(vibrationFork.getDamage(), 1, "The failed offline fork must still lose durability");
        helper.assertTrue(
                helper.getBlockState(VIBRATION_CRYSTAL_POS).is(DEBlocks.MEDIUM_RESONANCE_CRYSTAL_BUD.get()),
                "The later small resonance bud must advance to medium");
        helper.succeed();
    }

    private static TuningForkBlockEntity placeFork(GameTestHelper helper, BlockPos forkPos) {
        helper.setBlock(forkPos.below(), DEBlocks.TUNING_FORK_BASE.get());
        helper.setBlock(forkPos, DEBlocks.AMETHYST_TUNING_FORK.get());
        BlockEntity blockEntity = helper.getBlockEntity(forkPos);
        if (blockEntity instanceof TuningForkBlockEntity tuningFork) {
            return tuningFork;
        }
        throw new GameTestAssertException("Placed tuning fork has no matching block entity at " + forkPos);
    }
}
