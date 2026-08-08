package com.fish_dan_.data_energistics.api.registry.recipe;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * Stable result retained with a Trinity pattern definition so reloads cannot reinterpret queued work.
 *
 * @param resolverId resolver registration that established the identity
 * @param recipeId   recipe selected by the encoded pattern
 */
public record TrinityPatternRecipeIdResolution(@NotNull ResourceLocation resolverId,
                                               @NotNull ResourceLocation recipeId) {}
