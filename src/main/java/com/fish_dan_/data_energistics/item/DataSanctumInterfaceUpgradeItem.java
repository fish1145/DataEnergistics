package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.parts.misc.InterfacePart;

public class DataSanctumInterfaceUpgradeItem extends BlockAndPartUpgradeItem {

    public DataSanctumInterfaceUpgradeItem(Properties properties) {
        super(properties);
        addBlock(
                InterfaceBlockEntity.class,
                DEBlocks.DATA_SANCTUM_INTERFACE::get,
                DEBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY::get);
        addPart(InterfacePart.class, DEItems.DATA_SANCTUM_INTERFACE_PART::get);
    }
}
