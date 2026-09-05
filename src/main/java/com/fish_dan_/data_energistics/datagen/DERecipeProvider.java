package com.fish_dan_.data_energistics.datagen;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipe;

import appeng.core.definitions.AEItems;

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
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import com.glodblock.github.appflux.common.AFSingletons;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DERecipeProvider extends RecipeProvider {

    private static final Logger LOG = Data_Energistics.LOGGER;

    public DERecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
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

        new IntegratedChargerBuilder()
                .addItem(tag("c:gems/redstone"), 16)
                .fluidAmount(250)
                .addOutput(AFSingletons.CHARGED_REDSTONE, 20)
                .save(cond, id("data_charge_press/appflux/redstone_crystal"));

        new IntegratedChargerBuilder()
                .addItem(Items.REDSTONE_BLOCK, 16)
                .addItem(AEItems.FLUIX_CRYSTAL.asItem(), 16)
                .addItem(Items.GLOWSTONE_DUST, 16)
                .fluidAmount(250)
                .addOutput(AFSingletons.REDSTONE_CRYSTAL, 72)
                .save(cond, id("data_charge_press/appflux/appflux_redstone_crystal"));
    }

    private void buildExtendedAeRecipes(RecipeOutput output) {
        var cond = output.withConditions(new ModLoadedCondition("extendedae"));

        new IntegratedChargerBuilder()
                .addItem(tag("c:gems/entro"), 16)
                .addItem(tag("c:dusts/entro"), 16)
                .fluidAmount(250)
                .addOutput(item("extendedae:entro_crystal"), 72)
                .save(cond, id("data_charge_press/extendedae/eae_entro_crystal"));
    }

    private void buildNeoEcoAeRecipes(RecipeOutput output) {
        var cond = output.withConditions(new ModLoadedCondition("neoecoae"));

        new IntegratedChargerBuilder()
                .addItem(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.asItem(), 32)
                .addItem(item("neoecoae:energized_crystal_dust"), 32)
                .fluidAmount(250)
                .addOutput(item("neoecoae:energized_crystal"), 72)
                .save(cond, id("data_charge_press/neoecoae/neoeco_energized_crystal"));

        new IntegratedChargerBuilder()
                .addItem(item("neoecoae:energized_crystal_dust"), 48)
                .addItem(AEItems.FLUIX_CRYSTAL.asItem(), 48)
                .fluidAmount(250)
                .addOutput(item("neoecoae:energized_fluix_crystal"), 72)
                .save(cond, id("data_charge_press/neoecoae/neoeco_energized_fluix_crystal"));
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

    private static class IntegratedChargerBuilder {

        private final List<DataChargePressIngredient> inputs = new ArrayList<>();
        private int fluidAmount;
        private ItemStack itemOutput = ItemStack.EMPTY;

        IntegratedChargerBuilder addItem(Ingredient ingredient, int count) {
            inputs.add(new DataChargePressIngredient(ingredient, count));
            return this;
        }

        IntegratedChargerBuilder addItem(Item item, int count) {
            return addItem(Ingredient.of(item), count);
        }

        IntegratedChargerBuilder fluidAmount(int amount) {
            if (this.fluidAmount != 0) {
                throw new IllegalStateException("Integrated charger recipe fluid amount was already configured");
            }
            this.fluidAmount = amount;
            return this;
        }

        IntegratedChargerBuilder addOutput(ItemStack stack) {
            this.itemOutput = stack.copy();
            return this;
        }

        IntegratedChargerBuilder addOutput(Item item, int count) {
            return addOutput(new ItemStack(item, count));
        }

        void save(RecipeOutput output, ResourceLocation id) {
            if (this.inputs.isEmpty() ||
                    this.inputs.size() > DataChargePressRecipe.MAX_ITEM_INPUT_COUNT || this.fluidAmount <= 0 ||
                    this.fluidAmount > DataChargePressRecipe.MAX_FLUID_AMOUNT ||
                    this.itemOutput.isEmpty()) {
                throw new IllegalStateException("Invalid integrated charger crystal growth recipe: " + id);
            }
            var recipe = new DataChargePressRecipe(
                    List.copyOf(inputs),
                    this.fluidAmount,
                    this.itemOutput);
            output.accept(id, recipe, null);
        }
    }
}
