package com.fish_dan_.data_energistics.orbital.control.ui.target;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPresentation;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;

/** Always-visible mode choices; listeners run only for an actual user-visible selection change. */
public final class OrbitalModePicker extends UIElement {

    private final ObjectArrayList<Button> buttons = new ObjectArrayList<>();
    private final ObjectArrayList<Consumer<OrbitalAttackMode>> listeners = new ObjectArrayList<>();
    private OrbitalAttackMode selected = OrbitalAttackMode.KINETIC;

    public OrbitalModePicker() {
        setId("orbital_fire_control_mode");
        int index = 0;
        for (OrbitalAttackMode mode : OrbitalAttackMode.values()) {
            Button button = OrbitalControlUiTheme.button("orbital_mode_" + mode.name(),
                    OrbitalControlPresentation.modeName(mode), index++ * 74, 0, 72, 24, Tone.PANEL);
            button.text.textStyle(style -> style.textWrap(TextWrap.WRAP));
            button.setOnClick(ignored -> setSelected(mode, true));
            buttons.add(button);
            addChild(button);
        }
        updateSelection();
    }

    public OrbitalAttackMode getValue() {
        return selected;
    }

    public void setSelected(OrbitalAttackMode mode, boolean notify) {
        if (selected == mode) {
            return;
        }
        selected = mode;
        updateSelection();
        if (notify) {
            listeners.forEach(listener -> listener.accept(mode));
        }
    }

    public void registerValueListener(Consumer<OrbitalAttackMode> listener) {
        listeners.add(listener);
    }

    private void updateSelection() {
        for (int index = 0; index < buttons.size(); index++) {
            OrbitalControlUiTheme.styleButton(buttons.get(index),
                    OrbitalAttackMode.values()[index] == selected ? Tone.ACCENT : Tone.PANEL);
        }
    }
}
