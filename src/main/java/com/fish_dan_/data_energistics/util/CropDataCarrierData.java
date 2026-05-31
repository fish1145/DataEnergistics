package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.config.DataExtractorConfig;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class CropDataCarrierData {

    private static final String TAG_CROP_ITEM = "crop_item";
    private static final String TAG_SOURCE_BLOCK = "source_block";
    private static final String TAG_LOOT_TABLE = "loot_table";
    private static final String TAG_REQUIRED_AMOUNT = "required_amount";
    private static final String TAG_COLLECTED_AMOUNT = "collected_amount";
    private static final String TREE_LOOT_TABLE_PREFIX = "mimetic_tree/";

    private static final TagKey<Item> TAG_COMMON_SEEDS = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:seeds"));
    private static final TagKey<Item> TAG_COMMON_CROPS = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:crops"));

    private static final Map<Item, Block> CROP_TO_BLOCK = Map.ofEntries(
            Map.entry(Items.WHEAT_SEEDS, Blocks.WHEAT),
            Map.entry(Items.CARROT, Blocks.CARROTS),
            Map.entry(Items.POTATO, Blocks.POTATOES),
            Map.entry(Items.BEETROOT_SEEDS, Blocks.BEETROOTS),
            Map.entry(Items.NETHER_WART, Blocks.NETHER_WART),
            Map.entry(Items.SWEET_BERRIES, Blocks.SWEET_BERRY_BUSH));

    private CropDataCarrierData() {}

    public static boolean hasRecordedCrop(ItemStack stack) {
        return getCropItemId(stack) != null;
    }

    public static boolean recordFirstCrop(ItemStack stack, ItemStack cropStack) {
        if (hasRecordedCrop(stack) || cropStack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = getRecordedCropItemId(cropStack);
        if (itemId == null || !canRecordCrop(itemId)) {
            return false;
        }

        ResourceLocation sourceBlockId = deriveSourceBlockId(itemId);
        ResourceLocation lootTableId = deriveLootTableId(itemId, sourceBlockId);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_CROP_ITEM, itemId.toString());
            if (sourceBlockId != null) {
                tag.putString(TAG_SOURCE_BLOCK, sourceBlockId.toString());
            }
            if (lootTableId != null) {
                tag.putString(TAG_LOOT_TABLE, lootTableId.toString());
            }
            tag.putFloat(TAG_REQUIRED_AMOUNT, Math.max(1.0F, DataExtractorConfig.cropRequiredAmount));
            tag.putFloat(TAG_COLLECTED_AMOUNT, 0.0F);
        });
        return true;
    }

    public static boolean addCollectedCrop(ItemStack stack, float amount) {
        if (amount <= 0.0F || !hasRecordedCrop(stack)) {
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

    public static void setRequiredAmount(ItemStack stack, float requiredAmount) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            float clampedRequired = Math.max(1.0F, requiredAmount);
            float current = Math.max(0.0F, tag.getFloat(TAG_COLLECTED_AMOUNT));
            tag.putFloat(TAG_REQUIRED_AMOUNT, clampedRequired);
            tag.putFloat(TAG_COLLECTED_AMOUNT, Mth.clamp(current, 0.0F, clampedRequired));
        });
    }

    public static boolean isComplete(ItemStack stack) {
        if (!hasRecordedCrop(stack)) {
            return false;
        }
        float required = getRequiredAmount(stack);
        return required > 0.0F && getCollectedAmount(stack) + 0.0001F >= required;
    }

    @Nullable
    public static ResourceLocation getCropItemId(ItemStack stack) {
        String rawId = getTag(stack).getString(TAG_CROP_ITEM);
        if (rawId.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(rawId);
    }

    @Nullable
    public static ResourceLocation getSourceBlockId(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        String rawId = tag.getString(TAG_SOURCE_BLOCK);
        if (!rawId.isEmpty()) {
            return ResourceLocation.tryParse(rawId);
        }

        return deriveSourceBlockId(getCropItemId(stack));
    }

    @Nullable
    public static ResourceLocation getLootTableId(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        String rawId = tag.getString(TAG_LOOT_TABLE);
        if (!rawId.isEmpty()) {
            return ResourceLocation.tryParse(rawId);
        }

        ResourceLocation cropItemId = getCropItemId(stack);
        return deriveLootTableId(cropItemId, deriveSourceBlockId(cropItemId));
    }

    public static Component getCropDisplayName(ItemStack stack) {
        ResourceLocation itemId = getCropItemId(stack);
        if (itemId == null) {
            return Component.translatable("item.data_energistics.carrier.target_unknown");
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return Component.literal(itemId.toString());
        }

        return new ItemStack(item).getHoverName();
    }

    @Nullable
    public static ResourceLocation getRecordedCropItemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return null;
        }

        CropInputMapping mapping = getConfiguredInputMapping(itemId);
        if (mapping != null) {
            return mapping.recordedCropId();
        }

        return itemId;
    }

    public static float getCropProgressValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        CropInputMapping mapping = itemId == null ? null : getConfiguredInputMapping(itemId);
        if (mapping != null) {
            return mapping.progressPerItem();
        }
        return 1.0F;
    }

    public static ItemStack createCompletedCarrier(ItemStack source) {
        ItemStack result = new ItemStack(ModItems.CROP_DATA_CARRIER.get());
        CompoundTag tag = getTag(source);
        if (!tag.isEmpty()) {
            tag.putFloat(TAG_COLLECTED_AMOUNT, Math.max(tag.getFloat(TAG_COLLECTED_AMOUNT), tag.getFloat(TAG_REQUIRED_AMOUNT)));
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return result;
    }

    public static boolean canRecordCrop(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }
        return isAllowedCropItem(itemId) && !containsId(DataExtractorConfig.cropDataBlacklist, itemId);
    }

    private static boolean containsId(String csv, ResourceLocation id) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        for (String token : csv.split(",")) {
            if (id.toString().equals(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Nullable
    private static ResourceLocation deriveSourceBlockId(@Nullable ResourceLocation cropItemId) {
        if (cropItemId == null) {
            return null;
        }

        Item cropItem = BuiltInRegistries.ITEM.getOptional(cropItemId).orElse(null);
        if (cropItem == null) {
            return null;
        }

        Block sourceBlock = resolveCropSourceBlock(cropItem);
        return sourceBlock == null ? null : BuiltInRegistries.BLOCK.getKey(sourceBlock);
    }

    @Nullable
    private static ResourceLocation deriveLootTableId(@Nullable ResourceLocation cropItemId, @Nullable ResourceLocation sourceBlockId) {
        if (cropItemId == null || sourceBlockId == null) {
            return null;
        }

        Block sourceBlock = BuiltInRegistries.BLOCK.getOptional(sourceBlockId).orElse(null);
        if (sourceBlock == null) {
            return null;
        }

        if (!isTreeSaplingBlock(sourceBlock, cropItemId)) {
            return null;
        }

        return Data_Energistics.id(TREE_LOOT_TABLE_PREFIX + sourceBlockId.getNamespace() + "/" + sourceBlockId.getPath());
    }

    @Nullable
    private static Block resolveCropSourceBlock(Item cropItem) {
        Block direct = CROP_TO_BLOCK.get(cropItem);
        if (direct != null) return direct;
        return cropItem instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    private static boolean isTreeSaplingBlock(Block sourceBlock, ResourceLocation cropItemId) {
        return sourceBlock.defaultBlockState().is(BlockTags.SAPLINGS) || cropItemId.getPath().endsWith("_propagule");
    }

    public static boolean isAllowedCropItem(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }

        if (getConfiguredInputMapping(itemId) != null) {
            return true;
        }

        if (containsId(DataExtractorConfig.cropDataWhitelist, itemId)) {
            return true;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item != null && isInCropTag(item)) {
            return true;
        }

        Block sourceBlock = deriveSourceBlockId(itemId) != null ? BuiltInRegistries.BLOCK.getOptional(deriveSourceBlockId(itemId)).orElse(null) : null;
        return isBuiltInSupportedCrop(sourceBlock, itemId);
    }

    private static boolean isBuiltInSupportedCrop(@Nullable Block sourceBlock, ResourceLocation itemId) {
        if (sourceBlock == null) {
            return false;
        }

        if (sourceBlock instanceof CropBlock) return true;
        if (sourceBlock instanceof NetherWartBlock) return true;
        if (sourceBlock instanceof StemBlock) return true;
        if (sourceBlock instanceof CocoaBlock) return true;
        if (sourceBlock instanceof SweetBerryBushBlock) return true;

        if (isTreeSaplingBlock(sourceBlock, itemId)) return true;

        if (sourceBlock.defaultBlockState().is(BlockTags.CROPS)) return true;
        if (sourceBlock.defaultBlockState().is(BlockTags.MAINTAINS_FARMLAND)) return true;

        if (hasAgeProperty(sourceBlock)) return true;

        return false;
    }

    private static boolean isInCropTag(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(TAG_COMMON_SEEDS) || stack.is(TAG_COMMON_CROPS);
    }

    private static boolean hasAgeProperty(Block block) {
        return block.defaultBlockState().getProperties().stream()
                .anyMatch(prop -> prop.getName().equals("age"));
    }

    @Nullable
    private static CropInputMapping getConfiguredInputMapping(ResourceLocation inputItemId) {
        if (inputItemId == null || DataExtractorConfig.cropInputMappings == null || DataExtractorConfig.cropInputMappings.isBlank()) {
            return null;
        }

        for (String token : DataExtractorConfig.cropInputMappings.split(",")) {
            CropInputMapping mapping = parseInputMapping(token);
            if (mapping != null && mapping.inputItemId().equals(inputItemId)) {
                return mapping;
            }
        }

        return null;
    }

    @Nullable
    private static CropInputMapping parseInputMapping(String raw) {
        if (raw == null) {
            return null;
        }

        String entry = raw.trim();
        if (entry.isEmpty()) {
            return null;
        }

        int equalsIndex = entry.indexOf('=');
        int atIndex = entry.indexOf('@', equalsIndex + 1);
        if (equalsIndex <= 0 || atIndex <= equalsIndex + 1 || atIndex >= entry.length() - 1) {
            return null;
        }

        ResourceLocation inputItemId = ResourceLocation.tryParse(entry.substring(0, equalsIndex).trim());
        ResourceLocation recordedCropId = ResourceLocation.tryParse(entry.substring(equalsIndex + 1, atIndex).trim());
        if (inputItemId == null || recordedCropId == null) {
            return null;
        }

        try {
            float progressPerItem = Float.parseFloat(entry.substring(atIndex + 1).trim());
            if (progressPerItem <= 0.0F || !Float.isFinite(progressPerItem)) {
                return null;
            }
            return new CropInputMapping(inputItemId, recordedCropId, progressPerItem);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record CropInputMapping(ResourceLocation inputItemId, ResourceLocation recordedCropId, float progressPerItem) {}
}
