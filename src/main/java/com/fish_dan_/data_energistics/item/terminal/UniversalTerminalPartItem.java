package com.fish_dan_.data_energistics.item.terminal;

import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalData;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import appeng.items.parts.PartItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class UniversalTerminalPartItem extends PartItem<UniversalTerminalPart> {

    public UniversalTerminalPartItem(Properties properties) {
        super(properties, UniversalTerminalPart.class, UniversalTerminalPart::new);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
        tooltip.addAll(UniversalTerminalData.getInstalledTerminalLines(stack, context.registries()));
    }
}
