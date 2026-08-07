package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;
import com.fish_dan_.data_energistics.util.UniversalTerminalAdapter;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Immutable runtime view published after every discovered plugin has finished registration.
 *
 * <p>The snapshot is the only state visible to runtime integrations. Its typed facets retain the public API shape for
 * queries while rejecting every late mutation with an explicit lifecycle error.</p>
 */
public final class DataEnergisticsRegistrySnapshot implements DataEnergisticsRegistry {

    private final List<UniversalTerminalAdapter> universalTerminalAdapters;
    private final List<PatternProviderRegistration> patternProviderRegistrations;
    private final List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters;
    private final UniversalTerminalRegistry universalTerminals = new FrozenUniversalTerminalRegistry();
    private final PatternProviderRegistry patternProviders = new FrozenPatternProviderRegistry();
    private final VirtualCraftingRegistry virtualCrafting = new FrozenVirtualCraftingRegistry();

    /** Freezes all registry values without retaining any mutable staging collection. */
    DataEnergisticsRegistrySnapshot(List<UniversalTerminalAdapter> universalTerminalAdapters,
                                    List<PatternProviderRegistration> patternProviderRegistrations,
                                    List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters) {
        this.universalTerminalAdapters = List.copyOf(universalTerminalAdapters);
        this.patternProviderRegistrations = List.copyOf(patternProviderRegistrations);
        this.virtualCraftingOutputAdapters = List.copyOf(virtualCraftingOutputAdapters);
    }

    /** Returns terminal adapters in deterministic plugin and registration order. */
    public List<UniversalTerminalAdapter> universalTerminalAdapters() {
        return this.universalTerminalAdapters;
    }

    /** Returns provider declarations in deterministic plugin and registration order. */
    public List<PatternProviderRegistration> patternProviderRegistrations() {
        return this.patternProviderRegistrations;
    }

    /** Returns virtual-output adapters in deterministic plugin and registration order. */
    public List<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters() {
        return this.virtualCraftingOutputAdapters;
    }

    @Override
    public UniversalTerminalRegistry universalTerminals() {
        return this.universalTerminals;
    }

    @Override
    public PatternProviderRegistry patternProviders() {
        return this.patternProviders;
    }

    @Override
    public VirtualCraftingRegistry virtualCrafting() {
        return this.virtualCrafting;
    }

    /** Read-only terminal facet backed exclusively by this immutable snapshot. */
    private final class FrozenUniversalTerminalRegistry implements UniversalTerminalRegistry {

        @Override
        public void register(UniversalTerminalAdapter adapter) {
            throw frozenMutation();
        }

        @Override
        public boolean isSupportedTerminal(ItemStack stack) {
            return universalTerminalAdapters.stream()
                    .filter(adapter -> adapter.matches(stack))
                    .findFirst()
                    .map(adapter -> adapter.canInstall(stack))
                    .orElse(false);
        }
    }

    /** Read-only provider facet that makes late registration failures explicit. */
    private static final class FrozenPatternProviderRegistry implements PatternProviderRegistry {

        @Override
        public void register(PatternProviderRegistration registration) {
            throw frozenMutation();
        }
    }

    /** Read-only virtual crafting facet that makes late registration failures explicit. */
    private static final class FrozenVirtualCraftingRegistry implements VirtualCraftingRegistry {

        @Override
        public void registerOutputAdapter(VirtualCraftingOutputAdapter adapter) {
            throw frozenMutation();
        }
    }

    /** Creates one consistent lifecycle error for all frozen mutation paths. */
    private static IllegalStateException frozenMutation() {
        return new IllegalStateException("Data Energistics plugin registration is already frozen");
    }
}
