package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudClientState;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalTacticalMapClientState;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapRequestPayload;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared LDLib2 coordinate fire-control panel for both the held terminal and the bound console.
 *
 * <p>
 * The panel sends only a bounded target intent through its current menu RPC route. The server-side executor keeps
 * the exact source-validity predicate captured by that menu, resolves every enum from an explicit wire code, and then
 * delegates preview and confirmation to the authoritative orbital SavedData path.
 * </p>
 */
final class OrbitalFireControlPanel {

    static final int WIDTH = 404;
    static final int HEIGHT = 310;

    private static final int NO_MODE = -1;
    private static final int NO_DEPTH = -1;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 22;
    private static final int MAP_TOP = 146;
    private static final int MAP_GRID_TOP = 164;
    private static final int MAP_CELL_SIZE = 20;
    private static final int MAP_RADIUS = 3;
    private static final String TRANSLATION_PREFIX = "screen.data_energistics.orbital_control_terminal.fire_control.";

    private OrbitalFireControlPanel() {}

    static UIElement create(
                            UIElement rpcRoot,
                            Player player,
                            BooleanSupplier sourceValid,
                            boolean clientSide) {
        ClientPanelState state = new ClientPanelState();
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        RPCEmitter previewEmitter = previewEmitter(rpcRoot, player, sourceValid);
        RPCEmitter startHoldEmitter = startHoldEmitter(rpcRoot, player, sourceValid);
        RPCEmitter releaseEmitter = releaseEmitter(rpcRoot, player, sourceValid);
        RPCEmitter cancelHoldEmitter = cancelHoldEmitter(rpcRoot, player);

        UIElement panel = new UIElement();
        panel.setId("orbital_fire_control_panel");
        panel.layout(layout -> layout.width(WIDTH).height(HEIGHT));

        Label title = label(
                "orbital_fire_control_title",
                TRANSLATION_PREFIX + "title",
                0,
                0,
                WIDTH,
                16);

        TextField dimension = textField(
                "orbital_fire_control_dimension",
                player.level().dimension().location().toString(),
                54,
                18,
                174);
        dimension.setResourceLocationOnly();
        Label dimensionLabel = label(
                "orbital_fire_control_dimension_label",
                TRANSLATION_PREFIX + "dimension",
                0,
                18,
                50,
                FIELD_HEIGHT);

        Selector<OrbitalAttackMode> mode = selector(
                "orbital_fire_control_mode",
                280,
                18,
                124,
                List.of(OrbitalAttackMode.values()),
                OrbitalAttackMode.KINETIC,
                OrbitalFireControlPanel::modeName);
        Label modeLabel = label(
                "orbital_fire_control_mode_label",
                TRANSLATION_PREFIX + "mode",
                234,
                18,
                42,
                FIELD_HEIGHT);

        TextField targetX = integerField(
                "orbital_fire_control_x",
                player.blockPosition().getX(),
                -30_000_000,
                30_000_000,
                18,
                40,
                70);
        Label xLabel = label("orbital_fire_control_x_label", TRANSLATION_PREFIX + "x", 0, 40, 14, FIELD_HEIGHT);

        TextField targetZ = integerField(
                "orbital_fire_control_z",
                player.blockPosition().getZ(),
                -30_000_000,
                30_000_000,
                110,
                40,
                70);
        Label zLabel = label("orbital_fire_control_z_label", TRANSLATION_PREFIX + "z", 92, 40, 14, FIELD_HEIGHT);

        Selector<OrbitalTargetYMode> targetYMode = selector(
                "orbital_fire_control_y_mode",
                232,
                40,
                98,
                List.of(OrbitalTargetYMode.values()),
                OrbitalTargetYMode.SURFACE_OFFSET,
                OrbitalFireControlPanel::targetYModeName);
        targetYMode.setOnValueChanged(state::selectTargetYMode);
        Label targetYModeLabel = label(
                "orbital_fire_control_y_mode_label",
                TRANSLATION_PREFIX + "y_mode",
                184,
                40,
                44,
                FIELD_HEIGHT);

        TextField targetYValue = integerField(
                "orbital_fire_control_y",
                0,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                348,
                40,
                56);
        Label yLabel = label("orbital_fire_control_y_label", TRANSLATION_PREFIX + "y", 334, 40, 10, FIELD_HEIGHT);

        TextField radius = integerField(
                "orbital_fire_control_radius",
                settings.directedEnergyMinimumRadius,
                settings.directedEnergyMinimumRadius,
                settings.directedEnergyMaximumRadius,
                54,
                62,
                70);
        radius.setActive(false);
        Label radiusLabel = label(
                "orbital_fire_control_radius_label",
                TRANSLATION_PREFIX + "radius",
                0,
                62,
                50,
                FIELD_HEIGHT);

        Selector<OrbitalDirectedEnergyDepth> depth = selector(
                "orbital_fire_control_depth",
                184,
                62,
                146,
                List.of(OrbitalDirectedEnergyDepth.values()),
                OrbitalDirectedEnergyDepth.DEPTH_32,
                OrbitalFireControlPanel::depthName);
        depth.setActive(false);
        depth.setOnValueChanged(state::selectDepth);
        Label depthLabel = label(
                "orbital_fire_control_depth_label",
                TRANSLATION_PREFIX + "depth",
                132,
                62,
                48,
                FIELD_HEIGHT);

        mode.setOnValueChanged(selected -> {
            state.selectMode(selected);
            boolean directed = selected == OrbitalAttackMode.DIRECTED_ENERGY;
            radius.setActive(directed);
            depth.setActive(directed);
        });

        Button refreshPreview = button(
                "orbital_fire_control_preview",
                TRANSLATION_PREFIX + "preview",
                0);
        refreshPreview.setOnClick(event -> {
            if (!clientSide) {
                return;
            }
            try {
                TargetDraft draft = readDraft(
                        state,
                        dimension,
                        targetX,
                        targetZ,
                        targetYValue,
                        radius);
                previewEmitter.send(
                        draft.mode().wireCode(),
                        draft.dimension(),
                        draft.targetX(),
                        draft.targetZ(),
                        draft.targetYMode().wireCode(),
                        draft.targetYValue(),
                        draft.directedRadius(),
                        draft.depthCode());
            } catch (IllegalArgumentException ignored) {
                cancelClientHold(state, cancelHoldEmitter);
            }
        });

        Button confirm = button(
                "orbital_fire_control_confirm",
                TRANSLATION_PREFIX + "confirm",
                206);
        confirm.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!clientSide || event.button != 0 || state.holding()) {
                return;
            }
            int modeCode = state.beginHold();
            if (!startHoldEmitter.send(modeCode)) {
                state.clearHold();
            }
        });
        confirm.addEventListener(UIEvents.MOUSE_LEAVE, event -> {
            if (clientSide) {
                cancelClientHold(state, cancelHoldEmitter);
            }
        });
        rpcRoot.addEventListener(UIEvents.MOUSE_UP, event -> releaseClientHold(
                event.button,
                clientSide,
                state,
                releaseEmitter));

        Label hint = label(
                "orbital_fire_control_hint",
                TRANSLATION_PREFIX + "hint",
                0,
                114,
                WIDTH,
                32);
        hint.textStyle(style -> style.textAlignVertical(Vertical.TOP).textWrap(TextWrap.WRAP));

        Label mapTitle = label(
                "orbital_fire_control_map_title",
                TRANSLATION_PREFIX + "map.title",
                0,
                MAP_TOP,
                154,
                FIELD_HEIGHT);
        Label mapStatus = label(
                "orbital_fire_control_map_status",
                TRANSLATION_PREFIX + "map.status",
                168,
                MAP_GRID_TOP,
                WIDTH - 168,
                118);
        mapStatus.textStyle(style -> style.textAlignVertical(Vertical.TOP).textWrap(TextWrap.WRAP));

        Button mapRefresh = new Button();
        mapRefresh.setId("orbital_fire_control_map_refresh");
        mapRefresh.setText(Component.translatable(TRANSLATION_PREFIX + "map.refresh"));
        mapRefresh.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(168)
                .top(MAP_TOP)
                .width(WIDTH - 168)
                .height(BUTTON_HEIGHT));
        mapRefresh.setOnClick(event -> {
            if (clientSide) {
                requestMap(dimension, targetX, targetZ);
            }
        });

        ObjectArrayList<Button> mapCells = new ObjectArrayList<>((MAP_RADIUS * 2 + 1) * (MAP_RADIUS * 2 + 1));
        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                int cellOffsetX = offsetX;
                int cellOffsetZ = offsetZ;
                Button cell = new Button();
                cell.setId("orbital_fire_control_map_cell_" + (cellOffsetX + MAP_RADIUS) + "_" + (cellOffsetZ + MAP_RADIUS));
                cell.setText(Component.literal("?"));
                cell.layout(layout -> layout
                        .positionType(TaffyPosition.ABSOLUTE)
                        .left((cellOffsetX + MAP_RADIUS) * MAP_CELL_SIZE)
                        .top(MAP_GRID_TOP + (cellOffsetZ + MAP_RADIUS) * MAP_CELL_SIZE)
                        .width(MAP_CELL_SIZE - 1)
                        .height(MAP_CELL_SIZE - 1));
                cell.setOnClick(event -> {
                    if (clientSide) {
                        selectMapCell(state, dimension, targetX, targetZ, targetYMode, targetYValue, cellOffsetX, cellOffsetZ);
                    }
                });
                mapCells.add(cell);
            }
        }
        panel.addEventListener(UIEvents.TICK, event -> {
            if (clientSide) {
                updateMapCells(mapCells, mapStatus);
            }
        });

        panel.addChildren(
                title,
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
                refreshPreview,
                confirm,
                hint);
        panel.addChildren(mapTitle, mapStatus, mapRefresh);
        for (Button mapCell : mapCells) {
            panel.addChildren(mapCell);
        }
        return panel;
    }

    private static RPCEmitter previewEmitter(
                                             UIElement root,
                                             Player player,
                                             BooleanSupplier sourceValid) {
        return root.addRPCEvent(RPCEventBuilder.create()
                .args(
                        Integer.class,
                        String.class,
                        Integer.class,
                        Integer.class,
                        Integer.class,
                        Integer.class,
                        Integer.class,
                        Integer.class)
                .executor(arguments -> {
                    runServerRpc(player, "capture target preview", serverPlayer -> {
                        OrbitalAttackMode mode = OrbitalAttackMode.fromWireCode((Integer) arguments[0]);
                        ResourceLocation dimension = ResourceLocation.tryParse((String) arguments[1]);
                        if (dimension == null) {
                            throw new IllegalArgumentException("Invalid orbital target dimension");
                        }
                        OrbitalTargetYMode targetYMode = OrbitalTargetYMode.fromWireCode((Integer) arguments[4]);
                        int depthCode = (Integer) arguments[7];
                        OrbitalControlActionDispatcher.previewFireAtTarget(
                                serverPlayer,
                                mode,
                                dimension,
                                (Integer) arguments[2],
                                (Integer) arguments[3],
                                targetYMode,
                                (Integer) arguments[5],
                                (Integer) arguments[6],
                                depthCode == NO_DEPTH ? null : OrbitalDirectedEnergyDepth.fromWireCode(depthCode),
                                sourceValid);
                    });
                    return null;
                })
                .build());
    }

    private static RPCEmitter startHoldEmitter(
                                               UIElement root,
                                               Player player,
                                               BooleanSupplier sourceValid) {
        return root.addRPCEvent(RPCEventBuilder.simple(Integer.class, modeCode -> runServerRpc(player, "start confirmation hold", serverPlayer -> {
            OrbitalAttackMode mode = OrbitalAttackMode.fromWireCode(modeCode);
            if (!OrbitalControlActionDispatcher.startFireHold(serverPlayer, mode, sourceValid)) {
                serverPlayer.displayClientMessage(
                        Component.translatable(
                                "message.data_energistics.orbital_control_terminal.preview_expired"),
                        true);
            }
        })));
    }

    private static RPCEmitter releaseEmitter(
                                             UIElement root,
                                             Player player,
                                             BooleanSupplier sourceValid) {
        return root.addRPCEvent(RPCEventBuilder.simple(Integer.class, modeCode -> runServerRpc(player, "release confirmation hold", serverPlayer -> OrbitalControlActionDispatcher.releaseFireAtTarget(
                serverPlayer,
                OrbitalAttackMode.fromWireCode(modeCode),
                sourceValid))));
    }

    private static RPCEmitter cancelHoldEmitter(UIElement root, Player player) {
        return root.addRPCEvent(RPCEventBuilder.simple(Integer.class, ignored -> runServerRpc(player, "cancel confirmation hold", OrbitalControlActionDispatcher::cancelFireHold)));
    }

    private static void runServerRpc(
                                     Player player,
                                     String operation,
                                     Consumer<ServerPlayer> action) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        try {
            action.accept(serverPlayer);
        } catch (IllegalArgumentException exception) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.invalid_intent"),
                    true);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to {} for orbital control player {}",
                    operation,
                    serverPlayer.getUUID(),
                    exception);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.action_rejected"),
                    true);
        }
    }

    private static TargetDraft readDraft(
                                         ClientPanelState state,
                                         TextField dimension,
                                         TextField targetX,
                                         TextField targetZ,
                                         TextField targetY,
                                         TextField radius) {
        if (dimension.isError() || targetX.isError() || targetZ.isError() || targetY.isError() || (state.mode() == OrbitalAttackMode.DIRECTED_ENERGY && radius.isError())) {
            throw new IllegalArgumentException("Orbital target form contains an invalid value");
        }
        int directedRadius = 0;
        int depthCode = NO_DEPTH;
        if (state.mode() == OrbitalAttackMode.DIRECTED_ENERGY) {
            directedRadius = Integer.parseInt(radius.getRawText());
            OrbitalDirectedEnergyStrike.validateRadius(
                    directedRadius,
                    DataEnergisticsConfiguration.INSTANCE.orbitalWeapon);
            depthCode = state.depth().wireCode();
        }
        return new TargetDraft(
                state.mode(),
                dimension.getRawText(),
                Integer.parseInt(targetX.getRawText()),
                Integer.parseInt(targetZ.getRawText()),
                state.targetYMode(),
                Integer.parseInt(targetY.getRawText()),
                directedRadius,
                depthCode);
    }

    private static void releaseClientHold(
                                          int button,
                                          boolean clientSide,
                                          ClientPanelState state,
                                          RPCEmitter releaseEmitter) {
        if (!clientSide || button != 0 || !state.holding()) {
            return;
        }
        releaseEmitter.send(state.takeHoldMode());
    }

    private static void cancelClientHold(ClientPanelState state, RPCEmitter cancelEmitter) {
        if (!state.holding()) {
            return;
        }
        state.clearHold();
        cancelEmitter.send(0);
    }

    private static void requestMap(TextField dimension, TextField targetX, TextField targetZ) {
        UUID weaponId = OrbitalControlHudClientState.selectedWeaponId();
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
        PacketDistributor.sendToServer(new OrbitalTacticalMapRequestPayload(
                weaponId,
                sessionToken,
                dimensionId,
                center.x,
                center.z,
                MAP_RADIUS,
                OrbitalTacticalMapClientState.nextRequestNonce()));
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
            state.selectTargetYMode(OrbitalTargetYMode.ABSOLUTE);
            targetYMode.setSelected(OrbitalTargetYMode.ABSOLUTE, false);
            targetYValue.setText(Integer.toString(tile.surfaceY()), false);
        }
    }

    private static void updateMapCells(ObjectArrayList<Button> cells, Label status) {
        int centerX = OrbitalTacticalMapClientState.centerChunkX();
        int centerZ = OrbitalTacticalMapClientState.centerChunkZ();
        int index = 0;
        for (int offsetZ = -MAP_RADIUS; offsetZ <= MAP_RADIUS; offsetZ++) {
            for (int offsetX = -MAP_RADIUS; offsetX <= MAP_RADIUS; offsetX++) {
                cells.get(index++).setText(mapCellText(
                        OrbitalTacticalMapClientState.tileAt(centerX + offsetX, centerZ + offsetZ)));
            }
        }
        status.setValue(OrbitalTacticalMapClientState.revision() < 0L ? Component.translatable(TRANSLATION_PREFIX + "map.status") : OrbitalTacticalMapClientState.summary());
    }

    private static Component mapCellText(@Nullable OrbitalMapTile tile) {
        if (tile == null) {
            return Component.literal("?").withStyle(style -> style.withColor(0x777777));
        }
        char marker = tileMarker(tile);
        int color = tile.known() ? tile.biomeColor() : 0x777777;
        return Component.literal(Character.toString(marker)).withStyle(style -> style.withColor(color));
    }

    private static char tileMarker(OrbitalMapTile tile) {
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_ACTIVE_PUBLIC_ATTACK) != 0) {
            return 'A';
        }
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_PRIMARY_ANCHOR) != 0) {
            return 'P';
        }
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_UPLINK_BEACON) != 0) {
            return 'B';
        }
        return tile.known() ? '.' : '?';
    }

    private static TextField textField(String id, String initialValue, int left, int top, int width) {
        TextField field = new TextField();
        field.setId(id);
        field.setText(initialValue, false);
        field.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(FIELD_HEIGHT));
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
        selector.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(FIELD_HEIGHT));
        return selector;
    }

    private static Label label(
                               String id,
                               String translationKey,
                               int left,
                               int top,
                               int width,
                               int height) {
        Label label = new Label();
        label.setId(id);
        label.setText(Component.translatable(translationKey));
        label.setAllowHitTest(false);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(height));
        return label;
    }

    private static Button button(String id, String translationKey, int left) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(translationKey));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(88)
                .width(198)
                .height(BUTTON_HEIGHT));
        return button;
    }

    private static Component modeName(@Nullable OrbitalAttackMode mode) {
        // LDLib2 may render a selector once on the server before its selected value is assigned.
        if (mode == null) {
            return Component.empty();
        }
        return Component.translatable(switch (mode) {
            case KINETIC -> "screen.data_energistics.orbital_control_terminal.mode.kinetic";
            case DIRECTED_ENERGY -> "screen.data_energistics.orbital_control_terminal.mode.directed_energy";
            case DIGITAL_ANNIHILATION -> "screen.data_energistics.orbital_control_terminal.mode.digital_annihilation";
        });
    }

    private static Component targetYModeName(@Nullable OrbitalTargetYMode mode) {
        if (mode == null) {
            return Component.empty();
        }
        return Component.translatable(switch (mode) {
            case ABSOLUTE -> TRANSLATION_PREFIX + "y_mode.absolute";
            case SURFACE_OFFSET -> TRANSLATION_PREFIX + "y_mode.surface_offset";
        });
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

    private record TargetDraft(
                               OrbitalAttackMode mode,
                               String dimension,
                               int targetX,
                               int targetZ,
                               OrbitalTargetYMode targetYMode,
                               int targetYValue,
                               int directedRadius,
                               int depthCode) {}

    private static final class ClientPanelState {

        private OrbitalAttackMode mode = OrbitalAttackMode.KINETIC;
        private OrbitalTargetYMode targetYMode = OrbitalTargetYMode.SURFACE_OFFSET;
        private OrbitalDirectedEnergyDepth depth = OrbitalDirectedEnergyDepth.DEPTH_32;
        private int heldModeCode = NO_MODE;

        private OrbitalAttackMode mode() {
            return this.mode;
        }

        private OrbitalTargetYMode targetYMode() {
            return this.targetYMode;
        }

        private OrbitalDirectedEnergyDepth depth() {
            return this.depth;
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

        private int beginHold() {
            this.heldModeCode = this.mode.wireCode();
            return this.heldModeCode;
        }

        private boolean holding() {
            return this.heldModeCode != NO_MODE;
        }

        private int takeHoldMode() {
            int modeCode = this.heldModeCode;
            this.heldModeCode = NO_MODE;
            return modeCode;
        }

        private void clearHold() {
            this.heldModeCode = NO_MODE;
        }
    }
}
