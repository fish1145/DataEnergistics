package com.fish_dan_.data_energistics.mixin.core;

import net.minecraft.world.entity.ExperienceOrb;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the number of merged experience orbs without serializing the entire entity.
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {

    /**
     * Reads how many equivalent orbs are represented by this entity.
     *
     * @return merged orb count
     */
    @Accessor("count")
    int dataEnergistics$getCount();
}
