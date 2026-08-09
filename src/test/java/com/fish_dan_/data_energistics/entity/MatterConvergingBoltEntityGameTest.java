package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.core.definitions.AEItems;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MatterConvergingBoltEntityGameTest {

    private MatterConvergingBoltEntityGameTest() {}

    @TestHolder("matter_converging_bolt_preserves_consumed_pierces_after_reload")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5")
    public static void preservesConsumedPiercesAfterReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity target = EntityType.IRON_GOLEM.create(level);
        if (target == null) {
            throw new IllegalStateException("Failed to create an iron golem target");
        }
        target.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.assertTrue(level.addFreshEntity(target), "The iron golem target must be added to the test level");

        MatterConvergingBoltEntity firstBolt = createPiercingBolt(level);
        firstBolt.onHitEntity(new EntityHitResult(target));
        helper.assertFalse(firstBolt.isRemoved(), "The first hit must consume the level-one pierce and keep the bolt flying");
        float healthAfterFirstHit = target.getHealth();
        helper.assertTrue(healthAfterFirstHit < target.getMaxHealth(), "The first hit must damage the target");

        CompoundTag savedBolt = firstBolt.saveWithoutId(new CompoundTag());
        firstBolt.discard();

        MatterConvergingBoltEntity reloadedBolt = new MatterConvergingBoltEntity(DEEntities.MATTER_CONVERGING_BOLT.get(), level);
        reloadedBolt.load(savedBolt);
        helper.assertValueEqual(reloadedBolt.getPierceLevel(), 1, "The reloaded bolt must retain its pierce level");
        helper.assertTrue(reloadedBolt.canHitEntity(target), "Reloading must keep target identity transient under the selected contract");

        reloadedBolt.onHitEntity(new EntityHitResult(target));
        helper.assertTrue(target.getHealth() < healthAfterFirstHit, "The reloaded bolt must still be able to damage the same target");
        helper.assertTrue(reloadedBolt.isRemoved(), "The reloaded bolt must not regain an additional pierce");
        helper.succeed();
    }

    private static MatterConvergingBoltEntity createPiercingBolt(ServerLevel level) {
        MatterConvergingBoltEntity bolt = new MatterConvergingBoltEntity(DEEntities.MATTER_CONVERGING_BOLT.get(), level);
        bolt.setItem(new ItemStack(AEItems.MATTER_BALL.asItem()));
        bolt.setPierceLevel(1);
        bolt.setDeltaMovement(new Vec3(1.0D, 0.0D, 0.0D));
        return bolt;
    }
}
