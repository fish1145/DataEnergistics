package com.fish_dan_.data_energistics.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                UniversalTerminalStateSyncPayload.TYPE,
                UniversalTerminalStateSyncPayload.STREAM_CODEC,
                UniversalTerminalStateSyncPayload::handle);
        registrar.playToServer(
                UniversalTerminalCyclePayload.TYPE,
                UniversalTerminalCyclePayload.STREAM_CODEC,
                UniversalTerminalCyclePayload::handle);
        registrar.playToServer(
                UniversalTerminalSelectPayload.TYPE,
                UniversalTerminalSelectPayload.STREAM_CODEC,
                UniversalTerminalSelectPayload::handle);
        registrar.playToServer(
                DataTeleportAnchorKnifeTeleportPayload.TYPE,
                DataTeleportAnchorKnifeTeleportPayload.STREAM_CODEC,
                DataTeleportAnchorKnifeTeleportPayload::handle);
    }
}
