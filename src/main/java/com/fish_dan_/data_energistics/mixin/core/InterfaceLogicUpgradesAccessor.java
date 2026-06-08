package com.fish_dan_.data_energistics.mixin.core;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InterfaceLogic.class)
public interface InterfaceLogicUpgradesAccessor {

    @Accessor("upgrades")
    IUpgradeInventory dataEnergistics$getUpgradesField();

    @Accessor("upgrades")
    @Mutable
    void dataEnergistics$setUpgradesField(IUpgradeInventory upgrades);

    @Invoker("onUpgradesChanged")
    void dataEnergistics$invokeOnUpgradesChanged();
}
