package com.fish_dan_.data_energistics.item;

import appeng.items.parts.PartItem;
import com.fish_dan_.data_energistics.part.DataRipperPart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class DataRipperPartItem extends PartItem<DataRipperPart> {
    public DataRipperPartItem(Properties properties) {
        super(properties, DataRipperPart.class, DataRipperPart::new);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
        tooltip.add(Component.translatable("item.data_energistics.data_ripper.tip.requirement"));
        tooltip.add(Component.translatable("item.data_energistics.data_ripper.tip.max"));
        tooltip.add(Component.translatable("item.data_energistics.data_ripper.tip.energy"));
    }
}
