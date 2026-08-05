package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.common.PatternProviderClickStatistic;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** JSON-backed client preference repository with bounded, atomic persistence. */
public final class PatternEncodingClientPreferencesImpl implements PatternEncodingClientPreferences {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_STATISTICS_PER_PROFILE = 2048;
    public static final int MAX_STATISTICS_TOTAL = 8192;
    public static final int MAX_SERVER_PROFILES = 32;
    public static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;
    public static final int MIN_PANEL_OFFSET = -8192;
    public static final int MAX_PANEL_OFFSET = 8192;
    private static final Pattern PROFILE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Comparator<PatternProviderClickStatistic> EVICTION_ORDER = Comparator
            .comparingLong(PatternProviderClickStatistic::count)
            .thenComparingLong(PatternProviderClickStatistic::lastUsedEpochMillis)
            .thenComparing(PatternProviderClickStatistic::stableKey);

    private final Path file;
    private final BooleanSupplier mainThreadCheck;
    private final Clock clock;
    private final Map<String, ServerProfile> serverProfiles = new LinkedHashMap<>();
    private final ServerProfile sessionProfile = new ServerProfile(0L);
    private boolean loaded;
    private boolean writesDisabled;
    private boolean uploadEnabledPresent;
    private boolean uploadEnabled = true;
    private boolean patternSourceEnabledPresent;
    private boolean patternSourceEnabled = true;
    private boolean lastWorkstationPresent;
    @Nullable
    private ResourceLocation lastWorkstation;
    private boolean previewPanelPresent;
    private int previewPanelOffsetX;
    private int previewPanelOffsetY;
    @Nullable
    private String activeServerProfile;

    /** Creates a repository whose thread and clock dependencies can be supplied directly by production and tests. */
    public PatternEncodingClientPreferencesImpl(Path file, BooleanSupplier mainThreadCheck, Clock clock) {
        if (file == null || mainThreadCheck == null || clock == null) {
            throw new IllegalArgumentException("Pattern encoding preferences require a file, thread check, and clock");
        }
        this.file = file;
        this.mainThreadCheck = mainThreadCheck;
        this.clock = clock;
    }

    @Override
    public int presentMask() {
        ensureLoaded();
        int mask = 0;
        if (this.uploadEnabledPresent) {
            mask |= PRESENT_UPLOAD_ENABLED;
        }
        if (this.patternSourceEnabledPresent) {
            mask |= PRESENT_PATTERN_SOURCE_ENABLED;
        }
        if (this.lastWorkstationPresent) {
            mask |= PRESENT_LAST_WORKSTATION;
        }
        if (this.previewPanelPresent) {
            mask |= PRESENT_PREVIEW_PANEL;
        }
        return mask;
    }

    @Override
    public boolean uploadEnabled() {
        ensureLoaded();
        return this.uploadEnabled;
    }

    @Override
    public void setUploadEnabled(boolean enabled) {
        ensureLoaded();
        this.uploadEnabled = enabled;
        this.uploadEnabledPresent = true;
        save();
    }

    @Override
    public boolean patternSourceEnabled() {
        ensureLoaded();
        return this.patternSourceEnabled;
    }

    @Override
    public void setPatternSourceEnabled(boolean enabled) {
        ensureLoaded();
        this.patternSourceEnabled = enabled;
        this.patternSourceEnabledPresent = true;
        save();
    }

    @Override
    public @Nullable ResourceLocation lastWorkstation() {
        ensureLoaded();
        return this.lastWorkstation;
    }

    @Override
    public void setLastWorkstation(@Nullable ResourceLocation workstation) {
        ensureLoaded();
        this.lastWorkstation = workstation;
        this.lastWorkstationPresent = true;
        save();
    }

    @Override
    public int previewPanelOffsetX() {
        ensureLoaded();
        return this.previewPanelOffsetX;
    }

    @Override
    public int previewPanelOffsetY() {
        ensureLoaded();
        return this.previewPanelOffsetY;
    }

    @Override
    public void setPreviewPanelOffset(int offsetX, int offsetY) {
        ensureLoaded();
        validatePanelOffset(offsetX, offsetY);
        this.previewPanelOffsetX = offsetX;
        this.previewPanelOffsetY = offsetY;
        this.previewPanelPresent = true;
        save();
    }

    @Override
    public int applyMissingLegacyValues(boolean legacyUploadEnabled, boolean legacyPatternSourceEnabled,
                                        @Nullable ResourceLocation legacyLastWorkstation,
                                        int legacyOffsetX, int legacyOffsetY) {
        ensureLoaded();
        validatePanelOffset(legacyOffsetX, legacyOffsetY);
        int migratedMask = 0;
        if (!this.uploadEnabledPresent) {
            this.uploadEnabled = legacyUploadEnabled;
            this.uploadEnabledPresent = true;
            migratedMask |= PRESENT_UPLOAD_ENABLED;
        }
        if (!this.patternSourceEnabledPresent) {
            this.patternSourceEnabled = legacyPatternSourceEnabled;
            this.patternSourceEnabledPresent = true;
            migratedMask |= PRESENT_PATTERN_SOURCE_ENABLED;
        }
        if (!this.lastWorkstationPresent) {
            this.lastWorkstation = legacyLastWorkstation;
            this.lastWorkstationPresent = true;
            migratedMask |= PRESENT_LAST_WORKSTATION;
        }
        if (!this.previewPanelPresent) {
            this.previewPanelOffsetX = legacyOffsetX;
            this.previewPanelOffsetY = legacyOffsetY;
            this.previewPanelPresent = true;
            migratedMask |= PRESENT_PREVIEW_PANEL;
        }
        if (migratedMask != 0) {
            save();
        }
        return migratedMask;
    }

    @Override
    public void activateServerProfile(String profileDigest) {
        ensureLoaded();
        validateProfileDigest(profileDigest);
        this.activeServerProfile = profileDigest;
        ServerProfile profile = this.serverProfiles.computeIfAbsent(
                profileDigest, ignored -> new ServerProfile(this.clock.millis()));
        profile.lastAccessEpochMillis = this.clock.millis();
        trimProfiles();
        save();
    }

    @Override
    public void deactivateServerProfile() {
        ensureLoaded();
        this.activeServerProfile = null;
        this.sessionProfile.statistics.clear();
        this.sessionProfile.lastAccessEpochMillis = 0L;
    }

    @Override
    public List<PatternProviderClickStatistic> statistics(PatternEncodingRankingContext context,
                                                          Collection<String> providerDigests) {
        ensureLoaded();
        if (context == null || providerDigests == null) {
            throw new IllegalArgumentException("Pattern ranking context and provider digests must not be null");
        }
        Set<String> requested = new HashSet<>(providerDigests.size());
        for (String providerDigest : providerDigests) {
            PatternProviderClickStatistic validated = new PatternProviderClickStatistic(context, providerDigest, 0L, 0L);
            requested.add(validated.providerDigest());
        }
        List<PatternProviderClickStatistic> result = new ArrayList<>();
        for (PatternProviderClickStatistic statistic : activeProfile().statistics.values()) {
            if (statistic.context().equals(context) && requested.contains(statistic.providerDigest())) {
                result.add(statistic);
            }
        }
        result.sort(Comparator.comparing(PatternProviderClickStatistic::stableKey));
        return List.copyOf(result);
    }

    @Override
    public void recordUpload(PatternEncodingRankingContext context, String providerDigest,
                             long absoluteCount, long successEpochMillis) {
        ensureLoaded();
        PatternProviderClickStatistic incoming = new PatternProviderClickStatistic(
                context, providerDigest, absoluteCount, successEpochMillis);
        ServerProfile profile = activeProfile();
        String key = incoming.stableKey();
        PatternProviderClickStatistic existing = profile.statistics.get(key);
        long mergedCount = existing == null ? incoming.count() : Math.max(existing.count(), incoming.count());
        long mergedTime = existing == null ? incoming.lastUsedEpochMillis()
                : Math.max(existing.lastUsedEpochMillis(), incoming.lastUsedEpochMillis());
        profile.statistics.put(key, new PatternProviderClickStatistic(context, providerDigest, mergedCount, mergedTime));
        profile.lastAccessEpochMillis = Math.max(profile.lastAccessEpochMillis, successEpochMillis);
        trimStatistics();
        if (this.activeServerProfile != null) {
            save();
        }
    }

    private void ensureLoaded() {
        checkMainThread();
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        if (!Files.exists(this.file)) {
            return;
        }
        try {
            if (Files.size(this.file) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Client preference file exceeds " + MAX_FILE_BYTES + " bytes");
            }
            JsonElement rootElement;
            try (var reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                rootElement = JsonParser.parseReader(reader);
            }
            if (!rootElement.isJsonObject()) {
                throw new IllegalArgumentException("Client preference root must be a JSON object");
            }
            loadRoot(rootElement.getAsJsonObject());
        } catch (FutureSchemaException exception) {
            Data_Energistics.LOGGER.error("Cannot write future Data Energistics client preference schema in {}",
                    this.file, exception);
            resetToDefaults(true);
            this.writesDisabled = true;
        } catch (IOException | RuntimeException exception) {
            recoverCorruptFile(exception);
        }
    }

    private void loadRoot(JsonObject root) {
        int schemaVersion = readRequiredInt(root, "schemaVersion");
        if (schemaVersion > SCHEMA_VERSION) {
            throw new FutureSchemaException(schemaVersion);
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported client preference schema: " + schemaVersion);
        }
        JsonObject preferences = readOptionalObject(root, "preferences");
        if (preferences != null) {
            if (preferences.has("uploadEnabled")) {
                this.uploadEnabled = readRequiredBoolean(preferences, "uploadEnabled");
                this.uploadEnabledPresent = true;
            }
            if (preferences.has("patternSourceEnabled")) {
                this.patternSourceEnabled = readRequiredBoolean(preferences, "patternSourceEnabled");
                this.patternSourceEnabledPresent = true;
            }
            if (preferences.has("lastWorkstation")) {
                JsonElement workstationElement = preferences.get("lastWorkstation");
                if (workstationElement.isJsonNull()) {
                    this.lastWorkstation = null;
                } else {
                    this.lastWorkstation = parseResourceLocation(readString(workstationElement, "lastWorkstation"));
                }
                this.lastWorkstationPresent = true;
            }
            if (preferences.has("previewPanel")) {
                JsonObject previewPanel = readRequiredObject(preferences, "previewPanel");
                this.previewPanelOffsetX = readRequiredInt(previewPanel, "offsetX");
                this.previewPanelOffsetY = readRequiredInt(previewPanel, "offsetY");
                validatePanelOffset(this.previewPanelOffsetX, this.previewPanelOffsetY);
                this.previewPanelPresent = true;
            }
        }
        JsonObject profiles = readOptionalObject(root, "serverProfiles");
        if (profiles == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
            validateProfileDigest(entry.getKey());
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Server profile must be a JSON object: " + entry.getKey());
            }
            this.serverProfiles.put(entry.getKey(), readProfile(entry.getValue().getAsJsonObject()));
        }
        trimProfiles();
        trimStatistics();
    }

    private ServerProfile readProfile(JsonObject profileObject) {
        long lastAccess = readRequiredLong(profileObject, "lastAccessEpochMillis");
        if (lastAccess < 0L) {
            throw new IllegalArgumentException("Server profile last access must not be negative");
        }
        ServerProfile profile = new ServerProfile(lastAccess);
        JsonArray statistics = readRequiredArray(profileObject, "clickStatistics");
        for (JsonElement statisticElement : statistics) {
            if (!statisticElement.isJsonObject()) {
                throw new IllegalArgumentException("Click statistic must be a JSON object");
            }
            JsonObject statisticObject = statisticElement.getAsJsonObject();
            String recipeScope = readRequiredString(statisticObject, "recipeScope");
            ResourceLocation workstation = parseResourceLocation(readRequiredString(statisticObject, "workstation"));
            String providerDigest = readRequiredString(statisticObject, "providerDigest");
            long count = readRequiredLong(statisticObject, "count");
            long lastUsed = readRequiredLong(statisticObject, "lastUsedEpochMillis");
            PatternProviderClickStatistic statistic = new PatternProviderClickStatistic(
                    new PatternEncodingRankingContext(recipeScope, workstation), providerDigest, count, lastUsed);
            if (profile.statistics.putIfAbsent(statistic.stableKey(), statistic) != null) {
                throw new IllegalArgumentException("Duplicate click statistic: " + statistic.stableKey());
            }
        }
        return profile;
    }

    private void recoverCorruptFile(Exception failure) {
        Data_Energistics.LOGGER.error("Failed to load Data Energistics client preferences from {}",
                this.file, failure);
        Path backup = this.file.resolveSibling(this.file.getFileName() + "." + this.clock.millis() + ".corrupt");
        try {
            Files.move(this.file, backup, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException backupFailure) {
            Data_Energistics.LOGGER.error("Failed to preserve corrupt Data Energistics client preferences at {}",
                    this.file, backupFailure);
            resetToDefaults(true);
            this.writesDisabled = true;
            return;
        }
        resetToDefaults(true);
        save();
    }

    private void resetToDefaults(boolean markPresent) {
        this.uploadEnabled = true;
        this.patternSourceEnabled = true;
        this.lastWorkstation = null;
        this.previewPanelOffsetX = 0;
        this.previewPanelOffsetY = 0;
        this.uploadEnabledPresent = markPresent;
        this.patternSourceEnabledPresent = markPresent;
        this.lastWorkstationPresent = markPresent;
        this.previewPanelPresent = markPresent;
        this.serverProfiles.clear();
        this.sessionProfile.statistics.clear();
        this.activeServerProfile = null;
    }

    private void save() {
        if (this.writesDisabled) {
            return;
        }
        trimProfiles();
        trimStatistics();
        Path parent = this.file.getParent();
        if (parent == null) {
            throw new IllegalStateException("Client preference file must have a parent directory: " + this.file);
        }
        Path temporary = parent.resolve(this.file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, GSON.toJson(writeRoot()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(temporary, this.file,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Data_Energistics.LOGGER.error("Atomic client preference replacement is not supported for {}",
                    this.file, exception);
            deleteTemporary(temporary);
        } catch (IOException exception) {
            Data_Energistics.LOGGER.error("Failed to save Data Energistics client preferences to {}",
                    this.file, exception);
            deleteTemporary(temporary);
        }
    }

    private JsonObject writeRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject preferences = new JsonObject();
        if (this.uploadEnabledPresent) {
            preferences.addProperty("uploadEnabled", this.uploadEnabled);
        }
        if (this.patternSourceEnabledPresent) {
            preferences.addProperty("patternSourceEnabled", this.patternSourceEnabled);
        }
        if (this.lastWorkstationPresent) {
            if (this.lastWorkstation == null) {
                preferences.add("lastWorkstation", null);
            } else {
                preferences.addProperty("lastWorkstation", this.lastWorkstation.toString());
            }
        }
        if (this.previewPanelPresent) {
            JsonObject previewPanel = new JsonObject();
            previewPanel.addProperty("offsetX", this.previewPanelOffsetX);
            previewPanel.addProperty("offsetY", this.previewPanelOffsetY);
            preferences.add("previewPanel", previewPanel);
        }
        root.add("preferences", preferences);

        JsonObject profiles = new JsonObject();
        this.serverProfiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> profiles.add(entry.getKey(), writeProfile(entry.getValue())));
        root.add("serverProfiles", profiles);
        return root;
    }

    private JsonObject writeProfile(ServerProfile profile) {
        JsonObject result = new JsonObject();
        result.addProperty("lastAccessEpochMillis", profile.lastAccessEpochMillis);
        JsonArray statistics = new JsonArray();
        profile.statistics.values().stream()
                .sorted(Comparator.comparing(PatternProviderClickStatistic::stableKey))
                .forEach(statistic -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("recipeScope", statistic.context().recipeScope());
                    value.addProperty("workstation", statistic.context().workstation().toString());
                    value.addProperty("providerDigest", statistic.providerDigest());
                    value.addProperty("count", statistic.count());
                    value.addProperty("lastUsedEpochMillis", statistic.lastUsedEpochMillis());
                    statistics.add(value);
                });
        result.add("clickStatistics", statistics);
        return result;
    }

    private ServerProfile activeProfile() {
        if (this.activeServerProfile == null) {
            return this.sessionProfile;
        }
        return this.serverProfiles.computeIfAbsent(
                this.activeServerProfile, ignored -> new ServerProfile(this.clock.millis()));
    }

    private void trimProfiles() {
        while (this.serverProfiles.size() > MAX_SERVER_PROFILES) {
            String removable = this.serverProfiles.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(this.activeServerProfile))
                    .min(Comparator.<Map.Entry<String, ServerProfile>>comparingLong(entry -> entry.getValue().lastAccessEpochMillis)
                            .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .orElseThrow(() -> new IllegalStateException("Cannot evict the only active server profile"));
            this.serverProfiles.remove(removable);
        }
    }

    private void trimStatistics() {
        for (ServerProfile profile : this.serverProfiles.values()) {
            while (profile.statistics.size() > MAX_STATISTICS_PER_PROFILE) {
                removeLowestStatistic(profile);
            }
        }
        while (totalStatisticCount() > MAX_STATISTICS_TOTAL) {
            ServerProfile profile = this.serverProfiles.values().stream()
                    .filter(candidate -> !candidate.statistics.isEmpty())
                    .min(Comparator.comparing(
                            candidate -> candidate.statistics.values().stream().min(EVICTION_ORDER).orElseThrow(),
                            EVICTION_ORDER))
                    .orElseThrow();
            removeLowestStatistic(profile);
        }
    }

    private int totalStatisticCount() {
        return this.serverProfiles.values().stream().mapToInt(profile -> profile.statistics.size()).sum();
    }

    private static void removeLowestStatistic(ServerProfile profile) {
        PatternProviderClickStatistic statistic = profile.statistics.values().stream().min(EVICTION_ORDER).orElseThrow();
        profile.statistics.remove(statistic.stableKey());
    }

    private void checkMainThread() {
        if (!this.mainThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Pattern encoding client preferences may only be accessed on the client main thread");
        }
    }

    private static void validatePanelOffset(int offsetX, int offsetY) {
        if (offsetX < MIN_PANEL_OFFSET || offsetX > MAX_PANEL_OFFSET ||
                offsetY < MIN_PANEL_OFFSET || offsetY > MAX_PANEL_OFFSET) {
            throw new IllegalArgumentException("Preview panel offset is outside [" + MIN_PANEL_OFFSET + ", " + MAX_PANEL_OFFSET + "]");
        }
    }

    private static void validateProfileDigest(String profileDigest) {
        if (profileDigest == null || !PROFILE_DIGEST.matcher(profileDigest).matches()) {
            throw new IllegalArgumentException("Invalid server profile digest: " + profileDigest);
        }
    }

    private static ResourceLocation parseResourceLocation(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource location: " + value);
        }
        return id;
    }

    private static JsonObject readRequiredObject(JsonObject parent, String key) {
        JsonObject value = readOptionalObject(parent, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing JSON object: " + key);
        }
        return value;
    }

    @Nullable
    private static JsonObject readOptionalObject(JsonObject parent, String key) {
        if (!parent.has(key)) {
            return null;
        }
        JsonElement value = parent.get(key);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("JSON field must be an object: " + key);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray readRequiredArray(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonArray()) {
            throw new IllegalArgumentException("JSON field must be an array: " + key);
        }
        return parent.getAsJsonArray(key);
    }

    private static boolean readRequiredBoolean(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive() ||
                !parent.getAsJsonPrimitive(key).isBoolean()) {
            throw new IllegalArgumentException("JSON field must be a boolean: " + key);
        }
        return parent.get(key).getAsBoolean();
    }

    private static int readRequiredInt(JsonObject parent, String key) {
        long value = readRequiredLong(parent, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JSON integer is out of range: " + key);
        }
        return (int) value;
    }

    private static long readRequiredLong(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonPrimitive() ||
                !parent.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("JSON field must be an integer: " + key);
        }
        try {
            return Long.parseLong(parent.get(key).getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("JSON field must be an exact long: " + key, exception);
        }
    }

    private static String readRequiredString(JsonObject parent, String key) {
        if (!parent.has(key)) {
            throw new IllegalArgumentException("Missing JSON string: " + key);
        }
        return readString(parent.get(key), key);
    }

    private static String readString(JsonElement value, String key) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("JSON field must be a string: " + key);
        }
        return value.getAsString();
    }

    private static void deleteTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            Data_Energistics.LOGGER.error("Failed to delete temporary client preference file {}",
                    temporary, cleanupFailure);
        }
    }

    private static final class ServerProfile {

        private long lastAccessEpochMillis;
        private final Map<String, PatternProviderClickStatistic> statistics = new LinkedHashMap<>();

        private ServerProfile(long lastAccessEpochMillis) {
            this.lastAccessEpochMillis = lastAccessEpochMillis;
        }
    }

    private static final class FutureSchemaException extends RuntimeException {

        private FutureSchemaException(int version) {
            super("Client preference schema " + version + " is newer than supported schema " + SCHEMA_VERSION);
        }
    }
}
