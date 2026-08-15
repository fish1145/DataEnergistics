package com.fish_dan_.data_energistics.item.carrier;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.ItemRule;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

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

        stack.set(DEDataComponents.CROP_DATA_CARRIER.get(), new CropDataCarrierItemData(
                itemId,
                Optional.ofNullable(sourceBlockId),
                Optional.ofNullable(lootTableId),
                DataEnergisticsConfiguration.INSTANCE.dataExtractor().cropRequiredAmount(),
                0.0F));
        return true;
    }

    public static boolean addCollectedCrop(ItemStack stack, float amount) {
        if (amount <= 0.0F || !hasRecordedCrop(stack)) {
            return false;
        }

        CropDataCarrierItemData data = getData(stack);
        if (data == null) {
            return false;
        }
        stack.set(DEDataComponents.CROP_DATA_CARRIER.get(), data.withAddedCollectedAmount(amount));
        return true;
    }

    public static float getRequiredAmount(ItemStack stack) {
        CropDataCarrierItemData data = getData(stack);
        return data == null ? 0.0F : Math.max(0.0F, data.requiredAmount());
    }

    public static float getCollectedAmount(ItemStack stack) {
        CropDataCarrierItemData data = getData(stack);
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

        CropDataCarrierItemData data = getData(stack);
        if (data != null) {
            stack.set(DEDataComponents.CROP_DATA_CARRIER.get(), data.withRequiredAmount(requiredAmount));
        }
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
        CropDataCarrierItemData data = getData(stack);
        return data == null ? null : data.cropItem();
    }

    @Nullable
    public static ResourceLocation getSourceBlockId(ItemStack stack) {
        CropDataCarrierItemData data = getData(stack);
        if (data != null && data.sourceBlock().isPresent()) {
            return data.sourceBlock().get();
        }

        return deriveSourceBlockId(getCropItemId(stack));
    }

    @Nullable
    public static ResourceLocation getLootTableId(ItemStack stack) {
        CropDataCarrierItemData data = getData(stack);
        if (data != null && data.lootTable().isPresent()) {
            return data.lootTable().get();
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

        ItemRule mapping = getConfiguredInputMapping(itemId);
        if (mapping != null) {
            return mapping.recordedItemId();
        }

        return itemId;
    }

    public static float getCropProgressValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemRule mapping = itemId == null ? null : getConfiguredInputMapping(itemId);
        if (mapping != null) {
            return mapping.progressPerItem();
        }
        return 1.0F;
    }

    public static ItemStack createCompletedCarrier(ItemStack source) {
        ItemStack result = new ItemStack(DEItems.CROP_DATA_CARRIER.get());
        CropDataCarrierItemData data = getData(source);
        if (data != null) {
            result.set(DEDataComponents.CROP_DATA_CARRIER.get(), data.asComplete());
        }
        return result;
    }

    public static boolean canRecordCrop(ResourceLocation itemId) {
        return isAllowedCropItem(itemId) &&
                !DataEnergisticsConfiguration.INSTANCE.dataExtractor().cropDataBlacklist().contains(itemId);
    }

    private static @Nullable CropDataCarrierItemData getData(ItemStack stack) {
        CropDataCarrierItemData data = stack.get(DEDataComponents.CROP_DATA_CARRIER.get());
        if (data != null) {
            return data;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String rawCropItem = tag.getString(TAG_CROP_ITEM);
        ResourceLocation cropItem = rawCropItem.isEmpty() ? null : ResourceLocation.tryParse(rawCropItem);
        if (cropItem == null) {
            return null;
        }

        String rawSourceBlock = tag.getString(TAG_SOURCE_BLOCK);
        String rawLootTable = tag.getString(TAG_LOOT_TABLE);
        return new CropDataCarrierItemData(
                cropItem,
                rawSourceBlock.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(rawSourceBlock)),
                rawLootTable.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(rawLootTable)),
                Math.max(0.0F, tag.getFloat(TAG_REQUIRED_AMOUNT)),
                Math.max(0.0F, tag.getFloat(TAG_COLLECTED_AMOUNT)));
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

        if (DataEnergisticsConfiguration.INSTANCE.dataExtractor().cropDataWhitelist().contains(itemId)) {
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
    private static ItemRule getConfiguredInputMapping(ResourceLocation inputItemId) {
        for (ItemRule rule : DataExtractorRuleTable.snapshot().inputRules()) {
            if (rule.slot() == Slot.CROP && rule.inputItemId().equals(inputItemId)) {
                return rule;
            }
        }
        return null;
    }
}
