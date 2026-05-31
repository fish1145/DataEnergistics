package com.fish_dan_.data_energistics.datagen;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.glodblock.github.appflux.common.AFSingletons;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {

    private static final Logger LOG = LogUtils.getLogger();

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        buildAppFluxRecipes(output);
        buildExtendedAeRecipes(output);
        buildNeoEcoAeRecipes(output);
    }

    private void buildAppFluxRecipes(RecipeOutput output) {
        var cond = output.withConditions(new ModLoadedCondition("appflux"));

        new Builder()
                .addItem(tag("c:gems/redstone"), 16)
                .addFluid(fluid(Data_Energistics.MODID + ":data_corrosion_liquid"), 250)
                .addOutput(AFSingletons.CHARGED_REDSTONE, 20)
                .save(cond, id(Data_Energistics.MODID + "/data_reassembler/appflux/redstone_crystal"));

        new Builder()
                .addItem(Items.REDSTONE_BLOCK, 16)
                .addItem(AEItems.FLUIX_CRYSTAL.asItem(), 16)
                .addItem(Items.GLOWSTONE_DUST, 16)
                .addFluid(fluid(Data_Energistics.MODID + ":data_corrosion_liquid"), 250)
                .addOutput(AFSingletons.REDSTONE_CRYSTAL, 72)
                .keyInput(dataFlowKey(2400))
                .save(cond, id(Data_Energistics.MODID + "/data_reassembler/appflux/appflux_redstone_crystal"));
    }

    private void buildExtendedAeRecipes(RecipeOutput output) {
        var cond = output.withConditions(new ModLoadedCondition("extendedae"));

        new Builder()
                .addItem(tag("c:gems/entro"), 16)
                .addItem(tag("c:dusts/entro"), 16)
                .addFluid(fluid(Data_Energistics.MODID + ":data_corrosion_liquid"), 250)
                .addOutput(item("extendedae:entro_crystal"), 72)
                .keyInput(dataFlowKey(2400))
                .save(cond, id(Data_Energistics.MODID + "/data_reassembler/extendedae/eae_entro_crystal"));
    }

    private void buildNeoEcoAeRecipes(RecipeOutput output) {
        var cond = output.withConditions(new ModLoadedCondition("neoecoae"));

        new Builder()
                .addItem(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.asItem(), 32)
                .addItem(item("neoecoae:energized_crystal_dust"), 32)
                .addFluid(fluid(Data_Energistics.MODID + ":data_corrosion_liquid"), 250)
                .addOutput(item("neoecoae:energized_crystal"), 72)
                .keyInput(dataFlowKey(2400))
                .save(cond, id(Data_Energistics.MODID + "/data_reassembler/neoecoae/neoeco_energized_crystal"));

        new Builder()
                .addItem(item("neoecoae:energized_crystal_dust"), 48)
                .addItem(AEItems.FLUIX_CRYSTAL.asItem(), 48)
                .addFluid(fluid(Data_Energistics.MODID + ":data_corrosion_liquid"), 250)
                .addOutput(item("neoecoae:energized_fluix_crystal"), 72)
                .keyInput(dataFlowKey(2400))
                .save(cond, id(Data_Energistics.MODID + "/data_reassembler/neoecoae/neoeco_energized_fluix_crystal"));
    }

    private static ResourceLocation id(String path) {
        return Data_Energistics.id(path);
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) {
            LOG.warn("Item not found: {}", id);
        }
        return item;
    }

    private static Ingredient tag(String tagId) {
        return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId)));
    }

    private static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
    }

    private static GenericStack dataFlowKey(long amount) {
        return new GenericStack(DataFlowKey.of(), amount);
    }

    private static class Builder {

        private final List<DataRipperReassemblerIngredient> itemInputs = new ArrayList<>();
        private final List<GenericStack> fluidInputs = new ArrayList<>();
        private final List<ItemStack> itemOutputs = new ArrayList<>();
        private final List<GenericStack> fluidOutputs = new ArrayList<>();
        private int processTicks = DataRipperReassemblerRecipe.PROCESS_TICKS;
        private GenericStack keyInput;
        private GenericStack keyOutput;

        Builder addItem(Ingredient ingredient, int count) {
            itemInputs.add(new DataRipperReassemblerIngredient(ingredient, count));
            return this;
        }

        Builder addItem(Item item, int count) {
            return addItem(Ingredient.of(item), count);
        }

        Builder addFluid(Fluid fluid, long amount) {
            fluidInputs.add(new GenericStack(AEFluidKey.of(fluid), amount));
            return this;
        }

        Builder addOutput(ItemStack stack) {
            itemOutputs.add(stack);
            return this;
        }

        Builder addOutput(Item item, int count) {
            return addOutput(new ItemStack(item, count));
        }

        Builder keyInput(GenericStack key) {
            this.keyInput = key;
            return this;
        }

        Builder keyOutput(GenericStack key) {
            this.keyOutput = key;
            return this;
        }

        Builder processTicks(int ticks) {
            this.processTicks = ticks;
            return this;
        }

        void save(RecipeOutput output, ResourceLocation id) {
            var recipe = new DataRipperReassemblerRecipe(
                    List.copyOf(itemInputs),
                    List.copyOf(fluidInputs),
                    List.copyOf(itemOutputs),
                    List.copyOf(fluidOutputs),
                    processTicks,
                    keyInput,
                    keyOutput);
            output.accept(id, recipe, null);
        }
    }
}
