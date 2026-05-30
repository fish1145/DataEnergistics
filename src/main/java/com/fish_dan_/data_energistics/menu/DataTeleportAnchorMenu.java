package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;

import java.util.List;

public class DataTeleportAnchorMenu extends AEBaseMenu {

    private static final String ACTION_SET_REDSTONE_CONTROL = "set_redstone_control";
    private static final String ACTION_RECORD_CURRENT_TARGET = "record_current_target";
    private static final String ACTION_TELEPORT_TO_ANCHOR = "teleport_to_anchor";

    private final DataTeleportAnchorBlockEntity host;

    @GuiSync(800)
    public boolean online;
    @GuiSync(801)
    public boolean redstoneControlled;
    @GuiSync(802)
    public String anchorDimension = "";
    @GuiSync(803)
    public int anchorX;
    @GuiSync(804)
    public int anchorY;
    @GuiSync(805)
    public int anchorZ;
    @GuiSync(806)
    public boolean hasTarget;
    @GuiSync(807)
    public String targetDimension = "";
    @GuiSync(808)
    public int targetX;
    @GuiSync(809)
    public int targetY;
    @GuiSync(810)
    public int targetZ;
    @GuiSync(811)
    public int availableAnchorCount;
    @GuiSync(812)
    public String availableAnchors = "";

    public DataTeleportAnchorMenu(int id, Inventory playerInventory, DataTeleportAnchorBlockEntity host) {
        super(ModMenus.DATA_TELEPORT_ANCHOR.get(), id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_SET_REDSTONE_CONTROL, Boolean.class, this::setRedstoneControlled);
        registerClientAction(ACTION_RECORD_CURRENT_TARGET, Boolean.class, this::recordCurrentTarget);
        registerClientAction(ACTION_TELEPORT_TO_ANCHOR, AnchorAction.class, this::teleportToAnchor);
    }

    @Override
    public void broadcastChanges() {
        this.online = this.host.isOnline();
        this.redstoneControlled = this.host.isRedstoneControlled();
        this.anchorDimension = this.host.getAnchorDimensionId();
        this.anchorX = this.host.getBlockEntity().getBlockPos().getX();
        this.anchorY = this.host.getBlockEntity().getBlockPos().getY();
        this.anchorZ = this.host.getBlockEntity().getBlockPos().getZ();
        this.hasTarget = this.host.hasTarget();
        if (this.hasTarget) {
            this.targetDimension = this.host.getTargetDimension().toString();
            this.targetX = this.host.getTargetPos().getX();
            this.targetY = this.host.getTargetPos().getY();
            this.targetZ = this.host.getTargetPos().getZ();
        } else {
            this.targetDimension = "";
            this.targetX = 0;
            this.targetY = 0;
            this.targetZ = 0;
        }
        List<DataTeleportAnchorBlockEntity.AnchorSummary> anchors = this.host.getAvailableAnchors();
        this.availableAnchorCount = anchors.size();
        this.availableAnchors = String.join("\n", anchors.stream()
                .map(summary -> escape(summary.name()) + "|" + escape(summary.dimensionId()) + "|" + summary.pos().getX() + "|" + summary.pos().getY() + "|" + summary.pos().getZ())
                .toList());
        super.broadcastChanges();
    }

    public void sendSetRedstoneControlled(boolean enabled) {
        sendClientAction(ACTION_SET_REDSTONE_CONTROL, enabled);
    }

    public void sendRecordCurrentTarget() {
        sendClientAction(ACTION_RECORD_CURRENT_TARGET, true);
    }

    public void sendTeleportToAnchor(String dimensionId, int x, int y, int z) {
        sendClientAction(ACTION_TELEPORT_TO_ANCHOR, new AnchorAction(dimensionId, x, y, z));
    }

    private void setRedstoneControlled(Boolean enabled) {
        if (enabled == null) {
            return;
        }

        this.redstoneControlled = this.host.setRedstoneControlled(enabled);
        broadcastChanges();
    }

    private void recordCurrentTarget(Boolean ignored) {
        if (!(getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }

        this.host.recordTarget(serverPlayer);
        getPlayer().displayClientMessage(Component.translatable(
                "message.data_energistics.data_teleport_anchor.target_recorded",
                this.host.getTargetPos().getX(),
                this.host.getTargetPos().getY(),
                this.host.getTargetPos().getZ(),
                this.host.getTargetDimension().toString()), true);
        broadcastChanges();
    }

    private void teleportToAnchor(AnchorAction action) {
        if (action == null) {
            return;
        }

        DataTeleportAnchorBlockEntity.TeleportResult result = this.host.teleportEntitiesTo(
                ResourceLocation.parse(action.dimensionId()),
                new net.minecraft.core.BlockPos(action.x(), action.y(), action.z()));

        Component message = switch (result.status()) {
            case SUCCESS -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.teleported",
                    result.entityCount(),
                    action.x(),
                    action.y(),
                    action.z(),
                    action.dimensionId());
            case SOURCE_OFFLINE -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.source_offline");
            case TARGET_OFFLINE -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.target_offline");
            case CHANNEL_MISMATCH -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.channel_mismatch");
            case SELF_TARGET -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.self_target");
            case TARGET_NOT_FOUND -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.target_missing");
            case INSUFFICIENT_POWER -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.insufficient_power");
            case NO_RECORDED_TARGET -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.no_recorded_target");
            case NO_ENTITIES -> Component.translatable(
                    "message.data_energistics.data_teleport_anchor.no_entities");
        };
        getPlayer().displayClientMessage(message, true);
        broadcastChanges();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\n", "\\n");
    }

    private record AnchorAction(String dimensionId, int x, int y, int z) {}
}
