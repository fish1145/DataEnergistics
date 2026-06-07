package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.parts.crafting.PatternProviderPart;

public class AdaptivePatternProviderUpgradeItem extends BlockAndPartUpgradeItem {

    public AdaptivePatternProviderUpgradeItem(Properties properties) {
        super(properties);
        addBlock(
                PatternProviderBlockEntity.class,
                ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(),
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get());
        addPart(PatternProviderPart.class, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART::get);
    }
}
