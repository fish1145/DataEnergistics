package com.fish_dan_.data_energistics.common.crafting.virtual;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletion;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletionMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Identity-based adapter registry and deterministic output projection boundary.
 */
public final class VirtualCraftingOutputAdapters {

    private static volatile List<VirtualCraftingOutputAdapter> ADAPTERS = List.of();
    private static boolean installed;

    private VirtualCraftingOutputAdapters() {
    }

    /**
     * Installs the immutable adapter snapshot assembled by the unified plugin registry.
     *
     * @param adapters adapters in deterministic plugin and registration order
     */
    public static synchronized void install(
            @NotNull List<@NotNull VirtualCraftingOutputAdapter> adapters) {
        if (installed) {
            throw new IllegalStateException("Virtual crafting output adapters have already been installed");
        }
        ArrayList<VirtualCraftingOutputAdapter> validated = new ArrayList<>(adapters.size());
        for (VirtualCraftingOutputAdapter adapter : adapters) {
            if (validated.stream().anyMatch(existing -> existing == adapter)) {
                throw new IllegalStateException("Virtual crafting output adapter is registered more than once");
            }
            validated.add(adapter);
        }
        ADAPTERS = List.copyOf(validated);
        installed = true;
    }

    /**
     * Projects a decoded pattern, recovering raw processing outputs from its definition when the live details are a
     * routing wrapper.
     *
     * @param details live pattern details
     * @return immutable logical and virtual output projection
     */
    public static @NotNull VirtualCraftingOutputProjection project(@NotNull IPatternDetails details) {
        requireInstalled();
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
     * @param declaredOutputs ordered raw outputs; {@code null} entries are legal sparse-layout holes
     * @return immutable projection preserving first-key order after aggregation
     */
    public static @NotNull VirtualCraftingOutputProjection project(
            @NotNull List<@Nullable GenericStack> declaredOutputs) {
        requireInstalled();
        return project(declaredOutputs, ADAPTERS);
    }

    private static @NotNull VirtualCraftingOutputProjection project(
            @NotNull List<@Nullable GenericStack> declaredOutputs,
            @NotNull List<@NotNull VirtualCraftingOutputAdapter> adapters) {
        LinkedHashMap<AEKey, BigInteger> logical = new LinkedHashMap<>();
        ArrayList<VirtualCraftingCompletion> virtual = new ArrayList<>();
        for (GenericStack output : declaredOutputs) {
            // AE2's encoded processing pattern deliberately preserves sparse slot positions with null entries.
            // A routing/wrapper pattern may expose that same list through either its definition or live outputs.
            // The hole carries no output semantics and must not be passed to the adapter or amount validation.
            if (output == null) {
                continue;
            }
            if (output.amount() <= 0L) {
                throw new IllegalArgumentException("A virtual crafting projection requires positive complete outputs");
            }
            Optional<ResolvedOutput> resolved = resolveOutput(adapters, output);
            AEKey logicalKey = resolved
                    .filter(value -> value.mode() != VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT)
                    .map(ResolvedOutput::target)
                    .orElse(output.what());
            BigInteger amount = BigInteger.valueOf(output.amount());
            logical.merge(logicalKey, amount, BigInteger::add);
            resolved.ifPresent(value -> {
                AEKey completionKey = value.mode() == VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT ?
                        output.what() : value.target();
                virtual.add(new VirtualCraftingCompletion(
                        new GenericStack(completionKey, output.amount()),
                        value.mode()));
            });
        }
        if (logical.isEmpty()) {
            throw new IllegalArgumentException("A virtual crafting projection requires at least one output");
        }
        return new VirtualCraftingOutputProjection(
                VirtualCraftingOutputProjection.immutableStacks(logical),
                virtual);
    }

    /**
     * Reports whether a declared output is claimed as a completion-only control token.
     *
     * @param declaredOutput complete declared output identity
     * @return whether completion must not materialize an item
     */
    public static boolean hasNoOutputCompletion(@NotNull GenericStack declaredOutput) {
        requireInstalled();
        return resolveOutput(ADAPTERS, declaredOutput)
                .map(value -> value.mode() == VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT)
                .orElse(false);
    }

    private static @NotNull Optional<@NotNull ResolvedOutput> resolveOutput(
            @NotNull List<@NotNull VirtualCraftingOutputAdapter> adapters,
            @NotNull GenericStack output) {
        ResolvedOutput resolved = null;
        for (VirtualCraftingOutputAdapter adapter : adapters) {
            Optional<AEKey> candidate;
            try {
                candidate = adapter.resolveTarget(output);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Virtual crafting output adapter {} failed while resolving {}; isolating that adapter",
                        adapter,
                        output,
                        exception);
                continue;
            }
            if (candidate == null) {
                Data_Energistics.LOGGER.error(
                        "Virtual crafting output adapter {} returned a null target result for {}; isolating that adapter",
                        adapter,
                        output);
                continue;
            }
            if (candidate.isEmpty()) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("Multiple virtual crafting adapters claimed output " + output.what());
            }
            AEKey target = candidate.get();
            VirtualCraftingCompletionMode mode;
            try {
                mode = adapter.completionMode(output);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Virtual crafting output adapter {} failed to select completion mode for {}; isolating that adapter",
                        adapter,
                        output,
                        exception);
                continue;
            }
            if (mode == null) {
                Data_Energistics.LOGGER.error(
                        "Virtual crafting output adapter {} returned a null completion mode for {}; isolating that adapter",
                        adapter,
                        output);
                continue;
            }
            resolved = new ResolvedOutput(target, mode);
        }
        return Optional.ofNullable(resolved);
    }

    /**
     * Rejects runtime queries before the common-setup snapshot has been installed.
     */
    private static void requireInstalled() {
        if (!installed) {
            throw new IllegalStateException("Virtual crafting output adapters are not installed");
        }
    }

    private record ResolvedOutput(@NotNull AEKey target,
                                  @NotNull VirtualCraftingCompletionMode mode) {
    }
}
