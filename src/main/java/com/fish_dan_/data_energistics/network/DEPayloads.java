package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.network.action.DataTeleportAnchorKnifeTeleportPayload;
import com.fish_dan_.data_energistics.network.action.DigitalStorageDepotBucketModePayload;
import com.fish_dan_.data_energistics.network.action.DigitalStorageDepotScrollPayload;
import com.fish_dan_.data_energistics.network.action.MeVacuumLaunchPayload;
import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassRequestPayload;
import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlHudSnapshotPayload;
import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapRequestPayload;
import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapResponsePayload;
import com.fish_dan_.data_energistics.network.patternencoding.MultiblockPatternTransferPayload;
import com.fish_dan_.data_energistics.network.patternencoding.PatternEncodingPreferencesAckPayload;
import com.fish_dan_.data_energistics.network.patternencoding.PatternEncodingPreferencesSyncPayload;
import com.fish_dan_.data_energistics.network.patternencoding.PatternUploadSucceededPayload;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetsPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityHostedActionResponsePayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityHostedAutoBuildPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityHostedPatternMigrationPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityHostedPatternQuickMovePayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityHostedPatternSlotPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityHostedPriorityPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityOpenCpuStatusPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityRefundPatternsPayload;
import com.fish_dan_.data_energistics.network.trinity.TrinityRefundRetainedItemsPayload;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftConfirmCyclePayload;
import com.fish_dan_.data_energistics.network.ui.HostUiRequestPayload;
import com.fish_dan_.data_energistics.network.ui.HostUiResponsePayload;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalSelectPayload;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalStateSyncPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DEPayloads {

    private DEPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("6");
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
                TrinityCraftConfirmCyclePayload.TYPE,
                TrinityCraftConfirmCyclePayload.STREAM_CODEC,
                TrinityCraftConfirmCyclePayload::handle);
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
                TrinityHostedPriorityPayload.TYPE,
                TrinityHostedPriorityPayload.STREAM_CODEC,
                TrinityHostedPriorityPayload::handle);
        registrar.playToServer(
                TrinityHostedPatternSlotPayload.TYPE,
                TrinityHostedPatternSlotPayload.STREAM_CODEC,
                TrinityHostedPatternSlotPayload::handle);
        registrar.playToServer(
                TrinityHostedPatternQuickMovePayload.TYPE,
                TrinityHostedPatternQuickMovePayload.STREAM_CODEC,
                TrinityHostedPatternQuickMovePayload::handle);
        registrar.playToServer(
                TrinityHostedPatternMigrationPayload.TYPE,
                TrinityHostedPatternMigrationPayload.STREAM_CODEC,
                TrinityHostedPatternMigrationPayload::handle);
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
        registrar.playToClient(
                OrbitalControlHudSnapshotPayload.TYPE,
                OrbitalControlHudSnapshotPayload.STREAM_CODEC,
                OrbitalControlHudSnapshotPayload::handle);
        registrar.playToServer(
                OrbitalTacticalMapRequestPayload.TYPE,
                OrbitalTacticalMapRequestPayload.STREAM_CODEC,
                OrbitalTacticalMapRequestPayload::handle);
        registrar.playToClient(
                OrbitalTacticalMapResponsePayload.TYPE,
                OrbitalTacticalMapResponsePayload.STREAM_CODEC,
                OrbitalTacticalMapResponsePayload::handle);
    }
}
