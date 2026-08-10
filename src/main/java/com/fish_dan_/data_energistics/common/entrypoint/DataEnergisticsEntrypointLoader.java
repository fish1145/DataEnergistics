package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Discovers and invokes the single public Data Energistics plugin entrypoint during common setup.
 *
 * <p>
 * This class is the sole reflection boundary. Runtime provider matching and dispatch consume only the frozen values
 * produced here and never retain plugin classes, constructors, scan records, or mutable staging registries.
 * </p>
 */
public final class DataEnergisticsEntrypointLoader {

    private static final String REQUIRED_MODS_MEMBER = "requiredMods";
    private static volatile @Nullable DataEnergisticsRegistrySnapshot publishedSnapshot;

    private DataEnergisticsEntrypointLoader() {}

    /**
     * Loads plugins in deterministic owning-mod and class order, isolates failures, and publishes one snapshot.
     *
     * @return immutable runtime registry snapshot
     */
    public static synchronized DataEnergisticsRegistrySnapshot initialize() {
        if (publishedSnapshot != null) {
            throw new IllegalStateException("Data Energistics entrypoints have already been initialized");
        }

        PluginRegistrationAccumulator registry = new PluginRegistrationAccumulator();
        List<EntrypointCandidate> candidates = discoverCandidates();
        int loaded = 0;
        for (EntrypointCandidate candidate : candidates) {
            @Nullable
            PluginRegistrationAccumulator.PluginStaging staging = null;
            try {
                DataEnergisticsPlugin plugin = instantiate(candidate);
                staging = registry.createStaging(candidate.owningModId(), candidate.className());
                plugin.register(staging);
                registry.commit(staging);
                loaded++;
            } catch (Exception | LinkageError exception) {
                if (staging != null) {
                    staging.discard();
                }
                Data_Energistics.LOGGER.error(
                        "Failed to register Data Energistics plugin {} owned by mod {}; discarded all of its staged registrations",
                        candidate.className(),
                        candidate.owningModId(),
                        exception);
            }
        }

        publishedSnapshot = registry.freeze();
        Data_Energistics.LOGGER.info(
                "Loaded {} of {} Data Energistics plugins: {} terminals, {} provider declarations, {} adaptive provider definitions, {} Trinity recipe resolvers, {} Trinity search contributors, {} virtual output adapters",
                loaded,
                candidates.size(),
                publishedSnapshot.universalTerminalRegistrations().size(),
                publishedSnapshot.patternProviderRegistrations().size(),
                publishedSnapshot.adaptivePatternProviderRegistrations().size(),
                publishedSnapshot.trinityPatternRecipeResolverCount(),
                publishedSnapshot.trinityPatternSearchTermRegistrations().size(),
                publishedSnapshot.virtualCraftingOutputAdapters().size());
        return publishedSnapshot;
    }

    /**
     * Returns the immutable registry after common setup has completed.
     */
    public static DataEnergisticsRegistrySnapshot snapshot() {
        DataEnergisticsRegistrySnapshot current = publishedSnapshot;
        if (current == null) {
            throw new IllegalStateException("Data Energistics entrypoints have not been initialized yet");
        }
        return current;
    }

    /**
     * Reads only marker annotations and canonical owning mod IDs from NeoForge scan data.
     */
    private static List<EntrypointCandidate> discoverCandidates() {
        List<EntrypointCandidate> candidates = new ArrayList<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            List<ModFileScanData.AnnotationData> annotations = scanData
                    .getAnnotatedBy(DataEnergisticsEntrypoint.class, ElementType.TYPE)
                    .toList();
            if (annotations.isEmpty()) {
                continue;
            }
            try {
                String owningModId = resolveOwningModId(scanData);
                for (ModFileScanData.AnnotationData annotation : annotations) {
                    try {
                        List<String> missingMods = requiredMods(annotation).stream()
                                .filter(Predicate.not(ModList.get()::isLoaded))
                                .toList();
                        if (!missingMods.isEmpty()) {
                            Data_Energistics.LOGGER.debug(
                                    "Skipping Data Energistics plugin {} owned by mod {}; missing required mods {}",
                                    annotation.clazz().getClassName(),
                                    owningModId,
                                    missingMods);
                            continue;
                        }
                        candidates.add(new EntrypointCandidate(owningModId, annotation.clazz().getClassName()));
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Failed to read required mods for Data Energistics entrypoint {} owned by mod {}; " + "the entrypoint will be ignored",
                                annotation.clazz().getClassName(),
                                owningModId,
                                exception);
                    }
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to resolve the owning mod for {} Data Energistics entrypoint annotation(s); " + "those entrypoints will be ignored",
                        annotations.size(),
                        exception);
            }
        }
        candidates.sort(Comparator.comparing(EntrypointCandidate::owningModId)
                .thenComparing(EntrypointCandidate::className));
        return candidates.stream().distinct().toList();
    }

    /**
     * Decodes the marker's string-array member without resolving the annotated plugin class.
     */
    static List<String> requiredMods(ModFileScanData.AnnotationData annotation) {
        @Nullable
        Object encoded = annotation.annotationData().get(REQUIRED_MODS_MEMBER);
        if (encoded == null) {
            return List.of();
        }
        if (!(encoded instanceof List<?> values)) {
            throw new IllegalArgumentException("Data Energistics requiredMods scan value is not an array");
        }

        LinkedHashSet<String> requiredMods = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String modId) || modId.isBlank()) {
                throw new IllegalArgumentException("Data Energistics requiredMods contains an invalid mod ID");
            }
            requiredMods.add(modId);
        }
        return requiredMods.stream().sorted().toList();
    }

    /**
     * Resolves the only mod descriptor that can unambiguously own entrypoints in one scanned mod file.
     */
    private static String resolveOwningModId(ModFileScanData scanData) {
        List<IModFileInfo> fileInfos = scanData.getIModInfoData();
        if (fileInfos.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics entrypoint has no mod metadata");
        }
        List<String> owningModIds = fileInfos.stream()
                .flatMap(fileInfo -> fileInfo.getMods().stream())
                .map(IModInfo::getModId)
                .distinct()
                .sorted()
                .toList();
        if (owningModIds.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics entrypoint declares no owning mod");
        }
        if (owningModIds.size() != 1) {
            throw new IllegalStateException(
                    "A mod file containing a Data Energistics entrypoint has ambiguous owners: " + owningModIds);
        }
        return owningModIds.getFirst();
    }

    /**
     * Validates the public plugin contract before invoking its no-argument constructor.
     */
    private static DataEnergisticsPlugin instantiate(EntrypointCandidate candidate) throws ReflectiveOperationException {
        Class<?> rawClass = Class.forName(
                candidate.className(), false, DataEnergisticsEntrypointLoader.class.getClassLoader());
        if (!DataEnergisticsPlugin.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException("Entrypoint does not implement DataEnergisticsPlugin: " + candidate.className());
        }
        int modifiers = rawClass.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers)) {
            throw new IllegalArgumentException("Entrypoint must be a public concrete class: " + candidate.className());
        }

        Class<? extends DataEnergisticsPlugin> pluginClass = rawClass.asSubclass(DataEnergisticsPlugin.class);
        Constructor<? extends DataEnergisticsPlugin> constructor = pluginClass.getConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new IllegalArgumentException("Entrypoint must expose a public no-argument constructor: " + candidate.className());
        }
        return constructor.newInstance();
    }

    /**
     * Stable discovery key used exclusively before plugin instantiation.
     */
    private record EntrypointCandidate(String owningModId, String className) {}
}
