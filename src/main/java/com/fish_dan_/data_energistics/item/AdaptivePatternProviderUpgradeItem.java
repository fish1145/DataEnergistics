package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.parts.crafting.PatternProviderPart;

public class AdaptivePatternProviderUpgradeItem extends BlockAndPartUpgradeItem {

    public AdaptivePatternProviderUpgradeItem(Properties properties) {
        super(properties);
        addBlock(
                PatternProviderBlockEntity.class,
                DEBlocks.ADAPTIVE_PATTERN_PROVIDER::get,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY::get);
        addPart(PatternProviderPart.class, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART::get);
    }
}
