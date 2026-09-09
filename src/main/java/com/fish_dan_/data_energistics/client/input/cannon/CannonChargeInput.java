package com.fish_dan_.data_energistics.client.input.cannon;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.network.action.CannonChargePayload;
import com.fish_dan_.data_energistics.network.action.CannonChargePayload.Action;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Observes the attack binding (left mouse by default) without entering vanilla's right-button use state. */
public final class CannonChargeInput {

    private static final Map<InteractionHand, Muzzle> MUZZLES = new EnumMap<>(InteractionHand.class);
    private static boolean wasDown;
    private static @Nullable InteractionHand pressedHand;
    private static MatterConvergingCrossbowMode pressedMode = MatterConvergingCrossbowMode.GRENADE;
    private static int pressedSlot;

    private CannonChargeInput() {}

    public static @Nullable InteractionHand cannonHand(Player player) {
        if (player.isUsingItem() || player.isSpectator() || !player.isAlive()) return null;
        if (MatterConvergingCrossbowItem.isCannon(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (MatterConvergingCrossbowItem.isCannon(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static void tick(Minecraft minecraft) {
        boolean down = minecraft.options.keyAttack.isDown();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.getConnection() == null) {
            pressedHand = null;
            wasDown = down;
            MUZZLES.clear();
            return;
        }
        InteractionHand currentHand = cannonHand(player);
        boolean enabled = minecraft.screen == null && minecraft.isWindowActive() && !minecraft.isPaused() && player.isAlive() && !player.isSpectator() && !player.hasEffect(DEMobEffects.RADIX_LOSS);
        if (pressedHand != null && (!enabled || currentHand != pressedHand || player.getInventory().selected != pressedSlot || MatterConvergingCrossbowItem.mode(player.getItemInHand(pressedHand)) != pressedMode)) {
            cancel();
        } else if (pressedHand != null && !down && wasDown) {
            Muzzle muzzle = MUZZLES.get(pressedHand);
            Vec3 look = player.getViewVector(1.0F);
            Vec3 offset = look.scale(0.8D).add(0.0D, -0.2D, 0.0D);
            Vec3 direction = look;
            if (muzzle != null && minecraft.level.getGameTime() >= muzzle.tick && minecraft.level.getGameTime() - muzzle.tick <= 2) {
                offset = muzzle.position.subtract(player.getEyePosition());
                direction = muzzle.direction;
            }
            PacketDistributor.sendToServer(new CannonChargePayload(pressedHand, Action.RELEASE, offset, direction));
            pressedHand = null;
        }
        if (enabled && down && !wasDown && currentHand != null && !player.isUsingItem()) {
            pressedHand = currentHand;
            pressedMode = MatterConvergingCrossbowItem.mode(player.getItemInHand(currentHand));
            pressedSlot = player.getInventory().selected;
            PacketDistributor.sendToServer(new CannonChargePayload(currentHand, Action.START, Vec3.ZERO, Vec3.ZERO));
        }
        wasDown = down;
    }

    public static void cancel() {
        if (pressedHand != null) {
            PacketDistributor.sendToServer(new CannonChargePayload(pressedHand, Action.CANCEL, Vec3.ZERO, Vec3.ZERO));
            pressedHand = null;
        }
    }

    /** Receives a fresh numeric sample from the currently rendered model; never retains entities or stacks. */
    public static void recordMuzzle(InteractionHand hand, Vec3 position, Vec3 direction, long tick) {
        MUZZLES.put(hand, new Muzzle(position, direction, tick));
    }

    private record Muzzle(Vec3 position, Vec3 direction, long tick) {}
}
