package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.item.OreDataCarrierItemData;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.Nullable;

public final class OreDataCarrierData {

    private static final String TAG_ORE_ITEM = "ore_item";
    private static final String TAG_REQUIRED_AMOUNT = "required_amount";
    private static final String TAG_COLLECTED_AMOUNT = "collected_amount";

    private OreDataCarrierData() {}

    public static boolean hasRecordedOre(ItemStack stack) {
        return getOreItemId(stack) != null;
    }

    public static boolean recordFirstOre(ItemStack stack, ItemStack oreStack) {
        if (hasRecordedOre(stack) || oreStack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(oreStack.getItem());
        if (itemId == null || !canRecordOre(itemId)) {
            return false;
        }

        stack.set(DEDataComponents.ORE_DATA_CARRIER.get(), new OreDataCarrierItemData(
                itemId,
                DataEnergisticsConfiguration.INSTANCE.dataExtractor().oreRequiredAmount(),
                0.0F));
        return true;
    }

    public static boolean addCollectedOre(ItemStack stack, float amount) {
        if (amount <= 0.0F || !hasRecordedOre(stack)) {
            return false;
        }

        OreDataCarrierItemData data = getData(stack);
        if (data == null) {
            return false;
        }
        stack.set(DEDataComponents.ORE_DATA_CARRIER.get(), data.withAddedCollectedAmount(amount));
        return true;
    }

    public static float getRequiredAmount(ItemStack stack) {
        OreDataCarrierItemData data = getData(stack);
        return data == null ? 0.0F : Math.max(0.0F, data.requiredAmount());
    }

    public static float getCollectedAmount(ItemStack stack) {
        OreDataCarrierItemData data = getData(stack);
        if (data == null) {
            return 0.0F;
        }
        float required = Math.max(0.0F, data.requiredAmount());
        float collected = Math.max(0.0F, data.collectedAmount());
        return required > 0 ? Math.min(collected, required) : collected;
    }

    public static void setRequiredAmount(ItemStack stack, float requiredAmount) {
        if (stack.isEmpty()) {
            return;
        }

        OreDataCarrierItemData data = getData(stack);
        if (data != null) {
            stack.set(DEDataComponents.ORE_DATA_CARRIER.get(), data.withRequiredAmount(requiredAmount));
        }
    }

    public static boolean isComplete(ItemStack stack) {
        float required = getRequiredAmount(stack);
        return required > 0.0F && getCollectedAmount(stack) + 0.0001F >= required;
    }

    @Nullable
    public static ResourceLocation getOreItemId(ItemStack stack) {
        OreDataCarrierItemData data = getData(stack);
        return data == null ? null : data.oreItem();
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
        ItemStack result = new ItemStack(DEItems.ORE_DATA_CARRIER.get());
        OreDataCarrierItemData data = getData(source);
        if (data != null) {
            result.set(DEDataComponents.ORE_DATA_CARRIER.get(), data.asComplete());
        }
        return result;
    }

    private static @Nullable OreDataCarrierItemData getData(ItemStack stack) {
        OreDataCarrierItemData data = stack.get(DEDataComponents.ORE_DATA_CARRIER.get());
        if (data != null) {
            return data;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String rawId = tag.getString(TAG_ORE_ITEM);
        ResourceLocation oreItem = rawId.isEmpty() ? null : ResourceLocation.tryParse(rawId);
        if (oreItem == null) {
            return null;
        }
        return new OreDataCarrierItemData(
                oreItem,
                Math.max(0.0F, tag.getFloat(TAG_REQUIRED_AMOUNT)),
                Math.max(0.0F, tag.getFloat(TAG_COLLECTED_AMOUNT)));
    }

    public static boolean canRecordOre(@Nullable ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }
        return !DataEnergisticsConfiguration.INSTANCE.dataExtractor().oreDataBlacklist().contains(itemId);
    }
}
