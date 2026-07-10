package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicate;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicateTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a structure predicate with explicit item candidates for automatic placement.
 */
public record JsonMultiBlockPlacementPredicate(StructurePredicate delegate, List<ItemStack> placementCandidates)
        implements StructurePredicate {

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "placement_items");
    private static final String PREDICATE_PROPERTY = "predicate";
    private static final String ITEM_PROPERTY = "item";
    private static final String ITEMS_PROPERTY = "items";
    private static boolean registered;

    public JsonMultiBlockPlacementPredicate {
        placementCandidates = placementCandidates.stream().map(ItemStack::copy).toList();
        if (placementCandidates.isEmpty()) {
            throw new IllegalArgumentException("Placement item predicate requires at least one item candidate");
        }
    }

    public static synchronized void registerType() {
        if (registered) {
            return;
        }
        StructurePredicateTypes.register(TYPE, JsonMultiBlockPlacementPredicate::fromJson);
        registered = true;
    }

    public static JsonMultiBlockPlacementPredicate fromJson(JsonObject object) {
        StructurePredicate delegate = StructurePredicateTypes.decode(readRequiredObject(object, PREDICATE_PROPERTY));
        List<ItemStack> items = new ArrayList<>();
        for (String itemId : readItemIds(object)) {
            items.add(resolveItem(parseId(itemId, "item")).getDefaultInstance());
        }
        return new JsonMultiBlockPlacementPredicate(delegate, items);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public boolean test(MultiblockState state, boolean mutateCount) {
        return this.delegate.test(state, mutateCount);
    }

    @Override
    public boolean checkGlobalMinimum(MultiblockState state) {
        return this.delegate.checkGlobalMinimum(state);
    }

    @Override
    public boolean checkLayerMinimum(MultiblockState state) {
        return this.delegate.checkLayerMinimum(state);
    }

    @Override
    public List<Block> blockCandidates() {
        return this.delegate.blockCandidates();
    }

    @Override
    public List<BlockState> blockStateCandidates() {
        return this.delegate.blockStateCandidates();
    }

    @Override
    public List<ItemStack> placementCandidates() {
        return this.placementCandidates.stream().map(ItemStack::copy).toList();
    }

    @Override
    public boolean hasAir() {
        return this.delegate.hasAir();
    }

    private static List<String> readItemIds(JsonObject object) {
        if (object.has(ITEMS_PROPERTY)) {
            JsonArray array = readRequiredArray(object, ITEMS_PROPERTY);
            List<String> ids = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Placement item entries must be strings");
                }
                ids.add(element.getAsString());
            }
            return List.copyOf(ids);
        }
        return List.of(readRequiredString(object, ITEM_PROPERTY));
    }

    private static Item resolveItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!id.equals(BuiltInRegistries.ITEM.getKey(item))) {
            throw new IllegalStateException("Unknown item id in placement predicate: " + id);
        }
        return item;
    }

    private static ResourceLocation parseId(String raw, String label) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("Invalid " + label + " id: " + raw);
        }
        return id;
    }

    private static JsonObject readRequiredObject(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Placement item predicate requires object property '" + property + "'");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray readRequiredArray(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Placement item predicate requires array property '" + property + "'");
        }
        return element.getAsJsonArray();
    }

    private static String readRequiredString(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Placement item predicate requires string property '" + property + "'");
        }
        return element.getAsString();
    }
}
