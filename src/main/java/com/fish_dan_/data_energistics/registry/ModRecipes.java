package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.TimeShiftRecipe;
import com.fish_dan_.data_energistics.recipe.TimeShiftRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Data_Energistics.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Data_Energistics.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<TimeShiftRecipe>> TIME_SHIFT_TYPE =
            RECIPE_TYPES.register("time_shift", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return Data_Energistics.MODID + ":time_shift";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, TimeShiftRecipeSerializer> TIME_SHIFT_SERIALIZER =
            RECIPE_SERIALIZERS.register("time_shift", TimeShiftRecipeSerializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
