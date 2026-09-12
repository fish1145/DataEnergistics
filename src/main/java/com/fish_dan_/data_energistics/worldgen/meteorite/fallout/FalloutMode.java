package com.fish_dan_.data_energistics.worldgen.meteorite.fallout;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags.Biomes;

import com.google.common.collect.ImmutableList;

import java.util.List;

public enum FalloutMode {

    NONE(),
    DEFAULT(),
    SAND(Biomes.IS_SANDY, BiomeTags.IS_BEACH),
    TERRACOTTA(BiomeTags.IS_BADLANDS),
    ICE_SNOW(Biomes.IS_COLD);

    private final List<TagKey<Biome>> biomeTags;

    @SafeVarargs
    FalloutMode(TagKey<Biome>... biomeTags) {
        this.biomeTags = ImmutableList.copyOf(biomeTags);
    }

    public boolean matches(Holder<Biome> biome) {
        for (TagKey<Biome> biomeTag : this.biomeTags) {
            if (biome.is(biomeTag)) {
                return true;
            }
        }
        return false;
    }

    public static FalloutMode fromBiome(Holder<Biome> biome) {
        for (FalloutMode mode : values()) {
            if (mode.matches(biome)) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
