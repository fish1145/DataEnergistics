package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.RangeAdjustmentMode;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerDeviceKey;
import com.fish_dan_.data_energistics.menu.patternencoding.MenuClientRefresh;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetEntry;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetsPayload;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetsReceiver;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetsSnapshot;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DataDistributionTowerMenu extends AEBaseMenu implements DataDistributionTowerTargetsReceiver {

    private static final String ACTION_FOCUS_TARGET = "focus_target";
    private static final String ACTION_SET_RANGE_VISIBLE = "set_range_visible";
    private static final String ACTION_SET_CONNECTION_MODE = "set_connection_mode";
    private static final String ACTION_SET_RANGE_ADJUSTMENT_MODE = "set_range_adjustment_mode";
    private static final String ACTION_SET_TARGET_TRANSFER_MODE = "set_target_transfer_mode";
    private static final String ACTION_SET_VIRTUAL_DEVICE_DISABLED = "set_virtual_device_disabled";
    @Nullable
    private final DataDistributionTowerBlockEntity host;
    private final RestrictedInputSlot boosterSlot;
    private List<TargetSnapshotKey> lastTargetSnapshotKeys;
    private Set<TargetBindingIdentity> targetSnapshotIdentities = Set.of();
    private Set<TargetIdentity> focusableTargetSnapshotIdentities = Set.of();
    private long targetSnapshotRevision = -1L;
    private long targetDisplayStateRevision = Long.MIN_VALUE;
    public List<DataDistributionTowerTargetEntry> boundTargetEntries = List.of();

    @GuiSync(730)
    public int usedChannels;
    @GuiSync(731)
    public int maxChannels;
    @GuiSync(732)
    public long availableFe;
    @GuiSync(733)
    public int chunkRadius;
    @GuiSync(734)
    public boolean online;
    @GuiSync(735)
    public boolean rangeVisible;
    @GuiSync(736)
    public int boundTargetCount;
    @GuiSync(741)
    public int connectionMode = ConnectionMode.AE_AND_FE.ordinal();
    @GuiSync(744)
    public int rangeAdjustmentMode = RangeAdjustmentMode.POINT.ordinal();
    @GuiSync(745)
    public long physicalChannels;
    @GuiSync(746)
    public long virtualChannels;
    @GuiSync(747)
    public long remainingChannels;
    @GuiSync(748)
    public boolean unlimitedChannels;

    public DataDistributionTowerMenu(int id, Inventory playerInventory, @Nullable DataDistributionTowerBlockEntity host) {
        super(DEMenus.DATA_DISTRIBUTION_TOWER.get(), id, playerInventory, host);
        this.host = host;
        createPlayerInventorySlots(playerInventory);
        this.boosterSlot = new RestrictedInputSlot(
                RestrictedInputSlot.PlacableItemType.RANGE_BOOSTER,
                host != null ? host.getInternalInventory() : new AppEngInternalInventory(1),
                0);
        addSlot(this.boosterSlot, SlotSemantics.STORAGE);
        this.boosterSlot.setEmptyTooltip(() -> Tooltips.slotTooltip(ButtonToolTips.PlaceWirelessBooster.text()));
        registerClientAction(ACTION_FOCUS_TARGET, TargetAction.class, this::onFocusTarget);
        registerClientAction(ACTION_SET_RANGE_VISIBLE, Boolean.class, this::setRangeVisible);
        registerClientAction(ACTION_SET_CONNECTION_MODE, Integer.class, this::setConnectionMode);
        registerClientAction(ACTION_SET_RANGE_ADJUSTMENT_MODE, Boolean.class, this::setRangeAdjustmentMode);
        registerClientAction(ACTION_SET_TARGET_TRANSFER_MODE, TargetTransferModeAction.class, this::setTargetTransferMode);
        registerClientAction(
                ACTION_SET_VIRTUAL_DEVICE_DISABLED,
                VirtualDeviceDisabledAction.class,
                this::setVirtualDeviceDisabled);
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        if (this.isClientSide()) {
            MenuClientRefresh.refreshDataDistributionTowerScreen();
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.host != null) {
            var tower = this.host;
            this.usedChannels = tower.getUsedChannelCount();
            this.maxChannels = tower.getMaxChannelCount();
            this.physicalChannels = tower.getPhysicalChannelCount();
            this.virtualChannels = tower.getVirtualChannelCount();
            this.unlimitedChannels = tower.getRemainingChannelCount().isEmpty();
            this.remainingChannels = tower.getRemainingChannelCount().orElse(0L);
            this.availableFe = tower.getAvailableFeForUi();
            this.chunkRadius = tower.getConfiguredChunkRadius();
            this.online = tower.isTowerNetworkOnlineForUi();
            this.rangeVisible = tower.isRangeDisplayEnabled();
            this.connectionMode = tower.getConnectionMode().ordinal();
            this.rangeAdjustmentMode = tower.getRangeAdjustmentMode().ordinal();
            List<BoundTargetSummary> summaries = tower.getBoundTargetSummaries(Integer.MAX_VALUE);
            this.boundTargetCount = summaries.size();
            syncTargetSnapshot(summaries, tower.getTargetDisplayStateRevision());
        }

        super.broadcastChanges();
    }

    @Override
    public void receiveDataDistributionTowerTargets(DataDistributionTowerTargetsSnapshot snapshot) {
        if (snapshot.containerId() != this.containerId) {
            throw new IllegalArgumentException("Target snapshot container does not match this menu");
        }
        this.boundTargetEntries = snapshot.entries();
        this.boundTargetCount = snapshot.totalCount();
        this.targetSnapshotRevision = snapshot.revision();
        if (this.isClientSide()) {
            MenuClientRefresh.refreshDataDistributionTowerScreen();
        }
    }

    private void syncTargetSnapshot(List<BoundTargetSummary> summaries, long targetDisplayStateRevision) {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        List<TargetSnapshotKey> snapshotKeys = summaries.stream().map(TargetSnapshotKey::fromSummary).toList();
        this.targetDisplayStateRevision = targetDisplayStateRevision;
        if (snapshotKeys.equals(this.lastTargetSnapshotKeys)) {
            return;
        }

        this.lastTargetSnapshotKeys = snapshotKeys;
        this.targetSnapshotIdentities = Set.copyOf(summaries.stream()
                .map(TargetBindingIdentity::fromSummary)
                .toList());
        this.focusableTargetSnapshotIdentities = Set.copyOf(summaries.stream()
                .filter(summary -> !summary.transferInfo().logicalDevice())
                .map(TargetIdentity::fromSummary)
                .toList());
        this.targetSnapshotRevision = Math.incrementExact(this.targetSnapshotRevision);
        List<DataDistributionTowerTargetEntry> entries = summaries.stream()
                .map(DataDistributionTowerTargetEntry::fromSummary)
                .toList();
        for (DataDistributionTowerTargetsPayload payload : DataDistributionTowerTargetsPayload.batches(
                this.containerId, this.targetSnapshotRevision, entries)) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    public void sendFocusTarget(String dimensionId, int x, int y, int z, boolean teleport) {
        if (this.targetSnapshotRevision < 0L) {
            return;
        }
        sendClientAction(ACTION_FOCUS_TARGET, new TargetAction(this.targetSnapshotRevision, dimensionId, x, y, z, teleport));
    }

    public void sendSetRangeVisible(boolean visible) {
        sendClientAction(ACTION_SET_RANGE_VISIBLE, visible);
    }

    public void sendSetConnectionMode(ConnectionMode connectionMode) {
        sendClientAction(ACTION_SET_CONNECTION_MODE,
                (connectionMode == null ? ConnectionMode.AE_AND_FE : connectionMode).ordinal());
    }

    public void sendSetRangeAdjustmentMode(boolean scopeMode) {
        sendClientAction(ACTION_SET_RANGE_ADJUSTMENT_MODE, scopeMode);
    }

    public void sendSetTargetTransferMode(TargetTransferInfo transferInfo, TargetTransferMode mode) {
        if (this.targetSnapshotRevision < 0L) {
            return;
        }
        TowerRuntimeKey ownerTower = transferInfo.ownerTower();
        BlockPos bindingAnchor = transferInfo.bindingAnchor();
        sendClientAction(ACTION_SET_TARGET_TRANSFER_MODE, new TargetTransferModeAction(
                this.targetSnapshotRevision,
                ownerTower.dimensionId().toString(),
                ownerTower.position().getX(),
                ownerTower.position().getY(),
                ownerTower.position().getZ(),
                transferInfo.bindingDimensionId().toString(),
                bindingAnchor.getX(),
                bindingAnchor.getY(),
                bindingAnchor.getZ(),
                mode.ordinal()));
    }

    /**
     * Sends one per-device disable change using the stable key from the current batched snapshot.
     */
    public void sendSetVirtualDeviceDisabled(TargetTransferInfo transferInfo, boolean disabled) {
        TowerDeviceKey deviceKey = transferInfo.deviceKey();
        if (deviceKey == null || this.targetSnapshotRevision < 0L) {
            return;
        }
        TowerRuntimeKey ownerTower = transferInfo.ownerTower();
        BlockPos devicePosition = deviceKey.position() == null ? BlockPos.ZERO : deviceKey.position();
        sendClientAction(ACTION_SET_VIRTUAL_DEVICE_DISABLED, new VirtualDeviceDisabledAction(
                this.targetSnapshotRevision,
                ownerTower.dimensionId().toString(),
                ownerTower.position().getX(),
                ownerTower.position().getY(),
                ownerTower.position().getZ(),
                transferInfo.bindingDimensionId().toString(),
                transferInfo.bindingAnchor().getX(),
                transferInfo.bindingAnchor().getY(),
                transferInfo.bindingAnchor().getZ(),
                deviceKey.dimensionId().toString(),
                deviceKey.position() != null,
                devicePosition.getX(),
                devicePosition.getY(),
                devicePosition.getZ(),
                deviceKey.side(),
                deviceKey.nodeType(),
                deviceKey.occurrence(),
                disabled));
    }

    private void onFocusTarget(TargetAction action) {
        if (action == null || action.targetSnapshotRevision() == null || action.dimensionId() == null || action.x() == null || action.y() == null || action.z() == null || action.teleport() == null || action.targetSnapshotRevision() < 0L) {
            logRejectedTargetAction("with an incomplete target payload", action);
            return;
        }

        if (this.host == null) {
            logRejectedTargetAction("without a server tower host", action);
            return;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(action.dimensionId());
        if (dimensionId == null) {
            logRejectedTargetAction("with an invalid dimension identifier", action);
            return;
        }

        long actionRevision = action.targetSnapshotRevision();
        if (actionRevision != this.targetSnapshotRevision) {
            logRejectedTargetAction("with a stale target snapshot revision", action);
            return;
        }

        if (this.targetDisplayStateRevision != this.host.getTargetDisplayStateRevision()) {
            logRejectedTargetAction("after the target display state changed", action);
            return;
        }

        BlockPos targetPos = new BlockPos(action.x(), action.y(), action.z());
        TargetIdentity targetIdentity = new TargetIdentity(dimensionId, targetPos);
        if (!this.focusableTargetSnapshotIdentities.contains(targetIdentity)) {
            logRejectedTargetAction("for a target that is not currently focusable", action);
            return;
        }

        var levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);

        getPlayer().sendSystemMessage(Component.translatable(
                "message.data_energistics.data_distribution_tower.target",
                targetPos.getX(),
                targetPos.getY(),
                targetPos.getZ(),
                dimensionId.toString()));

        if (!action.teleport()) {
            return;
        }

        if (!getPlayer().hasPermissions(2)) {
            getPlayer().displayClientMessage(Component.translatable(
                    "message.data_energistics.data_distribution_tower.teleport_requires_cheats"), true);
            return;
        }

        var server = getPlayer().getServer();
        if (server == null) {
            return;
        }

        var targetLevel = server.getLevel(levelKey);
        if (targetLevel == null) {
            return;
        }

        getPlayer().closeContainer();
        getPlayer().teleportTo(targetLevel, targetPos.getX() + 0.5, targetPos.getY() + 1.1, targetPos.getZ() + 0.5,
                Set.of(), getPlayer().getYRot(), getPlayer().getXRot());
    }

    private void logRejectedTargetAction(String reason, @Nullable TargetAction action) {
        Data_Energistics.LOGGER.warn(
                "Rejected Data Distribution Tower target action {} at {}: revision={}, dimension={}, x={}, y={}, z={}, teleport={}",
                reason,
                this.host == null ? null : this.host.getBlockPos(),
                action == null ? null : action.targetSnapshotRevision(),
                action == null ? null : action.dimensionId(),
                action == null ? null : action.x(),
                action == null ? null : action.y(),
                action == null ? null : action.z(),
                action == null ? null : action.teleport());
    }

    private void setRangeVisible(Boolean visible) {
        if (visible == null || this.host == null) {
            return;
        }

        this.rangeVisible = this.host.toggleRangeDisplay();
        broadcastChanges();
    }

    private void setConnectionMode(Integer connectionMode) {
        if (connectionMode == null || this.host == null) {
            return;
        }

        this.host.setConnectionMode(ConnectionMode.fromOrdinal(connectionMode));
        this.connectionMode = this.host.getConnectionMode().ordinal();
        broadcastChanges();
    }

    private void setRangeAdjustmentMode(Boolean scopeMode) {
        if (scopeMode == null || this.host == null) {
            return;
        }

        this.host.setRangeAdjustmentMode(scopeMode ? RangeAdjustmentMode.SCOPE : RangeAdjustmentMode.POINT);
        this.rangeAdjustmentMode = this.host.getRangeAdjustmentMode().ordinal();
        broadcastChanges();
    }

    private void setTargetTransferMode(TargetTransferModeAction action) {
        DataDistributionTowerBlockEntity tower = this.host;
        if (action == null || tower == null || !action.complete()) {
            logRejectedTargetTransferModeAction("with an incomplete target payload", action);
            return;
        }
        if (action.targetSnapshotRevision() != this.targetSnapshotRevision || this.targetDisplayStateRevision != tower.getTargetDisplayStateRevision()) {
            logRejectedTargetTransferModeAction("after the target snapshot changed", action);
            return;
        }

        ResourceLocation ownerDimensionId = ResourceLocation.tryParse(action.ownerDimensionId());
        ResourceLocation bindingDimensionId = ResourceLocation.tryParse(action.bindingDimensionId());
        if (ownerDimensionId == null || bindingDimensionId == null) {
            logRejectedTargetTransferModeAction("with an invalid dimension identifier", action);
            return;
        }

        var level = tower.getLevel();
        if (level == null) {
            logRejectedTargetTransferModeAction("without a tower level", action);
            return;
        }

        if (!ownerDimensionId.equals(level.dimension().location()) || !bindingDimensionId.equals(level.dimension().location())) {
            logRejectedTargetTransferModeAction("for a different dimension", action);
            return;
        }

        TowerRuntimeKey ownerTower = new TowerRuntimeKey(
                ownerDimensionId,
                new BlockPos(action.ownerX(), action.ownerY(), action.ownerZ()));
        BlockPos targetPos = new BlockPos(action.bindingX(), action.bindingY(), action.bindingZ()).immutable();
        if (!this.targetSnapshotIdentities.contains(new TargetBindingIdentity(
                ownerTower, bindingDimensionId, targetPos))) {
            logRejectedTargetTransferModeAction("for a target that is not currently bound", action);
            return;
        }

        TargetTransferMode[] transferModes = TargetTransferMode.values();
        int modeOrdinal = action.mode();
        if (modeOrdinal < 0 || modeOrdinal >= transferModes.length) {
            logRejectedTargetTransferModeAction("with an invalid transfer mode", action);
            return;
        }

        if (!tower.setTowerNetworkTargetTransferMode(
                ownerTower, bindingDimensionId, targetPos, transferModes[modeOrdinal])) {
            logRejectedTargetTransferModeAction("for a binding absent from the current tower network", action);
            return;
        }
        broadcastChanges();
    }

    /** Validates and applies one per-device disable action against the current server snapshot. */
    private void setVirtualDeviceDisabled(VirtualDeviceDisabledAction action) {
        DataDistributionTowerBlockEntity tower = this.host;
        if (action == null || tower == null || !action.complete()) {
            logRejectedVirtualDeviceAction("with an incomplete payload", action);
            return;
        }
        if (action.targetSnapshotRevision() != this.targetSnapshotRevision || this.targetDisplayStateRevision != tower.getTargetDisplayStateRevision()) {
            logRejectedVirtualDeviceAction("after the target snapshot changed", action);
            return;
        }
        ResourceLocation ownerDimension = ResourceLocation.tryParse(action.ownerDimensionId());
        ResourceLocation bindingDimension = ResourceLocation.tryParse(action.bindingDimensionId());
        ResourceLocation deviceDimension = ResourceLocation.tryParse(action.deviceDimensionId());
        if (ownerDimension == null || bindingDimension == null || deviceDimension == null) {
            logRejectedVirtualDeviceAction("with an invalid dimension identifier", action);
            return;
        }
        var level = tower.getLevel();
        if (level == null || !ownerDimension.equals(level.dimension().location()) || !bindingDimension.equals(level.dimension().location())) {
            logRejectedVirtualDeviceAction("for a different tower dimension", action);
            return;
        }
        TowerRuntimeKey ownerTower = new TowerRuntimeKey(
                ownerDimension,
                new BlockPos(action.ownerX(), action.ownerY(), action.ownerZ()));
        BlockPos bindingAnchor = new BlockPos(action.bindingX(), action.bindingY(), action.bindingZ()).immutable();
        if (!this.targetSnapshotIdentities.contains(new TargetBindingIdentity(
                ownerTower, bindingDimension, bindingAnchor))) {
            logRejectedVirtualDeviceAction("for a binding absent from the current snapshot", action);
            return;
        }
        BlockPos devicePosition = action.devicePositioned() ? new BlockPos(action.deviceX(), action.deviceY(), action.deviceZ()).immutable() : null;
        TowerDeviceKey deviceKey = new TowerDeviceKey(
                deviceDimension,
                devicePosition,
                action.side(),
                action.nodeType(),
                action.occurrence());
        if (!tower.setTowerNetworkVirtualDeviceDisabled(
                ownerTower, bindingDimension, bindingAnchor, deviceKey, action.disabled())) {
            logRejectedVirtualDeviceAction("for a device absent from the current snapshot", action);
            return;
        }
        broadcastChanges();
    }

    private record TargetAction(@Nullable Long targetSnapshotRevision, @Nullable String dimensionId,
                                @Nullable Integer x, @Nullable Integer y, @Nullable Integer z,
                                @Nullable Boolean teleport) {}

    private void logRejectedTargetTransferModeAction(String reason, @Nullable TargetTransferModeAction action) {
        Data_Energistics.LOGGER.warn(
                "Rejected Data Distribution Tower target transfer mode action {} at {}: action={}",
                reason,
                this.host == null ? null : this.host.getBlockPos(),
                action);
    }

    private record TargetTransferModeAction(
                                            @Nullable Long targetSnapshotRevision,
                                            @Nullable String ownerDimensionId,
                                            @Nullable Integer ownerX,
                                            @Nullable Integer ownerY,
                                            @Nullable Integer ownerZ,
                                            @Nullable String bindingDimensionId,
                                            @Nullable Integer bindingX,
                                            @Nullable Integer bindingY,
                                            @Nullable Integer bindingZ,
                                            @Nullable Integer mode) {

        private boolean complete() {
            return this.targetSnapshotRevision != null && this.ownerDimensionId != null && this.ownerX != null && this.ownerY != null && this.ownerZ != null && this.bindingDimensionId != null && this.bindingX != null && this.bindingY != null && this.bindingZ != null && this.mode != null;
        }
    }

    private record VirtualDeviceDisabledAction(
                                               @Nullable Long targetSnapshotRevision,
                                               @Nullable String ownerDimensionId,
                                               @Nullable Integer ownerX,
                                               @Nullable Integer ownerY,
                                               @Nullable Integer ownerZ,
                                               @Nullable String bindingDimensionId,
                                               @Nullable Integer bindingX,
                                               @Nullable Integer bindingY,
                                               @Nullable Integer bindingZ,
                                               @Nullable String deviceDimensionId,
                                               @Nullable Boolean devicePositioned,
                                               @Nullable Integer deviceX,
                                               @Nullable Integer deviceY,
                                               @Nullable Integer deviceZ,
                                               @Nullable Integer side,
                                               @Nullable String nodeType,
                                               @Nullable Integer occurrence,
                                               @Nullable Boolean disabled) {

        private boolean complete() {
            return this.targetSnapshotRevision != null && this.ownerDimensionId != null && this.ownerX != null && this.ownerY != null && this.ownerZ != null && this.bindingDimensionId != null && this.bindingX != null && this.bindingY != null && this.bindingZ != null && this.deviceDimensionId != null && this.devicePositioned != null && this.deviceX != null && this.deviceY != null && this.deviceZ != null && this.side != null && this.nodeType != null && this.occurrence != null && this.disabled != null;
        }
    }

    private void logRejectedVirtualDeviceAction(
                                                String reason, @Nullable VirtualDeviceDisabledAction action) {
        Data_Energistics.LOGGER.warn(
                "Rejected Data Distribution Tower virtual-device action {} at {}: action={}",
                reason,
                this.host == null ? null : this.host.getBlockPos(),
                action);
    }

    private record TargetSnapshotKey(ResourceLocation itemId, String displayName, int count,
                                     ResourceLocation dimensionId, BlockPos pos, TargetKind kind,
                                     TargetTransferMode transferMode, TargetTransferInfo transferInfo) {

        private static TargetSnapshotKey fromSummary(BoundTargetSummary summary) {
            return new TargetSnapshotKey(summary.itemId(), summary.displayName(), summary.count(), summary.dimensionId(),
                    summary.pos(), summary.kind(), summary.transferMode(), summary.transferInfo());
        }
    }

    private record TargetIdentity(ResourceLocation dimensionId, BlockPos pos) {

        private TargetIdentity {
            pos = pos.immutable();
        }

        private static TargetIdentity fromSummary(BoundTargetSummary summary) {
            return new TargetIdentity(summary.dimensionId(), summary.pos());
        }
    }

    private record TargetBindingIdentity(TowerRuntimeKey ownerTower, ResourceLocation dimensionId, BlockPos pos) {

        private TargetBindingIdentity {
            pos = pos.immutable();
        }

        private static TargetBindingIdentity fromSummary(BoundTargetSummary summary) {
            TargetTransferInfo transferInfo = summary.transferInfo();
            return new TargetBindingIdentity(
                    transferInfo.ownerTower(), transferInfo.bindingDimensionId(), transferInfo.bindingAnchor());
        }
    }
}
