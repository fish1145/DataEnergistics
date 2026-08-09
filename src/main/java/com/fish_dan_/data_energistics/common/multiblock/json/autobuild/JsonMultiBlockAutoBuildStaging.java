package com.fish_dan_.data_energistics.common.multiblock.json.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPlacementPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockReplaceableCompartmentPredicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.parts.IPartItem;
import com.modularmc.mdl.api.multiblock.PatternCandidate;
import com.modularmc.mdl.api.multiblock.json.StructurePatternResolver.StringArrayDefinition;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable auto-build staging permissions derived from one resolved JSON multiblock definition.
 *
 * <p>
 * The declaration is fail-closed: only exact states and part items selected by JSON metadata are accepted. The
 * runtime object is built with the same definition instance as the pattern, so a resource reload cannot combine a new
 * pattern with an old staging permission set.
 * </p>
 */
public final class JsonMultiBlockAutoBuildStaging {

    private static final JsonMultiBlockAutoBuildStaging NONE = new JsonMultiBlockAutoBuildStaging(Map.of(), Map.of());

    private final Map<BlockState, Boolean> blockStaging;
    private final Map<Item, BlockState> partHosts;

    private JsonMultiBlockAutoBuildStaging(Map<BlockState, Boolean> blockStaging, Map<Item, BlockState> partHosts) {
        this.blockStaging = Map.copyOf(blockStaging);
        this.partHosts = Map.copyOf(partHosts);
    }

    /**
     * Returns the empty fail-closed staging declaration for definitions with no auto-build metadata.
     */
    public static JsonMultiBlockAutoBuildStaging none() {
        return NONE;
    }

    public static JsonMultiBlockAutoBuildStaging resolve(ResourceLocation resourceId,
                                                         JsonMultiBlockAutoBuildStagingMetadata metadata,
                                                         StringArrayDefinition definition) {
        if (metadata.isEmpty()) {
            return NONE;
        }
        Set<Character> usedSymbols = usedSymbols(definition);
        Map<Character, StructurePredicate> predicates = definition.predicates();
        LinkedHashMap<BlockState, Boolean> blockStaging = new LinkedHashMap<>();
        LinkedHashMap<Item, BlockState> partHosts = new LinkedHashMap<>();

        for (String symbol : metadata.blockSymbols()) {
            StructurePredicate predicate = requiredPredicate(resourceId, symbol, usedSymbols, predicates);
            boolean physical = metadata.physicalBlockSymbols().contains(symbol);
            for (BlockState state : requiredBaseBlockStates(resourceId, symbol, predicate)) {
                if (physical && state.hasBlockEntity()) {
                    throw new IllegalArgumentException("JSON multiblock physical staging symbol '" + symbol +
                            "' resolves a block-entity state: " + resourceId);
                }
                addBlockState(resourceId, symbol, blockStaging, state, physical);
            }
        }
        for (String symbol : metadata.replaceableCompartmentSymbols()) {
            StructurePredicate predicate = requiredPredicate(resourceId, symbol, usedSymbols, predicates);
            if (!(predicate instanceof JsonMultiBlockReplaceableCompartmentPredicate replaceablePredicate)) {
                throw new IllegalArgumentException("JSON multiblock replaceable compartment staging symbol '" + symbol +
                        "' does not declare a replaceable compartment predicate: " + resourceId);
            }
            for (BlockState state : replaceableCompartmentStates(replaceablePredicate)) {
                addBlockState(resourceId, symbol, blockStaging, state, false);
            }
        }
        for (String symbol : metadata.partHostSymbols()) {
            StructurePredicate predicate = requiredPredicate(resourceId, symbol, usedSymbols, predicates);
            if (!(predicate instanceof JsonMultiBlockPlacementPredicate placementPredicate)) {
                throw new IllegalArgumentException("JSON multiblock part host staging symbol '" + symbol +
                        "' must use data_energistics:placement_items: " + resourceId);
            }
            List<BlockState> hostStates = placementPredicate.blockStateCandidates().stream().distinct().toList();
            if (hostStates.size() != 1) {
                throw new IllegalArgumentException("JSON multiblock part host staging symbol '" + symbol +
                        "' must resolve exactly one host state: " + resourceId);
            }
            List<ItemStack> partCandidates = placementPredicate.placementCandidates();
            if (partCandidates.isEmpty()) {
                throw new IllegalArgumentException("JSON multiblock part host staging symbol '" + symbol +
                        "' must declare at least one part item: " + resourceId);
            }
            for (ItemStack partCandidate : partCandidates) {
                if (!(partCandidate.getItem() instanceof IPartItem<?>)) {
                    throw new IllegalArgumentException("JSON multiblock part host staging symbol '" + symbol +
                            "' contains a non-AE2-part item: " + resourceId);
                }
                addPartHost(resourceId, symbol, partHosts, partCandidate.getItem(), hostStates.getFirst());
            }
        }
        rejectAmbiguousUnmarkedCandidates(resourceId, metadata, predicates, blockStaging, partHosts);
        return new JsonMultiBlockAutoBuildStaging(blockStaging, partHosts);
    }

    /**
     * Returns whether the exact final block state is authorized for staged auto-build placement.
     */
    public boolean allowsBlock(BlockState desiredState) {
        return this.blockStaging.containsKey(desiredState);
    }

    /**
     * Returns whether the exact final state is authorized for a real-world pre-publication write.
     */
    public boolean allowsPhysicalBlock(BlockState desiredState) {
        return Boolean.TRUE.equals(this.blockStaging.get(desiredState));
    }

    /**
     * Returns the JSON-declared temporary host state for one exact AE2 part item, or {@code null} when not declared.
     */
    @Nullable
    public BlockState partHostState(ItemStack partStack) {
        return this.partHosts.get(partStack.getItem());
    }

    private static Set<Character> usedSymbols(StringArrayDefinition definition) {
        LinkedHashSet<Character> symbols = new LinkedHashSet<>();
        definition.units().forEach(unit -> unit.slices().forEach(slice -> {
            for (String row : slice) {
                for (int index = 0; index < row.length(); index++) {
                    symbols.add(row.charAt(index));
                }
            }
        }));
        return Set.copyOf(symbols);
    }

    private static StructurePredicate requiredPredicate(ResourceLocation resourceId,
                                                        String symbol,
                                                        Set<Character> usedSymbols,
                                                        Map<Character, StructurePredicate> predicates) {
        char character = symbol.charAt(0);
        if (!usedSymbols.contains(character)) {
            throw new IllegalArgumentException("JSON multiblock auto-build staging symbol '" + symbol +
                    "' is not used by the pattern: " + resourceId);
        }
        StructurePredicate predicate = predicates.get(character);
        if (predicate == null) {
            throw new IllegalArgumentException("JSON multiblock auto-build staging symbol '" + symbol +
                    "' has no predicate: " + resourceId);
        }
        return predicate;
    }

    private static List<BlockState> requiredBaseBlockStates(ResourceLocation resourceId,
                                                            String symbol,
                                                            StructurePredicate predicate) {
        List<BlockState> states = blockPlacementStates(basePredicate(predicate));
        if (states.isEmpty()) {
            throw new IllegalArgumentException("JSON multiblock block staging symbol '" + symbol +
                    "' has no exact BlockItem state candidate: " + resourceId);
        }
        return states;
    }

    private static StructurePredicate basePredicate(StructurePredicate predicate) {
        if (predicate instanceof JsonMultiBlockReplaceableCompartmentPredicate replaceablePredicate) {
            return replaceablePredicate.delegate();
        }
        return predicate;
    }

    private static List<BlockState> blockPlacementStates(StructurePredicate predicate) {
        LinkedHashSet<BlockState> states = new LinkedHashSet<>();
        for (PatternCandidate candidate : predicate.patternCandidates()) {
            if (candidate.placementStack().getItem() instanceof BlockItem) {
                states.add(candidate.previewState());
            }
        }
        return List.copyOf(states);
    }

    private static List<BlockState> replaceableCompartmentStates(
                                                                 JsonMultiBlockReplaceableCompartmentPredicate replaceablePredicate) {
        ArrayList<BlockState> states = new ArrayList<>();
        for (var type : replaceablePredicate.compartmentTypes()) {
            Block block = JsonMultiBlockCompartmentPredicate.blockFor(type);
            if (!(block.asItem() instanceof BlockItem)) {
                throw new IllegalStateException("Replaceable compartment does not expose a BlockItem: " + type);
            }
            states.add(block.defaultBlockState());
        }
        return states.stream().distinct().toList();
    }

    private static void addBlockState(ResourceLocation resourceId,
                                      String symbol,
                                      Map<BlockState, Boolean> blockStaging,
                                      BlockState state,
                                      boolean physical) {
        Boolean previous = blockStaging.putIfAbsent(state, physical);
        if (previous != null && previous != physical) {
            throw new IllegalArgumentException("JSON multiblock staging state has conflicting physical modes for symbol '" +
                    symbol + "': " + resourceId);
        }
    }

    private static void addPartHost(ResourceLocation resourceId,
                                    String symbol,
                                    Map<Item, BlockState> partHosts,
                                    Item partItem,
                                    BlockState hostState) {
        BlockState previous = partHosts.putIfAbsent(partItem, hostState);
        if (previous != null && !previous.equals(hostState)) {
            throw new IllegalArgumentException("JSON multiblock part item has conflicting host states for symbol '" +
                    symbol + "': " + resourceId);
        }
    }

    private static void rejectAmbiguousUnmarkedCandidates(ResourceLocation resourceId,
                                                          JsonMultiBlockAutoBuildStagingMetadata metadata,
                                                          Map<Character, StructurePredicate> predicates,
                                                          Map<BlockState, Boolean> blockStaging,
                                                          Map<Item, BlockState> partHosts) {
        for (Map.Entry<Character, StructurePredicate> entry : predicates.entrySet()) {
            String symbol = Character.toString(entry.getKey());
            StructurePredicate predicate = entry.getValue();
            if (!metadata.blockSymbols().contains(symbol)) {
                for (BlockState state : blockPlacementStates(basePredicate(predicate))) {
                    if (blockStaging.containsKey(state)) {
                        throw new IllegalArgumentException("JSON multiblock unmarked block symbol '" + symbol +
                                "' shares a staged state: " + resourceId);
                    }
                }
            }
            if (predicate instanceof JsonMultiBlockReplaceableCompartmentPredicate replaceablePredicate &&
                    !metadata.replaceableCompartmentSymbols().contains(symbol)) {
                for (BlockState state : replaceableCompartmentStates(replaceablePredicate)) {
                    if (blockStaging.containsKey(state)) {
                        throw new IllegalArgumentException("JSON multiblock unmarked replaceable compartment symbol '" +
                                symbol + "' shares a staged state: " + resourceId);
                    }
                }
            }
            if (!metadata.partHostSymbols().contains(symbol)) {
                for (ItemStack candidate : predicate.placementCandidates()) {
                    if (candidate.getItem() instanceof IPartItem<?> && partHosts.containsKey(candidate.getItem())) {
                        throw new IllegalArgumentException("JSON multiblock unmarked part symbol '" + symbol +
                                "' shares a staged part item: " + resourceId);
                    }
                }
            }
        }
    }
}
