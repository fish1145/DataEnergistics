package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.util.UniversalTerminalHostAccessor;

import net.minecraft.world.entity.player.Player;

import appeng.menu.me.crafting.CraftConfirmMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuMixin {

    @Inject(method = "startJob",
            at = @At(value = "INVOKE",
                     target = "Lappeng/api/storage/ISubMenuHost;returnToMainMenu(Lnet/minecraft/world/entity/player/Player;Lappeng/menu/ISubMenu;)V",
                     shift = At.Shift.AFTER),
            remap = false)
    private void dataEnergistics$returnUniversalTerminalAfterSubmit(CallbackInfo ci) {
        CraftConfirmMenu self = (CraftConfirmMenu) (Object) this;
        Player player = self.getPlayer();
        if (player == null || player.level().isClientSide) {
            return;
        }

        Object target = self.getTarget();
        if (target instanceof UniversalTerminalPart part) {
            part.returnToMainMenu(player, self);
            return;
        }

        if (target instanceof UniversalTerminalHostAccessor accessor) {
            accessor.getUniversalTerminalPart().returnToMainMenu(player, self);
        }
    }
}
