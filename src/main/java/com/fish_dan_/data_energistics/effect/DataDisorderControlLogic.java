package com.fish_dan_.data_energistics.effect;

import com.fish_dan_.data_energistics.registry.DEMobEffects;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class DataDisorderControlLogic {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!hasDataDisorder(player)) {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, Math.min(movement.y, 0.0D), 0.0D);
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.stopUsingItem();
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (hasDataDisorder(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (hasDataDisorder(event.getEntity())) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (hasDataDisorder(event.getEntity())) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (hasDataDisorder(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (hasDataDisorder(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean hasDataDisorder(Player player) {
        return player.hasEffect(DEMobEffects.DATA_DISORDER);
    }
}
