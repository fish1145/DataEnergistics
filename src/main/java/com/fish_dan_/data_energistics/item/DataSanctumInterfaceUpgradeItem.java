package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import appeng.blockentity.misc.InterfaceBlockEntity;

public class DataSanctumInterfaceUpgradeItem extends BlockAndPartUpgradeItem {

    public DataSanctumInterfaceUpgradeItem(Properties properties) {
        super(properties);
        addBlock(
                InterfaceBlockEntity.class,
                ModBlocks.DATA_SANCTUM_INTERFACE.get(),
                ModBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get());
    }
}
