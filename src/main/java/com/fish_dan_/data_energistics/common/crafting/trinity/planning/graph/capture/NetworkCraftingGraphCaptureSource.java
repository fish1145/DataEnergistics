package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.helpers.NetworkCraftingProviders;

import net.minecraft.core.HolderLookup;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Server-thread adapter over AE2's provider index.
 *
 * <p>
 * This object deliberately remains outside every published snapshot. It is only a narrow capture boundary for the
 * incremental rebuilder.
 * </p>
 */
public final class NetworkCraftingGraphCaptureSource implements TrinityCraftingGraphCaptureSource {

    private final NetworkCraftingProviders providers;
    private final HolderLookup.Provider registries;
    private final LongSupplier revision;
    private long lastObservedRevision = -1L;

    /**
     * Binds one grid-local AE2 index to the registry lookup of its server.
     *
     * @param providers  live provider index, accessed only by the server-thread caller
     * @param registries server registry lookup used during canonical capture
     */
    public NetworkCraftingGraphCaptureSource(NetworkCraftingProviders providers,
                                             HolderLookup.Provider registries) {
        this(providers, registries, requiredRevisionSource(providers));
    }

    /**
     * Binds an explicit monotonic revision source for direct tests without a transformed AE2 provider class.
     *
     * @param providers  live provider index, accessed only by the server-thread caller
     * @param registries server registry lookup used during canonical capture
     * @param revision   non-negative monotonic provider mutation revision
     */
    NetworkCraftingGraphCaptureSource(NetworkCraftingProviders providers,
                                      HolderLookup.Provider registries,
                                      LongSupplier revision) {
        this.providers = providers;
        this.registries = registries;
        this.revision = revision;
    }

    @Override
    public long revision() {
        long current = this.revision.getAsLong();
        if (current < 0L || current < this.lastObservedRevision) {
            throw new IllegalStateException("A Trinity crafting-provider revision must be non-negative and monotonic");
        }
        this.lastObservedRevision = current;
        return current;
    }

    @Override
    public HolderLookup.Provider registries() {
        return this.registries;
    }

    @Override
    public List<AEKey> captureCraftableKeys() {
        return List.copyOf(this.providers.getCraftableKeys());
    }

    @Override
    public List<IPatternDetails> capturePatternsFor(AEKey primaryOutput) {
        return List.copyOf(this.providers.getCraftingFor(primaryOutput));
    }

    private static LongSupplier requiredRevisionSource(NetworkCraftingProviders providers) {
        if (providers instanceof TrinityCraftingProviderRevision revision) {
            return revision::data_energistics$trinityCraftingProviderRevision;
        }
        throw new IllegalStateException(
                "A production Trinity graph source requires the monotonic crafting-provider revision bridge");
    }
}
