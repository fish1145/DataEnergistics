package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.item.MobDataCarrierItemData;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class BiologyDataCarrierData {

    private static final String TAG_ENTITY_TYPE = "entity_type";
    private static final String TAG_REQUIRED_DAMAGE = "required_damage";
    private static final String TAG_COLLECTED_DAMAGE = "collected_damage";

    private BiologyDataCarrierData() {}

    public static boolean hasRecordedEntity(ItemStack stack) {
        return getEntityTypeId(stack) != null;
    }

    public static boolean recordFirstEntity(ItemStack stack, LivingEntity entity) {
        if (hasRecordedEntity(stack)) {
            return false;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null || !canRecordEntity(entityId)) {
            return false;
        }

        stack.set(DEDataComponents.MOB_DATA_CARRIER.get(), new MobDataCarrierItemData(
                entityId,
                DataEnergisticsConfiguration.INSTANCE.dataExtractor().mobRequiredDamage(),
                0.0F));
        return true;
    }

    public static boolean addCollectedDamage(ItemStack stack, float damage) {
        if (damage <= 0.0F || !hasRecordedEntity(stack)) {
            return false;
        }

        MobDataCarrierItemData data = getData(stack);
        if (data == null) {
            return false;
        }
        stack.set(DEDataComponents.MOB_DATA_CARRIER.get(), data.withAddedCollectedDamage(damage));
        return true;
    }

    public static float getRequiredDamage(ItemStack stack) {
        MobDataCarrierItemData data = getData(stack);
        return data == null ? 0.0F : Math.max(0.0F, data.requiredDamage());
    }

    public static float getCollectedDamage(ItemStack stack) {
        MobDataCarrierItemData data = getData(stack);
        if (data == null) {
            return 0.0F;
        }
        float required = Math.max(0.0F, data.requiredDamage());
        float collected = Math.max(0.0F, data.collectedDamage());
        return required > 0.0F ? Math.min(collected, required) : collected;
    }

    public static boolean isComplete(ItemStack stack) {
        float required = getRequiredDamage(stack);
        return required > 0.0F && getCollectedDamage(stack) + 0.0001F >= required;
    }

    @Nullable
    public static ResourceLocation getEntityTypeId(ItemStack stack) {
        MobDataCarrierItemData data = getData(stack);
        return data == null ? null : data.entityType();
    }

    public static Component getEntityDisplayName(ItemStack stack) {
        ResourceLocation entityId = getEntityTypeId(stack);
        if (entityId == null) {
            return Component.translatable("item.data_energistics.carrier.target_unknown");
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            return Component.literal(entityId.toString());
        }

        return entityType.getDescription();
    }

    public static ItemStack createCompletedCarrier(ItemStack source) {
        ItemStack result = new ItemStack(DEItems.MOB_DATA_CARRIER.get());
        MobDataCarrierItemData data = getData(source);
        if (data != null) {
            result.set(DEDataComponents.MOB_DATA_CARRIER.get(), data.asComplete());
        }
        return result;
    }

    public static String formatAmount(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static @Nullable MobDataCarrierItemData getData(ItemStack stack) {
        MobDataCarrierItemData data = stack.get(DEDataComponents.MOB_DATA_CARRIER.get());
        if (data != null) {
            return data;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String rawId = tag.getString(TAG_ENTITY_TYPE);
        ResourceLocation entityType = rawId.isEmpty() ? null : ResourceLocation.tryParse(rawId);
        if (entityType == null) {
            return null;
        }
        return new MobDataCarrierItemData(
                entityType,
                Math.max(0.0F, tag.getFloat(TAG_REQUIRED_DAMAGE)),
                Math.max(0.0F, tag.getFloat(TAG_COLLECTED_DAMAGE)));
    }

    public static boolean canRecordEntity(@Nullable ResourceLocation entityId) {
        if (entityId == null) {
            return false;
        }
        return !DataEnergisticsConfiguration.INSTANCE.dataExtractor().mobDataBlacklist().contains(entityId);
    }
}
