package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageProfile;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

/** World-level storage contents for Trinity Data Core hosts, keyed by the storage UUID carried by the host item. */
public class TrinityDataCoreStorageSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_trinity_data_core_storage";
    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 1;
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
        if (schemaVersion != SCHEMA_VERSION) {
            LOGGER.warn(
                    "Ignoring Trinity Data Core storage SavedData schema version {}; expected {}",
                    schemaVersion,
                    SCHEMA_VERSION);
            return data;
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
        return tag;
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

        private final Object2ObjectOpenHashMap<AEKey, BigInteger> entries = new Object2ObjectOpenHashMap<>();
        private int typeCount;
        private BigInteger totalAmount = BigInteger.ZERO;

        private void insert(AEKey key, long amount) {
            BigInteger insertedAmount = BigInteger.valueOf(amount);
            BigInteger current = this.entries.put(
                    key,
                    this.entries.getOrDefault(key, BigInteger.ZERO).add(insertedAmount));
            if (current == null) {
                this.typeCount++;
            }
            this.totalAmount = this.totalAmount.add(insertedAmount);
        }

        private void extract(AEKey key, BigInteger currentAmount, BigInteger extractedAmount) {
            BigInteger remaining = currentAmount.subtract(extractedAmount);
            if (remaining.signum() <= 0) {
                this.entries.remove(key);
                this.typeCount--;
            } else {
                this.entries.put(key, remaining);
            }
            this.totalAmount = this.totalAmount.subtract(extractedAmount);
        }

        private void putLoaded(AEKey key, BigInteger amount) {
            BigInteger previous = this.entries.put(key, amount);
            if (previous == null) {
                this.typeCount++;
            } else {
                this.totalAmount = this.totalAmount.subtract(previous);
            }
            this.totalAmount = this.totalAmount.add(amount);
        }

        private BigInteger amount(AEKey key) {
            return this.entries.getOrDefault(key, BigInteger.ZERO);
        }

        private StorageSummary summary() {
            return new StorageSummary(this.typeCount, this.totalAmount.toString());
        }

        private boolean isEmpty() {
            return this.typeCount == 0;
        }

        private Object2ObjectOpenHashMap<AEKey, BigInteger> entries() {
            return this.entries;
        }

        private int typeCount() {
            return this.typeCount;
        }

        private BigInteger totalAmount() {
            return this.totalAmount;
        }
    }

    public record StorageSummary(int typeCount, String totalAmount) {

        public static final StorageSummary EMPTY = new StorageSummary(0, "0");
    }
}
