package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DispersingDataEntityGameTest {

    private static final double EPSILON = 1.0E-6D;

    private DispersingDataEntityGameTest() {}

    @TestHolder("dispersing_data_merges_without_exceeding_16")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void mergesWithoutExceedingMaximum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO));
        DispersingDataEntity first = create(level, position, 10);
        DispersingDataEntity second = create(level, position, 10);
        helper.assertTrue(level.addFreshEntity(first), "The first dispersing data entity must spawn");
        helper.assertTrue(level.addFreshEntity(second), "The second dispersing data entity must spawn");

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    List<DispersingDataEntity> entities = level.getEntitiesOfClass(
                            DispersingDataEntity.class,
                            new AABB(position, position).inflate(2.0D));
                    helper.assertValueEqual(entities.size(), 2, "Overflow must remain in a second entity");
                    helper.assertValueEqual(
                            entities.stream().mapToInt(DispersingDataEntity::getDataAmount).sum(),
                            20,
                            "Merging must preserve the total data amount");
                    helper.assertTrue(
                            entities.stream().anyMatch(entity -> entity.getDataAmount() == 16),
                            "One merged entity must reach the 16-data limit");
                    helper.assertTrue(
                            entities.stream().allMatch(entity -> entity.getDataAmount() <= 16),
                            "No merged entity may exceed the 16-data limit");
                })
                .thenSucceed();
    }

    @TestHolder("dispersing_data_attracts_and_merges_from_a_distance")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 120)
    public static void attractsAndMergesFromDistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 firstPosition = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2)));
        Vec3 secondPosition = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 2)));
        helper.assertTrue(level.addFreshEntity(create(level, firstPosition, 1)), "The first data entity must spawn");
        helper.assertTrue(level.addFreshEntity(create(level, secondPosition, 1)), "The second data entity must spawn");

        AABB searchArea = new AABB(firstPosition, secondPosition).inflate(2.0D);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<DispersingDataEntity> entities = level.getEntitiesOfClass(
                            DispersingDataEntity.class,
                            searchArea);
                    helper.assertValueEqual(entities.size(), 1, "Distant data entities must attract and merge");
                    helper.assertValueEqual(entities.getFirst().getDataAmount(), 2, "The merged entity must contain both data units");
                })
                .thenSucceed();
    }

    @TestHolder("dispersing_data_persists_amount_and_scales_with_amount")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5")
    public static void persistsAmountAndScalesToFour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DispersingDataEntity entity = create(level, Vec3.ZERO, 16);
        CompoundTag saved = new CompoundTag();
        entity.addAdditionalSaveData(saved);

        DispersingDataEntity restored = create(level, Vec3.ZERO, 1);
        restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.getDataAmount(), 16, "Saved data amount must round-trip");
        helper.assertValueEqual(
                restored.getName(),
                restored.getType().getDescription().copy().append("*").append("16"),
                "Merged data must include its amount in the display name");
        helper.assertTrue(
                Math.abs(restored.getSizeScale() - (float) Math.cbrt(16.0D)) < EPSILON,
                "A full 16-data entity must scale with the cube root of its amount");
        helper.assertTrue(
                Math.abs(restored.getDimensions(Pose.STANDING).width() - 0.25F * (float) Math.cbrt(16.0D)) < EPSILON,
                "A full 16-data entity must have a matching scaled hitbox");

        CompoundTag legacy = new CompoundTag();
        restored.readAdditionalSaveData(legacy);
        helper.assertValueEqual(restored.getDataAmount(), 1, "Legacy entities without an amount must default to one");
        helper.assertValueEqual(
                restored.getName(),
                restored.getType().getDescription(),
                "A single data entity must keep its base display name");
        helper.succeed();
    }

    @TestHolder("capture_ball_partially_collects_merged_dispersing_data")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5")
    public static void captureBallPartiallyCollectsMergedData(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack captureBall = DataCaptureBallItem.createConfiguredStack(2_500.0D, 0L);
        DataCaptureBallItem captureBallItem = (DataCaptureBallItem) captureBall.getItem();
        DispersingDataEntity entity = create(helper.getLevel(), Vec3.ZERO, 3);

        boolean captured = captureBallItem.captureDispersingData(captureBall, player, entity);

        helper.assertTrue(captured, "The capture ball must collect affordable data from a merged entity");
        helper.assertValueEqual(
                DataCaptureBallItem.getStoredDataAmount(captureBall),
                2L,
                "The capture ball must store each collected data unit");
        helper.assertValueEqual(entity.getDataAmount(), 1, "Unpaid data must remain in the entity");
        helper.assertTrue(
                Math.abs(captureBallItem.getAECurrentPower(captureBall) - 500.0D) < EPSILON,
                "Capturing two data units must consume energy twice");
        helper.succeed();
    }

    private static DispersingDataEntity create(ServerLevel level, Vec3 position, int amount) {
        DispersingDataEntity entity = DEEntities.DISPERSING_DATA.get().create(level);
        if (entity == null) {
            throw new IllegalStateException("Failed to create dispersing data entity");
        }
        entity.setPos(position);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setDataAmount(amount);
        return entity;
    }
}
