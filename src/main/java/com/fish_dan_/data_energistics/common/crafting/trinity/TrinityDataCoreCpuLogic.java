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
import appeng.api.stacks.AEItemKey;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
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
    private static final double ENERGY_TOLERANCE = 0.01D;

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
     * @return number of dispatch operations consumed; one counted Trinity batch consumes one operation
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
            PatternInputTransaction inputTransaction = beginPatternInputTransaction(details, level);

            for (var provider : craftingService.getProviders(details)) {
                if (inputTransaction == null) {
                    break;
                }
                if (provider.isBusy()) {
                    continue;
                }

                if (provider instanceof TrinityBatchCraftingProvider batchProvider) {
                    while (inputTransaction != null && !provider.isBusy() &&
                            task.getValue().value > 0L && pushedPatterns < maxPatterns) {
                        PreparedPatternBatch batch = preparePatternBatch(
                                currentJob,
                                details,
                                task.getValue(),
                                inputTransaction,
                                task.getValue().value,
                                energyService);
                        if (batch == null) {
                            inputTransaction.rollback();
                            inputTransaction = null;
                            break;
                        }
                        EnergyCharge energyCharge = chargeEnergy(energyService, batch.power());
                        if (energyCharge == null) {
                            inputTransaction.rollback();
                            inputTransaction = null;
                            break;
                        }
                        boolean accepted;
                        try {
                            accepted = batchProvider.pushPatternBatch(
                                    details,
                                    inputTransaction.inputs().inputHolder(),
                                    batch.commit().count());
                        } catch (RuntimeException exception) {
                            Data_Energistics.LOGGER.error(
                                    "Trinity Data Core batch provider threw while dispatching {} crafts for {}",
                                    batch.commit().count(),
                                    details.getDefinition(),
                                    exception);
                            energyCharge.rollback();
                            inputTransaction.rollback();
                            inputTransaction = null;
                            break;
                        }
                        if (!accepted) {
                            energyCharge.rollback();
                            inputTransaction.rollback();
                            inputTransaction = null;
                            break;
                        }

                        energyCharge.commit();
                        inputTransaction.commit();
                        commitPatternPush(currentJob, task.getValue(), batch.commit());
                        pushedPatterns++;
                        inputTransaction = null;

                        if (task.getValue().value <= 0L) {
                            iterator.remove();
                            continue taskLoop;
                        }
                        if (pushedPatterns >= maxPatterns) {
                            break taskLoop;
                        }
                        inputTransaction = beginPatternInputTransaction(details, level);
                    }
                    continue;
                }

                PreparedPatternCommit commit = preparePatternCommit(
                        currentJob,
                        details,
                        task.getValue(),
                        inputTransaction.inputs(),
                        1L);
                if (commit == null) {
                    inputTransaction.rollback();
                    inputTransaction = null;
                    break;
                }
                double patternPower = CraftingCpuHelper.calculatePatternPower(inputTransaction.inputs().inputHolder());
                EnergyCharge energyCharge = chargeEnergy(energyService, patternPower);
                if (energyCharge == null) {
                    inputTransaction.rollback();
                    inputTransaction = null;
                    break;
                }
                boolean accepted;
                try {
                    accepted = provider.pushPattern(details, inputTransaction.inputs().inputHolder());
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error(
                            "Trinity Data Core crafting provider threw while dispatching {}",
                            details.getDefinition(),
                            exception);
                    energyCharge.rollback();
                    inputTransaction.rollback();
                    inputTransaction = null;
                    break;
                }
                if (accepted) {
                    energyCharge.commit();
                    inputTransaction.commit();
                    commitPatternPush(currentJob, task.getValue(), commit);
                    pushedPatterns++;
                    inputTransaction = null;
                    if (task.getValue().value <= 0) {
                        iterator.remove();
                        continue taskLoop;
                    }
                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    inputTransaction = beginPatternInputTransaction(details, level);
                } else {
                    energyCharge.rollback();
                    inputTransaction.rollback();
                    inputTransaction = null;
                }
            }

            if (inputTransaction != null) {
                inputTransaction.rollback();
            }
        }

        return pushedPatterns;
    }

    @Nullable
    private PatternInputTransaction beginPatternInputTransaction(IPatternDetails details, Level level) {
        KeyCounter expectedOutputs = new KeyCounter();
        KeyCounter expectedContainerItems = new KeyCounter();
        KeyCounter[] inputHolder = CraftingCpuHelper.extractPatternInputs(
                details,
                this.inventory,
                level,
                expectedOutputs,
                expectedContainerItems);
        if (inputHolder == null) {
            return null;
        }
        CapturedPatternInputs capturedInputs = capturePatternInputs(inputHolder);
        if (capturedInputs == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot track overflowing extracted pattern inputs");
            CraftingCpuHelper.reinjectPatternInputs(this.inventory, inputHolder);
            return null;
        }
        CapturedPatternResults capturedResults = capturePatternResults(expectedOutputs, expectedContainerItems);
        if (capturedResults == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot track overflowing expected pattern outputs");
            CraftingCpuHelper.reinjectPatternInputs(this.inventory, inputHolder);
            return null;
        }
        return new PatternInputTransaction(
                new ExtractedPatternInputs(
                        inputHolder,
                        capturedInputs.inputsPerCraft(),
                        capturedResults.expectedOutputs(),
                        capturedResults.expectedContainerItems(),
                        capturedResults.waitingPerCraft()),
                capturedInputs.ownedInputs());
    }

    @Nullable
    private PreparedPatternBatch preparePatternBatch(TrinityDataCoreExecutingCraftingJob currentJob,
                                                     IPatternDetails details,
                                                     TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                                     PatternInputTransaction inputTransaction,
                                                     long maximumCount,
                                                     IEnergyService energyService) {
        ExtractedPatternInputs extractedInputs = inputTransaction.inputs();
        long count = limitByInputAvailability(extractedInputs.inputsPerCraft(), maximumCount);
        count = limitByWaitingCapacity(currentJob, extractedInputs.waitingPerCraft(), count);
        double powerPerCraft = CraftingCpuHelper.calculatePatternPower(extractedInputs.inputHolder());
        count = limitByEnergy(powerPerCraft, count, energyService);
        if (count <= 0L) {
            return null;
        }
        PreparedPatternCommit commit = preparePatternCommit(
                currentJob,
                details,
                task,
                extractedInputs,
                count);
        if (commit == null || !extractAdditionalInputs(extractedInputs.inputsPerCraft(), count, inputTransaction)) {
            return null;
        }
        return new PreparedPatternBatch(powerPerCraft * count, commit);
    }

    @Nullable
    private static CapturedPatternInputs capturePatternInputs(KeyCounter[] inputHolder) {
        KeyCounter ownedInputs = new KeyCounter();
        for (KeyCounter input : inputHolder) {
            if (!addCounterChecked(ownedInputs, input)) {
                return null;
            }
        }
        return new CapturedPatternInputs(counterSnapshot(ownedInputs), ownedInputs);
    }

    @Nullable
    private static CapturedPatternResults capturePatternResults(KeyCounter expectedOutputs,
                                                                KeyCounter expectedContainerItems) {
        KeyCounter waitingPerCraft = new KeyCounter();
        List<GenericStack> capturedOutputs = captureCounter(expectedOutputs, waitingPerCraft);
        if (capturedOutputs == null) {
            return null;
        }
        List<GenericStack> capturedContainerItems = captureCounter(expectedContainerItems, waitingPerCraft);
        if (capturedContainerItems == null) {
            return null;
        }
        return new CapturedPatternResults(
                capturedOutputs,
                capturedContainerItems,
                counterSnapshot(waitingPerCraft));
    }

    @Nullable
    private static List<GenericStack> captureCounter(KeyCounter source, KeyCounter aggregate) {
        ArrayList<GenericStack> captured = new ArrayList<>();
        for (var entry : source) {
            long amount = entry.getLongValue();
            long existing = aggregate.get(entry.getKey());
            if (amount <= 0L || existing > Long.MAX_VALUE - amount) {
                return null;
            }
            aggregate.add(entry.getKey(), amount);
            captured.add(new GenericStack(entry.getKey(), amount));
        }
        return List.copyOf(captured);
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

    private long limitByInputAvailability(List<GenericStack> inputsPerCraft, long maximumCount) {
        long count = maximumCount;
        for (GenericStack input : inputsPerCraft) {
            long amountPerCraft = input.amount();
            long availableCopies = this.inventory.list.get(input.what()) / amountPerCraft;
            long availableCount = availableCopies == Long.MAX_VALUE ? Long.MAX_VALUE : availableCopies + 1L;
            count = Math.min(count, availableCount);
            count = Math.min(count, Long.MAX_VALUE / amountPerCraft);
        }
        return count;
    }

    private static long limitByWaitingCapacity(TrinityDataCoreExecutingCraftingJob currentJob,
                                               List<GenericStack> waitingPerCraft,
                                               long maximumCount) {
        long count = maximumCount;
        for (GenericStack waiting : waitingPerCraft) {
            long currentlyWaiting = currentJob.waitingFor.list.get(waiting.what());
            count = Math.min(count, (Long.MAX_VALUE - currentlyWaiting) / waiting.amount());
        }
        return count;
    }

    private static long limitByEnergy(double powerPerCraft,
                                      long maximumCount,
                                      IEnergyService energyService) {
        if (powerPerCraft < 0.0D || !Double.isFinite(powerPerCraft) || maximumCount <= 0L) {
            return 0L;
        }
        if (powerPerCraft == 0.0D) {
            return maximumCount;
        }
        long finiteCount = Math.min(maximumCount, (long) Math.floor(Double.MAX_VALUE / powerPerCraft));
        if (finiteCount <= 0L) {
            return 0L;
        }
        double requestedPower = powerPerCraft * finiteCount;
        double availablePower;
        try {
            availablePower = energyService.extractAEPower(
                    requestedPower,
                    Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU failed while checking {} AE for a counted pattern dispatch",
                    requestedPower,
                    exception);
            return 0L;
        }
        if (!Double.isFinite(availablePower) || availablePower < 0.0D) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU energy service returned invalid simulated extraction {}",
                    availablePower);
            return 0L;
        }
        if (availablePower >= requestedPower - ENERGY_TOLERANCE) {
            return finiteCount;
        }
        double affordableCount = Math.floor((availablePower + ENERGY_TOLERANCE) / powerPerCraft);
        if (affordableCount <= 0.0D) {
            return 0L;
        }
        return Math.min(finiteCount, (long) affordableCount);
    }

    @Nullable
    private boolean extractAdditionalInputs(List<GenericStack> inputsPerCraft,
                                            long count,
                                            PatternInputTransaction inputTransaction) {
        if (count == 1L) {
            return true;
        }
        long additionalCopies = count - 1L;
        for (GenericStack stack : inputsPerCraft) {
            long additionalAmount = Math.multiplyExact(stack.amount(), additionalCopies);
            long extracted;
            try {
                extracted = this.inventory.extract(stack.what(), additionalAmount, Actionable.MODULATE);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU failed while extracting {} additional inputs of {}",
                        additionalAmount,
                        stack.what(),
                        exception);
                return false;
            }
            inputTransaction.recordAdditionalInput(stack.what(), extracted);
            if (extracted != additionalAmount) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU inventory changed while preparing a counted pattern batch: expected {} of {}, extracted {}",
                        additionalAmount,
                        stack.what(),
                        extracted);
                return false;
            }
        }
        return true;
    }

    private static List<GenericStack> counterSnapshot(KeyCounter counter) {
        ArrayList<GenericStack> snapshot = new ArrayList<>();
        for (var entry : counter) {
            snapshot.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return List.copyOf(snapshot);
    }

    @Nullable
    private static EnergyCharge chargeEnergy(IEnergyService energyService, double power) {
        if (power < 0.0D || !Double.isFinite(power)) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU calculated invalid pattern power {}", power);
            return null;
        }
        if (power == 0.0D) {
            return new EnergyCharge(energyService, 0.0D);
        }
        double extracted;
        try {
            extracted = energyService.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU failed while extracting {} AE for a pattern dispatch",
                    power,
                    exception);
            return null;
        }
        if (!Double.isFinite(extracted) || extracted < power - ENERGY_TOLERANCE ||
                extracted > power + ENERGY_TOLERANCE) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU expected to extract {} AE for a pattern dispatch, but extracted {}",
                    power,
                    extracted);
            refundEnergy(energyService, extracted);
            return null;
        }
        return new EnergyCharge(energyService, extracted);
    }

    private static void refundEnergy(IEnergyService energyService, double extracted) {
        if (extracted <= 0.0D || !Double.isFinite(extracted)) {
            return;
        }
        double refund = PowerMultiplier.CONFIG.multiply(extracted);
        if (!Double.isFinite(refund)) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU calculated invalid energy refund {}", refund);
            return;
        }
        double refundTolerance = PowerMultiplier.CONFIG.multiply(ENERGY_TOLERANCE);
        try {
            double remainder = energyService.injectPower(refund, Actionable.MODULATE);
            if (remainder > refundTolerance) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU could not refund {} of {} AE after a failed pattern dispatch",
                        remainder,
                        refund);
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU failed to refund {} AE after a failed pattern dispatch",
                    refund,
                    exception);
        }
    }

    @Nullable
    private static PreparedPatternCommit preparePatternCommit(TrinityDataCoreExecutingCraftingJob currentJob,
                                                              IPatternDetails details,
                                                              TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                                              ExtractedPatternInputs extractedInputs,
                                                              long count) {
        if (count <= 0L || count > task.value) {
            Data_Energistics.LOGGER.error(
                    "Trinity Data Core CPU cannot commit {} crafts from a task with {} remaining",
                    count,
                    task.value);
            return null;
        }
        if (limitByWaitingCapacity(currentJob, extractedInputs.waitingPerCraft(), count) < count) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot commit overflowing waiting counters");
            return null;
        }
        List<GenericStack> expectedOutputs = scaleAmounts(extractedInputs.expectedOutputs(), count);
        List<GenericStack> expectedContainerItems = scaleAmounts(extractedInputs.expectedContainerItems(), count);
        List<GenericStack> scheduledOutputs = scaleStacks(details.getOutputs(), count);
        if (expectedOutputs == null || expectedContainerItems == null || scheduledOutputs == null) {
            Data_Energistics.LOGGER.error("Trinity Data Core CPU cannot commit overflowing pattern outputs");
            return null;
        }
        for (GenericStack output : scheduledOutputs) {
            if (currentJob.getPendingOutputs(output.what()) < output.amount()) {
                Data_Energistics.LOGGER.error(
                        "Trinity Data Core CPU cannot remove {} scheduled units of {}",
                        output.amount(),
                        output.what());
                return null;
            }
        }
        HashSet<AEKey> changedKeys = new HashSet<>();
        for (GenericStack output : expectedOutputs) {
            changedKeys.add(output.what());
        }
        for (GenericStack output : expectedContainerItems) {
            changedKeys.add(output.what());
        }
        return new PreparedPatternCommit(
                count,
                expectedOutputs,
                expectedContainerItems,
                new PreparedScheduledOutputs(details.getDefinition(), scheduledOutputs),
                Set.copyOf(changedKeys));
    }

    @Nullable
    private static List<GenericStack> scaleAmounts(List<GenericStack> amounts, long count) {
        ArrayList<GenericStack> scaled = new ArrayList<>();
        for (GenericStack stack : amounts) {
            long amount = stack.amount();
            if (amount <= 0L || amount > Long.MAX_VALUE / count) {
                return null;
            }
            scaled.add(new GenericStack(stack.what(), amount * count));
        }
        return List.copyOf(scaled);
    }

    @Nullable
    private static List<GenericStack> scaleStacks(List<GenericStack> stacks, long count) {
        KeyCounter scaled = new KeyCounter();
        for (GenericStack stack : stacks) {
            long amount = stack.amount();
            if (amount <= 0L || amount > Long.MAX_VALUE / count) {
                return null;
            }
            long scaledAmount = amount * count;
            long existing = scaled.get(stack.what());
            if (existing > Long.MAX_VALUE - scaledAmount) {
                return null;
            }
            scaled.add(stack.what(), scaledAmount);
        }
        return counterSnapshot(scaled);
    }

    private void commitPatternPush(TrinityDataCoreExecutingCraftingJob currentJob,
                                   TrinityDataCoreExecutingCraftingJob.TaskProgress task,
                                   PreparedPatternCommit commit) {
        addWaiting(currentJob, commit.expectedOutputs());
        addWaiting(currentJob, commit.expectedContainerItems());
        task.value -= commit.count();
        currentJob.recordTaskDispatch(commit.scheduledRemoval(), 1L);
        for (GenericStack containerItem : commit.expectedContainerItems()) {
            currentJob.timeTracker.addMaxItems(containerItem.amount(), containerItem.what().getType());
        }
        this.cpu.markDirty();
        for (AEKey changedKey : commit.changedKeys()) {
            postChange(changedKey);
        }
    }

    private static void addWaiting(TrinityDataCoreExecutingCraftingJob currentJob,
                                   List<GenericStack> additions) {
        for (GenericStack addition : additions) {
            currentJob.waitingFor.list.add(addition.what(), addition.amount());
        }
    }

    private record CapturedPatternInputs(List<GenericStack> inputsPerCraft, KeyCounter ownedInputs) {}

    private record CapturedPatternResults(List<GenericStack> expectedOutputs,
                                          List<GenericStack> expectedContainerItems,
                                          List<GenericStack> waitingPerCraft) {}

    private record ExtractedPatternInputs(KeyCounter[] inputHolder,
                                          List<GenericStack> inputsPerCraft,
                                          List<GenericStack> expectedOutputs,
                                          List<GenericStack> expectedContainerItems,
                                          List<GenericStack> waitingPerCraft) {}

    private record PreparedPatternCommit(long count,
                                         List<GenericStack> expectedOutputs,
                                         List<GenericStack> expectedContainerItems,
                                         IPatternDetails scheduledRemoval,
                                         Set<AEKey> changedKeys) {}

    private record PreparedPatternBatch(double power, PreparedPatternCommit commit) {}

    private record PreparedScheduledOutputs(AEItemKey definition, List<GenericStack> outputs)
            implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }
    }

    private final class PatternInputTransaction {

        private final ExtractedPatternInputs inputs;
        private final KeyCounter ownedInputs;
        private boolean active = true;

        private PatternInputTransaction(ExtractedPatternInputs inputs, KeyCounter ownedInputs) {
            this.inputs = inputs;
            this.ownedInputs = ownedInputs;
        }

        private ExtractedPatternInputs inputs() {
            return this.inputs;
        }

        private void commit() {
            this.active = false;
        }

        private void recordAdditionalInput(AEKey what, long amount) {
            if (amount > 0L) {
                long existing = this.ownedInputs.get(what);
                if (existing > Long.MAX_VALUE - amount) {
                    throw new IllegalStateException("Trinity Data Core CPU extracted-input ownership overflow for " + what);
                }
                this.ownedInputs.add(what, amount);
            }
        }

        private void rollback() {
            if (!this.active) {
                return;
            }
            this.active = false;
            for (var entry : this.ownedInputs) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
    }

    private static final class EnergyCharge {

        private final IEnergyService energyService;
        private final double extracted;
        private boolean active = true;

        private EnergyCharge(IEnergyService energyService, double extracted) {
            this.energyService = energyService;
            this.extracted = extracted;
        }

        private void commit() {
            this.active = false;
        }

        private void rollback() {
            if (!this.active) {
                return;
            }
            this.active = false;
            refundEnergy(this.energyService, this.extracted);
        }
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
