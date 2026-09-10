package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.registry.DEParticles;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Numeric anchors captured from the rendered model, valid for at most two client ticks. */
public final class CannonModelAnchors {

    private static final Map<LivingEntity, EnumMap<InteractionHand, Anchors>> SAMPLES = new WeakHashMap<>();

    private CannonModelAnchors() {}

    public record Anchors(Vec3 muzzle, Vec3 left, Vec3 right, Vec3 leftVelocity, Vec3 rightVelocity, long tick) {}

    public static void record(LivingEntity owner, InteractionHand hand, Anchors anchors) {
        SAMPLES.computeIfAbsent(owner, unused -> new EnumMap<>(InteractionHand.class)).put(hand, anchors);
    }

    public static Vec3 muzzle(LivingEntity owner, InteractionHand hand, Vec3 fallback) {
        var hands = SAMPLES.get(owner);
        var anchors = hands == null ? null : hands.get(hand);
        return anchors == null || owner.level().getGameTime() - anchors.tick > 2 ? fallback : anchors.muzzle;
    }

    public static void particles(LivingEntity owner, InteractionHand hand, float strength) {
        var hands = SAMPLES.get(owner);
        var anchors = hands == null ? null : hands.get(hand);
        if (anchors == null || owner.level().getGameTime() - anchors.tick > 2 || strength <= 0) return;
        for (int side = 0; side < 2; side++) {
            Vec3 position = side == 0 ? anchors.left : anchors.right;
            Vec3 velocity = (side == 0 ? anchors.leftVelocity : anchors.rightVelocity).scale(strength);
            for (int i = 0; i < Math.ceil(3 * strength); i++) {
                owner.level().addParticle(DEParticles.RAIL_STEAM.get(), position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
            }
        }
    }
}
