package com.fish_dan_.data_energistics.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.Tool;

public abstract class AbstractPoweredTieredItem extends PoweredItem {

    private static final double TIERED_ITEM_BASE_ENERGY_PER_ACTION = 10.0D;
    private static final double TIERED_ITEM_SABER_ENERGY_CARD_EXTRA_COST = 40.0D;
    private final Tier tier;

    protected AbstractPoweredTieredItem(Tier tier, Properties properties, Tool toolComponent) {
        super(properties.component(DataComponents.TOOL, toolComponent));
        this.tier = tier;
    }

    public Tier getTier() {
        return this.tier;
    }

    @Override
    public int getEnchantmentValue() {
        return this.tier.getEnchantmentValue();
    }

    @Override
    public boolean mineBlock(ItemStack stack, net.minecraft.world.level.Level level,
                             net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos,
                             net.minecraft.world.entity.LivingEntity miningEntity) {
        return stack.has(DataComponents.TOOL);
    }

    @Override
    public double getActionEnergyCost(ItemStack stack) {
        return TIERED_ITEM_BASE_ENERGY_PER_ACTION
                + ENERGY_PER_SPEED_CARD * this.getSpeedCardCount(stack)
                + TIERED_ITEM_SABER_ENERGY_CARD_EXTRA_COST * this.getSaberEnergyCardCount(stack);
    }
}
