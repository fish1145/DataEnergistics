package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.client.screen.DataDistributionTowerScreen;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jetbrains.annotations.Nullable;

public class DataDistributionTowerMenu extends AEBaseMenu {

    private static final String ACTION_FOCUS_TARGET = "focus_target";
    private static final String ACTION_SET_RANGE_VISIBLE = "set_range_visible";
    private static final String ACTION_SET_CONNECTION_MODE = "set_connection_mode";
    @Nullable
    private final DataDistributionTowerBlockEntity host;
    private final RestrictedInputSlot boosterSlot;

    @GuiSync(730)
    public int usedChannels;
    @GuiSync(731)
    public int maxChannels;
    @GuiSync(732)
    public int availableFe;
    @GuiSync(733)
    public int chunkRadius;
    @GuiSync(734)
    public boolean online;
    @GuiSync(735)
    public boolean rangeVisible;
    @GuiSync(736)
    public int boundTargetCount;
    @GuiSync(737)
    public String boundTargets = "";
    @GuiSync(738)
    public String boundTargetIcons = "";
    @GuiSync(739)
    public String boundTargetMeta = "";
    @GuiSync(740)
    public String boundTargetKinds = "";
    @GuiSync(741)
    public int connectionMode = ConnectionMode.AE_AND_FE.ordinal();

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
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        if (this.isClientSide() && Minecraft.getInstance().screen instanceof DataDistributionTowerScreen screen) {
            screen.refreshFromServer();
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
            this.boundTargetCount = tower.getBoundTargetCount();
            var summaries = tower.getBoundTargetSummaries(64);
            this.boundTargets = String.join("\n", summaries.stream()
                    .map(summary -> summary.count() > 1 ? summary.displayName() + " x" + summary.count() + " (" + summary.kind().name() + ")" : summary.displayName() + " (" + summary.kind().name() + ")")
                    .toList());
            this.boundTargetIcons = String.join("\n", summaries.stream()
                    .map(summary -> summary.itemId().toString())
                    .toList());
            this.boundTargetMeta = String.join("\n", summaries.stream()
                    .map(summary -> summary.dimensionId() + "|" + summary.pos().getX() + "|" + summary.pos().getY() + "|" + summary.pos().getZ())
                    .toList());
            this.boundTargetKinds = String.join("\n", summaries.stream()
                    .map(summary -> summary.kind().name())
                    .toList());
        }

        super.broadcastChanges();
    }

    public void sendFocusTarget(String dimensionId, int x, int y, int z, boolean teleport) {
        sendClientAction(ACTION_FOCUS_TARGET, new TargetAction(dimensionId, x, y, z, teleport));
    }

    public void sendSetRangeVisible(boolean visible) {
        sendClientAction(ACTION_SET_RANGE_VISIBLE, visible);
    }

    public void sendSetConnectionMode(ConnectionMode connectionMode) {
        sendClientAction(ACTION_SET_CONNECTION_MODE,
                (connectionMode == null ? ConnectionMode.AE_AND_FE : connectionMode).ordinal());
    }

    private void onFocusTarget(TargetAction action) {
        if (action == null) {
            return;
        }

        var levelKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(action.dimensionId()));

        getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.data_energistics.data_distribution_tower.target",
                action.x(),
                action.y(),
                action.z(),
                action.dimensionId()));

        if (!action.teleport()) {
            return;
        }

        if (!getPlayer().hasPermissions(2)) {
            getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(
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
        getPlayer().teleportTo(targetLevel, action.x() + 0.5, action.y() + 1.1, action.z() + 0.5,
                java.util.Set.of(), getPlayer().getYRot(), getPlayer().getXRot());
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

    private record TargetAction(String dimensionId, int x, int y, int z, boolean teleport) {}
}
