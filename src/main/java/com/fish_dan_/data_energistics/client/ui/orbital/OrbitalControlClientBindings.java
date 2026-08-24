package com.fish_dan_.data_energistics.client.ui.orbital;

import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalTacticalMapClientState;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapter;
import com.fish_dan_.data_energistics.client.map.orbital.compatibility.TacticalMapAdapters;
import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapRequestPayload;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlDraft;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlClientBinding;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlClientBridge;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlDashboard;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlDashboard.MapProviderOption;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiSource;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Physical-client lifecycle and event bindings for the common orbital control dashboard. */
public final class OrbitalControlClientBindings {

    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.fire_control.";
    private static final Object2ObjectOpenHashMap<ModularUI, Session> SESSIONS = new Object2ObjectOpenHashMap<>();

    private OrbitalControlClientBindings() {}

    /** Installs the physical-client binder before any orbital menu is opened. */
    public static void install() {
        OrbitalControlClientBridge.install(OrbitalControlClientBindings::bind);
    }

    /** Closes the session owned by the screen that is about to be replaced. */
    public static void onScreenOpening(Screen current) {
        ModularUI modularUI = ModularUI.of(current);
        if (modularUI == null) {
            return;
        }
        if (!SESSIONS.containsKey(modularUI)) {
            return;
        }
        SESSIONS.remove(modularUI).close();
    }

    /** Releases every menu session and process-local map selection when the client disconnects. */
    public static void clear() {
        for (Session session : List.copyOf(SESSIONS.values())) {
            session.close(false);
        }
        SESSIONS.clear();
        OrbitalTacticalMapClientState.clear();
        OrbitalMapSelectionClientSession.clear();
    }

    private static OrbitalControlClientBinding bind(
                                                    OrbitalControlDashboard dashboard,
                                                    OrbitalControlUiSource source,
                                                    RPCEmitter commandEmitter) {
        return new Session(dashboard, source, commandEmitter);
    }

    private static final class Session implements OrbitalControlClientBinding {

        private final OrbitalControlDashboard dashboard;
        private final OrbitalControlUiSource source;
        private final RPCEmitter commandEmitter;
        private final Object2ObjectOpenHashMap<ResourceLocation, TacticalMapAdapter> mapAdapters = new Object2ObjectOpenHashMap<>();

        private @Nullable ModularUI modularUI;
        private @Nullable ISubscription mapSubscription;
        private @Nullable UUID selectedWeaponId;
        private @Nullable OrbitalFireControlDraft previewedDraft;
        private @Nullable UUID previewNonce;
        private @Nullable UUID holdNonce;
        private boolean operable;
        private boolean discardSent;
        private boolean pendingSelectionConsumed;
        private boolean closed;

        private Session(
                        OrbitalControlDashboard dashboard,
                        OrbitalControlUiSource source,
                        RPCEmitter commandEmitter) {
            this.dashboard = dashboard;
            this.source = source;
            this.commandEmitter = commandEmitter;
            configureMapProviders();
            bindDraftListeners();
            bindButtons();
        }

        @Override
        public void attach(ModularUI modularUI) {
            if (this.closed || this.modularUI != null) {
                throw new IllegalStateException("Orbital control client session cannot be attached twice");
            }
            this.modularUI = modularUI;
            Session previous = SESSIONS.put(modularUI, this);
            if (previous != null && previous != this) {
                previous.close();
            }
            this.mapSubscription = OrbitalTacticalMapClientState.subscribe(this::updateMapViewport);
            updateMapViewport();
        }

        @Override
        public void acceptSnapshot(OrbitalControlMenuSnapshot snapshot) {
            if (this.closed) {
                return;
            }
            UUID newSelectedWeaponId = snapshot.terminal().selectedWeaponId();
            boolean weaponChanged = !Objects.equals(this.selectedWeaponId, newSelectedWeaponId);
            if (weaponChanged) {
                this.holdNonce = null;
                this.previewedDraft = null;
                this.previewNonce = null;
                this.discardSent = false;
                OrbitalTacticalMapClientState.clear();
            }
            this.selectedWeaponId = newSelectedWeaponId;
            this.operable = snapshot.terminal()
                    .selectedWeapon()
                    .map(WeaponEntry::canOperate)
                    .orElse(false);
            this.dashboard.updateDirectedFields(this.operable);

            OrbitalFireControlSessionSnapshot fireControl = snapshot.fireControl();
            OrbitalFireControlSessionSnapshot.PreviewDetails preview = fireControl.preview();
            if ((fireControl.phase() == OrbitalFireControlSessionSnapshot.Phase.READY ||
                    fireControl.phase() == OrbitalFireControlSessionSnapshot.Phase.HOLDING) && preview != null) {
                this.previewNonce = preview.nonce();
            } else {
                this.previewNonce = null;
                if (fireControl.phase() == OrbitalFireControlSessionSnapshot.Phase.IDLE ||
                        fireControl.phase() == OrbitalFireControlSessionSnapshot.Phase.REJECTED) {
                    this.holdNonce = null;
                }
            }

            if (!this.pendingSelectionConsumed && this.selectedWeaponId != null) {
                this.pendingSelectionConsumed = true;
                OrbitalFireControlDraft pending = OrbitalMapSelectionClientSession.takePending(this.selectedWeaponId);
                if (pending != null) {
                    applyDraft(pending);
                    requestPreview();
                }
            }
        }

        @Override
        public void close() {
            close(true);
        }

        private void close(boolean notifyServer) {
            if (this.closed) {
                return;
            }
            if (notifyServer) {
                cancelLocalHold();
            } else {
                this.holdNonce = null;
            }
            this.closed = true;
            if (this.mapSubscription != null) {
                this.mapSubscription.unsubscribe();
                this.mapSubscription = null;
            }
            if (this.modularUI != null && SESSIONS.get(this.modularUI) == this) {
                SESSIONS.remove(this.modularUI);
            }
            this.modularUI = null;
        }

        private void configureMapProviders() {
            List<TacticalMapAdapter> available = TacticalMapAdapters.available();
            ObjectArrayList<MapProviderOption> options = new ObjectArrayList<>(available.size());
            for (TacticalMapAdapter adapter : available) {
                this.mapAdapters.put(adapter.id(), adapter);
                options.add(new MapProviderOption(adapter.id(), adapter.displayName()));
            }
            this.dashboard.mapProvider.setCandidates(List.copyOf(options));
            this.dashboard.mapProvider.setSelected(options.getFirst(), false);
        }

        private void bindDraftListeners() {
            this.dashboard.dimension.registerValueListener(ignored -> draftChanged());
            this.dashboard.targetX.registerValueListener(ignored -> draftChanged());
            this.dashboard.targetZ.registerValueListener(ignored -> draftChanged());
            this.dashboard.targetYValue.registerValueListener(ignored -> draftChanged());
            this.dashboard.radius.registerValueListener(ignored -> draftChanged());
            this.dashboard.mode.registerValueListener(ignored -> {
                this.dashboard.updateDirectedFields(this.operable);
                draftChanged();
            });
            this.dashboard.targetYMode.registerValueListener(ignored -> draftChanged());
            this.dashboard.depth.registerValueListener(ignored -> draftChanged());
        }

        private void bindButtons() {
            this.dashboard.previousWeapon.setOnClick(ignored -> cycleWeapon(false));
            this.dashboard.nextWeapon.setOnClick(ignored -> cycleWeapon(true));
            for (OrbitalControlDashboard.ModeRow row : this.dashboard.modeRows) {
                row.action.setOnClick(ignored -> {
                    if (this.operable) {
                        send(new OrbitalControlIntent.CancelOrAbortMode(row.mode));
                    }
                });
            }
            this.dashboard.refreshPreview.setOnClick(ignored -> requestPreview());
            this.dashboard.selectOnMap.setOnClick(ignored -> selectOnMap());
            this.dashboard.mapRefresh.setOnClick(ignored -> requestMap());
            for (int cellIndex = 0; cellIndex < this.dashboard.mapCells.size(); cellIndex++) {
                int offsetX = cellIndex % (OrbitalControlDashboard.MAP_RADIUS * 2 + 1) - OrbitalControlDashboard.MAP_RADIUS;
                int offsetZ = cellIndex / (OrbitalControlDashboard.MAP_RADIUS * 2 + 1) - OrbitalControlDashboard.MAP_RADIUS;
                this.dashboard.mapCells.get(cellIndex).setOnClick(ignored -> selectMapCell(offsetX, offsetZ));
            }
            this.dashboard.confirm.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0) {
                    UUID nonce = startableHoldNonce();
                    if (nonce != null) {
                        this.holdNonce = nonce;
                        send(new OrbitalControlIntent.StartHold(nonce));
                    }
                }
            });
            this.dashboard.confirm.addEventListener(UIEvents.MOUSE_LEAVE, ignored -> cancelLocalHold());
            this.dashboard.root.addEventListener(UIEvents.MOUSE_UP, event -> {
                if (event.button == 0 && this.holdNonce != null) {
                    UUID nonce = this.holdNonce;
                    this.holdNonce = null;
                    send(new OrbitalControlIntent.ReleaseHold(nonce));
                }
            });
        }

        private void cycleWeapon(boolean forward) {
            cancelLocalHold();
            send(new OrbitalControlIntent.CycleWeapon(forward));
        }

        private @Nullable UUID startableHoldNonce() {
            UUID nonce = this.previewNonce;
            if (!this.operable || nonce == null || this.holdNonce != null || this.previewedDraft == null) {
                return null;
            }
            try {
                return this.previewedDraft.equals(readDraft()) ? nonce : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private void requestPreview() {
            if (!this.operable) {
                return;
            }
            OrbitalFireControlDraft draft;
            try {
                draft = readDraft();
            } catch (IllegalArgumentException exception) {
                cancelLocalHold();
                this.dashboard.feedback.setValue(Component.translatable(PREFIX + "form.invalid"));
                return;
            }
            cancelLocalHold();
            this.previewedDraft = draft;
            this.previewNonce = null;
            this.discardSent = false;
            if (!send(new OrbitalControlIntent.RequestPreview(draft))) {
                this.previewedDraft = null;
            }
        }

        private void draftChanged() {
            if (this.previewedDraft == null || this.discardSent) {
                return;
            }
            boolean changed;
            try {
                changed = !this.previewedDraft.equals(readDraft());
            } catch (IllegalArgumentException exception) {
                changed = true;
            }
            if (!changed) {
                return;
            }
            cancelLocalHold();
            this.previewNonce = null;
            this.discardSent = true;
            send(OrbitalControlIntent.DiscardPreview.INSTANCE);
        }

        private OrbitalFireControlDraft readDraft() {
            ResourceLocation dimensionId = ResourceLocation.tryParse(this.dashboard.dimension.getRawText());
            OrbitalAttackMode mode = this.dashboard.mode.getValue();
            OrbitalTargetYMode targetYMode = this.dashboard.targetYMode.getValue();
            if (dimensionId == null || mode == null || targetYMode == null) {
                throw new IllegalArgumentException("Orbital fire-control form has an invalid selector value");
            }
            int targetX = parseInteger(this.dashboard.targetX.getRawText(), "target X");
            int targetZ = parseInteger(this.dashboard.targetZ.getRawText(), "target Z");
            int targetYValue = parseInteger(this.dashboard.targetYValue.getRawText(), "target Y");
            int directedRadius = 0;
            OrbitalDirectedEnergyDepth directedDepth = null;
            if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
                Integer selectedRadius = this.dashboard.radius.getValue();
                directedDepth = this.dashboard.depth.getValue();
                if (selectedRadius == null || directedDepth == null) {
                    throw new IllegalArgumentException("Directed-energy range is not selected");
                }
                directedRadius = selectedRadius;
            }
            return new OrbitalFireControlDraft(
                    mode,
                    dimensionId,
                    targetX,
                    targetZ,
                    targetYMode,
                    targetYValue,
                    directedRadius,
                    directedDepth);
        }

        private void applyDraft(OrbitalFireControlDraft draft) {
            this.dashboard.dimension.setText(draft.dimensionId().toString(), false);
            this.dashboard.mode.setSelected(draft.mode(), false);
            this.dashboard.targetX.setText(Integer.toString(draft.targetX()), false);
            this.dashboard.targetZ.setText(Integer.toString(draft.targetZ()), false);
            this.dashboard.targetYMode.setSelected(draft.targetYMode(), false);
            this.dashboard.targetYValue.setText(Integer.toString(draft.targetYValue()), false);
            if (draft.mode() == OrbitalAttackMode.DIRECTED_ENERGY) {
                this.dashboard.radius.setSelected(draft.directedRadius(), false);
                this.dashboard.depth.setSelected(Objects.requireNonNull(draft.directedDepth()), false);
            }
            this.dashboard.updateDirectedFields(this.operable);
            draftChanged();
        }

        private void selectOnMap() {
            if (!this.operable) {
                return;
            }
            OrbitalFireControlDraft draft;
            try {
                draft = readDraft();
            } catch (IllegalArgumentException exception) {
                cancelLocalHold();
                this.dashboard.feedback.setValue(Component.translatable(PREFIX + "form.invalid"));
                return;
            }
            MapProviderOption provider = this.dashboard.mapProvider.getValue();
            TacticalMapAdapter adapter = provider == null ? null : this.mapAdapters.get(provider.id());
            if (adapter == null) {
                return;
            }
            cancelLocalHold();
            UUID sessionToken = OrbitalMapSelectionClientSession.begin(
                    adapter.id(),
                    this.selectedWeaponId,
                    draft,
                    this.source);
            TacticalMapAdapter.SelectionStart result = TacticalMapAdapters.start(
                    adapter,
                    Minecraft.getInstance(),
                    sessionToken);
            if (result == TacticalMapAdapter.SelectionStart.EMBEDDED) {
                OrbitalMapSelectionClientSession.cancel();
                requestMap();
            } else if (result == TacticalMapAdapter.SelectionStart.FAILED) {
                OrbitalMapSelectionClientSession.cancel();
                this.dashboard.feedback.setValue(Component.translatable(PREFIX + "map.failed"));
            }
        }

        private void requestMap() {
            if (!this.operable || this.selectedWeaponId == null) {
                return;
            }
            OrbitalFireControlDraft draft;
            try {
                draft = readDraft();
            } catch (IllegalArgumentException exception) {
                return;
            }
            int centerChunkX = Math.floorDiv(draft.targetX(), 16);
            int centerChunkZ = Math.floorDiv(draft.targetZ(), 16);
            UUID sessionToken = OrbitalTacticalMapClientState.sessionTokenFor(
                    this.selectedWeaponId,
                    draft.dimensionId());
            long requestNonce = OrbitalTacticalMapClientState.nextRequestNonce();
            OrbitalTacticalMapClientState.expectResponse(
                    this.selectedWeaponId,
                    draft.dimensionId(),
                    requestNonce);
            PacketDistributor.sendToServer(new OrbitalTacticalMapRequestPayload(
                    this.selectedWeaponId,
                    sessionToken,
                    draft.dimensionId(),
                    centerChunkX,
                    centerChunkZ,
                    OrbitalControlDashboard.MAP_RADIUS,
                    requestNonce));
        }

        private void updateMapViewport() {
            if (this.closed || OrbitalTacticalMapClientState.revision() < 0L) {
                this.dashboard.mapStatus.setValue(Component.translatable(PREFIX + "map.status"));
                for (var cell : this.dashboard.mapCells) {
                    cell.setText(OrbitalTacticalMapClientState.cellComponent(null));
                }
                return;
            }
            ResourceLocation dimensionId = OrbitalTacticalMapClientState.dimensionId();
            int centerChunkX = OrbitalTacticalMapClientState.centerChunkX();
            int centerChunkZ = OrbitalTacticalMapClientState.centerChunkZ();
            this.dashboard.mapStatus.setValue(Component.translatable(
                    PREFIX + "map.viewport",
                    Component.literal(dimensionId.toString()),
                    centerChunkX,
                    centerChunkZ));
            int diameter = OrbitalControlDashboard.MAP_RADIUS * 2 + 1;
            for (int cellIndex = 0; cellIndex < this.dashboard.mapCells.size(); cellIndex++) {
                int offsetX = cellIndex % diameter - OrbitalControlDashboard.MAP_RADIUS;
                int offsetZ = cellIndex / diameter - OrbitalControlDashboard.MAP_RADIUS;
                OrbitalMapTile tile = OrbitalTacticalMapClientState.tileAt(
                        centerChunkX + offsetX,
                        centerChunkZ + offsetZ);
                this.dashboard.mapCells.get(cellIndex).setText(OrbitalTacticalMapClientState.cellComponent(tile));
            }
        }

        private void selectMapCell(int offsetX, int offsetZ) {
            if (!this.operable || OrbitalTacticalMapClientState.revision() < 0L) {
                return;
            }
            int chunkX = OrbitalTacticalMapClientState.centerChunkX() + offsetX;
            int chunkZ = OrbitalTacticalMapClientState.centerChunkZ() + offsetZ;
            OrbitalMapTile tile = OrbitalTacticalMapClientState.tileAt(chunkX, chunkZ);
            if (tile == null) {
                return;
            }
            long targetX = (long) chunkX * 16L + 8L;
            long targetZ = (long) chunkZ * 16L + 8L;
            if (Math.abs(targetX) > OrbitalFireControlDraft.MAX_TARGET_COORDINATE ||
                    Math.abs(targetZ) > OrbitalFireControlDraft.MAX_TARGET_COORDINATE) {
                return;
            }
            this.dashboard.dimension.setText(OrbitalTacticalMapClientState.dimensionId().toString(), false);
            this.dashboard.targetX.setText(Long.toString(targetX), false);
            this.dashboard.targetZ.setText(Long.toString(targetZ), false);
            if (tile.known()) {
                this.dashboard.targetYMode.setSelected(OrbitalTargetYMode.SURFACE_OFFSET, false);
                this.dashboard.targetYValue.setText("0", false);
            }
            draftChanged();
        }

        private void cancelLocalHold() {
            if (this.holdNonce == null) {
                return;
            }
            this.holdNonce = null;
            send(OrbitalControlIntent.CancelHold.INSTANCE);
        }

        private boolean send(OrbitalControlIntent intent) {
            return !this.closed && this.commandEmitter.send(intent);
        }

        private static int parseInteger(String value, String description) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid orbital " + description, exception);
            }
        }
    }
}
