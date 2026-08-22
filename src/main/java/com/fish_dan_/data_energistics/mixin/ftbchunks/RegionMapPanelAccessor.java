package com.fish_dan_.data_energistics.mixin.ftbchunks;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the owning large-map screen without duplicating FTB Chunks coordinate conversion. */
@Mixin(value = RegionMapPanel.class, remap = false)
public interface RegionMapPanelAccessor {

    @Accessor("largeMap")
    LargeMapScreen dataEnergistics$getLargeMap();
}
