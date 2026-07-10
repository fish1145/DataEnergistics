package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Executes one virtual Trinity Data Core crafting CPU partition.
 *
 * <p>
 * This logic follows AE2's CPU execution flow while replacing native cluster callbacks with the Trinity Data Core
 * runtime.
 */
final class TrinityDataCoreCpuLogic {

    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 1;
    private static final String INVENTORY_TAG = "inventory";
    private static final String JOB_TAG = "job";

    private final TrinityDataCoreVirtualCpu cpu;
    @Nullable
    private TrinityDataCoreExecutingCraftingJob job;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final int[] usedOps = new int[3];
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private boolean cantStoreItems;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    TrinityDataCoreCpuLogic(TrinityDataCoreVirtualCpu cpu) {
        this.cpu = cpu;
    }

    /**
     * @return CPU partition owning this logic
     */
    TrinityDataCoreVirtualCpu cpu() {
        return this.cpu;
    }

    /**
     * Attempts to bind and start a crafting plan on this CPU partition.
     *
     * @param grid      AE2 grid that owns the request
     * @param plan      calculated crafting plan
     * @param source    action source used to extract initial ingredients
     * @param requester optional requester that receives final outputs
     * @return submit result
     */
    ICraftingSubmitResult trySubmitJob(IGrid grid,
                                       ICraftingPlan plan,
                                       IActionSource source,
                                       @Nullable ICraftingRequester requester) {
        if (this.job != null) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!this.cpu.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        if (!this.inventory.list.isEmpty()) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU inventory is not empty when a job is submitted");
        }

        GenericStack missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, this.inventory, source);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        Integer playerId = source.player()
                .map(player -> player instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        UUID craftId = UUID.randomUUID();
        CraftingLink linkCpu = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, requester == null, false),
                this.cpu);
        this.job = new TrinityDataCoreExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);
        this.cpu.markDirty();
        notifyJobOwner(this.job, CraftingJobStatusPacket.Status.STARTED);

        if (requester != null) {
            CraftingLink linkRequester = new CraftingLink(
                    CraftingCpuHelper.generateLinkData(craftId, false, true),
                    requester);
            CraftingService craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkRequester);
            return CraftingSubmitResult.successful(linkRequester);
        }
        return CraftingSubmitResult.successful(null);
    }

    /**
     * Advances pattern dispatch and inventory cleanup for one server tick.
     *
     * @param energyService   AE2 energy service
     * @param craftingService AE2 crafting service
     */
    void tickCraftingLogic(IEnergyService energyService, CraftingService craftingService) {
        if (!this.cpu.isActive()) {
            return;
        }
        this.cantStoreItems = false;
        if (this.job == null) {
            storeItems();
            if (!this.inventory.list.isEmpty()) {
                this.cantStoreItems = true;
            }
            return;
        }
        if (this.job.link.isCanceled()) {
            cancel();
            return;
        }
        int remainingOperations = operationBudget(this.cpu.getCoProcessors(), this.usedOps);
        int started = remainingOperations;
        Level level = this.cpu.level();
        if (level == null) {
            Data_Energistics.LOGGER.warn("Trinity Data Core CPU cannot tick crafting job without a level");
            return;
        }

        while (remainingOperations > 0) {
            int pushedPatterns = executeCrafting(remainingOperations, craftingService, energyService, level);
            if (pushedPatterns <= 0) {
                break;
            }
            remainingOperations -= pushedPatterns;
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - remainingOperations;
    }

    /**
     * Calculates this tick's dispatch window without overflowing at the maximum co-processor count.
     */
    static int operationBudget(int coProcessors, int[] usedOps) {
        if (coProcessors < 0) {
            throw new IllegalArgumentException("coProcessors must not be negative");
        }
        if (usedOps.length != 3) {
            throw new IllegalArgumentException("usedOps must contain exactly three ticks");
        }
        long recentlyUsed = 0L;
        for (int used : usedOps) {
            if (used < 0) {
                throw new IllegalArgumentException("usedOps must not contain negative values");
            }
            recentlyUsed += used;
        }
        long available = (long) coProcessors + 1L - recentlyUsed;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, available));
    }

    /**
     * Dispatches available pattern tasks to AE2 crafting providers.
     *
     * @param maxPatterns     maximum pattern pushes for this tick
     * @param craftingService AE2 crafting service
     * @param energyService   AE2 energy service
     * @param level           server level used by pattern validation
     * @return number of pushed patterns
     */
    int executeCrafting(int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null) {
            return 0;
        }

        int pushedPatterns = 0;
        var iterator = currentJob.tasks.entrySet().iterator();
        taskLoop:
        while (iterator.hasNext()) {
            var task = iterator.next();
            if (task.getValue().value <= 0) {
                iterator.remove();
                continue;
            }

            var details = task.getKey();
            KeyCounter expectedOutputs = new KeyCounter();
            KeyCounter expectedContainerItems = new KeyCounter();
            KeyCounter[] craftingContainer = CraftingCpuHelper.extractPatternInputs(
                    details,
                    this.inventory,
                    level,
                    expectedOutputs,
                    expectedContainerItems);

            for (var provider : craftingService.getProviders(details)) {
                if (craftingContainer == null) {
                    break;
                }
                if (provider.isBusy()) {
                    continue;
                }

                double patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) <
                        patternPower - 0.01D) {
                    break;
                }

                if (provider.pushPattern(details, craftingContainer)) {
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;

                    for (var expectedOutput : expectedOutputs) {
                        currentJob.waitingFor.insert(
                                expectedOutput.getKey(),
                                expectedOutput.getLongValue(),
                                Actionable.MODULATE);
                    }
                    for (var expectedContainerItem : expectedContainerItems) {
                        currentJob.waitingFor.insert(
                                expectedContainerItem.getKey(),
                                expectedContainerItem.getLongValue(),
                                Actionable.MODULATE);
                        currentJob.timeTracker.addMaxItems(
                                expectedContainerItem.getLongValue(),
                                expectedContainerItem.getKey().getType());
                    }

                    this.cpu.markDirty();
                    task.getValue().value--;
                    if (task.getValue().value <= 0) {
                        iterator.remove();
                        continue taskLoop;
                    }
                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details,
                            this.inventory,
                            level,
                            expectedOutputs,
                            expectedContainerItems);
                }
            }

            if (craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(this.inventory, craftingContainer);
            }
        }

        return pushedPatterns;
    }

    /**
     * Inserts returned crafting outputs into this CPU when it is waiting for them.
     *
     * @param what   key to insert
     * @param amount amount to insert
     * @param type   simulation or mutation mode
     * @return accepted amount
     */
    long insert(AEKey what, long amount, Actionable type) {
        if (this.job == null || amount <= 0) {
            return 0L;
        }

        long waitingFor = this.job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0L;
        }
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            this.job.timeTracker.decrementItems(amount, what.getType());
            this.job.waitingFor.extract(what, amount, Actionable.MODULATE);
            this.cpu.markDirty();
        }

        long inserted = amount;
        if (what.matches(this.job.finalOutput)) {
            inserted = this.job.link.insert(what, amount, type);
            if (type == Actionable.MODULATE) {
                postChange(what);
                this.job.remainingAmount = Math.max(0L, this.job.remainingAmount - amount);

                if (this.job.remainingAmount <= 0) {
                    finishJob(true);
                } else {
                    this.cpu.markDirty();
                }
            }
        } else if (type == Actionable.MODULATE) {
            this.inventory.insert(what, amount, Actionable.MODULATE);
        }

        return inserted;
    }

    /**
     * Cancels the current job, if one is active.
     */
    void cancel() {
        if (this.job == null) {
            return;
        }
        finishJob(false);
    }

    /**
     * @return true when this CPU currently owns a job
     */
    boolean hasJob() {
        return this.job != null;
    }

    /**
     * @return current final output, or null when idle
     */
    @Nullable
    GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    /**
     * @return progress tracker for the active job
     */
    TrinityDataCoreElapsedTimeTracker elapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        }
        return new TrinityDataCoreElapsedTimeTracker();
    }

    /**
     * @return last tick where crafting-visible state changed
     */
    long getLastModifiedOnTick() {
        return this.lastModifiedOnTick;
    }

    /**
     * @return last job link for AE2 crafting service restoration
     */
    @Nullable
    ICraftingLink getLastLink() {
        return this.job != null ? this.job.link : null;
    }

    /**
     * Adds waiting keys to the output set used by AE2 request watchers.
     *
     * @param waitingFor output key set
     */
    void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job == null) {
            return;
        }
        for (var entry : this.job.waitingFor.list) {
            waitingFor.add(entry.getKey());
        }
    }

    /**
     * @param template requested key
     * @return amount this CPU is waiting for
     */
    long getWaitingFor(AEKey template) {
        if (this.job == null) {
            return 0L;
        }
        return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    /**
     * @param registries registry lookup
     * @return serialized logic state
     */
    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        data.put(INVENTORY_TAG, this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put(JOB_TAG, this.job.writeToTag(registries));
        }
        return data;
    }

    /**
     * Restores inventory and job state for this CPU partition.
     *
     * @param data       serialized logic state
     * @param registries registry lookup
     */
    void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        clearPersistedState();
        if (!data.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            Data_Energistics.LOGGER.warn("Ignoring Trinity Data Core CPU logic without a schema version");
            return;
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring Trinity Data Core CPU logic schema version {}; expected {}",
                    schemaVersion,
                    SCHEMA_VERSION);
            return;
        }

        this.inventory.readFromNBT(data.getList(INVENTORY_TAG, 10), registries);
        if (!data.contains(JOB_TAG)) {
            return;
        }
        if (!data.contains(JOB_TAG, Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error("Ignoring Trinity Data Core CPU job because its persisted tag is not a compound");
            return;
        }

        CompoundTag jobData = data.getCompound(JOB_TAG);
        if (!TrinityDataCoreExecutingCraftingJob.hasCurrentSchema(jobData)) {
            return;
        }
        try {
            this.job = new TrinityDataCoreExecutingCraftingJob(
                    jobData,
                    registries,
                    this::postChange,
                    this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Ignoring invalid persisted Trinity Data Core CPU job", exception);
            this.job = null;
        }
    }

    private void clearPersistedState() {
        this.inventory.clear();
        this.job = null;
    }

    /**
     * @return true when idle inventory could not be returned to the network
     */
    boolean isCantStoreItems() {
        return this.cantStoreItems;
    }

    void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job == null) {
            return;
        }
        out.addAll(this.job.waitingFor.list);
        for (var entry : this.job.tasks.entrySet()) {
            for (GenericStack output : entry.getKey().getOutputs()) {
                out.add(output.what(), output.amount() * entry.getValue().value);
            }
        }
    }

    long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    long getPendingOutputs(AEKey template) {
        long count = 0L;
        if (this.job != null) {
            for (var entry : this.job.tasks.entrySet()) {
                for (GenericStack output : entry.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count += output.amount() * entry.getValue().value;
                    }
                }
            }
        }
        return count;
    }

    void addListener(Consumer<AEKey> listener) {
        this.listeners.add(listener);
    }

    void removeListener(Consumer<AEKey> listener) {
        this.listeners.remove(listener);
    }

    private void finishJob(boolean success) {
        if (this.job == null) {
            return;
        }
        if (success) {
            this.job.link.markDone();
        } else {
            this.job.link.cancel();
        }

        this.job.waitingFor.clear();
        for (var entry : this.job.tasks.entrySet()) {
            for (GenericStack output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }
        notifyJobOwner(
                this.job,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);
        this.job = null;
        storeItems();
    }

    private void storeItems() {
        Preconditions.checkState(this.job == null, "CPU should not have a job while dumping inventory");
        if (this.inventory.list.isEmpty()) {
            return;
        }

        IGrid grid = this.cpu.grid();
        if (grid == null) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        IActionSource source = this.cpu.actionSource();
        for (var entry : this.inventory.list) {
            postChange(entry.getKey());
            long inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();
        this.cpu.markDirty();
    }

    private void postChange(AEKey what) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (Consumer<AEKey> listener : this.listeners) {
            listener.accept(what);
        }
    }

    private void notifyJobOwner(TrinityDataCoreExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        Integer playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        Level level = this.cpu.level();
        if (level == null || level.getServer() == null) {
            return;
        }
        ServerPlayer connectedPlayer = IPlayerRegistry.getConnected(level.getServer(), playerId);
        if (connectedPlayer != null) {
            ClientboundPacket message = new CraftingJobStatusPacket(
                    job.link.getCraftingID(),
                    job.finalOutput.what(),
                    job.finalOutput.amount(),
                    job.remainingAmount,
                    status);
            connectedPlayer.connection.send(message);
        }
    }
}
