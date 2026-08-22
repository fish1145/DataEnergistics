package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
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

    private static final String SNAPSHOT_SYNC_NAME = "orbital_control_snapshot";
    private static final String PREVIEW_SYNC_NAME = "orbital_control_preview";
    private static final String PREVIEW_NONCE_SYNC_NAME = "orbital_control_preview_nonce";
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
        OrbitalControlUiSyncAccessors.init();
        boolean clientSide = player.level().isClientSide();
        OrbitalControlTerminalSnapshot initialSnapshot = clientSide ?
                OrbitalControlTerminalSnapshot.EMPTY : snapshotSupplier.get();

        UIElement root = new UIElement();
        root.setId("orbital_control_root");
        root.layout(layout -> layout.width(UI_WIDTH).height(UI_HEIGHT));
        OrbitalControlUiTheme.stylePanel(root, Tone.SHELL);

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
        previousWeapon.setOnServerClick(event -> cycleWeapon(player, false, sourceValid));
        nextWeapon.setOnServerClick(event -> cycleWeapon(player, true, sourceValid));

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

        OrbitalControlOverviewPanel overview = OrbitalControlOverviewPanel.create(player, sourceValid);
        OrbitalControlUiTheme.place(
                overview.root(),
                CONTENT_LEFT,
                CONTENT_TOP,
                OrbitalControlOverviewPanel.WIDTH,
                OrbitalControlOverviewPanel.HEIGHT);
        OrbitalFireControlPanel.View fireControl = OrbitalFireControlPanel.create(
                root,
                player,
                sourceValid,
                source,
                clientSide);
        OrbitalControlUiTheme.place(
                fireControl.root(),
                CONTENT_LEFT,
                CONTENT_TOP,
                OrbitalFireControlPanel.WIDTH,
                OrbitalFireControlPanel.HEIGHT);
        boolean returningFromMap = clientSide && OrbitalMapSelectionClientSession.hasPending();
        overview.root().setDisplay(!returningFromMap);
        fireControl.root().setDisplay(returningFromMap);

        overviewTab.setOnClick(event -> {
            if (clientSide) {
                fireControl.cancelHold();
                overview.root().setDisplay(true);
                fireControl.root().setDisplay(false);
            }
        });
        fireControlTab.setOnClick(event -> {
            if (clientSide) {
                overview.root().setDisplay(false);
                fireControl.root().setDisplay(true);
            }
        });

        root.addChildren(header, sidebar, overview.root(), fireControl.root());
        updateSnapshot(initialSnapshot, overview, fireControl, selectorPosition, previousWeapon, nextWeapon);

        SyncValue<OrbitalControlTerminalSnapshot> snapshotSync = new SyncValue<>(
                SNAPSHOT_SYNC_NAME,
                OrbitalControlTerminalSnapshot.class,
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
        if (!clientSide) {
            snapshotSync.setValueProvider(snapshotSupplier);
        }

        SyncValue<Component> previewSync = new SyncValue<>(
                PREVIEW_SYNC_NAME,
                Component.class,
                Component.translatable("screen.data_energistics.orbital_control_terminal.preview.none"));
        previewSync.setToSync(!clientSide);
        previewSync.setAcceptSync(clientSide);
        previewSync.addListener(fireControl::updatePreview);

        SyncValue<String> previewNonceSync = new SyncValue<>(PREVIEW_NONCE_SYNC_NAME, String.class, "");
        previewNonceSync.setToSync(!clientSide);
        previewNonceSync.setAcceptSync(clientSide);
        previewNonceSync.addListener(fireControl::updatePreviewNonce);
        if (!clientSide) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            previewSync.setValueProvider(() -> OrbitalControlActionDispatcher.currentPreviewStatus(serverPlayer));
            previewNonceSync.setValueProvider(() -> OrbitalControlActionDispatcher.currentPreviewNonce(serverPlayer));
            fireControl.updatePreview(OrbitalControlActionDispatcher.currentPreviewStatus(serverPlayer));
            fireControl.updatePreviewNonce(OrbitalControlActionDispatcher.currentPreviewNonce(serverPlayer));
        }

        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        modularUI.syncManager.registerSyncValue(snapshotSync);
        modularUI.syncManager.registerSyncValue(previewSync);
        modularUI.syncManager.registerSyncValue(previewNonceSync);
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

    private static void cycleWeapon(Player player, boolean forward, BooleanSupplier sourceValid) {
        if (player instanceof ServerPlayer serverPlayer && sourceValid.getAsBoolean()) {
            OrbitalControlActionDispatcher.cycleWeapon(serverPlayer, forward);
        }
    }

    private static void updateSnapshot(
                                       OrbitalControlTerminalSnapshot snapshot,
                                       OrbitalControlOverviewPanel overview,
                                       OrbitalFireControlPanel.View fireControl,
                                       Label selectorPosition,
                                       Button previousWeapon,
                                       Button nextWeapon) {
        overview.update(snapshot);
        fireControl.updateSnapshot(snapshot);
        selectorPosition.setValue(OrbitalControlPresentation.selectorPosition(snapshot));
        boolean multipleWeapons = snapshot.weapons().size() > 1;
        previousWeapon.setActive(multipleWeapons);
        nextWeapon.setActive(multipleWeapons);
    }
}
