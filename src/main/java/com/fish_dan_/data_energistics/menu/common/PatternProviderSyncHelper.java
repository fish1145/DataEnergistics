package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.integration.SomeUselessThingsCompat;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.util.PatternProviderNameHelper;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatternProviderSyncHelper {

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+");
    private static final Pattern NEOECOAE_TIER_TOKEN = Pattern.compile("([fl]\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String EXTENDEDAE_ASSEMBLER_MATRIX_NAME_KEY = "gui.extendedae.assembler_matrix";
    private static final String EXTENDEDAE_PLUS_NAMESPACE = "extendedae_plus";
    private static final String NEOECOAE_NAMESPACE = "neoecoae";
    private static final String APPLIED_PNEUMATICS_NAMESPACE = "appliedpneumatics";
    private static final String NEOECOAE_CRAFTING_SYSTEM_PATH = "eco_crafting_system";
    private static final String NEOECOAE_CRAFTING_SYSTEM_PREFIX = "crafting_system_";
    private static final String NEOECOAE_CRAFTING_WORKER_PATH = "crafting_worker";
    private static final String NEOECOAE_CRAFTING_PATTERN_BUS_PATH = "crafting_pattern_bus";
    private static final String NEOECOAE_ECO_CRAFTING_WORKER_PATH = "eco_crafting_worker";
    private static final String APPLIED_PNEUMATICS_AMADRON_PROCESS_STATION_PATH = "me_amadron_process_station";
    private static final String APPLIED_PNEUMATICS_AMADRON_EXTENDED_PROCESS_STATION_PATH = "me_amadron_extended_process_station";
    private static final ResourceLocation CRAFTING_TABLE_ID = ResourceLocation.withDefaultNamespace("crafting_table");
    private static final ResourceLocation FURNACE_ID = ResourceLocation.withDefaultNamespace("furnace");
    private static final ResourceLocation BLAST_FURNACE_ID = ResourceLocation.withDefaultNamespace("blast_furnace");
    private static final ResourceLocation SMOKER_ID = ResourceLocation.withDefaultNamespace("smoker");
    private static final ResourceLocation CAMPFIRE_ID = ResourceLocation.withDefaultNamespace("campfire");
    private static final ResourceLocation STONECUTTER_ID = ResourceLocation.withDefaultNamespace("stonecutter");
    private static final ResourceLocation SMITHING_TABLE_ID = ResourceLocation.withDefaultNamespace("smithing_table");
    private static final ResourceLocation AE2_CRAFTING_PATTERN_ITEM_ID = ResourceLocation.fromNamespaceAndPath("ae2", "crafting_pattern");
    private static final ResourceLocation AE2_STONECUTTING_PATTERN_ITEM_ID = ResourceLocation.fromNamespaceAndPath("ae2", "stonecutting_pattern");
    private static final ResourceLocation AE2_SMITHING_TABLE_PATTERN_ITEM_ID = ResourceLocation.fromNamespaceAndPath("ae2", "smithing_table_pattern");
    private static final ResourceLocation AE2_MOLECULAR_ASSEMBLER_ID = ResourceLocation.fromNamespaceAndPath("ae2", "molecular_assembler");
    private static final ResourceLocation AE2_INSCRIBER_ID = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber");
    private static final ResourceLocation AE2_CHARGER_ID = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final ResourceLocation DATA_RIPPER_REASSEMBLER_ID = Data_Energistics.id("data_reassembler");
    private static final ResourceLocation EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID = ResourceLocation.fromNamespaceAndPath("extendedae", "assembler_matrix_speed");
    private static final ResourceLocation EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID = ResourceLocation.fromNamespaceAndPath(EXTENDEDAE_PLUS_NAMESPACE, "assembler_matrix_speed_plus");
    private static final ResourceLocation EXTENDEDAE_CRYSTAL_ASSEMBLER_ID = ResourceLocation.fromNamespaceAndPath("extendedae", "crystal_assembler");
    private static final ResourceLocation EXTENDEDAE_EX_MOLECULAR_ASSEMBLER_ID = ResourceLocation.fromNamespaceAndPath("extendedae", "ex_molecular_assembler");
    private static final ResourceLocation AE2CS_RESONATING_PATTERN_PROVIDER_ID = ResourceLocation.fromNamespaceAndPath("ae2cs", "resonating_pattern_provider");
    private static final ResourceLocation AE2CS_EXTENDED_RESONATING_PATTERN_PROVIDER_ID = ResourceLocation.fromNamespaceAndPath("ae2cs", "extended_resonating_pattern_provider");
    private static final ResourceLocation AE2CS_EX_RESONATING_PATTERN_PROVIDER_ID = ResourceLocation.fromNamespaceAndPath("ae2cs", "ex_resonating_pattern_provider");
    private static final ResourceLocation AE2CS_METEORITE_PATTERN_PROVIDER_ID = ResourceLocation.fromNamespaceAndPath("ae2cs", "meteorite_pattern_provider");
    private static final Set<String> EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_PATHS = Set.of(
            "assembler_matrix_crafter_plus",
            "assembler_matrix_pattern_plus",
            "assembler_matrix_speed_plus");

    private PatternProviderSyncHelper() {}

    public static PatternEncodingPreviewMenu.SyncedPatternProviderList collectSyncedPatternProviders(
                                                                                                     @Nullable IGrid grid,
                                                                                                     Map<PatternContainer, Long> syncedPatternProviderIds,
                                                                                                     Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                                                                     LongSupplier nextIdSupplier,
                                                                                                     @Nullable ResourceLocation preferredWorkstationId,
                                                                                                     @Nullable EncodingMode encodingMode,
                                                                                                     ItemStack currentEncodedPattern) {
        syncedProviderTargetsById.clear();
        if (grid == null) {
            syncedPatternProviderIds.clear();
            return PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY;
        }
        boolean primaryWorkbenchOrdering = shouldUsePrimaryWorkbenchPriorityLine(encodingMode, currentEncodedPattern);
        ResourceLocation effectivePreferredWorkstationId = normalizePreferredWorkstationId(
                preferredWorkstationId, encodingMode, currentEncodedPattern, primaryWorkbenchOrdering);
        List<DiscoveredPatternProvider> discoveredProviders = new ArrayList<>();
        Map<PatternContainer, Boolean> activeProviders = new IdentityHashMap<>();
        Map<PatternContainer, Boolean> discoveredProviderSet = new IdentityHashMap<>();

        collectDirectPatternProviders(grid, syncedPatternProviderIds, nextIdSupplier, discoveredProviders, activeProviders,
                discoveredProviderSet, effectivePreferredWorkstationId, encodingMode, currentEncodedPattern,
                primaryWorkbenchOrdering);

        for (var machineClass : grid.getMachineClasses()) {
            var patternContainerClass = asPatternContainerClass(machineClass);
            if (patternContainerClass == null) {
                continue;
            }

            for (var container : grid.getMachines(patternContainerClass)) {
                addProviderIfVisible(container, syncedPatternProviderIds, nextIdSupplier, discoveredProviders, activeProviders,
                        discoveredProviderSet, effectivePreferredWorkstationId, encodingMode, currentEncodedPattern,
                        primaryWorkbenchOrdering);
            }
        }

        syncedPatternProviderIds.keySet().removeIf(provider -> !activeProviders.containsKey(provider));

        List<AggregatedPatternProvider> aggregatedProviders = aggregateDiscoveredProviders(discoveredProviders, primaryWorkbenchOrdering);
        aggregatedProviders.sort(createAggregatedProviderComparator(primaryWorkbenchOrdering));

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
                    provider.usedPatternSlotCount()));
        }

        return providers.isEmpty() ? PatternEncodingPreviewMenu.SyncedPatternProviderList.EMPTY : new PatternEncodingPreviewMenu.SyncedPatternProviderList(providers);
    }

    @Nullable
    private static ResourceLocation normalizePreferredWorkstationId(@Nullable ResourceLocation preferredWorkstationId,
                                                                    @Nullable EncodingMode encodingMode,
                                                                    ItemStack currentEncodedPattern,
                                                                    boolean primaryWorkbenchOrdering) {
        if (!primaryWorkbenchOrdering) {
            return preferredWorkstationId;
        }

        ResourceLocation patternItemId = resolveItemId(currentEncodedPattern);
        if (AE2_CRAFTING_PATTERN_ITEM_ID.equals(patternItemId)) {
            return CRAFTING_TABLE_ID;
        }
        if (AE2_STONECUTTING_PATTERN_ITEM_ID.equals(patternItemId)) {
            return STONECUTTER_ID;
        }
        if (AE2_SMITHING_TABLE_PATTERN_ITEM_ID.equals(patternItemId)) {
            return SMITHING_TABLE_ID;
        }

        if (preferredWorkstationId != null && isPrimaryWorkbenchFamilyMode(encodingMode, preferredWorkstationId)) {
            return preferredWorkstationId;
        }

        return PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(encodingMode);
    }

    @Nullable
    public static PatternContainer findProviderById(Map<PatternContainer, Long> syncedPatternProviderIds, long providerId) {
        for (var entry : syncedPatternProviderIds.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == providerId) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    public static List<PatternContainer> findProvidersById(Map<Long, List<PatternContainer>> syncedProviderTargetsById,
                                                           long providerId) {
        return syncedProviderTargetsById.get(providerId);
    }

    public static ItemStack transferEncodedPatternToProvider(PatternContainer container, ItemStack encodedPattern) {
        if (container == null || encodedPattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            return encodedPattern;
        }

        var patternInventory = container.getTerminalPatternInventory();
        if (patternInventory.size() <= 0) {
            return encodedPattern;
        }

        ItemStack remainder = patternInventory.addItems(encodedPattern.copy(), false);
        if (remainder.getCount() == encodedPattern.getCount()) {
            return encodedPattern;
        }

        if (container instanceof PatternProviderLogicHost providerHost) {
            providerHost.getLogic().updatePatterns();
            providerHost.saveChanges();
        }
        SomeUselessThingsCompat.afterPatternUpload(container);

        return remainder;
    }

    public static TransferResult transferEncodedPatternToProvidersChecked(List<PatternContainer> containers, ItemStack encodedPattern) {
        if (containers == null || containers.isEmpty() || encodedPattern.isEmpty()) {
            return new TransferResult(encodedPattern, false, false);
        }

        if (containsEquivalentEncodedPattern(containers, encodedPattern)) {
            return new TransferResult(encodedPattern, false, true);
        }

        ItemStack remainder = encodedPattern.copy();
        boolean transferred = false;
        for (var container : containers) {
            if (remainder.isEmpty()) {
                break;
            }

            ItemStack nextRemainder = transferEncodedPatternToProvider(container, remainder);
            if (nextRemainder.getCount() != remainder.getCount()) {
                transferred = true;
            }
            remainder = nextRemainder;
        }

        return new TransferResult(transferred ? remainder : encodedPattern, transferred, false);
    }

    public static ItemStack transferEncodedPatternToProviders(List<PatternContainer> containers, ItemStack encodedPattern) {
        if (containers == null || containers.isEmpty() || encodedPattern.isEmpty()) {
            return encodedPattern;
        }

        ItemStack remainder = encodedPattern.copy();
        boolean transferred = false;
        for (var container : containers) {
            if (remainder.isEmpty()) {
                break;
            }

            ItemStack nextRemainder = transferEncodedPatternToProvider(container, remainder);
            if (nextRemainder.getCount() != remainder.getCount()) {
                transferred = true;
            }
            remainder = nextRemainder;
        }

        return transferred ? remainder : encodedPattern;
    }

    private static boolean containsEquivalentEncodedPattern(List<PatternContainer> containers, ItemStack encodedPattern) {
        for (var container : containers) {
            if (container == null) {
                continue;
            }

            var inventory = container.getTerminalPatternInventory();
            if (inventory == null) {
                continue;
            }

            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack existing = inventory.getStackInSlot(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, encodedPattern)) {
                    return true;
                }
            }
        }

        return false;
    }

    public record TransferResult(ItemStack remainder, boolean transferred, boolean duplicateFound) {}

    private static void collectDirectPatternProviders(
                                                      IGrid grid,
                                                      Map<PatternContainer, Long> syncedPatternProviderIds,
                                                      LongSupplier nextIdSupplier,
                                                      List<DiscoveredPatternProvider> discoveredProviders,
                                                      Map<PatternContainer, Boolean> activeProviders,
                                                      Map<PatternContainer, Boolean> discoveredProviderSet,
                                                      @Nullable ResourceLocation preferredWorkstationId,
                                                      @Nullable EncodingMode encodingMode,
                                                      ItemStack currentEncodedPattern,
                                                      boolean primaryWorkbenchOrdering) {
        try {
            for (var providerHost : grid.getMachines(PatternProviderLogicHost.class)) {
                addProviderIfVisible(providerHost, syncedPatternProviderIds, nextIdSupplier, discoveredProviders,
                        activeProviders, discoveredProviderSet, preferredWorkstationId, encodingMode, currentEncodedPattern,
                        primaryWorkbenchOrdering);
            }
        } catch (Exception ignored) {}
    }

    private static void addProviderIfVisible(
                                             PatternContainer container,
                                             Map<PatternContainer, Long> syncedPatternProviderIds,
                                             LongSupplier nextIdSupplier,
                                             List<DiscoveredPatternProvider> discoveredProviders,
                                             Map<PatternContainer, Boolean> activeProviders,
                                             Map<PatternContainer, Boolean> discoveredProviderSet,
                                             @Nullable ResourceLocation preferredWorkstationId,
                                             @Nullable EncodingMode encodingMode,
                                             ItemStack currentEncodedPattern,
                                             boolean primaryWorkbenchOrdering) {
        if (!isProviderContainer(container) || discoveredProviderSet.containsKey(container)) {
            return;
        }

        var patternInventory = container.getTerminalPatternInventory();
        if (patternInventory.size() <= 0) {
            return;
        }

        discoveredProviderSet.put(container, Boolean.TRUE);

        long providerId = syncedPatternProviderIds.computeIfAbsent(container,
                ignored -> nextIdSupplier.getAsLong());
        activeProviders.put(container, Boolean.TRUE);
        Component displayName = resolveProviderDisplayName(container);
        ResourceLocation iconItemId = resolveProviderIconItemId(container);
        boolean renameable = isRenameableProvider(container, displayName, iconItemId);
        int usedPatternSlots = countUsedPatternSlots(patternInventory);
        discoveredProviders.add(new DiscoveredPatternProvider(
                container,
                providerId,
                container.getTerminalSortOrder(),
                displayName,
                iconItemId,
                shouldUseAeButtonStyle(container),
                renameable,
                patternInventory.size(),
                usedPatternSlots,
                getWorkbenchLinePriority(container, displayName, iconItemId, primaryWorkbenchOrdering),
                getRecordedDeviceScore(container, displayName, iconItemId, preferredWorkstationId,
                        primaryWorkbenchOrdering),
                getPreferredProviderScore(container, displayName, iconItemId, patternInventory.size(), usedPatternSlots,
                        preferredWorkstationId, encodingMode, currentEncodedPattern, primaryWorkbenchOrdering)));
    }

    private static boolean isProviderContainer(PatternContainer container) {
        if (container instanceof PatternProviderLogicHost) {
            return true;
        }

        String className = container.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (className.contains("provider") || className.contains("pattern") || className.contains("crafting")) {
            return true;
        }

        ResourceLocation terminalIconItemId = resolveTerminalIconItemId(container);
        return isNeoEcoCraftingSubsystemIcon(terminalIconItemId) || !resolveTerminalGroupIcon(container).isEmpty() || !resolveMainMenuIconReflectively(container).isEmpty();
    }

    private static boolean shouldUseAeButtonStyle(PatternContainer container) {
        return isProviderContainer(container);
    }

    public static boolean isRenameableProvider(PatternContainer container) {
        if (container == null || isAssemblerMatrixPatternContainer(container) || !isProviderContainer(container)) {
            return false;
        }

        Component displayName = resolveProviderDisplayName(container);
        ResourceLocation iconItemId = resolveProviderIconItemId(container);
        return isRenameableProvider(container, displayName, iconItemId);
    }

    private static boolean isRenameableProvider(PatternContainer container, Component displayName,
                                                ResourceLocation iconItemId) {
        if (isNeoEcoCraftingSubsystemIdentity(container, displayName, iconItemId)) {
            return false;
        }

        return PatternProviderNameHelper.canRename(container) || PatternProviderNameHelper.getCustomName(container) != null;
    }

    private static boolean isAssemblerMatrixPatternContainer(PatternContainer container) {
        if (container.getClass().getSimpleName().equals("TileAssemblerMatrixPattern")) {
            return true;
        }

        ResourceLocation terminalIconItemId = resolveTerminalIconItemId(container);
        if (terminalIconItemId != null && isAssemblerMatrixPlusIcon(terminalIconItemId)) {
            return true;
        }

        ResourceLocation providerIconItemId = resolveBaseProviderIconItemId(container);
        return isAssemblerMatrixPlusIcon(providerIconItemId);
    }

    private static boolean isAssemblerMatrixPlusIcon(@Nullable ResourceLocation iconItemId) {
        return iconItemId != null && EXTENDEDAE_PLUS_NAMESPACE.equals(iconItemId.getNamespace()) && EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_PATHS.contains(iconItemId.getPath());
    }

    private static boolean isNeoEcoCraftingSubsystemContainer(DiscoveredPatternProvider provider) {
        return isNeoEcoCraftingSubsystemIcon(provider.iconItemId()) || isNeoEcoCraftingSubsystemClassName(provider.container()) || isNeoEcoCraftingSubsystemName(provider.displayName().getString());
    }

    private static boolean isNeoEcoCraftingSubsystemContainer(PatternContainer container) {
        return isNeoEcoCraftingSubsystemIcon(resolveTerminalIconItemId(container)) || isNeoEcoCraftingSubsystemIcon(resolveBaseProviderIconItemId(container)) || isNeoEcoCraftingSubsystemClassName(container) || isNeoEcoCraftingSubsystemName(resolveProviderDisplayName(container).getString());
    }

    private static List<AggregatedPatternProvider> aggregateDiscoveredProviders(
                                                                                List<DiscoveredPatternProvider> discoveredProviders,
                                                                                boolean primaryWorkbenchOrdering) {
        List<DiscoveredPatternProvider> sortedProviders = new ArrayList<>(discoveredProviders);
        sortedProviders.sort(createDiscoveredProviderComparator(primaryWorkbenchOrdering));

        List<AggregatedPatternProvider> aggregatedProviders = new ArrayList<>();
        Map<String, AggregatedPatternProvider> aggregatedSpecialProviders = new HashMap<>();

        for (var provider : sortedProviders) {
            if (shouldAggregateSpecialProvider(provider)) {
                String key = getAggregationKey(provider);
                var aggregated = aggregatedSpecialProviders.get(key);
                if (aggregated == null) {
                    aggregated = new AggregatedPatternProvider(provider);
                    aggregatedSpecialProviders.put(key, aggregated);
                }
                aggregated.include(provider);
            } else {
                var aggregated = new AggregatedPatternProvider(provider);
                aggregated.include(provider);
                aggregatedProviders.add(aggregated);
            }
        }

        aggregatedProviders.addAll(aggregatedSpecialProviders.values());
        return aggregatedProviders;
    }

    private static Comparator<AggregatedPatternProvider> createAggregatedProviderComparator(
                                                                                            boolean primaryWorkbenchOrdering) {
        Comparator<AggregatedPatternProvider> comparator;
        if (primaryWorkbenchOrdering) {
            comparator = Comparator.<AggregatedPatternProvider>comparingInt(AggregatedPatternProvider::recordedDeviceScore)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(AggregatedPatternProvider::preferredScore).reversed())
                    .thenComparing(Comparator.comparingInt(AggregatedPatternProvider::workbenchLinePriority).reversed());
        } else {
            comparator = Comparator.<AggregatedPatternProvider>comparingInt(AggregatedPatternProvider::workbenchLinePriority)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(AggregatedPatternProvider::recordedDeviceScore).reversed())
                    .thenComparing(Comparator.comparingInt(AggregatedPatternProvider::preferredScore).reversed());
        }

        return comparator.thenComparingLong(AggregatedPatternProvider::sortOrder)
                .thenComparing(provider -> provider.displayName().getString());
    }

    private static Comparator<DiscoveredPatternProvider> createDiscoveredProviderComparator(
                                                                                            boolean primaryWorkbenchOrdering) {
        Comparator<DiscoveredPatternProvider> comparator;
        if (primaryWorkbenchOrdering) {
            comparator = Comparator.<DiscoveredPatternProvider>comparingInt(DiscoveredPatternProvider::recordedDeviceScore)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(DiscoveredPatternProvider::preferredScore).reversed())
                    .thenComparing(Comparator.comparingInt(DiscoveredPatternProvider::workbenchLinePriority).reversed());
        } else {
            comparator = Comparator.<DiscoveredPatternProvider>comparingInt(DiscoveredPatternProvider::workbenchLinePriority)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(DiscoveredPatternProvider::recordedDeviceScore).reversed())
                    .thenComparing(Comparator.comparingInt(DiscoveredPatternProvider::preferredScore).reversed());
        }

        return comparator.thenComparingLong(DiscoveredPatternProvider::sortOrder)
                .thenComparing(provider -> provider.displayName().getString());
    }

    private static boolean shouldAggregateSpecialProvider(DiscoveredPatternProvider provider) {
        return isAssemblerMatrixPatternContainer(provider.container()) || isNeoEcoCraftingSubsystemContainer(provider);
    }

    private static String getAggregationKey(DiscoveredPatternProvider provider) {
        if (isAssemblerMatrixPatternContainer(provider.container())) {
            return "extendedae:assembler_matrix";
        }
        if (isNeoEcoCraftingSubsystemContainer(provider)) {
            String tierKey = resolveNeoEcoCraftingSubsystemTierKey(provider);
            return tierKey == null ? "neoecoae:crafting_system" : "neoecoae:crafting_system:" + tierKey;
        }
        return provider.iconItemId() + "|" + provider.displayName().getString();
    }

    @Nullable
    private static Class<? extends PatternContainer> asPatternContainerClass(Class<?> machineClass) {
        return PatternContainer.class.isAssignableFrom(machineClass) ? machineClass.asSubclass(PatternContainer.class) : null;
    }

    private static Component resolveProviderDisplayName(PatternContainer container) {
        if (isAssemblerMatrixPatternContainer(container)) {
            return Component.translatable(EXTENDEDAE_ASSEMBLER_MATRIX_NAME_KEY);
        }

        ItemStack ae2CsResolvedIcon = resolveAe2CsResolvedProviderIcon(container);
        if (!ae2CsResolvedIcon.isEmpty()) {
            return ae2CsResolvedIcon.getHoverName();
        }

        ItemStack appliedPneumaticsIcon = resolveAppliedPneumaticsMainMenuIcon(container);
        if (!appliedPneumaticsIcon.isEmpty()) {
            return appliedPneumaticsIcon.getHoverName();
        }

        if (container instanceof AdaptivePatternProviderHost adaptiveHost) {
            var attachedGroup = adaptiveHost.getPrimaryAttachedMachineGroup();
            if (attachedGroup != null && attachedGroup.name() != null) {
                return attachedGroup.name();
            }
            var terminalGroup = container.getTerminalGroup();
            if (terminalGroup != null && terminalGroup.name() != null) {
                return terminalGroup.name();
            }
            return adaptiveHost.getTerminalDisplayName();
        }

        var terminalGroup = container.getTerminalGroup();
        if (terminalGroup != null && terminalGroup.name() != null) {
            return terminalGroup.name();
        }

        ItemStack icon = resolveProviderIcon(container);
        if (!icon.isEmpty()) {
            return icon.getHoverName();
        }

        return Component.literal(container.getClass().getSimpleName());
    }

    private static ResourceLocation resolveProviderIconItemId(PatternContainer container) {
        ItemStack icon = resolveProviderIcon(container);
        ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(icon.getItem());
        if (iconItemId != null) {
            return iconItemId;
        }
        return BuiltInRegistries.ITEM.getKey(Items.AIR);
    }

    private static ResourceLocation resolveBaseProviderIconItemId(PatternContainer container) {
        ItemStack icon = resolveBaseProviderIcon(container);
        ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(icon.getItem());
        if (iconItemId != null) {
            return iconItemId;
        }
        return BuiltInRegistries.ITEM.getKey(Items.AIR);
    }

    @Nullable
    private static ResourceLocation resolveTerminalIconItemId(PatternContainer container) {
        ItemStack icon = resolveTerminalGroupIcon(container);
        if (icon.isEmpty()) {
            return null;
        }

        ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(icon.getItem());
        if (iconItemId == null || Items.AIR.equals(icon.getItem())) {
            return null;
        }

        return iconItemId;
    }

    private static ItemStack resolveTerminalGroupIcon(PatternContainer container) {
        var terminalGroup = container.getTerminalGroup();
        if (terminalGroup != null && terminalGroup.icon() != null) {
            ItemStack groupIcon = terminalGroup.icon().toStack();
            if (!groupIcon.isEmpty()) {
                return groupIcon;
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack resolveProviderIcon(PatternContainer container) {
        if (isAssemblerMatrixPatternContainer(container)) {
            var speedBlock = BuiltInRegistries.BLOCK.getOptional(EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID).orElse(null);
            if (speedBlock == null) {
                speedBlock = BuiltInRegistries.BLOCK.getOptional(EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID).orElse(null);
            }
            if (speedBlock != null) {
                ItemStack speedCore = speedBlock.asItem().getDefaultInstance();
                if (!speedCore.isEmpty()) {
                    return speedCore;
                }
            }
        }

        return resolveBaseProviderIcon(container);
    }

    private static ItemStack resolveBaseProviderIcon(PatternContainer container) {
        ItemStack ae2CsResolvedIcon = resolveAe2CsResolvedProviderIcon(container);
        if (!ae2CsResolvedIcon.isEmpty()) {
            return ae2CsResolvedIcon;
        }

        ItemStack appliedPneumaticsIcon = resolveAppliedPneumaticsMainMenuIcon(container);
        if (!appliedPneumaticsIcon.isEmpty()) {
            return appliedPneumaticsIcon;
        }

        if (container instanceof AdaptivePatternProviderHost adaptiveHost) {
            var attachedGroup = adaptiveHost.getPrimaryAttachedMachineGroup();
            if (attachedGroup != null && attachedGroup.icon() != null) {
                ItemStack attachedIcon = attachedGroup.icon().toStack();
                if (!attachedIcon.isEmpty()) {
                    return attachedIcon;
                }
            }
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

        ItemStack reflectedIcon = resolveMainMenuIconReflectively(container);
        if (!reflectedIcon.isEmpty()) {
            return reflectedIcon;
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

    private static ItemStack resolveMainMenuIconReflectively(Object source) {
        Object result = invokeNoArgReflectively(source, "getMainMenuIcon");
        if (result instanceof ItemStack stack && !stack.isEmpty()) {
            return stack.copy();
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack resolveAe2CsResolvedProviderIcon(PatternContainer container) {
        if (!isAe2CsResonatingProviderContainer(container)) {
            return ItemStack.EMPTY;
        }

        ResourceLocation providerId = getAe2CsResonatingProviderItemId(container);
        return providerId == null ? ItemStack.EMPTY : createRegistryItemStack(providerId);
    }

    @Nullable
    private static ResourceLocation getAe2CsResonatingProviderItemId(PatternContainer container) {
        if (!isAe2CsResonatingProviderContainer(container)) {
            return null;
        }

        int slotCount = getProviderPatternSlotCapacity(container);
        return slotCount > 9 ? AE2CS_EXTENDED_RESONATING_PATTERN_PROVIDER_ID : AE2CS_RESONATING_PATTERN_PROVIDER_ID;
    }

    private static boolean isAe2CsResonatingProviderContainer(PatternContainer container) {
        String className = container.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("io.github.lounode.ae2cs") && className.contains("resonatingpatternprovider");
    }

    private static int getProviderPatternSlotCapacity(PatternContainer container) {
        try {
            var inventory = container.getTerminalPatternInventory();
            return inventory == null ? 0 : inventory.size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static ItemStack createRegistryItemStack(ResourceLocation itemId) {
        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item != null && item != Items.AIR) {
            return item.getDefaultInstance();
        }

        var block = BuiltInRegistries.BLOCK.getOptional(itemId).orElse(null);
        return block == null ? ItemStack.EMPTY : block.asItem().getDefaultInstance();
    }

    private static ItemStack resolveAppliedPneumaticsMainMenuIcon(PatternContainer container) {
        ItemStack reflectedIcon = resolveMainMenuIconReflectively(container);
        if (reflectedIcon.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(reflectedIcon.getItem());
        if (iconItemId == null || !APPLIED_PNEUMATICS_NAMESPACE.equals(iconItemId.getNamespace())) {
            return ItemStack.EMPTY;
        }

        String path = iconItemId.getPath();
        if (!APPLIED_PNEUMATICS_AMADRON_PROCESS_STATION_PATH.equals(path) && !APPLIED_PNEUMATICS_AMADRON_EXTENDED_PROCESS_STATION_PATH.equals(path)) {
            return ItemStack.EMPTY;
        }

        return reflectedIcon;
    }

    private static int countUsedPatternSlots(appeng.api.inventories.InternalInventory inventory) {
        int usedSlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                usedSlots++;
            }
        }
        return usedSlots;
    }

    private static int getPreferredProviderScore(PatternContainer container, Component displayName, ResourceLocation iconItemId,
                                                 int patternSlotCount, int usedPatternSlotCount,
                                                 @Nullable ResourceLocation preferredWorkstationId,
                                                 @Nullable EncodingMode encodingMode,
                                                 ItemStack currentEncodedPattern,
                                                 boolean primaryWorkbenchEncoding) {
        if (patternSlotCount <= 0 || usedPatternSlotCount >= patternSlotCount) {
            return 0;
        }

        String providerName = normalizeForMatch(displayName.getString());
        String providerIconName = normalizeForMatch(iconItemId.toString());
        String workstationName = "";
        String workstationId = "";
        int fuzzyNameScore = 0;
        int fuzzyIconScore = 0;

        if (preferredWorkstationId != null) {
            Component workstationDisplayName = resolveWorkstationDisplayName(preferredWorkstationId);
            workstationName = normalizeForMatch(workstationDisplayName.getString());
            workstationId = normalizeForMatch(preferredWorkstationId.toString());
            fuzzyNameScore = computeFuzzyNamePriority(providerName, workstationName);
            fuzzyIconScore = computeFuzzyNamePriority(providerIconName, workstationId) / 3;
        }

        if (primaryWorkbenchEncoding || isPrimaryWorkbenchFamilyMode(encodingMode, preferredWorkstationId)) {
            int workbenchFamilyPriority = getWorkbenchFamilyPriority(container, displayName, iconItemId);
            if (workbenchFamilyPriority > 0) {
                return workbenchFamilyPriority + Math.min(12, fuzzyNameScore + fuzzyIconScore);
            }
        }

        if (preferredWorkstationId == null) {
            return 0;
        }

        int exactProviderPriority = getExactProviderMatchPriority(container, displayName, iconItemId, preferredWorkstationId);
        if (exactProviderPriority > 0) {
            return exactProviderPriority + Math.min(10, fuzzyNameScore + fuzzyIconScore);
        }

        if (providerName.isEmpty() && providerIconName.isEmpty()) {
            return 0;
        }

        int fallbackScore = 0;
        fallbackScore = Math.max(fallbackScore, fuzzyNameScore);
        fallbackScore = Math.max(fallbackScore, computeFuzzyNamePriority(providerIconName, workstationName) / 3);

        if (fallbackScore <= 0) {
            fallbackScore = Math.max(fallbackScore, computeRegistryIdFallbackPriority(providerName, workstationId));
            fallbackScore = Math.max(fallbackScore, computeRegistryIdFallbackPriority(providerIconName, workstationId) / 2);
        }

        if (containsSimilarText(providerName, workstationName) || containsSimilarText(providerIconName, workstationName)) {
            fallbackScore += 8;
        }

        if (sharesKeyword(providerName, workstationName) || sharesKeyword(providerIconName, workstationName)) {
            fallbackScore += 5;
        }

        return fallbackScore;
    }

    private static int getRecordedDeviceScore(PatternContainer container, Component displayName,
                                              ResourceLocation iconItemId,
                                              @Nullable ResourceLocation preferredWorkstationId,
                                              boolean primaryWorkbenchOrdering) {
        if (preferredWorkstationId == null || primaryWorkbenchOrdering) {
            return 0;
        }

        List<String> workstationTexts = List.of(
                resolveWorkstationDisplayName(preferredWorkstationId).getString(),
                preferredWorkstationId.toString(),
                preferredWorkstationId.getPath());
        int score = 0;
        for (String providerText : collectProviderIdentityStrings(container, displayName, iconItemId)) {
            String normalizedProviderText = normalizeForMatch(providerText);
            if (normalizedProviderText.isEmpty()) {
                continue;
            }
            for (String workstationText : workstationTexts) {
                score = Math.max(score, computeRecordedDeviceTextScore(
                        normalizedProviderText,
                        normalizeForMatch(workstationText)));
            }
        }
        return score;
    }

    private static int getWorkbenchLinePriority(PatternContainer container, Component displayName,
                                                ResourceLocation iconItemId,
                                                boolean primaryWorkbenchEncoding) {
        if (!primaryWorkbenchEncoding || !isEligiblePrimaryWorkbenchProvider(container, displayName, iconItemId)) {
            return 0;
        }
        return getWorkbenchFamilyPriority(container, displayName, iconItemId);
    }

    private static boolean isEligiblePrimaryWorkbenchProvider(PatternContainer container, Component displayName,
                                                              ResourceLocation iconItemId) {
        if (!(container instanceof AdaptivePatternProviderHost)) {
            return true;
        }

        return matchesActualPrimaryWorkbench(displayName, iconItemId, CRAFTING_TABLE_ID) || matchesActualPrimaryWorkbench(displayName, iconItemId, STONECUTTER_ID) || matchesActualPrimaryWorkbench(displayName, iconItemId, SMITHING_TABLE_ID);
    }

    private static boolean matchesActualPrimaryWorkbench(Component displayName, ResourceLocation iconItemId,
                                                         ResourceLocation workstationId) {
        if (workstationId.equals(iconItemId)) {
            return true;
        }

        String providerName = normalizeForMatch(displayName.getString());
        String workstationName = normalizeForMatch(resolveWorkstationDisplayName(workstationId).getString());
        String workstationKey = normalizeForMatch(workstationId.toString());

        return containsSimilarText(providerName, workstationName) || containsSimilarText(providerName, workstationKey) || sharesKeyword(providerName, workstationName) || sharesKeyword(providerName, workstationKey);
    }

    private static boolean shouldUsePrimaryWorkbenchPriorityLine(@Nullable EncodingMode encodingMode,
                                                                 ItemStack currentEncodedPattern) {
        ResourceLocation patternItemId = resolveItemId(currentEncodedPattern);
        if (AE2_CRAFTING_PATTERN_ITEM_ID.equals(patternItemId) || AE2_STONECUTTING_PATTERN_ITEM_ID.equals(patternItemId) || AE2_SMITHING_TABLE_PATTERN_ITEM_ID.equals(patternItemId)) {
            return true;
        }

        return encodingMode == EncodingMode.CRAFTING || encodingMode == EncodingMode.STONECUTTING || encodingMode == EncodingMode.SMITHING_TABLE;
    }

    private static boolean isStrictWorkstationMatch(ResourceLocation workstationId) {
        return FURNACE_ID.equals(workstationId) || BLAST_FURNACE_ID.equals(workstationId) || SMOKER_ID.equals(workstationId) || CAMPFIRE_ID.equals(workstationId) || AE2_INSCRIBER_ID.equals(workstationId) || AE2_CHARGER_ID.equals(workstationId);
    }

    private static int computeRecordedDeviceTextScore(String providerText, String workstationText) {
        if (providerText.isEmpty() || workstationText.isEmpty()) {
            return 0;
        }

        int sharedDistinctCharacters = countSharedDistinctCharacters(providerText, workstationText);
        if (sharedDistinctCharacters <= 0) {
            return 0;
        }

        int sharedKeywords = countSharedKeywords(providerText, workstationText);
        int score = sharedDistinctCharacters * 10_000 + sharedKeywords * 100_000;

        if (providerText.equals(workstationText)) {
            score += 1_000_000;
        } else if (providerText.contains(workstationText) || workstationText.contains(providerText)) {
            score += 200_000;
        }

        for (String workstationToken : TOKEN_SPLITTER.split(workstationText)) {
            if (workstationToken.length() < 2) {
                continue;
            }
            if (providerText.contains(workstationToken)) {
                score += workstationToken.length() * 50_000;
            }
        }

        return score;
    }

    private static int computeRegistryIdFallbackPriority(String providerText, String workstationText) {
        if (providerText.isEmpty() || workstationText.isEmpty()) {
            return 0;
        }

        int sharedKeywords = countSharedKeywords(providerText, workstationText);
        if (sharedKeywords > 0) {
            return sharedKeywords * 10;
        }

        int sharedDistinctCharacters = countSharedDistinctCharacters(providerText, workstationText);
        if (sharedDistinctCharacters >= 2) {
            return sharedDistinctCharacters;
        }

        return 0;
    }

    private static int countSharedKeywords(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }

        Set<String> leftKeywords = new HashSet<>();
        for (String token : TOKEN_SPLITTER.split(left)) {
            if (token.length() >= 2) {
                leftKeywords.add(token);
            }
        }

        int shared = 0;
        for (String token : TOKEN_SPLITTER.split(right)) {
            if (token.length() < 2 || !leftKeywords.contains(token)) {
                continue;
            }
            shared++;
        }
        return shared;
    }

    private static int countSharedDistinctCharacters(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }

        Set<Integer> leftCharacters = new java.util.HashSet<>();
        left.codePoints().forEach(leftCharacters::add);

        Set<Integer> sharedCharacters = new java.util.HashSet<>();
        right.codePoints().forEach(codePoint -> {
            if (leftCharacters.contains(codePoint)) {
                sharedCharacters.add(codePoint);
            }
        });
        return sharedCharacters.size();
    }

    private static int getExactProviderMatchPriority(PatternContainer container, Component displayName,
                                                     ResourceLocation iconItemId, ResourceLocation preferredWorkstationId) {
        if (isStrictWorkstationMatch(preferredWorkstationId)) {
            return getStrictProviderMatchPriority(container, displayName, iconItemId, preferredWorkstationId);
        }
        if (isAppliedPneumaticsAmadronExtendedStation(preferredWorkstationId)) {
            return isAppliedPneumaticsAmadronExtendedStation(container, displayName, iconItemId) ? 35 : 0;
        }
        if (isAppliedPneumaticsAmadronStation(preferredWorkstationId)) {
            if (isAppliedPneumaticsAmadronExtendedStation(container, displayName, iconItemId)) {
                return 35;
            }
            return isAppliedPneumaticsAmadronStation(container, displayName, iconItemId) ? 25 : 0;
        }
        return 0;
    }

    private static int getStrictProviderMatchPriority(PatternContainer container, Component displayName,
                                                      ResourceLocation iconItemId, ResourceLocation preferredWorkstationId) {
        String providerName = normalizeForMatch(displayName.getString());
        String providerIconName = normalizeForMatch(resolveWorkstationDisplayName(iconItemId).getString());
        String providerIconId = normalizeForMatch(iconItemId.toString());
        String workstationName = normalizeForMatch(resolveWorkstationDisplayName(preferredWorkstationId).getString());
        String workstationId = normalizeForMatch(preferredWorkstationId.toString());

        boolean matched = providerName.contains(workstationName) || providerName.contains(workstationId) || providerIconName.contains(workstationName) || providerIconName.contains(workstationId) || providerIconId.contains(workstationId);

        if (!matched) {
            return 0;
        }

        return 90;
    }

    private static boolean isWorkbenchFamily(ResourceLocation workstationId) {
        return CRAFTING_TABLE_ID.equals(workstationId) || STONECUTTER_ID.equals(workstationId) || SMITHING_TABLE_ID.equals(workstationId) || AE2_MOLECULAR_ASSEMBLER_ID.equals(workstationId) || DATA_RIPPER_REASSEMBLER_ID.equals(workstationId) || EXTENDEDAE_CRYSTAL_ASSEMBLER_ID.equals(workstationId) || EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID.equals(workstationId);
    }

    private static boolean isPrimaryWorkbenchFamily(ResourceLocation workstationId) {
        return CRAFTING_TABLE_ID.equals(workstationId) || STONECUTTER_ID.equals(workstationId) || SMITHING_TABLE_ID.equals(workstationId);
    }

    private static boolean isPrimaryWorkbenchFamilyMode(@Nullable EncodingMode encodingMode,
                                                        ResourceLocation workstationId) {
        if (encodingMode == EncodingMode.CRAFTING) {
            return CRAFTING_TABLE_ID.equals(workstationId);
        }
        if (encodingMode == EncodingMode.STONECUTTING) {
            return STONECUTTER_ID.equals(workstationId);
        }
        if (encodingMode == EncodingMode.SMITHING_TABLE) {
            return SMITHING_TABLE_ID.equals(workstationId);
        }
        return isPrimaryWorkbenchFamily(workstationId);
    }

    private static int getWorkbenchFamilyPriority(PatternContainer container, Component displayName,
                                                  ResourceLocation iconItemId) {
        Integer neoEcoTier = resolveNeoEcoCraftingSubsystemTier(container, displayName, iconItemId);
        if (neoEcoTier != null) {
            return switch (neoEcoTier) {
                case 9 -> 700_000;
                case 6 -> 600_000;
                case 4 -> 500_000;
                default -> 0;
            };
        }

        if (isAssemblerMatrixWorkbenchProvider(container, displayName, iconItemId)) {
            return 400_000;
        }
        if (isMeteoriteWorkbenchProvider(container, displayName, iconItemId)) {
            return 300_000;
        }
        if (isExtendedMolecularAssemblerWorkbenchProvider(container, displayName, iconItemId)) {
            return 200_000;
        }
        if (isMolecularAssemblerWorkbenchProvider(container, displayName, iconItemId)) {
            return 100_000;
        }

        return 0;
    }

    private static boolean isAssemblerMatrixWorkbenchProvider(PatternContainer container, Component displayName,
                                                              ResourceLocation iconItemId) {
        if (isAssemblerMatrixPatternContainer(container)) {
            return true;
        }

        if (hasIdentityIconId(container, iconItemId, EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID) || hasIdentityIconId(container, iconItemId, EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID)) {
            return true;
        }

        return matchesIdentityTokens(container, displayName, iconItemId,
                "装配矩阵",
                "assemblermatrix",
                "assemblermatrixspeed",
                "assemblermatrixcrafter",
                "assemblermatrixpattern");
    }

    private static boolean isMeteoriteWorkbenchProvider(PatternContainer container, Component displayName,
                                                        ResourceLocation iconItemId) {
        if (AE2CS_METEORITE_PATTERN_PROVIDER_ID.equals(iconItemId)) {
            return true;
        }

        if (container instanceof AdaptivePatternProviderHost adaptiveHost && adaptiveHost.isMeteoriteProviderSelected()) {
            return true;
        }

        return matchesIdentityTokens(container, displayName, iconItemId,
                "自装配式样板供应器",
                "meteoritepatternprovider");
    }

    private static boolean isExtendedMolecularAssemblerWorkbenchProvider(PatternContainer container, Component displayName,
                                                                         ResourceLocation iconItemId) {
        if (hasIdentityIconId(container, iconItemId, EXTENDEDAE_EX_MOLECULAR_ASSEMBLER_ID)) {
            return true;
        }

        return matchesIdentityTokens(container, displayName, iconItemId,
                "扩展分子装配室",
                "extendedmolecularassembler",
                "exmolecularassembler");
    }

    private static boolean isMolecularAssemblerWorkbenchProvider(PatternContainer container, Component displayName,
                                                                 ResourceLocation iconItemId) {
        if (isExtendedMolecularAssemblerWorkbenchProvider(container, displayName, iconItemId)) {
            return false;
        }

        if (hasIdentityIconId(container, iconItemId, AE2_MOLECULAR_ASSEMBLER_ID)) {
            return true;
        }

        return matchesIdentityTokens(container, displayName, iconItemId,
                "分子装配室",
                "molecularassembler");
    }

    private static boolean isAppliedPneumaticsAmadronStation(ResourceLocation workstationId) {
        return APPLIED_PNEUMATICS_NAMESPACE.equals(workstationId.getNamespace()) && APPLIED_PNEUMATICS_AMADRON_PROCESS_STATION_PATH.equals(workstationId.getPath());
    }

    private static boolean isAppliedPneumaticsAmadronExtendedStation(ResourceLocation workstationId) {
        return APPLIED_PNEUMATICS_NAMESPACE.equals(workstationId.getNamespace()) && APPLIED_PNEUMATICS_AMADRON_EXTENDED_PROCESS_STATION_PATH.equals(workstationId.getPath());
    }

    private static boolean isAppliedPneumaticsAmadronStation(PatternContainer container, Component displayName,
                                                             ResourceLocation iconItemId) {
        return matchesAppliedPneumaticsMachine(container, displayName, iconItemId,
                APPLIED_PNEUMATICS_AMADRON_PROCESS_STATION_PATH, "meamadronprocessstation");
    }

    private static boolean isAppliedPneumaticsAmadronExtendedStation(PatternContainer container, Component displayName,
                                                                     ResourceLocation iconItemId) {
        return matchesAppliedPneumaticsMachine(container, displayName, iconItemId,
                APPLIED_PNEUMATICS_AMADRON_EXTENDED_PROCESS_STATION_PATH, "meamadronextendedprocessstation");
    }

    private static boolean matchesAppliedPneumaticsMachine(PatternContainer container, Component displayName,
                                                           ResourceLocation iconItemId, String expectedPath,
                                                           String normalizedNameToken) {
        if (APPLIED_PNEUMATICS_NAMESPACE.equals(iconItemId.getNamespace()) && expectedPath.equals(iconItemId.getPath())) {
            return true;
        }

        String normalizedName = normalizeForMatch(displayName.getString());
        if (normalizedName.contains(normalizedNameToken)) {
            return true;
        }

        String className = normalizeForMatch(container.getClass().getName());
        return className.contains(normalizedNameToken);
    }

    private static boolean isNeoEcoCraftingSubsystemIcon(@Nullable ResourceLocation iconItemId) {
        if (iconItemId == null || !NEOECOAE_NAMESPACE.equals(iconItemId.getNamespace())) {
            return false;
        }

        String path = iconItemId.getPath();
        return path.equals(NEOECOAE_CRAFTING_SYSTEM_PATH) || path.startsWith(NEOECOAE_CRAFTING_SYSTEM_PREFIX) || path.equals(NEOECOAE_CRAFTING_WORKER_PATH) || path.equals(NEOECOAE_CRAFTING_PATTERN_BUS_PATH) || path.equals(NEOECOAE_ECO_CRAFTING_WORKER_PATH);
    }

    private static boolean isNeoEcoCraftingSubsystemClassName(PatternContainer container) {
        String className = container.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("neoeco") && (className.contains("crafting") || className.contains("patternbus") || className.contains("worker"));
    }

    private static boolean isNeoEcoCraftingSubsystemName(String displayName) {
        String normalizedName = normalizeForMatch(displayName);
        return normalizedName.contains("可拓展合成子系统") || normalizedName.contains("ecocraftingsystem") || normalizedName.contains("craftingsystem");
    }

    private static boolean isNeoEcoCraftingSubsystemIdentity(PatternContainer container, Component displayName,
                                                             ResourceLocation iconItemId) {
        return isNeoEcoCraftingSubsystemIcon(iconItemId) || isNeoEcoCraftingSubsystemIcon(resolveBaseProviderIconItemId(container)) || isNeoEcoCraftingSubsystemIcon(resolveTerminalIconItemId(container)) || isNeoEcoCraftingSubsystemIcon(resolveAdaptiveInternalProviderIconItemId(container)) || isNeoEcoCraftingSubsystemClassName(container) || isNeoEcoCraftingSubsystemName(displayName.getString()) || matchesIdentityTokens(container, displayName, iconItemId,
                "可拓展合成子系统",
                "ecocraftingsystem",
                "craftingsystem",
                "craftingpatternbus",
                "neoeco");
    }

    @Nullable
    private static Integer resolveNeoEcoCraftingSubsystemTier(PatternContainer container, Component displayName,
                                                              ResourceLocation iconItemId) {
        if (!isNeoEcoCraftingSubsystemIdentity(container, displayName, iconItemId)) {
            return null;
        }

        String tierKey = null;
        for (String identityText : collectProviderIdentityStrings(container, displayName, iconItemId)) {
            tierKey = extractNeoEcoTierToken(identityText);
            if (tierKey != null) {
                break;
            }
        }

        if (tierKey == null || tierKey.length() < 2) {
            return null;
        }

        try {
            return Integer.parseInt(tierKey.substring(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static String resolveNeoEcoCraftingSubsystemTierKey(DiscoveredPatternProvider provider) {
        Integer tier = resolveNeoEcoCraftingSubsystemTier(provider.container(), provider.displayName(), provider.iconItemId());
        return tier == null ? null : "F" + tier;
    }

    @Nullable
    private static String extractNeoEcoTierToken(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher matcher = NEOECOAE_TIER_TOKEN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    private static boolean hasIdentityIconId(PatternContainer container, ResourceLocation iconItemId,
                                             ResourceLocation expectedId) {
        if (expectedId.equals(iconItemId) || expectedId.equals(resolveBaseProviderIconItemId(container)) || expectedId.equals(resolveTerminalIconItemId(container)) || expectedId.equals(resolveAdaptiveInternalProviderIconItemId(container)) || expectedId.equals(resolveBlockEntityTypeId(container)) || expectedId.equals(resolveBlockId(container))) {
            return true;
        }

        return false;
    }

    private static boolean matchesIdentityTokens(PatternContainer container, Component displayName,
                                                 ResourceLocation iconItemId, String... tokens) {
        Set<String> normalizedIdentities = new HashSet<>();
        for (String identityText : collectProviderIdentityStrings(container, displayName, iconItemId)) {
            String normalizedIdentity = normalizeForMatch(identityText);
            if (!normalizedIdentity.isEmpty()) {
                normalizedIdentities.add(normalizedIdentity);
            }
        }

        for (String token : tokens) {
            String normalizedToken = normalizeForMatch(token);
            if (normalizedToken.isEmpty()) {
                continue;
            }

            for (String identity : normalizedIdentities) {
                if (identity.contains(normalizedToken)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> collectProviderIdentityStrings(PatternContainer container, Component displayName,
                                                               ResourceLocation iconItemId) {
        Set<String> identities = new java.util.LinkedHashSet<>();
        identities.add(displayName.getString());
        identities.add(container.getClass().getSimpleName());
        identities.add(container.getClass().getName());
        identities.add(iconItemId.toString());
        identities.add(resolveWorkstationDisplayName(iconItemId).getString());

        ResourceLocation baseIconId = resolveBaseProviderIconItemId(container);
        identities.add(baseIconId.toString());
        identities.add(resolveWorkstationDisplayName(baseIconId).getString());

        ResourceLocation terminalIconId = resolveTerminalIconItemId(container);
        if (terminalIconId != null) {
            identities.add(terminalIconId.toString());
            identities.add(resolveWorkstationDisplayName(terminalIconId).getString());
        }

        ResourceLocation adaptiveInternalIconId = resolveAdaptiveInternalProviderIconItemId(container);
        if (adaptiveInternalIconId != null) {
            identities.add(adaptiveInternalIconId.toString());
            identities.add(resolveWorkstationDisplayName(adaptiveInternalIconId).getString());
        }

        Component adaptiveInternalName = resolveAdaptiveInternalProviderName(container);
        if (adaptiveInternalName != null) {
            identities.add(adaptiveInternalName.getString());
        }

        ResourceLocation blockEntityTypeId = resolveBlockEntityTypeId(container);
        if (blockEntityTypeId != null) {
            identities.add(blockEntityTypeId.toString());
        }

        ResourceLocation blockId = resolveBlockId(container);
        if (blockId != null) {
            identities.add(blockId.toString());
            identities.add(resolveWorkstationDisplayName(blockId).getString());
        }

        identities.removeIf(text -> text == null || text.isEmpty());
        return new ArrayList<>(identities);
    }

    @Nullable
    private static Component resolveAdaptiveInternalProviderName(PatternContainer container) {
        if (!(container instanceof AdaptivePatternProviderHost)) {
            return null;
        }

        Object directName = invokeNoArgReflectively(container, "getResolvedInternalProviderName");
        if (directName instanceof Component component) {
            return component;
        }

        Object profile = invokeNoArgReflectively(container, "getProviderProfile");
        if (profile != null) {
            Object profileName = invokeNoArgReflectively(profile, "displayName");
            if (profileName instanceof Component component) {
                return component;
            }
        }

        Object providerStack = invokeNoArgReflectively(container, "getProviderStack");
        if (providerStack instanceof ItemStack stack && !stack.isEmpty()) {
            return AdaptivePatternProviderResolver.getResolvedProviderDisplayName(stack);
        }

        return null;
    }

    @Nullable
    private static ResourceLocation resolveAdaptiveInternalProviderIconItemId(PatternContainer container) {
        if (!(container instanceof AdaptivePatternProviderHost)) {
            return null;
        }

        Object profile = invokeNoArgReflectively(container, "getProviderProfile");
        if (profile != null) {
            Object profileIcon = invokeNoArgReflectively(profile, "mainMenuIcon");
            if (profileIcon instanceof ItemStack iconStack && !iconStack.isEmpty()) {
                ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(iconStack.getItem());
                if (iconItemId != null && iconStack.getItem() != Items.AIR) {
                    return iconItemId;
                }
            }
        }

        Object providerStack = invokeNoArgReflectively(container, "getProviderStack");
        if (providerStack instanceof ItemStack stack && !stack.isEmpty()) {
            ItemStack resolvedIcon = AdaptivePatternProviderResolver.getResolvedProviderMainMenuIcon(stack);
            if (resolvedIcon != null && !resolvedIcon.isEmpty()) {
                ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(resolvedIcon.getItem());
                if (iconItemId != null && resolvedIcon.getItem() != Items.AIR) {
                    return iconItemId;
                }
            }
        }

        return null;
    }

    @Nullable
    private static ResourceLocation resolveBlockEntityTypeId(PatternContainer container) {
        if (!(container instanceof BlockEntity blockEntity)) {
            return null;
        }

        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
    }

    @Nullable
    private static ResourceLocation resolveBlockId(PatternContainer container) {
        if (!(container instanceof BlockEntity blockEntity)) {
            return null;
        }

        return BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
    }

    @Nullable
    private static Object invokeNoArgReflectively(Object source, String methodName) {
        return ReflectionAccess.invokeNoArg(source, methodName);
    }

    private static Component resolveWorkstationDisplayName(ResourceLocation workstationId) {
        if (DATA_RIPPER_REASSEMBLER_ID.equals(workstationId)) {
            return Component.translatable("workstation.data_energistics.data_reassembler");
        }

        var item = BuiltInRegistries.ITEM.getOptional(workstationId).orElse(null);
        if (item != null) {
            return item.getDefaultInstance().getHoverName();
        }

        var block = BuiltInRegistries.BLOCK.getOptional(workstationId).orElse(null);
        if (block != null) {
            return block.getName();
        }

        return Component.literal(workstationId.toString());
    }

    private static ResourceLocation resolveItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(Items.AIR);
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null ? itemId : BuiltInRegistries.ITEM.getKey(Items.AIR);
    }

    private static boolean containsSimilarText(String providerText, String workstationText) {
        return !providerText.isEmpty() && !workstationText.isEmpty() && (providerText.contains(workstationText) || workstationText.contains(providerText));
    }

    private static boolean sharesKeyword(String providerText, String workstationText) {
        if (providerText.isEmpty() || workstationText.isEmpty()) {
            return false;
        }

        for (String workstationToken : TOKEN_SPLITTER.split(workstationText)) {
            if (workstationToken.length() < 2) {
                continue;
            }
            if (providerText.contains(workstationToken)) {
                return true;
            }
        }

        return false;
    }

    private static int computeFuzzyNamePriority(String providerText, String workstationText) {
        if (providerText.isEmpty() || workstationText.isEmpty()) {
            return 0;
        }

        int sharedDistinctCharacters = countSharedDistinctCharacters(providerText, workstationText);
        if (sharedDistinctCharacters <= 0) {
            return 0;
        }

        int score = sharedDistinctCharacters * 100;
        int sharedKeywords = countSharedKeywords(providerText, workstationText);
        score += sharedKeywords * 1000;

        if (providerText.equals(workstationText)) {
            score += 10_000;
        } else if (providerText.contains(workstationText) || workstationText.contains(providerText)) {
            score += 2_000;
        }

        for (String workstationToken : TOKEN_SPLITTER.split(workstationText)) {
            if (workstationToken.length() < 2) {
                continue;
            }
            if (providerText.contains(workstationToken)) {
                score += workstationToken.length() * 500;
            }
        }

        return score;
    }

    private static String normalizeForMatch(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        text.codePoints()
                .map(Character::toLowerCase)
                .filter(PatternProviderSyncHelper::isMatchCharacter)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }

    private static boolean isMatchCharacter(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private record DiscoveredPatternProvider(
                                             PatternContainer container,
                                             long id,
                                             long sortOrder,
                                             Component displayName,
                                             ResourceLocation iconItemId,
                                             boolean useAeButtonStyle,
                                             boolean renameable,
                                             int patternSlotCount,
                                             int usedPatternSlotCount,
                                             int workbenchLinePriority,
                                             int recordedDeviceScore,
                                             int preferredScore) {}

    private static final class AggregatedPatternProvider {

        private long id;
        private long sortOrder;
        private Component displayName;
        private ResourceLocation iconItemId;
        private boolean useAeButtonStyle;
        private boolean renameable;
        private int patternSlotCount;
        private int usedPatternSlotCount;
        private int workbenchLinePriority;
        private int recordedDeviceScore;
        private int preferredScore;
        private int representationPriority;
        private final List<PatternContainer> containers = new ArrayList<>();

        private AggregatedPatternProvider(DiscoveredPatternProvider provider) {
            this.id = provider.id();
            this.sortOrder = provider.sortOrder();
            this.displayName = provider.displayName();
            this.iconItemId = provider.iconItemId();
            this.useAeButtonStyle = provider.useAeButtonStyle();
            this.renameable = provider.renameable();
            this.representationPriority = getRepresentationPriority(provider);
        }

        private void include(DiscoveredPatternProvider provider) {
            this.id = Math.min(this.id, provider.id());
            this.sortOrder = Math.min(this.sortOrder, provider.sortOrder());
            this.patternSlotCount += provider.patternSlotCount();
            this.usedPatternSlotCount += provider.usedPatternSlotCount();
            this.workbenchLinePriority = Math.max(this.workbenchLinePriority, provider.workbenchLinePriority());
            this.recordedDeviceScore = Math.max(this.recordedDeviceScore, provider.recordedDeviceScore());
            this.preferredScore = Math.max(this.preferredScore, provider.preferredScore());
            this.useAeButtonStyle |= provider.useAeButtonStyle();
            this.renameable |= provider.renameable();
            int incomingPriority = getRepresentationPriority(provider);
            if (incomingPriority > this.representationPriority) {
                this.displayName = provider.displayName();
                this.iconItemId = provider.iconItemId();
                this.representationPriority = incomingPriority;
            }
            this.containers.add(provider.container());
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

        private int preferredScore() {
            return this.preferredScore;
        }

        private int recordedDeviceScore() {
            return this.recordedDeviceScore;
        }

        private int workbenchLinePriority() {
            return this.workbenchLinePriority;
        }

        private List<PatternContainer> containers() {
            return this.containers;
        }

        private int getRepresentationPriority(DiscoveredPatternProvider provider) {
            if (isAssemblerMatrixPatternContainer(provider.container())) {
                return 3;
            }
            if (isNeoEcoCraftingSubsystemIcon(provider.iconItemId()) && provider.iconItemId().getPath().startsWith(NEOECOAE_CRAFTING_SYSTEM_PREFIX)) {
                return 3;
            }
            if (isNeoEcoCraftingSubsystemIcon(provider.iconItemId()) && provider.iconItemId().getPath().equals(NEOECOAE_CRAFTING_SYSTEM_PATH)) {
                return 2;
            }
            if (isNeoEcoCraftingSubsystemContainer(provider)) {
                return 1;
            }
            return 0;
        }
    }
}
