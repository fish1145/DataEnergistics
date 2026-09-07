package com.fish_dan_.data_energistics.world.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCanonicalNbt;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityDataCoreStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.google.common.hash.Hashing;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * World-level storage contents for Trinity Data Core hosts, keyed by the storage UUID carried by the host item.
 */
public class TrinityDataCoreStorageSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_trinity_data_core_storage";
    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 2;
    private static final int INVENTORY_ONLY_SCHEMA_VERSION = 1;
    private static final String DETACHED_RUNTIMES_TAG = "detached_cpu_runtimes";
    private static final String HOSTS_TAG = "hosts";
    private static final String HOST_ID_TAG = "host_id";
    private static final String ENTRIES_TAG = "entries";
    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Factory<TrinityDataCoreStorageSavedData> FACTORY = new Factory<>(
            TrinityDataCoreStorageSavedData::new,
            TrinityDataCoreStorageSavedData::load);

    private final Object2ObjectOpenHashMap<UUID, HostState> hosts = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<RecoveryKey, DetachedRuntime> detachedRuntimes = new Object2ObjectLinkedOpenHashMap<>();
    private final List<Tag> quarantinedRuntimeRecords = new ObjectArrayList<>();

    public static TrinityDataCoreStorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static TrinityDataCoreStorageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        if (!tag.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            LOGGER.warn("Ignoring Trinity Data Core storage SavedData without a schema version");
            return data;
        }
        int schemaVersion = tag.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != INVENTORY_ONLY_SCHEMA_VERSION && schemaVersion != SCHEMA_VERSION) {
            LOGGER.warn(
                    "Ignoring Trinity Data Core storage SavedData schema version {}; expected {}",
                    schemaVersion,
                    SCHEMA_VERSION);
            return data;
        }
        if (schemaVersion == SCHEMA_VERSION) {
            data.readDetachedRuntimes(tag);
        }
        Tag hostsTag = tag.get(HOSTS_TAG);
        if (!(hostsTag instanceof ListTag hostList)) {
            return data;
        }

        for (Tag hostEntryTag : hostList) {
            if (!(hostEntryTag instanceof CompoundTag hostEntry)) {
                continue;
            }
            UUID hostId = readHostId(hostEntry);
            if (hostId == null) {
                continue;
            }

            HostState hostState = new HostState();
            Tag entriesTag = hostEntry.get(ENTRIES_TAG);
            if (entriesTag instanceof ListTag entryList) {
                readEntries(registries, hostId, entryList, hostState);
            }
            if (!hostState.isEmpty()) {
                data.hosts.put(hostId, hostState);
            }
        }
        return data;
    }

    public long insert(UUID hostId, AEKey key, long amount, Actionable mode) {
        return insert(hostId, key, amount, mode, TrinityDataCoreStorageProfile.UNLIMITED);
    }

    public long insert(UUID hostId,
                       AEKey key,
                       long amount,
                       Actionable mode,
                       TrinityDataCoreStorageProfile profile) {
        if (amount <= 0L) {
            return 0L;
        }
        long acceptedAmount = acceptedInsertAmount(hostId, key, amount, profile);
        if (acceptedAmount <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            HostState hostState = this.hosts.computeIfAbsent(hostId, ignored -> new HostState());
            hostState.insert(key, acceptedAmount);
            setDirty();
        }
        return acceptedAmount;
    }

    public long extract(UUID hostId, AEKey key, long amount, Actionable mode) {
        if (amount <= 0L) {
            return 0L;
        }

        HostState hostState = this.hosts.get(hostId);
        if (hostState == null) {
            return 0L;
        }
        BigInteger current = hostState.amount(key);
        if (current.signum() <= 0) {
            return 0L;
        }

        BigInteger extracted = current.min(BigInteger.valueOf(amount));
        if (mode == Actionable.MODULATE) {
            hostState.extract(key, current, extracted);
            if (hostState.isEmpty()) {
                this.hosts.remove(hostId);
            }
            setDirty();
        }
        return extracted.longValue();
    }

    public BigInteger amount(UUID hostId, AEKey key) {
        HostState hostState = this.hosts.get(hostId);
        return hostState == null ? BigInteger.ZERO : hostState.amount(key);
    }

    public StorageSummary summary(UUID hostId) {
        HostState hostState = this.hosts.get(hostId);
        return hostState == null ? StorageSummary.EMPTY : hostState.summary();
    }

    /**
     * Combines the exact stored contents with the host's current capacity profile for state consumers.
     */
    public TrinityDataCoreStorageStatus storageStatus(UUID hostId, TrinityDataCoreStorageProfile profile) {
        if (profile == null) {
            throw new NullPointerException("Storage profile must not be null");
        }
        StorageSummary summary = summary(hostId);
        return new TrinityDataCoreStorageStatus(
                summary.typeCount(),
                profile.typeCapacity(),
                summary.itemAmount(),
                summary.fluidAmount(),
                summary.otherKeyAmount(),
                profile.totalCapacity(),
                profile.unlimited());
    }

    /**
     * Captures the authoritative capacity and exact contents in one immutable UI synchronization frame.
     */
    public TrinityDataCoreStorageView storageView(UUID hostId,
                                                  TrinityDataCoreStorageProfile profile,
                                                  int firstEntry) {
        TrinityDataCoreStorageStatus status = storageStatus(hostId, profile);
        HostState hostState = this.hosts.get(hostId);
        if (hostState == null) {
            return new TrinityDataCoreStorageView(status, 0, List.of());
        }
        return hostState.storageView(status, firstEntry);
    }

    private long acceptedInsertAmount(UUID hostId, AEKey key, long amount, TrinityDataCoreStorageProfile profile) {
        if (profile.unlimited()) {
            return amount;
        }
        if (!profile.available()) {
            return 0L;
        }

        HostState hostState = this.hosts.get(hostId);
        if (hostState == null) {
            return BigInteger.valueOf(amount).min(profile.totalCapacity()).longValue();
        }
        if (hostState.amount(key).signum() <= 0 && hostState.typeCount() >= profile.typeCapacity()) {
            return 0L;
        }

        BigInteger remainingCapacity = profile.totalCapacity().subtract(hostState.totalAmount());
        if (remainingCapacity.signum() <= 0) {
            return 0L;
        }

        BigInteger accepted = BigInteger.valueOf(amount).min(remainingCapacity);
        return accepted.longValue();
    }

    public void addAvailableStacks(UUID hostId, KeyCounter out) {
        HostState hostState = this.hosts.get(hostId);
        if (hostState == null) {
            return;
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> entry : hostState.entries().object2ObjectEntrySet()) {
            out.add(entry.getKey(), saturatingLong(entry.getValue()));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        ListTag hostList = new ListTag();
        for (Object2ObjectMap.Entry<UUID, HostState> hostEntry : this.hosts.object2ObjectEntrySet()) {
            CompoundTag hostTag = new CompoundTag();
            hostTag.putUUID(HOST_ID_TAG, hostEntry.getKey());
            ListTag entryList = new ListTag();
            for (Map.Entry<AEKey, BigInteger> storageEntry : hostEntry.getValue().entries().entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(KEY_TAG, storageEntry.getKey().toTagGeneric(registries));
                entryTag.putString(AMOUNT_TAG, storageEntry.getValue().toString());
                entryList.add(entryTag);
            }
            if (!entryList.isEmpty()) {
                hostTag.put(ENTRIES_TAG, entryList);
                hostList.add(hostTag);
            }
        }
        tag.put(HOSTS_TAG, hostList);
        ListTag journal = new ListTag();
        for (var entry : this.detachedRuntimes.object2ObjectEntrySet()) {
            journal.add(writeDetachedRuntime(entry.getKey(), entry.getValue()));
        }
        for (Tag quarantined : this.quarantinedRuntimeRecords) {
            CompoundTag preserved = new CompoundTag();
            preserved.put("quarantine_raw", quarantined.copy());
            journal.add(preserved);
        }
        tag.put(DETACHED_RUNTIMES_TAG, journal);
        return tag;
    }

    /** Full host identity and one removal generation; old item copies cannot claim a later generation. */
    public record RecoveryKey(UUID hostId, UUID storageId, UUID removalToken) {}

    /** CLAIMED is recorded before the importer callback; no non-AVAILABLE state automatically grants assets again. */
    public enum RecoveryStatus {
        AVAILABLE,
        CLAIMED,
        RESTORED,
        UNVERIFIED,
        FAILED
    }

    /** Read-only forensic copy. The original snapshot remains in SavedData after both success and failure. */
    public record RecoverySnapshot(RecoveryKey key, RecoveryStatus status, String fingerprint,
                                   @Nullable UUID claimant, String failure, CompoundTag runtime) {

        public RecoverySnapshot {
            runtime = runtime.copy();
        }

        @Override
        public CompoundTag runtime() {
            return runtime.copy();
        }
    }

    /** Stores only the post-cancellation, post-local-recovery remainder. Repeated identical removal is a no-op. */
    public void storeDetachedRuntime(RecoveryKey key, CompoundTag runtime) {
        String fingerprint = runtimeFingerprint(runtime);
        DetachedRuntime previous = this.detachedRuntimes.get(key);
        if (previous != null) {
            if (!previous.fingerprint.equals(fingerprint)) {
                previous.status = RecoveryStatus.UNVERIFIED;
                previous.failure = "Conflicting snapshots for the same host removal token";
                this.quarantinedRuntimeRecords.add(writeDetachedRuntime(key, new DetachedRuntime(runtime, fingerprint)));
                setDirty();
                throw new IllegalStateException(previous.failure);
            }
            return;
        }
        this.detachedRuntimes.put(key, new DetachedRuntime(runtime, fingerprint));
        setDirty();
    }

    /** Preserves a host-level ambiguous source without making its local assets automatically claimable. */
    public void quarantineDetachedRuntime(RecoveryKey key, CompoundTag runtime, String reason) {
        DetachedRuntime evidence = new DetachedRuntime(runtime, runtimeFingerprint(runtime));
        evidence.status = RecoveryStatus.UNVERIFIED;
        evidence.failure = reason;
        DetachedRuntime previous = this.detachedRuntimes.putIfAbsent(key, evidence);
        if (previous != null) {
            if (!previous.fingerprint.equals(evidence.fingerprint)) {
                this.quarantinedRuntimeRecords.add(writeDetachedRuntime(key, evidence));
            }
            previous.status = RecoveryStatus.UNVERIFIED;
            previous.failure = reason;
        }
        setDirty();
    }

    /**
     * Invokes import at most once for this journal generation. setDirty is not a synchronous disk transaction;
     * source snapshots and claim evidence are retained so an interrupted or failed transfer remains diagnosable.
     * The importer returns true only after checking that its restored worker state preserves the supplied snapshot.
     */
    public Optional<RecoveryStatus> claimDetachedRuntime(RecoveryKey key, UUID claimant, Predicate<CompoundTag> importer) {
        DetachedRuntime entry = this.detachedRuntimes.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.status != RecoveryStatus.AVAILABLE) {
            return Optional.of(entry.status);
        }
        entry.claimant = claimant;
        entry.status = RecoveryStatus.CLAIMED;
        setDirty();
        try {
            boolean verified = importer.test(entry.runtime.copy());
            if (entry.status != RecoveryStatus.CLAIMED) {
                return Optional.of(entry.status);
            }
            if (verified) {
                entry.status = RecoveryStatus.RESTORED;
            } else {
                entry.status = RecoveryStatus.UNVERIFIED;
                entry.failure = "Restored CPU runtime differs from the retained removal snapshot";
            }
        } catch (RuntimeException exception) {
            entry.status = RecoveryStatus.FAILED;
            entry.failure = exception.toString();
            LOGGER.error("Failed to restore detached Trinity CPU state for host {} storage {} removal {}; snapshot retained",
                    key.hostId(), key.storageId(), key.removalToken(), exception);
        }
        setDirty();
        return Optional.of(entry.status);
    }

    public Optional<RecoverySnapshot> detachedRuntime(RecoveryKey key) {
        DetachedRuntime entry = this.detachedRuntimes.get(key);
        return entry == null ? Optional.empty() : Optional.of(new RecoverySnapshot(key, entry.status, entry.fingerprint,
                entry.claimant, entry.failure, entry.runtime));
    }

    /** A reloaded source or unproven recipient must not resume custody also retained by a removal journal. */
    public boolean requiresCpuRecoveryReconciliation(UUID hostId, UUID storageId, @Nullable UUID appliedToken, UUID claimant) {
        RecoveryKey latestKey = null;
        DetachedRuntime latest = null;
        for (var entry : this.detachedRuntimes.object2ObjectEntrySet()) {
            if (entry.getKey().hostId().equals(hostId) && entry.getKey().storageId().equals(storageId)) {
                latestKey = entry.getKey();
                latest = entry.getValue();
            }
        }
        return latest != null && !(latestKey.removalToken().equals(appliedToken) && latest.status == RecoveryStatus.RESTORED &&
                claimant.equals(latest.claimant));
    }

    public static String runtimeFingerprint(CompoundTag runtime) {
        return Hashing.sha256().hashString(TrinityCanonicalNbt.encode(runtime), StandardCharsets.UTF_8).toString();
    }

    private void readDetachedRuntimes(CompoundTag tag) {
        if (!(tag.get(DETACHED_RUNTIMES_TAG) instanceof ListTag entries)) {
            Tag damaged = tag.get(DETACHED_RUNTIMES_TAG);
            CompoundTag evidence = new CompoundTag();
            if (damaged != null) {
                evidence.put("invalid_journal", damaged.copy());
            } else {
                evidence.putBoolean("missing_journal", true);
            }
            this.quarantinedRuntimeRecords.add(evidence);
            LOGGER.error("Trinity storage schema two has a missing or malformed detached CPU journal; retaining evidence");
            return;
        }
        for (Tag raw : entries) {
            try {
                if (raw instanceof CompoundTag preserved && preserved.getAllKeys().size() == 1 && preserved.contains("quarantine_raw")) {
                    this.quarantinedRuntimeRecords.add(preserved.get("quarantine_raw").copy());
                    continue;
                }
                if (!(raw instanceof CompoundTag entry) || !entry.hasUUID("host") || !entry.hasUUID("storage") ||
                        !entry.hasUUID("removal") || !entry.contains("status", Tag.TAG_STRING) ||
                        !entry.contains("fingerprint", Tag.TAG_STRING) || !entry.contains("failure", Tag.TAG_STRING) ||
                        !(entry.get("runtime") instanceof CompoundTag runtime)) {
                    throw new IllegalArgumentException("Incomplete detached CPU journal entry");
                }
                RecoveryKey key = new RecoveryKey(entry.getUUID("host"), entry.getUUID("storage"), entry.getUUID("removal"));
                DetachedRuntime restored = new DetachedRuntime(runtime, entry.getString("fingerprint"));
                restored.status = RecoveryStatus.valueOf(entry.getString("status"));
                restored.failure = entry.getString("failure");
                if (entry.contains("claimant")) {
                    if (!entry.hasUUID("claimant")) {
                        throw new IllegalArgumentException("Invalid detached CPU claimant identity");
                    }
                    restored.claimant = entry.getUUID("claimant");
                }
                if (restored.status == RecoveryStatus.AVAILABLE && restored.claimant != null ||
                        (restored.status == RecoveryStatus.CLAIMED || restored.status == RecoveryStatus.RESTORED ||
                                restored.status == RecoveryStatus.FAILED) && restored.claimant == null) {
                    throw new IllegalArgumentException("Detached CPU journal status contradicts its claim marker");
                }
                if (!runtimeFingerprint(runtime).equals(restored.fingerprint)) {
                    restored.status = RecoveryStatus.UNVERIFIED;
                    restored.failure = "Detached CPU snapshot fingerprint mismatch";
                }
                DetachedRuntime duplicate = this.detachedRuntimes.putIfAbsent(key, restored);
                if (duplicate != null) {
                    duplicate.status = RecoveryStatus.UNVERIFIED;
                    duplicate.failure = "Duplicate detached CPU journal identity";
                    throw new IllegalArgumentException(duplicate.failure);
                }
            } catch (IllegalArgumentException exception) {
                this.quarantinedRuntimeRecords.add(raw.copy());
                LOGGER.error("Retaining malformed detached Trinity CPU journal entry without granting its assets", exception);
            }
        }
    }

    private static CompoundTag writeDetachedRuntime(RecoveryKey key, DetachedRuntime entry) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("host", key.hostId());
        tag.putUUID("storage", key.storageId());
        tag.putUUID("removal", key.removalToken());
        tag.putString("status", entry.status.name());
        tag.putString("fingerprint", entry.fingerprint);
        tag.putString("failure", entry.failure);
        tag.put("runtime", entry.runtime.copy());
        if (entry.claimant != null) {
            tag.putUUID("claimant", entry.claimant);
        }
        return tag;
    }

    private static final class DetachedRuntime {

        private final CompoundTag runtime;
        private final String fingerprint;
        private RecoveryStatus status = RecoveryStatus.AVAILABLE;
        private @Nullable UUID claimant;
        private String failure = "";

        private DetachedRuntime(CompoundTag runtime, String fingerprint) {
            this.runtime = runtime.copy();
            this.fingerprint = fingerprint;
        }
    }

    private static void readEntries(HolderLookup.Provider registries,
                                    UUID hostId,
                                    ListTag entryList,
                                    HostState hostState) {
        for (Tag entryTag : entryList) {
            if (!(entryTag instanceof CompoundTag entry)) {
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(KEY_TAG));
            BigInteger amount = readAmount(hostId, entry);
            if (key != null && amount.signum() > 0) {
                hostState.putLoaded(key, amount);
            }
        }
    }

    private static UUID readHostId(CompoundTag tag) {
        if (!tag.hasUUID(HOST_ID_TAG)) {
            LOGGER.warn("Trinity Data Core storage entry is missing a valid storage id");
            return null;
        }
        return tag.getUUID(HOST_ID_TAG);
    }

    private static BigInteger readAmount(UUID hostId, CompoundTag tag) {
        String rawAmount = tag.getString(AMOUNT_TAG);
        if (rawAmount.isBlank()) {
            LOGGER.warn("Trinity Data Core storage entry for {} is missing amount", hostId);
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(rawAmount);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Trinity Data Core storage entry for {} has invalid amount '{}'", hostId, rawAmount, exception);
            return BigInteger.ZERO;
        }
    }

    private static long saturatingLong(BigInteger amount) {
        return amount.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValue();
    }

    private static final class HostState {

        private static final Comparator<TrinityDataCoreStorageView.Entry> ENTRY_ORDER = Comparator
                .comparing(TrinityDataCoreStorageView.Entry::amount)
                .reversed()
                .thenComparing(entry -> entry.key().getType().getId().toString())
                .thenComparing(entry -> entry.key().toString());

        private final Object2ObjectOpenHashMap<AEKey, BigInteger> entries = new Object2ObjectOpenHashMap<>();
        private BigInteger itemAmount = BigInteger.ZERO;
        private BigInteger fluidAmount = BigInteger.ZERO;
        private BigInteger otherKeyAmount = BigInteger.ZERO;
        private List<TrinityDataCoreStorageView.Entry> cachedOrderedEntries;

        private void insert(AEKey key, long amount) {
            BigInteger insertedAmount = BigInteger.valueOf(amount);
            this.entries.put(
                    key,
                    this.entries.getOrDefault(key, BigInteger.ZERO).add(insertedAmount));
            addCategoryAmount(key, insertedAmount);
            invalidateView();
        }

        private void extract(AEKey key, BigInteger currentAmount, BigInteger extractedAmount) {
            BigInteger remaining = currentAmount.subtract(extractedAmount);
            if (remaining.signum() <= 0) {
                this.entries.remove(key);
            } else {
                this.entries.put(key, remaining);
            }
            subtractCategoryAmount(key, extractedAmount);
            invalidateView();
        }

        private void putLoaded(AEKey key, BigInteger amount) {
            BigInteger previous = this.entries.put(key, amount);
            if (previous != null) {
                subtractCategoryAmount(key, previous);
            }
            addCategoryAmount(key, amount);
            invalidateView();
        }

        private BigInteger amount(AEKey key) {
            return this.entries.getOrDefault(key, BigInteger.ZERO);
        }

        private StorageSummary summary() {
            return new StorageSummary(
                    this.entries.size(),
                    this.itemAmount,
                    this.fluidAmount,
                    this.otherKeyAmount);
        }

        private TrinityDataCoreStorageView storageView(TrinityDataCoreStorageStatus status,
                                                       int requestedFirstEntry) {
            List<TrinityDataCoreStorageView.Entry> orderedEntries = orderedEntries();
            int firstEntry = TrinityDataCoreStorageView.normalizeFirstEntry(
                    requestedFirstEntry,
                    orderedEntries.size());
            int lastEntry = Math.min(orderedEntries.size(), firstEntry + TrinityDataCoreStorageView.PAGE_SIZE);
            return new TrinityDataCoreStorageView(
                    status,
                    firstEntry,
                    orderedEntries.subList(firstEntry, lastEntry));
        }

        private List<TrinityDataCoreStorageView.Entry> orderedEntries() {
            if (this.cachedOrderedEntries != null) {
                return this.cachedOrderedEntries;
            }
            List<TrinityDataCoreStorageView.Entry> snapshot = this.entries.object2ObjectEntrySet().stream()
                    .map(entry -> new TrinityDataCoreStorageView.Entry(entry.getKey(), entry.getValue()))
                    .sorted(ENTRY_ORDER)
                    .toList();
            this.cachedOrderedEntries = snapshot;
            return snapshot;
        }

        private void invalidateView() {
            this.cachedOrderedEntries = null;
        }

        private boolean isEmpty() {
            return this.entries.isEmpty();
        }

        private Object2ObjectOpenHashMap<AEKey, BigInteger> entries() {
            return this.entries;
        }

        private int typeCount() {
            return this.entries.size();
        }

        private BigInteger totalAmount() {
            return this.itemAmount.add(this.fluidAmount).add(this.otherKeyAmount);
        }

        private void addCategoryAmount(AEKey key, BigInteger amount) {
            if (key instanceof AEItemKey) {
                this.itemAmount = this.itemAmount.add(amount);
            } else if (key instanceof AEFluidKey) {
                this.fluidAmount = this.fluidAmount.add(amount);
            } else {
                this.otherKeyAmount = this.otherKeyAmount.add(amount);
            }
        }

        private void subtractCategoryAmount(AEKey key, BigInteger amount) {
            if (key instanceof AEItemKey) {
                this.itemAmount = this.itemAmount.subtract(amount);
            } else if (key instanceof AEFluidKey) {
                this.fluidAmount = this.fluidAmount.subtract(amount);
            } else {
                this.otherKeyAmount = this.otherKeyAmount.subtract(amount);
            }
        }
    }

    public record StorageSummary(int typeCount,
                                 BigInteger itemAmount,
                                 BigInteger fluidAmount,
                                 BigInteger otherKeyAmount) {

        public static final StorageSummary EMPTY = new StorageSummary(
                0,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ZERO);

        public BigInteger totalAmount() {
            return this.itemAmount.add(this.fluidAmount).add(this.otherKeyAmount);
        }
    }
}
