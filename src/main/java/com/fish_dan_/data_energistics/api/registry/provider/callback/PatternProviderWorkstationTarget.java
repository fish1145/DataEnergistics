package com.fish_dan_.data_energistics.api.registry.provider.callback;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One actual workstation route exposed by a custom pattern provider.
 *
 * @param workstation exact live block entity that may receive this provider's crafting inputs
 * @param inputSide   workstation face reached by the route
 */
public record PatternProviderWorkstationTarget(BlockEntity workstation, Direction inputSide) {}
