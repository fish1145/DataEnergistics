package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import org.jetbrains.annotations.Nullable;

public class DataSanctumStatusMenu extends AEBaseMenu {

    private static final String ACTION_SET_MODE = "set_mode";

    @Nullable
    private final DataSanctumBlockEntity host;

    @GuiSync(850)
    public boolean online;
    @GuiSync(851)
    public boolean active;
    @GuiSync(852)
    public int mode;
    @GuiSync(853)
    public boolean networkPortAvailable;

    public DataSanctumStatusMenu(int id, Inventory playerInventory, @Nullable DataSanctumBlockEntity host) {
        super(ModMenus.DATA_SANCTUM_STATUS.get(), id, playerInventory, host);
        this.host = host;
        createPlayerInventorySlots(playerInventory);
        registerClientAction(ACTION_SET_MODE, Integer.class, this::setMode);
    }

    @Override
    public void broadcastChanges() {
        if (this.host != null) {
            BlockState state = this.host.getBlockState();
            this.online = this.host.getMainNode().isOnline();
            this.active = getActive(state);
            this.mode = this.host.getMode();
            this.networkPortAvailable = hasNetworkPortPart(this.host, state);
        } else {
            this.online = false;
            this.active = false;
            this.mode = 0;
            this.networkPortAvailable = false;
        }

        super.broadcastChanges();
    }

    private static boolean getActive(BlockState state) {
        return state.hasProperty(DataSanctumBlock.ACTIVE) && state.getValue(DataSanctumBlock.ACTIVE);
    }

    public void sendSetMode(int mode) {
        sendClientAction(ACTION_SET_MODE, mode);
    }

    private void setMode(Integer mode) {
        if (mode == null || this.host == null) {
            return;
        }

        this.host.setMode(mode);
        this.mode = Math.max(0, Math.min(2, mode));
        broadcastChanges();
    }

    private static boolean hasNetworkPortPart(DataSanctumBlockEntity host, BlockState state) {
        Level level = host.getLevel();
        if (level == null || !state.hasProperty(DataSanctumBlock.FACING)) {
            return false;
        }

        Direction facing = state.getValue(DataSanctumBlock.FACING);
        for (BlockPos partPos : DataSanctumBlockEntity.iterFootprint(host.getBlockPos(), facing)) {
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(state.getBlock()) && DataSanctumBlockEntity.isNetworkPortPart(partState)) {
                return true;
            }
        }
        return false;
    }
}
