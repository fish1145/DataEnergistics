package com.fish_dan_.data_energistics.common.crafting.virtual;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputRegistration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Identity-based adapter registry and deterministic output projection boundary.
 */
public final class VirtualCraftingOutputAdapters {

    private static final List<VirtualCraftingOutputAdapter> ADAPTERS = new ArrayList<>();

    private VirtualCraftingOutputAdapters() {}

    /**
     * Registers one adapter without permitting identity-duplicate lifecycle registration.
     *
     * @param adapter adapter to retain
     * @return idempotent removal handle
     */
    public static synchronized VirtualCraftingOutputRegistration register(VirtualCraftingOutputAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        if (ADAPTERS.stream().anyMatch(registered -> registered == adapter)) {
            throw new IllegalStateException("Virtual crafting output adapter is already registered");
        }
        ADAPTERS.add(adapter);
        return new Registration(adapter);
    }

    /**
     * Projects a decoded pattern, recovering raw processing outputs from its definition when the live details are a
     * routing wrapper.
     *
     * @param details live pattern details
     * @return immutable logical and virtual output projection
     */
    public static VirtualCraftingOutputProjection project(IPatternDetails details) {
        Objects.requireNonNull(details, "details");
        if (details instanceof VirtualCraftingPatternOutputs projected) {
            return projected.dataEnergistics$virtualOutputProjection();
        }
        var encoded = details.getDefinition().get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded != null && !encoded.containsMissingContent()) {
            return project(encoded.sparseOutputs());
        }
        return project(details.getOutputs(), List.of());
    }

    /**
     * Projects raw declared outputs without recursively resolving targets returned by an adapter.
     *
     * @param declaredOutputs ordered raw outputs; sparse null entries are ignored
     * @return immutable projection preserving first-key order after aggregation
     */
    public static VirtualCraftingOutputProjection project(List<GenericStack> declaredOutputs) {
        Objects.requireNonNull(declaredOutputs, "declaredOutputs");
        List<VirtualCraftingOutputAdapter> adapters;
        synchronized (VirtualCraftingOutputAdapters.class) {
            adapters = List.copyOf(ADAPTERS);
        }
        return project(declaredOutputs, adapters);
    }

    private static VirtualCraftingOutputProjection project(List<GenericStack> declaredOutputs,
                                                           List<VirtualCraftingOutputAdapter> adapters) {
        LinkedHashMap<AEKey, BigInteger> logical = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> virtual = new LinkedHashMap<>();
        for (GenericStack output : declaredOutputs) {
            if (output == null) {
                continue;
            }
            if (output.what() == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("A virtual crafting projection requires positive complete outputs");
            }
            Optional<AEKey> target = resolveTarget(adapters, output);
            AEKey logicalKey = target.orElse(output.what());
            BigInteger amount = BigInteger.valueOf(output.amount());
            logical.merge(logicalKey, amount, BigInteger::add);
            target.ifPresent(key -> virtual.merge(key, amount, BigInteger::add));
        }
        if (logical.isEmpty()) {
            throw new IllegalArgumentException("A virtual crafting projection requires at least one output");
        }
        return new VirtualCraftingOutputProjection(
                VirtualCraftingOutputProjection.immutableStacks(logical),
                VirtualCraftingOutputProjection.immutableStacks(virtual));
    }

    private static Optional<AEKey> resolveTarget(List<VirtualCraftingOutputAdapter> adapters,
                                                 GenericStack output) {
        AEKey resolved = null;
        for (VirtualCraftingOutputAdapter adapter : adapters) {
            Optional<AEKey> candidate = Objects.requireNonNull(
                    adapter.resolveTarget(output),
                    "Virtual crafting output adapter returned null instead of Optional");
            if (candidate.isEmpty()) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("Multiple virtual crafting adapters claimed output " + output.what());
            }
            resolved = Objects.requireNonNull(candidate.get(), "Virtual crafting output adapter returned a null key");
        }
        return Optional.ofNullable(resolved);
    }

    private static final class Registration implements VirtualCraftingOutputRegistration {

        private final VirtualCraftingOutputAdapter adapter;
        private boolean active = true;

        private Registration(VirtualCraftingOutputAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public synchronized void close() {
            if (!this.active) {
                return;
            }
            synchronized (VirtualCraftingOutputAdapters.class) {
                ADAPTERS.removeIf(registered -> registered == this.adapter);
            }
            this.active = false;
        }
    }
}
