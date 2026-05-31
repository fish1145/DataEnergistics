package com.fish_dan_.data_energistics.datagen;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.registries.DeferredItem;

import appeng.core.definitions.AEItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moakiee.ae2lt.api.lightning.LightningTier;
import com.moakiee.ae2lt.registry.ModItems;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ModAe2LtRecipeProvider implements DataProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final PackOutput output;

    public ModAe2LtRecipeProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var recipeDir = output.getOutputFolder()
                .resolve("data/" + Data_Energistics.MODID + "/recipe/" + Data_Energistics.MODID + "/data_reassembler/ae2lt");

        var tasks = new CompletableFuture<?>[3];
        tasks[0] = saveStable(cache, recipeDir.resolve("perfect_electro_chime_crystal.json"), perfectElectroChime());
        tasks[1] = saveStable(cache, recipeDir.resolve("overload_crystal.json"), overloadCrystal());
        tasks[2] = saveStable(cache, recipeDir.resolve("electro_chime_crystal.json"), electroChimeCrystal());
        return CompletableFuture.allOf(tasks);
    }

    private static CompletableFuture<?> saveStable(CachedOutput cache, Path path, JsonObject json) {
        return DataProvider.saveStable(cache, GSON.toJsonTree(json), path);
    }

    private static JsonObject conditions(String modid) {
        var arr = new JsonArray();
        var c = new JsonObject();
        c.addProperty("type", "neoforge:mod_loaded");
        c.addProperty("modid", modid);
        arr.add(c);

        var root = new JsonObject();
        root.add("neoforge:conditions", arr);
        return root;
    }

    private static JsonObject itemInput(String itemId, int count) {
        var ing = new JsonObject();
        ing.addProperty("item", itemId);
        var input = new JsonObject();
        input.add("ingredient", ing);
        input.addProperty("count", count);
        return input;
    }

    private static JsonObject fluidInput(String fluidId, long amount) {
        var f = new JsonObject();
        f.addProperty("#", amount);
        f.addProperty("#t", "ae2:f");
        f.addProperty("id", fluidId);
        return f;
    }

    private static JsonObject itemOutput(String itemId, int count) {
        var out = new JsonObject();
        out.addProperty("id", itemId);
        out.addProperty("count", count);
        return out;
    }

    private static String itemId(DeferredItem<?> deferred) {
        return deferred.getId().toString();
    }

    private static JsonObject lightningKey(long amount, LightningTier tier) {
        var key = new JsonObject();
        key.addProperty("#t", "ae2lt:lightning");
        key.addProperty("#", amount);
        key.addProperty("tier", tier.name().toLowerCase());
        return key;
    }

    private static JsonObject perfectElectroChime() {
        var root = conditions("ae2lt");
        root.addProperty("type", Data_Energistics.MODID + ":data_reassembler");

        var items = new JsonArray();
        items.add(itemInput(itemId(ModItems.ELECTRO_CHIME_CRYSTAL), 1));
        items.add(itemInput(itemId(ModItems.OVERLOAD_SINGULARITY), 384));
        root.add("item_inputs", items);

        var fluids = new JsonArray();
        fluids.add(fluidInput(Data_Energistics.MODID + ":data_corrosion_liquid", 250));
        root.add("fluid_inputs", fluids);

        var outputs = new JsonArray();
        outputs.add(itemOutput(itemId(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL), 1));
        root.add("item_outputs", outputs);

        root.add("key_input", lightningKey(512, LightningTier.EXTREME_HIGH_VOLTAGE));
        return root;
    }

    private static JsonObject overloadCrystal() {
        var root = conditions("ae2lt");
        root.addProperty("type", Data_Energistics.MODID + ":data_reassembler");

        var items = new JsonArray();
        items.add(itemInput(itemId(ModItems.OVERLOAD_CRYSTAL_DUST), 16));
        items.add(itemInput(itemId(ModItems.OVERLOAD_CRYSTAL), 16));
        root.add("item_inputs", items);

        var fluids = new JsonArray();
        fluids.add(fluidInput(Data_Energistics.MODID + ":data_corrosion_liquid", 250));
        root.add("fluid_inputs", fluids);

        var outputs = new JsonArray();
        outputs.add(itemOutput(itemId(ModItems.OVERLOAD_CRYSTAL), 72));
        root.add("item_outputs", outputs);

        root.add("key_input", lightningKey(8, LightningTier.HIGH_VOLTAGE));
        return root;
    }

    private static JsonObject electroChimeCrystal() {
        var root = conditions("ae2lt");
        root.addProperty("type", Data_Energistics.MODID + ":data_reassembler");

        var items = new JsonArray();
        items.add(itemInput(AEItems.FLUIX_CRYSTAL.id().toString(), 16));
        items.add(itemInput(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.id().toString(), 16));
        items.add(itemInput(itemId(ModItems.OVERLOAD_CRYSTAL), 16));
        root.add("item_inputs", items);

        var fluids = new JsonArray();
        fluids.add(fluidInput(Data_Energistics.MODID + ":data_corrosion_liquid", 250));
        root.add("fluid_inputs", fluids);

        var outputs = new JsonArray();
        outputs.add(itemOutput(itemId(ModItems.ELECTRO_CHIME_CRYSTAL), 2));
        root.add("item_outputs", outputs);

        root.add("key_input", lightningKey(16, LightningTier.HIGH_VOLTAGE));
        return root;
    }

    @Override
    public String getName() {
        return "AE2LT Recipes";
    }
}
