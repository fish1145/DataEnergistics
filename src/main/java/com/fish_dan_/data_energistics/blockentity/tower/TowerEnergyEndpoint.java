package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

/**
 * Describes one FE-capable endpoint visible to a Data Distribution Tower.
 *
 * @param pos     block position that owns the energy storage
 * @param side    queried side, or null for internal access
 * @param storage resolved energy storage instance
 */
public record TowerEnergyEndpoint(BlockPos pos, @Nullable Direction side, IEnergyStorage storage) {}
