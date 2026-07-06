package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicate;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicateTypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Predicate wrapper that allows selected normal structure blocks to be replaced by declared compartment roles.
 */
public record JsonMultiBlockReplaceableCompartmentPredicate(Set<CompartmentType> compartmentTypes,
                                                            StructurePredicate delegate)
        implements StructurePredicate {

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "replaceable_compartment");
    private static final String COMPARTMENTS_PROPERTY = "compartments";
    private static final String PREDICATE_PROPERTY = "predicate";
    private static boolean registered;

    public JsonMultiBlockReplaceableCompartmentPredicate {
        if (compartmentTypes == null || compartmentTypes.isEmpty()) {
            throw new IllegalArgumentException("Replaceable compartment predicate requires at least one compartment type");
        }
        compartmentTypes = Set.copyOf(compartmentTypes);
        delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static synchronized void registerType() {
        if (registered) {
            return;
        }
        StructurePredicateTypes.register(TYPE, JsonMultiBlockReplaceableCompartmentPredicate::fromJson);
        registered = true;
    }

    public static JsonMultiBlockReplaceableCompartmentPredicate fromJson(JsonObject object) {
        if (object == null) {
            throw new IllegalArgumentException("Replaceable compartment predicate JSON cannot be null");
        }
        return new JsonMultiBlockReplaceableCompartmentPredicate(
                readCompartmentTypes(object),
                StructurePredicateTypes.decode(readRequiredObject(object, PREDICATE_PROPERTY)));
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public boolean test(MultiblockState state, boolean mutateCount) {
        if (state.getBlockState().getBlock() instanceof CompartmentBlock block) {
            CompartmentType actualType = block.compartmentType();
            if (!this.compartmentTypes.contains(actualType)) {
                state.setDiagnostic(PatternDiagnostic.of(
                        "replaceable_compartment_mismatch",
                        "Replaceable structure slot did not accept this compartment role",
                        state.getPos(),
                        expected()));
                return false;
            }
            JsonMultiBlockCompartmentPredicate.recordMatchedCompartment(
                    state.getMatchContext(),
                    state.getPos(),
                    actualType);
            return true;
        }
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
        ArrayList<Block> candidates = new ArrayList<>(this.delegate.blockCandidates());
        for (CompartmentType type : this.compartmentTypes) {
            candidates.add(JsonMultiBlockCompartmentPredicate.blockFor(type));
        }
        return List.copyOf(candidates);
    }

    private List<String> expected() {
        ArrayList<String> values = new ArrayList<>();
        for (CompartmentType type : this.compartmentTypes) {
            values.add(type.id());
        }
        return List.copyOf(values);
    }

    private static Set<CompartmentType> readCompartmentTypes(JsonObject object) {
        JsonElement compartmentsElement = object.get(COMPARTMENTS_PROPERTY);
        if (compartmentsElement == null || !compartmentsElement.isJsonArray()) {
            throw new IllegalArgumentException("Replaceable compartment predicate requires array property '" +
                    COMPARTMENTS_PROPERTY + "'");
        }
        JsonArray compartments = compartmentsElement.getAsJsonArray();
        LinkedHashSet<CompartmentType> types = new LinkedHashSet<>();
        for (JsonElement compartmentElement : compartments) {
            if (!compartmentElement.isJsonPrimitive() || !compartmentElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Replaceable compartment type entries must be strings");
            }
            String typeId = compartmentElement.getAsString();
            CompartmentType type = CompartmentType.byId(typeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown replaceable compartment type: " + typeId));
            types.add(type);
        }
        return Set.copyOf(types);
    }

    private static JsonObject readRequiredObject(JsonObject object, String property) {
        if (!object.has(property) || !object.get(property).isJsonObject()) {
            throw new IllegalArgumentException("Replaceable compartment predicate requires object property '" + property + "'");
        }
        return object.get(property).getAsJsonObject();
    }
}
