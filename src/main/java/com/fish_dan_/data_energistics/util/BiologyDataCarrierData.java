package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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

    private BiologyDataCarrierData() {
    }

    public static boolean hasRecordedEntity(ItemStack stack) {
        return getEntityTypeId(stack) != null;
    }

    public static boolean recordFirstEntity(ItemStack stack, LivingEntity entity) {
        if (hasRecordedEntity(stack)) {
            return false;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null) {
            return false;
        }

        float requiredDamage = Math.max(1.0F, entity.getMaxHealth() * 64.0F);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_ENTITY_TYPE, entityId.toString());
            tag.putFloat(TAG_REQUIRED_DAMAGE, requiredDamage);
            tag.putFloat(TAG_COLLECTED_DAMAGE, 0.0F);
        });
        return true;
    }

    public static boolean addCollectedDamage(ItemStack stack, float damage) {
        if (damage <= 0.0F || !hasRecordedEntity(stack)) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            float required = Math.max(1.0F, tag.getFloat(TAG_REQUIRED_DAMAGE));
            float current = tag.getFloat(TAG_COLLECTED_DAMAGE);
            tag.putFloat(TAG_COLLECTED_DAMAGE, Mth.clamp(current + damage, 0.0F, required));
        });
        return true;
    }

    public static float getRequiredDamage(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return Math.max(0.0F, tag.getFloat(TAG_REQUIRED_DAMAGE));
    }

    public static float getCollectedDamage(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        float required = Math.max(0.0F, tag.getFloat(TAG_REQUIRED_DAMAGE));
        float collected = Math.max(0.0F, tag.getFloat(TAG_COLLECTED_DAMAGE));
        return required > 0.0F ? Math.min(collected, required) : collected;
    }

    public static boolean isComplete(ItemStack stack) {
        float required = getRequiredDamage(stack);
        return required > 0.0F && getCollectedDamage(stack) + 0.0001F >= required;
    }

    @Nullable
    public static ResourceLocation getEntityTypeId(ItemStack stack) {
        String rawId = getTag(stack).getString(TAG_ENTITY_TYPE);
        if (rawId.isEmpty()) {
            return null;
        }

        return ResourceLocation.tryParse(rawId);
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
        ItemStack result = new ItemStack(ModItems.BIOLOGY_DATA_CARRIER.get());
        CompoundTag tag = getTag(source);
        if (!tag.isEmpty()) {
            tag.putFloat(TAG_COLLECTED_DAMAGE, Math.max(tag.getFloat(TAG_COLLECTED_DAMAGE), tag.getFloat(TAG_REQUIRED_DAMAGE)));
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return result;
    }

    public static String formatAmount(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
