package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class LightBladeChargeEntityGameTest {

    private static final BlockPos PROJECTILE_POS = new BlockPos(1, 2, 1);
    private static final double WITHIN_NEW_MARGIN_OFFSET = 1.2D;
    private static final double OUTSIDE_NEW_MARGIN_OFFSET = 1.85D;

    private LightBladeChargeEntityGameTest() {}

    @TestHolder("light_blade_charge_uses_wider_entity_hit_margin")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesWiderEntityHitMargin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LightBladeChargeEntity projectile = createProjectile(helper, level);

        LivingEntity targetWithinMargin = createTarget(helper, level, WITHIN_NEW_MARGIN_OFFSET);
        HitResult widenedHit = ProjectileUtil.getHitResultOnMoveVector(projectile, entity -> entity == targetWithinMargin);
        helper.assertTrue(
                widenedHit instanceof EntityHitResult entityHit && entityHit.getEntity() == targetWithinMargin,
                "The light blade charge must hit beyond the vanilla 0.3-block margin and within its 1.0-block margin");
        targetWithinMargin.discard();

        LivingEntity targetOutsideMargin = createTarget(helper, level, OUTSIDE_NEW_MARGIN_OFFSET);
        HitResult missedHit = ProjectileUtil.getHitResultOnMoveVector(projectile, entity -> entity == targetOutsideMargin);
        helper.assertFalse(
                missedHit instanceof EntityHitResult,
                "The light blade charge must not hit an entity outside its 1.0-block margin");
        helper.succeed();
    }

    private static LightBladeChargeEntity createProjectile(GameTestHelper helper, ServerLevel level) {
        LightBladeChargeEntity projectile = new LightBladeChargeEntity(ModEntities.LIGHT_BLADE_CHARGE.get(), level);
        projectile.setPos(Vec3.atCenterOf(helper.absolutePos(PROJECTILE_POS)));
        projectile.setDeltaMovement(0.0D, 0.0D, 4.0D);
        return projectile;
    }

    private static LivingEntity createTarget(GameTestHelper helper, ServerLevel level, double horizontalOffset) {
        LivingEntity target = EntityType.IRON_GOLEM.create(level);
        if (target == null) {
            throw new IllegalStateException("Failed to create a light blade charge target");
        }
        Vec3 projectilePosition = Vec3.atCenterOf(helper.absolutePos(PROJECTILE_POS));
        target.setPos(
                projectilePosition.x + horizontalOffset,
                projectilePosition.y - 1.0D,
                projectilePosition.z + 2.0D);
        helper.assertTrue(level.addFreshEntity(target), "The light blade charge target must be added to the test level");
        return target;
    }
}
