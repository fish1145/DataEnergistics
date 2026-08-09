package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.parts.misc.InterfacePart;

public class DataSanctumInterfaceUpgradeItem extends BlockAndPartUpgradeItem {

    public DataSanctumInterfaceUpgradeItem(Properties properties) {
        super(properties);
        addBlock(
                InterfaceBlockEntity.class,
                DEBlocks.DATA_SANCTUM_INTERFACE::get,
                ModBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY::get);
        addPart(InterfacePart.class, DEItems.DATA_SANCTUM_INTERFACE_PART::get);
    }
}
