package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallCondenserRecipe;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallCondenserRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallRightClickRecipe;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallRightClickRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.TimeShiftRecipe;
import com.fish_dan_.data_energistics.recipe.TimeShiftRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.UniversalTerminalCombineRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Data_Energistics.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Data_Energistics.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<TimeShiftRecipe>> TIME_SHIFT_TYPE = RECIPE_TYPES.register("time_shift", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":time_shift";
        }
    });
    public static final DeferredHolder<RecipeType<?>, RecipeType<DataRipperReassemblerRecipe>> DATA_RIPPER_REASSEMBLER_TYPE = RECIPE_TYPES.register("data_reassembler", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":data_reassembler";
        }
    });
    public static final DeferredHolder<RecipeType<?>, RecipeType<DataCaptureBallRightClickRecipe>> DATA_CAPTURE_BALL_RIGHT_CLICK_TYPE = RECIPE_TYPES.register("right_click", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":right_click";
        }
    });
    public static final DeferredHolder<RecipeType<?>, RecipeType<DataCaptureBallCondenserRecipe>> DATA_CAPTURE_BALL_CONDENSER_TYPE = RECIPE_TYPES.register("data_capture_ball_condenser", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":data_capture_ball_condenser";
        }
    });
    public static final DeferredHolder<RecipeSerializer<?>, TimeShiftRecipeSerializer> TIME_SHIFT_SERIALIZER = RECIPE_SERIALIZERS.register("time_shift", TimeShiftRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataRipperReassemblerRecipeSerializer> DATA_RIPPER_REASSEMBLER_SERIALIZER = RECIPE_SERIALIZERS.register("data_reassembler", DataRipperReassemblerRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataCaptureBallRightClickRecipeSerializer> DATA_CAPTURE_BALL_RIGHT_CLICK_SERIALIZER = RECIPE_SERIALIZERS.register("right_click",
            DataCaptureBallRightClickRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataCaptureBallCondenserRecipeSerializer> DATA_CAPTURE_BALL_CONDENSER_SERIALIZER = RECIPE_SERIALIZERS.register("data_capture_ball_condenser",
            DataCaptureBallCondenserRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataCaptureBallRightClickRecipeSerializer> DATA_CAPTURE_BALL_RIGHT_CLICK_LEGACY_SERIALIZER = RECIPE_SERIALIZERS.register("data_capture_ball_right_click",
            DataCaptureBallRightClickRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<UniversalTerminalCombineRecipe>> UNIVERSAL_TERMINAL_COMBINE_SERIALIZER = RECIPE_SERIALIZERS.register(
            "universal_terminal_combine",
            () -> new SimpleCraftingRecipeSerializer<>(UniversalTerminalCombineRecipe::new));

    private ModRecipes() {}

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
