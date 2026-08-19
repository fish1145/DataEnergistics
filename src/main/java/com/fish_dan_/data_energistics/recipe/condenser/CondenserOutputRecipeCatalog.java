package com.fish_dan_.data_energistics.recipe.condenser;

import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Resolves the current, reload-sensitive matter condenser output catalog from a level's recipe manager. */
public final class CondenserOutputRecipeCatalog {

    private CondenserOutputRecipeCatalog() {}

    /** Returns all custom outputs in deterministic recipe-ID order. */
    public static List<RecipeHolder<CondenserOutputRecipe>> getRecipes(Level level) {
        return level.getRecipeManager()
                .getAllRecipesFor(DERecipes.CONDENSER_OUTPUT_TYPE.get())
                .stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    /** Resolves a recipe by its authoritative holder ID, returning null after removal or a failed reload. */
    @Nullable
    public static RecipeHolder<CondenserOutputRecipe> find(Level level, ResourceLocation id) {
        return getRecipes(level).stream()
                .filter(holder -> holder.id().equals(id))
                .findFirst()
                .orElse(null);
    }
}
