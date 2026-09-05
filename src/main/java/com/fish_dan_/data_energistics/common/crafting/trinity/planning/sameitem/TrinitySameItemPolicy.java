package com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem;

import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable request-local equivalence policy for explicitly authorised processing-output item domains.
 *
 * <p>
 * The policy never rewrites an {@link AEItemKey} or its backing {@code ItemStack}. It selects one existing exact key
 * as the accounting representative for each authorised registered item, while pattern identities and physical
 * inputs and outputs retain their complete data components.
 * </p>
 */
public final class TrinitySameItemPolicy {

    private static final TrinitySameItemPolicy EMPTY = new TrinitySameItemPolicy(List.of());

    private final Map<Item, AEItemKey> representativesByItem;
    private final Set<AEItemKey> representatives;

    private TrinitySameItemPolicy(Collection<AEItemKey> representatives) {
        Object2ObjectLinkedOpenHashMap<Item, AEItemKey> copied = new Object2ObjectLinkedOpenHashMap<>();
        ObjectLinkedOpenHashSet<AEItemKey> keys = new ObjectLinkedOpenHashSet<>();
        for (AEItemKey representative : representatives) {
            if (copied.putIfAbsent(representative.getItem(), representative) != null) {
                throw new IllegalArgumentException("A same-item policy requires one representative per item");
            }
            keys.add(representative);
        }
        this.representativesByItem = Object2ObjectMaps.unmodifiable(copied);
        this.representatives = ObjectSets.unmodifiable(keys);
    }

    /** Returns the exact-only policy used by unmarked graphs and legacy saved jobs. */
    public static TrinitySameItemPolicy empty() {
        return EMPTY;
    }

    /**
     * Captures all marker-authorised items from the complete graph and chooses deterministic request representatives.
     * The exact requested key is preferred for its own item so final-output accounting keeps the requested key.
     */
    public static TrinitySameItemPolicy fromGraph(TrinityCraftingGraphSnapshot graph, AEKey target) {
        Object2ObjectLinkedOpenHashMap<Item, AEItemKey> representatives = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityCraftingGraphPattern pattern : graph.patterns()) {
            if (!EncodedPatternDynamicOutput.isMarked(pattern.definition()) ||
                    !(pattern.outputs().getFirst().what() instanceof AEItemKey primaryOutput)) {
                continue;
            }
            representatives.putIfAbsent(primaryOutput.getItem(), primaryOutput);
        }
        if (target instanceof AEItemKey targetItem && representatives.containsKey(targetItem.getItem())) {
            representatives.put(targetItem.getItem(), targetItem);
        }
        return representatives.isEmpty() ? EMPTY : new TrinitySameItemPolicy(representatives.values());
    }

    /** Reconstructs a persisted policy from one representative for each authorised item. */
    public static TrinitySameItemPolicy ofRepresentatives(Collection<AEItemKey> representatives) {
        if (representatives.isEmpty()) {
            return EMPTY;
        }
        return new TrinitySameItemPolicy(representatives);
    }

    /** Returns whether this exact key belongs to an explicitly authorised registered-item domain. */
    public boolean allowsSameItem(AEKey key) {
        return key instanceof AEItemKey itemKey && this.representativesByItem.containsKey(itemKey.getItem());
    }

    /** Returns the request-local logical accounting key without modifying the supplied exact key. */
    public AEKey normalizeKey(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            AEItemKey representative = this.representativesByItem.get(itemKey.getItem());
            if (representative != null) {
                return representative;
            }
        }
        return key;
    }

    /** Merges signed exact-key amounts into their logical domains exactly once and removes zero balances. */
    public Map<AEKey, BigInteger> normalizeAmounts(Map<AEKey, BigInteger> amounts) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> normalized = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> normalized.merge(normalizeKey(key), amount, BigInteger::add));
        normalized.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Object2ObjectMaps.unmodifiable(normalized);
    }

    /** Merges positive stack amounts by logical accounting key without changing any source stack. */
    public List<GenericStack> normalizeStacks(List<GenericStack> stacks) {
        if (isEmpty()) {
            return stacks;
        }
        Object2LongLinkedOpenHashMap<AEKey> normalized = new Object2LongLinkedOpenHashMap<>();
        for (GenericStack stack : stacks) {
            normalized.mergeLong(normalizeKey(stack.what()), stack.amount(), Math::addExact);
        }
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>(normalized.size());
        normalized.object2LongEntrySet().forEach(
                entry -> result.add(new GenericStack(entry.getKey(), entry.getLongValue())));
        return List.copyOf(result);
    }

    /** Returns one stable representative per authorised registered item. */
    public Set<AEItemKey> representatives() {
        return this.representatives;
    }

    /** Returns normalized keys in deterministic first-occurrence order. */
    public List<AEKey> normalizeKeys(Collection<AEKey> keys) {
        ObjectLinkedOpenHashSet<AEKey> normalized = new ObjectLinkedOpenHashSet<>();
        keys.forEach(key -> normalized.add(normalizeKey(key)));
        return List.copyOf(normalized);
    }

    /** Returns whether no item domain is authorised. */
    public boolean isEmpty() {
        return this.representativesByItem.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TrinitySameItemPolicy policy &&
                this.representativesByItem.equals(policy.representativesByItem);
    }

    @Override
    public int hashCode() {
        return this.representativesByItem.hashCode();
    }

    @Override
    public String toString() {
        return "TrinitySameItemPolicy" + this.representatives;
    }
}
