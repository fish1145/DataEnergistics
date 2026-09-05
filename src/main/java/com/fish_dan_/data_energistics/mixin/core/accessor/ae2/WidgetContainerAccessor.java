package com.fish_dan_.data_energistics.mixin.core.accessor.ae2;

import appeng.client.gui.WidgetContainer;

import net.minecraft.client.gui.components.AbstractWidget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(WidgetContainer.class)
public interface WidgetContainerAccessor {

    @Accessor("widgets")
    Map<String, AbstractWidget> dataEnergistics$getWidgets();
}
