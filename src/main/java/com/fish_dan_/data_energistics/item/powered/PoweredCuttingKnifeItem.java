package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.util.ConfigInventory;

import java.util.List;
import java.util.Optional;

public class PoweredCuttingKnifeItem extends PoweredItem implements IBasicCellItem {

    private static final int DATA_FLOW_BYTES = 256;
    private static final int SABER_ENERGY_DATA_FLOW_BYTES = 512;
    private static final int BYTES_PER_TYPE = 1;
    private static final int TOTAL_TYPES = 1;

    public PoweredCuttingKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        this.addCellInformationToTooltip(stack, lines);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return this.getCellTooltipImage(stack);
    }

    @Override
    public AEKeyType getKeyType() {
        return DigitalizationKeyType.TYPE;
    }

    @Override
    public int getBytes(ItemStack stack) {
        return this.getUpgrades(stack).getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()) > 0 ? SABER_ENERGY_DATA_FLOW_BYTES : DATA_FLOW_BYTES;
    }

    @Override
    public int getBytesPerType(ItemStack stack) {
        return BYTES_PER_TYPE;
    }

    @Override
    public int getTotalTypes(ItemStack stack) {
        return TOTAL_TYPES;
    }

    @Override
    public boolean isBlackListed(ItemStack stack, AEKey requestedAddition) {
        return requestedAddition != DataFlowKey.of();
    }

    @Override
    public double getIdleDrain() {
        return 0.0D;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return ConfigInventory.emptyTypes();
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, this.getMaxUpgradeSlots(stack), this::onUpgradesChanged);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode fuzzyMode) {}
}
