package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

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
        if (!this.cpu.isActiveOnGrid(grid)) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.job != null) {
            return CraftingSubmitResult.CPU_BUSY;
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
        if (!this.cpu.isOnline()) {
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
        if (!this.cpu.isActive()) {
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
        if (currentJob == null || maxPatterns <= 0) {
            return 0;
        }

        int pushedPatterns = 0;
        var iterator = currentJob.tasks.entrySet().iterator();
        taskLoop:
        while (iterator.hasNext() && pushedPatterns < maxPatterns) {
            var task = iterator.next();
            if (task.getValue().value <= 0) {
                iterator.remove();
                continue;
            }

            var details = task.getKey();
            ExtractedPatternInputs extractedInputs = extractPatternInputs(details, level);

            for (var provider : craftingService.getProviders(details)) {
                if (extractedInputs == null) {
                    break;
                }
                if (provider.isBusy()) {
                    continue;
                }

                if (provider instanceof TrinityBatchCraftingProvider batchProvider) {
                    while (extractedInputs != null && !provider.isBusy() &&
                            task.getValue().value > 0L && pushedPatterns < maxPatterns) {
                        long maximumCount = Math.min(
                                task.getValue().value,
                                (long) maxPatterns - pushedPatterns);
                        PreparedPatternBatch batch = preparePatternBatch(
                                currentJob,
                                extractedInputs,
                                maximumCount,
                                energyService);
                        if (batch == null) {
                            break;
                        }
                        if (!batchProvider.pushPatternBatch(
                                details,
                                extractedInputs.inputHolder(),
                                batch.count())) {
                            reinjectAdditionalInputs(batch.additionalInputs());
                            break;
                        }

                        energyService.extractAEPower(
                                batch.power(),
                                Actionable.MODULATE,
                                PowerMultiplier.CONFIG);
                        commitPatternPush(currentJob, details, task.getValue(), extractedInputs, batch.count());
                        pushedPatterns = Math.addExact(pushedPatterns, Math.toIntExact(batch.count()));
                        extractedInputs = null;

                        if (task.getValue().value <= 0L) {
                            iterator.remove();
                            continue taskLoop;
                        }
                        if (pushedPatterns >= maxPatterns) {
                            break taskLoop;
                        }
                        extractedInputs = extractPatternInputs(details, level);
                    }
                    continue;
                }

                KeyCounter waitingPerCraft = aggregateWaiting(extractedInputs);
                if (waitingPerCraft == null ||
                        limitByWaitingCapacity(currentJob, waitingPerCraft, 1L) == 0L) {
                    if (waitingPerCraft == null) {
                        Data_Energistics.LOGGER.error(
                                "Trinity Data Core CPU cannot dispatch overflowing per-craft output counters");
                    }
                    break;
                }
                double patternPower = CraftingCpuHelper.calculatePatternPower(extractedInputs.inputHolder());
                if (!hasEnergyFor(patternPower, energyService)) {
                    break;
                }

                if (provider.pushPattern(details, extractedInputs.inputHolder())) {
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;
                    commitPatternPush(currentJob, details, task.getValue(), extractedInputs, 1L);
                    extractedInputs = null;
                    if (task.getValue().value <= 0) {
                        iterator.remove();
                        continue taskLoop;
                    }
                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    extractedInputs = extractPatternInputs(details, level);
                }
            }

            if (extractedInputs != null) {
                CraftingCpuHelper.reinjectPatternInputs(this.inventory, extractedInputs.inputHolder());
            }
        }

        return pushedPatterns;
    }

    @Nullable
    private ExtractedPatternInputs extractPatternInputs(IPatternDetails details, Level level) {
        KeyCounter expectedOutputs = new KeyCounter();
        KeyCounter expectedContainerItems = new KeyCounter();
        KeyCounter[] inputHolder = CraftingCpuHelper.extractPatternInputs(
                details,
                this.inventory,
                level,
                expectedOutputs,
                expectedContainerItems);
        return inputHolder == null ? null : new ExtractedPatternInputs(
                inputHolder,
                expectedOutputs,
                expectedContainerItems);
    }

    @Nullable
    private PreparedPatternBatch preparePatternBatch(TrinityDataCoreExecutingCraftingJob currentJob,
                                                      ExtractedPatternInputs extractedInputs,
                                                      long maximumCount,
                                                      IEnergyService energyService) {
        KeyCounter inputsPerCraft = aggregateInputs(extractedInputs.inputHolder());
        KeyCounter waitingPerCraft = aggregateWaiting(extractedInputs);
        if (inputsPerCraft == null || waitingPerCraft == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot batch overflowing per-craft counters");
            return null;
        }

        long count = limitByInputAvailability(inputsPerCraft, maximumCount);
        count = limitByWaitingCapacity(currentJob, waitingPerCraft, count);
        double powerPerCraft = CraftingCpuHelper.calculatePatternPower(extractedInputs.inputHolder());
        count = limitByEnergy(powerPerCraft, count, energyService);
        if (count <= 0L) {
            return null;
        }
        KeyCounter additionalInputs = extractAdditionalInputs(inputsPerCraft, count);
        return additionalInputs == null ? null : new PreparedPatternBatch(
                count,
                powerPerCraft * count,
                additionalInputs);
    }

    @Nullable
    private static KeyCounter aggregateInputs(KeyCounter[] inputHolder) {
        KeyCounter result = new KeyCounter();
        for (KeyCounter input : inputHolder) {
            if (!addCounterChecked(result, input)) {
                return null;
            }
        }
        return result;
    }

    @Nullable
    private static KeyCounter aggregateWaiting(ExtractedPatternInputs extractedInputs) {
        KeyCounter result = new KeyCounter();
        if (!addCounterChecked(result, extractedInputs.expectedOutputs()) ||
                !addCounterChecked(result, extractedInputs.expectedContainerItems())) {
            return null;
        }
        return result;
    }

    private static boolean addCounterChecked(KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            long amount = entry.getLongValue();
            long existing = target.get(entry.getKey());
            if (amount <= 0L || existing > Long.MAX_VALUE - amount) {
                return false;
            }
            target.add(entry.getKey(), amount);
        }
        return true;
    }

    private long limitByInputAvailability(KeyCounter inputsPerCraft, long maximumCount) {
        long count = maximumCount;
        for (var entry : inputsPerCraft) {
            long amountPerCraft = entry.getLongValue();
            long availableCopies = this.inventory.list.get(entry.getKey()) / amountPerCraft;
            long availableCount = availableCopies == Long.MAX_VALUE ? Long.MAX_VALUE : availableCopies + 1L;
            count = Math.min(count, availableCount);
            count = Math.min(count, Long.MAX_VALUE / amountPerCraft);
        }
        return count;
    }

    private static long limitByWaitingCapacity(TrinityDataCoreExecutingCraftingJob currentJob,
                                               KeyCounter waitingPerCraft,
                                               long maximumCount) {
        long count = maximumCount;
        for (var entry : waitingPerCraft) {
            long currentlyWaiting = currentJob.waitingFor.list.get(entry.getKey());
            count = Math.min(count, (Long.MAX_VALUE - currentlyWaiting) / entry.getLongValue());
        }
        return count;
    }

    private static long limitByEnergy(double powerPerCraft,
                                      long maximumCount,
                                      IEnergyService energyService) {
        double requestedPower = powerPerCraft * maximumCount;
        double availablePower = energyService.extractAEPower(
                requestedPower,
                Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        if (powerPerCraft <= 0.0D || availablePower >= requestedPower - 0.01D) {
            return maximumCount;
        }
        double affordableCount = Math.floor((availablePower + 0.01D) / powerPerCraft);
        if (affordableCount <= 0.0D) {
            return 0L;
        }
        return Math.min(maximumCount, (long) affordableCount);
    }

    @Nullable
    private KeyCounter extractAdditionalInputs(KeyCounter inputsPerCraft, long count) {
        KeyCounter extractedInputs = new KeyCounter();
        if (count == 1L) {
            return extractedInputs;
        }
        long additionalCopies = count - 1L;
        for (GenericStack stack : snapshot(inputsPerCraft)) {
            long additionalAmount = Math.multiplyExact(stack.amount(), additionalCopies);
            long extracted = this.inventory.extract(stack.what(), additionalAmount, Actionable.MODULATE);
            extractedInputs.add(stack.what(), extracted);
            if (extracted != additionalAmount) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU inventory changed while preparing a counted pattern batch: expected {} of {}, extracted {}",
                        additionalAmount,
                        stack.what(),
                        extracted);
                reinjectAdditionalInputs(extractedInputs);
                return null;
            }
        }
        return extractedInputs;
    }

    private void reinjectAdditionalInputs(KeyCounter additionalInputs) {
        for (var entry : additionalInputs) {
            this.inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
    }

    private static List<GenericStack> snapshot(KeyCounter counter) {
        return StreamSupport.stream(counter.spliterator(), false)
                .map(entry -> new GenericStack(entry.getKey(), entry.getLongValue()))
                .toList();
    }

    private static boolean hasEnergyFor(double patternPower, IEnergyService energyService) {
        return energyService.extractAEPower(
                patternPower,
                Actionable.SIMULATE,
                PowerMultiplier.CONFIG) >= patternPower - 0.01D;
    }

    private void commitPatternPush(TrinityDataCoreExecutingCraftingJob currentJob,
                                   IPatternDetails details,
                                   TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                   ExtractedPatternInputs extractedInputs,
                                   long count) {
        addWaiting(currentJob, extractedInputs.expectedOutputs(), count, false);
        addWaiting(currentJob, extractedInputs.expectedContainerItems(), count, true);
        currentJob.recordTaskDispatch(details, count);
        this.cpu.markDirty();
        task.value -= count;
    }

    private static void addWaiting(TrinityDataCoreExecutingCraftingJob currentJob,
                                   KeyCounter perCraft,
                                   long count,
                                   boolean trackMaximum) {
        for (var entry : perCraft) {
            long amount = Math.multiplyExact(entry.getLongValue(), count);
            currentJob.waitingFor.insert(entry.getKey(), amount, Actionable.MODULATE);
            if (trackMaximum) {
                currentJob.timeTracker.addMaxItems(amount, entry.getKey().getType());
            }
        }
    }

    private record ExtractedPatternInputs(KeyCounter[] inputHolder,
                                          KeyCounter expectedOutputs,
                                          KeyCounter expectedContainerItems) {}

    private record PreparedPatternBatch(long count, double power, KeyCounter additionalInputs) {}

    /**
     * Inserts returned crafting outputs into this CPU when it is waiting for them.
     *
     * @param what   key to insert
     * @param amount amount to insert
     * @param type   simulation or mutation mode
     * @return accepted amount
     */
    long insert(AEKey what, long amount, Actionable type) {
        TrinityDataCoreExecutingCraftingJob currentJob = this.job;
        if (currentJob == null || amount <= 0) {
            return 0L;
        }

        long waitingFor = currentJob.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0L;
        }
        long requested = Math.min(amount, waitingFor);
        boolean finalOutput = what.matches(currentJob.finalOutput);
        boolean receiveLocally = !finalOutput || currentJob.link.isStandalone();
        long accepted;
        if (receiveLocally) {
            accepted = requested;
        } else {
            accepted = currentJob.link.insert(what, requested, type);
            validateLinkAcceptance(what, requested, accepted, type);
        }
        if (accepted <= 0L || type == Actionable.SIMULATE) {
            return accepted;
        }

        if (receiveLocally) {
            this.inventory.insert(what, accepted, Actionable.MODULATE);
        }
        currentJob.timeTracker.decrementItems(accepted, what.getType());
        currentJob.waitingFor.extract(what, accepted, Actionable.MODULATE);
        if (finalOutput) {
            currentJob.remainingAmount = Math.max(0L, currentJob.remainingAmount - accepted);
        }
        this.cpu.markDirty();

        if (currentJob.isComplete()) {
            finishJob(true);
        }
        return accepted;
    }

    private static void validateLinkAcceptance(AEKey what,
                                               long requested,
                                               long accepted,
                                               Actionable type) {
        if (accepted >= 0L && accepted <= requested) {
            return;
        }
        String message = "Crafting link violated the insertion contract for " + what + " in " + type + " mode: requested " + requested + ", accepted " + accepted + "; expected 0 <= accepted <= requested";
        Data_Energistics.LOGGER.error(message);
        throw new IllegalStateException(message);
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

    /** Returns whether this worker owns state that must survive hiding, saving, and pool reuse. */
    boolean hasRetainedState() {
        return this.job != null || !this.inventory.list.isEmpty();
    }

    /** Returns whether the runtime can discard this worker and reuse its number. */
    boolean isReleasable() {
        return !hasRetainedState();
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
        discardPersistedState();
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
        Tag rawInventory = data.get(INVENTORY_TAG);
        if (!(rawInventory instanceof ListTag inventoryTag) ||
                (!inventoryTag.isEmpty() && inventoryTag.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error("Ignoring Trinity Data Core CPU logic without a list inventory");
            return;
        }

        this.inventory.readFromNBT(inventoryTag, registries);
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

    void discardPersistedState() {
        this.inventory.clear();
        this.job = null;
    }

    static boolean persistedHasJob(CompoundTag data) {
        return data.contains(JOB_TAG, Tag.TAG_COMPOUND);
    }

    static boolean persistedHasRetainedState(CompoundTag data) {
        return data.contains(JOB_TAG) ||
                !data.getList(INVENTORY_TAG, Tag.TAG_COMPOUND).isEmpty();
    }

    /**
     * @return true when idle inventory could not be returned to the network
     */
    boolean isCantStoreItems() {
        return this.cantStoreItems;
    }

    /** Moves idle inventory through a durable sink and reports whether no remainder is retained. */
    boolean recoverIdleInventory(BiFunction<AEKey, Long, Long> recovery) {
        Preconditions.checkState(this.job == null, "CPU should not have a job while recovering inventory");
        for (var entry : this.inventory.list) {
            long available = entry.getLongValue();
            long recovered = recovery.apply(entry.getKey(), available);
            if (recovered < 0L || recovered > available) {
                throw new IllegalStateException("Trinity CPU inventory recovery violated the insertion contract for " +
                        entry.getKey() + ": offered " + available + ", recovered " + recovered);
            }
            if (recovered > 0L) {
                postChange(entry.getKey());
                entry.setValue(available - recovered);
            }
        }
        this.inventory.list.removeZeros();
        this.cpu.markDirty();
        return this.inventory.list.isEmpty();
    }

    void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job == null) {
            return;
        }
        out.addAll(this.job.waitingFor.list);
        this.job.addScheduledOutputsTo(out);
    }

    long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    long getPendingOutputs(AEKey template) {
        return this.job == null ? 0L : this.job.getPendingOutputs(template);
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
