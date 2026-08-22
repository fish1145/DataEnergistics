package com.fish_dan_.data_energistics.mixin.ftbchunks;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses only the large map's own cursor panel and drag sentinel. */
@Mixin(value = LargeMapScreen.class, remap = false)
public interface LargeMapScreenAccessor {

    @Accessor("regionPanel")
    RegionMapPanel dataEnergistics$getRegionPanel();

    @Accessor("grabbed")
    int dataEnergistics$getGrabbed();
}
