package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudClientState;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalTacticalMapClientState;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingClientPreferencesAccess;
import com.fish_dan_.data_energistics.client.preferences.PatternUploadSucceededClientHandler;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalAttackVisualClientState;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalProjectionVisualClientState;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

final class ClientGameEventRegistrar {

    private ClientGameEventRegistrar() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMovementInputUpdate);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onInteractionKeyTriggered);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMouseButtonPre);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(ClientTickHandler::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenInitPost);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenRenderPost);
        NeoForge.EVENT_BUS.addListener(ClientGameEventRegistrar::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientGameEventRegistrar::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        PatternEncodingClientPreferencesAccess.activateCurrentServerProfile();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            PatternEncodingPreferenceSession.clearForMenu(event.getPlayer().containerMenu);
        }
        PatternUploadSucceededClientHandler.clear();
        OrbitalMapSelectionClientSession.clear();
        OrbitalTacticalMapClientState.clear();
        OrbitalAttackVisualClientState.clear();
        OrbitalProjectionVisualClientState.clear();
        OrbitalControlHudClientState.clear();
        PatternEncodingClientPreferencesAccess.deactivateServerProfile();
    }
}
