package com.fish_dan_.data_energistics.common.multiblock.json.matching;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonObject;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.PatternMatchContext;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicate;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicateTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MDLib predicate wrapper that requires a JSON symbol to be a declared compartment role.
 */
public record JsonMultiBlockCompartmentPredicate(CompartmentType compartmentType,
                                                 @Nullable StructurePredicate delegate)
        implements StructurePredicate {

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "compartment");
    private static final String MATCHED_COMPARTMENTS_CONTEXT_KEY = Data_Energistics.MODID + ":matched_compartments";
    private static final String COMPARTMENT_PROPERTY = "compartment";
    private static final String PREDICATE_PROPERTY = "predicate";
    private static boolean registered;

    public static synchronized void registerType() {
        if (registered) {
            return;
        }
        StructurePredicateTypes.register(TYPE, JsonMultiBlockCompartmentPredicate::fromJson);
        registered = true;
    }

    public static JsonMultiBlockCompartmentPredicate fromJson(JsonObject object) {
        CompartmentType compartmentType = CompartmentType.byId(readRequiredString(object, COMPARTMENT_PROPERTY))
                .orElseThrow(() -> new IllegalArgumentException("Unknown compartment predicate type: " +
                        object.get(COMPARTMENT_PROPERTY)));
        StructurePredicate delegate = object.has(PREDICATE_PROPERTY) ? StructurePredicateTypes.decode(readRequiredObject(object, PREDICATE_PROPERTY)) : null;
        return new JsonMultiBlockCompartmentPredicate(compartmentType, delegate);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public boolean test(MultiblockState state, boolean mutateCount) {
        if (!(state.getBlockState().getBlock() instanceof CompartmentBlock block) ||
                block.compartmentType() != this.compartmentType) {
            state.setDiagnostic(PatternDiagnostic.of(
                    "compartment_mismatch",
                    "Compartment predicate did not match declared role",
                    state.getPos(),
                    expected()));
            return false;
        }
        if (this.delegate != null && !this.delegate.test(state, mutateCount)) {
            return false;
        }
        matchedCompartments(state.getMatchContext()).put(state.getPos().asLong(), this.compartmentType);
        return true;
    }

    @Override
    public boolean checkGlobalMinimum(MultiblockState state) {
        return this.delegate == null || this.delegate.checkGlobalMinimum(state);
    }

    @Override
    public boolean checkLayerMinimum(MultiblockState state) {
        return this.delegate == null || this.delegate.checkLayerMinimum(state);
    }

    @Override
    public List<Block> blockCandidates() {
        return List.of(blockFor(this.compartmentType));
    }

    @Override
    public List<BlockState> blockStateCandidates() {
        return List.of(blockFor(this.compartmentType).defaultBlockState());
    }

    @Override
    public List<ItemStack> placementCandidates() {
        return List.of(blockFor(this.compartmentType).asItem().getDefaultInstance());
    }

    private List<String> expected() {
        return List.of(BuiltInRegistries.BLOCK.getKey(blockFor(this.compartmentType)).toString());
    }

    public static Map<BlockPos, CompartmentType> declaredCompartments(PatternMatchContext context) {
        Long2ObjectMap<CompartmentType> matchedCompartments = context.get(
                MATCHED_COMPARTMENTS_CONTEXT_KEY,
                Long2ObjectMap.class);
        if (matchedCompartments == null || matchedCompartments.isEmpty()) {
            return Map.of();
        }
        Map<BlockPos, CompartmentType> compartments = new LinkedHashMap<>();
        for (Long2ObjectMap.Entry<CompartmentType> entry : matchedCompartments.long2ObjectEntrySet()) {
            compartments.put(BlockPos.of(entry.getLongKey()), entry.getValue());
        }
        return Map.copyOf(compartments);
    }

    private static Long2ObjectOpenHashMap<CompartmentType> matchedCompartments(PatternMatchContext context) {
        return context.getOrCreate(
                MATCHED_COMPARTMENTS_CONTEXT_KEY,
                Long2ObjectOpenHashMap.class,
                Long2ObjectOpenHashMap::new);
    }

    public static void recordMatchedCompartment(PatternMatchContext context, BlockPos pos, CompartmentType type) {
        matchedCompartments(context).put(pos.asLong(), type);
    }

    public static Block blockFor(CompartmentType type) {
        return switch (type) {
            case INPUT -> DEBlocks.COMPOSITE_INPUT_WAREHOUSE.get();
            case OUTPUT -> DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get();
            case ME_INPUT -> DEBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get();
            case ME_OUTPUT -> DEBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get();
            case PATTERN_BUFFER -> DEBlocks.ME_PATTERN_BUFFER.get();
            case TRINITY_ACCESS -> DEBlocks.TRINITY_ACCESS_HATCH.get();
        };
    }

    private static String readRequiredString(JsonObject object, String property) {
        if (!object.has(property) || !object.get(property).isJsonPrimitive() ||
                !object.get(property).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Compartment predicate requires string property '" + property + "'");
        }
        return object.get(property).getAsString();
    }

    private static JsonObject readRequiredObject(JsonObject object, String property) {
        if (!object.has(property) || !object.get(property).isJsonObject()) {
            throw new IllegalArgumentException("Compartment predicate requires object property '" + property + "'");
        }
        return object.get(property).getAsJsonObject();
    }
}
