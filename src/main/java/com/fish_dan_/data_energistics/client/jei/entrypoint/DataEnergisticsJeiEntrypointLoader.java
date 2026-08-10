package com.fish_dan_.data_energistics.client.jei.entrypoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.jei.DataEnergisticsJeiPlugin;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import mezz.jei.api.registration.IRecipeTransferRegistration;
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
 * Discovers and invokes optional Data Energistics JEI plugins during Data Energistics's own JEI registration cycle.
 *
 * <p>
 * This is the sole reflection boundary for JEI entrypoints. The frozen registrations retain only typed transfer
 * behavior and never retain plugin classes, constructors, or scan data.
 * </p>
 */
public final class DataEnergisticsJeiEntrypointLoader {

    private static final String REQUIRED_MODS_MEMBER = "requiredMods";
    private static boolean initialized;

    private DataEnergisticsJeiEntrypointLoader() {}

    /**
     * Loads every eligible annotation entrypoint, freezes their successful registrations, and attaches those
     * registrations to JEI before generic fallback handlers are added.
     *
     * @param registration active Data Energistics JEI transfer registration phase
     */
    public static synchronized void initialize(IRecipeTransferRegistration registration) {
        if (initialized) {
            throw new IllegalStateException("Data Energistics JEI entrypoints have already been initialized");
        }

        JeiPluginRegistrationAccumulator registry = new JeiPluginRegistrationAccumulator();
        List<EntrypointCandidate> candidates = discoverCandidates();
        int loaded = 0;
        for (EntrypointCandidate candidate : candidates) {
            @Nullable
            JeiPluginRegistrationAccumulator.PluginStaging staging = null;
            try {
                DataEnergisticsJeiPlugin plugin = instantiate(candidate);
                staging = registry.createStaging(candidate.owningModId(), candidate.className());
                plugin.register(staging);
                registry.commit(staging);
                loaded++;
            } catch (Exception | LinkageError exception) {
                if (staging != null) {
                    staging.discard();
                }
                Data_Energistics.LOGGER.error(
                        "Failed to register Data Energistics JEI plugin {} owned by mod {}; discarded all of its staged registrations",
                        candidate.className(),
                        candidate.owningModId(),
                        exception);
            }
        }

        List<JeiRecipeTransferRegistration<?, ?>> recipeTransferHandlers = registry.freeze();
        for (JeiRecipeTransferRegistration<?, ?> recipeTransferHandler : recipeTransferHandlers) {
            recipeTransferHandler.register(registration);
        }
        initialized = true;
        Data_Energistics.LOGGER.info(
                "Loaded {} of {} Data Energistics JEI plugins: {} recipe-transfer handlers",
                loaded,
                candidates.size(),
                recipeTransferHandlers.size());
    }

    /**
     * Reads marker metadata and canonical owning IDs without resolving an annotated plugin class.
     */
    private static List<EntrypointCandidate> discoverCandidates() {
        List<EntrypointCandidate> candidates = new ArrayList<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            List<ModFileScanData.AnnotationData> annotations = scanData
                    .getAnnotatedBy(DataEnergisticsJeiEntrypoint.class, ElementType.TYPE)
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
                                    "Skipping Data Energistics JEI plugin {} owned by mod {}; missing required mods {}",
                                    annotation.clazz().getClassName(),
                                    owningModId,
                                    missingMods);
                            continue;
                        }
                        candidates.add(new EntrypointCandidate(owningModId, annotation.clazz().getClassName()));
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Failed to read required mods for Data Energistics JEI entrypoint {} owned by mod {}; the entrypoint will be ignored",
                                annotation.clazz().getClassName(),
                                owningModId,
                                exception);
                    }
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to resolve the owning mod for {} Data Energistics JEI entrypoint annotation(s); those entrypoints will be ignored",
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
            throw new IllegalArgumentException("Data Energistics JEI requiredMods scan value is not an array");
        }

        LinkedHashSet<String> requiredMods = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String modId) || modId.isBlank()) {
                throw new IllegalArgumentException("Data Energistics JEI requiredMods contains an invalid mod ID");
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
            throw new IllegalStateException("A mod file containing a Data Energistics JEI entrypoint has no mod metadata");
        }
        List<String> owningModIds = fileInfos.stream()
                .flatMap(fileInfo -> fileInfo.getMods().stream())
                .map(IModInfo::getModId)
                .distinct()
                .sorted()
                .toList();
        if (owningModIds.isEmpty()) {
            throw new IllegalStateException("A mod file containing a Data Energistics JEI entrypoint declares no owning mod");
        }
        if (owningModIds.size() != 1) {
            throw new IllegalStateException(
                    "A mod file containing a Data Energistics JEI entrypoint has ambiguous owners: " + owningModIds);
        }
        return owningModIds.getFirst();
    }

    /**
     * Validates the public entrypoint contract before invoking its no-argument constructor.
     */
    private static DataEnergisticsJeiPlugin instantiate(EntrypointCandidate candidate) throws ReflectiveOperationException {
        Class<?> rawClass = Class.forName(
                candidate.className(), false, DataEnergisticsJeiEntrypointLoader.class.getClassLoader());
        if (!DataEnergisticsJeiPlugin.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException(
                    "JEI entrypoint does not implement DataEnergisticsJeiPlugin: " + candidate.className());
        }
        int modifiers = rawClass.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers)) {
            throw new IllegalArgumentException("JEI entrypoint must be a public concrete class: " + candidate.className());
        }

        Class<? extends DataEnergisticsJeiPlugin> pluginClass = rawClass.asSubclass(DataEnergisticsJeiPlugin.class);
        Constructor<? extends DataEnergisticsJeiPlugin> constructor = pluginClass.getConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
            throw new IllegalArgumentException("JEI entrypoint must expose a public no-argument constructor: " + candidate.className());
        }
        return constructor.newInstance();
    }

    /**
     * Stable discovery key used only before class resolution.
     */
    private record EntrypointCandidate(String owningModId, String className) {}
}
