package com.fish_dan_.data_energistics.integration.ae2lt;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModInfo;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Optional;

/**
 * Defines the last AE2 Lightning Tech version for which Data Energistics enables its optional compatibility paths.
 */
public final class Ae2LtVersionPolicy {

    /**
     * AE2 Lightning Tech's stable mod identifier.
     */
    public static final String MOD_ID = "ae2lt";

    private static final ArtifactVersion MAXIMUM_SUPPORTED_VERSION = new DefaultArtifactVersion("2.0.0");

    private Ae2LtVersionPolicy() {}

    /**
     * Returns the installed AE2LT version captured from NeoForge mod metadata during loading.
     */
    public static Optional<ArtifactVersion> installedVersion() {
        return InstalledVersionHolder.VERSION;
    }

    /**
     * Returns the installed version only when it is newer than the supported compatibility boundary.
     */
    public static Optional<ArtifactVersion> unsupportedInstalledVersion() {
        return installedVersion().filter(Ae2LtVersionPolicy::isUnsupported);
    }

    /**
     * Tests a semantic Maven version against the strict {@code > 2.0.0} unsupported boundary.
     */
    public static boolean isUnsupported(ArtifactVersion version) {
        return version.compareTo(MAXIMUM_SUPPORTED_VERSION) > 0;
    }

    /**
     * Returns the inclusive upper compatibility boundary for logs and localized player notices.
     */
    public static ArtifactVersion maximumSupportedVersion() {
        return MAXIMUM_SUPPORTED_VERSION;
    }

    private static Optional<ArtifactVersion> detectInstalledVersion() {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModContainerById(MOD_ID)
                    .map(ModContainer::getModInfo)
                    .map(IModInfo::getVersion);
        }

        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList == null) {
            return Optional.empty();
        }
        return loadingModList.getMods().stream()
                .filter(mod -> MOD_ID.equals(mod.getModId()))
                .findFirst()
                .map(IModInfo::getVersion);
    }

    private static final class InstalledVersionHolder {

        private static final Optional<ArtifactVersion> VERSION = detectInstalledVersion();

        private InstalledVersionHolder() {}
    }
}
