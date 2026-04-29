package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class BiologyDataCarrierItem extends Item {
    private final boolean completedCarrier;

    public BiologyDataCarrierItem(Properties properties, boolean completedCarrier) {
        super(properties);
        this.completedCarrier = completedCarrier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);

        if (!BiologyDataCarrierData.hasRecordedEntity(stack)) {
            return;
        }

        tooltip.add(Component.translatable(
                "item.data_energistics.carrier.target",
                BiologyDataCarrierData.getEntityDisplayName(stack)
        ));
        tooltip.add(Component.translatable(
                "item.data_energistics.carrier.progress",
                BiologyDataCarrierData.formatAmount(BiologyDataCarrierData.getCollectedDamage(stack)),
                BiologyDataCarrierData.formatAmount(BiologyDataCarrierData.getRequiredDamage(stack))
        ));

        if (this.completedCarrier) {
            tooltip.add(Component.translatable("item.data_energistics.carrier.completed"));
        }
    }
}
