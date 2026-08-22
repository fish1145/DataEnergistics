package com.fish_dan_.data_energistics.integration.map.xaero.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapter;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapters;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

import java.util.UUID;

/** Bridges the supported Xaero map screen lifecycle to the common provider-bound selection session. */
public final class XaeroWorldMapOrbitalAdapter implements TacticalMapAdapter {

    public static final XaeroWorldMapOrbitalAdapter INSTANCE = new XaeroWorldMapOrbitalAdapter();

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "xaero_world_map");

    private @Nullable UUID selectionToken;

    private XaeroWorldMapOrbitalAdapter() {}

    /** Registers the adapter after the client loader has confirmed Xaero World Map and XaeroLib are present. */
    public static void register() {
        TacticalMapAdapters.register(INSTANCE);
        Data_Energistics.LOGGER.info("Registered Xaero World Map orbital tactical-map adapter");
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable(
                "screen.data_energistics.orbital_control_terminal.fire_control.map.provider.xaero");
    }

    @Override
    public SelectionStart startSelection(Minecraft minecraft, UUID sessionToken) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (minecraft.player == null || cameraEntity == null) {
            return SelectionStart.FAILED;
        }
        WorldMapSession session = WorldMapSession.getForPlayer(minecraft.player);
        if (session == null || !session.isUsable()) {
            return SelectionStart.FAILED;
        }
        this.selectionToken = sessionToken;
        minecraft.setScreen(new GuiMap(null, null, session.getMapProcessor(), cameraEntity));
        return SelectionStart.EXTERNAL_WAITING;
    }

    /** Accepts a real non-drag left click only while this adapter still owns the matching one-shot token. */
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

    /** Returns whether Xaero should append the direct orbital-preview action to its current map popup. */
    public boolean shouldOfferPreviewAction() {
        UUID currentToken = this.selectionToken;
        boolean activeSelection = currentToken != null && isAwaitingSelection(currentToken);
        return activeSelection || OrbitalMapSelectionClientSession.canOpenDirectPreview();
    }

    /** Opens the common preview path from a snapshot of Xaero's right-click coordinates. */
    public void openRightClickPreview(ResourceLocation dimensionId, int targetX, int targetZ) {
        if (openPreview(dimensionId, targetX, targetZ)) {
            this.selectionToken = null;
        }
    }
}
