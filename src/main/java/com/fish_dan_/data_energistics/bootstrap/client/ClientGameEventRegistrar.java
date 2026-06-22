package com.fish_dan_.data_energistics.bootstrap.client;

import net.neoforged.neoforge.common.NeoForge;

final class ClientGameEventRegistrar {

    private ClientGameEventRegistrar() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMovementInputUpdate);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onInteractionKeyTriggered);
        NeoForge.EVENT_BUS.addListener(ClientTickHandler::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenInitPost);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenRenderPost);
    }
}
