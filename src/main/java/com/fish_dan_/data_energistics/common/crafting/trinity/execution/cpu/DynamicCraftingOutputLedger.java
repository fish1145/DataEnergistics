package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.common.crafting.dynamic.DynamicCraftingOutputResolutionException;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Durable exact-template ledger for runtime outputs that may return a different item-component variant.
 */
final class DynamicCraftingOutputLedger {

    private static final String KEY_TAG = "planned_key";
    private static final String AMOUNT_TAG = "remaining";
    private static final String ROUTE_TAG = "route";
    private static final String SOURCE_TAG = "source";
    private static final String WAITING_TAG = "waiting";
    private static final String INPUT_ALIASES_TAG = "same_item_inputs";
    private static final String ACTUAL_KEY_TAG = "actual_key";

    private final ObjectArrayList<MutableEntry> entries = new ObjectArrayList<>();
    private final Object2LongLinkedOpenHashMap<AEItemKey> inputAliases = new Object2LongLinkedOpenHashMap<>();

    /**
     * Output ownership route selected at provider-commit time.
     */
    enum Route {
        INVENTORY,
        FINAL_OUTPUT
    }

    /**
     * One amount registered by a successful provider submission.
     *
     * @param plannedKey exact key whose waiting counter remains authoritative
     * @param amount     positive accepted amount
     * @param route      destination for the actual runtime key
     * @param source     adapter ID or request-local manual source
     */
    record Registration(AEItemKey plannedKey,
                        long amount,
                        Route route,
                        ResourceLocation source) {

        Registration {
            if (amount <= 0L) {
                throw new IllegalArgumentException("A dynamic output registration must be positive");
            }
        }
    }

    /**
     * Immutable acceptance selected without mutating the ledger.
     *
     * @param plannedKey exact waiting key to deduct
     * @param amount     maximum accepted actual amount
     * @param route      actual-key destination
     * @param source     persisted semantic source
     */
    record Match(AEItemKey plannedKey,
                 long amount,
                 Route route,
                 ResourceLocation source) {}

    /**
     * Whether a new push can coexist with all active same-item matching domains.
     */
    enum DispatchSafety {
        SAFE,
        CONFLICT
    }

    boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /**
     * Rejects intrinsically ambiguous declarations and defers transient conflicts with already in-flight outputs.
     */
    DispatchSafety evaluate(KeyCounter waitingFor,
                            List<GenericStack> expectedPhysicalOutputs,
                            List<Registration> registrations) {
        Map<Item, Domain> activeDomains = domains(this.entries.stream()
                .map(MutableEntry::registration)
                .toList());
        Map<Item, Domain> newDomains = domains(registrations);
        Map<Item, List<AEItemKey>> expectedDomains = expectedDomains(expectedPhysicalOutputs);

        for (Map.Entry<Item, Domain> dynamic : newDomains.entrySet()) {
            List<AEItemKey> expectedKeys = expectedDomains.getOrDefault(dynamic.getKey(), List.of());
            if (expectedKeys.stream().anyMatch(key -> !key.equals(dynamic.getValue().plannedKey()))) {
                throw new DynamicCraftingOutputResolutionException(
                        "One provider push exposes multiple component templates in dynamic item domain " +
                                dynamic.getValue().plannedKey().getItem());
            }
            long expected = expectedPhysicalOutputs.stream()
                    .filter(stack -> stack.what().equals(dynamic.getValue().plannedKey()))
                    .mapToLong(GenericStack::amount)
                    .reduce(0L, Math::addExact);
            long registered = registrations.stream()
                    .filter(value -> value.plannedKey().equals(dynamic.getValue().plannedKey()))
                    .mapToLong(Registration::amount)
                    .reduce(0L, Math::addExact);
            if (registered > expected) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output declaration exceeds the provider push output for " +
                                dynamic.getValue().plannedKey());
            }
        }

        for (Map.Entry<Item, List<AEItemKey>> expected : expectedDomains.entrySet()) {
            Domain active = activeDomains.get(expected.getKey());
            Domain incoming = newDomains.get(expected.getKey());
            if (active != null && (incoming == null || !active.compatible(incoming))) {
                return DispatchSafety.CONFLICT;
            }
        }

        for (Map.Entry<Item, Domain> incoming : newDomains.entrySet()) {
            Domain active = activeDomains.get(incoming.getKey());
            if (active != null) {
                if (!active.compatible(incoming.getValue())) {
                    return DispatchSafety.CONFLICT;
                }
                for (var waiting : waitingFor) {
                    if (waiting.getKey() instanceof AEItemKey itemKey &&
                            itemKey.getItem() == incoming.getKey() &&
                            !itemKey.equals(active.plannedKey()) && waiting.getLongValue() > 0L) {
                        return DispatchSafety.CONFLICT;
                    }
                }
                continue;
            }
            for (var waiting : waitingFor) {
                if (waiting.getKey() instanceof AEItemKey itemKey &&
                        itemKey.getItem() == incoming.getKey() && waiting.getLongValue() > 0L) {
                    return DispatchSafety.CONFLICT;
                }
            }
        }
        return DispatchSafety.SAFE;
    }

    void register(List<Registration> registrations) {
        for (Registration registration : registrations) {
            MutableEntry existing = this.entries.stream()
                    .filter(entry -> entry.matches(registration))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                this.entries.add(new MutableEntry(registration));
            } else {
                existing.remaining = Math.addExact(existing.remaining, registration.amount());
            }
        }
    }

    /**
     * Withdraws only uncompleted registrations identified by their frozen key, route and source.
     * Duplicate requests are summed before checking remaining amounts. Every lookup and amount check
     * completes before returning, so an invalid cancellation cannot partially consume another registration.
     * This does not remove actual input aliases or adjust the CPU's separate exact waiting counter.
     * The returned one-shot action must run in the same server callback without intervening ledger mutations.
     *
     * @param cancelledRegistrations positive cancelled amounts, not the original accepted totals
     * @throws IllegalStateException when an exact registration is absent or has insufficient remaining amount
     * @throws ArithmeticException   when duplicate cancellation amounts overflow the long registration domain
     */
    Runnable prepareWithdrawal(List<Registration> cancelledRegistrations) {
        Object2LongLinkedOpenHashMap<MutableEntry> withdrawals = new Object2LongLinkedOpenHashMap<>();
        for (Registration registration : cancelledRegistrations) {
            MutableEntry existing = this.entries.stream()
                    .filter(entry -> entry.matches(registration))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cancelled dynamic output registration is absent: " + registration));
            withdrawals.mergeLong(existing, registration.amount(), Math::addExact);
        }
        for (var withdrawal : withdrawals.object2LongEntrySet()) {
            if (withdrawal.getLongValue() > withdrawal.getKey().remaining) {
                throw new IllegalStateException("Cancelled dynamic output exceeds its uncompleted registration: " +
                        withdrawal.getKey().registration());
            }
            withdrawal.setValue(withdrawal.getKey().remaining - withdrawal.getLongValue());
        }
        return new Runnable() {

            private boolean applied;

            @Override
            public void run() {
                if (applied) {
                    throw new IllegalStateException("A prepared dynamic withdrawal may only be applied once");
                }
                applied = true;
                withdrawals.object2LongEntrySet().forEach(entry -> entry.getKey().remaining = entry.getLongValue());
                removeEmpty();
            }
        };
    }

    /**
     * Finds a same-item entry after the exact waiting path has rejected the remaining actual stack.
     */
    Optional<Match> match(AEItemKey actualKey, long maximumAmount, KeyCounter waitingFor) {
        if (maximumAmount <= 0L) {
            return Optional.empty();
        }
        for (MutableEntry entry : this.entries) {
            if (entry.plannedKey.getItem() != actualKey.getItem()) {
                continue;
            }
            long exactWaiting = waitingFor.get(entry.plannedKey);
            long amount = Math.min(maximumAmount, Math.min(entry.remaining, exactWaiting));
            if (amount > 0L) {
                return Optional.of(entry.match(amount));
            }
        }
        return Optional.empty();
    }

    /**
     * Deducts exact output receipts from a compatible dynamic allowance first, releasing its item domain promptly.
     */
    void consumeExact(AEKey exactKey, long amount) {
        long remaining = amount;
        for (MutableEntry entry : this.entries) {
            if (!entry.plannedKey.equals(exactKey) || remaining == 0L) {
                continue;
            }
            long consumed = Math.min(remaining, entry.remaining);
            entry.remaining -= consumed;
            remaining -= consumed;
        }
        removeEmpty();
    }

    void consume(Match match, long amount) {
        if (amount <= 0L || amount > match.amount()) {
            throw new IllegalArgumentException("A dynamic output receipt must consume a bounded positive amount");
        }
        for (MutableEntry entry : this.entries) {
            if (entry.plannedKey.equals(match.plannedKey()) &&
                    entry.route == match.route() &&
                    entry.source.equals(match.source())) {
                if (amount > entry.remaining) {
                    throw new IllegalStateException("Dynamic output ledger changed after acceptance simulation");
                }
                entry.remaining -= amount;
                removeEmpty();
                return;
            }
        }
        throw new IllegalStateException("Dynamic output ledger lost its simulated acceptance entry");
    }

    /**
     * Marks an ordinary actual dynamic output as eligible for same-item input binding within this job only.
     */
    void recordInputAlias(AEItemKey actualKey, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("A dynamic input alias must be a positive item amount");
        }
        this.inputAliases.mergeLong(actualKey, amount, Math::addExact);
    }

    /**
     * Returns the owned same-item alternatives without requiring one variant to satisfy the whole input.
     */
    List<GenericStack> resolveInputs(AEKey plannedKey, KeyCounter inventory) {
        if (!(plannedKey instanceof AEItemKey plannedItem)) {
            return List.of();
        }
        ObjectArrayList<GenericStack> alternatives = new ObjectArrayList<>();
        for (var alias : this.inputAliases.object2LongEntrySet()) {
            if (alias.getKey().getItem() == plannedItem.getItem() &&
                    !alias.getKey().equals(plannedKey)) {
                long available = Math.min(alias.getLongValue(), inventory.get(alias.getKey()));
                if (available > 0L) {
                    alternatives.add(new GenericStack(alias.getKey(), available));
                }
            }
        }
        return List.copyOf(alternatives);
    }

    boolean isInputAlias(AEKey key) {
        return key instanceof AEItemKey itemKey && this.inputAliases.containsKey(itemKey);
    }

    /**
     * Deducts only aliases that the accepted provider submission actually consumed.
     */
    void consumeInputAliases(KeyCounter consumedInputs) {
        for (var consumed : consumedInputs) {
            if (!(consumed.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            long aliased = this.inputAliases.getLong(itemKey);
            if (aliased == 0L) {
                continue;
            }
            long remaining = aliased - Math.min(aliased, consumed.getLongValue());
            if (remaining == 0L) {
                this.inputAliases.removeLong(itemKey);
            } else {
                this.inputAliases.put(itemKey, remaining);
            }
        }
    }

    void validateInputAliases(KeyCounter inventory) {
        this.inputAliases.forEach((key, amount) -> {
            if (amount <= 0L || inventory.get(key) < amount) {
                throw new IllegalArgumentException(
                        "Persisted same-item input ownership exceeds CPU inventory for " + key);
            }
        });
    }

    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        ListTag encoded = new ListTag();
        for (MutableEntry entry : this.entries) {
            CompoundTag tag = new CompoundTag();
            tag.put(KEY_TAG, entry.plannedKey.toTagGeneric(registries));
            tag.putLong(AMOUNT_TAG, entry.remaining);
            tag.putString(ROUTE_TAG, entry.route.name());
            tag.putString(SOURCE_TAG, entry.source.toString());
            encoded.add(tag);
        }
        root.put(WAITING_TAG, encoded);
        ListTag aliases = new ListTag();
        this.inputAliases.forEach((key, amount) -> {
            CompoundTag tag = new CompoundTag();
            tag.put(ACTUAL_KEY_TAG, key.toTagGeneric(registries));
            tag.putLong(AMOUNT_TAG, amount);
            aliases.add(tag);
        });
        root.put(INPUT_ALIASES_TAG, aliases);
        return root;
    }

    static DynamicCraftingOutputLedger readFromTag(CompoundTag root,
                                                   HolderLookup.Provider registries) {
        if (!root.getAllKeys().equals(Set.of(WAITING_TAG, INPUT_ALIASES_TAG)) ||
                !root.contains(WAITING_TAG, Tag.TAG_LIST) ||
                !root.contains(INPUT_ALIASES_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Damaged dynamic crafting output ledger root");
        }
        DynamicCraftingOutputLedger ledger = new DynamicCraftingOutputLedger();
        ObjectArrayList<Registration> registrations = new ObjectArrayList<>();
        Tag rawWaiting = root.get(WAITING_TAG);
        if (!(rawWaiting instanceof ListTag encoded) ||
                (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Damaged dynamic crafting output waiting list");
        }
        for (Tag value : encoded) {
            if (!(value instanceof CompoundTag tag) ||
                    !tag.getAllKeys().equals(Set.of(KEY_TAG, AMOUNT_TAG, ROUTE_TAG, SOURCE_TAG)) ||
                    !tag.contains(KEY_TAG, Tag.TAG_COMPOUND) ||
                    !tag.contains(AMOUNT_TAG, Tag.TAG_LONG) ||
                    !tag.contains(ROUTE_TAG, Tag.TAG_STRING) ||
                    !tag.contains(SOURCE_TAG, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Damaged dynamic crafting output ledger entry");
            }
            AEKey decoded = AEKey.fromTagGeneric(registries, tag.getCompound(KEY_TAG));
            if (!(decoded instanceof AEItemKey itemKey)) {
                throw new IllegalArgumentException("Dynamic crafting output ledger requires item keys");
            }
            Route route;
            ResourceLocation source;
            try {
                route = Route.valueOf(tag.getString(ROUTE_TAG));
                source = ResourceLocation.parse(tag.getString(SOURCE_TAG));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Damaged dynamic crafting output ledger metadata", exception);
            }
            registrations.add(new Registration(itemKey, tag.getLong(AMOUNT_TAG), route, source));
        }
        domains(registrations);
        ledger.register(registrations);
        if (ledger.entries.size() != registrations.size()) {
            throw new IllegalArgumentException("Persisted dynamic crafting output ledger contains duplicate entries");
        }
        Tag rawAliases = root.get(INPUT_ALIASES_TAG);
        if (!(rawAliases instanceof ListTag aliases) ||
                (!aliases.isEmpty() && aliases.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Damaged same-item input alias list");
        }
        for (Tag value : aliases) {
            if (!(value instanceof CompoundTag tag) ||
                    !tag.getAllKeys().equals(Set.of(ACTUAL_KEY_TAG, AMOUNT_TAG)) ||
                    !tag.contains(ACTUAL_KEY_TAG, Tag.TAG_COMPOUND) ||
                    !tag.contains(AMOUNT_TAG, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Damaged same-item input alias entry");
            }
            AEKey decoded = AEKey.fromTagGeneric(registries, tag.getCompound(ACTUAL_KEY_TAG));
            if (!(decoded instanceof AEItemKey itemKey) || tag.getLong(AMOUNT_TAG) <= 0L ||
                    ledger.inputAliases.putIfAbsent(itemKey, tag.getLong(AMOUNT_TAG)) != 0L) {
                throw new IllegalArgumentException("Same-item input aliases require unique positive item entries");
            }
        }
        return ledger;
    }

    private void removeEmpty() {
        this.entries.removeIf(entry -> entry.remaining == 0L);
    }

    private static Map<Item, Domain> domains(List<Registration> registrations) {
        Object2ObjectOpenHashMap<Item, Domain> domains = new Object2ObjectOpenHashMap<>();
        for (Registration registration : registrations) {
            Domain candidate = new Domain(registration.plannedKey(), registration.route());
            Domain existing = domains.putIfAbsent(registration.plannedKey().getItem(), candidate);
            if (existing != null && !existing.compatible(candidate)) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output semantics contain ambiguous templates or routes for item " +
                                registration.plannedKey().getItem());
            }
        }
        return domains;
    }

    private static Map<Item, List<AEItemKey>> expectedDomains(List<GenericStack> outputs) {
        Object2ObjectLinkedOpenHashMap<Item, List<AEItemKey>> domains = new Object2ObjectLinkedOpenHashMap<>();
        for (GenericStack output : outputs) {
            if (output.what() instanceof AEItemKey itemKey) {
                List<AEItemKey> keys = domains.computeIfAbsent(itemKey.getItem(), ignored -> new ObjectArrayList<>());
                if (!keys.contains(itemKey)) {
                    keys.add(itemKey);
                }
            }
        }
        return domains;
    }

    private record Domain(AEItemKey plannedKey, Route route) {

        private boolean compatible(Domain other) {
            return this.plannedKey.equals(other.plannedKey) && this.route == other.route;
        }
    }

    private static final class MutableEntry {

        private final AEItemKey plannedKey;
        private final Route route;
        private final ResourceLocation source;
        private long remaining;

        private MutableEntry(Registration registration) {
            this.plannedKey = registration.plannedKey();
            this.remaining = registration.amount();
            this.route = registration.route();
            this.source = registration.source();
        }

        private Registration registration() {
            return new Registration(this.plannedKey, this.remaining, this.route, this.source);
        }

        private boolean matches(Registration registration) {
            return this.plannedKey.equals(registration.plannedKey()) &&
                    this.route == registration.route() &&
                    this.source.equals(registration.source());
        }

        private Match match(long amount) {
            return new Match(this.plannedKey, amount, this.route, this.source);
        }
    }
}
