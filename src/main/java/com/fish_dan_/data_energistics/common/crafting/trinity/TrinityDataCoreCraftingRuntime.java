package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runtime CPU container owned by a Trinity Data Core block entity.
 *
 * <p>
 * The runtime keeps structure contribution data separate from AE2's crafting service and exposes resolved virtual CPU
 * partitions for mixin integration.
 */
public final class TrinityDataCoreCraftingRuntime {

    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 1;
    private static final String CONTRIBUTIONS_TAG = "contributions";
    private static final String CONTRIBUTION_NAME_TAG = "name";
    private static final String STORAGE_BYTES_TAG = "storage_bytes";
    private static final String CO_PROCESSORS_TAG = "co_processors";
    private static final String PARTITION_COUNT_TAG = "partition_count";
    private static final String SELECTION_MODE_TAG = "selection_mode";
    private static final String PARTITIONS_TAG = "partitions";
    private static final String PARTITION_INDEX_TAG = "index";
    private static final String PARTITION_LOGIC_TAG = "logic";

    private final DigitalConstructFlowerBlockEntity host;
    private final Map<String, TrinityDataCoreCpuContribution> externalContributions = new TreeMap<>();
    private final List<TrinityDataCoreVirtualCpu> partitions = new ArrayList<>();
    private TrinityDataCoreCpuProfile profile = TrinityDataCoreCpuProfile.EMPTY;
    private int activePartitionCount;
    private boolean mainStructureFormed;
    private boolean paused;
    private ListTag pendingPartitionLogic;

    public TrinityDataCoreCraftingRuntime(DigitalConstructFlowerBlockEntity host) {
        this.host = host;
    }

    /**
     * Updates whether child structure CPU contributions are active.
     *
     * @param formed true when the main structure is formed
     */
    public void setMainStructureFormed(boolean formed) {
        if (this.mainStructureFormed == formed) {
            rebuildPartitions();
            return;
        }
        this.mainStructureFormed = formed;
        rebuildPartitions();
    }

    /**
     * Pauses or resumes execution without discarding jobs or their inventories.
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Cancels every active job. This is reserved for permanent host removal.
     */
    public void cancelAllJobs() {
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            cpu.cancelJob();
        }
    }

    /**
     * Returns whether at least one partition currently owns a job.
     */
    public boolean hasBusyJobs() {
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            if (cpu.isBusy()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds or replaces CPU data contributed by a named child structure.
     *
     * @param structureName structure name
     * @param contribution  contribution data
     */
    public void setContribution(String structureName, TrinityDataCoreCpuContribution contribution) {
        this.externalContributions.put(requireStructureName(structureName), contribution);
        rebuildPartitions();
    }

    /**
     * Clears CPU data contributed by a named child structure.
     *
     * @param structureName structure name
     */
    public void clearContribution(String structureName) {
        this.externalContributions.remove(requireStructureName(structureName));
        rebuildPartitions();
    }

    /**
     * Returns whether a named child structure currently has stored CPU data.
     *
     * @param structureName structure name
     * @return true when contribution data exists for the structure
     */
    public boolean hasContribution(String structureName) {
        return this.externalContributions.containsKey(requireStructureName(structureName));
    }

    /**
     * @return active virtual CPU partitions owned by the formed host
     */
    public List<TrinityDataCoreVirtualCpu> partitions() {
        if (!this.mainStructureFormed || this.activePartitionCount == 0) {
            return List.of();
        }
        return List.copyOf(activePartitions());
    }

    /**
     * @return current aggregate profile
     */
    public TrinityDataCoreCpuProfile profile() {
        return this.profile;
    }

    /**
     * Ticks every virtual CPU partition from AE2's crafting service.
     *
     * @param energyService   AE2 energy service
     * @param craftingService AE2 crafting service
     */
    public void tick(IEnergyService energyService, CraftingService craftingService) {
        if (this.paused) {
            return;
        }
        for (TrinityDataCoreVirtualCpu cpu : activePartitions()) {
            cpu.tick(energyService, craftingService);
        }
    }

    /**
     * Inserts returned crafting outputs into the virtual CPU partitions.
     *
     * @param what     key to insert
     * @param amount   amount to insert
     * @param mode     simulation or mutation mode
     * @param inserted amount already inserted by earlier CPU providers
     * @return total inserted amount
     */
    public long insertIntoCpus(AEKey what, long amount, Actionable mode, long inserted) {
        long totalInserted = inserted;
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            if (totalInserted >= amount) {
                break;
            }
            totalInserted += cpu.insert(what, amount - totalInserted, mode);
        }
        return totalInserted;
    }

    /**
     * Adds all currently awaited keys to AE2's request set.
     *
     * @param waitingFor output set
     */
    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            cpu.getAllWaitingFor(waitingFor);
        }
    }

    /**
     * @param what requested key
     * @return amount all partitions are waiting for
     */
    public long getRequestedAmount(AEKey what) {
        long requested = 0L;
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            requested += cpu.getWaitingFor(what);
        }
        return requested;
    }

    /**
     * @param cpu CPU instance to check
     * @return true when the CPU belongs to this runtime
     */
    public boolean hasCpu(Object cpu) {
        for (TrinityDataCoreVirtualCpu partition : this.partitions) {
            if (partition == cpu) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return latest crafting-visible change tick across all partitions
     */
    public long getLastModifiedOnTick() {
        long latest = 0L;
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            latest = Math.max(latest, cpu.getLastModifiedOnTick());
        }
        return latest;
    }

    /**
     * Re-registers persisted crafting links after AE2 refreshes its CPU list.
     *
     * @param service AE2 crafting service
     */
    public void restoreLinks(CraftingService service) {
        for (TrinityDataCoreVirtualCpu cpu : activePartitions()) {
            ICraftingLink link = cpu.logic().getLastLink();
            if (link instanceof CraftingLink craftingLink) {
                service.addLink(craftingLink);
            }
        }
    }

    /**
     * Serializes runtime contributions and partition state.
     *
     * @param data       destination tag
     * @param registries registry lookup
     */
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        data.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);

        ListTag contributionsTag = new ListTag();
        for (Map.Entry<String, TrinityDataCoreCpuContribution> entry : this.externalContributions.entrySet()) {
            CompoundTag contributionTag = new CompoundTag();
            contributionTag.putString(CONTRIBUTION_NAME_TAG, entry.getKey());
            writeContribution(contributionTag, entry.getValue());
            contributionsTag.add(contributionTag);
        }
        data.put(CONTRIBUTIONS_TAG, contributionsTag);

        ListTag partitionsTag = new ListTag();
        for (TrinityDataCoreVirtualCpu cpu : this.partitions) {
            CompoundTag partitionTag = new CompoundTag();
            partitionTag.putInt(PARTITION_INDEX_TAG, cpu.index());
            partitionTag.putInt(PARTITION_COUNT_TAG, this.partitions.size());
            partitionTag.putLong(STORAGE_BYTES_TAG, cpu.getAvailableStorage());
            partitionTag.putInt(CO_PROCESSORS_TAG, cpu.getCoProcessors());
            partitionTag.putString(SELECTION_MODE_TAG, cpu.getSelectionMode().name());
            partitionTag.put(PARTITION_LOGIC_TAG, cpu.logic().writeToTag(registries));
            partitionsTag.add(partitionTag);
        }
        data.put(PARTITIONS_TAG, partitionsTag);
    }

    /**
     * Restores runtime contributions and stores partition state until partitions are rebuilt.
     *
     * @param data       source tag
     * @param registries registry lookup
     */
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        clearPersistedState();
        if (!data.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            Data_Energistics.LOGGER.warn("Ignoring Trinity Data Core CPU runtime without a schema version");
            return;
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring Trinity Data Core CPU runtime schema version {}; expected {}",
                    schemaVersion,
                    SCHEMA_VERSION);
            return;
        }

        ListTag contributionsTag = data.getList(CONTRIBUTIONS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < contributionsTag.size(); index++) {
            CompoundTag contributionTag = contributionsTag.getCompound(index);
            this.externalContributions.put(
                    requireStructureName(contributionTag.getString(CONTRIBUTION_NAME_TAG)),
                    readContribution(contributionTag));
        }
        this.pendingPartitionLogic = data.getList(PARTITIONS_TAG, Tag.TAG_COMPOUND);
        rebuildPartitions();
        restoreRetainedPartitions();
        restorePendingPartitionLogic(registries);
    }

    private void clearPersistedState() {
        this.externalContributions.clear();
        this.partitions.clear();
        this.profile = TrinityDataCoreCpuProfile.EMPTY;
        this.activePartitionCount = 0;
        this.pendingPartitionLogic = null;
    }

    /**
     * Restores pending partition logic once the owning block entity is attached to a level.
     *
     * @param registries registry lookup
     */
    public void restorePendingPartitionLogic(HolderLookup.Provider registries) {
        applyPendingPartitionLogic(registries);
    }

    private void rebuildPartitions() {
        Map<String, TrinityDataCoreCpuContribution> contributions = new TreeMap<>(this.externalContributions);
        this.profile = TrinityDataCoreCpuProfile.fromContributions(contributions);
        List<TrinityDataCoreCpuPartitionProfile> partitionProfiles = this.profile.partitions();
        this.activePartitionCount = partitionProfiles.size();
        for (TrinityDataCoreCpuPartitionProfile partitionProfile : partitionProfiles) {
            if (partitionProfile.index() < this.partitions.size()) {
                this.partitions.get(partitionProfile.index()).updateProfile(partitionProfile);
            } else {
                this.partitions.add(new TrinityDataCoreVirtualCpu(this.host, partitionProfile));
            }
        }
    }

    private List<TrinityDataCoreVirtualCpu> activePartitions() {
        return this.partitions.subList(0, this.activePartitionCount);
    }

    private void restoreRetainedPartitions() {
        for (int tagIndex = 0; tagIndex < this.pendingPartitionLogic.size(); tagIndex++) {
            CompoundTag partitionTag = this.pendingPartitionLogic.getCompound(tagIndex);
            int cpuIndex = partitionTag.getInt(PARTITION_INDEX_TAG);
            if (cpuIndex < this.partitions.size()) {
                continue;
            }
            if (cpuIndex != this.partitions.size()) {
                Data_Energistics.LOGGER.error(
                        "Cannot restore Trinity CPU partition {} because partition {} is missing",
                        cpuIndex,
                        this.partitions.size());
                continue;
            }
            try {
                this.partitions.add(new TrinityDataCoreVirtualCpu(
                        this.host,
                        new TrinityDataCoreCpuPartitionProfile(
                                cpuIndex,
                                partitionTag.getInt(PARTITION_COUNT_TAG),
                                partitionTag.getLong(STORAGE_BYTES_TAG),
                                partitionTag.getInt(CO_PROCESSORS_TAG),
                                CpuSelectionMode.valueOf(partitionTag.getString(SELECTION_MODE_TAG)))));
            } catch (IllegalArgumentException exception) {
                Data_Energistics.LOGGER.error("Cannot restore Trinity CPU partition {}", cpuIndex, exception);
            }
        }
    }

    private void applyPendingPartitionLogic(HolderLookup.Provider registries) {
        if (this.pendingPartitionLogic == null) {
            return;
        }
        if (this.host.getLevel() == null) {
            return;
        }
        for (int tagIndex = 0; tagIndex < this.pendingPartitionLogic.size(); tagIndex++) {
            CompoundTag partitionTag = this.pendingPartitionLogic.getCompound(tagIndex);
            int cpuIndex = partitionTag.getInt(PARTITION_INDEX_TAG);
            if (cpuIndex >= 0 && cpuIndex < this.partitions.size() && partitionTag.contains(PARTITION_LOGIC_TAG)) {
                this.partitions.get(cpuIndex).logic().readFromTag(partitionTag.getCompound(PARTITION_LOGIC_TAG), registries);
            }
        }
        this.pendingPartitionLogic = null;
    }

    private static void writeContribution(CompoundTag data, TrinityDataCoreCpuContribution contribution) {
        data.putLong(STORAGE_BYTES_TAG, contribution.storageBytes());
        data.putInt(CO_PROCESSORS_TAG, contribution.coProcessors());
        data.putInt(PARTITION_COUNT_TAG, contribution.partitionCount());
        data.putString(SELECTION_MODE_TAG, contribution.selectionMode().name());
    }

    private static TrinityDataCoreCpuContribution readContribution(CompoundTag data) {
        CpuSelectionMode selectionMode = CpuSelectionMode.valueOf(data.getString(SELECTION_MODE_TAG));
        return new TrinityDataCoreCpuContribution(
                data.getLong(STORAGE_BYTES_TAG),
                data.getInt(CO_PROCESSORS_TAG),
                data.getInt(PARTITION_COUNT_TAG),
                selectionMode);
    }

    private static String requireStructureName(String structureName) {
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("Trinity Data Core CPU contribution structure name must not be blank");
        }
        return structureName;
    }
}
