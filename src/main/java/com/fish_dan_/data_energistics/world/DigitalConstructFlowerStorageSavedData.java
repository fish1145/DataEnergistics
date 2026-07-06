package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.DigitalConstructFlowerStorageProfile;

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
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * World-level storage contents for Digital Construct Flower hosts, keyed by the host UUID saved on the dropped item.
 */
public class DigitalConstructFlowerStorageSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_digital_construct_flower_storage";
    private static final String HOSTS_TAG = "hosts";
    private static final String HOST_ID_TAG = "host_id";
    private static final String ENTRIES_TAG = "entries";
    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Factory<DigitalConstructFlowerStorageSavedData> FACTORY = new Factory<>(
            DigitalConstructFlowerStorageSavedData::new,
            DigitalConstructFlowerStorageSavedData::load);

    private final Object2ObjectOpenHashMap<UUID, Object2ObjectOpenHashMap<AEKey, BigInteger>> hosts = new Object2ObjectOpenHashMap<>();

    public static DigitalConstructFlowerStorageSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static DigitalConstructFlowerStorageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DigitalConstructFlowerStorageSavedData data = new DigitalConstructFlowerStorageSavedData();
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

            Object2ObjectOpenHashMap<AEKey, BigInteger> entries = new Object2ObjectOpenHashMap<>();
            Tag entriesTag = hostEntry.get(ENTRIES_TAG);
            if (entriesTag instanceof ListTag entryList) {
                readEntries(registries, hostId, entryList, entries);
            }
            if (!entries.isEmpty()) {
                data.hosts.put(hostId, entries);
            }
        }
        return data;
    }

    public long insert(UUID hostId, AEKey key, long amount, Actionable mode) {
        return insert(hostId, key, amount, mode, DigitalConstructFlowerStorageProfile.UNLIMITED);
    }

    public long insert(UUID hostId,
                       AEKey key,
                       long amount,
                       Actionable mode,
                       DigitalConstructFlowerStorageProfile profile) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(profile, "profile");
        if (amount <= 0L) {
            return 0L;
        }
        long acceptedAmount = acceptedInsertAmount(hostId, key, amount, profile);
        if (acceptedAmount <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            Object2ObjectOpenHashMap<AEKey, BigInteger> entries = this.hosts.computeIfAbsent(
                    hostId,
                    ignored -> new Object2ObjectOpenHashMap<>());
            BigInteger current = entries.getOrDefault(key, BigInteger.ZERO);
            entries.put(key, current.add(BigInteger.valueOf(acceptedAmount)));
            setDirty();
        }
        return acceptedAmount;
    }

    public long extract(UUID hostId, AEKey key, long amount, Actionable mode) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mode, "mode");
        if (amount <= 0L) {
            return 0L;
        }

        Object2ObjectOpenHashMap<AEKey, BigInteger> entries = this.hosts.get(hostId);
        if (entries == null) {
            return 0L;
        }
        BigInteger current = entries.getOrDefault(key, BigInteger.ZERO);
        if (current.signum() <= 0) {
            return 0L;
        }

        BigInteger extracted = current.min(BigInteger.valueOf(amount));
        if (mode == Actionable.MODULATE) {
            BigInteger remaining = current.subtract(extracted);
            if (remaining.signum() <= 0) {
                entries.remove(key);
                if (entries.isEmpty()) {
                    this.hosts.remove(hostId);
                }
            } else {
                entries.put(key, remaining);
            }
            setDirty();
        }
        return extracted.longValue();
    }

    public BigInteger amount(UUID hostId, AEKey key) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(key, "key");
        Object2ObjectOpenHashMap<AEKey, BigInteger> entries = this.hosts.get(hostId);
        return entries == null ? BigInteger.ZERO : entries.getOrDefault(key, BigInteger.ZERO);
    }

    public StorageSummary summary(UUID hostId) {
        Objects.requireNonNull(hostId, "hostId");
        Object2ObjectOpenHashMap<AEKey, BigInteger> entries = this.hosts.get(hostId);
        if (entries == null || entries.isEmpty()) {
            return StorageSummary.EMPTY;
        }
        BigInteger total = BigInteger.ZERO;
        int typeCount = 0;
        for (Object2ObjectMap.Entry<AEKey, BigInteger> entry : entries.object2ObjectEntrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.signum() <= 0) {
                continue;
            }
            typeCount++;
            total = total.add(amount);
        }
        return typeCount == 0 ? StorageSummary.EMPTY : new StorageSummary(typeCount, total.toString());
    }

    private long acceptedInsertAmount(UUID hostId, AEKey key, long amount, DigitalConstructFlowerStorageProfile profile) {
        if (profile.unlimited()) {
            return amount;
        }
        if (!profile.available()) {
            return 0L;
        }

        Object2ObjectOpenHashMap<AEKey, BigInteger> entries = this.hosts.get(hostId);
        BigInteger current = entries == null ? BigInteger.ZERO : entries.getOrDefault(key, BigInteger.ZERO);
        if (current.signum() <= 0 && positiveTypeCount(entries) >= profile.typeCapacity()) {
            return 0L;
        }

        BigInteger remainingCapacity = profile.totalCapacity().subtract(totalAmount(entries));
        if (remainingCapacity.signum() <= 0) {
            return 0L;
        }

        BigInteger accepted = BigInteger.valueOf(amount).min(remainingCapacity);
        return accepted.longValue();
    }

    private static int positiveTypeCount(@Nullable Object2ObjectOpenHashMap<AEKey, BigInteger> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int typeCount = 0;
        for (BigInteger amount : entries.values()) {
            if (amount.signum() > 0) {
                typeCount++;
            }
        }
        return typeCount;
    }

    private static BigInteger totalAmount(@Nullable Object2ObjectOpenHashMap<AEKey, BigInteger> entries) {
        if (entries == null || entries.isEmpty()) {
            return BigInteger.ZERO;
        }
        BigInteger total = BigInteger.ZERO;
        for (BigInteger amount : entries.values()) {
            if (amount.signum() > 0) {
                total = total.add(amount);
            }
        }
        return total;
    }

    public void addAvailableStacks(UUID hostId, KeyCounter out) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(out, "out");
        Object2ObjectOpenHashMap<AEKey, BigInteger> entries = this.hosts.get(hostId);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> entry : entries.object2ObjectEntrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.signum() > 0) {
                out.add(entry.getKey(), saturatingLong(amount));
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag hostList = new ListTag();
        for (Object2ObjectMap.Entry<UUID, Object2ObjectOpenHashMap<AEKey, BigInteger>> hostEntry : this.hosts.object2ObjectEntrySet()) {
            CompoundTag hostTag = new CompoundTag();
            hostTag.putString(HOST_ID_TAG, hostEntry.getKey().toString());
            ListTag entryList = new ListTag();
            for (Map.Entry<AEKey, BigInteger> storageEntry : hostEntry.getValue().entrySet()) {
                BigInteger amount = storageEntry.getValue();
                if (amount.signum() <= 0) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(KEY_TAG, storageEntry.getKey().toTagGeneric(registries));
                entryTag.putString(AMOUNT_TAG, amount.toString());
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
                                    Object2ObjectOpenHashMap<AEKey, BigInteger> entries) {
        for (Tag entryTag : entryList) {
            if (!(entryTag instanceof CompoundTag entry)) {
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(KEY_TAG));
            BigInteger amount = readAmount(hostId, entry);
            if (key != null && amount.signum() > 0) {
                entries.put(key, amount);
            }
        }
    }

    private static UUID readHostId(CompoundTag tag) {
        String rawId = tag.getString(HOST_ID_TAG);
        if (rawId.isBlank()) {
            LOGGER.warn("Digital Construct Flower storage entry is missing host id");
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Digital Construct Flower storage entry has invalid host id '{}'", rawId, exception);
            return null;
        }
    }

    private static BigInteger readAmount(UUID hostId, CompoundTag tag) {
        String rawAmount = tag.getString(AMOUNT_TAG);
        if (rawAmount.isBlank()) {
            LOGGER.warn("Digital Construct Flower storage entry for {} is missing amount", hostId);
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(rawAmount);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Digital Construct Flower storage entry for {} has invalid amount '{}'", hostId, rawAmount, exception);
            return BigInteger.ZERO;
        }
    }

    private static long saturatingLong(BigInteger amount) {
        return amount.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValue();
    }

    public record StorageSummary(int typeCount, String totalAmount) {

        public static final StorageSummary EMPTY = new StorageSummary(0, "0");
    }
}
