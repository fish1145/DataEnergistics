package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.decor.DollVariant;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.item.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.util.LightSaberColorData;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

final class ClientItemModelPropertyRegistrar {

    private ClientItemModelPropertyRegistrar() {}

    static void register() {
        registerMatterConvergingCrossbowProperties();
        registerDataCaptureBallProperties();
        registerLightSaberProperties();
        registerDollProperties();
    }

    private static void registerMatterConvergingCrossbowProperties() {
        var item = DEItems.MATTER_CONVERGING_CROSSBOW.get();
        ItemProperties.register(item, Data_Energistics.id("loaded_special_light_saber"),
                (stack, level, entity, seed) -> {
                    ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                    return !charged.isEmpty() && MatterConvergingCrossbowItem.isSpecialLightSaberAmmo(charged.getItems().getFirst()) ? 1.0F : 0.0F;
                });
        ItemProperties.register(item, Data_Energistics.id("load_stage"),
                (stack, level, entity, seed) -> {
                    if (CrossbowItem.isCharged(stack)) {
                        return 0.67F;
                    }
                    if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) {
                        return 0.0F;
                    }

                    float progress = (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) MatterConvergingCrossbowItem.getChargeDuration(stack, entity);
                    progress = Mth.clamp(progress, 0.0F, 1.0F);
                    if (progress < 1.0F / 3.0F) {
                        return 0.0F;
                    }
                    if (progress >= 2.0F / 3.0F) {
                        return 0.67F;
                    }
                    return 0.42F;
                });
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pull"),
                (stack, level, entity, seed) -> {
                    if (entity == null) {
                        return 0.0F;
                    }
                    if (CrossbowItem.isCharged(stack)) {
                        return 0.0F;
                    }
                    return entity.getUseItem() != stack ? 0.0F : (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) MatterConvergingCrossbowItem.getChargeDuration(stack, entity);
                });
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("charged"),
                (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("firework"),
                (stack, level, entity, seed) -> {
                    var charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                    return charged != null && charged.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
                });
    }

    private static void registerDataCaptureBallProperties() {
        ItemProperties.register(DEItems.DATA_CAPTURE_BALL.get(), Data_Energistics.id("fill_level"),
                (stack, level, entity, seed) -> DataCaptureBallItem.getFillModelValue(stack));
    }

    private static void registerLightSaberProperties() {
        ItemProperties.register(DEItems.DATA_LIGHT_SABER.get(), Data_Energistics.id("powered"),
                (stack, level, entity, seed) -> isPowered(stack) ? 1.0F : 0.0F);
        ItemProperties.register(DEItems.DATA_LIGHT_SABER.get(), Data_Energistics.id("light_saber_color"),
                (stack, level, entity, seed) -> LightSaberColorData.getModelValue(stack));
        ItemProperties.register(DEItems.DATA_SANCTIFIER.get(), Data_Energistics.id("powered"),
                (stack, level, entity, seed) -> isPowered(stack) ? 1.0F : 0.0F);
    }

    private static void registerDollProperties() {
        ItemProperties.register(DEItems.FISH_DAN.get(), Data_Energistics.id("doll_variant"),
                (stack, level, entity, seed) -> DollVariant.fromStack(stack));
    }

    private static boolean isPowered(ItemStack stack) {
        return stack.getItem() instanceof PoweredEnergyItem poweredEnergyItem && poweredEnergyItem.getAECurrentPower(stack) > 0.0D;
    }
}
