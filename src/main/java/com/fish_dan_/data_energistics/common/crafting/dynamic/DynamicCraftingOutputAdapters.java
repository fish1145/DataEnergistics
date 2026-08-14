package com.fish_dan_.data_energistics.common.crafting.dynamic;

import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutput;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputMatchMode;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputSemantics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Frozen adapter runtime that resolves and validates one outer pattern before a provider receives its inputs.
 */
public final class DynamicCraftingOutputAdapters {

    private static volatile List<DynamicCraftingOutputAdapter> adapters = List.of();
    private static boolean installed;

    private DynamicCraftingOutputAdapters() {}

    /**
     * Installs the immutable plugin snapshot exactly once during common setup.
     *
     * @param values adapters in deterministic plugin and declaration order
     */
    public static synchronized void install(List<DynamicCraftingOutputAdapter> values) {
        if (installed) {
            throw new IllegalStateException("Dynamic crafting output adapters have already been installed");
        }
        ArrayList<DynamicCraftingOutputAdapter> validated = new ArrayList<>(values.size());
        for (DynamicCraftingOutputAdapter adapter : values) {
            if (adapter == null || adapter.id() == null) {
                throw new IllegalStateException("A dynamic crafting output adapter requires a stable ID");
            }
            validated.add(adapter);
        }
        adapters = List.copyOf(validated);
        installed = true;
    }

    /**
     * Resolves exactly one claiming adapter and verifies every declaration against the outer pattern's real outputs.
     *
     * @param details original outer pattern object selected for provider dispatch
     * @return validated semantics, or empty when all outputs remain exact
     */
    public static Optional<ResolvedSemantics> resolve(IPatternDetails details) {
        requireInstalled();
        if (details == null) {
            throw new DynamicCraftingOutputResolutionException("Dynamic output resolution requires pattern details");
        }

        ResolvedSemantics claimed = null;
        for (DynamicCraftingOutputAdapter adapter : adapters) {
            Optional<DynamicCraftingOutputSemantics> candidate;
            try {
                candidate = adapter.resolve(details);
            } catch (RuntimeException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapter.id() + " failed for pattern " + details.getDefinition(),
                        exception);
            }
            if (candidate == null) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapter.id() + " returned null for pattern " +
                                details.getDefinition());
            }
            if (candidate.isEmpty()) {
                continue;
            }
            if (claimed != null) {
                throw new DynamicCraftingOutputResolutionException(
                        "Multiple dynamic output adapters claimed pattern " + details.getDefinition() + ": " +
                                claimed.adapterId() + " and " + adapter.id());
            }
            claimed = validate(details, adapter.id(), candidate.orElseThrow());
        }
        return Optional.ofNullable(claimed);
    }

    private static ResolvedSemantics validate(IPatternDetails details,
                                              ResourceLocation adapterId,
                                              DynamicCraftingOutputSemantics semantics) {
        LinkedHashMap<AEKey, Long> declared = new LinkedHashMap<>();
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0L) {
                throw new DynamicCraftingOutputResolutionException(
                        "Pattern " + details.getDefinition() + " exposes an invalid physical output");
            }
            try {
                declared.merge(output.what(), output.amount(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Pattern " + details.getDefinition() + " overflows its physical output amount",
                        exception);
            }
        }

        LinkedHashMap<AEKey, Long> claimed = new LinkedHashMap<>();
        Map<Item, AEItemKey> domains = new HashMap<>();
        for (DynamicCraftingOutput output : semantics.outputs()) {
            if (output.matchMode() != DynamicCraftingOutputMatchMode.SAME_ITEM ||
                    !(output.plannedOutput().what() instanceof AEItemKey plannedKey)) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId + " declared an unsupported output match");
            }
            AEItemKey existingDomain = domains.putIfAbsent(plannedKey.getItem(), plannedKey);
            if (existingDomain != null && !existingDomain.equals(plannedKey)) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId +
                                " declared multiple component templates for the same registered item");
            }
            try {
                claimed.merge(plannedKey, output.plannedOutput().amount(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId + " overflows a declared output amount",
                        exception);
            }
        }
        claimed.forEach((key, amount) -> {
            long available = declared.getOrDefault(key, 0L);
            if (amount > available) {
                throw new DynamicCraftingOutputResolutionException(
                        "Dynamic output adapter " + adapterId + " declared absent output " + key +
                                " x" + amount + " for pattern " + details.getDefinition());
            }
        });
        return new ResolvedSemantics(adapterId, semantics.outputs());
    }

    private static void requireInstalled() {
        if (!installed) {
            throw new IllegalStateException("Dynamic crafting output adapters are not installed");
        }
    }

    /**
     * Validated declarations for one logical provider push.
     *
     * @param adapterId stable persisted source identity
     * @param outputs   dynamic physical outputs in deterministic declaration order
     */
    public record ResolvedSemantics(ResourceLocation adapterId,
                                    List<DynamicCraftingOutput> outputs) {

        /**
         * Isolates the adapter-owned list from runtime callers.
         */
        public ResolvedSemantics {
            outputs = List.copyOf(outputs);
        }
    }
}
