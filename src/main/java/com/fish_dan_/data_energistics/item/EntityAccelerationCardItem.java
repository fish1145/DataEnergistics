package com.fish_dan_.data_energistics.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import appeng.items.materials.UpgradeCardItem;

public class EntityAccelerationCardItem extends UpgradeCardItem {
    public static final String NBT_MULTIPLIER = "EAS:mult";

    public EntityAccelerationCardItem(Item.Properties properties) {
        super(properties);
    }

    public ItemStack withMultiplier(byte multiplier) {
        ItemStack stack = new ItemStack(this);
        CompoundTag tag = new CompoundTag();
        tag.putByte(NBT_MULTIPLIER, multiplier);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static byte readMultiplier(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return 1;
        }

        CompoundTag tag = customData.copyTag();
        return tag.contains(NBT_MULTIPLIER) ? tag.getByte(NBT_MULTIPLIER) : 1;
    }

    public static int getCap(byte multiplier) {
        return switch (multiplier) {
            case 2 -> 8;
            case 4 -> 64;
            case 8 -> 256;
            case 16 -> 1024;
            default -> 1;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.data_energistics.entity_speed_card.x" + readMultiplier(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        byte multiplier = readMultiplier(stack);
        tooltip.add(Component.translatable("tooltip.data_energistics.entity_speed_card.multiplier", multiplier));
        tooltip.add(Component.translatable("tooltip.data_energistics.entity_speed_card.max", getCap(multiplier)));
    }
}
