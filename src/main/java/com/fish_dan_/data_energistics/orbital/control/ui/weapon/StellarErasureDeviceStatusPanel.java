package com.fish_dan_.data_energistics.orbital.control.ui.weapon;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPresentation;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Readable weapon details and task controls, kept separate from target editing. */
public final class StellarErasureDeviceStatusPanel {

    public final ScrollerView root = OrbitalControlUiTheme.scrollPanel("stellar_erasure_device_status");
    public final ObjectArrayList<ModeRow> modeRows = new ObjectArrayList<>();
    private final Label details = OrbitalControlUiTheme.label("stellar_erasure_device_details", Component.empty(),
            4, 4, 216, 110, OrbitalControlUiTheme.TEXT, 9, TextWrap.WRAP);

    public StellarErasureDeviceStatusPanel() {
        root.viewContainer.layout(layout -> layout.width(226).height(302));
        root.addScrollViewChild(details);
        for (OrbitalAttackMode mode : OrbitalAttackMode.values()) {
            int y = 120 + mode.ordinal() * 60;
            UIElement row = OrbitalControlUiTheme.panel("orbital_task_" + mode.name(), 2, y, 220, 56, Tone.PANEL_ALT);
            Label status = OrbitalControlUiTheme.label("orbital_task_state_" + mode.name(), Component.empty(),
                    4, 3, 212, 24, OrbitalControlUiTheme.TEXT, 9, TextWrap.WRAP);
            Button action = OrbitalControlUiTheme.button("orbital_task_action_" + mode.name(), Component.empty(),
                    4, 30, 212, 22, Tone.DANGER);
            row.addChildren(status, action);
            root.addScrollViewChild(row);
            modeRows.add(new ModeRow(mode, status, action));
        }
    }

    public void apply(OrbitalControlTerminalSnapshot snapshot) {
        var weapon = snapshot.selectedWeapon().orElse(null);
        if (weapon == null) {
            details.setValue(Component.translatable("screen.data_energistics.orbital_control_terminal.empty"));
        } else {
            details.setValue(OrbitalControlPresentation.weaponTitle(snapshot).copy().append("\n")
                    .append(OrbitalControlPresentation.identity(weapon)).append("\n")
                    .append(OrbitalControlPresentation.lifecycle(weapon)).append("\n")
                    .append(OrbitalControlPresentation.stellarFlux(weapon)).append("\n")
                    .append(OrbitalControlPresentation.aeEnergy(weapon)));
        }
        for (ModeRow row : modeRows) {
            boolean available = weapon != null && OrbitalControlPresentation.modeActionAvailable(weapon, row.mode());
            row.status().setValue(weapon == null ? OrbitalControlPresentation.modeName(row.mode()) :
                    OrbitalControlPresentation.modeRail(weapon, row.mode()));
            row.action().setActive(available);
            row.action().setDisplay(available);
            if (available) {
                row.action().setText(OrbitalControlPresentation.modeAction(weapon, row.mode()));
            }
        }
    }

    public record ModeRow(OrbitalAttackMode mode, Label status, Button action) {}
}
