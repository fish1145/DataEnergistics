package com.fish_dan_.data_energistics.mixin.ftbchunks;

import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalAdapter;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalMapBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenu;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Appends one preview action while preserving FTB Chunks' existing waypoint menu construction and handling. */
@Mixin(value = LargeMapScreen.class, remap = false)
public abstract class LargeMapScreenMixin implements FtbChunksOrbitalMapBridge.Input {

    @Redirect(
              method = "mousePressed",
              at = @At(
                       value = "INVOKE",
                       target = "Ldev/ftb/mods/ftbchunks/client/gui/LargeMapScreen;openContextMenu(Ljava/util/List;)Ldev/ftb/mods/ftblibrary/ui/ContextMenu;"),
              require = 0)
    private ContextMenu dataEnergistics$appendOrbitalPreview(
                                                             LargeMapScreen screen,
                                                             List<ContextMenuItem> items) {
        if (FtbChunksOrbitalAdapter.INSTANCE.shouldOfferPreviewAction()) {
            RegionMapPanel regionPanel = ((LargeMapScreenAccessor) screen).dataEnergistics$getRegionPanel();
            BlockPos target = regionPanel.blockPos();
            items.add(new ContextMenuItem(
                    Component.translatable(
                            "screen.data_energistics.orbital_control_terminal.fire_control.map.preview"),
                    Icons.MAP,
                    ignoredButton -> FtbChunksOrbitalAdapter.INSTANCE.openRightClickPreview(
                            screen.currentDimension().location(),
                            target.getX(),
                            target.getZ())));
        }
        return screen.openContextMenu(items);
    }
}
