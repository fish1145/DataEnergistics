package com.fish_dan_.data_energistics.worldgen.meteorite;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum CraterType {

    NONE((Block) null),
    NORMAL(Blocks.AIR),
    LAVA(Blocks.LAVA),
    OBSIDIAN(Blocks.OBSIDIAN),
    WATER(Blocks.WATER),
    SNOW(Blocks.SNOW_BLOCK),
    ICE(Blocks.ICE);

    private final Block filler;

    CraterType(Block filler) {
        this.filler = filler;
    }

    public Block getFiller() {
        return this.filler;
    }
}
