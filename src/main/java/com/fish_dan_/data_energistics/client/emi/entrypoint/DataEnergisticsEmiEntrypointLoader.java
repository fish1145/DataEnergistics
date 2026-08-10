package com.fish_dan_.data_energistics.client.emi.entrypoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.emi.DataEnergisticsEmiEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.emi.DataEnergisticsEmiPlugin;

import dev.emi.emi.api.EmiRegistry;

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
 * Discovers and invokes optional Data Energistics EMI plugins during Data Energistics's own EMI registration cycle.
 *
 * <p>
 * This is the sole reflection boundary for EMI entrypoints. Frozen registrations retain only typed handlers and
 * never retain plugin classes, constructors, or scan data.
 * </p>
 */
public final class DataEnergisticsEmiEntrypointLoader {

    private static final String REQUIRED_MODS_MEMBER = "requiredMods";
    private static boolean initialized;

    private DataEnergisticsEmiEntrypointLoader() {
    }

    /**
     * Loads every eligible annotation entrypoint, freezes their successful registrations, and attaches those
     * registrations before Data Energistics's generic EMI fallback handlers are added.
     *
     * @param registry active Data Energistics EMI registration phase
     */
    public static synchronized void initialize(EmiRegistry registry) {
        if (initialized) {
            throw new IllegalStateException("Data Energistics EMI entrypoints have already been initialized");
        }

        EmiPluginRegistrationAccumulator accumulator = new EmiPluginRegistrationAccumulator();
        List<EntrypointCandidate> candidates = discoverCandidates();
        int loaded = 0;
        for (EntrypointCandidate candidate : candidates) {
            @Nullable
            EmiPluginRegistrationAccumulator.PluginStaging staging = null;
            try {
                DataEnergisticsEmiPlugin plugin = instantiate(candidate);
                staging = accumulator.createStaging(candidate.owningModId(), candidate.className());
                plugin.register(staging);
                accumulator.commit(staging);
                loaded++;
            } catch (Exception | LinkageError exception) {
                if (staging != null) {
                    staging.discard();
                }
                Data_Energistics.LOGGER.error(
                        "Failed to register Data Energistics EMI plugin {} owned by mod {}; discarded all of its staged registrations",
                        candidate.className(),
                        candidate.owningModId(),
                        exception);
            }
        }

        List<EmiRecipeHandlerRegistration<?>> recipeHandlers = accumulator.freeze();
        for (EmiRecipeHandlerRegistration<?> recipeHandler : recipeHandlers) {
            recipeHandler.register(registry);
        }
        initialized = true;
        Data_Energistics.LOGGER.info(
                "Loaded {} of {} Data Energistics EMI plugins: {} recipe handlers",
                loaded,
                candidates.size(),
                recipeHandlers.size());
    }

    /**
     * Reads marker metadata and canonical owning IDs without resolving an annotated plugin class.
     */
    private static List<EntrypointCandidate> discoverCandidates() {
        List<EntrypointCandidate> candidates = new ArrayList<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            List<ModFileScanData.AnnotationData> annotations = scanData
                    .getAnnotatedBy(DataEnergisticsEmiEntrypoint.class, ElementType.TYPE)
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
                                    "Skipping Data Energistics EMI plugin {} owned by mod {}; missing required mods {}",
                                    annotation.clazz().getClassName(),
                                    owningModId,
                                    missingMods);
                            continue;
                        }
                        candidates.add(new EntrypointCandidate(owningModId, annotation.clazz().getClassName()));
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Failed to read required mods for Data Energistics EMI entrypoint {} owned by mod {}; the entrypoint will be ignored",
                                annotation.clazz().getClassName(),
                                owningModId,
                                exception);
                    }
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to resolve the owning mod for {} Data Energistics EMI entrypoint annotation(s); those entrypoints will be ignored",
                        annotations.size(),
                        exception);
            }
        }
        candidates.sort(Comparator.comparing(EntrypointCandidate::owningModId)
                .thenComparing(EntrypointCandidate::className));
        return candidates.stream().distinct().toList();
    }

    /**
     * Decodes the marker's required-mod array from scan metadata.
     */
    private static List<String> requiredMods(ModFileScanData.AnnotationData annotation) {
        @Nullable
        Object encoded = annotation.annotationData().get(REQUIRED_MODS_MEMBER);
        if (encoded == null) {
            return List.of();
        }
        if (!(encoded instanceof List<?> values)) {
            throw new IllegalArgumentException("Data Energistics EMI requiredMods scan value is not an array");
        }

        LinkedHashSet<String> requiredMods = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String modId) || modId.isBlank()) {
                throw new IllegalArgumentException("Data Energistics EMI requiredMods contains an invalid mod ID");
            }
            requiredMods.add(modId);
        }
        return requiredMods.stream().sorted().toList();
    }

    /**
     * Resolves one unambiguous owning mod ID from a scanned mod file.
     */
    private static String resolveOwningModId(ModFileScanData scanData) {
        List<IModFileInfo> fileInfos = scanData.getIModInfoData();
        if (fileInfos.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics EMI entrypoint has no mod metadata");
        }
        List<String> owningModIds = fileInfos.stream()
                .flatMap(fileInfo -> fileInfo.getMods().stream())
                .map(IModInfo::getModId)
                .distinct()
                .sorted()
                .toList();
        if (owningModIds.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics EMI entrypoint declares no owning mod");
        }
        if (owningModIds.size() != 1) {
            throw new IllegalStateException(
                    "A mod file containing a Data Energistics EMI entrypoint has ambiguous owners: " + owningModIds);
        }
        return owningModIds.getFirst();
    }

    /**
     * Validates the public entrypoint contract before invoking its no-argument constructor.
     */
    private static DataEnergisticsEmiPlugin instantiate(EntrypointCandidate candidate)
            throws ReflectiveOperationException {
        Class<?> rawClass = Class.forName(
                candidate.className(), false, DataEnergisticsEmiEntrypointLoader.class.getClassLoader());
        if (!DataEnergisticsEmiPlugin.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException(
                    "EMI entrypoint does not implement DataEnergisticsEmiPlugin: " + candidate.className());
        }
        int modifiers = rawClass.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers)) {
            throw new IllegalArgumentException("EMI entrypoint must be a public concrete class: " + candidate.className());
        }

        Class<? extends DataEnergisticsEmiPlugin> pluginClass = rawClass.asSubclass(DataEnergisticsEmiPlugin.class);
        Constructor<? extends DataEnergisticsEmiPlugin> constructor = pluginClass.getConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new IllegalArgumentException(
                    "EMI entrypoint must expose a public no-argument constructor: " + candidate.className());
        }
        return constructor.newInstance();
    }

    /**
     * Stable discovery key used only before class resolution.
     */
    private record EntrypointCandidate(String owningModId, String className) {
    }
}
