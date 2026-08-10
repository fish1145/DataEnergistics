package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderDisplayHelper;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitContext;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitHook;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.entrypoint.provider.ResolvedProviderBinding;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentityResolver;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.util.PatternProviderNameHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import appeng.parts.encoding.EncodingMode;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

public final class PatternProviderSyncHelper {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final ProviderIdentityResolver PROVIDER_IDENTITY_RESOLVER = ProviderIdentityResolver.create();

    private PatternProviderSyncHelper() {}

    /**
     * Discovers every visible provider and applies the supplied per-leaf history before grouping rows. Ranking
     * context annotates and orders the snapshot but never removes providers from it.
     */
    public static PatternEncodingPreviewMenu.SyncedPatternProviderList collectSyncedPatternProviders(
                                                                                                     @Nullable IGrid grid,
                                                                                                     Map<PatternContainer, Long> syncedPatternProviderIds,
                                                                                                     Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                                                                     LongSupplier nextIdSupplier,
                                                                                                     @Nullable PatternEncodingRankingContext rankingContext,
                                                                                                     List<ResourceLocation> viewerWorkstationIds,
                                                                                                     Map<String, Long> leafClickCounts) {
        syncedProviderTargetsById.clear();
        if (grid == null) {
            syncedPatternProviderIds.clear();
            return PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY;
        }
        List<PatternProviderAggregationEntry> discoveredProviders = new ArrayList<>();
        Set<PatternContainer> activeProviders = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PatternContainer> discoveredProviderSet = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ResourceLocation> viewerWorkstations = Set.copyOf(viewerWorkstationIds);

        collectDirectPatternProviders(grid, syncedPatternProviderIds, nextIdSupplier, discoveredProviders, activeProviders,
                discoveredProviderSet, rankingContext, viewerWorkstations);

        for (var machineClass : grid.getMachineClasses()) {
            var patternContainerClass = asPatternContainerClass(machineClass);
            if (patternContainerClass == null) {
                continue;
            }

            for (var container : grid.getMachines(patternContainerClass)) {
                addProviderIfVisible(container, syncedPatternProviderIds, nextIdSupplier, discoveredProviders, activeProviders,
                        discoveredProviderSet, rankingContext, viewerWorkstations);
            }
        }

        syncedPatternProviderIds.keySet().removeIf(provider -> !activeProviders.contains(provider));

        return aggregateSyncedPatternProviders(
                discoveredProviders, syncedProviderTargetsById, leafClickCounts, rankingContext,
                viewerWorkstationIds);
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
     * Aggregates provider leaves and ranks synchronized rows with server-authoritative context and history.
     */
    static PatternEncodingPreviewMenu.SyncedPatternProviderList aggregateSyncedPatternProviders(
                                                                                                List<PatternProviderAggregationEntry> discoveredProviders,
                                                                                                Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                                                                Map<String, Long> leafClickCounts) {
        return aggregateSyncedPatternProviders(
                discoveredProviders, syncedProviderTargetsById, leafClickCounts, null, List.of());
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProviderList aggregateSyncedPatternProviders(
                                                                                                        List<PatternProviderAggregationEntry> discoveredProviders,
                                                                                                        Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                                                                        Map<String, Long> leafClickCounts,
                                                                                                        @Nullable PatternEncodingRankingContext rankingContext,
                                                                                                        List<ResourceLocation> viewerWorkstationIds) {
        syncedProviderTargetsById.clear();
        List<AggregatedPatternProvider> aggregatedProviders = aggregateDiscoveredProviders(discoveredProviders);
        aggregatedProviders.sort(createAggregatedProviderRankingComparator(leafClickCounts));

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
                    provider.leafDigests(),
                    provider.supportedRecipeTypeIds(),
                    provider.exactContextMatch(),
                    provider.preferredWorkstationId()));
        }

        if (providers.isEmpty() && rankingContext == null) {
            return PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY;
        }
        return new PatternEncodingPreviewMenu.SyncedPatternProviderList(
                providers, rankingContext, viewerWorkstationIds);
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

    /**
     * Captures the server-owned upload context for the current encoder mode.
     */
    public static PatternUploadContext createPatternUploadContext(
                                                                  PatternEncodingPreviewMenu previewMenu,
                                                                  PatternEncodingPreferenceSession session,
                                                                  long providerId) {
        EncodingMode mode = previewMenu.data_energistics$getEncodingMode();
        if (mode == EncodingMode.PROCESSING) {
            PatternEncodingRankingContext rankingContext = session.rankingContext();
            ResourceLocation preferredWorkstation = resolveSyncedProviderWorkstation(
                    previewMenu.data_energistics$getSyncedPatternProviderState(),
                    rankingContext,
                    session.viewerWorkstationIds(),
                    providerId);
            return new PatternUploadContext(
                    mode,
                    rankingContext,
                    session.viewerWorkstationIds(),
                    preferredWorkstation);
        }

        ResourceLocation workstationId = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode);
        PatternEncodingRankingContext fixedContext = PatternEncodingSourceHelper.resolveFixedModeRankingContext(mode, workstationId);
        if (fixedContext == null) {
            throw new IllegalStateException("Could not derive the fixed upload context for encoding mode " + mode);
        }
        session.setRankingContext(fixedContext);
        return new PatternUploadContext(mode, fixedContext, List.of(), null);
    }

    @Nullable
    private static ResourceLocation resolveSyncedProviderWorkstation(
                                                                     PatternEncodingPreviewMenu.SyncedPatternProviderList providerState,
                                                                     @Nullable PatternEncodingRankingContext rankingContext,
                                                                     List<ResourceLocation> viewerWorkstationIds,
                                                                     long providerId) {
        if (!Objects.equals(providerState.rankingContext(), rankingContext) ||
                !providerState.viewerWorkstationIds().equals(viewerWorkstationIds)) {
            return null;
        }
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : providerState.providers()) {
            if (provider.id() == providerId) {
                return provider.preferredWorkstationId();
            }
        }
        return null;
    }

    public static TransferResult transferEncodedPatternToProvidersChecked(
                                                                          List<PatternContainer> containers,
                                                                          ItemStack encodedPattern,
                                                                          PatternUploadContext uploadContext) {
        if (containers.isEmpty() || encodedPattern.isEmpty()) {
            return TransferResult.noTransfer(encodedPattern);
        }

        if (uploadContext.processing() && uploadContext.rankingContext() == null) {
            LOGGER.warn(
                    "Rejected processing pattern upload without a viewer recipe-type context: providers={}, resolvedWorkstation={}",
                    containers.size(),
                    uploadContext.resolvedWorkstation());
            return TransferResult.rejected(encodedPattern, PatternUploadRejection.CONTEXT_UNAVAILABLE);
        }

        PreparationResult preparation = preparePatternUploads(containers, uploadContext);
        if (preparation.rejection() != PatternUploadRejection.NONE) {
            return TransferResult.rejected(encodedPattern, preparation.rejection());
        }
        List<PreparedPatternUpload> preparedUploads = preparation.uploads();
        if (preparedUploads.isEmpty()) {
            return TransferResult.rejected(encodedPattern, PatternUploadRejection.TARGET_UNAVAILABLE);
        }

        if (containsEquivalentEncodedPattern(preparedUploads, encodedPattern)) {
            return new TransferResult(
                    encodedPattern,
                    false,
                    true,
                    null,
                    PatternUploadRejection.NONE);
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

        return new TransferResult(
                transferred ? remainder : encodedPattern,
                transferred,
                false,
                firstCommittedTarget,
                PatternUploadRejection.NONE);
    }

    private static PreparationResult preparePatternUploads(
                                                           List<PatternContainer> containers,
                                                           PatternUploadContext uploadContext) {
        List<PreparedPatternUpload> preparedUploads = new ArrayList<>(containers.size());
        for (PatternContainer container : containers) {
            try {
                ProviderResolution provider = resolveProvider(container);
                WorkstationResolution workstation = resolveWorkstation(container, provider, uploadContext);
                if (workstation.rejection() != PatternUploadRejection.NONE) {
                    logContextRejection(provider, uploadContext, workstation.rejection());
                    return new PreparationResult(List.of(), workstation.rejection());
                }
                PatternUploadTarget target = createProviderUploadTarget(
                        container,
                        provider.identity(),
                        workstation.workstationId());
                preparedUploads.add(new PreparedPatternUpload(container, target));
            } catch (RuntimeException exception) {
                LOGGER.error("Could not resolve a typed upload target for {}; rejecting the group before inventory mutation",
                        container, exception);
                return new PreparationResult(List.of(), PatternUploadRejection.TARGET_UNAVAILABLE);
            }
        }
        return new PreparationResult(List.copyOf(preparedUploads), PatternUploadRejection.NONE);
    }

    private static WorkstationResolution resolveWorkstation(
                                                            PatternContainer container,
                                                            ProviderResolution provider,
                                                            PatternUploadContext uploadContext) {
        if (!uploadContext.processing()) {
            return WorkstationResolution.accepted(null);
        }
        PatternEncodingRankingContext rankingContext = uploadContext.rankingContext();
        if (rankingContext == null) {
            return WorkstationResolution.rejected(PatternUploadRejection.CONTEXT_UNAVAILABLE);
        }
        ResolvedProviderBinding binding = provider.binding();
        if (binding == null) {
            Set<ResourceLocation> viewerWorkstations = Set.copyOf(uploadContext.viewerWorkstationIds());
            if (viewerWorkstations.isEmpty()) {
                return WorkstationResolution.rejected(PatternUploadRejection.PROVIDER_CONTEXT_UNKNOWN);
            }
            ResourceLocation exposedWorkstation = resolveProviderContextWorkstationId(container);
            return viewerWorkstations.contains(exposedWorkstation) ?
                    WorkstationResolution.accepted(exposedWorkstation) :
                    WorkstationResolution.rejected(PatternUploadRejection.TARGET_UNAVAILABLE);
        }
        PatternProviderMetadata metadata = binding.registration().metadata();
        if (!metadata.categoryIds().contains(rankingContext.recipeTypeId())) {
            return WorkstationResolution.rejected(PatternUploadRejection.PROVIDER_CONTEXT_UNKNOWN);
        }
        Set<ResourceLocation> viewerWorkstations = Set.copyOf(uploadContext.viewerWorkstationIds());
        List<ResourceLocation> candidates = resolveMatchingWorkstationIds(
                metadata, rankingContext, viewerWorkstations);
        if (!viewerWorkstations.isEmpty() && candidates.isEmpty()) {
            return WorkstationResolution.rejected(PatternUploadRejection.TARGET_UNAVAILABLE);
        }
        if (candidates.isEmpty()) {
            return WorkstationResolution.accepted(null);
        }
        ResourceLocation resolvedWorkstation = uploadContext.resolvedWorkstation();
        if (resolvedWorkstation != null && candidates.contains(resolvedWorkstation)) {
            return WorkstationResolution.accepted(resolvedWorkstation);
        }
        return WorkstationResolution.accepted(candidates.getFirst());
    }

    private static void logContextRejection(
                                            ProviderResolution provider,
                                            PatternUploadContext uploadContext,
                                            PatternUploadRejection rejection) {
        ResolvedProviderBinding binding = provider.binding();
        PatternProviderMetadata metadata = binding == null ? null : binding.registration().metadata();
        LOGGER.warn(
                "Rejected processing pattern upload before inventory mutation: reason={}, providerIdentity={}, registrationId={}, recipeType={}, viewerWorkstations={}, registeredRecipeTypes={}, registeredWorkstations={}, resolvedWorkstation={}",
                rejection,
                provider.identity(),
                metadata == null ? null : metadata.registrationId(),
                uploadContext.rankingContext() == null ? null : uploadContext.rankingContext().recipeTypeId(),
                uploadContext.viewerWorkstationIds(),
                metadata == null ? List.of() : metadata.categoryIds(),
                metadata == null ? List.of() : metadata.workstationIds(),
                uploadContext.resolvedWorkstation());
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

    private record PreparedPatternUpload(PatternContainer container,
                                         PatternUploadTarget target) {}

    private record PreparationResult(List<PreparedPatternUpload> uploads,
                                     PatternUploadRejection rejection) {}

    private record WorkstationResolution(@Nullable ResourceLocation workstationId,
                                         PatternUploadRejection rejection) {

        private static WorkstationResolution accepted(@Nullable ResourceLocation workstationId) {
            return new WorkstationResolution(workstationId, PatternUploadRejection.NONE);
        }

        private static WorkstationResolution rejected(PatternUploadRejection rejection) {
            return new WorkstationResolution(null, rejection);
        }
    }

    /**
     * Result of one upload attempt, including the first inventory that actually accepted a pattern.
     */
    public record TransferResult(ItemStack remainder, boolean transferred, boolean duplicateFound,
                                 @Nullable PatternUploadTarget firstCommittedTarget,
                                 PatternUploadRejection rejection) {

        public TransferResult {
            if ((transferred && firstCommittedTarget == null) ||
                    (!transferred && firstCommittedTarget != null)) {
                throw new IllegalArgumentException(
                        "A pattern upload result must expose one committed target exactly when a transfer occurred");
            }
            if (transferred && (duplicateFound || rejection != PatternUploadRejection.NONE)) {
                throw new IllegalArgumentException(
                        "A committed pattern upload cannot also be a duplicate or rejection");
            }
            if (duplicateFound && rejection != PatternUploadRejection.NONE) {
                throw new IllegalArgumentException("A duplicate pattern upload cannot also be rejected");
            }
        }

        public boolean rejected() {
            return this.rejection != PatternUploadRejection.NONE;
        }

        /**
         * Returns the committed target after {@link #transferred()} has established the success variant.
         */
        public PatternUploadTarget committedTarget() {
            if (this.firstCommittedTarget == null) {
                throw new IllegalStateException("Pattern upload result did not commit to a provider target");
            }
            return this.firstCommittedTarget;
        }

        private static TransferResult noTransfer(ItemStack remainder) {
            return new TransferResult(remainder, false, false, null, PatternUploadRejection.NONE);
        }

        private static TransferResult rejected(ItemStack remainder, PatternUploadRejection rejection) {
            return new TransferResult(remainder, false, false, null, rejection);
        }
    }

    /**
     * Server-owned information used to validate one upload before any inventory mutation.
     */
    public record PatternUploadContext(EncodingMode mode,
                                       @Nullable PatternEncodingRankingContext rankingContext,
                                       List<ResourceLocation> viewerWorkstationIds,
                                       @Nullable ResourceLocation resolvedWorkstation) {

        public PatternUploadContext {
            viewerWorkstationIds = List.copyOf(viewerWorkstationIds);
        }

        public boolean processing() {
            return this.mode == EncodingMode.PROCESSING;
        }
    }

    /**
     * Explicit reason why a provider upload was rejected before inventory mutation.
     */
    public enum PatternUploadRejection {

        NONE(null),
        CONTEXT_UNAVAILABLE("data_energistics.pattern_transfer.context_unavailable"),
        PROVIDER_CONTEXT_UNKNOWN("message.data_energistics.pattern_provider.context_unknown"),
        TARGET_UNAVAILABLE("message.data_energistics.pattern_provider.target_unavailable");

        @Nullable
        private final String messageKey;

        PatternUploadRejection(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /**
         * Returns the localized rejection key after the caller has established that this is not {@link #NONE}.
         */
        public String messageKeyOrThrow() {
            if (this.messageKey == null) {
                throw new IllegalStateException("The successful upload state has no rejection message");
            }
            return this.messageKey;
        }
    }

    /**
     * Stable successful upload target returned only after its inventory has actually changed.
     */
    public record PatternUploadTarget(String providerDigest, Component targetName,
                                      @Nullable ResourceLocation dimensionId, @Nullable BlockPos position,
                                      @Nullable ResourceLocation confirmedWorkstation) {

        public PatternUploadTarget {
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
                                                      Set<PatternContainer> activeProviders,
                                                      Set<PatternContainer> discoveredProviderSet,
                                                      @Nullable PatternEncodingRankingContext rankingContext,
                                                      Set<ResourceLocation> viewerWorkstationIds) {
        try {
            for (var providerHost : grid.getMachines(PatternProviderLogicHost.class)) {
                addProviderIfVisible(providerHost, syncedPatternProviderIds, nextIdSupplier, discoveredProviders,
                        activeProviders, discoveredProviderSet, rankingContext, viewerWorkstationIds);
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
                                             Set<PatternContainer> activeProviders,
                                             Set<PatternContainer> discoveredProviderSet,
                                             @Nullable PatternEncodingRankingContext rankingContext,
                                             Set<ResourceLocation> viewerWorkstationIds) {
        if (!container.isVisibleInTerminal() || discoveredProviderSet.contains(container)) {
            return;
        }

        var patternInventory = container.getTerminalPatternInventory();
        if (patternInventory.size() <= 0) {
            return;
        }

        discoveredProviderSet.add(container);

        long providerId = syncedPatternProviderIds.computeIfAbsent(container,
                ignored -> nextIdSupplier.getAsLong());
        Component displayName;
        ResourceLocation iconItemId;
        PatternProviderAggregationKey aggregationKey;
        String providerDigest;
        boolean exactContextMatch;
        List<ResourceLocation> supportedRecipeTypeIds;
        List<ResourceLocation> matchingWorkstationIds;
        try {
            ProviderPresentation presentation = resolveProviderPresentation(container);
            displayName = presentation.displayName();
            iconItemId = presentation.iconItemId();
            ResourceLocation contextWorkstationId = resolveProviderContextWorkstationId(container);
            ProviderResolution provider = resolveProvider(container);
            ProviderIdentity identity = provider.identity();
            if (provider.binding() != null) {
                PatternProviderMetadata metadata = provider.binding().registration().metadata();
                aggregationKey = new PatternProviderAggregationKey.Registered(
                        metadata.registrationId(), metadata.providerIdentity());
                exactContextMatch = matchesRankingContext(metadata, rankingContext, viewerWorkstationIds);
                supportedRecipeTypeIds = metadata.categoryIds();
                matchingWorkstationIds = resolveMatchingWorkstationIds(
                        metadata, rankingContext, viewerWorkstationIds);
            } else {
                aggregationKey = PatternProviderAggregationKey.NetworkGroup.from(container.getTerminalGroup());
                exactContextMatch = matchesNetworkExposedWorkstation(
                        contextWorkstationId, rankingContext, viewerWorkstationIds);
                supportedRecipeTypeIds = List.of();
                matchingWorkstationIds = exactContextMatch ? List.of(contextWorkstationId) : List.of();
            }
            aggregationKey = resolveDisplayAggregationKey(
                    aggregationKey,
                    presentation.hasCustomName(),
                    providerId);
            providerDigest = identity.digest();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not resolve typed presentation or identity for pattern provider {}; isolating it",
                    container, exception);
            return;
        }

        activeProviders.add(container);
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
                providerDigest,
                supportedRecipeTypeIds,
                matchingWorkstationIds));
    }

    private static ProviderResolution resolveProvider(PatternContainer container) {
        Optional<ResolvedProviderBinding> resolved = PatternProviderRuntimeBindings.resolve(container);
        ProviderIdentity identity;
        if (resolved.isPresent()) {
            identity = resolved.get().identity();
        } else if (container instanceof PatternProviderIdentitySource source) {
            identity = ProviderIdentity.fromExternal(source.providerIdentity(), "External pattern provider");
        } else {
            identity = PROVIDER_IDENTITY_RESOLVER.resolve(container);
        }
        if (identity instanceof ProviderIdentity.Virtual) {
            throw new IllegalStateException("Pattern provider has no typed physical or registered identity: " +
                    container);
        }
        return new ProviderResolution(identity, resolved.orElse(null));
    }

    /**
     * Resolves stable identity, name, and optional physical location for an actual upload leaf.
     */
    public static PatternUploadTarget resolveProviderUploadTarget(PatternContainer container) {
        ProviderResolution provider = resolveProvider(container);
        return createProviderUploadTarget(container, provider.identity(), null);
    }

    private static PatternUploadTarget createProviderUploadTarget(
                                                                  PatternContainer container,
                                                                  ProviderIdentity identity,
                                                                  @Nullable ResourceLocation confirmedWorkstation) {
        Component displayName = resolveProviderPresentation(container).displayName();
        return switch (identity) {
            case ProviderIdentity.Block block -> new PatternUploadTarget(
                    identity.digest(), displayName, block.dimensionId(), block.blockPos(), confirmedWorkstation);
            case ProviderIdentity.Part part -> new PatternUploadTarget(
                    identity.digest(), displayName, part.dimensionId(), part.blockPos(), confirmedWorkstation);
            case ProviderIdentity.Trinity ignored -> new PatternUploadTarget(
                    identity.digest(), displayName, null, null, confirmedWorkstation);
            case ProviderIdentity.External ignored -> new PatternUploadTarget(
                    identity.digest(), displayName, null, null, confirmedWorkstation);
            case ProviderIdentity.Virtual ignored -> throw new IllegalStateException(
                    "Pattern upload target resolved to a display-derived virtual identity");
        };
    }

    private record ProviderResolution(ProviderIdentity identity,
                                      @Nullable ResolvedProviderBinding binding) {}

    private record ProviderPresentation(Component displayName,
                                        ResourceLocation iconItemId,
                                        boolean hasCustomName) {}

    public static boolean isRenameableProvider(PatternContainer container) {
        return container.isVisibleInTerminal() &&
                (PatternProviderNameHelper.canRename(container) ||
                        PatternProviderNameHelper.getCustomName(container) != null);
    }

    private static List<AggregatedPatternProvider> aggregateDiscoveredProviders(
                                                                                List<PatternProviderAggregationEntry> discoveredProviders) {
        List<PatternProviderAggregationEntry> sortedProviders = new ArrayList<>(discoveredProviders);
        sortedProviders.sort(createStableDiscoveredProviderComparator());

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

    /**
     * Keeps a provider with a user-defined name isolated so its display and rename action never affect another leaf.
     */
    private static PatternProviderAggregationKey resolveDisplayAggregationKey(
                                                                              PatternProviderAggregationKey defaultKey,
                                                                              boolean hasCustomName,
                                                                              long providerId) {
        return hasCustomName ? new PatternProviderAggregationKey.Leaf(providerId) : defaultKey;
    }

    private static Comparator<AggregatedPatternProvider> createAggregatedProviderRankingComparator(
                                                                                                   Map<String, Long> leafClickCounts) {
        return Comparator.comparing(AggregatedPatternProvider::exactContextMatch)
                .reversed()
                .thenComparing(Comparator.<AggregatedPatternProvider>comparingLong(
                        provider -> provider.leafCountScore(leafClickCounts))
                        .reversed())
                .thenComparingLong(AggregatedPatternProvider::sortOrder)
                .thenComparing(provider -> provider.displayName().getString())
                .thenComparing(provider -> provider.leafDigests().getFirst());
    }

    private static Comparator<PatternProviderAggregationEntry> createStableDiscoveredProviderComparator() {
        return Comparator.comparingLong(PatternProviderAggregationEntry::sortOrder)
                .thenComparing(provider -> provider.displayName().getString())
                .thenComparing(PatternProviderAggregationEntry::providerDigest);
    }

    private static boolean matchesRankingContext(PatternProviderMetadata metadata,
                                                 @Nullable PatternEncodingRankingContext rankingContext,
                                                 Set<ResourceLocation> viewerWorkstationIds) {
        return matchesRecipeType(metadata, rankingContext) &&
                (viewerWorkstationIds.isEmpty() || metadata.workstationIds().stream()
                        .anyMatch(viewerWorkstationIds::contains));
    }

    static boolean matchesViewerRecipe(PatternProviderMetadata metadata,
                                       @Nullable PatternEncodingRankingContext rankingContext,
                                       List<ResourceLocation> viewerWorkstationIds) {
        return matchesRankingContext(metadata, rankingContext, Set.copyOf(viewerWorkstationIds));
    }

    static boolean matchesRecipeType(PatternProviderMetadata metadata,
                                     @Nullable PatternEncodingRankingContext rankingContext) {
        return rankingContext != null && metadata.categoryIds().contains(rankingContext.recipeTypeId());
    }

    private static boolean matchesNetworkExposedWorkstation(
                                                            ResourceLocation exposedWorkstationId,
                                                            @Nullable PatternEncodingRankingContext rankingContext,
                                                            Set<ResourceLocation> viewerWorkstationIds) {
        return rankingContext != null && viewerWorkstationIds.contains(exposedWorkstationId);
    }

    private static List<ResourceLocation> resolveMatchingWorkstationIds(
                                                                        PatternProviderMetadata metadata,
                                                                        @Nullable PatternEncodingRankingContext rankingContext,
                                                                        Set<ResourceLocation> viewerWorkstationIds) {
        if (!matchesRecipeType(metadata, rankingContext)) {
            return List.of();
        }
        if (viewerWorkstationIds.isEmpty()) {
            return metadata.workstationIds();
        }
        return metadata.workstationIds().stream()
                .filter(viewerWorkstationIds::contains)
                .toList();
    }

    @Nullable
    private static Class<? extends PatternContainer> asPatternContainerClass(Class<?> machineClass) {
        return PatternContainer.class.isAssignableFrom(machineClass) ? machineClass.asSubclass(PatternContainer.class) : null;
    }

    private static Component resolveProviderDisplayName(PatternContainer container,
                                                        @Nullable Component customName,
                                                        @Nullable PatternContainerGroup attachedMachineGroup) {
        if (customName == null) {
            return container.getTerminalGroup().name();
        }
        Component baseName = attachedMachineGroup == null ?
                resolveProviderFallbackDisplayName(container) : attachedMachineGroup.name();
        return AdaptivePatternProviderDisplayHelper.decorateAttachedMachineName(baseName, customName);
    }

    private static ProviderPresentation resolveProviderPresentation(PatternContainer container) {
        Component customName = PatternProviderNameHelper.getCustomName(container);
        PatternContainerGroup attachedMachineGroup = customName == null ? null :
                resolveDefaultAttachedMachineGroup(container);
        return new ProviderPresentation(
                resolveProviderDisplayName(container, customName, attachedMachineGroup),
                resolveProviderIconItemId(container, attachedMachineGroup),
                customName != null);
    }

    private static Component resolveProviderFallbackDisplayName(PatternContainer container) {
        ItemStack icon = resolveProviderIcon(container, null);
        if (icon.isEmpty()) {
            throw new IllegalStateException("Pattern provider does not expose a fallback display name: " + container);
        }
        return icon.getHoverName();
    }

    /**
     * Preserves the terminal's original workstation identity while custom-name presentation uses an attached machine.
     */
    private static ResourceLocation resolveProviderContextWorkstationId(PatternContainer container) {
        return resolveProviderIconItemId(container, null);
    }

    private static ResourceLocation resolveProviderIconItemId(PatternContainer container,
                                                              @Nullable PatternContainerGroup attachedMachineGroup) {
        ItemStack icon = resolveProviderIcon(container, attachedMachineGroup);
        if (icon.isEmpty()) {
            throw new IllegalStateException("Pattern provider does not expose a terminal icon: " + container);
        }
        return BuiltInRegistries.ITEM.getKey(icon.getItem());
    }

    private static ItemStack resolveTerminalGroupIcon(PatternContainer container) {
        return resolvePatternContainerGroupIcon(container.getTerminalGroup());
    }

    private static ItemStack resolveProviderIcon(PatternContainer container,
                                                 @Nullable PatternContainerGroup attachedMachineGroup) {
        ItemStack attachedMachineIcon = resolvePatternContainerGroupIcon(attachedMachineGroup);
        if (!attachedMachineIcon.isEmpty()) {
            return attachedMachineIcon;
        }

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

    private static ItemStack resolvePatternContainerGroupIcon(@Nullable PatternContainerGroup group) {
        return group == null || group.icon() == null ? ItemStack.EMPTY : group.icon().toStack();
    }

    /**
     * Recreates AE2's default single-machine terminal group before a custom name is appended as a suffix.
     */
    @Nullable
    private static PatternContainerGroup resolveDefaultAttachedMachineGroup(PatternContainer container) {
        if (container instanceof AdaptivePatternProviderHost adaptiveHost) {
            return adaptiveHost.getPrimaryAttachedMachineGroup();
        }
        if (!(container instanceof PatternProviderLogicHost providerHost) ||
                !(providerHost.getLogic() instanceof PatternProviderBatchAccess logic)) {
            return null;
        }

        var providerBlockEntity = providerHost.getBlockEntity();
        var level = providerBlockEntity.getLevel();
        if (level == null) {
            return null;
        }

        Set<PatternContainerGroup> activeGroups = resolveAttachedMachineGroups(
                level,
                providerBlockEntity.getBlockPos(),
                logic.dataEnergistics$invokeGetActiveSides());
        return activeGroups.size() == 1 ? activeGroups.iterator().next() : null;
    }

    private static Set<PatternContainerGroup> resolveAttachedMachineGroups(Level level,
                                                                           BlockPos providerPosition,
                                                                           Set<Direction> sides) {
        Set<PatternContainerGroup> groups = new LinkedHashSet<>();
        for (var side : sides) {
            var group = PatternContainerGroup.fromMachine(
                    level,
                    providerPosition.relative(side),
                    side.getOpposite());
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
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

    private record PatternContainerRenameTarget(PatternContainer provider)
            implements PatternProviderRenameTarget {

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
            return this.provider.getClass().getName();
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
                                           String providerDigest,
                                           List<ResourceLocation> supportedRecipeTypeIds,
                                           List<ResourceLocation> matchingWorkstationIds) {

        PatternProviderAggregationEntry(
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
                                        String providerDigest) {
            this(
                    container,
                    id,
                    sortOrder,
                    displayName,
                    iconItemId,
                    aggregationKey,
                    exactContextMatch,
                    useAeButtonStyle,
                    renameable,
                    patternSlotCount,
                    usedPatternSlotCount,
                    providerDigest,
                    List.of(),
                    List.of());
        }

        PatternProviderAggregationEntry {
            supportedRecipeTypeIds = List.copyOf(supportedRecipeTypeIds);
            matchingWorkstationIds = List.copyOf(matchingWorkstationIds);
        }
    }

    sealed interface PatternProviderAggregationKey {

        record Registered(ResourceLocation registrationId,
                          ProviderIdentityDescriptor providerIdentity)
                implements PatternProviderAggregationKey {}

        record NetworkGroup(@Nullable AEItemKey icon,
                            Component name,
                            List<Component> tooltip)
                implements PatternProviderAggregationKey {

            public NetworkGroup {
                tooltip = List.copyOf(tooltip);
            }

            private static NetworkGroup from(PatternContainerGroup terminalGroup) {
                return new NetworkGroup(terminalGroup.icon(), terminalGroup.name(), terminalGroup.tooltip());
            }
        }

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
        private final Set<ResourceLocation> supportedRecipeTypeIds = new LinkedHashSet<>();
        private final Set<ResourceLocation> matchingWorkstationIds = new LinkedHashSet<>();

        private AggregatedPatternProvider(PatternProviderAggregationEntry provider) {
            this.id = provider.id();
            this.sortOrder = provider.sortOrder();
            this.displayName = provider.displayName();
            this.iconItemId = provider.iconItemId();
            this.exactContextMatch = provider.exactContextMatch();
            this.useAeButtonStyle = provider.useAeButtonStyle();
            this.renameable = provider.renameable();
            this.leafDigests.add(provider.providerDigest());
            this.supportedRecipeTypeIds.addAll(provider.supportedRecipeTypeIds());
            this.matchingWorkstationIds.addAll(provider.matchingWorkstationIds());
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
            this.supportedRecipeTypeIds.addAll(provider.supportedRecipeTypeIds());
            this.matchingWorkstationIds.addAll(provider.matchingWorkstationIds());
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

        private List<ResourceLocation> supportedRecipeTypeIds() {
            return this.supportedRecipeTypeIds.stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .toList();
        }

        @Nullable
        private ResourceLocation preferredWorkstationId() {
            return this.matchingWorkstationIds.stream()
                    .min(Comparator.comparing(ResourceLocation::toString))
                    .orElse(null);
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
