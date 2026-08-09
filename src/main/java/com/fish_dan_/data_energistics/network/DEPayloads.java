package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassRequestPayload;
import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetsPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DEPayloads {

    private DEPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToServer(
                PatternEncodingPreferencesSyncPayload.TYPE,
                PatternEncodingPreferencesSyncPayload.STREAM_CODEC,
                PatternEncodingPreferencesSyncPayload::handle);
        registrar.playToClient(
                PatternEncodingPreferencesAckPayload.TYPE,
                PatternEncodingPreferencesAckPayload.STREAM_CODEC,
                PatternEncodingPreferencesAckPayload::handle);
        registrar.playToClient(
                PatternUploadSucceededPayload.TYPE,
                PatternUploadSucceededPayload.STREAM_CODEC,
                PatternUploadSucceededPayload::handle);
        registrar.playToClient(
                DataDistributionTowerTargetsPayload.TYPE,
                DataDistributionTowerTargetsPayload.STREAM_CODEC,
                DataDistributionTowerTargetsPayload::handle);
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
        registrar.playToServer(
                MeVacuumLaunchPayload.TYPE,
                MeVacuumLaunchPayload.STREAM_CODEC,
                MeVacuumLaunchPayload::handle);
        registrar.playToServer(
                DigitalStorageDepotScrollPayload.TYPE,
                DigitalStorageDepotScrollPayload.STREAM_CODEC,
                DigitalStorageDepotScrollPayload::handle);
        registrar.playToServer(
                DigitalStorageDepotBucketModePayload.TYPE,
                DigitalStorageDepotBucketModePayload.STREAM_CODEC,
                DigitalStorageDepotBucketModePayload::handle);
        registrar.playToServer(
                HostUiRequestPayload.TYPE,
                HostUiRequestPayload.STREAM_CODEC,
                HostUiRequestPayload::handle);
        registrar.playToServer(
                MultiblockPatternTransferPayload.TYPE,
                MultiblockPatternTransferPayload.STREAM_CODEC,
                MultiblockPatternTransferPayload::handle);
        registrar.playToClient(
                HostUiResponsePayload.TYPE,
                HostUiResponsePayload.STREAM_CODEC,
                HostUiResponsePayload::handle);
        registrar.playToServer(
                TrinityHostedAutoBuildPayload.TYPE,
                TrinityHostedAutoBuildPayload.STREAM_CODEC,
                TrinityHostedAutoBuildPayload::handle);
        registrar.playToServer(
                TrinityRefundPatternsPayload.TYPE,
                TrinityRefundPatternsPayload.STREAM_CODEC,
                TrinityRefundPatternsPayload::handle);
        registrar.playToServer(
                TrinityRefundRetainedItemsPayload.TYPE,
                TrinityRefundRetainedItemsPayload.STREAM_CODEC,
                TrinityRefundRetainedItemsPayload::handle);
        registrar.playToServer(
                TrinityOpenCpuStatusPayload.TYPE,
                TrinityOpenCpuStatusPayload.STREAM_CODEC,
                TrinityOpenCpuStatusPayload::handle);
        registrar.playToClient(
                TrinityHostedActionResponsePayload.TYPE,
                TrinityHostedActionResponsePayload.STREAM_CODEC,
                TrinityHostedActionResponsePayload::handle);
        registrar.playToServer(
                DataMeteoriteCompassRequestPayload.TYPE,
                DataMeteoriteCompassRequestPayload.STREAM_CODEC,
                DataMeteoriteCompassRequestPayload::handle);
        registrar.playToClient(
                DataMeteoriteCompassResponsePayload.TYPE,
                DataMeteoriteCompassResponsePayload.STREAM_CODEC,
                DataMeteoriteCompassResponsePayload::handle);
    }
}
