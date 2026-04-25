package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.items.tools.powered.PortableCellItem;
import appeng.items.storage.StorageTier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;

import java.lang.reflect.Field;

public class DataFlowPortableCellItem extends PortableCellItem {
    public DataFlowPortableCellItem(StorageTier tier, Item.Properties properties, int color) {
        super(DataFlowKeyType.TYPE, 1, portableItemCellMenu(), tier, properties.stacksTo(1), color);
    }

    @Override
    public int getBytes(ItemStack stack) {
        return super.getBytes(stack);
    }

    private static MenuType<?> portableItemCellMenu() {
        try {
            Field field = AbstractPortableCell.class.getDeclaredField("menuType");
            field.setAccessible(true);
            return (MenuType<?>) field.get(AEItems.PORTABLE_ITEM_CELL1K.get());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access AE2 portable item cell menu type", e);
        }
    }
}
