package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderDisplayHelper;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationSource;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.entrypoint.provider.ResolvedProviderBinding;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentityResolver;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.source.PatternEncodingSourceHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import appeng.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
                                                                                                     Reference2LongMap<PatternContainer> syncedPatternProviderIds,
                                                                                                     Long2ObjectMap<ObjectList<PatternContainer>> syncedProviderTargetsById,
                                                                                                     LongSupplier nextIdSupplier,
                                                                                                     @Nullable PatternEncodingRankingContext rankingContext,
                                                                                                     Object2LongMap<String> leafClickCounts) {
        syncedProviderTargetsById.clear();
        if (grid == null) {
            syncedPatternProviderIds.clear();
            return PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY;
        }
        ObjectList<PatternProviderAggregationEntry> discoveredProviders = new ObjectArrayList<>();
        ReferenceSet<PatternContainer> activeProviders = new ReferenceOpenHashSet<>();
        ReferenceSet<PatternContainer> discoveredProviderSet = new ReferenceOpenHashSet<>();
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

        syncedPatternProviderIds.keySet().removeIf(provider -> !activeProviders.contains(provider));

        return aggregateSyncedPatternProviders(
                discoveredProviders, syncedProviderTargetsById, leafClickCounts, rankingContext);
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProviderList aggregateSyncedPatternProviders(
                                                                                                        List<PatternProviderAggregationEntry> discoveredProviders,
                                                                                                        Long2ObjectMap<ObjectList<PatternContainer>> syncedProviderTargetsById,
                                                                                                        Object2LongMap<String> leafClickCounts,
                                                                                                        @Nullable PatternEncodingRankingContext rankingContext) {
        syncedProviderTargetsById.clear();
        List<AggregatedPatternProvider> aggregatedProviders = aggregateDiscoveredProviders(discoveredProviders);
        aggregatedProviders.sort(createAggregatedProviderRankingComparator(leafClickCounts));

        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = new ObjectArrayList<>(aggregatedProviders.size());
        for (var provider : aggregatedProviders) {
            syncedProviderTargetsById.put(provider.id(), provider.containers());
            providers.add(new PatternEncodingPreviewMenu.SyncedPatternProvider(
                    provider.id(),
                    provider.displayName(),
                    provider.iconItemId(),
                    provider.useAeButtonStyle(),
                    provider.renameable(),
                    provider.patternSlotCount(),
                    provider.usedPatternSlotCount(),
                    provider.syncedLeaves(),
                    provider.supportedRecipeTypeIds(),
                    provider.exactContextMatch(),
                    provider.preferredWorkstationId()));
        }

        if (providers.isEmpty() && rankingContext == null) {
            return PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY;
        }
        return new PatternEncodingPreviewMenu.SyncedPatternProviderList(providers, rankingContext);
    }

    @Nullable
    @SuppressWarnings("ConstantConditions") // fastutil returns its null default value for an unknown menu-local id.
    public static ObjectList<PatternContainer> findProvidersById(
                                                                 Long2ObjectMap<ObjectList<PatternContainer>> syncedProviderTargetsById,
                                                                 long providerId) {
        return syncedProviderTargetsById.get(providerId);
    }

    /** Resolves one exact leaf while proving that it still belongs to the selected synchronized group. */
    @Nullable
    public static PatternContainer findProviderLeafById(
                                                        Reference2LongMap<PatternContainer> syncedPatternProviderIds,
                                                        Long2ObjectMap<ObjectList<PatternContainer>> syncedProviderTargetsById,
                                                        long groupId,
                                                        long leafId) {
        ObjectList<PatternContainer> group = findProvidersById(syncedProviderTargetsById, groupId);
        if (group == null) {
            return null;
        }
        for (PatternContainer provider : group) {
            long candidateId = syncedPatternProviderIds.getLong(provider);
            if (candidateId == leafId && provider.isVisibleInTerminal()) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Renames every provider in one synchronized display group as a single transaction.
     */
    public static void renamePatternProviders(List<PatternContainer> providers, @Nullable String name) {
        ObjectArrayList<PatternProviderRenameTarget> targets = new ObjectArrayList<>(providers.size());
        for (PatternContainer provider : providers) {
            targets.add(new PatternContainerRenameTarget(provider));
        }
        renamePatternProviderTargets(targets, name);
    }

    /**
     * Applies one custom name to a provider group as an atomic transaction.
     * The target abstraction keeps rollback behavior testable while production targets delegate to
     * {@link PatternProviderNameHelper}.
     */
    private static void renamePatternProviderTargets(
                                                     List<? extends PatternProviderRenameTarget> targets,
                                                     @Nullable String name) {
        if (targets.isEmpty()) {
            LOGGER.warn("Rejected pattern provider rename because the provider group is empty");
            return;
        }

        String sanitized = name == null ? "" : name.trim();
        Component customName = sanitized.isEmpty() ? null : Component.literal(sanitized);
        ObjectList<@Nullable Component> originalNames = new ObjectArrayList<>(targets.size());
        int modifiedCount = 0;
        try {
            for (var target : targets) {
                if (!target.canRename()) {
                    LOGGER.warn("Rejected pattern provider group rename because {} is not renameable",
                            target.description());
                    return;
                }
            }
            for (var target : targets) {
                originalNames.add(target.customName());
            }
            for (var target : targets) {
                boolean renamed = target.setCustomName(customName);
                if (!renamed) {
                    throw new IllegalStateException("Pattern provider rejected its custom name update: " +
                            target.description());
                }
                modifiedCount++;
                target.syncRename();
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to rename pattern provider group; rolling back {} modified providers",
                    modifiedCount, exception);
            rollbackPatternProviderNames(targets, originalNames, modifiedCount);
        }
    }

    private static void rollbackPatternProviderNames(List<? extends PatternProviderRenameTarget> targets,
                                                     ObjectList<@Nullable Component> originalNames,
                                                     int modifiedCount) {
        for (int index = modifiedCount - 1; index >= 0; index--) {
            PatternProviderRenameTarget target = targets.get(index);
            try {
                boolean restored = target.setCustomName(originalNames.get(index));
                if (!restored) {
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

    /**
     * Captures the server-owned context used to attribute a successful upload without filtering its target.
     */
    public static PatternUploadContext createPatternUploadContext(
                                                                  ServerPlayer player,
                                                                  PatternEncodingPreviewMenu previewMenu,
                                                                  PatternEncodingPreferenceSession session,
                                                                  long providerId) {
        EncodingMode mode = previewMenu.data_energistics$getEncodingMode();
        EncodedPatternRecipeReference recipeReference = previewMenu.data_energistics$getEncodedPatternRecipeReference();
        PatternEncodingRankingContext persistedContext = recipeReference == null ? null :
                PatternEncodingRankingContext.of(recipeReference.recipeTypeId());
        if (mode == EncodingMode.PROCESSING) {
            PatternEncodingRankingContext rankingContext = persistedContext == null ? session.rankingContext() :
                    persistedContext;
            session.setRankingContext(rankingContext);
            ResourceLocation preferredWorkstation = resolveSyncedProviderWorkstation(
                    previewMenu.data_energistics$getSyncedPatternProviderState(),
                    rankingContext,
                    providerId);
            return new PatternUploadContext(
                    player,
                    mode,
                    rankingContext,
                    preferredWorkstation);
        }

        ResourceLocation workstationId = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode);
        PatternEncodingRankingContext fixedContext = PatternEncodingSourceHelper.resolveFixedModeRankingContext(mode, workstationId);
        if (fixedContext == null) {
            throw new IllegalStateException("Could not derive the fixed upload context for encoding mode " + mode);
        }
        PatternEncodingRankingContext rankingContext = persistedContext == null ? fixedContext : persistedContext;
        session.setRankingContext(rankingContext);
        return new PatternUploadContext(player, mode, rankingContext, null);
    }

    @Nullable
    private static ResourceLocation resolveSyncedProviderWorkstation(
                                                                     PatternEncodingPreviewMenu.SyncedPatternProviderList providerState,
                                                                     @Nullable PatternEncodingRankingContext rankingContext,
                                                                     long providerId) {
        if (!Objects.equals(providerState.rankingContext(), rankingContext)) {
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

        PreparationResult preparation = preparePatternUploads(containers, uploadContext);
        if (preparation.rejected()) {
            return TransferResult.rejected(encodedPattern, preparation.rejectionMessageOrThrow());
        }
        ObjectList<PreparedPatternUpload> preparedUploads = preparation.uploads();
        if (preparedUploads.isEmpty()) {
            return TransferResult.rejected(
                    encodedPattern,
                    Component.translatable("message.data_energistics.pattern_provider.target_unavailable"));
        }

        if (containsEquivalentEncodedPattern(preparedUploads, encodedPattern)) {
            return new TransferResult(
                    encodedPattern,
                    false,
                    true,
                    null,
                    null,
                    null);
        }

        IPatternDetails patternDetails = PatternDetailsHelper.decodePattern(
                encodedPattern,
                uploadContext.player().level());
        if (patternDetails == null) {
            return TransferResult.rejected(
                    encodedPattern,
                    Component.translatable("message.data_energistics.pattern_provider.target_unavailable"));
        }
        EncodedPatternRecipeReference recipeReference = EncodedPatternRecipeReference.get(encodedPattern);
        ResourceLocation recipeTypeId = recipeReference == null ? null : recipeReference.recipeTypeId();

        ItemStack remainder = encodedPattern.copy();
        boolean transferred = false;
        boolean eligibleLeafAttempted = false;
        boolean indeterminateMutation = false;
        PatternUploadTarget firstCommittedTarget = null;
        @Nullable
        Component firstWorkstationRejection = null;
        for (PreparedPatternUpload preparedUpload : preparedUploads) {
            if (remainder.isEmpty()) {
                break;
            }

            PatternProviderUploadWorkstations.Preparation workstationPreparation;
            try {
                workstationPreparation = PatternProviderUploadWorkstations.prepare(
                        uploadContext.player(),
                        preparedUpload.container(),
                        preparedUpload.providerIdentity(),
                        preparedUpload.workstationSource(),
                        patternDetails,
                        recipeTypeId,
                        remainder.getCount());
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not prepare pattern upload workstations for provider {}; skipping this leaf",
                        preparedUpload.container(),
                        exception);
                if (firstWorkstationRejection == null) {
                    firstWorkstationRejection = Component.translatable(
                            "message.data_energistics.pattern_provider.target_unavailable");
                }
                continue;
            }
            if (workstationPreparation.rejected()) {
                if (firstWorkstationRejection == null) {
                    firstWorkstationRejection = workstationPreparation.rejectionMessageOrThrow();
                }
                continue;
            }
            PatternContainer container = preparedUpload.container();
            PatternProviderUploadCommit.Result providerTransfer = PatternProviderUploadCommit.attempt(
                    container,
                    remainder,
                    workstationPreparation.changes());
            if (providerTransfer.failed()) {
                if (firstWorkstationRejection == null) {
                    firstWorkstationRejection = Component.translatable(
                            "message.data_energistics.pattern_provider.target_unavailable");
                }
                continue;
            }
            eligibleLeafAttempted = true;
            if (providerTransfer.indeterminate()) {
                indeterminateMutation = true;
                break;
            }
            if (providerTransfer.committedCount() > 0) {
                transferred = true;
                if (firstCommittedTarget == null) {
                    firstCommittedTarget = preparedUpload.target();
                }
            }
            remainder = providerTransfer.remainder();
        }

        if (indeterminateMutation) {
            return new TransferResult(
                    transferred ? remainder : encodedPattern,
                    transferred,
                    false,
                    firstCommittedTarget,
                    null,
                    Component.translatable("message.data_energistics.pattern_provider.upload_state_unknown"));
        }
        if (!transferred && !eligibleLeafAttempted && firstWorkstationRejection != null) {
            return TransferResult.rejected(encodedPattern, firstWorkstationRejection);
        }
        return new TransferResult(
                transferred ? remainder : encodedPattern,
                transferred,
                false,
                firstCommittedTarget,
                null,
                null);
    }

    private static PreparationResult preparePatternUploads(
                                                           List<PatternContainer> containers,
                                                           PatternUploadContext uploadContext) {
        ObjectList<PreparedPatternUpload> preparedUploads = new ObjectArrayList<>(containers.size());
        for (PatternContainer container : containers) {
            try {
                ProviderResolution provider = resolveProvider(container);
                PatternUploadTarget target = createProviderUploadTarget(
                        container,
                        provider.identity(),
                        uploadContext.resolvedWorkstation());
                preparedUploads.add(new PreparedPatternUpload(
                        container,
                        target,
                        provider.identity(),
                        PatternProviderRuntimeBindings.resolveWorkstationSource(provider.identity())));
            } catch (RuntimeException exception) {
                LOGGER.error("Could not resolve a typed upload target for {}; rejecting the group before inventory mutation",
                        container, exception);
                return PreparationResult.rejected(Component.translatable(
                        "message.data_energistics.pattern_provider.target_unavailable"));
            }
        }
        return PreparationResult.accepted(ObjectLists.unmodifiable(preparedUploads));
    }

    private static boolean containsEquivalentEncodedPattern(ObjectList<PreparedPatternUpload> preparedUploads,
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
                                         PatternUploadTarget target,
                                         ProviderIdentity providerIdentity,
                                         @Nullable PatternProviderWorkstationSource workstationSource) {}

    private record PreparationResult(ObjectList<PreparedPatternUpload> uploads,
                                     @Nullable Component rejectionMessage) {

        private static PreparationResult accepted(ObjectList<PreparedPatternUpload> uploads) {
            return new PreparationResult(uploads, null);
        }

        private static PreparationResult rejected(Component message) {
            return new PreparationResult(ObjectLists.emptyList(), message);
        }

        private boolean rejected() {
            return this.rejectionMessage != null;
        }

        private Component rejectionMessageOrThrow() {
            if (this.rejectionMessage == null) {
                throw new IllegalStateException("Successful pattern upload preparation has no rejection message");
            }
            return this.rejectionMessage;
        }
    }

    /**
     * Result of one upload attempt, including the first inventory that actually accepted a pattern.
     */
    public record TransferResult(ItemStack remainder, boolean transferred, boolean duplicateFound,
                                 @Nullable PatternUploadTarget firstCommittedTarget,
                                 @Nullable Component rejectionMessage,
                                 @Nullable Component warningMessage) {

        public TransferResult {
            if ((transferred && firstCommittedTarget == null) ||
                    (!transferred && firstCommittedTarget != null)) {
                throw new IllegalArgumentException(
                        "A pattern upload result must expose one committed target exactly when a transfer occurred");
            }
            if (transferred && (duplicateFound || rejectionMessage != null)) {
                throw new IllegalArgumentException(
                        "A committed pattern upload cannot also be a duplicate or rejection");
            }
            if (duplicateFound && rejectionMessage != null) {
                throw new IllegalArgumentException("A duplicate pattern upload cannot also be rejected");
            }
            if (warningMessage != null && (duplicateFound || rejectionMessage != null)) {
                throw new IllegalArgumentException("An indeterminate pattern upload cannot be a duplicate or rejection");
            }
        }

        public boolean rejected() {
            return this.rejectionMessage != null;
        }

        /** Returns the user-visible rejection after {@link #rejected()} established the result variant. */
        public Component rejectionMessageOrThrow() {
            if (this.rejectionMessage == null) {
                throw new IllegalStateException("Successful pattern upload result has no rejection message");
            }
            return this.rejectionMessage.copy();
        }

        public boolean hasWarning() {
            return this.warningMessage != null;
        }

        /** Returns the user-visible warning after {@link #hasWarning()} established the result variant. */
        public Component warningMessageOrThrow() {
            if (this.warningMessage == null) {
                throw new IllegalStateException("Pattern upload result has no warning message");
            }
            return this.warningMessage.copy();
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
            return new TransferResult(remainder, false, false, null, null, null);
        }

        private static TransferResult rejected(ItemStack remainder, Component rejectionMessage) {
            return new TransferResult(remainder, false, false, null, rejectionMessage, null);
        }
    }

    /**
     * Server-owned context used to attribute one committed upload after inventory mutation.
     */
    public record PatternUploadContext(ServerPlayer player,
                                       EncodingMode mode,
                                       @Nullable PatternEncodingRankingContext rankingContext,
                                       @Nullable ResourceLocation resolvedWorkstation) {

        public boolean processing() {
            return this.mode == EncodingMode.PROCESSING;
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
                                                      Reference2LongMap<PatternContainer> syncedPatternProviderIds,
                                                      LongSupplier nextIdSupplier,
                                                      List<PatternProviderAggregationEntry> discoveredProviders,
                                                      ReferenceSet<PatternContainer> activeProviders,
                                                      ReferenceSet<PatternContainer> discoveredProviderSet,
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
                                             Reference2LongMap<PatternContainer> syncedPatternProviderIds,
                                             LongSupplier nextIdSupplier,
                                             List<PatternProviderAggregationEntry> discoveredProviders,
                                             ReferenceSet<PatternContainer> activeProviders,
                                             ReferenceSet<PatternContainer> discoveredProviderSet,
                                             @Nullable PatternEncodingRankingContext rankingContext) {
        if (!container.isVisibleInTerminal() || discoveredProviderSet.contains(container)) {
            return;
        }

        var patternInventory = container.getTerminalPatternInventory();
        if (patternInventory.size() <= 0) {
            return;
        }

        discoveredProviderSet.add(container);

        long providerId;
        if (syncedPatternProviderIds.containsKey(container)) {
            providerId = syncedPatternProviderIds.getLong(container);
        } else {
            providerId = nextIdSupplier.getAsLong();
            syncedPatternProviderIds.put(container, providerId);
        }
        Component displayName;
        ResourceLocation iconItemId;
        PatternProviderAggregationKey aggregationKey;
        ProviderIdentity identity;
        String providerDigest;
        boolean openable;
        boolean exactContextMatch;
        List<ResourceLocation> supportedRecipeTypeIds;
        List<ResourceLocation> matchingWorkstationIds;
        try {
            ProviderPresentation presentation = resolveProviderPresentation(container);
            displayName = presentation.displayName();
            iconItemId = presentation.iconItemId();
            ProviderResolution provider = resolveProvider(container);
            identity = provider.identity();
            if (provider.binding() != null) {
                PatternProviderMetadata metadata = provider.binding().registration().metadata();
                aggregationKey = new PatternProviderAggregationKey.Registered(
                        metadata.registrationId(), metadata.providerIdentity());
                exactContextMatch = matchesRecipeType(metadata, rankingContext);
                supportedRecipeTypeIds = metadata.categoryIds();
                matchingWorkstationIds = resolveMatchingWorkstationIds(metadata, rankingContext);
            } else {
                aggregationKey = PatternProviderAggregationKey.NetworkGroup.from(container.getTerminalGroup());
                exactContextMatch = false;
                supportedRecipeTypeIds = List.of();
                matchingWorkstationIds = List.of();
            }
            openable = provider.binding() != null &&
                    provider.binding().registration().menuOpenAdapter() != null ||
                    container instanceof PatternProviderLogicHost ||
                    container instanceof MenuProvider;
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
                identity,
                aggregationKey,
                exactContextMatch,
                true,
                isRenameableProvider(container),
                openable,
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
        List<PatternProviderAggregationEntry> sortedProviders = new ObjectArrayList<>(discoveredProviders);
        sortedProviders.sort(createStableDiscoveredProviderComparator());

        Object2ObjectLinkedOpenHashMap<PatternProviderAggregationKey, AggregatedPatternProvider> aggregatedProvidersByKey = new Object2ObjectLinkedOpenHashMap<>();

        for (var provider : sortedProviders) {
            PatternProviderAggregationKey key = provider.aggregationKey();
            AggregatedPatternProvider aggregated;
            if (aggregatedProvidersByKey.containsKey(key)) {
                aggregated = aggregatedProvidersByKey.get(key);
            } else {
                aggregated = new AggregatedPatternProvider(provider);
                aggregatedProvidersByKey.put(key, aggregated);
            }
            aggregated.include(provider);
        }
        List<AggregatedPatternProvider> aggregated = new ObjectArrayList<>(aggregatedProvidersByKey.values());
        aggregated.forEach(AggregatedPatternProvider::sortLeaves);
        return aggregated;
    }

    private static final Comparator<PatternProviderAggregationEntry> PROVIDER_LEAF_COMPARATOR = Comparator
            .comparingInt((PatternProviderAggregationEntry entry) -> providerLocationRank(entry.identity()))
            .thenComparing(entry -> providerDimension(entry.identity()))
            .thenComparingInt(entry -> providerCoordinate(entry.identity(), 0))
            .thenComparingInt(entry -> providerCoordinate(entry.identity(), 1))
            .thenComparingInt(entry -> providerCoordinate(entry.identity(), 2))
            .thenComparingInt(entry -> providerKindRank(entry.identity()))
            .thenComparingInt(entry -> providerMountRank(entry.identity()))
            .thenComparing(PatternProviderAggregationEntry::providerDigest);

    private static int providerLocationRank(ProviderIdentity identity) {
        return identity instanceof ProviderIdentity.Block || identity instanceof ProviderIdentity.Part ? 0 : 1;
    }

    private static String providerDimension(ProviderIdentity identity) {
        return switch (identity) {
            case ProviderIdentity.Block block -> block.dimensionId().toString();
            case ProviderIdentity.Part part -> part.dimensionId().toString();
            default -> "";
        };
    }

    private static int providerCoordinate(ProviderIdentity identity, int axis) {
        BlockPos position = switch (identity) {
            case ProviderIdentity.Block block -> block.blockPos();
            case ProviderIdentity.Part part -> part.blockPos();
            default -> BlockPos.ZERO;
        };
        return switch (axis) {
            case 0 -> position.getX();
            case 1 -> position.getY();
            case 2 -> position.getZ();
            default -> throw new IllegalArgumentException("Unknown provider coordinate axis: " + axis);
        };
    }

    private static int providerKindRank(ProviderIdentity identity) {
        return identity instanceof ProviderIdentity.Block ? 0 : identity instanceof ProviderIdentity.Part ? 1 : 2;
    }

    private static int providerMountRank(ProviderIdentity identity) {
        if (!(identity instanceof ProviderIdentity.Part part)) {
            return 0;
        }
        return switch (part.mount()) {
            case CENTER -> 0;
            case DOWN -> 1;
            case UP -> 2;
            case NORTH -> 3;
            case SOUTH -> 4;
            case WEST -> 5;
            case EAST -> 6;
        };
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProviderLeaf toSyncedLeaf(
                                                                                     PatternProviderAggregationEntry entry) {
        return new PatternEncodingPreviewMenu.SyncedPatternProviderLeaf(
                entry.id(),
                entry.providerDigest(),
                entry.displayName(),
                entry.iconItemId(),
                entry.identity().kind().name().toLowerCase(Locale.ROOT),
                entry.renameable(),
                entry.openable(),
                entry.patternSlotCount(),
                entry.usedPatternSlotCount(),
                toSyncedLocation(entry.identity()));
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation toSyncedLocation(
                                                                                                 ProviderIdentity identity) {
        return switch (identity) {
            case ProviderIdentity.Block block -> PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation.block(
                    block.dimensionId(), block.blockPos());
            case ProviderIdentity.Part part -> PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation.part(
                    part.dimensionId(), part.blockPos(), part.mount().direction());
            default -> PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation.unlocated();
        };
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
                                                                                                   Object2LongMap<String> leafClickCounts) {
        return Comparator.comparing(AggregatedPatternProvider::exactContextMatch)
                .reversed()
                .thenComparing(Comparator.<AggregatedPatternProvider>comparingLong(
                        provider -> provider.leafCountScore(leafClickCounts))
                        .reversed())
                .thenComparingLong(AggregatedPatternProvider::sortOrder)
                .thenComparing(provider -> provider.displayName().getString())
                .thenComparing(AggregatedPatternProvider::firstLeafDigest);
    }

    private static Comparator<PatternProviderAggregationEntry> createStableDiscoveredProviderComparator() {
        return Comparator.comparingLong(PatternProviderAggregationEntry::sortOrder)
                .thenComparing(provider -> provider.displayName().getString())
                .thenComparing(PatternProviderAggregationEntry::providerDigest);
    }

    static boolean matchesRecipeType(PatternProviderMetadata metadata,
                                     @Nullable PatternEncodingRankingContext rankingContext) {
        return rankingContext != null && metadata.categoryIds().contains(rankingContext.recipeTypeId());
    }

    private static List<ResourceLocation> resolveMatchingWorkstationIds(
                                                                        PatternProviderMetadata metadata,
                                                                        @Nullable PatternEncodingRankingContext rankingContext) {
        if (!matchesRecipeType(metadata, rankingContext)) {
            return List.of();
        }
        return metadata.workstationIds();
    }

    @Nullable
    private static Class<? extends PatternContainer> asPatternContainerClass(Class<?> machineClass) {
        return PatternContainer.class.isAssignableFrom(machineClass) ? machineClass.asSubclass(PatternContainer.class) : null;
    }

    private static Component resolveProviderDisplayName(PatternContainer container,
                                                        @Nullable Component customName,
                                                        @Nullable PatternContainerGroup uncustomizedTerminalGroup) {
        if (customName == null) {
            return container.getTerminalGroup().name();
        }
        Component baseName = uncustomizedTerminalGroup == null ?
                resolveProviderFallbackDisplayName(container) : uncustomizedTerminalGroup.name();
        return AdaptivePatternProviderDisplayHelper.decorateAttachedMachineName(
                baseName,
                customName);
    }

    private static Component resolveProviderFallbackDisplayName(PatternContainer container) {
        ItemStack icon = resolveProviderIcon(container, null);
        if (icon.isEmpty()) {
            throw new IllegalStateException("Pattern provider does not expose a terminal icon: " + container);
        }
        return icon.getItem().getDefaultInstance().getHoverName();
    }

    private static ProviderPresentation resolveProviderPresentation(PatternContainer container) {
        Component customName = PatternProviderNameHelper.getCustomName(container);
        PatternContainerGroup uncustomizedTerminalGroup = customName == null ? null :
                resolveUncustomizedTerminalGroup(container);
        return new ProviderPresentation(
                resolveProviderDisplayName(container, customName, uncustomizedTerminalGroup),
                resolveProviderIconItemId(container, uncustomizedTerminalGroup),
                customName != null);
    }

    private static ResourceLocation resolveProviderIconItemId(
                                                              PatternContainer container,
                                                              @Nullable PatternContainerGroup uncustomizedTerminalGroup) {
        ItemStack icon = resolveProviderIcon(container, uncustomizedTerminalGroup);
        if (icon.isEmpty()) {
            throw new IllegalStateException("Pattern provider does not expose a terminal icon: " + container);
        }
        return BuiltInRegistries.ITEM.getKey(icon.getItem());
    }

    private static ItemStack resolveTerminalGroupIcon(PatternContainer container) {
        return resolvePatternContainerGroupIcon(container.getTerminalGroup());
    }

    private static ItemStack resolveProviderIcon(PatternContainer container,
                                                 @Nullable PatternContainerGroup uncustomizedTerminalGroup) {
        ItemStack uncustomizedTerminalIcon = resolvePatternContainerGroupIcon(uncustomizedTerminalGroup);
        if (!uncustomizedTerminalIcon.isEmpty()) {
            return uncustomizedTerminalIcon;
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
     * Reproduces the terminal group that would be selected before AE2 short-circuits on a custom provider name.
     */
    @Nullable
    private static PatternContainerGroup resolveUncustomizedTerminalGroup(PatternContainer container) {
        if (container instanceof AdaptivePatternProviderHost adaptiveHost) {
            return resolveUncustomizedAdaptiveTerminalGroup(adaptiveHost);
        }
        if (!(container instanceof PatternProviderLogicHost providerHost) ||
                !(providerHost.getLogic() instanceof PatternProviderBatchAccess logic)) {
            return null;
        }

        PatternProviderLogicHost host = logic.dataEnergistics$getHost();
        var hostBlockEntity = host.getBlockEntity();
        Level hostLevel = hostBlockEntity.getLevel();
        if (hostLevel == null) {
            return null;
        }

        Set<Direction> sides = logic.dataEnergistics$invokeGetActiveSides();
        var groups = new ObjectLinkedOpenHashSet<PatternContainerGroup>(sides.size());
        for (var side : sides) {
            var group = PatternContainerGroup.fromMachine(
                    hostLevel,
                    hostBlockEntity.getBlockPos().relative(side),
                    side.getOpposite());
            if (group != null) {
                groups.add(group);
            }
        }

        if (groups.size() == 1) {
            return groups.getFirst();
        }

        List<Component> tooltip = List.of();
        if (groups.size() > 1) {
            tooltip = new ObjectArrayList<>();
            tooltip.add(GuiText.AdjacentToDifferentMachines.text().withStyle(ChatFormatting.BOLD));
            for (var group : groups) {
                tooltip.add(group.name());
                for (var line : group.tooltip()) {
                    tooltip.add(Component.literal("  ").append(line));
                }
            }
        }

        var hostIcon = host.getTerminalIcon();
        return new PatternContainerGroup(hostIcon, hostIcon.getDisplayName(), tooltip);
    }

    private static PatternContainerGroup resolveUncustomizedAdaptiveTerminalGroup(AdaptivePatternProviderHost host) {
        PatternContainerGroup attachedMachineGroup = host.getPrimaryAttachedMachineGroup();
        return new PatternContainerGroup(
                attachedMachineGroup == null ? null : attachedMachineGroup.icon(),
                host.getTerminalDisplayName(),
                attachedMachineGroup == null ? List.of() : attachedMachineGroup.tooltip());
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
                                           ProviderIdentity identity,
                                           PatternProviderAggregationKey aggregationKey,
                                           boolean exactContextMatch,
                                           boolean useAeButtonStyle,
                                           boolean renameable,
                                           boolean openable,
                                           int patternSlotCount,
                                           int usedPatternSlotCount,
                                           String providerDigest,
                                           List<ResourceLocation> supportedRecipeTypeIds,
                                           List<ResourceLocation> matchingWorkstationIds) {

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
        private final List<PatternProviderAggregationEntry> leaves = new ObjectArrayList<>();
        private final ObjectSet<ResourceLocation> supportedRecipeTypeIds = new ObjectLinkedOpenHashSet<>();
        private final ObjectSet<ResourceLocation> matchingWorkstationIds = new ObjectLinkedOpenHashSet<>();

        private AggregatedPatternProvider(PatternProviderAggregationEntry provider) {
            this.id = provider.id();
            this.sortOrder = provider.sortOrder();
            this.displayName = provider.displayName();
            this.iconItemId = provider.iconItemId();
            this.exactContextMatch = provider.exactContextMatch();
            this.useAeButtonStyle = provider.useAeButtonStyle();
            this.renameable = provider.renameable();
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
            this.leaves.add(provider);
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

        private ObjectList<PatternContainer> containers() {
            ObjectArrayList<PatternContainer> containers = new ObjectArrayList<>(this.leaves.size());
            this.leaves.forEach(leaf -> containers.add(leaf.container()));
            return ObjectLists.unmodifiable(containers);
        }

        private String firstLeafDigest() {
            return this.leaves.getFirst().providerDigest();
        }

        private ObjectList<PatternEncodingPreviewMenu.SyncedPatternProviderLeaf> syncedLeaves() {
            ObjectArrayList<PatternEncodingPreviewMenu.SyncedPatternProviderLeaf> synced = new ObjectArrayList<>(this.leaves.size());
            this.leaves.forEach(leaf -> synced.add(toSyncedLeaf(leaf)));
            return ObjectLists.unmodifiable(synced);
        }

        private void sortLeaves() {
            this.leaves.sort(PROVIDER_LEAF_COMPARATOR);
        }

        private ObjectList<ResourceLocation> supportedRecipeTypeIds() {
            ObjectArrayList<ResourceLocation> supported = new ObjectArrayList<>(this.supportedRecipeTypeIds);
            supported.sort(Comparator.comparing(ResourceLocation::toString));
            return ObjectLists.unmodifiable(supported);
        }

        @Nullable
        private ResourceLocation preferredWorkstationId() {
            return this.matchingWorkstationIds.stream()
                    .min(Comparator.comparing(ResourceLocation::toString))
                    .orElse(null);
        }

        private long leafCountScore(Object2LongMap<String> leafClickCounts) {
            long score = 0L;
            for (PatternProviderAggregationEntry leaf : this.leaves) {
                long count = leafClickCounts.getLong(leaf.providerDigest());
                if (Long.MAX_VALUE - score < count) {
                    return Long.MAX_VALUE;
                }
                score += count;
            }
            return score;
        }
    }
}
