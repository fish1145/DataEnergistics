package com.fish_dan_.data_energistics.orbital.control.ui.target;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlDraft;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;

/** Scrollable target inputs and preview; action buttons belong to the stationary terminal footer. */
public final class OrbitalTargetPanel {

    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.";
    public final ScrollerView root = OrbitalControlUiTheme.scrollPanel("orbital_target_panel");
    public final OrbitalModePicker mode = new OrbitalModePicker();
    public final TextField dimension;
    public final TextField targetX;
    public final TextField targetZ;
    public final Selector<OrbitalTargetYMode> targetYMode;
    public final TextField targetYValue;
    public final Selector<Integer> radius;
    public final Selector<OrbitalDirectedEnergyDepth> depth;
    public final Label preview;
    private final UIElement directedFields = new UIElement();
    private final Label formMessage;
    private final List<UIElement> inputs;

    public OrbitalTargetPanel(Player player) {
        UIElement content = root.viewContainer;
        content.layout(layout -> layout.width(226).height(340));
        OrbitalControlUiTheme.place(mode, 2, 2, 222, 24);
        content.addChild(mode);
        dimension = OrbitalControlUiTheme.textInput("orbital_fire_control_dimension",
                player.level().dimension().location().toString(), 44, 34, 178);
        dimension.setResourceLocationOnly();
        content.addChildren(caption("fire_control.dimension", 2, 34, 40), dimension);
        targetX = integer("orbital_fire_control_x", player.blockPosition().getX(),
                -OrbitalFireControlDraft.MAX_TARGET_COORDINATE, OrbitalFireControlDraft.MAX_TARGET_COORDINATE, 20, 60, 88);
        targetZ = integer("orbital_fire_control_z", player.blockPosition().getZ(),
                -OrbitalFireControlDraft.MAX_TARGET_COORDINATE, OrbitalFireControlDraft.MAX_TARGET_COORDINATE, 134, 60, 88);
        content.addChildren(caption("fire_control.x", 2, 60, 16), targetX,
                caption("fire_control.z", 116, 60, 16), targetZ);
        targetYMode = OrbitalControlUiTheme.choices("orbital_fire_control_y_mode", 44, 86, 96,
                List.of(OrbitalTargetYMode.values()), OrbitalTargetYMode.SURFACE_OFFSET,
                value -> Component.translatable(PREFIX + "fire_control.y_mode." +
                        (value == OrbitalTargetYMode.ABSOLUTE ? "absolute" : "surface_offset")));
        targetYValue = integer("orbital_fire_control_y", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 158, 86, 64);
        content.addChildren(caption("fire_control.y_mode", 2, 86, 40), targetYMode,
                caption("fire_control.y", 144, 86, 12), targetYValue);

        List<Integer> radii = radiusOptions(DataEnergisticsConfiguration.INSTANCE.stellarErasureDevice);
        radius = OrbitalControlUiTheme.choices("orbital_fire_control_radius", 44, 0, 64, radii, radii.getFirst(),
                value -> Component.translatable(PREFIX + "fire_control.radius.blocks", value));
        depth = OrbitalControlUiTheme.choices("orbital_fire_control_depth", 150, 0, 72,
                List.of(OrbitalDirectedEnergyDepth.values()), OrbitalDirectedEnergyDepth.DEPTH_32,
                value -> Component.translatable(PREFIX + "depth." + switch (value) {
                    case DEPTH_32 -> "32";
                    case DEPTH_128 -> "128";
                    case DEPTH_512 -> "512";
                    case THROUGH -> "through";
                }));
        OrbitalControlUiTheme.place(directedFields, 0, 112, 226, 24);
        directedFields.addChildren(caption("fire_control.radius", 2, 0, 40), radius,
                caption("fire_control.depth", 114, 0, 34), depth);
        formMessage = OrbitalControlUiTheme.label("orbital_form_message", Component.empty(), 2, 140, 220, 30,
                OrbitalControlUiTheme.ACCENT_TEXT, 9, TextWrap.WRAP);
        preview = OrbitalControlUiTheme.label("orbital_fire_control_preview_status",
                Component.translatable(PREFIX + "preview.none"), 2, 178, 220, 154,
                OrbitalControlUiTheme.TEXT, 9, TextWrap.WRAP);
        preview.textStyle(style -> style.adaptiveHeight(true));
        content.addChildren(directedFields, formMessage, preview);
        inputs = List.of(mode, dimension, targetX, targetZ, targetYMode, targetYValue, radius, depth);
        setOperable(false);
    }

    public void setOperable(boolean operable) {
        inputs.forEach(input -> input.setActive(operable));
        updateMode(operable);
    }

    public void updateMode(boolean operable) {
        boolean directed = mode.getValue() == OrbitalAttackMode.DIRECTED_ENERGY;
        directedFields.setDisplay(directed);
        radius.setActive(operable && directed);
        depth.setActive(operable && directed);
    }

    public void setMessage(Component message) {
        formMessage.setValue(message);
    }

    private static Label caption(String key, int x, int y, int width) {
        return OrbitalControlUiTheme.label("orbital_caption_" + key, Component.translatable(PREFIX + key),
                x, y, width, 20, OrbitalControlUiTheme.MUTED_TEXT, 9, TextWrap.HOVER_ROLL);
    }

    private static TextField integer(String id, int initial, int minimum, int maximum, int x, int y, int width) {
        TextField field = OrbitalControlUiTheme.textInput(id, Integer.toString(initial), x, y, width);
        field.setNumbersOnlyInt(minimum, maximum);
        return field;
    }

    private static List<Integer> radiusOptions(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        int minimum = settings.directedEnergyMinimumRadius;
        int maximum = settings.directedEnergyMaximumRadius;
        int step = settings.directedEnergyRadiusStep;
        if (minimum < 1 || maximum < minimum || step < 1) {
            throw new IllegalStateException("Invalid directed-energy radius configuration");
        }
        IntArrayList values = new IntArrayList();
        for (long value = minimum; value <= maximum; value += step) {
            values.add((int) value);
        }
        return values;
    }
}
