package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.PoweredAxeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.item.powered.PoweredHoeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredPickaxeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredShovelItem;
import com.fish_dan_.data_energistics.item.powered.PoweredSwordItem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

final class PoweredToolAttributeModifierHandler {

    private static final ResourceLocation POWERED_TOOL_SPEED_CARD_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "powered_tool_speed_card_attack_speed");
    private static final ResourceLocation POWERED_TOOL_SABER_ENERGY_ATTACK_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "powered_tool_saber_energy_attack_damage");

    @SubscribeEvent
    public void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PoweredEnergyItem poweredEnergyItem)) {
            return;
        }

        if (!(stack.getItem() instanceof PoweredSwordItem || stack.getItem() instanceof PoweredAxeItem || stack.getItem() instanceof PoweredPickaxeItem || stack.getItem() instanceof PoweredHoeItem || stack.getItem() instanceof PoweredShovelItem)) {
            return;
        }

        double attackSpeedBonus = poweredEnergyItem.getSpeedCardAttackSpeedBonus(stack);
        if (attackSpeedBonus > 0.0D) {
            event.addModifier(
                    Attributes.ATTACK_SPEED,
                    new AttributeModifier(
                            POWERED_TOOL_SPEED_CARD_ATTACK_SPEED_ID,
                            attackSpeedBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }

        double baseAttackDamage = event.getModifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .filter(entry -> entry.slot().test(EquipmentSlot.MAINHAND))
                .mapToDouble(entry -> entry.modifier().amount())
                .sum();
        double saberEnergyAttackDamageBonus = poweredEnergyItem.getSaberEnergyAttackDamageBonus(stack, baseAttackDamage);
        if (saberEnergyAttackDamageBonus > 0.0D) {
            event.addModifier(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            POWERED_TOOL_SABER_ENERGY_ATTACK_DAMAGE_ID,
                            saberEnergyAttackDamageBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
    }
}
