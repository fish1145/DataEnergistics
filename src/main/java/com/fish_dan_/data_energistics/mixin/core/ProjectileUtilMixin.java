package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.entity.LightBladeChargeEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * Widens only the entity-intersection margin used by light blade charges while preserving vanilla block clipping and
 * projectile ordering.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {

    @Unique
    private static final float LIGHT_BLADE_ENTITY_HIT_MARGIN = 1.0F;

    @WrapOperation(
                   method = "getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;",
                   at = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResult(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;FLnet/minecraft/world/level/ClipContext$Block;)Lnet/minecraft/world/phys/HitResult;"))
    private static HitResult dataEnergistics$useLightBladeEntityHitMargin(
                                                                          Vec3 position,
                                                                          Entity projectile,
                                                                          Predicate<Entity> filter,
                                                                          Vec3 deltaMovement,
                                                                          Level level,
                                                                          float margin,
                                                                          ClipContext.Block blockClip,
                                                                          Operation<HitResult> original) {
        float effectiveMargin = projectile instanceof LightBladeChargeEntity ? LIGHT_BLADE_ENTITY_HIT_MARGIN : margin;
        return original.call(position, projectile, filter, deltaMovement, level, effectiveMargin, blockClip);
    }
}
