package com.fish_dan_.data_energistics.menu;

import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import com.fish_dan_.data_energistics.Config;
import com.fish_dan_.data_energistics.ae2.DataRipperSettings;
import com.fish_dan_.data_energistics.part.DataRipperPart;
import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils;
import com.fish_dan_.data_energistics.util.DataRipperPowerUtils;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.fish_dan_.data_energistics.client.screen.DataRipperScreen;
import com.fish_dan_.data_energistics.registry.ModMenus;

public class DataRipperMenu extends UpgradeableMenu<DataRipperPart> {
    private final DataRipperPart logic;

    @GuiSync(716)
    public int energyCardCount;
    @GuiSync(717)
    public int effectiveSpeed = 1;
    @GuiSync(718)
    public double multiplier = 1.0D;
    @GuiSync(719)
    public boolean targetBlacklisted = false;
    @GuiSync(720)
    public YesNo networkEnergySufficient = YesNo.YES;
    @GuiSync(721)
    private YesNo accelerate = YesNo.YES;
    @GuiSync(722)
    private YesNo redstoneControl = YesNo.NO;

    public DataRipperMenu(int id, Inventory playerInventory, DataRipperPart host) {
        super(ModMenus.DATA_RIPPER.get(), id, playerInventory, host);
        this.logic = host;
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        this.updateTargetStatus();
        this.updateEffectiveSpeed();
        if (this.isClientSide()) {
            this.refreshClientGui();
        }
    }

    @Override
    public void onSlotChange(Slot slot) {
        super.onSlotChange(slot);
        if (this.isClientSide()) {
            this.updateEffectiveSpeed();
            this.refreshClientGui();
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            this.accelerate = this.logic.getConfigManager().getSetting(DataRipperSettings.ACCELERATE);
            this.redstoneControl = this.logic.getConfigManager().getSetting(DataRipperSettings.REDSTONE_CONTROL);
            this.networkEnergySufficient = this.logic.isNetworkEnergySufficient() ? YesNo.YES : YesNo.NO;
            this.energyCardCount = this.getUpgrades().getInstalledUpgrades(AEItems.ENERGY_CARD);
            this.updateTargetStatus();
            this.updateEffectiveSpeed();
        }
        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // The data ripper only exposes custom toggles, so the default AE2 config sync slots are not used here.
    }

    public YesNo getAccelerate() {
        return this.accelerate;
    }

    public YesNo getRedstoneControl() {
        return this.redstoneControl;
    }

    private void updateTargetStatus() {
        BlockEntity target = this.getTargetBlockEntity();
        if (target == null) {
            this.multiplier = 1.0D;
            this.targetBlacklisted = false;
            return;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()).toString();
        this.multiplier = DataRipperConfigParsingUtils.getMultiplierForBlock(blockId, Config.dataRipperMultipliers);
        this.targetBlacklisted = DataRipperConfigParsingUtils.isBlockBlacklisted(blockId, Config.dataRipperBlacklist);
    }

    private void updateEffectiveSpeed() {
        this.effectiveSpeed = this.targetBlacklisted ? 0 : this.logic == null ? 0 : DataRipperPowerUtils.computeProductWithCap(this.getUpgrades());
    }

    private void refreshClientGui() {
        if (Minecraft.getInstance().screen instanceof DataRipperScreen screen) {
            screen.refreshGui();
        }
    }

    private BlockEntity getTargetBlockEntity() {
        if (this.getHost() == null || this.getHost().getLevel() == null || this.getHost().getBlockEntity() == null) {
            return null;
        }

        return this.getHost().getLevel().getBlockEntity(this.getHost().getBlockEntity().getBlockPos().relative(this.getHost().getSide()));
    }
}
