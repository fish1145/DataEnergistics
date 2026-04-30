package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class OreDataCarrierData {
    private static final String TAG_ORE_ITEM = "ore_item";
    private static final String TAG_REQUIRED_AMOUNT = "required_amount";
    private static final String TAG_COLLECTED_AMOUNT = "collected_amount";
    private static final float REQUIRED_AMOUNT = 4096.0F;
    private OreDataCarrierData() {
    }

    public static boolean hasRecordedOre(ItemStack stack) {
        return getOreItemId(stack) != null;
    }

    public static boolean recordFirstOre(ItemStack stack, ItemStack oreStack) {
        if (hasRecordedOre(stack) || oreStack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(oreStack.getItem());
        if (itemId == null) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_ORE_ITEM, itemId.toString());
            tag.putFloat(TAG_REQUIRED_AMOUNT, REQUIRED_AMOUNT);
            tag.putFloat(TAG_COLLECTED_AMOUNT, 0.0F);
        });
        return true;
    }

    public static boolean addCollectedOre(ItemStack stack, float amount) {
        if (amount <= 0.0F || !hasRecordedOre(stack)) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            float required = Math.max(1.0F, tag.getFloat(TAG_REQUIRED_AMOUNT));
            float current = Math.max(0.0F, tag.getFloat(TAG_COLLECTED_AMOUNT));
            tag.putFloat(TAG_COLLECTED_AMOUNT, Mth.clamp(current + amount, 0.0F, required));
        });
        return true;
    }

    public static float getRequiredAmount(ItemStack stack) {
        return Math.max(0.0F, getTag(stack).getFloat(TAG_REQUIRED_AMOUNT));
    }

    public static float getCollectedAmount(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        float required = Math.max(0.0F, tag.getFloat(TAG_REQUIRED_AMOUNT));
        float collected = Math.max(0.0F, tag.getFloat(TAG_COLLECTED_AMOUNT));
        return required > 0 ? Math.min(collected, required) : collected;
    }

    public static boolean isComplete(ItemStack stack) {
        float required = getRequiredAmount(stack);
        return required > 0.0F && getCollectedAmount(stack) + 0.0001F >= required;
    }

    @Nullable
    public static ResourceLocation getOreItemId(ItemStack stack) {
        String rawId = getTag(stack).getString(TAG_ORE_ITEM);
        if (rawId.isEmpty()) {
            return null;
        }

        return ResourceLocation.tryParse(rawId);
    }

    public static Component getOreDisplayName(ItemStack stack) {
        ResourceLocation itemId = getOreItemId(stack);
        if (itemId == null) {
            return Component.translatable("item.data_energistics.carrier.target_unknown");
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return Component.literal(itemId.toString());
        }

        return new ItemStack(item).getHoverName();
    }

    public static ItemStack createCompletedCarrier(ItemStack source) {
        ItemStack result = new ItemStack(ModItems.ORE_DATA_CARRIER.get());
        CompoundTag tag = getTag(source);
        if (!tag.isEmpty()) {
            tag.putFloat(TAG_COLLECTED_AMOUNT, Math.max(tag.getFloat(TAG_COLLECTED_AMOUNT), tag.getFloat(TAG_REQUIRED_AMOUNT)));
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return result;
    }

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
