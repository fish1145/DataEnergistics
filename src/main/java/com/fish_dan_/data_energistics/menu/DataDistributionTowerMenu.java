package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.RangeAdjustmentMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.menu.common.MenuClientRefresh;
import com.fish_dan_.data_energistics.network.DataDistributionTowerTargetEntry;
import com.fish_dan_.data_energistics.network.DataDistributionTowerTargetsPayload;
import com.fish_dan_.data_energistics.network.DataDistributionTowerTargetsReceiver;
import com.fish_dan_.data_energistics.network.DataDistributionTowerTargetsSnapshot;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DataDistributionTowerMenu extends AEBaseMenu implements DataDistributionTowerTargetsReceiver {

    private static final String ACTION_FOCUS_TARGET = "focus_target";
    private static final String ACTION_SET_RANGE_VISIBLE = "set_range_visible";
    private static final String ACTION_SET_CONNECTION_MODE = "set_connection_mode";
    private static final String ACTION_SET_RANGE_ADJUSTMENT_MODE = "set_range_adjustment_mode";
    private static final String ACTION_SET_TARGET_TRANSFER_MODE = "set_target_transfer_mode";
    @Nullable
    private final DataDistributionTowerBlockEntity host;
    private final RestrictedInputSlot boosterSlot;
    private List<TargetSnapshotKey> lastTargetSnapshotKeys;
    private Set<TargetIdentity> targetSnapshotIdentities = Set.of();
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

    public DataDistributionTowerMenu(int id, Inventory playerInventory, @Nullable DataDistributionTowerBlockEntity host) {
        super(ModMenus.DATA_DISTRIBUTION_TOWER.get(), id, playerInventory, host);
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
            this.availableFe = tower.getAvailableFeForUi();
            this.chunkRadius = tower.getConfiguredChunkRadius();
            this.online = tower.isNetworkNodeOnline();
            this.rangeVisible = tower.isRangeDisplayEnabled();
            this.connectionMode = tower.getConnectionMode().ordinal();
            this.rangeAdjustmentMode = tower.getRangeAdjustmentMode().ordinal();
            this.boundTargetCount = tower.getBoundTargetCount();
            syncTargetSnapshot(tower.getBoundTargetSummaries(Integer.MAX_VALUE), tower.getTargetDisplayStateRevision());
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
        this.targetSnapshotIdentities = Set.copyOf(summaries.stream().map(TargetIdentity::fromSummary).toList());
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

    public void sendSetTargetTransferMode(String dimensionId, int x, int y, int z, TargetTransferMode mode) {
        sendClientAction(ACTION_SET_TARGET_TRANSFER_MODE,
                new TargetTransferModeAction(dimensionId, x, y, z,
                        (mode == null ? TargetTransferMode.AUTO : mode).ordinal()));
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
        if (!this.targetSnapshotIdentities.contains(targetIdentity)) {
            logRejectedTargetAction("for a target that is not currently bound", action);
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
        if (action == null || this.host == null) {
            return;
        }

        this.host.setTargetTransferMode(new BlockPos(action.x(), action.y(), action.z()),
                TargetTransferMode.fromOrdinal(action.mode()));
        broadcastChanges();
    }

    private record TargetAction(@Nullable Long targetSnapshotRevision, @Nullable String dimensionId,
                                @Nullable Integer x, @Nullable Integer y, @Nullable Integer z,
                                @Nullable Boolean teleport) {}

    private record TargetTransferModeAction(String dimensionId, int x, int y, int z, int mode) {}

    private record TargetSnapshotKey(ResourceLocation itemId, String displayName, int count,
                                     ResourceLocation dimensionId, BlockPos pos, TargetKind kind,
                                     TargetTransferMode transferMode, int channelConnections,
                                     boolean hasAeTarget, boolean hasEnergyTarget,
                                     boolean canExtractFe, boolean canReceiveFe) {

        private static TargetSnapshotKey fromSummary(BoundTargetSummary summary) {
            TargetTransferInfo transferInfo = summary.transferInfo();
            return new TargetSnapshotKey(summary.itemId(), summary.displayName(), summary.count(), summary.dimensionId(),
                    summary.pos(), summary.kind(), summary.transferMode(), transferInfo.channelConnections(),
                    transferInfo.hasAeTarget(), transferInfo.hasEnergyTarget(), transferInfo.canExtractFe(),
                    transferInfo.canReceiveFe());
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
}
