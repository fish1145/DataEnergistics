package com.fish_dan_.data_energistics.mixin.core;

import appeng.helpers.InterfaceLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InterfaceLogic.class)
public interface InterfaceLogicTickAccessor {

    @Invoker("updateStorage")
    boolean dataEnergistics$invokeUpdateStorage();

    @Invoker("hasWorkToDo")
    boolean dataEnergistics$invokeHasWorkToDo();
}
