package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Pure LDLib2 component tree for the compact single-page orbital command dashboard. */
public final class OrbitalControlDashboard {

    public static final int WIDTH = 520;
    public static final int HEIGHT = 336;
    public static final int MAP_RADIUS = 3;

    private static final int HEADER_HEIGHT = 30;
    private static final int BODY_TOP = 34;
    private static final int BODY_HEIGHT = 296;
    private static final int STATUS_LEFT = 6;
    private static final int STATUS_WIDTH = 124;
    private static final int TARGET_LEFT = 134;
    private static final int TARGET_WIDTH = 230;
    private static final int MAP_LEFT = 368;
    private static final int MAP_WIDTH = 146;
    private static final int FIELD_HEIGHT = 18;
    private static final int MAP_CELL_SIZE = 18;
    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.";

    public final UIElement root;
    public final Label feedback;
    public final Label selectorPosition;
    public final Button previousWeapon;
    public final Button nextWeapon;
    public final TextField dimension;
    public final Selector<OrbitalAttackMode> mode;
    public final TextField targetX;
    public final TextField targetZ;
    public final Selector<OrbitalTargetYMode> targetYMode;
    public final TextField targetYValue;
    public final TextField radius;
    public final Selector<OrbitalDirectedEnergyDepth> depth;
    public final Selector<MapProviderOption> mapProvider;
    public final Button selectOnMap;
    public final Button refreshPreview;
    public final Button confirm;
    public final Button mapRefresh;
    public final Label mapStatus;
    public final Label preview;
    public final ObjectArrayList<Button> mapCells;
    public final ModeRow[] modeRows;

    private final Label weaponTitle;
    private final Label identity;
    private final Label lifecycle;
    private final Label celestialEnergy;
    private final Label aeEnergy;
    private final ObjectArrayList<UIElement> operableElements;

    private OrbitalControlDashboard(Player player) {
        this.root = new UIElement();
        this.root.setId("orbital_control_root");
        this.root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
        OrbitalControlUiTheme.stylePanel(this.root, Tone.SHELL);

        UIElement header = OrbitalControlUiTheme.panel(
                "orbital_control_header",
                0,
                0,
                WIDTH,
                HEADER_HEIGHT,
                Tone.ACCENT);
        Label title = label(
                "orbital_control_title",
                Component.translatable(PREFIX + "title"),
                10,
                7,
                176,
                16,
                OrbitalControlUiTheme.TEXT,
                11,
                TextWrap.HOVER_ROLL);
        this.feedback = label(
                "orbital_control_feedback",
                Component.translatable(PREFIX + "subtitle"),
                194,
                7,
                316,
                16,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.HOVER_ROLL);
        header.addChildren(title, this.feedback);

        UIElement status = OrbitalControlUiTheme.panel(
                "orbital_control_status_rail",
                STATUS_LEFT,
                BODY_TOP,
                STATUS_WIDTH,
                BODY_HEIGHT,
                Tone.PANEL_ALT);
        this.selectorPosition = label(
                "orbital_control_selector_position",
                Component.translatable(PREFIX + "selector.empty"),
                6,
                6,
                STATUS_WIDTH - 12,
                16,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL);
        this.previousWeapon = button(
                "orbital_control_previous_weapon",
                Component.translatable(PREFIX + "action.previous_weapon"),
                6,
                25,
                53,
                22,
                Tone.PANEL);
        this.nextWeapon = button(
                "orbital_control_next_weapon",
                Component.translatable(PREFIX + "action.next_weapon"),
                65,
                25,
                53,
                22,
                Tone.PANEL);
        this.weaponTitle = label(
                "orbital_control_overview_weapon",
                Component.translatable(PREFIX + "empty"),
                6,
                52,
                STATUS_WIDTH - 12,
                16,
                OrbitalControlUiTheme.ACCENT_TEXT,
                10,
                TextWrap.HOVER_ROLL);
        this.identity = label(
                "orbital_control_overview_identity",
                Component.empty(),
                6,
                70,
                STATUS_WIDTH - 12,
                14,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.HOVER_ROLL);
        this.lifecycle = label(
                "orbital_control_overview_lifecycle",
                Component.empty(),
                6,
                86,
                STATUS_WIDTH - 12,
                30,
                OrbitalControlUiTheme.TEXT,
                8,
                TextWrap.WRAP);
        this.celestialEnergy = resourceLabel("orbital_control_overview_celestial", 120, Tone.ACCENT);
        this.aeEnergy = resourceLabel("orbital_control_overview_ae", 150, Tone.PANEL);
        status.addChildren(
                this.selectorPosition,
                this.previousWeapon,
                this.nextWeapon,
                this.weaponTitle,
                this.identity,
                this.lifecycle,
                this.celestialEnergy,
                this.aeEnergy);
        this.modeRows = new ModeRow[OrbitalAttackMode.values().length];
        for (OrbitalAttackMode attackMode : OrbitalAttackMode.values()) {
            ModeRow row = createModeRow(attackMode, 182 + attackMode.ordinal() * 36);
            this.modeRows[attackMode.ordinal()] = row;
            status.addChild(row.root);
        }

        UIElement target = OrbitalControlUiTheme.panel(
                "orbital_control_target_and_preview",
                TARGET_LEFT,
                BODY_TOP,
                TARGET_WIDTH,
                BODY_HEIGHT,
                Tone.PANEL);
        target.style(style -> style.tooltips(Component.translatable(PREFIX + "fire_control.hint")));
        target.addChild(label(
                "orbital_fire_control_target_title",
                Component.translatable(PREFIX + "page.fire_control"),
                6,
                5,
                TARGET_WIDTH - 12,
                14,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL));
        this.mode = selector(
                "orbital_fire_control_mode",
                42,
                21,
                182,
                List.of(OrbitalAttackMode.values()),
                OrbitalAttackMode.KINETIC,
                OrbitalControlPresentation::modeName);
        target.addChildren(fieldLabel("orbital_fire_control_mode_label", PREFIX + "fire_control.mode", 6, 21, 32), this.mode);

        this.dimension = textField(
                "orbital_fire_control_dimension",
                player.level().dimension().location().toString(),
                42,
                43,
                182);
        this.dimension.setResourceLocationOnly();
        target.addChildren(fieldLabel("orbital_fire_control_dimension_label", PREFIX + "fire_control.dimension", 6, 43, 32), this.dimension);

        this.targetX = integerField("orbital_fire_control_x", player.blockPosition().getX(), -30_000_000, 30_000_000, 20, 65, 82);
        this.targetZ = integerField("orbital_fire_control_z", player.blockPosition().getZ(), -30_000_000, 30_000_000, 132, 65, 92);
        target.addChildren(
                fieldLabel("orbital_fire_control_x_label", PREFIX + "fire_control.x", 6, 65, 12),
                this.targetX,
                fieldLabel("orbital_fire_control_z_label", PREFIX + "fire_control.z", 110, 65, 18),
                this.targetZ);

        this.targetYMode = selector(
                "orbital_fire_control_y_mode",
                42,
                87,
                96,
                List.of(OrbitalTargetYMode.values()),
                OrbitalTargetYMode.SURFACE_OFFSET,
                OrbitalControlDashboard::targetYModeName);
        this.targetYValue = integerField(
                "orbital_fire_control_y",
                0,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                158,
                87,
                66);
        target.addChildren(
                fieldLabel("orbital_fire_control_y_mode_label", PREFIX + "fire_control.y_mode", 6, 87, 32),
                this.targetYMode,
                fieldLabel("orbital_fire_control_y_label", PREFIX + "fire_control.y", 144, 87, 10),
                this.targetYValue);

        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        this.radius = integerField(
                "orbital_fire_control_radius",
                settings.directedEnergyMinimumRadius,
                settings.directedEnergyMinimumRadius,
                settings.directedEnergyMaximumRadius,
                42,
                109,
                56);
        this.depth = selector(
                "orbital_fire_control_depth",
                126,
                109,
                98,
                List.of(OrbitalDirectedEnergyDepth.values()),
                OrbitalDirectedEnergyDepth.DEPTH_32,
                OrbitalControlDashboard::depthName);
        this.radius.setActive(false);
        this.depth.setActive(false);
        target.addChildren(
                fieldLabel("orbital_fire_control_radius_label", PREFIX + "fire_control.radius", 6, 109, 32),
                this.radius,
                fieldLabel("orbital_fire_control_depth_label", PREFIX + "fire_control.depth", 104, 109, 18),
                this.depth);

        this.refreshPreview = button(
                "orbital_fire_control_preview",
                Component.translatable(PREFIX + "fire_control.preview"),
                6,
                136,
                106,
                22,
                Tone.ACCENT);
        this.confirm = button(
                "orbital_fire_control_confirm",
                Component.translatable(PREFIX + "fire_control.confirm"),
                116,
                136,
                108,
                22,
                Tone.DANGER);
        Label previewTitle = label(
                "orbital_fire_control_preview_title",
                Component.translatable(PREFIX + "fire_control.preview.title"),
                6,
                164,
                TARGET_WIDTH - 12,
                14,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL);
        this.preview = label(
                "orbital_fire_control_preview_status",
                Component.translatable(PREFIX + "preview.none"),
                6,
                180,
                TARGET_WIDTH - 12,
                108,
                OrbitalControlUiTheme.TEXT,
                8,
                TextWrap.WRAP);
        target.addChildren(this.refreshPreview, this.confirm, previewTitle, this.preview);

        UIElement map = OrbitalControlUiTheme.panel(
                "orbital_control_tactical_map",
                MAP_LEFT,
                BODY_TOP,
                MAP_WIDTH,
                BODY_HEIGHT,
                Tone.PANEL_ALT);
        map.addChild(label(
                "orbital_fire_control_map_title",
                Component.translatable(PREFIX + "fire_control.map.title_compact"),
                6,
                5,
                MAP_WIDTH - 12,
                14,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL));
        MapProviderOption builtin = new MapProviderOption(
                Data_Energistics.id("builtin_tactical_map"),
                Component.translatable(PREFIX + "fire_control.map.provider.builtin"));
        this.mapProvider = selector(
                "orbital_fire_control_map_provider",
                6,
                20,
                MAP_WIDTH - 12,
                List.of(builtin),
                builtin,
                option -> option == null ? Component.empty() : option.label);
        this.selectOnMap = button(
                "orbital_fire_control_select_on_map",
                Component.translatable(PREFIX + "fire_control.map.select"),
                6,
                42,
                MAP_WIDTH - 12,
                22,
                Tone.PANEL);
        this.mapRefresh = button(
                "orbital_fire_control_map_refresh",
                Component.translatable(PREFIX + "fire_control.map.refresh"),
                6,
                66,
                MAP_WIDTH - 12,
                22,
                Tone.ACCENT);
        this.mapStatus = label(
                "orbital_fire_control_map_status",
                Component.translatable(PREFIX + "fire_control.map.status"),
                6,
                92,
                MAP_WIDTH - 12,
                38,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.WRAP);
        map.addChildren(this.mapProvider, this.selectOnMap, this.mapRefresh, this.mapStatus);
        this.mapCells = new ObjectArrayList<>((MAP_RADIUS * 2 + 1) * (MAP_RADIUS * 2 + 1));
        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                Button cell = button(
                        "orbital_fire_control_map_cell_" + (offsetX + MAP_RADIUS) + "_" + (offsetZ + MAP_RADIUS),
                        Component.literal("?"),
                        10 + (offsetX + MAP_RADIUS) * MAP_CELL_SIZE,
                        134 + (offsetZ + MAP_RADIUS) * MAP_CELL_SIZE,
                        MAP_CELL_SIZE - 1,
                        MAP_CELL_SIZE - 1,
                        Tone.PANEL);
                this.mapCells.add(cell);
                map.addChild(cell);
            }
        }
        map.addChild(label(
                "orbital_fire_control_map_legend",
                Component.translatable(PREFIX + "fire_control.map.legend"),
                6,
                264,
                MAP_WIDTH - 12,
                26,
                OrbitalControlUiTheme.MUTED_TEXT,
                7,
                TextWrap.WRAP));

        this.operableElements = new ObjectArrayList<>(13 + this.mapCells.size());
        this.operableElements.addAll(List.of(
                this.dimension,
                this.mode,
                this.targetX,
                this.targetZ,
                this.targetYMode,
                this.targetYValue,
                this.radius,
                this.depth,
                this.mapProvider,
                this.selectOnMap,
                this.refreshPreview,
                this.confirm,
                this.mapRefresh));
        this.operableElements.addAll(this.mapCells);
        this.root.addChildren(header, status, target, map);
    }

    public static OrbitalControlDashboard create(Player player) {
        return new OrbitalControlDashboard(player);
    }

    /** Projects one received immutable snapshot into the existing component tree without rebuilding it. */
    public void apply(OrbitalControlMenuSnapshot snapshot) {
        OrbitalControlTerminalSnapshot terminal = snapshot.terminal();
        WeaponEntry weapon = terminal.selectedWeapon().orElse(null);
        this.feedback.setValue(OrbitalControlPresentation.feedback(snapshot.feedback()));
        this.selectorPosition.setValue(OrbitalControlPresentation.selectorPosition(terminal));
        this.previousWeapon.setActive(terminal.weapons().size() > 1);
        this.nextWeapon.setActive(terminal.weapons().size() > 1);
        this.weaponTitle.setValue(OrbitalControlPresentation.weaponTitle(terminal));
        boolean operable = weapon != null && weapon.canOperate();
        if (weapon == null) {
            this.identity.setValue(Component.empty());
            this.lifecycle.setValue(Component.translatable(PREFIX + "empty"));
            this.celestialEnergy.setValue(Component.empty());
            this.aeEnergy.setValue(Component.empty());
        } else {
            this.identity.setValue(OrbitalControlPresentation.identity(weapon));
            this.lifecycle.setValue(OrbitalControlPresentation.lifecycle(weapon));
            this.celestialEnergy.setValue(OrbitalControlPresentation.celestialEnergy(weapon));
            this.aeEnergy.setValue(OrbitalControlPresentation.aeEnergy(weapon));
        }
        for (OrbitalAttackMode attackMode : OrbitalAttackMode.values()) {
            ModeRow row = this.modeRows[attackMode.ordinal()];
            if (weapon == null) {
                row.status.setValue(Component.translatable(PREFIX + "overview.mode.idle", OrbitalControlPresentation.modeName(attackMode)));
                row.action.setText(Component.translatable(PREFIX + "overview.action.none"));
                row.action.setActive(false);
            } else {
                row.status.setValue(OrbitalControlPresentation.modeRail(weapon, attackMode));
                row.action.setText(OrbitalControlPresentation.modeAction(weapon, attackMode));
                boolean available = OrbitalControlPresentation.modeActionAvailable(weapon, attackMode);
                row.action.setActive(available);
                OrbitalControlUiTheme.stylePanel(row.action, available ? Tone.DANGER : Tone.PANEL_ALT);
            }
        }
        for (UIElement element : this.operableElements) {
            element.setActive(operable);
        }
        boolean directed = this.mode.getValue() == OrbitalAttackMode.DIRECTED_ENERGY;
        this.radius.setActive(operable && directed);
        this.depth.setActive(operable && directed);
        OrbitalFireControlSessionSnapshot.Phase phase = snapshot.fireControl().phase();
        this.refreshPreview.setActive(operable && phase != OrbitalFireControlSessionSnapshot.Phase.CALCULATING);
        this.confirm.setActive(operable &&
                (phase == OrbitalFireControlSessionSnapshot.Phase.READY ||
                        phase == OrbitalFireControlSessionSnapshot.Phase.HOLDING));
        this.preview.setValue(OrbitalControlPresentation.fireControl(snapshot.fireControl(), snapshot.feedback()));
    }

    /** Applies the selected target mode immediately rather than waiting for another server snapshot. */
    public void updateDirectedFields(boolean operable) {
        boolean directed = this.mode.getValue() == OrbitalAttackMode.DIRECTED_ENERGY;
        this.radius.setActive(operable && directed);
        this.depth.setActive(operable && directed);
    }

    private Label resourceLabel(String id, int top, Tone tone) {
        Label value = label(
                id,
                Component.empty(),
                6,
                top,
                STATUS_WIDTH - 12,
                26,
                OrbitalControlUiTheme.TEXT,
                8,
                TextWrap.HOVER_ROLL);
        OrbitalControlUiTheme.stylePanel(value, tone);
        return value;
    }

    private ModeRow createModeRow(OrbitalAttackMode mode, int top) {
        UIElement row = OrbitalControlUiTheme.panel(
                "orbital_control_overview_mode_" + mode.name().toLowerCase(Locale.ROOT),
                6,
                top,
                STATUS_WIDTH - 12,
                34,
                Tone.PANEL);
        Label status = label(
                row.getId() + "_status",
                Component.empty(),
                4,
                4,
                58,
                26,
                OrbitalControlUiTheme.TEXT,
                7,
                TextWrap.WRAP);
        Button action = button(
                row.getId() + "_action",
                Component.translatable(PREFIX + "overview.action.none"),
                64,
                7,
                44,
                20,
                Tone.PANEL_ALT);
        row.addChildren(status, action);
        return new ModeRow(mode, row, status, action);
    }

    private static Label fieldLabel(String id, String key, int left, int top, int width) {
        return label(id, Component.translatable(key), left, top, width, FIELD_HEIGHT, OrbitalControlUiTheme.MUTED_TEXT, 8, TextWrap.HOVER_ROLL);
    }

    private static TextField textField(String id, String initial, int left, int top, int width) {
        TextField field = new TextField();
        field.setId(id);
        field.setText(initial, false);
        OrbitalControlUiTheme.place(field, left, top, width, FIELD_HEIGHT);
        return field;
    }

    private static TextField integerField(
                                          String id,
                                          int initial,
                                          int minimum,
                                          int maximum,
                                          int left,
                                          int top,
                                          int width) {
        TextField field = textField(id, Integer.toString(initial), left, top, width);
        field.setNumbersOnlyInt(minimum, maximum);
        return field;
    }

    private static <T> Selector<T> selector(
                                            String id,
                                            int left,
                                            int top,
                                            int width,
                                            List<T> candidates,
                                            T selected,
                                            Function<T, Component> label) {
        Selector<T> selector = new Selector<>();
        selector.setId(id);
        selector.setCandidates(candidates);
        selector.setCandidateUIProvider(UIElementProvider.text(label));
        selector.setSelected(selected, false);
        selector.selectorStyle(style -> style.closeAfterSelect(true).maxItemCount(Math.max(1, candidates.size())));
        OrbitalControlUiTheme.place(selector, left, top, width, FIELD_HEIGHT);
        return selector;
    }

    private static Component targetYModeName(@Nullable OrbitalTargetYMode mode) {
        if (mode == null) {
            return Component.empty();
        }
        return Component.translatable(switch (mode) {
            case ABSOLUTE -> PREFIX + "fire_control.y_mode.absolute";
            case SURFACE_OFFSET -> PREFIX + "fire_control.y_mode.surface_offset";
        });
    }

    private static Component depthName(@Nullable OrbitalDirectedEnergyDepth depth) {
        if (depth == null) {
            return Component.empty();
        }
        return Component.translatable(switch (depth) {
            case DEPTH_32 -> PREFIX + "depth.32";
            case DEPTH_128 -> PREFIX + "depth.128";
            case DEPTH_512 -> PREFIX + "depth.512";
            case THROUGH -> PREFIX + "depth.through";
        });
    }

    private static Label label(
                               String id,
                               Component value,
                               int left,
                               int top,
                               int width,
                               int height,
                               int color,
                               int fontSize,
                               TextWrap wrap) {
        return OrbitalControlUiTheme.label(id, value, left, top, width, height, color, fontSize, wrap);
    }

    private static Button button(
                                 String id,
                                 Component value,
                                 int left,
                                 int top,
                                 int width,
                                 int height,
                                 Tone tone) {
        return OrbitalControlUiTheme.button(id, value, left, top, width, height, tone);
    }

    public record MapProviderOption(ResourceLocation id, Component label) {}

    public static final class ModeRow {

        public final OrbitalAttackMode mode;
        public final UIElement root;
        public final Label status;
        public final Button action;

        private ModeRow(OrbitalAttackMode mode, UIElement root, Label status, Button action) {
            this.mode = mode;
            this.root = root;
            this.status = status;
            this.action = action;
        }
    }
}
