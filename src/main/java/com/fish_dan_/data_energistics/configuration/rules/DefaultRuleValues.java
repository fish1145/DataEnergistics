package com.fish_dan_.data_energistics.configuration.rules;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Carries atomic values used only when no active or legacy rule file exists. */
public record DefaultRuleValues(
                                List<CropRule> cropRules,
                                float cropRequiredAmount,
                                float oreRequiredAmount) {

    private static final List<CropRule> BUILT_IN_CROP_RULES = List.of(
            crop("minecraft:wheat_seeds", "minecraft:wheat", 0.5F),
            crop("minecraft:beetroot_seeds", "minecraft:beetroot", 0.5F),
            crop("minecraft:melon", "minecraft:melon", 1.0F),
            crop("minecraft:melon_seeds", "minecraft:melon", 0.5F),
            crop("minecraft:melon_slice", "minecraft:melon", 0.5F),
            crop("minecraft:pumpkin", "minecraft:pumpkin", 1.0F),
            crop("minecraft:pumpkin_seeds", "minecraft:pumpkin", 0.5F),
            crop("minecraft:sweet_berries", "minecraft:sweet_berries", 1.0F),
            crop("minecraft:brown_mushroom", "minecraft:brown_mushroom", 1.0F),
            crop("minecraft:red_mushroom", "minecraft:red_mushroom", 1.0F),
            crop("minecraft:crimson_fungus", "minecraft:crimson_fungus", 1.0F),
            crop("minecraft:warped_fungus", "minecraft:warped_fungus", 1.0F),
            crop("minecraft:cactus", "minecraft:cactus", 1.0F),
            crop("minecraft:sugar_cane", "minecraft:sugar_cane", 1.0F),
            crop("minecraft:bamboo", "minecraft:bamboo", 1.0F),
            crop("minecraft:dandelion", "minecraft:dandelion", 1.0F),
            crop("minecraft:poppy", "minecraft:poppy", 1.0F),
            crop("minecraft:blue_orchid", "minecraft:blue_orchid", 1.0F),
            crop("minecraft:allium", "minecraft:allium", 1.0F),
            crop("minecraft:azure_bluet", "minecraft:azure_bluet", 1.0F),
            crop("minecraft:red_tulip", "minecraft:red_tulip", 1.0F),
            crop("minecraft:orange_tulip", "minecraft:orange_tulip", 1.0F),
            crop("minecraft:white_tulip", "minecraft:white_tulip", 1.0F),
            crop("minecraft:pink_tulip", "minecraft:pink_tulip", 1.0F),
            crop("minecraft:oxeye_daisy", "minecraft:oxeye_daisy", 1.0F),
            crop("minecraft:cornflower", "minecraft:cornflower", 1.0F),
            crop("minecraft:lily_of_the_valley", "minecraft:lily_of_the_valley", 1.0F),
            crop("minecraft:wither_rose", "minecraft:wither_rose", 1.0F),
            crop("minecraft:sunflower", "minecraft:sunflower", 1.0F),
            crop("minecraft:lilac", "minecraft:lilac", 1.0F),
            crop("minecraft:rose_bush", "minecraft:rose_bush", 1.0F),
            crop("minecraft:peony", "minecraft:peony", 1.0F),
            crop("minecraft:pink_petals", "minecraft:pink_petals", 1.0F),
            crop("minecraft:torchflower", "minecraft:torchflower", 1.0F),
            crop("minecraft:open_eyeblossom", "minecraft:open_eyeblossom", 1.0F),
            crop("minecraft:closed_eyeblossom", "minecraft:closed_eyeblossom", 1.0F));

    public DefaultRuleValues {
        cropRules = List.copyOf(cropRules);
    }

    /** Returns the immutable built-in crop rows used for a fresh rule file. */
    public static List<CropRule> builtInCropRules() {
        return BUILT_IN_CROP_RULES;
    }

    private static CropRule crop(String inputItem, String recordedItem, float progressPerItem) {
        return new CropRule(
                ResourceLocation.parse(inputItem),
                ResourceLocation.parse(recordedItem),
                progressPerItem);
    }

    /** One crop carrier row before the current required amount is applied. */
    public record CropRule(
                           ResourceLocation inputItem,
                           ResourceLocation recordedItem,
                           float progressPerItem) {}
}
