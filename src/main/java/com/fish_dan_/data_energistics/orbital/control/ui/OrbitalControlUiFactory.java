package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;
import com.fish_dan_.data_energistics.orbital.control.session.OrbitalControlServerSession;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Builds the one LDLib2 control surface shared by a handheld terminal and bound console. */
public final class OrbitalControlUiFactory {

    private static final String MENU_SNAPSHOT_SYNC_NAME = "orbital_control_menu_snapshot";
    private static final int UI_WIDTH = 540;
    private static final int UI_HEIGHT = 384;
    private static final int HEADER_HEIGHT = 34;
    private static final int SIDEBAR_LEFT = 8;
    private static final int SIDEBAR_TOP = 42;
    private static final int SIDEBAR_WIDTH = 112;
    private static final int CONTENT_LEFT = 120;
    private static final int CONTENT_TOP = 42;

    private OrbitalControlUiFactory() {}

    /**
     * Creates one typed, server-authoritative UI tree. Entry points differ only in their source-validity predicate.
     */
    public static ModularUI create(
                                   Player player,
                                   Supplier<OrbitalControlTerminalSnapshot> snapshotSupplier,
                                   BooleanSupplier sourceValid,
                                   OrbitalControlUiSource source) {
        boolean clientSide = player.level().isClientSide();
        OrbitalControlServerSession serverSession = player instanceof ServerPlayer serverPlayer ?
                new OrbitalControlServerSession(serverPlayer, snapshotSupplier, sourceValid) : null;
        OrbitalControlMenuSnapshot initialSnapshot = clientSide || serverSession == null ?
                OrbitalControlMenuSnapshot.EMPTY : serverSession.snapshot();

        UIElement root = new UIElement();
        root.setId("orbital_control_root");
        root.layout(layout -> layout.width(UI_WIDTH).height(UI_HEIGHT));
        OrbitalControlUiTheme.stylePanel(root, Tone.SHELL);
        RPCEmitter commandEmitter = root.addRPCEvent(RPCEventBuilder.simple(
                OrbitalControlIntent.class,
                intent -> {
                    if (serverSession != null) {
                        serverSession.handle(intent);
                    }
                }));

        UIElement header = OrbitalControlUiTheme.panel(
                "orbital_control_header",
                0,
                0,
                UI_WIDTH,
                HEADER_HEIGHT,
                Tone.ACCENT);
        Label title = OrbitalControlUiTheme.label(
                "orbital_control_title",
                Component.translatable("screen.data_energistics.orbital_control_terminal.title"),
                12,
                8,
                250,
                18,
                OrbitalControlUiTheme.TEXT,
                12,
                TextWrap.HOVER_ROLL);
        Label subtitle = OrbitalControlUiTheme.label(
                "orbital_control_subtitle",
                Component.translatable("screen.data_energistics.orbital_control_terminal.subtitle"),
                270,
                10,
                UI_WIDTH - 282,
                16,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.HOVER_ROLL);
        header.addChildren(title, subtitle);

        UIElement sidebar = OrbitalControlUiTheme.panel(
                "orbital_control_sidebar",
                SIDEBAR_LEFT,
                SIDEBAR_TOP,
                SIDEBAR_WIDTH - 8,
                OrbitalControlOverviewPanel.HEIGHT,
                Tone.PANEL_ALT);
        Label selectorTitle = OrbitalControlUiTheme.label(
                "orbital_control_selector_title",
                Component.translatable("screen.data_energistics.orbital_control_terminal.selector.title"),
                8,
                10,
                SIDEBAR_WIDTH - 24,
                14,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL);
        Label selectorPosition = OrbitalControlUiTheme.label(
                "orbital_control_selector_position",
                Component.empty(),
                8,
                30,
                SIDEBAR_WIDTH - 24,
                28,
                OrbitalControlUiTheme.TEXT,
                9,
                TextWrap.WRAP);
        Button previousWeapon = OrbitalControlUiTheme.button(
                "orbital_control_previous_weapon",
                Component.translatable("screen.data_energistics.orbital_control_terminal.action.previous_weapon"),
                8,
                64,
                42,
                22,
                Tone.PANEL);
        Button nextWeapon = OrbitalControlUiTheme.button(
                "orbital_control_next_weapon",
                Component.translatable("screen.data_energistics.orbital_control_terminal.action.next_weapon"),
                54,
                64,
                42,
                22,
                Tone.PANEL);
        if (clientSide) {
            previousWeapon.setOnClick(event -> commandEmitter.send(new OrbitalControlIntent.CycleWeapon(false)));
            nextWeapon.setOnClick(event -> commandEmitter.send(new OrbitalControlIntent.CycleWeapon(true)));
        }

        Button overviewTab = pageButton(
                "orbital_control_overview_tab",
                "screen.data_energistics.orbital_control_terminal.page.overview",
                100);
        Button fireControlTab = pageButton(
                "orbital_control_fire_control_tab",
                "screen.data_energistics.orbital_control_terminal.page.fire_control",
                130);
        Label navigationHint = OrbitalControlUiTheme.label(
                "orbital_control_navigation_hint",
                Component.translatable("screen.data_energistics.orbital_control_terminal.navigation.hint"),
                8,
                168,
                SIDEBAR_WIDTH - 24,
                120,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.WRAP);
        sidebar.addChildren(
                selectorTitle,
                selectorPosition,
                previousWeapon,
                nextWeapon,
                overviewTab,
                fireControlTab,
                navigationHint);

        OrbitalControlOverviewPanel overview = OrbitalControlOverviewPanel.create(commandEmitter, clientSide);
        OrbitalControlUiTheme.place(
                overview.root(),
                CONTENT_LEFT,
                CONTENT_TOP,
                OrbitalControlOverviewPanel.WIDTH,
                OrbitalControlOverviewPanel.HEIGHT);
        OrbitalFireControlPanel.View fireControl = OrbitalFireControlPanel.create(
                root,
                player,
                source,
                clientSide,
                commandEmitter);
        OrbitalControlUiTheme.place(
                fireControl.root(),
                CONTENT_LEFT,
                CONTENT_TOP,
                OrbitalFireControlPanel.WIDTH,
                OrbitalFireControlPanel.HEIGHT);
        boolean returningFromMap = clientSide && OrbitalMapSelectionClientSession.hasPending();
        overview.root().setDisplay(!returningFromMap);
        fireControl.root().setDisplay(returningFromMap);

        if (clientSide) {
            overviewTab.setOnClick(event -> {
                fireControl.cancelHold();
                overview.root().setDisplay(true);
                fireControl.root().setDisplay(false);
            });
            fireControlTab.setOnClick(event -> {
                overview.root().setDisplay(false);
                fireControl.root().setDisplay(true);
            });
        }

        root.addChildren(header, sidebar, overview.root(), fireControl.root());
        updateSnapshot(initialSnapshot, overview, fireControl, selectorPosition, previousWeapon, nextWeapon);

        SyncValue<OrbitalControlMenuSnapshot> snapshotSync = new SyncValue<>(
                MENU_SNAPSHOT_SYNC_NAME,
                OrbitalControlMenuSnapshot.class,
                initialSnapshot);
        snapshotSync.setToSync(!clientSide);
        snapshotSync.setAcceptSync(clientSide);
        snapshotSync.addListener(snapshot -> updateSnapshot(
                snapshot,
                overview,
                fireControl,
                selectorPosition,
                previousWeapon,
                nextWeapon));
        if (serverSession != null) {
            snapshotSync.setValueProvider(serverSession::snapshot);
        }

        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        if (serverSession != null) {
            serverSession.attach(modularUI);
        }
        modularUI.syncManager.registerSyncValue(snapshotSync);
        return modularUI;
    }

    private static Button pageButton(String id, String translationKey, int top) {
        return OrbitalControlUiTheme.button(
                id,
                Component.translatable(translationKey),
                8,
                top,
                SIDEBAR_WIDTH - 24,
                24,
                Tone.PANEL);
    }

    private static void updateSnapshot(
                                       OrbitalControlMenuSnapshot snapshot,
                                       OrbitalControlOverviewPanel overview,
                                       OrbitalFireControlPanel.View fireControl,
                                       Label selectorPosition,
                                       Button previousWeapon,
                                       Button nextWeapon) {
        OrbitalControlTerminalSnapshot terminal = snapshot.terminal();
        overview.update(terminal);
        fireControl.updateSnapshot(terminal);
        fireControl.updateSession(snapshot.fireControl(), snapshot.feedback());
        selectorPosition.setValue(OrbitalControlPresentation.selectorPosition(terminal));
        boolean multipleWeapons = terminal.weapons().size() > 1;
        previousWeapon.setActive(multipleWeapons);
        nextWeapon.setActive(multipleWeapons);
    }
}
