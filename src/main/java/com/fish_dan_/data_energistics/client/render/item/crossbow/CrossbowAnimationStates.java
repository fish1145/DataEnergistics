package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-thread visual state for rendered entity hands; values never retain their entity keys. */
public final class CrossbowAnimationStates {

    private static final Map<LivingEntity, EnumMap<InteractionHand, HandAnimation>> ANIMATIONS = new WeakHashMap<>();

    private CrossbowAnimationStates() {}

    /** Registers a rendered hand and reads its pose without advancing the animation. */
    public static CrossbowAnimation.Pose pose(LivingEntity entity, InteractionHand hand, float partialTick) {
        var hands = ANIMATIONS.computeIfAbsent(entity, ignored -> new EnumMap<>(InteractionHand.class));
        var tracked = hands.computeIfAbsent(hand, ignored -> new HandAnimation(slot(entity, hand), new CrossbowAnimation(),
                entity.getItemInHand(hand).getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0)));
        return tracked.animation().pose(partialTick);
    }

    /** Observes actual held state once per client tick; paused worlds do not advance. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            ANIMATIONS.clear();
            return;
        }
        ANIMATIONS.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != minecraft.level);
        if (minecraft.isPaused()) {
            return;
        }
        for (var entry : ANIMATIONS.entrySet()) {
            LivingEntity entity = entry.getKey();
            for (var handEntry : entry.getValue().entrySet()) {
                InteractionHand hand = handEntry.getKey();
                HandAnimation tracked = handEntry.getValue();
                ItemStack stack = entity.getItemInHand(hand);
                boolean held = stack.getItem() instanceof MatterConvergingCrossbowItem;
                MatterConvergingCrossbowMode mode = held ? MatterConvergingCrossbowMode.fromId(
                        stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.GRENADE.id())) : MatterConvergingCrossbowMode.GRENADE;
                int slot = slot(entity, hand);
                if (tracked.slot() != slot) {
                    tracked = new HandAnimation(slot, held ? new CrossbowAnimation() : tracked.animation(), stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0));
                    handEntry.setValue(tracked);
                }
                boolean using = held && entity.isUsingItem() && entity.getUsedItemHand() == hand;
                boolean charged = held && CrossbowItem.isCharged(stack);
                float progress = using ? Mth.clamp((float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / MatterConvergingCrossbowItem.getChargeDuration(stack, entity), 0.0F, 1.0F) : 0.0F;
                CannonCharge charge = stack.get(DEDataComponents.CANNON_CHARGE.get());
                if (mode != MatterConvergingCrossbowMode.CROSSBOW) {
                    using = held && charge != null && charge.belongsTo(entity, hand, mode);
                    charged = false;
                    progress = using ? charge.progress(minecraft.level.getGameTime()) : 0.0F;
                }
                int shot = stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0);
                tracked.animation().tick(held, using, charged, progress, mode, shot != 0 && shot != tracked.shot());
                handEntry.setValue(new HandAnimation(slot, tracked.animation(), shot));
            }
        }
    }

    private static int slot(LivingEntity entity, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && entity instanceof Player player ? player.getInventory().selected : -1;
    }

    private record HandAnimation(int slot, CrossbowAnimation animation, int shot) {}
}
