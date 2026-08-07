package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitContext;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitHook;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.entrypoint.provider.ResolvedProviderBinding;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentityResolver;
import com.fish_dan_.data_energistics.util.PatternProviderNameHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

public final class PatternProviderSyncHelper {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final ProviderIdentityResolver PROVIDER_IDENTITY_RESOLVER = ProviderIdentityResolver.create();

    private PatternProviderSyncHelper() {}

    /**
     * Discovers providers and applies the supplied per-leaf history before grouping rows.
     */
    public static PatternEncodingPreviewMenu.SyncedPatternProviderList collectSyncedPatternProviders(
                                                                                                     @Nullable IGrid grid,
                                                                                                      Map<PatternContainer, Long> syncedPatternProviderIds,
                                                                                                      Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                                                                      LongSupplier nextIdSupplier,
                                                                                                      @Nullable PatternEncodingRankingContext rankingContext,
                                                                                                      Map<String, Long> leafClickCounts) {
        syncedProviderTargetsById.clear();
        if (grid == null) {
            syncedPatternProviderIds.clear();
            return PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY;
        }
        List<PatternProviderAggregationEntry> discoveredProviders = new ArrayList<>();
        Map<PatternContainer, Boolean> activeProviders = new IdentityHashMap<>();
        Map<PatternContainer, Boolean> discoveredProviderSet = new IdentityHashMap<>();

        collectDirectPatternProviders(grid, syncedPatternProviderIds, nextIdSupplier, discoveredProviders, activeProviders,
                discoveredProviderSet, rankingContext);

        for (var machineClass : grid.getMachineClasses()) {
            var patternContainerClass = asPatternContainerClass(machineClass);
            if (patternContainerClass == null) {
                continue;
            }

            for (var container : grid.getMachines(patternContainerClass)) {
                addProviderIfVisible(container, syncedPatternProviderIds, nextIdSupplier, discoveredProviders, activeProviders,
                        discoveredProviderSet, rankingContext);
            }
        }

        syncedPatternProviderIds.keySet().removeIf(provider -> !activeProviders.containsKey(provider));

        return aggregateSyncedPatternProviders(
                discoveredProviders, syncedProviderTargetsById, leafClickCounts);
    }

    /**
     * Aggregates already discovered pattern providers into the rows synchronized to encoding terminals.
     * This is the shared aggregation boundary used by live grid discovery and logic tests.
     */
    static PatternEncodingPreviewMenu.SyncedPatternProviderList aggregateSyncedPatternProviders(
                                                                                                List<PatternProviderAggregationEntry> discoveredProviders,
                                                                                                Map<Long, List<PatternContainer>> syncedProviderTargetsById) {
        return aggregateSyncedPatternProviders(discoveredProviders, syncedProviderTargetsById, Map.of());
    }

    /**
     * Aggregates rows while ranking each unique provider leaf by its recorded success count.
     */
    static PatternEncodingPreviewMenu.SyncedPatternProviderList aggregateSyncedPatternProviders(
                                                                                                List<PatternProviderAggregationEntry> discoveredProviders,
                                                                                                Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                                                                Map<String, Long> leafClickCounts) {
        syncedProviderTargetsById.clear();
        List<AggregatedPatternProvider> aggregatedProviders = aggregateDiscoveredProviders(
                discoveredProviders, leafClickCounts);
        aggregatedProviders.sort(createAggregatedProviderComparator(leafClickCounts));

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = new ArrayList<>(aggregatedProviders.size());
        for (var provider : aggregatedProviders) {
            syncedProviderTargetsById.put(provider.id(), List.copyOf(provider.containers()));
            providers.add(new PatternEncodingPreviewMenu.SyncedPatternProvider(
                    provider.id(),
                    provider.displayName(),
                    provider.iconItemId(),
                    provider.useAeButtonStyle(),
                    provider.renameable(),
                    provider.patternSlotCount(),
                    provider.usedPatternSlotCount(),
                    provider.leafDigests()));
        }

        return providers.isEmpty() ? PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY :
                new PatternEncodingPreviewMenu.SyncedPatternProviderList(providers);
    }

    @Nullable
    public static List<PatternContainer> findProvidersById(Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                           long providerId) {
        return syncedProviderTargetsById.get(providerId);
    }

    /**
     * Renames every provider in one synchronized display group as a single transaction.
     */
    public static boolean renamePatternProviders(List<PatternContainer> providers, @Nullable String name) {
        if (providers == null) {
            return renamePatternProviderTargets(null, name);
        }
        return renamePatternProviderTargets(providers.stream()
                .map(PatternContainerRenameTarget::new)
                .toList(), name);
    }

    /**
     * Applies one custom name to a provider group as an atomic transaction.
     * The target abstraction keeps rollback behavior testable while production targets delegate to
     * {@link PatternProviderNameHelper}.
     */
    static boolean renamePatternProviderTargets(
                                                @Nullable List<? extends PatternProviderRenameTarget> targets, @Nullable String name) {
        if (targets == null || targets.isEmpty()) {
            LOGGER.warn("Rejected pattern provider rename because the provider group is empty");
            return false;
        }

        String sanitized = name == null ? "" : name.trim();
        Component customName = sanitized.isEmpty() ? null : Component.literal(sanitized);
        List<Component> originalNames = new ArrayList<>(targets.size());
        int modifiedCount = 0;
        try {
            for (var target : targets) {
                if (target == null || !target.canRename()) {
                    LOGGER.warn("Rejected pattern provider group rename because {} is not renameable",
                            target == null ? "null" : target.description());
                    return false;
                }
            }
            for (var target : targets) {
                originalNames.add(target.customName());
            }
            for (var target : targets) {
                if (!target.setCustomName(customName)) {
                    throw new IllegalStateException("Pattern provider rejected its custom name update: " +
                            target.description());
                }
                modifiedCount++;
                target.syncRename();
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to rename pattern provider group; rolling back {} modified providers",
                    modifiedCount, exception);
            rollbackPatternProviderNames(targets, originalNames, modifiedCount);
            return false;
        }
    }

    private static void rollbackPatternProviderNames(List<? extends PatternProviderRenameTarget> targets,
                                                     List<Component> originalNames,
                                                     int modifiedCount) {
        for (int index = modifiedCount - 1; index >= 0; index--) {
            PatternProviderRenameTarget target = targets.get(index);
            try {
                if (!target.setCustomName(originalNames.get(index))) {
                    LOGGER.error("Failed to roll back custom name for pattern provider {}",
                            target.description());
                    continue;
                }
                target.syncRename();
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to roll back custom name for pattern provider {}",
                        target.description(), exception);
            }
        }
    }

    private static ItemStack transferEncodedPatternToProvider(PatternContainer container, ItemStack encodedPattern) {
        if (encodedPattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            return encodedPattern;
        }

        var patternInventory = container.getTerminalPatternInventory();
        if (patternInventory.size() <= 0) {
            return encodedPattern;
        }

        long matchingCountBefore = countMatchingEncodedPatterns(patternInventory, encodedPattern);
        ItemStack reportedRemainder;
        try {
            reportedRemainder = patternInventory.addItems(encodedPattern.copy(), false);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to insert encoded pattern into {}; checking the target inventory for committed patterns",
                    container, exception);
            int committedCount = countCommittedPatternDelta(
                    matchingCountBefore, patternInventory, encodedPattern);
            if (committedCount <= 0) {
                return encodedPattern;
            }

            notifyCommittedPatternUpload(container, encodedPattern, committedCount);
            return createRemainderAfterCommit(encodedPattern, committedCount);
        }

        int reportedCommittedCount = countReportedPatternCommit(encodedPattern, reportedRemainder);
        int committedCount = countCommittedPatternDelta(
                matchingCountBefore, patternInventory, encodedPattern);
        if (reportedCommittedCount != committedCount) {
            LOGGER.warn("Pattern provider {} reported {} of {} patterns committed, but its inventory changed by {}; " +
                    "using the inventory delta as the committed count",
                    container, reportedCommittedCount, encodedPattern.getCount(), committedCount);
        }
        if (committedCount <= 0) {
            return encodedPattern;
        }

        notifyCommittedPatternUpload(container, encodedPattern, committedCount);
        return createRemainderAfterCommit(encodedPattern, committedCount);
    }

    private static long countMatchingEncodedPatterns(InternalInventory inventory, ItemStack encodedPattern) {
        long matchingCount = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, encodedPattern)) {
                matchingCount += stack.getCount();
            }
        }
        return matchingCount;
    }

    private static int countReportedPatternCommit(ItemStack encodedPattern, ItemStack reportedRemainder) {
        int boundedRemainderCount = Math.min(encodedPattern.getCount(), reportedRemainder.getCount());
        return encodedPattern.getCount() - boundedRemainderCount;
    }

    private static int countCommittedPatternDelta(long matchingCountBefore, InternalInventory inventory,
                                                  ItemStack encodedPattern) {
        long matchingCountAfter = countMatchingEncodedPatterns(inventory, encodedPattern);
        long positiveDelta = Math.max(0, matchingCountAfter - matchingCountBefore);
        return (int) Math.min(encodedPattern.getCount(), positiveDelta);
    }

    private static ItemStack createRemainderAfterCommit(ItemStack encodedPattern, int committedCount) {
        if (committedCount >= encodedPattern.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = encodedPattern.copy();
        remainder.shrink(committedCount);
        return remainder;
    }

    private static void notifyCommittedPatternUpload(PatternContainer container,
                                                     ItemStack encodedPattern,
                                                     int committedCount) {
        if (container instanceof PatternProviderLogicHost providerHost) {
            try {
                providerHost.getLogic().updatePatterns();
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to update patterns after committing {} encoded patterns to {}",
                        committedCount, container, exception);
            }
            try {
                providerHost.saveChanges();
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to save pattern provider after committing {} encoded patterns to {}",
                        committedCount, container, exception);
            }
        }

        Optional<ResolvedProviderBinding> resolved;
        try {
            resolved = PatternProviderRuntimeBindings.resolve(container);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to resolve a post-commit provider plugin after committing {} encoded patterns to {}",
                    committedCount, container, exception);
            return;
        }
        if (resolved.isEmpty()) {
            return;
        }
        ResolvedProviderBinding binding = resolved.get();
        PatternProviderPostCommitHook hook = binding.registration().postCommitHook();
        if (hook == null) {
            return;
        }
        try {
            hook.afterCommit(new PatternProviderPostCommitContext(
                    container,
                    binding.identity(),
                    encodedPattern,
                    committedCount));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Pattern provider post-commit hook '{}' failed after committing {} encoded patterns to identity {}",
                    binding.registration().metadata().registrationId(),
                    committedCount,
                    binding.identity(),
                    exception);
        }
    }

    public static TransferResult transferEncodedPatternToProvidersChecked(List<PatternContainer> containers, ItemStack encodedPattern) {
        if (containers.isEmpty() || encodedPattern.isEmpty()) {
            return new TransferResult(encodedPattern, false, false, null);
        }

        List<PreparedPatternUpload> preparedUploads = preparePatternUploads(containers);
        if (preparedUploads.isEmpty()) {
            return new TransferResult(encodedPattern, false, false, null);
        }

        if (containsEquivalentEncodedPattern(preparedUploads, encodedPattern)) {
            return new TransferResult(encodedPattern, false, true, null);
        }

        ItemStack remainder = encodedPattern.copy();
        boolean transferred = false;
        PatternUploadTarget firstCommittedTarget = null;
        for (PreparedPatternUpload preparedUpload : preparedUploads) {
            if (remainder.isEmpty()) {
                break;
            }

            PatternContainer container = preparedUpload.container();
            ItemStack nextRemainder = transferEncodedPatternToProvider(container, remainder);
            if (nextRemainder.getCount() != remainder.getCount()) {
                transferred = true;
                if (firstCommittedTarget == null) {
                    firstCommittedTarget = preparedUpload.target();
                }
            }
            remainder = nextRemainder;
        }

        return new TransferResult(transferred ? remainder : encodedPattern, transferred, false, firstCommittedTarget);
    }

    private static List<PreparedPatternUpload> preparePatternUploads(List<PatternContainer> containers) {
        List<PreparedPatternUpload> preparedUploads = new ArrayList<>(containers.size());
        for (PatternContainer container : containers) {
            try {
                preparedUploads.add(new PreparedPatternUpload(container, resolveProviderUploadTarget(container)));
            } catch (RuntimeException exception) {
                LOGGER.error("Could not resolve a typed upload target for {}; skipping it before inventory mutation",
                        container, exception);
            }
        }
        return preparedUploads;
    }

    private static boolean containsEquivalentEncodedPattern(List<PreparedPatternUpload> preparedUploads,
                                                            ItemStack encodedPattern) {
        for (PreparedPatternUpload preparedUpload : preparedUploads) {
            PatternContainer container = preparedUpload.container();
            var inventory = container.getTerminalPatternInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack existing = inventory.getStackInSlot(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, encodedPattern)) {
                    return true;
                }
            }
        }

        return false;
    }

    private record PreparedPatternUpload(PatternContainer container, PatternUploadTarget target) {}

    /**
     * Result of one upload attempt, including the first inventory that actually accepted a pattern.
     */
    public record TransferResult(ItemStack remainder, boolean transferred, boolean duplicateFound,
                                 @Nullable PatternUploadTarget firstCommittedTarget) {}

    /**
     * Stable successful upload target returned only after its inventory has actually changed.
     */
    public record PatternUploadTarget(String providerDigest, Component targetName,
                                      @Nullable ResourceLocation dimensionId, @Nullable BlockPos position) {

        public PatternUploadTarget {
            if (providerDigest == null || targetName == null) {
                throw new IllegalArgumentException("Pattern upload target identity and name must not be null");
            }
            if (dimensionId == null != (position == null)) {
                throw new IllegalArgumentException("Pattern upload target location must be complete or absent");
            }
            position = position == null ? null : position.immutable();
        }
    }

    private static void collectDirectPatternProviders(
                                                      IGrid grid,
                                                      Map<PatternContainer, Long> syncedPatternProviderIds,
                                                      LongSupplier nextIdSupplier,
                                                       List<PatternProviderAggregationEntry> discoveredProviders,
                                                       Map<PatternContainer, Boolean> activeProviders,
                                                       Map<PatternContainer, Boolean> discoveredProviderSet,
                                                       @Nullable PatternEncodingRankingContext rankingContext) {
        try {
            for (var providerHost : grid.getMachines(PatternProviderLogicHost.class)) {
                addProviderIfVisible(providerHost, syncedPatternProviderIds, nextIdSupplier, discoveredProviders,
                        activeProviders, discoveredProviderSet, rankingContext);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to enumerate direct pattern providers from the active grid", exception);
        }
    }

    private static void addProviderIfVisible(
                                             PatternContainer container,
                                             Map<PatternContainer, Long> syncedPatternProviderIds,
                                             LongSupplier nextIdSupplier,
                                              List<PatternProviderAggregationEntry> discoveredProviders,
                                              Map<PatternContainer, Boolean> activeProviders,
                                              Map<PatternContainer, Boolean> discoveredProviderSet,
                                              @Nullable PatternEncodingRankingContext rankingContext) {
        if (!container.isVisibleInTerminal() || discoveredProviderSet.containsKey(container)) {
            return;
        }

        var patternInventory = container.getTerminalPatternInventory();
        if (patternInventory.size() <= 0) {
            return;
        }

        discoveredProviderSet.put(container, Boolean.TRUE);

        long providerId = syncedPatternProviderIds.computeIfAbsent(container,
                ignored -> nextIdSupplier.getAsLong());
        Component displayName;
        ResourceLocation iconItemId;
        PatternProviderAggregationKey aggregationKey;
        String providerDigest;
        boolean exactContextMatch;
        try {
            displayName = resolveProviderDisplayName(container);
            iconItemId = resolveProviderIconItemId(container);
            Optional<ResolvedProviderBinding> resolved = PatternProviderRuntimeBindings.resolve(container);
            ProviderIdentity identity = resolved
                    .map(ResolvedProviderBinding::identity)
                    .orElseGet(() -> PROVIDER_IDENTITY_RESOLVER.resolve(container));
            if (identity instanceof ProviderIdentity.Virtual) {
                throw new IllegalStateException("Pattern provider has no typed physical or registered identity: " +
                        container);
            }
            if (resolved.isPresent()) {
                PatternProviderMetadata metadata = resolved.get().registration().metadata();
                aggregationKey = new PatternProviderAggregationKey.Registered(
                        metadata.registrationId(), metadata.providerIdentity());
                exactContextMatch = matchesRankingContext(metadata, rankingContext);
            } else {
                aggregationKey = ProviderIdentityDescriptor.from(identity)
                        .<PatternProviderAggregationKey>map(PatternProviderAggregationKey.Core::new)
                        .orElseGet(() -> new PatternProviderAggregationKey.Leaf(providerId));
                exactContextMatch = false;
            }
            providerDigest = identity.digest();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not resolve typed presentation or identity for pattern provider {}; isolating it",
                    container, exception);
            return;
        }

        activeProviders.put(container, Boolean.TRUE);
        int usedPatternSlots = countUsedPatternSlots(patternInventory);
        discoveredProviders.add(new PatternProviderAggregationEntry(
                container,
                providerId,
                container.getTerminalSortOrder(),
                displayName,
                iconItemId,
                aggregationKey,
                exactContextMatch,
                true,
                isRenameableProvider(container),
                patternInventory.size(),
                usedPatternSlots,
                providerDigest));
    }

    private static ProviderIdentity resolveProviderIdentity(PatternContainer container) {
        ProviderIdentity identity = PatternProviderRuntimeBindings.resolve(container)
                .map(ResolvedProviderBinding::identity)
                .orElseGet(() -> PROVIDER_IDENTITY_RESOLVER.resolve(container));
        if (identity instanceof ProviderIdentity.Virtual) {
            throw new IllegalStateException("Pattern provider has no typed physical or registered identity: " +
                    container);
        }
        return identity;
    }

    /**
     * Resolves stable identity, name, and optional physical location for an actual upload leaf.
     */
    public static PatternUploadTarget resolveProviderUploadTarget(PatternContainer container) {
        Component displayName = resolveProviderDisplayName(container);
        ProviderIdentity identity = resolveProviderIdentity(container);
        return switch (identity) {
            case ProviderIdentity.Block block -> new PatternUploadTarget(
                    identity.digest(), displayName, block.dimensionId(), block.blockPos());
            case ProviderIdentity.Part part -> new PatternUploadTarget(
                    identity.digest(), displayName, part.dimensionId(), part.blockPos());
            case ProviderIdentity.Trinity ignored -> new PatternUploadTarget(
                    identity.digest(), displayName, null, null);
            case ProviderIdentity.External ignored -> new PatternUploadTarget(
                    identity.digest(), displayName, null, null);
            case ProviderIdentity.Virtual ignored -> throw new IllegalStateException(
                    "Pattern upload target resolved to a display-derived virtual identity");
        };
    }

    public static boolean isRenameableProvider(PatternContainer container) {
        return container.isVisibleInTerminal() &&
                (PatternProviderNameHelper.canRename(container) ||
                        PatternProviderNameHelper.getCustomName(container) != null);
    }

    private static List<AggregatedPatternProvider> aggregateDiscoveredProviders(
                                                                                List<PatternProviderAggregationEntry> discoveredProviders,
                                                                                Map<String, Long> leafClickCounts) {
        List<PatternProviderAggregationEntry> sortedProviders = new ArrayList<>(discoveredProviders);
        sortedProviders.sort(createDiscoveredProviderComparator(leafClickCounts));

        Map<PatternProviderAggregationKey, AggregatedPatternProvider> aggregatedProvidersByKey = new LinkedHashMap<>();

        for (var provider : sortedProviders) {
            PatternProviderAggregationKey key = provider.aggregationKey();
            var aggregated = aggregatedProvidersByKey.get(key);
            if (aggregated == null) {
                aggregated = new AggregatedPatternProvider(provider);
                aggregatedProvidersByKey.put(key, aggregated);
            }
            aggregated.include(provider);
        }

        return new ArrayList<>(aggregatedProvidersByKey.values());
    }

    private static Comparator<AggregatedPatternProvider> createAggregatedProviderComparator(
                                                                                             Map<String, Long> leafClickCounts) {
        return Comparator.comparing(AggregatedPatternProvider::exactContextMatch)
                .reversed()
                .thenComparing(Comparator.<AggregatedPatternProvider>comparingLong(
                                provider -> provider.leafCountScore(leafClickCounts))
                        .reversed())
                .thenComparingLong(AggregatedPatternProvider::sortOrder)
                .thenComparing(provider -> provider.displayName().getString());
    }

    private static Comparator<PatternProviderAggregationEntry> createDiscoveredProviderComparator(
                                                                                                   Map<String, Long> leafClickCounts) {
        return Comparator.comparing(PatternProviderAggregationEntry::exactContextMatch)
                .reversed()
                .thenComparing(Comparator.<PatternProviderAggregationEntry>comparingLong(
                                provider -> leafClickCounts.getOrDefault(provider.providerDigest(), 0L))
                        .reversed())
                .thenComparingLong(PatternProviderAggregationEntry::sortOrder)
                .thenComparing(provider -> provider.displayName().getString());
    }

    private static boolean matchesRankingContext(PatternProviderMetadata metadata,
                                                 @Nullable PatternEncodingRankingContext rankingContext) {
        if (rankingContext == null || !metadata.categoryIds().contains(rankingContext.categoryId())) {
            return false;
        }
        List<ResourceLocation> registeredWorkstations = metadata.workstationIds();
        List<ResourceLocation> transferredWorkstations = rankingContext.workstationIds();
        if (registeredWorkstations.isEmpty() || transferredWorkstations.isEmpty()) {
            return registeredWorkstations.isEmpty() && transferredWorkstations.isEmpty();
        }
        return transferredWorkstations.stream().anyMatch(registeredWorkstations::contains);
    }

    @Nullable
    private static Class<? extends PatternContainer> asPatternContainerClass(Class<?> machineClass) {
        return PatternContainer.class.isAssignableFrom(machineClass) ? machineClass.asSubclass(PatternContainer.class) : null;
    }

    private static Component resolveProviderDisplayName(PatternContainer container) {
        return container.getTerminalGroup().name();
    }

    private static ResourceLocation resolveProviderIconItemId(PatternContainer container) {
        ItemStack icon = resolveProviderIcon(container);
        if (icon.isEmpty()) {
            throw new IllegalStateException("Pattern provider does not expose a terminal icon: " + container);
        }
        return BuiltInRegistries.ITEM.getKey(icon.getItem());
    }

    private static ItemStack resolveTerminalGroupIcon(PatternContainer container) {
        var terminalGroup = container.getTerminalGroup();
        return terminalGroup.icon() == null ? ItemStack.EMPTY : terminalGroup.icon().toStack();
    }

    private static ItemStack resolveProviderIcon(PatternContainer container) {
        ItemStack terminalIcon = resolveTerminalGroupIcon(container);
        if (!terminalIcon.isEmpty()) {
            return terminalIcon;
        }

        if (container instanceof AdaptivePatternProviderHost adaptiveHost) {
            return adaptiveHost.getProviderMainMenuIcon();
        }
        if (container instanceof PatternProviderBlockEntity blockEntity) {
            return blockEntity.getMainMenuIcon();
        }
        if (container instanceof PatternProviderPart part) {
            return part.getMainMenuIcon();
        }

        if (container instanceof PatternProviderLogicHost providerHost) {
            var providerTerminalIcon = providerHost.getTerminalIcon();
            if (providerTerminalIcon != null) {
                ItemStack terminalIconStack = providerTerminalIcon.toStack();
                if (!terminalIconStack.isEmpty()) {
                    return terminalIconStack;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    private static int countUsedPatternSlots(InternalInventory inventory) {
        int usedSlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                usedSlots++;
            }
        }
        return usedSlots;
    }

    /**
     * Supplies the mutable name operations required by an atomic provider-group rename.
     */
    interface PatternProviderRenameTarget {

        /**
         * Returns whether this target may participate in a group rename.
         */
        boolean canRename();

        /**
         * Returns the custom name that must be restored if a later target fails.
         */
        @Nullable
        Component customName();

        /**
         * Writes the requested custom name, or reports that the target rejected it.
         */
        boolean setCustomName(@Nullable Component customName);

        /**
         * Persists and publishes the most recently written custom name.
         */
        void syncRename();

        /**
         * Identifies the target in failure logs.
         */
        String description();
    }

    private record PatternContainerRenameTarget(PatternContainer provider) implements PatternProviderRenameTarget {

        @Override
        public boolean canRename() {
            return isRenameableProvider(this.provider);
        }

        @Override
        public @Nullable Component customName() {
            return PatternProviderNameHelper.getCustomName(this.provider);
        }

        @Override
        public boolean setCustomName(@Nullable Component customName) {
            return PatternProviderNameHelper.setCustomName(this.provider, customName);
        }

        @Override
        public void syncRename() {
            PatternProviderNameHelper.syncRename(this.provider);
        }

        @Override
        public String description() {
            return this.provider == null ? "null" : this.provider.getClass().getName();
        }
    }

    /**
     * Captures one discovered provider before typed identity aggregation.
     */
    record PatternProviderAggregationEntry(
                                           PatternContainer container,
                                           long id,
                                           long sortOrder,
                                            Component displayName,
                                            ResourceLocation iconItemId,
                                            PatternProviderAggregationKey aggregationKey,
                                            boolean exactContextMatch,
                                            boolean useAeButtonStyle,
                                           boolean renameable,
                                           int patternSlotCount,
                                           int usedPatternSlotCount,
                                           String providerDigest) {}

    sealed interface PatternProviderAggregationKey {

        record Registered(ResourceLocation registrationId,
                          ProviderIdentityDescriptor providerIdentity) implements PatternProviderAggregationKey {}

        record Core(ProviderIdentityDescriptor providerIdentity) implements PatternProviderAggregationKey {}

        record Leaf(long providerId) implements PatternProviderAggregationKey {}
    }

    private static final class AggregatedPatternProvider {

        private long id;
        private long sortOrder;
        private final Component displayName;
        private final ResourceLocation iconItemId;
        private boolean exactContextMatch;
        private boolean useAeButtonStyle;
        private boolean renameable;
        private int patternSlotCount;
        private int usedPatternSlotCount;
        private final List<PatternContainer> containers = new ArrayList<>();
        private final Set<String> leafDigests = new LinkedHashSet<>();

        private AggregatedPatternProvider(PatternProviderAggregationEntry provider) {
            this.id = provider.id();
            this.sortOrder = provider.sortOrder();
            this.displayName = provider.displayName();
            this.iconItemId = provider.iconItemId();
            this.exactContextMatch = provider.exactContextMatch();
            this.useAeButtonStyle = provider.useAeButtonStyle();
            this.renameable = provider.renameable();
            this.leafDigests.add(provider.providerDigest());
        }

        private void include(PatternProviderAggregationEntry provider) {
            int updatedPatternSlotCount;
            int updatedUsedPatternSlotCount;
            try {
                updatedPatternSlotCount = Math.addExact(this.patternSlotCount, provider.patternSlotCount());
                updatedUsedPatternSlotCount = Math.addExact(this.usedPatternSlotCount,
                        provider.usedPatternSlotCount());
            } catch (ArithmeticException exception) {
                LOGGER.error("Pattern provider slot count overflow while aggregating {} ({})",
                        provider.displayName().getString(), provider.iconItemId(), exception);
                throw exception;
            }

            this.id = Math.min(this.id, provider.id());
            this.sortOrder = Math.min(this.sortOrder, provider.sortOrder());
            this.patternSlotCount = updatedPatternSlotCount;
            this.usedPatternSlotCount = updatedUsedPatternSlotCount;
            this.exactContextMatch |= provider.exactContextMatch();
            this.useAeButtonStyle |= provider.useAeButtonStyle();
            this.renameable &= provider.renameable();
            this.containers.add(provider.container());
            this.leafDigests.add(provider.providerDigest());
        }

        private long id() {
            return this.id;
        }

        private long sortOrder() {
            return this.sortOrder;
        }

        private Component displayName() {
            return this.displayName;
        }

        private ResourceLocation iconItemId() {
            return this.iconItemId;
        }

        private boolean exactContextMatch() {
            return this.exactContextMatch;
        }

        private boolean useAeButtonStyle() {
            return this.useAeButtonStyle;
        }

        private boolean renameable() {
            return this.renameable;
        }

        private int patternSlotCount() {
            return this.patternSlotCount;
        }

        private int usedPatternSlotCount() {
            return this.usedPatternSlotCount;
        }

        private List<PatternContainer> containers() {
            return this.containers;
        }

        private List<String> leafDigests() {
            return this.leafDigests.stream().sorted().toList();
        }

        private long leafCountScore(Map<String, Long> leafClickCounts) {
            long score = 0L;
            for (String digest : this.leafDigests) {
                long count = leafClickCounts.getOrDefault(digest, 0L);
                if (Long.MAX_VALUE - score < count) {
                    return Long.MAX_VALUE;
                }
                score += count;
            }
            return score;
        }

    }
}
