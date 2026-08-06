package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.StableDigest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;

/**
 * Provides the single client-thread-confined preference repository and connection profile lifecycle.
 */
public final class PatternEncodingClientPreferencesAccess {

    private static final PatternEncodingClientPreferences INSTANCE = new PatternEncodingClientPreferencesImpl(
            FMLPaths.CONFIGDIR.get().resolve("data_energistics").resolve("client_preferences.json"),
            () -> Minecraft.getInstance().isSameThread(),
            Clock.systemUTC());

    private PatternEncodingClientPreferencesAccess() {}

    /**
     * Returns the shared client preference repository.
     */
    public static PatternEncodingClientPreferences get() {
        return INSTANCE;
    }

    /**
     * Selects an isolated profile for the currently connected remote or integrated server.
     */
    public static void activateCurrentServerProfile() {
        String profile = resolveCurrentServerProfile();
        if (profile == null) {
            Data_Energistics.LOGGER.warn(
                    "Unable to identify the current server; pattern provider statistics will remain session-only");
            INSTANCE.deactivateServerProfile();
            return;
        }
        INSTANCE.activateServerProfile(profile);
    }

    /**
     * Clears all connection-scoped preference state on disconnect.
     */
    public static void deactivateServerProfile() {
        INSTANCE.deactivateServerProfile();
    }

    @Nullable
    private static String resolveCurrentServerProfile() {
        Minecraft minecraft = Minecraft.getInstance();
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            Path worldPath = integratedServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return StableDigest.sha256("singleplayer\0" + worldPath);
        }
        var serverData = minecraft.getCurrentServer();
        if (serverData == null || serverData.ip == null || serverData.ip.isBlank()) {
            return null;
        }
        String rawAddress = serverData.ip.trim();
        if (!ServerAddress.isValidAddress(rawAddress)) {
            Data_Energistics.LOGGER.warn("Unable to normalize invalid server address for pattern statistics: {}",
                    rawAddress);
            return null;
        }
        ServerAddress address = ServerAddress.parseString(rawAddress);
        String host = address.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank()) {
            return null;
        }
        return StableDigest.sha256("remote\0" + host + '\0' + address.getPort());
    }
}
