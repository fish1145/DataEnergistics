package com.fish_dan_.data_energistics.mixin;

import appeng.menu.AEBaseMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AEBaseMenu.class)
public interface AEBaseMenuActionAccessor {

    @Invoker("sendClientAction")
    void dataEnergistics$invokeSendClientAction(String action, Object arg);
}
