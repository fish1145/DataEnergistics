package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

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
import java.util.Set;

/**
 * Matches a block and only the block state properties explicitly declared by JSON.
 */
public record JsonMultiBlockStatePropertiesPredicate(List<StatePattern> statePatterns)
        implements StructurePredicate {

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "block_state_properties");
    private static final String BLOCK_PROPERTY = "block";
    private static final String BLOCK_STATES_PROPERTY = "block_states";
    private static final String PROPERTIES_PROPERTY = "properties";
    private static boolean registered;

    public JsonMultiBlockStatePropertiesPredicate {
        statePatterns = List.copyOf(statePatterns);
        if (statePatterns.isEmpty()) {
            throw new IllegalArgumentException("Block state properties predicate must contain at least one state");
        }
    }

    public static synchronized void registerType() {
        if (registered) {
            return;
        }
        StructurePredicateTypes.register(TYPE, JsonMultiBlockStatePropertiesPredicate::fromJson);
        registered = true;
    }

    public static JsonMultiBlockStatePropertiesPredicate fromJson(JsonObject object) {
        if (object.has(BLOCK_STATES_PROPERTY)) {
            JsonArray states = readRequiredArray(object, BLOCK_STATES_PROPERTY);
            List<StatePattern> statePatterns = new ArrayList<>();
            for (JsonElement stateElement : states) {
                statePatterns.add(parseStatePattern(stateElement));
            }
            return new JsonMultiBlockStatePropertiesPredicate(statePatterns);
        }
        return new JsonMultiBlockStatePropertiesPredicate(List.of(parseStatePattern(object)));
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public boolean test(MultiblockState state, boolean mutateCount) {
        BlockState actualState = state.getBlockState();
        boolean matched = this.statePatterns.stream().anyMatch(pattern -> pattern.matches(actualState));
        if (!matched) {
            state.setDiagnostic(PatternDiagnostic.of(
                    "block_state_properties_mismatch",
                    "Block state predicate did not match",
                    state.getPos(),
                    expected()));
        }
        return matched;
    }

    @Override
    public List<Block> blockCandidates() {
        Set<Block> blocks = new LinkedHashSet<>();
        for (StatePattern pattern : this.statePatterns) {
            blocks.add(pattern.block());
        }
        return List.copyOf(blocks);
    }

    @Override
    public List<BlockState> blockStateCandidates() {
        return this.statePatterns.stream().map(StatePattern::preferredState).distinct().toList();
    }

    @Override
    public boolean hasAir() {
        return this.statePatterns.stream().anyMatch(pattern -> pattern.block() == Blocks.AIR);
    }

    private List<String> expected() {
        return this.statePatterns.stream().map(StatePattern::asExpectedString).toList();
    }

    private static StatePattern parseStatePattern(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Block state predicate entries must be objects");
        }
        JsonObject object = element.getAsJsonObject();
        Block block = resolveBlock(parseId(readRequiredString(object, BLOCK_PROPERTY), "block"));
        List<StatePropertyValue<?>> properties = new ArrayList<>();
        if (object.has(PROPERTIES_PROPERTY)) {
            JsonObject propertyObject = readRequiredObject(object, PROPERTIES_PROPERTY);
            StateDefinition<Block, BlockState> definition = block.getStateDefinition();
            for (String key : propertyObject.keySet()) {
                Property<?> property = definition.getProperty(key);
                if (property == null) {
                    throw new IllegalArgumentException("Unknown block state property '" + key + "' for " + block);
                }
                properties.add(parsePropertyValue(property, readRequiredString(propertyObject, key)));
            }
        }
        return new StatePattern(block, properties);
    }

    private static <T extends Comparable<T>> StatePropertyValue<T> parsePropertyValue(Property<T> property,
                                                                                      String rawValue) {
        T value = property.getValue(rawValue).orElseThrow(
                () -> new IllegalArgumentException("Invalid value '" + rawValue + "' for property " +
                        property.getName()));
        return new StatePropertyValue<>(property, value);
    }

    private static Block resolveBlock(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (!id.equals(BuiltInRegistries.BLOCK.getKey(block))) {
            throw new IllegalStateException("Unknown block id in structure predicate: " + id);
        }
        return block;
    }

    private static ResourceLocation parseId(String raw, String label) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("Invalid " + label + " id: " + raw);
        }
        return id;
    }

    private static String readRequiredString(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Block state properties predicate requires string property '" +
                    property + "'");
        }
        return element.getAsString();
    }

    private static JsonObject readRequiredObject(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Block state properties predicate requires object property '" +
                    property + "'");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray readRequiredArray(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Block state properties predicate requires array property '" +
                    property + "'");
        }
        return element.getAsJsonArray();
    }

    public record StatePattern(Block block, List<StatePropertyValue<?>> properties) {

        public StatePattern {
            properties = List.copyOf(properties);
        }

        boolean matches(BlockState actualState) {
            if (actualState.getBlock() != this.block) {
                return false;
            }
            for (StatePropertyValue<?> property : this.properties) {
                if (!property.matches(actualState)) {
                    return false;
                }
            }
            return true;
        }

        BlockState preferredState() {
            BlockState state = this.block.defaultBlockState();
            for (StatePropertyValue<?> property : this.properties) {
                state = property.apply(state);
            }
            return state;
        }

        String asExpectedString() {
            String blockId = BuiltInRegistries.BLOCK.getKey(this.block).toString();
            if (this.properties.isEmpty()) {
                return blockId;
            }
            return blockId + "[" + String.join(",", this.properties.stream()
                    .map(StatePropertyValue::asExpectedString)
                    .toList()) + "]";
        }
    }

    public record StatePropertyValue<T extends Comparable<T>>(Property<T> property, T value) {

        boolean matches(BlockState actualState) {
            return actualState.getValue(this.property).equals(this.value);
        }

        BlockState apply(BlockState state) {
            return state.setValue(this.property, this.value);
        }

        String asExpectedString() {
            return this.property.getName() + "=" + this.value;
        }
    }
}
