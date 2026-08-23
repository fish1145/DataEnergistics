package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import java.util.EnumMap;
import java.util.Locale;

/** Structured selected-weapon overview shared by both control entry points. */
final class OrbitalControlOverviewPanel {

    static final int WIDTH = 412;
    static final int HEIGHT = 304;

    private static final int MODE_GAP = 6;
    private static final int MODE_WIDTH = (WIDTH - MODE_GAP * 2) / 3;
    private static final int MODE_TOP = 126;
    private static final int MODE_HEIGHT = HEIGHT - MODE_TOP;

    private final UIElement root;
    private final UIElement content;
    private final Label empty;
    private final Label weaponTitle;
    private final Label identity;
    private final Label lifecycle;
    private final Label celestialEnergy;
    private final Label aeEnergy;
    private final EnumMap<OrbitalAttackMode, Label> modeLabels = new EnumMap<>(OrbitalAttackMode.class);
    private final EnumMap<OrbitalAttackMode, Button> modeActions = new EnumMap<>(OrbitalAttackMode.class);

    private OrbitalControlOverviewPanel(RPCEmitter commandEmitter, boolean clientSide) {
        this.root = new UIElement();
        this.root.setId("orbital_control_overview");
        this.root.layout(layout -> layout.width(WIDTH).height(HEIGHT));

        this.content = new UIElement();
        this.content.setId("orbital_control_overview_content");
        this.content.layout(layout -> layout.width(WIDTH).height(HEIGHT));

        this.empty = OrbitalControlUiTheme.label(
                "orbital_control_overview_empty",
                Component.translatable("screen.data_energistics.orbital_control_terminal.empty"),
                12,
                18,
                WIDTH - 24,
                40,
                OrbitalControlUiTheme.MUTED_TEXT,
                10,
                TextWrap.WRAP);

        UIElement identityCard = OrbitalControlUiTheme.panel(
                "orbital_control_overview_identity_card",
                0,
                0,
                WIDTH,
                56,
                Tone.PANEL);
        this.weaponTitle = OrbitalControlUiTheme.label(
                "orbital_control_overview_weapon",
                Component.empty(),
                10,
                8,
                184,
                16,
                OrbitalControlUiTheme.ACCENT_TEXT,
                11,
                TextWrap.HOVER_ROLL);
        this.identity = OrbitalControlUiTheme.label(
                "orbital_control_overview_identity",
                Component.empty(),
                10,
                30,
                196,
                16,
                OrbitalControlUiTheme.MUTED_TEXT,
                9,
                TextWrap.HOVER_ROLL);
        this.lifecycle = OrbitalControlUiTheme.label(
                "orbital_control_overview_lifecycle",
                Component.empty(),
                214,
                10,
                188,
                34,
                OrbitalControlUiTheme.TEXT,
                9,
                TextWrap.WRAP);
        identityCard.addChildren(this.weaponTitle, this.identity, this.lifecycle);

        UIElement celestialCard = OrbitalControlUiTheme.panel(
                "orbital_control_overview_celestial_card",
                0,
                64,
                203,
                54,
                Tone.ACCENT);
        this.celestialEnergy = resourceLabel(
                "orbital_control_overview_celestial",
                celestialCard);
        UIElement aeCard = OrbitalControlUiTheme.panel(
                "orbital_control_overview_ae_card",
                211,
                64,
                201,
                54,
                Tone.PANEL_ALT);
        this.aeEnergy = resourceLabel("orbital_control_overview_ae", aeCard);

        for (OrbitalAttackMode mode : OrbitalAttackMode.values()) {
            createModeCard(commandEmitter, clientSide, mode);
        }

        this.content.addChildren(identityCard, celestialCard, aeCard);
        this.root.addChildren(this.content, this.empty);
        update(OrbitalControlTerminalSnapshot.EMPTY);
    }

    static OrbitalControlOverviewPanel create(RPCEmitter commandEmitter, boolean clientSide) {
        return new OrbitalControlOverviewPanel(commandEmitter, clientSide);
    }

    UIElement root() {
        return this.root;
    }

    void update(OrbitalControlTerminalSnapshot snapshot) {
        WeaponEntry weapon = snapshot.selectedWeapon().orElse(null);
        boolean available = weapon != null;
        this.content.setDisplay(available);
        this.empty.setDisplay(!available);
        if (!available) {
            return;
        }

        this.weaponTitle.setValue(OrbitalControlPresentation.weaponTitle(snapshot));
        this.identity.setValue(OrbitalControlPresentation.identity(weapon));
        this.lifecycle.setValue(OrbitalControlPresentation.lifecycle(weapon));
        this.celestialEnergy.setValue(OrbitalControlPresentation.celestialEnergy(weapon));
        this.aeEnergy.setValue(OrbitalControlPresentation.aeEnergy(weapon));
        for (OrbitalAttackMode mode : OrbitalAttackMode.values()) {
            this.modeLabels.get(mode).setValue(OrbitalControlPresentation.modeCard(weapon, mode));
            Button action = this.modeActions.get(mode);
            action.setText(OrbitalControlPresentation.modeAction(weapon, mode));
            boolean actionAvailable = OrbitalControlPresentation.modeActionAvailable(weapon, mode);
            action.setActive(actionAvailable);
            OrbitalControlUiTheme.stylePanel(action, actionAvailable ? Tone.DANGER : Tone.PANEL_ALT);
        }
    }

    private Label resourceLabel(String id, UIElement card) {
        Label label = OrbitalControlUiTheme.label(
                id,
                Component.empty(),
                10,
                9,
                181,
                36,
                OrbitalControlUiTheme.TEXT,
                10,
                TextWrap.WRAP);
        card.addChild(label);
        return label;
    }

    private void createModeCard(RPCEmitter commandEmitter, boolean clientSide, OrbitalAttackMode mode) {
        int left = mode.ordinal() * (MODE_WIDTH + MODE_GAP);
        String id = "orbital_control_overview_mode_" + mode.name().toLowerCase(Locale.ROOT);
        UIElement card = OrbitalControlUiTheme.panel(
                id,
                left,
                MODE_TOP,
                MODE_WIDTH,
                MODE_HEIGHT,
                Tone.PANEL);
        Label label = OrbitalControlUiTheme.label(
                id + "_status",
                Component.empty(),
                8,
                8,
                MODE_WIDTH - 16,
                MODE_HEIGHT - 46,
                OrbitalControlUiTheme.TEXT,
                9,
                TextWrap.WRAP);
        Button action = OrbitalControlUiTheme.button(
                id + "_action",
                Component.translatable("screen.data_energistics.orbital_control_terminal.overview.action.none"),
                8,
                MODE_HEIGHT - 32,
                MODE_WIDTH - 16,
                24,
                Tone.PANEL_ALT);
        if (clientSide) {
            action.setOnClick(event -> commandEmitter.send(new OrbitalControlIntent.CancelOrAbortMode(mode)));
        }
        card.addChildren(label, action);
        this.content.addChild(card);
        this.modeLabels.put(mode, label);
        this.modeActions.put(mode, action);
    }
}
