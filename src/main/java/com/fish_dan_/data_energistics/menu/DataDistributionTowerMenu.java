package com.fish_dan_.data_energistics.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.client.screen.DataDistributionTowerScreen;
import com.fish_dan_.data_energistics.registry.ModMenus;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

public class DataDistributionTowerMenu extends UpgradeableMenu<DataDistributionTowerBlockEntity> {
    private static final String ACTION_FOCUS_TARGET = "focus_target";

    @GuiSync(730)
    public int usedChannels;
    @GuiSync(731)
    public int maxChannels;
    @GuiSync(732)
    public int availableFe;
    @GuiSync(733)
    public int range;
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

    public DataDistributionTowerMenu(int id, Inventory playerInventory, DataDistributionTowerBlockEntity host) {
        super(ModMenus.DATA_DISTRIBUTION_TOWER.get(), id, playerInventory, host);
        registerClientAction(ACTION_FOCUS_TARGET, TargetAction.class, this::onFocusTarget);
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
        if (getTarget() instanceof DataDistributionTowerBlockEntity tower) {
            this.usedChannels = tower.getUsedChannelCount();
            this.maxChannels = tower.getMaxChannelCount();
            this.availableFe = tower.getAvailableFeForUi();
            this.range = tower.getConfiguredRange();
            this.online = tower.isNetworkNodeOnline();
            this.rangeVisible = tower.isRangeDisplayEnabled();
            this.boundTargetCount = tower.getBoundTargetCount();
            var summaries = tower.getBoundTargetSummaries(64);
            this.boundTargets = String.join("\n", summaries.stream()
                    .map(summary -> summary.count() > 1
                            ? summary.displayName() + " x" + summary.count() + " (" + summary.kind().name() + ")"
                            : summary.displayName() + " (" + summary.kind().name() + ")")
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

    private void onFocusTarget(TargetAction action) {
        if (action == null) {
            return;
        }

        var levelKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(action.dimensionId())
        );

        getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.data_energistics.data_distribution_tower.target",
                action.x(),
                action.y(),
                action.z(),
                action.dimensionId()
        ));

        if (!action.teleport()) {
            return;
        }

        if (!getPlayer().hasPermissions(2)) {
            getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.data_energistics.data_distribution_tower.teleport_requires_cheats"
            ), true);
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

    private record TargetAction(String dimensionId, int x, int y, int z, boolean teleport) {
    }
}
