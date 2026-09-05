package com.fish_dan_.data_energistics.integration.map.ftbchunks.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapter;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapters;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Opens FTB Chunks through its public large-map API and delegates all selected coordinates to the common preview. */
public final class FtbChunksOrbitalAdapter implements TacticalMapAdapter {

    public static final FtbChunksOrbitalAdapter INSTANCE = new FtbChunksOrbitalAdapter();

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "ftb_chunks");

    private @Nullable UUID selectionToken;

    private FtbChunksOrbitalAdapter() {}

    /** Registers the adapter only after the client loader has confirmed FTB Chunks is present. */
    public static void register() {
        TacticalMapAdapters.register(INSTANCE);
        Data_Energistics.LOGGER.info("Registered FTB Chunks orbital tactical-map adapter");
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable(
                "screen.data_energistics.orbital_control_terminal.fire_control.map.provider.ftb_chunks");
    }

    @Override
    public SelectionStart startSelection(Minecraft minecraft, UUID sessionToken) {
        if (mixinBridgeUnavailable()) {
            throw new IllegalStateException("FTB Chunks orbital Mixin bridge is unavailable");
        }
        this.selectionToken = sessionToken;
        LargeMapScreen.openMap();
        if (!(minecraft.screen instanceof ScreenWrapper wrapper) ||
                !(wrapper.getGui() instanceof LargeMapScreen)) {
            this.selectionToken = null;
            return SelectionStart.FAILED;
        }
        return SelectionStart.EXTERNAL_WAITING;
    }

    private static boolean mixinBridgeUnavailable() {
        return !FtbChunksOrbitalMapBridge.Input.class.isAssignableFrom(LargeMapScreen.class) ||
                !FtbChunksOrbitalMapBridge.Access.class.isAssignableFrom(LargeMapScreen.class) ||
                !FtbChunksOrbitalMapBridge.Input.class.isAssignableFrom(RegionMapPanel.class) ||
                !FtbChunksOrbitalMapBridge.Access.class.isAssignableFrom(RegionMapPanel.class);
    }

    /** Accepts a non-drag map-background click only while FTB Chunks owns the matching one-shot token. */
    public void completeMapClick(ResourceLocation dimensionId, int targetX, int targetZ) {
        UUID currentToken = this.selectionToken;
        if (currentToken == null) {
            return;
        }
        if (!isAwaitingSelection(currentToken)) {
            this.selectionToken = null;
            return;
        }
        if (completeSelection(currentToken, dimensionId, targetX, targetZ)) {
            this.selectionToken = null;
        }
    }

    /** Returns whether the FTB Chunks context menu should expose the common orbital-preview action. */
    public boolean shouldOfferPreviewAction() {
        UUID currentToken = this.selectionToken;
        boolean activeSelection = currentToken != null && isAwaitingSelection(currentToken);
        return activeSelection || OrbitalMapSelectionClientSession.canOpenDirectPreview();
    }

    /** Opens the common preview path from a snapshot of the FTB Chunks cursor position. */
    public void openRightClickPreview(ResourceLocation dimensionId, int targetX, int targetZ) {
        if (openPreview(dimensionId, targetX, targetZ)) {
            this.selectionToken = null;
        }
    }
}
