package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Atomically published, read-only derivation of every crafting transition visible on one grid revision.
 *
 * <p>
 * The snapshot contains value objects only. It is safe to hand to planner threads because it never retains a grid,
 * level, block entity, provider, decoded pattern implementation, or registry lookup.
 * </p>
 */
public final class TrinityCraftingGraphSnapshot {

    private final long revision;
    private final List<TrinityCraftingGraphPattern> patterns;
    private final List<AEKey> keys;
    private final Map<AEKey, List<TrinityCraftingGraphPattern>> patternsByOutput;

    /**
     * Builds a deterministic graph and rejects duplicate semantic identities.
     *
     * @param revision crafting-provider revision captured for the complete graph
     * @param patterns immutable pattern values captured for that revision
     */
    public TrinityCraftingGraphSnapshot(long revision, List<TrinityCraftingGraphPattern> patterns) {
        if (revision < 0L) {
            throw new IllegalArgumentException("A Trinity crafting graph revision cannot be negative");
        }
        TreeMap<TrinityPatternIdentity, TrinityCraftingGraphPattern> sortedPatterns = new TreeMap<>();
        for (TrinityCraftingGraphPattern pattern : patterns) {
            if (sortedPatterns.putIfAbsent(pattern.identity(), pattern) != null) {
                throw new IllegalArgumentException(
                        "A Trinity crafting graph cannot contain duplicate pattern identity " + pattern.identity());
            }
        }

        this.revision = revision;
        this.patterns = List.copyOf(sortedPatterns.values());

        LinkedHashSet<AEKey> encounteredKeys = new LinkedHashSet<>();
        LinkedHashMap<AEKey, LinkedHashSet<TrinityCraftingGraphPattern>> producerSets = new LinkedHashMap<>();
        for (TrinityCraftingGraphPattern pattern : this.patterns) {
            for (TrinityPatternPublicationSignature.Input input : pattern.inputs()) {
                for (TrinityPatternPublicationSignature.Alternative alternative : input.alternatives()) {
                    encounteredKeys.add(alternative.stack().what());
                    if (alternative.remainingKey() != null) {
                        encounteredKeys.add(alternative.remainingKey());
                        producerSets
                                .computeIfAbsent(alternative.remainingKey(), ignored -> new LinkedHashSet<>())
                                .add(pattern);
                    }
                }
            }
            for (GenericStack output : pattern.outputs()) {
                encounteredKeys.add(output.what());
                producerSets.computeIfAbsent(output.what(), ignored -> new LinkedHashSet<>()).add(pattern);
            }
        }

        LinkedHashMap<AEKey, List<TrinityCraftingGraphPattern>> producerIndex = new LinkedHashMap<>();
        producerSets.forEach((key, producers) -> producerIndex.put(key, List.copyOf(producers)));
        this.keys = List.copyOf(encounteredKeys);
        this.patternsByOutput = Collections.unmodifiableMap(producerIndex);
    }

    /**
     * @return provider revision represented by every value in this snapshot
     */
    public long revision() {
        return this.revision;
    }

    /**
     * @return patterns sorted by stable full semantic identity
     */
    public List<TrinityCraftingGraphPattern> patterns() {
        return this.patterns;
    }

    /**
     * @return every input or output key in deterministic first-semantic-occurrence order
     */
    public List<AEKey> keys() {
        return this.keys;
    }

    /**
     * Resolves all transitions that produce a key, including transitions where it is a byproduct.
     *
     * @param key requested graph node
     * @return immutable identity-sorted producer list
     */
    public List<TrinityCraftingGraphPattern> patternsProducing(AEKey key) {
        return this.patternsByOutput.getOrDefault(key, List.of());
    }

    /**
     * @return immutable producer index for algorithms that traverse several output nodes
     */
    public Map<AEKey, List<TrinityCraftingGraphPattern>> patternsByOutput() {
        return this.patternsByOutput;
    }

    /**
     * Derives the complete reverse-reachable hypergraph for one requested output.
     *
     * <p>
     * Every producer route and every legal input alternative remains present. Patterns unrelated to the requested
     * output are excluded before binding expansion and SCC analysis, so planning work follows the request graph
     * instead of the size of the whole grid catalog. Server-lifetime callers cache this pure derivation in the shared
     * bounded computation cache.
     * </p>
     *
     * @param target requested output key
     * @return immutable target-reachable snapshot with the same publication revision
     */
    public TrinityCraftingGraphSnapshot reachableSubgraph(AEKey target) {
        if (target == null) {
            throw new IllegalArgumentException("A Trinity reachable graph requires a target");
        }
        return deriveReachableSubgraph(target);
    }

    private TrinityCraftingGraphSnapshot deriveReachableSubgraph(AEKey target) {
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        LinkedHashSet<AEKey> visitedKeys = new LinkedHashSet<>();
        LinkedHashSet<TrinityCraftingGraphPattern> reachablePatterns = new LinkedHashSet<>();
        pending.add(target);
        while (!pending.isEmpty()) {
            AEKey required = pending.removeFirst();
            if (!visitedKeys.add(required)) {
                continue;
            }
            for (TrinityCraftingGraphPattern pattern : patternsProducing(required)) {
                if (!reachablePatterns.add(pattern)) {
                    continue;
                }
                for (TrinityPatternPublicationSignature.Input input : pattern.inputs()) {
                    for (TrinityPatternPublicationSignature.Alternative alternative : input.alternatives()) {
                        pending.addLast(alternative.stack().what());
                    }
                }
            }
        }
        if (reachablePatterns.size() == this.patterns.size()) {
            return this;
        }
        return new TrinityCraftingGraphSnapshot(this.revision, List.copyOf(reachablePatterns));
    }
}
