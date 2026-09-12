package com.fish_dan_.data_energistics.integration.map.journeymap.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapter;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapters;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.event.PopupMenuEvent;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Uses only JourneyMap's supported v2 fullscreen events; no JourneyMap mixin or internal screen class is involved. */
@SuppressWarnings("unused") // JourneyMap instantiates this annotated plugin from its mod-scan metadata.
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapOrbitalPlugin implements IClientPlugin, TacticalMapAdapter {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "journeymap");
    private static final String POPUP_TRANSLATION_KEY = "screen.data_energistics.orbital_control_terminal.fire_control.map.preview";

    private @Nullable UUID selectionToken;

    @Override
    public void initialize(IClientAPI ignoredClientApi) {
        TacticalMapAdapters.register(this);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(
                Data_Energistics.MODID,
                this::onMapClick);
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(
                Data_Energistics.MODID,
                this::onPopupMenu);
        Data_Energistics.LOGGER.info("Registered JourneyMap orbital tactical-map adapter");
    }

    @Override
    public String getModId() {
        return Data_Energistics.MODID;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public SelectionStart startSelection(Minecraft minecraft, UUID sessionToken) {
        this.selectionToken = sessionToken;
        minecraft.setScreen(null);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable(
                            "screen.data_energistics.orbital_control_terminal.fire_control.map.journeymap.open_hint"),
                    true);
        }
        return SelectionStart.EXTERNAL_WAITING;
    }

    private void onMapClick(FullscreenMapEvent.ClickEvent event) {
        UUID currentToken = this.selectionToken;
        if (currentToken == null ||
                event.getStage() != FullscreenMapEvent.Stage.POST ||
                event.getButton() != 0) {
            return;
        }
        if (!isAwaitingSelection(currentToken)) {
            this.selectionToken = null;
            return;
        }
        BlockPos target = event.getLocation();
        if (completeSelection(
                currentToken,
                event.getLevel().location(),
                target.getX(),
                target.getZ())) {
            this.selectionToken = null;
        }
    }

    private void onPopupMenu(PopupMenuEvent.FullscreenPopupMenuEvent event) {
        UUID currentToken = this.selectionToken;
        boolean activeSelection = currentToken != null && isAwaitingSelection(currentToken);
        if (activeSelection || OrbitalMapSelectionClientSession.canOpenDirectPreview()) {
            ResourceLocation dimensionId = event.getFullscreen().getUiState().dimension.location();
            event.getPopupMenu().addMenuItem(POPUP_TRANSLATION_KEY, target -> {
                if (openPreview(dimensionId, target.getX(), target.getZ())) {
                    this.selectionToken = null;
                }
            });
        }
    }
}
