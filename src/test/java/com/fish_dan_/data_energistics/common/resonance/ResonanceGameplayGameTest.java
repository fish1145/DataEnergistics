package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

    @TestHolder("resonance_crystal_stages_advance_to_maturity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 20)
    public static void crystalStagesAdvanceToMaturity(GameTestHelper helper) {
        BlockState state = DEBlocks.SMALL_RESONANCE_CRYSTAL_BUD.get().defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.WEST)
                .setValue(AmethystClusterBlock.WATERLOGGED, true);
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, state);

        helper.assertTrue(
                ResonanceCrystalWaveTransformation.tryTransformFromVibration(
                        helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getLevel().getRandom()),
                "Small resonance bud must advance when hit by a vibration");
        assertCrystalStage(helper, pos, DEBlocks.MEDIUM_RESONANCE_CRYSTAL_BUD.get(), Direction.WEST);

        helper.assertTrue(
                ResonanceCrystalWaveTransformation.tryTransformFromVibration(
                        helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getLevel().getRandom()),
                "Medium resonance bud must advance when hit by a vibration");
        assertCrystalStage(helper, pos, DEBlocks.LARGE_RESONANCE_CRYSTAL_BUD.get(), Direction.WEST);

        helper.assertTrue(
                ResonanceCrystalWaveTransformation.tryTransformFromVibration(
                        helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getLevel().getRandom()),
                "Large resonance bud must advance when hit by a vibration");
        assertCrystalStage(helper, pos, DEBlocks.RESONANCE_CRYSTAL_CLUSTER.get(), Direction.WEST);

        helper.assertFalse(
                ResonanceCrystalWaveTransformation.tryTransformFromVibration(
                        helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getLevel().getRandom()),
                "A mature resonance cluster must not advance further");
        helper.succeed();
    }

    @TestHolder("warden_sonic_boom_resonance_precedes_target_damage_rejection")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 20)
    public static void wardenSonicBoomResonancePrecedesTargetDamageRejection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Warden warden = EntityType.WARDEN.create(level);
        WitherBoss target = EntityType.WITHER.create(level);
        if (warden == null || target == null) {
            throw new GameTestAssertException("Failed to create Warden sonic-boom mixin test entities");
        }

        warden.setPos(Vec3.atBottomCenterOf(helper.absolutePos(WARDEN_POS)));
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(TARGET_POS)));
        target.setInvulnerableTicks(200);
        level.addFreshEntity(warden);
        level.addFreshEntity(target);

        Vec3 start = warden.position().add(
                warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, warden.getYRot()));
        Vec3 direction = target.getEyePosition().subtract(start).normalize();
        BlockPos crystalPos = BlockPos.containing(start.add(direction.scale(2.0D)));
        level.setBlock(crystalPos.below(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(crystalPos, Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_ALL);

        warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
        warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN);

        float healthBefore = target.getHealth();
        new ExposedSonicBoom().emit(level, warden);

        helper.assertValueEqual(
                target.getHealth(),
                healthBefore,
                "The invulnerable Wither must reject sonic-boom damage");
        helper.assertTrue(
                level.getBlockState(crystalPos).is(DEBlocks.MEDIUM_RESONANCE_CRYSTAL_BUD.get()),
                "The emitted sonic boom must transform resonance candidates before target damage rejection");
        helper.succeed();
    }

    @TestHolder("online_tuning_fork_intercepts_warden_sonic_boom")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void onlineTuningForkInterceptsWardenSonicBoom(GameTestHelper helper) {
        BlockPos forkPos = new BlockPos(2, 2, 2);
        BlockPos basePos = forkPos.below();
        TuningForkBlockEntity tuningFork = placeFork(helper, forkPos);
        helper.setBlock(
                basePos.relative(Direction.NORTH),
                BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2", "creative_energy_cell")));
        helper.setBlock(
                basePos.relative(Direction.SOUTH),
                BuiltInRegistries.BLOCK.get(Data_Energistics.id("digital_storage_depot")));

        BlockState baseState = helper.getBlockState(basePos);
        if (!(baseState.getBlock().getStateDefinition().getProperty("online") instanceof BooleanProperty onlineProperty)) {
            throw new GameTestAssertException("Placed tuning-fork base has no online property");
        }

        ServerLevel level = helper.getLevel();
        Warden warden = EntityType.WARDEN.create(level);
        LivingEntity target = EntityType.COW.create(level);
        if (warden == null || target == null) {
            throw new GameTestAssertException("Failed to create Warden interception test entities");
        }
        warden.setNoAi(true);
        warden.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 2))));
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2))));
        level.addFreshEntity(warden);
        level.addFreshEntity(target);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(
                    helper.getBlockState(basePos).getValue(onlineProperty),
                    "The tuning-fork base must join the powered AE network");

            warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
            warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN);
            float healthBefore = target.getHealth();
            Vec3 movementBefore = target.getDeltaMovement();
            new ExposedSonicBoom().emit(level, warden);

            helper.assertValueEqual(
                    target.getHealth(),
                    healthBefore,
                    "A successful tuning-fork interception must cancel sonic-boom damage");
            helper.assertValueEqual(
                    target.getDeltaMovement(),
                    movementBefore,
                    "A successful tuning-fork interception must cancel sonic-boom knockback");
            helper.assertValueEqual(
                    tuningFork.getDamage(),
                    1,
                    "The intercepting tuning fork must lose one durability");
            helper.succeed();
        });
    }

    private static void assertCrystalStage(GameTestHelper helper, BlockPos pos, Block expected,
                                           Direction expectedFacing) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(expected), "Unexpected resonance crystal stage: " + state);
        helper.assertValueEqual(
                state.getValue(AmethystClusterBlock.FACING),
                expectedFacing,
                "Crystal growth must preserve facing");
        helper.assertTrue(
                state.getValue(AmethystClusterBlock.WATERLOGGED),
                "Crystal growth must preserve waterlogging");
    }

    private static TuningForkBlockEntity placeFork(GameTestHelper helper, BlockPos forkPos) {
        BlockPos basePos = forkPos.below();
        helper.setBlock(basePos, BuiltInRegistries.BLOCK.get(Data_Energistics.id("tuning_fork_base")));
        helper.setBlock(forkPos, DEBlocks.AMETHYST_TUNING_FORK.get());
        BlockEntity blockEntity = helper.getBlockEntity(forkPos);
        if (blockEntity instanceof TuningForkBlockEntity tuningFork) {
            return tuningFork;
        }
        throw new GameTestAssertException("Placed tuning fork has no matching block entity at " + forkPos);
    }

    private static final class ExposedSonicBoom extends SonicBoom {

        private void emit(ServerLevel level, Warden warden) {
            super.tick(level, warden, level.getGameTime());
        }
    }
}
