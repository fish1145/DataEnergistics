package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.registry.ModEntities;

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

    @TestHolder("dispersing_data_merges_without_exceeding_64")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 40)
    public static void mergesWithoutExceedingMaximum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO));
        DispersingDataEntity first = create(level, position, 40);
        DispersingDataEntity second = create(level, position, 40);
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
                            80,
                            "Merging must preserve the total data amount");
                    helper.assertTrue(
                            entities.stream().anyMatch(entity -> entity.getDataAmount() == 64),
                            "One merged entity must reach the 64-data limit");
                    helper.assertTrue(
                            entities.stream().allMatch(entity -> entity.getDataAmount() <= 64),
                            "No merged entity may exceed the 64-data limit");
                })
                .thenSucceed();
    }

    @TestHolder("dispersing_data_persists_amount_and_scales_to_four")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5")
    public static void persistsAmountAndScalesToFour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DispersingDataEntity entity = create(level, Vec3.ZERO, 64);
        CompoundTag saved = new CompoundTag();
        entity.addAdditionalSaveData(saved);

        DispersingDataEntity restored = create(level, Vec3.ZERO, 1);
        restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.getDataAmount(), 64, "Saved data amount must round-trip");
        helper.assertTrue(
                Math.abs(restored.getSizeScale() - 4.0F) < EPSILON,
                "A full 64-data entity must render at four times the base size");
        helper.assertTrue(
                Math.abs(restored.getDimensions(Pose.STANDING).width() - 1.0F) < EPSILON,
                "A full 64-data entity must have a four-times-wide hitbox");

        CompoundTag legacy = new CompoundTag();
        restored.readAdditionalSaveData(legacy);
        helper.assertValueEqual(restored.getDataAmount(), 1, "Legacy entities without an amount must default to one");
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
        DispersingDataEntity entity = ModEntities.DISPERSING_DATA.get().create(level);
        if (entity == null) {
            throw new IllegalStateException("Failed to create dispersing data entity");
        }
        entity.setPos(position);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setDataAmount(amount);
        return entity;
    }
}
