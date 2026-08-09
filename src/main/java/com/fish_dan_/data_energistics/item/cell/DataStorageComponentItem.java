package com.fish_dan_.data_energistics.item.cell;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.items.IStorageComponent;

public class DataStorageComponentItem extends Item implements IStorageComponent {

    private final int storageInKb;

    public DataStorageComponentItem(Properties properties, int storageInKb) {
        super(properties);
        this.storageInKb = storageInKb;
    }

    @Override
    public int getBytes(ItemStack itemStack) {
        return this.storageInKb * 1024;
    }

    @Override
    public boolean isStorageComponent(ItemStack itemStack) {
        return true;
    }
}
