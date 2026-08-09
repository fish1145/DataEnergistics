package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.DEKeyMappings;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.item.vacuum.MeVacuumItem;
import com.fish_dan_.data_energistics.network.action.DigitalStorageDepotBucketModePayload;
import com.fish_dan_.data_energistics.network.action.DigitalStorageDepotScrollPayload;
import com.fish_dan_.data_energistics.network.action.MeVacuumLaunchPayload;
import com.fish_dan_.data_energistics.registry.DEMobEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.Input;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

final class ClientInputHandler {

    private ClientInputHandler() {}

    static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!event.getEntity().hasEffect(DEMobEffects.DATA_DISORDER)) {
            return;
        }

        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (minecraft.player.hasEffect(DEMobEffects.DATA_DISORDER)) {
            if (event.isAttack() || event.isUseItem() || event.isPickBlock()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }

        if (!event.isAttack() || !tryLaunchMeVacuum(minecraft)) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
    }

    static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }

        if (tryLaunchMeVacuum(minecraft)) {
            event.setCanceled(true);
        }
    }

    static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode())) {
            tryLaunchMeVacuum(minecraft);
        }
    }

    private static boolean tryLaunchMeVacuum(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null) {
            return false;
        }

        if (minecraft.player.hasEffect(DEMobEffects.DATA_DISORDER) || !minecraft.player.isUsingItem() || minecraft.player.isShiftKeyDown()) {
            return false;
        }

        ItemStack stack = minecraft.player.getUseItem();
        if (!(stack.getItem() instanceof MeVacuumItem)) {
            return false;
        }

        InteractionHand usedHand = minecraft.player.getUsedItemHand();
        PacketDistributor.sendToServer(new MeVacuumLaunchPayload(usedHand == InteractionHand.OFF_HAND));
        return true;
    }

    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        boolean controlDown = Screen.hasControlDown();
        boolean altDown = Screen.hasAltDown();
        if (controlDown == altDown) {
            return;
        }

        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();
        boolean useMainHand = DigitalStorageDepotBlockItem.isDepotStack(mainHand);
        boolean useOffHand = !useMainHand && DigitalStorageDepotBlockItem.isDepotStack(offHand);
        if (!useMainHand && !useOffHand) {
            return;
        }

        double delta = event.getScrollDeltaY();
        if (delta == 0) {
            return;
        }

        PacketDistributor.sendToServer(new DigitalStorageDepotScrollPayload(delta < 0, useOffHand, altDown));
        event.setCanceled(true);
    }

    static void toggleDepotBucketMode(Minecraft minecraft) {
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }

        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();
        boolean useMainHand = DigitalStorageDepotBlockItem.isDepotStack(mainHand);
        boolean useOffHand = !useMainHand && DigitalStorageDepotBlockItem.isDepotStack(offHand);
        if (!useMainHand && !useOffHand) {
            return;
        }

        PacketDistributor.sendToServer(new DigitalStorageDepotBucketModePayload(useOffHand));
    }

    static boolean consumeToggleDepotBucketModeClick() {
        return DEKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE.consumeClick();
    }
}
