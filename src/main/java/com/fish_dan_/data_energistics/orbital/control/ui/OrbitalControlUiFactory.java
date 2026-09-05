package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;
import com.fish_dan_.data_energistics.orbital.control.session.OrbitalControlServerSession;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Builds the one protocol-stable dashboard shared by terminal, Curios and bound-console entry points. */
public final class OrbitalControlUiFactory {

    private static final String MENU_SNAPSHOT_SYNC_NAME = "orbital_control_menu_snapshot";

    private OrbitalControlUiFactory() {}

    /** Creates the common tree, one typed RPC, one atomic snapshot and the side-specific menu lifecycle. */
    public static ModularUI create(
                                   Player player,
                                   Supplier<OrbitalControlTerminalSnapshot> snapshotSupplier,
                                   BooleanSupplier sourceValid,
                                   OrbitalControlUiSource source) {
        boolean clientSide = player.level().isClientSide();
        OrbitalControlServerSession serverSession = player instanceof ServerPlayer serverPlayer ?
                new OrbitalControlServerSession(serverPlayer, snapshotSupplier, sourceValid) : null;
        OrbitalControlDashboard dashboard = OrbitalControlDashboard.create(player);
        RPCEmitter commandEmitter = dashboard.root.addRPCEvent(RPCEventBuilder.simple(
                OrbitalControlIntent.class,
                intent -> {
                    if (serverSession != null) {
                        serverSession.handle(intent);
                    }
                }));
        OrbitalControlClientBinding clientBinding = clientSide ?
                OrbitalControlClientBridge.bind(dashboard, source, commandEmitter) : null;
        OrbitalControlMenuSnapshot initialSnapshot = serverSession == null ?
                OrbitalControlMenuSnapshot.EMPTY : serverSession.snapshot();
        if (clientBinding != null) {
            dashboard.apply(initialSnapshot);
            clientBinding.acceptSnapshot(initialSnapshot);
        }

        SyncValue<OrbitalControlMenuSnapshot> snapshotSync = new SyncValue<>(
                MENU_SNAPSHOT_SYNC_NAME,
                OrbitalControlMenuSnapshot.class,
                initialSnapshot);
        snapshotSync.setToSync(!clientSide);
        snapshotSync.setAcceptSync(clientSide);
        snapshotSync.addListener(snapshot -> {
            dashboard.apply(snapshot);
            Objects.requireNonNull(clientBinding, "A client snapshot requires an attached client binding")
                    .acceptSnapshot(snapshot);
        });
        if (serverSession != null) {
            snapshotSync.setValueProvider(serverSession::snapshot);
        }

        ModularUI modularUI = ModularUI.of(UI.of(dashboard.root), player);
        if (serverSession != null) {
            serverSession.attach(modularUI);
        }
        if (clientBinding != null) {
            clientBinding.attach(modularUI);
        }
        modularUI.syncManager.registerSyncValue(snapshotSync);
        return modularUI;
    }
}
