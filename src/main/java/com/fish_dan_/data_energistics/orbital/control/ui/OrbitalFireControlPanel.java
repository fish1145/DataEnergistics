package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalTacticalMapClientState;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapter;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapters;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapRequestPayload;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlFeedback;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlDraft;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** One target draft, one map viewport and one confirmation path shared by both orbital control sources. */
final class OrbitalFireControlPanel {

    static final int WIDTH = 412;
    static final int HEIGHT = 328;

    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 22;
    private static final int TARGET_CARD_HEIGHT = 136;
    private static final int LOWER_TOP = 144;
    private static final int MAP_CARD_WIDTH = 154;
    private static final int PREVIEW_CARD_LEFT = 162;
    private static final int PREVIEW_CARD_WIDTH = WIDTH - PREVIEW_CARD_LEFT;
    private static final int MAP_GRID_LEFT = 7;
    private static final int MAP_GRID_TOP = 30;
    private static final int MAP_CELL_SIZE = 20;
    private static final int MAP_RADIUS = 3;
    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.fire_control.";

    private OrbitalFireControlPanel() {}

    static View create(
                       UIElement eventRoot,
                       Player player,
                       OrbitalControlUiSource source,
                       boolean clientSide,
                       RPCEmitter commandEmitter) {
        ClientPanelState state = new ClientPanelState();
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;

        UIElement root = new UIElement();
        root.setId("orbital_fire_control_panel");
        root.layout(layout -> layout.width(WIDTH).height(HEIGHT));

        UIElement targetCard = OrbitalControlUiTheme.panel(
                "orbital_fire_control_target_card",
                0,
                0,
                WIDTH,
                TARGET_CARD_HEIGHT,
                Tone.PANEL);
        targetCard.style(style -> style.tooltips(Component.translatable(PREFIX + "hint")));

        TextField dimension = textField(
                "orbital_fire_control_dimension",
                player.level().dimension().location().toString(),
                52,
                8,
                164);
        dimension.setResourceLocationOnly();
        Label dimensionLabel = fieldLabel(
                "orbital_fire_control_dimension_label",
                PREFIX + "dimension",
                8,
                8,
                40);

        Selector<OrbitalAttackMode> mode = selector(
                "orbital_fire_control_mode",
                264,
                8,
                140,
                List.of(OrbitalAttackMode.values()),
                OrbitalAttackMode.KINETIC,
                OrbitalFireControlPanel::modeName);
        Label modeLabel = fieldLabel("orbital_fire_control_mode_label", PREFIX + "mode", 224, 8, 36);

        TextField targetX = integerField(
                "orbital_fire_control_x",
                player.blockPosition().getX(),
                -30_000_000,
                30_000_000,
                20,
                32,
                66);
        Label xLabel = fieldLabel("orbital_fire_control_x_label", PREFIX + "x", 8, 32, 10);

        TextField targetZ = integerField(
                "orbital_fire_control_z",
                player.blockPosition().getZ(),
                -30_000_000,
                30_000_000,
                106,
                32,
                66);
        Label zLabel = fieldLabel("orbital_fire_control_z_label", PREFIX + "z", 94, 32, 10);

        Selector<OrbitalTargetYMode> targetYMode = selector(
                "orbital_fire_control_y_mode",
                220,
                32,
                100,
                List.of(OrbitalTargetYMode.values()),
                OrbitalTargetYMode.SURFACE_OFFSET,
                OrbitalFireControlPanel::targetYModeName);
        targetYMode.setOnValueChanged(state::selectTargetYMode);
        Label targetYModeLabel = fieldLabel(
                "orbital_fire_control_y_mode_label",
                PREFIX + "y_mode",
                180,
                32,
                36);

        TextField targetYValue = integerField(
                "orbital_fire_control_y",
                0,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                340,
                32,
                64);
        Label yLabel = fieldLabel("orbital_fire_control_y_label", PREFIX + "y", 328, 32, 10);

        TextField radius = integerField(
                "orbital_fire_control_radius",
                settings.directedEnergyMinimumRadius,
                settings.directedEnergyMinimumRadius,
                settings.directedEnergyMaximumRadius,
                52,
                56,
                64);
        radius.setActive(false);
        Label radiusLabel = fieldLabel(
                "orbital_fire_control_radius_label",
                PREFIX + "radius",
                8,
                56,
                40);

        Selector<OrbitalDirectedEnergyDepth> depth = selector(
                "orbital_fire_control_depth",
                168,
                56,
                130,
                List.of(OrbitalDirectedEnergyDepth.values()),
                OrbitalDirectedEnergyDepth.DEPTH_32,
                OrbitalFireControlPanel::depthName);
        depth.setActive(false);
        depth.setOnValueChanged(state::selectDepth);
        Label depthLabel = fieldLabel(
                "orbital_fire_control_depth_label",
                PREFIX + "depth",
                124,
                56,
                40);

        mode.setOnValueChanged(selected -> {
            state.selectMode(selected);
            boolean directed = selected == OrbitalAttackMode.DIRECTED_ENERGY;
            radius.setActive(directed && state.operable());
            depth.setActive(directed && state.operable());
        });

        List<TacticalMapAdapter> mapAdapters = TacticalMapAdapters.available();
        Selector<TacticalMapAdapter> mapProvider = selector(
                "orbital_fire_control_map_provider",
                8,
                82,
                190,
                mapAdapters,
                mapAdapters.getFirst(),
                OrbitalFireControlPanel::mapProviderName);
        Button selectOnMap = OrbitalControlUiTheme.button(
                "orbital_fire_control_select_on_map",
                Component.translatable(PREFIX + "map.select"),
                206,
                82,
                198,
                BUTTON_HEIGHT,
                Tone.PANEL);
        Button refreshPreview = OrbitalControlUiTheme.button(
                "orbital_fire_control_preview",
                Component.translatable(PREFIX + "preview"),
                8,
                106,
                190,
                BUTTON_HEIGHT,
                Tone.ACCENT);
        Button confirm = OrbitalControlUiTheme.button(
                "orbital_fire_control_confirm",
                Component.translatable(PREFIX + "confirm"),
                206,
                106,
                198,
                BUTTON_HEIGHT,
                Tone.DANGER);

        selectOnMap.setOnClick(event -> {
            if (!clientSide || !state.operable()) {
                return;
            }
            TacticalMapAdapter adapter = mapProvider.getValue();
            if (adapter == null) {
                return;
            }
            try {
                OrbitalFireControlDraft draft = readDraft(
                        state,
                        dimension,
                        targetX,
                        targetZ,
                        targetYValue,
                        radius);
                cancelClientHold(state, commandEmitter);
                UUID sessionToken = OrbitalMapSelectionClientSession.begin(
                        adapter.id(),
                        state.selectedWeaponId(),
                        draft,
                        source);
                TacticalMapAdapter.SelectionStart selectionStart = TacticalMapAdapters.start(
                        adapter,
                        Minecraft.getInstance(),
                        sessionToken);
                if (selectionStart == TacticalMapAdapter.SelectionStart.EMBEDDED) {
                    OrbitalMapSelectionClientSession.cancel();
                    requestMap(state, dimension, targetX, targetZ);
                } else if (selectionStart == TacticalMapAdapter.SelectionStart.FAILED) {
                    OrbitalMapSelectionClientSession.cancel();
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.translatable(PREFIX + "map.failed"),
                                true);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                OrbitalMapSelectionClientSession.cancel();
                cancelClientHold(state, commandEmitter);
            }
        });

        refreshPreview.setOnClick(event -> {
            if (!clientSide || !state.operable()) {
                return;
            }
            try {
                requestPreview(
                        state,
                        readDraft(state, dimension, targetX, targetZ, targetYValue, radius),
                        commandEmitter);
            } catch (IllegalArgumentException ignored) {
                cancelClientHold(state, commandEmitter);
            }
        });

        confirm.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!clientSide || event.button != 0 || state.holding() || !state.operable()) {
                return;
            }
            try {
                UUID nonce = state.beginHold(readDraft(
                        state,
                        dimension,
                        targetX,
                        targetZ,
                        targetYValue,
                        radius));
                if (!commandEmitter.send(new OrbitalControlIntent.StartHold(nonce))) {
                    state.clearHold();
                }
            } catch (IllegalArgumentException ignored) {
                state.clearHold();
            }
        });
        confirm.addEventListener(UIEvents.MOUSE_LEAVE, event -> {
            if (clientSide) {
                cancelClientHold(state, commandEmitter);
            }
        });
        eventRoot.addEventListener(UIEvents.MOUSE_UP, event -> releaseClientHold(
                event.button,
                clientSide,
                state,
                commandEmitter));

        targetCard.addChildren(
                dimensionLabel,
                dimension,
                modeLabel,
                mode,
                xLabel,
                targetX,
                zLabel,
                targetZ,
                targetYModeLabel,
                targetYMode,
                yLabel,
                targetYValue,
                radiusLabel,
                radius,
                depthLabel,
                depth,
                mapProvider,
                selectOnMap,
                refreshPreview,
                confirm);

        UIElement mapCard = OrbitalControlUiTheme.panel(
                "orbital_fire_control_map_card",
                0,
                LOWER_TOP,
                MAP_CARD_WIDTH,
                HEIGHT - LOWER_TOP,
                Tone.PANEL_ALT);
        Label mapTitle = OrbitalControlUiTheme.label(
                "orbital_fire_control_map_title",
                Component.translatable(PREFIX + "map.title_compact"),
                8,
                8,
                MAP_CARD_WIDTH - 16,
                14,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL);
        ObjectArrayList<Button> mapCells = createMapCells(
                state,
                clientSide,
                dimension,
                targetX,
                targetZ,
                targetYMode,
                targetYValue);
        mapCard.addChild(mapTitle);
        for (Button mapCell : mapCells) {
            mapCard.addChild(mapCell);
        }

        UIElement previewCard = OrbitalControlUiTheme.panel(
                "orbital_fire_control_preview_card",
                PREVIEW_CARD_LEFT,
                LOWER_TOP,
                PREVIEW_CARD_WIDTH,
                HEIGHT - LOWER_TOP,
                Tone.PANEL);
        Button mapRefresh = OrbitalControlUiTheme.button(
                "orbital_fire_control_map_refresh",
                Component.translatable(PREFIX + "map.refresh"),
                8,
                8,
                PREVIEW_CARD_WIDTH - 16,
                BUTTON_HEIGHT,
                Tone.ACCENT);
        mapRefresh.setOnClick(event -> {
            if (clientSide && state.operable()) {
                requestMap(state, dimension, targetX, targetZ);
            }
        });
        Label mapStatus = OrbitalControlUiTheme.label(
                "orbital_fire_control_map_status",
                Component.translatable(PREFIX + "map.status"),
                8,
                36,
                PREVIEW_CARD_WIDTH - 16,
                40,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.WRAP);
        Label previewTitle = OrbitalControlUiTheme.label(
                "orbital_fire_control_preview_title",
                Component.translatable(PREFIX + "preview.title"),
                8,
                82,
                PREVIEW_CARD_WIDTH - 16,
                14,
                OrbitalControlUiTheme.ACCENT_TEXT,
                9,
                TextWrap.HOVER_ROLL);
        Label preview = OrbitalControlUiTheme.label(
                "orbital_fire_control_preview_status",
                Component.translatable("screen.data_energistics.orbital_control_terminal.preview.none"),
                8,
                100,
                PREVIEW_CARD_WIDTH - 16,
                76,
                OrbitalControlUiTheme.TEXT,
                8,
                TextWrap.WRAP);
        previewCard.addChildren(mapRefresh, mapStatus, previewTitle, preview);

        root.addChildren(targetCard, mapCard, previewCard);
        ObjectArrayList<UIElement> interactive = new ObjectArrayList<>(13 + mapCells.size());
        interactive.addAll(List.of(
                dimension,
                mode,
                targetX,
                targetZ,
                targetYMode,
                targetYValue,
                radius,
                depth,
                mapProvider,
                selectOnMap,
                refreshPreview,
                confirm,
                mapRefresh));
        interactive.addAll(mapCells);
        root.addEventListener(UIEvents.TICK, event -> {
            if (!clientSide) {
                return;
            }
            OrbitalFireControlDraft mapDraft = state.operable() ?
                    OrbitalMapSelectionClientSession.takePending(state.selectedWeaponId()) : null;
            if (mapDraft != null) {
                applyDraft(
                        state,
                        mapDraft,
                        dimension,
                        mode,
                        targetX,
                        targetZ,
                        targetYMode,
                        targetYValue,
                        radius,
                        depth);
                requestPreview(state, mapDraft, commandEmitter);
            }
            if (state.previewDraftChanged(() -> readDraft(
                    state,
                    dimension,
                    targetX,
                    targetZ,
                    targetYValue,
                    radius))) {
                cancelClientHold(state, commandEmitter);
            }
            updateMapCells(state, mapCells, mapStatus);
        });
        return new View(
                root,
                preview,
                radius,
                depth,
                interactive,
                state,
                commandEmitter,
                clientSide);
    }

    private static ObjectArrayList<Button> createMapCells(
                                                          ClientPanelState state,
                                                          boolean clientSide,
                                                          TextField dimension,
                                                          TextField targetX,
                                                          TextField targetZ,
                                                          Selector<OrbitalTargetYMode> targetYMode,
                                                          TextField targetYValue) {
        ObjectArrayList<Button> cells = new ObjectArrayList<>((MAP_RADIUS * 2 + 1) * (MAP_RADIUS * 2 + 1));
        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                int cellOffsetX = offsetX;
                int cellOffsetZ = offsetZ;
                Button cell = OrbitalControlUiTheme.button(
                        "orbital_fire_control_map_cell_" + (cellOffsetX + MAP_RADIUS) + "_" + (cellOffsetZ + MAP_RADIUS),
                        Component.literal("?"),
                        MAP_GRID_LEFT + (cellOffsetX + MAP_RADIUS) * MAP_CELL_SIZE,
                        MAP_GRID_TOP + (cellOffsetZ + MAP_RADIUS) * MAP_CELL_SIZE,
                        MAP_CELL_SIZE - 1,
                        MAP_CELL_SIZE - 1,
                        Tone.PANEL);
                cell.setOnClick(event -> {
                    if (clientSide && state.operable()) {
                        selectMapCell(
                                state,
                                dimension,
                                targetX,
                                targetZ,
                                targetYMode,
                                targetYValue,
                                cellOffsetX,
                                cellOffsetZ);
                    }
                });
                cells.add(cell);
            }
        }
        return cells;
    }

    private static OrbitalFireControlDraft readDraft(
                                                     ClientPanelState state,
                                                     TextField dimension,
                                                     TextField targetX,
                                                     TextField targetZ,
                                                     TextField targetY,
                                                     TextField radius) {
        if (dimension.isError() || targetX.isError() || targetZ.isError() || targetY.isError() ||
                (state.mode() == OrbitalAttackMode.DIRECTED_ENERGY && radius.isError())) {
            throw new IllegalArgumentException("Orbital target form contains an invalid value");
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension.getRawText());
        if (dimensionId == null) {
            throw new IllegalArgumentException("Orbital target form contains an invalid dimension");
        }
        int directedRadius = 0;
        OrbitalDirectedEnergyDepth directedDepth = null;
        if (state.mode() == OrbitalAttackMode.DIRECTED_ENERGY) {
            directedRadius = Integer.parseInt(radius.getRawText());
            OrbitalDirectedEnergyStrike.validateRadius(
                    directedRadius,
                    DataEnergisticsConfiguration.INSTANCE.orbitalWeapon);
            directedDepth = state.depth();
        }
        return new OrbitalFireControlDraft(
                state.mode(),
                dimensionId,
                Integer.parseInt(targetX.getRawText()),
                Integer.parseInt(targetZ.getRawText()),
                state.targetYMode(),
                Integer.parseInt(targetY.getRawText()),
                directedRadius,
                directedDepth);
    }

    private static void requestPreview(
                                       ClientPanelState state,
                                       OrbitalFireControlDraft draft,
                                       RPCEmitter commandEmitter) {
        cancelClientHold(state, commandEmitter);
        state.previewRequested(draft);
        commandEmitter.send(new OrbitalControlIntent.RequestPreview(draft));
    }

    private static void applyDraft(
                                   ClientPanelState state,
                                   OrbitalFireControlDraft draft,
                                   TextField dimension,
                                   Selector<OrbitalAttackMode> mode,
                                   TextField targetX,
                                   TextField targetZ,
                                   Selector<OrbitalTargetYMode> targetYMode,
                                   TextField targetY,
                                   TextField radius,
                                   Selector<OrbitalDirectedEnergyDepth> depth) {
        state.selectMode(draft.mode());
        mode.setSelected(draft.mode(), false);
        dimension.setText(draft.dimensionId().toString(), false);
        targetX.setText(Integer.toString(draft.targetX()), false);
        targetZ.setText(Integer.toString(draft.targetZ()), false);
        state.selectTargetYMode(draft.targetYMode());
        targetYMode.setSelected(draft.targetYMode(), false);
        targetY.setText(Integer.toString(draft.targetYValue()), false);

        boolean directed = draft.mode() == OrbitalAttackMode.DIRECTED_ENERGY;
        if (directed) {
            radius.setText(Integer.toString(draft.directedRadius()), false);
            OrbitalDirectedEnergyDepth selectedDepth = Objects.requireNonNull(draft.directedDepth());
            state.selectDepth(selectedDepth);
            depth.setSelected(selectedDepth, false);
        }
        radius.setActive(directed && state.operable());
        depth.setActive(directed && state.operable());
    }

    private static void releaseClientHold(
                                          int button,
                                          boolean clientSide,
                                          ClientPanelState state,
                                          RPCEmitter commandEmitter) {
        if (!clientSide || button != 0) {
            return;
        }
        UUID nonce = state.takeHold();
        if (nonce != null) {
            commandEmitter.send(new OrbitalControlIntent.ReleaseHold(nonce));
        }
    }

    private static void cancelClientHold(ClientPanelState state, RPCEmitter cancelEmitter) {
        if (!state.holding()) {
            return;
        }
        state.clearHold();
        cancelEmitter.send(OrbitalControlIntent.CancelHold.INSTANCE);
    }

    private static void requestMap(
                                   ClientPanelState state,
                                   TextField dimension,
                                   TextField targetX,
                                   TextField targetZ) {
        UUID weaponId = state.selectedWeaponId();
        if (weaponId == null || dimension.isError() || targetX.isError() || targetZ.isError()) {
            return;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension.getRawText());
        if (dimensionId == null) {
            return;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(targetX.getRawText());
            z = Integer.parseInt(targetZ.getRawText());
        } catch (NumberFormatException exception) {
            return;
        }
        if (Math.abs((long) x) > 30_000_000L || Math.abs((long) z) > 30_000_000L) {
            return;
        }
        ChunkPos center = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        UUID sessionToken = OrbitalTacticalMapClientState.sessionTokenFor(weaponId, dimensionId);
        long requestNonce = OrbitalTacticalMapClientState.nextRequestNonce();
        OrbitalTacticalMapClientState.expectResponse(
                weaponId,
                dimensionId,
                requestNonce);
        PacketDistributor.sendToServer(new OrbitalTacticalMapRequestPayload(
                weaponId,
                sessionToken,
                dimensionId,
                center.x,
                center.z,
                MAP_RADIUS,
                requestNonce));
    }

    private static void selectMapCell(
                                      ClientPanelState state,
                                      TextField dimension,
                                      TextField targetX,
                                      TextField targetZ,
                                      Selector<OrbitalTargetYMode> targetYMode,
                                      TextField targetYValue,
                                      int offsetX,
                                      int offsetZ) {
        int chunkX = OrbitalTacticalMapClientState.centerChunkX() + offsetX;
        int chunkZ = OrbitalTacticalMapClientState.centerChunkZ() + offsetZ;
        OrbitalMapTile tile = OrbitalTacticalMapClientState.tileAt(chunkX, chunkZ);
        if (tile == null) {
            return;
        }
        dimension.setText(OrbitalTacticalMapClientState.dimensionId().toString(), false);
        targetX.setText(Integer.toString(chunkX * 16 + 8), false);
        targetZ.setText(Integer.toString(chunkZ * 16 + 8), false);
        if (tile.known()) {
            state.selectTargetYMode(OrbitalTargetYMode.SURFACE_OFFSET);
            targetYMode.setSelected(OrbitalTargetYMode.SURFACE_OFFSET, false);
            targetYValue.setText("0", false);
        }
    }

    private static void updateMapCells(
                                       ClientPanelState state,
                                       ObjectArrayList<Button> cells,
                                       Label status) {
        long revision = OrbitalTacticalMapClientState.revision();
        if (!state.consumeMapRevision(revision)) {
            return;
        }
        int centerX = OrbitalTacticalMapClientState.centerChunkX();
        int centerZ = OrbitalTacticalMapClientState.centerChunkZ();
        int index = 0;
        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                cells.get(index++).setText(OrbitalTacticalMapClientState.cellComponent(
                        OrbitalTacticalMapClientState.tileAt(centerX + offsetX, centerZ + offsetZ)));
            }
        }
        status.setValue(revision < 0L ? Component.translatable(PREFIX + "map.status") : Component.translatable(
                PREFIX + "map.viewport",
                Component.literal(OrbitalTacticalMapClientState.dimensionId().toString()),
                centerX,
                centerZ));
    }

    private static TextField textField(String id, String initialValue, int left, int top, int width) {
        TextField field = new TextField();
        field.setId(id);
        field.setText(initialValue, false);
        OrbitalControlUiTheme.place(field, left, top, width, FIELD_HEIGHT);
        return field;
    }

    private static TextField integerField(
                                          String id,
                                          int initialValue,
                                          int minimum,
                                          int maximum,
                                          int left,
                                          int top,
                                          int width) {
        TextField field = textField(id, Integer.toString(initialValue), left, top, width);
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
        selector.selectorStyle(style -> style.closeAfterSelect(true).maxItemCount(candidates.size()));
        OrbitalControlUiTheme.place(selector, left, top, width, FIELD_HEIGHT);
        return selector;
    }

    private static Label fieldLabel(String id, String translationKey, int left, int top, int width) {
        return OrbitalControlUiTheme.label(
                id,
                Component.translatable(translationKey),
                left,
                top,
                width,
                FIELD_HEIGHT,
                OrbitalControlUiTheme.MUTED_TEXT,
                8,
                TextWrap.HOVER_ROLL);
    }

    private static Component targetYModeName(@Nullable OrbitalTargetYMode mode) {
        if (mode == null) {
            return Component.empty();
        }
        return Component.translatable(switch (mode) {
            case ABSOLUTE -> PREFIX + "y_mode.absolute";
            case SURFACE_OFFSET -> PREFIX + "y_mode.surface_offset";
        });
    }

    private static Component mapProviderName(@Nullable TacticalMapAdapter adapter) {
        return adapter == null ? Component.empty() : adapter.displayName();
    }

    private static Component modeName(@Nullable OrbitalAttackMode mode) {
        return mode == null ? Component.empty() : OrbitalControlPresentation.modeName(mode);
    }

    private static Component depthName(@Nullable OrbitalDirectedEnergyDepth depth) {
        if (depth == null) {
            return Component.empty();
        }
        return Component.translatable(switch (depth) {
            case DEPTH_32 -> "screen.data_energistics.orbital_control_terminal.depth.32";
            case DEPTH_128 -> "screen.data_energistics.orbital_control_terminal.depth.128";
            case DEPTH_512 -> "screen.data_energistics.orbital_control_terminal.depth.512";
            case THROUGH -> "screen.data_energistics.orbital_control_terminal.depth.through";
        });
    }

    static final class View {

        private final UIElement root;
        private final Label preview;
        private final TextField radius;
        private final Selector<OrbitalDirectedEnergyDepth> depth;
        private final List<UIElement> interactive;
        private final ClientPanelState state;
        private final RPCEmitter commandEmitter;
        private final boolean clientSide;

        private View(
                     UIElement root,
                     Label preview,
                     TextField radius,
                     Selector<OrbitalDirectedEnergyDepth> depth,
                     List<UIElement> interactive,
                     ClientPanelState state,
                     RPCEmitter commandEmitter,
                     boolean clientSide) {
            this.root = root;
            this.preview = preview;
            this.radius = radius;
            this.depth = depth;
            this.interactive = List.copyOf(interactive);
            this.state = state;
            this.commandEmitter = commandEmitter;
            this.clientSide = clientSide;
        }

        UIElement root() {
            return this.root;
        }

        void updateSnapshot(OrbitalControlTerminalSnapshot snapshot) {
            UUID selectedWeaponId = snapshot.selectedWeaponId();
            boolean changed = this.state.selectWeapon(selectedWeaponId);
            boolean operable = snapshot.selectedWeapon().map(OrbitalControlTerminalSnapshot.WeaponEntry::canOperate).orElse(false);
            this.state.setOperable(operable);
            for (UIElement element : this.interactive) {
                element.setActive(operable);
            }
            boolean directed = this.state.mode() == OrbitalAttackMode.DIRECTED_ENERGY;
            this.radius.setActive(operable && directed);
            this.depth.setActive(operable && directed);
            if ((changed || !operable) && this.clientSide) {
                cancelHold();
            }
            if (changed && this.clientSide) {
                OrbitalTacticalMapClientState.clear();
            }
        }

        void updateSession(
                           OrbitalFireControlSessionSnapshot snapshot,
                           OrbitalControlFeedback feedback) {
            if (!this.clientSide) {
                return;
            }
            this.preview.setValue(OrbitalControlPresentation.fireControl(snapshot, feedback));
            this.state.setPreviewNonce(snapshot.preview() == null ? null : snapshot.preview().nonce());
        }

        void cancelHold() {
            if (this.clientSide) {
                cancelClientHold(this.state, this.commandEmitter);
            }
        }
    }

    private static final class ClientPanelState {

        private OrbitalAttackMode mode = OrbitalAttackMode.KINETIC;
        private OrbitalTargetYMode targetYMode = OrbitalTargetYMode.SURFACE_OFFSET;
        private OrbitalDirectedEnergyDepth depth = OrbitalDirectedEnergyDepth.DEPTH_32;
        private @Nullable UUID selectedWeaponId;
        private @Nullable OrbitalFireControlDraft previewedDraft;
        private @Nullable UUID previewNonce;
        private @Nullable UUID hold;
        private long renderedMapRevision = Long.MIN_VALUE;
        private boolean operable;

        private OrbitalAttackMode mode() {
            return this.mode;
        }

        private OrbitalTargetYMode targetYMode() {
            return this.targetYMode;
        }

        private OrbitalDirectedEnergyDepth depth() {
            return this.depth;
        }

        private @Nullable UUID selectedWeaponId() {
            return this.selectedWeaponId;
        }

        private boolean selectWeapon(@Nullable UUID selectedWeaponId) {
            if (Objects.equals(this.selectedWeaponId, selectedWeaponId)) {
                return false;
            }
            this.selectedWeaponId = selectedWeaponId;
            this.previewedDraft = null;
            this.previewNonce = null;
            this.renderedMapRevision = Long.MIN_VALUE;
            return true;
        }

        private boolean operable() {
            return this.operable;
        }

        private void setOperable(boolean operable) {
            this.operable = operable;
        }

        private void selectMode(OrbitalAttackMode mode) {
            this.mode = mode;
        }

        private void selectTargetYMode(OrbitalTargetYMode targetYMode) {
            this.targetYMode = targetYMode;
        }

        private void selectDepth(OrbitalDirectedEnergyDepth depth) {
            this.depth = depth;
        }

        private void previewRequested(OrbitalFireControlDraft draft) {
            this.previewedDraft = draft;
            this.previewNonce = null;
            this.hold = null;
        }

        private void setPreviewNonce(@Nullable UUID value) {
            this.previewNonce = value;
            if (this.previewNonce == null) {
                this.hold = null;
            }
        }

        private UUID beginHold(OrbitalFireControlDraft draft) {
            if (this.previewNonce == null || !draft.equals(this.previewedDraft)) {
                throw new IllegalArgumentException("Orbital confirmation has no matching target preview");
            }
            this.hold = this.previewNonce;
            return this.hold;
        }

        private boolean holding() {
            return this.hold != null;
        }

        private @Nullable UUID takeHold() {
            UUID current = this.hold;
            this.hold = null;
            return current;
        }

        private void clearHold() {
            this.hold = null;
        }

        private boolean previewDraftChanged(DraftSupplier currentDraft) {
            if (this.previewedDraft == null) {
                return false;
            }
            try {
                if (this.previewedDraft.equals(currentDraft.get())) {
                    return false;
                }
            } catch (IllegalArgumentException ignored) {
                // An invalid edit invalidates the previously captured preview just like a valid different draft.
            }
            this.previewedDraft = null;
            this.previewNonce = null;
            return true;
        }

        private boolean consumeMapRevision(long revision) {
            if (this.renderedMapRevision == revision) {
                return false;
            }
            this.renderedMapRevision = revision;
            return true;
        }
    }

    @FunctionalInterface
    private interface DraftSupplier {

        OrbitalFireControlDraft get();
    }
}
