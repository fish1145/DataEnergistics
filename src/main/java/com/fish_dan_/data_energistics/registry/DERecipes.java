package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.UniversalTerminalCombineRecipe;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipe;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereCondenserRecipe;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereCondenserRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereRightClickRecipe;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereRightClickRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.reassembler.DataReassemblerCraftingRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeSerializer;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipe;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipeSerializer;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DERecipes {

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
    public static final DeferredHolder<RecipeType<?>, RecipeType<RadixContainmentSphereRightClickRecipe>> RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_TYPE = RECIPE_TYPES.register("right_click", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":right_click";
        }
    });
    public static final DeferredHolder<RecipeType<?>, RecipeType<RadixContainmentSphereCondenserRecipe>> RADIX_CONTAINMENT_SPHERE_CONDENSER_TYPE = RECIPE_TYPES.register("radix_containment_sphere_condenser", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":radix_containment_sphere_condenser";
        }
    });
    public static final DeferredHolder<RecipeType<?>, RecipeType<DataChargerRecipe>> DATA_CHARGER_TYPE = RECIPE_TYPES.register("data_charger", () -> new RecipeType<>() {

        @Override
        public String toString() {
            return Data_Energistics.MODID + ":data_charger";
        }
    });
    public static final DeferredHolder<RecipeSerializer<?>, TimeShiftRecipeSerializer> TIME_SHIFT_SERIALIZER = RECIPE_SERIALIZERS.register("time_shift", TimeShiftRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataRipperReassemblerRecipeSerializer> DATA_RIPPER_REASSEMBLER_SERIALIZER = RECIPE_SERIALIZERS.register("data_reassembler", DataRipperReassemblerRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, RadixContainmentSphereRightClickRecipeSerializer> RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_SERIALIZER = RECIPE_SERIALIZERS.register("right_click",
            RadixContainmentSphereRightClickRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, RadixContainmentSphereCondenserRecipeSerializer> RADIX_CONTAINMENT_SPHERE_CONDENSER_SERIALIZER = RECIPE_SERIALIZERS.register("radix_containment_sphere_condenser",
            RadixContainmentSphereCondenserRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataChargerRecipeSerializer> DATA_CHARGER_SERIALIZER = RECIPE_SERIALIZERS.register("data_charger",
            DataChargerRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, DataReassemblerCraftingRecipeSerializer> DATA_REASSEMBLER_CRAFTING_SERIALIZER = RECIPE_SERIALIZERS.register(
            "data_reassembler_crafting",
            DataReassemblerCraftingRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, RadixContainmentSphereRightClickRecipeSerializer> LEGACY_RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_SERIALIZER = RECIPE_SERIALIZERS.register("data_capture_ball_right_click",
            RadixContainmentSphereRightClickRecipeSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<UniversalTerminalCombineRecipe>> UNIVERSAL_TERMINAL_COMBINE_SERIALIZER = RECIPE_SERIALIZERS.register(
            "universal_terminal_combine",
            () -> new SimpleCraftingRecipeSerializer<>(UniversalTerminalCombineRecipe::new));

    private DERecipes() {}

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
