package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdLookup;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolution;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default definition-table and counted-FIFO implementation for one {@link TrinityPatternSlot}.
 */
public final class TrinityPatternSlotImpl implements TrinityPatternSlot {

    private static final String AMOUNT_TAG = "amount";
    private static final String BATCHES_TAG = "batches";
    private static final String DEFINITION_ID_TAG = "definition_id";
    private static final String DEFINITIONS_TAG = "definitions";
    private static final String INSTALLED_DEFINITION_ID_TAG = "installed_definition_id";
    private static final String PATTERN_TAG = "pattern";
    private static final String PENDING_OUTPUTS_TAG = "pending_outputs";
    private static final String PROTOTYPE_TAG = "prototype";
    private static final String RECIPE_ID_TAG = "recipe_id";
    private static final String RESOLVER_ID_TAG = "resolver_id";
    private static final String ROUTE_TAG = "route";
    private static final String SLOT_TAG = "slot";
    private static final String STACK_TAG = "stack";
    private static final String OUTPUTS_TAG = "outputs";

    private final int index;
    private final TrinityPatternCore.PatternDecoder decoder;
    private final TrinityPatternRecipeIdLookup recipeIdResolvers;
    private final ChangeListener changeListener;
    private LinkedHashMap<Long, TrinityPatternDefinition> definitions = new LinkedHashMap<>();
    private ArrayDeque<TrinityCraftingBatch> queue = new ArrayDeque<>();
    private LinkedHashMap<PatternRoute, ArrayList<TrinityItemAmount>> pendingOutputs = new LinkedHashMap<>();
    /**
     * Counts queued groups per host so ordinary mutations never rescan the FIFO.
     */
    private Map<UUID, Integer> queuedGroupsByHost = new HashMap<>();
    /**
     * Counts pending routes per host so partial output consumption leaves membership untouched.
     */
    private Map<UUID, Integer> pendingRoutesByHost = new HashMap<>();

    private ItemStack pattern = ItemStack.EMPTY;
    @Nullable
    private TrinityPatternDefinition installedDefinition;
    @Nullable
    private IMolecularAssemblerSupportedPattern decodedPattern;
    private long nextDefinitionId;
    private long revision;
    /**
     * Separates queue and pending topology for one precise internal WORK notification.
     */
    private WorkMembership workMembership = new WorkMembership(Set.of(), Set.of());
    /**
     * Immutable union consumed by the core's sparse per-host work index.
     */
    private Set<UUID> workHostIds = Set.of();
    @Nullable
    private PendingOutputCursorImpl activePendingOutputCursor;

    /**
     * Creates an empty physical slot.
     *
     * @param index             stable physical slot index
     * @param decoder           supported-pattern decoder
     * @param recipeIdResolvers unique recipe identity registry
     * @param changeListener    typed owner callback
     */
    public TrinityPatternSlotImpl(int index, TrinityPatternCore.PatternDecoder decoder,
                                  TrinityPatternRecipeIdLookup recipeIdResolvers,
                                  ChangeListener changeListener) {
        if (index < 0) {
            throw new IllegalArgumentException("Trinity pattern slot index must not be negative: " + index);
        }
        this.index = index;
        this.decoder = decoder;
        this.recipeIdResolvers = recipeIdResolvers;
        this.changeListener = changeListener;
    }

    @Override
    public int index() {
        return this.index;
    }

    @Override
    public long revision() {
        return this.revision;
    }

    @Override
    public ItemStack pattern() {
        return this.pattern.copy();
    }

    @Override
    public boolean acceptsPattern(ItemStack pattern) {
        if (pattern.isEmpty()) {
            return false;
        }
        IMolecularAssemblerSupportedPattern decoded = this.decoder.decode(normalizePattern(pattern));
        return decoded != null && this.recipeIdResolvers.resolve(decoded).isPresent();
    }

    @Override
    public boolean trySetPattern(ItemStack pattern) {
        ItemStack normalized = normalizePattern(pattern);
        if (ItemStack.matches(this.pattern, normalized)) {
            return true;
        }
        if (normalized.isEmpty()) {
            this.pattern = ItemStack.EMPTY;
            this.installedDefinition = null;
            this.decodedPattern = null;
            collectUnusedDefinitions();
            changed(ChangeKind.CATALOG);
            changed(ChangeKind.PERSISTENT);
            return true;
        }

        IMolecularAssemblerSupportedPattern decoded = this.decoder.decode(normalized);
        if (decoded == null) {
            return false;
        }
        Optional<TrinityPatternRecipeIdResolution> resolved = this.recipeIdResolvers.resolve(decoded);
        if (resolved.isEmpty()) {
            return false;
        }
        TrinityPatternDefinition definition = findOrCreateDefinition(normalized, resolved.get());
        this.pattern = normalized;
        this.installedDefinition = definition;
        this.decodedPattern = decoded;
        collectUnusedDefinitions();
        changed(ChangeKind.CATALOG);
        changed(ChangeKind.PERSISTENT);
        return true;
    }

    @Nullable
    @Override
    public IMolecularAssemblerSupportedPattern decodedPattern() {
        return this.decodedPattern;
    }

    TrinityPatternDefinition requiredInstalledDefinition() {
        if (this.installedDefinition == null) {
            throw new IllegalStateException("Occupied Trinity pattern slot " + this.index + " has no definition");
        }
        return this.installedDefinition;
    }

    @Override
    public void refreshPatternCache() {
        if (this.pattern.isEmpty()) {
            return;
        }
        boolean persistentChange = false;
        IMolecularAssemblerSupportedPattern decoded = this.decoder.decode(this.pattern);
        if (decoded == null) {
            this.decodedPattern = null;
        } else {
            Optional<TrinityPatternRecipeIdResolution> resolved = this.recipeIdResolvers.resolve(decoded);
            if (resolved.isEmpty()) {
                this.decodedPattern = null;
            } else if (this.installedDefinition == null) {
                this.installedDefinition = findOrCreateDefinition(this.pattern, resolved.get());
                this.decodedPattern = decoded;
                persistentChange = true;
            } else if (!this.installedDefinition.matchesPattern(this.pattern)) {
                this.decodedPattern = null;
            } else if (this.installedDefinition.resolution().equals(resolved.get())) {
                this.decodedPattern = decoded;
            } else {
                this.decodedPattern = null;
            }
        }
        if (persistentChange) {
            collectUnusedDefinitions();
        }
        runtimeBindingRefreshed();
        if (persistentChange) {
            changed(ChangeKind.PERSISTENT);
        }
    }

    @Override
    public boolean enqueue(PatternRoute route,
                           ItemStack patternSnapshot,
                           List<ItemStack> inputs,
                           long queuedTick,
                           long count) {
        validateCount(count);
        ItemStack normalized = normalizePattern(patternSnapshot);
        if (this.pattern.isEmpty() || this.decodedPattern == null || this.installedDefinition == null ||
                !ItemStack.matches(this.pattern, normalized)) {
            return false;
        }
        return enqueueResolved(
                route,
                this.installedDefinition,
                TrinityCraftingBatch.InputSignature.copyOf(inputs),
                queuedTick,
                count);
    }

    boolean enqueueCached(PatternRoute route,
                          TrinityPatternDefinition expectedDefinition,
                          TrinityCraftingBatch.InputSignature inputs,
                          long queuedTick,
                          long count) {
        validateCount(count);
        if (this.decodedPattern == null || this.installedDefinition != expectedDefinition) {
            return false;
        }
        return enqueueResolved(route, expectedDefinition, inputs, queuedTick, count);
    }

    private boolean enqueueResolved(PatternRoute route,
                                    TrinityPatternDefinition definition,
                                    TrinityCraftingBatch.InputSignature inputs,
                                    long queuedTick,
                                    long count) {
        TrinityCraftingBatch incoming = TrinityCraftingBatch.resolved(
                queuedTick,
                route,
                definition,
                inputs,
                count,
                true);
        WorkMembership previousWork = this.workMembership;
        long mergeCount = this.queue.isEmpty() ? 0L : this.queue.getLast().mergeableCount(incoming);
        long remainingCount = count - mergeCount;
        TrinityCraftingBatch mergedTail = mergeCount > 0L ?
                this.queue.getLast().mergedWith(incoming, mergeCount) : null;
        TrinityCraftingBatch remainingBatch = remainingCount > 0L && mergeCount > 0L ?
                incoming.withCount(remainingCount) : null;
        boolean addsQueueGroup = mergeCount == 0L || remainingBatch != null;
        Integer currentHostGroups = this.queuedGroupsByHost.get(route.hostId());
        if (addsQueueGroup && currentHostGroups != null && currentHostGroups == Integer.MAX_VALUE) {
            throw new ArithmeticException("Trinity queued host-group count overflow");
        }
        int requiredRevisions = addsQueueGroup && currentHostGroups == null ? 2 : 1;
        if (this.revision > Long.MAX_VALUE - requiredRevisions) {
            throw new ArithmeticException("Trinity pattern slot revision overflow");
        }

        boolean membershipChanged = false;
        if (mergeCount > 0L) {
            this.queue.removeLast();
            this.queue.addLast(mergedTail);
            if (remainingBatch != null) {
                this.queue.addLast(remainingBatch);
                membershipChanged = incrementHostCount(this.queuedGroupsByHost, route.hostId());
            }
        } else {
            this.queue.addLast(incoming);
            membershipChanged = incrementHostCount(this.queuedGroupsByHost, route.hostId());
        }
        if (membershipChanged) {
            refreshWorkMembership();
        }
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
        return true;
    }

    private static void validateCount(long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("Queued crafting count must be positive: " + count);
        }
    }

    @Override
    public List<TrinityCraftingBatch> queuedBatches() {
        return this.queue.stream().map(TrinityCraftingBatch::copy).toList();
    }

    @Override
    public int queuedBatchCount() {
        return this.queue.size();
    }

    @Override
    public boolean hasQueuedWork() {
        return !this.queue.isEmpty();
    }

    @Override
    public boolean hasPendingOutputs() {
        return !this.pendingOutputs.isEmpty();
    }

    /**
     * Collects every host that owns a queued group or pending-output route in this physical slot.
     *
     * @return immutable host-membership snapshot for sparse core work indexes
     */
    Set<UUID> workHostIds() {
        return this.workHostIds;
    }

    /**
     * Returns the cached host set that currently owns at least one pending-output route.
     */
    Set<UUID> pendingOutputHostIds() {
        return this.workMembership.pendingOutputHosts();
    }

    @Override
    public List<PatternRoute> pendingOutputRoutes() {
        return List.copyOf(this.pendingOutputs.keySet());
    }

    @Override
    public List<TrinityItemAmount> pendingOutputs(PatternRoute route) {
        validateRoute(route);
        List<TrinityItemAmount> outputs = this.pendingOutputs.get(route);
        return outputs == null ? List.of() : List.copyOf(outputs);
    }

    @Override
    public TrinityPatternOutputRouter.PendingOutputCursor openPendingOutputCursor(PatternRoute route) {
        validateRoute(route);
        if (this.activePendingOutputCursor != null) {
            throw new IllegalStateException("Trinity pattern slot " + this.index + " already has an open output cursor");
        }
        PendingOutputCursorImpl cursor = new PendingOutputCursorImpl(route, this.pendingOutputs.get(route));
        this.activePendingOutputCursor = cursor;
        return cursor;
    }

    void appendPendingOutputs(PatternRoute route, List<TrinityItemAmount> outputs) {
        validateRoute(route);
        ensureNoPendingOutputCursor();
        if (outputs.isEmpty()) {
            return;
        }
        List<TrinityItemAmount> appended = List.copyOf(outputs);
        WorkMembership previousWork = this.workMembership;
        ArrayList<TrinityItemAmount> routeOutputs = this.pendingOutputs.get(route);
        if (routeOutputs == null) {
            routeOutputs = new ArrayList<>();
            this.pendingOutputs.put(route, routeOutputs);
            if (incrementHostCount(this.pendingRoutesByHost, route.hostId())) {
                refreshWorkMembership();
            }
        }
        for (TrinityItemAmount output : appended) {
            appendCountedOutput(routeOutputs, output);
        }
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    @Nullable
    @Override
    public TrinityCraftingBatch readyHead(long currentTick) {
        if (this.queue.isEmpty()) {
            return null;
        }
        TrinityCraftingBatch head = this.queue.getFirst();
        if (head.queuedTick() >= currentTick || this.decodedPattern == null ||
                this.installedDefinition == null || !head.matchesDefinition(this.installedDefinition)) {
            return null;
        }
        return head;
    }

    @Override
    public void completeHead(TrinityCraftingBatch completed, List<TrinityItemAmount> outputs) {
        if (this.queue.isEmpty() || this.queue.getFirst() != completed) {
            throw new IllegalStateException("Completed Trinity crafting group is no longer the FIFO head");
        }
        ensureNoPendingOutputCursor();
        List<TrinityItemAmount> completedOutputs = List.copyOf(outputs);
        WorkMembership previousWork = this.workMembership;
        boolean membershipChanged = false;
        if (!completedOutputs.isEmpty()) {
            PatternRoute route = completed.route();
            ArrayList<TrinityItemAmount> routeOutputs = this.pendingOutputs.get(route);
            if (routeOutputs == null) {
                routeOutputs = new ArrayList<>();
                this.pendingOutputs.put(route, routeOutputs);
                membershipChanged = incrementHostCount(this.pendingRoutesByHost, route.hostId());
            }
            for (TrinityItemAmount output : completedOutputs) {
                appendCountedOutput(routeOutputs, output);
            }
        }
        this.queue.removeFirst();
        membershipChanged |= decrementHostCount(this.queuedGroupsByHost, completed.route().hostId());
        if (membershipChanged) {
            refreshWorkMembership();
        }
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    @Override
    public void removeCompletedHead(TrinityCraftingBatch completed) {
        if (this.queue.isEmpty() || this.queue.getFirst() != completed) {
            throw new IllegalStateException("Completed Trinity crafting group is no longer the FIFO head");
        }
        WorkMembership previousWork = this.workMembership;
        this.queue.removeFirst();
        if (decrementHostCount(this.queuedGroupsByHost, completed.route().hostId())) {
            refreshWorkMembership();
        }
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    @Override
    public void clearQueuedBatches() {
        if (this.queue.isEmpty()) {
            return;
        }
        WorkMembership previousWork = this.workMembership;
        this.queue.clear();
        this.queuedGroupsByHost.clear();
        refreshWorkMembership();
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    @Override
    public void clearQueuedBatches(UUID hostId) {
        WorkMembership previousWork = this.workMembership;
        boolean changedQueue = this.queue.removeIf(batch -> hostId.equals(batch.route().hostId()));
        if (!changedQueue) {
            return;
        }
        this.queuedGroupsByHost.remove(hostId);
        refreshWorkMembership();
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    @Override
    public void replaceQueuedBatches(List<TrinityCraftingBatch> batches) {
        WorkMembership previousWork = this.workMembership;
        this.queue.clear();
        for (TrinityCraftingBatch batch : batches) {
            retainDefinition(batch.definition());
            this.queue.addLast(batch.copy());
        }
        rebuildQueuedHostCounts();
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    @Override
    public CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putInt(SLOT_TAG, this.index);
        if (!this.pattern.isEmpty()) {
            data.put(PATTERN_TAG, this.pattern.saveOptional(registries));
            data.putLong(INSTALLED_DEFINITION_ID_TAG, this.installedDefinition.id());
        }
        ListTag definitionList = new ListTag();
        for (TrinityPatternDefinition definition : this.definitions.values()) {
            definitionList.add(writeDefinition(definition, registries));
        }
        data.put(DEFINITIONS_TAG, definitionList);
        ListTag batchList = new ListTag();
        for (TrinityCraftingBatch batch : this.queue) {
            batchList.add(batch.writeToTag(registries));
        }
        data.put(BATCHES_TAG, batchList);
        data.put(PENDING_OUTPUTS_TAG, writePendingOutputs(registries));
        return data;
    }

    /**
     * Writes only the durable queued and pending-output state needed after an installed pattern is returned as a
     * separate mining drop.
     *
     * <p>
     * Queue definitions remain because batches refer to them, but the installed pattern and its definition are
     * deliberately omitted. A restored core can therefore refund retained work without silently resuming it before
     * a player installs a valid pattern again.
     * </p>
     *
     * @param registries item component registry access
     * @return complete slot state without an installed pattern
     */
    CompoundTag writeRetainedWorkToTag(HolderLookup.Provider registries) {
        ensureNoPendingOutputCursor();
        if (!hasQueuedWork() && !hasPendingOutputs()) {
            throw new IllegalStateException("Cannot serialize an empty Trinity retained-work slot " + this.index);
        }
        CompoundTag data = new CompoundTag();
        data.putInt(SLOT_TAG, this.index);

        Set<Long> queuedDefinitionIds = new HashSet<>();
        ListTag batchList = new ListTag();
        for (TrinityCraftingBatch batch : this.queue) {
            queuedDefinitionIds.add(batch.definition().id());
            batchList.add(batch.writeToTag(registries));
        }
        ListTag definitionList = new ListTag();
        for (TrinityPatternDefinition definition : this.definitions.values()) {
            if (queuedDefinitionIds.contains(definition.id())) {
                definitionList.add(writeDefinition(definition, registries));
            }
        }
        data.put(DEFINITIONS_TAG, definitionList);
        data.put(BATCHES_TAG, batchList);
        data.put(PENDING_OUTPUTS_TAG, writePendingOutputs(registries));
        return data;
    }

    /**
     * Atomically parses one persisted slot without mutating an existing core.
     *
     * @param data              persisted slot unit
     * @param decoder           supported-pattern decoder
     * @param recipeIdResolvers recipe identity registry
     * @param changeListener    typed owner callback
     * @param registries        item component registry access
     * @return fully validated detached slot
     */
    public static TrinityPatternSlotImpl readFromTag(CompoundTag data, TrinityPatternCore.PatternDecoder decoder,
                                                     TrinityPatternRecipeIdLookup recipeIdResolvers,
                                                     ChangeListener changeListener,
                                                     HolderLookup.Provider registries) {
        if (!data.contains(SLOT_TAG, Tag.TAG_INT) || !data.contains(DEFINITIONS_TAG, Tag.TAG_LIST) ||
                !data.contains(BATCHES_TAG, Tag.TAG_LIST) || !data.contains(PENDING_OUTPUTS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Trinity pattern slot is incomplete");
        }
        TrinityPatternSlotImpl slot = new TrinityPatternSlotImpl(
                data.getInt(SLOT_TAG), decoder, recipeIdResolvers, changeListener);
        Map<Long, IMolecularAssemblerSupportedPattern> validatedPatterns = new HashMap<>();
        ListTag definitionList = compoundList(data, DEFINITIONS_TAG);
        for (int index = 0; index < definitionList.size(); index++) {
            TrinityPatternDefinition definition = readDefinition(definitionList.getCompound(index), registries);
            IMolecularAssemblerSupportedPattern decoded = slot.validatePersistedDefinition(definition);
            if (decoded != null) {
                validatedPatterns.put(definition.id(), decoded);
            }
            slot.retainParsedDefinition(definition);
        }
        boolean hasPattern = data.contains(PATTERN_TAG, Tag.TAG_COMPOUND);
        if (hasPattern != data.contains(INSTALLED_DEFINITION_ID_TAG, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Trinity pattern slot has an incomplete installed definition");
        }
        if (hasPattern) {
            slot.pattern = normalizePattern(ItemStack.parseOptional(registries, data.getCompound(PATTERN_TAG)));
            long installedId = data.getLong(INSTALLED_DEFINITION_ID_TAG);
            slot.installedDefinition = slot.requiredDefinition(installedId);
            if (!slot.installedDefinition.matchesPattern(slot.pattern)) {
                throw new IllegalArgumentException("Installed Trinity pattern does not match its definition");
            }
        }
        ListTag batchList = compoundList(data, BATCHES_TAG);
        for (int index = 0; index < batchList.size(); index++) {
            CompoundTag batchData = batchList.getCompound(index);
            if (!batchData.contains(DEFINITION_ID_TAG, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Queued crafting group is missing its definition reference");
            }
            TrinityPatternDefinition definition = slot.requiredDefinition(batchData.getLong(DEFINITION_ID_TAG));
            slot.queue.addLast(TrinityCraftingBatch.readFromTag(batchData, definition, registries));
        }
        slot.readPendingOutputs(compoundList(data, PENDING_OUTPUTS_TAG), registries);
        slot.validateDefinitionReferences();
        if (hasPattern) {
            slot.bindValidatedInstalledPattern(validatedPatterns.get(slot.installedDefinition.id()));
        }
        slot.rebuildWorkMembership();
        return slot;
    }

    boolean hasPersistentState() {
        return !this.pattern.isEmpty() || !this.queue.isEmpty() || !this.pendingOutputs.isEmpty();
    }

    void ensureCanApplyValidatedState(TrinityPatternSlotImpl state) {
        if (state.index != this.index) {
            throw new IllegalArgumentException(
                    "Validated Trinity slot " + state.index + " cannot replace physical slot " + this.index);
        }
        ensureNoPendingOutputCursor();
        if (this.revision == Long.MAX_VALUE) {
            throw new ArithmeticException("Trinity pattern slot revision overflow");
        }
    }

    void applyValidatedState(TrinityPatternSlotImpl state) {
        this.pattern = state.pattern;
        this.installedDefinition = state.installedDefinition;
        this.decodedPattern = state.decodedPattern;
        this.nextDefinitionId = state.nextDefinitionId;
        this.definitions = state.definitions;
        this.queue = state.queue;
        this.pendingOutputs = state.pendingOutputs;
        this.queuedGroupsByHost = state.queuedGroupsByHost;
        this.pendingRoutesByHost = state.pendingRoutesByHost;
        this.workMembership = state.workMembership;
        this.workHostIds = state.workHostIds;
        this.revision++;
    }

    boolean matchesWorkMembership(TrinityPatternSlotImpl state) {
        return this.workMembership.equals(state.workMembership);
    }

    WorkState captureWorkState() {
        ensureNoPendingOutputCursor();
        LinkedHashMap<PatternRoute, List<TrinityItemAmount>> outputSnapshot = new LinkedHashMap<>();
        for (Map.Entry<PatternRoute, ArrayList<TrinityItemAmount>> entry : this.pendingOutputs.entrySet()) {
            outputSnapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new WorkState(queuedBatches(), outputSnapshot);
    }

    boolean matchesWorkState(WorkState state) {
        return queuesMatch(this.queue, state.batches()) && pendingOutputsMatch(this.pendingOutputs, state.pendingOutputs());
    }

    void clearRefundableWork(@Nullable UUID hostId) {
        ensureNoPendingOutputCursor();
        WorkMembership previousWork = this.workMembership;
        boolean queueChanged = this.queue.removeIf(batch -> hostId == null || hostId.equals(batch.route().hostId()));
        boolean outputsChanged = this.pendingOutputs.entrySet().removeIf(
                entry -> hostId == null || hostId.equals(entry.getKey().hostId()));
        if (!queueChanged && !outputsChanged) {
            return;
        }
        if (hostId == null) {
            this.queuedGroupsByHost.clear();
            this.pendingRoutesByHost.clear();
        } else {
            if (queueChanged) {
                this.queuedGroupsByHost.remove(hostId);
            }
            if (outputsChanged) {
                this.pendingRoutesByHost.remove(hostId);
            }
        }
        refreshWorkMembership();
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    void restoreWorkState(WorkState state) {
        ensureNoPendingOutputCursor();
        WorkMembership previousWork = this.workMembership;
        this.queue.clear();
        for (TrinityCraftingBatch batch : state.batches()) {
            retainDefinition(batch.definition());
            this.queue.addLast(batch.copy());
        }
        this.pendingOutputs.clear();
        this.pendingOutputs.putAll(copyPendingOutputs(state.pendingOutputs()));
        rebuildWorkMembership();
        collectUnusedDefinitions();
        changed(ChangeKind.PERSISTENT);
        notifyWorkMembershipChanged(previousWork);
    }

    private TrinityPatternDefinition findOrCreateDefinition(
            ItemStack pattern, TrinityPatternRecipeIdResolution resolution) {
        for (TrinityPatternDefinition definition : this.definitions.values()) {
            if (definition.matchesPattern(pattern) && definition.resolution().equals(resolution)) {
                return definition;
            }
        }
        long definitionId = this.nextDefinitionId;
        this.nextDefinitionId = Math.incrementExact(this.nextDefinitionId);
        TrinityPatternDefinition definition = TrinityPatternDefinition.resolved(definitionId, pattern, resolution);
        this.definitions.put(definitionId, definition);
        return definition;
    }

    private IMolecularAssemblerSupportedPattern validatePersistedDefinition(TrinityPatternDefinition definition) {
        IMolecularAssemblerSupportedPattern decoded = this.decoder.decode(definition.pattern());
        Optional<TrinityPatternRecipeIdResolution> resolution = decoded == null ? Optional.empty() : this.recipeIdResolvers.resolve(decoded);
        if (resolution.isEmpty() || !resolution.get().equals(definition.resolution())) {
            throw new IllegalArgumentException(
                    "Trinity pattern definition " + definition.id() + " does not match its encoded recipe identity");
        }
        return decoded;
    }

    private void bindValidatedInstalledPattern(IMolecularAssemblerSupportedPattern decoded) {
        this.decodedPattern = decoded;
    }

    private void retainParsedDefinition(TrinityPatternDefinition definition) {
        if (this.definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate Trinity pattern definition ID " + definition.id());
        }
        for (TrinityPatternDefinition retained : this.definitions.values()) {
            if (retained.matchesPattern(definition.pattern()) &&
                    retained.resolution().equals(definition.resolution())) {
                throw new IllegalArgumentException(
                        "Duplicate Trinity pattern definition identity " + definition.id());
            }
        }
        retainDefinition(definition);
    }

    private void retainDefinition(TrinityPatternDefinition definition) {
        TrinityPatternDefinition previous = this.definitions.putIfAbsent(definition.id(), definition);
        if (previous != null && (!previous.matchesPattern(definition.pattern()) ||
                !previous.resolution().equals(definition.resolution()))) {
            throw new IllegalArgumentException("Conflicting Trinity pattern definition ID " + definition.id());
        }
        if (definition.id() >= this.nextDefinitionId) {
            this.nextDefinitionId = Math.incrementExact(definition.id());
        }
    }

    private TrinityPatternDefinition requiredDefinition(long definitionId) {
        TrinityPatternDefinition definition = this.definitions.get(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Trinity pattern definition ID " + definitionId);
        }
        return definition;
    }

    private void collectUnusedDefinitions() {
        Set<Long> retained = new HashSet<>();
        if (this.installedDefinition != null) {
            retained.add(this.installedDefinition.id());
        }
        for (TrinityCraftingBatch batch : this.queue) {
            retained.add(batch.definitionId());
        }
        this.definitions.keySet().removeIf(definitionId -> !retained.contains(definitionId));
    }

    private void validateDefinitionReferences() {
        Set<Long> referenced = new HashSet<>();
        if (this.installedDefinition != null) {
            referenced.add(this.installedDefinition.id());
        }
        for (TrinityCraftingBatch batch : this.queue) {
            referenced.add(batch.definitionId());
        }
        if (!referenced.equals(this.definitions.keySet())) {
            throw new IllegalArgumentException("Trinity pattern slot contains unreferenced definitions");
        }
    }

    private ListTag writePendingOutputs(HolderLookup.Provider registries) {
        ListTag groups = new ListTag();
        for (Map.Entry<PatternRoute, ArrayList<TrinityItemAmount>> group : this.pendingOutputs.entrySet()) {
            CompoundTag groupData = new CompoundTag();
            groupData.put(ROUTE_TAG, group.getKey().writeToTag());
            ListTag outputs = new ListTag();
            for (TrinityItemAmount output : group.getValue()) {
                CompoundTag outputData = new CompoundTag();
                outputData.put(PROTOTYPE_TAG, output.key().toStack(1).saveOptional(registries));
                outputData.putLong(AMOUNT_TAG, output.amount());
                outputs.add(outputData);
            }
            groupData.put(OUTPUTS_TAG, outputs);
            groups.add(groupData);
        }
        return groups;
    }

    private void readPendingOutputs(ListTag groups, HolderLookup.Provider registries) {
        Set<PatternRoute> populated = new HashSet<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            CompoundTag groupData = groups.getCompound(groupIndex);
            if (!groupData.contains(ROUTE_TAG, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Trinity pending-output group is missing its route");
            }
            PatternRoute route = PatternRoute.readFromTag(groupData.getCompound(ROUTE_TAG));
            validateRoute(route);
            if (!populated.add(route)) {
                throw new IllegalArgumentException("Duplicate Trinity pending-output route " + route);
            }
            ListTag outputEntries = compoundList(groupData, OUTPUTS_TAG);
            if (outputEntries.isEmpty()) {
                throw new IllegalArgumentException("Trinity pending-output route " + route + " is empty");
            }
            ArrayList<TrinityItemAmount> outputs = new ArrayList<>(outputEntries.size());
            for (int outputIndex = 0; outputIndex < outputEntries.size(); outputIndex++) {
                CompoundTag outputData = outputEntries.getCompound(outputIndex);
                if (!outputData.contains(PROTOTYPE_TAG, Tag.TAG_COMPOUND) ||
                        !outputData.contains(AMOUNT_TAG, Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("Trinity pending-output entry is incomplete");
                }
                ItemStack prototype = ItemStack.parseOptional(registries, outputData.getCompound(PROTOTYPE_TAG));
                if (prototype.isEmpty() || prototype.getCount() != 1) {
                    throw new IllegalArgumentException(
                            "Trinity pending-output prototype must contain exactly one item");
                }
                outputs.add(TrinityItemAmount.of(prototype).withAmount(outputData.getLong(AMOUNT_TAG)));
            }
            this.pendingOutputs.put(route, outputs);
        }
    }

    private void validateRoute(PatternRoute route) {
        if (route.slot() != this.index) {
            throw new IllegalArgumentException(
                    "Pattern route slot " + route.slot() + " does not match physical slot " + this.index);
        }
    }

    private void ensureNoPendingOutputCursor() {
        if (this.activePendingOutputCursor != null) {
            throw new IllegalStateException(
                    "Trinity pattern slot " + this.index + " has an active pending-output cursor");
        }
    }

    private void changed(ChangeKind kind) {
        this.revision = Math.incrementExact(this.revision);
        this.changeListener.onChanged(new Change(this.index, kind));
    }

    private void runtimeBindingRefreshed() {
        this.changeListener.onChanged(new Change(this.index, ChangeKind.RUNTIME_BINDING));
    }

    private void rebuildWorkMembership() {
        this.queuedGroupsByHost.clear();
        for (TrinityCraftingBatch batch : this.queue) {
            incrementHostCount(this.queuedGroupsByHost, batch.route().hostId());
        }
        this.pendingRoutesByHost.clear();
        for (PatternRoute route : this.pendingOutputs.keySet()) {
            incrementHostCount(this.pendingRoutesByHost, route.hostId());
        }
        refreshWorkMembership();
    }

    private void rebuildQueuedHostCounts() {
        this.queuedGroupsByHost.clear();
        for (TrinityCraftingBatch batch : this.queue) {
            incrementHostCount(this.queuedGroupsByHost, batch.route().hostId());
        }
        refreshWorkMembership();
    }

    private void refreshWorkMembership() {
        Set<UUID> queuedHosts = Set.copyOf(this.queuedGroupsByHost.keySet());
        Set<UUID> pendingOutputHosts = Set.copyOf(this.pendingRoutesByHost.keySet());
        this.workMembership = new WorkMembership(queuedHosts, pendingOutputHosts);
        HashSet<UUID> combinedHosts = new HashSet<>(queuedHosts);
        combinedHosts.addAll(pendingOutputHosts);
        this.workHostIds = Set.copyOf(combinedHosts);
    }

    private void notifyWorkMembershipChanged(WorkMembership previousWork) {
        if (!previousWork.equals(this.workMembership)) {
            changed(ChangeKind.WORK);
        }
    }

    private static boolean incrementHostCount(Map<UUID, Integer> counts, UUID hostId) {
        Integer previous = counts.get(hostId);
        if (previous == null) {
            counts.put(hostId, 1);
            return true;
        }
        counts.put(hostId, Math.incrementExact(previous));
        return false;
    }

    private static boolean decrementHostCount(Map<UUID, Integer> counts, UUID hostId) {
        Integer previous = counts.get(hostId);
        if (previous == null) {
            throw new IllegalStateException("Missing Trinity work membership for host " + hostId);
        }
        if (previous == 1) {
            counts.remove(hostId);
            return true;
        }
        counts.put(hostId, previous - 1);
        return false;
    }

    private static void appendCountedOutput(ArrayList<TrinityItemAmount> outputs, TrinityItemAmount output) {
        long remaining = output.amount();
        if (!outputs.isEmpty()) {
            TrinityItemAmount previous = outputs.getLast();
            if (previous.key().equals(output.key()) && previous.amount() < Long.MAX_VALUE) {
                long merged = Math.min(remaining, Long.MAX_VALUE - previous.amount());
                outputs.set(outputs.size() - 1, previous.withAmount(previous.amount() + merged));
                remaining -= merged;
            }
        }
        if (remaining > 0L) {
            outputs.add(output.withAmount(remaining));
        }
    }

    private static LinkedHashMap<PatternRoute, ArrayList<TrinityItemAmount>> copyPendingOutputs(
            Map<PatternRoute, ? extends List<TrinityItemAmount>> outputs) {
        LinkedHashMap<PatternRoute, ArrayList<TrinityItemAmount>> copy = new LinkedHashMap<>();
        for (Map.Entry<PatternRoute, ? extends List<TrinityItemAmount>> entry : outputs.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static boolean queuesMatch(Iterable<TrinityCraftingBatch> current,
                                       List<TrinityCraftingBatch> captured) {
        ArrayList<TrinityCraftingBatch> currentBatches = new ArrayList<>();
        current.forEach(currentBatches::add);
        if (currentBatches.size() != captured.size()) {
            return false;
        }
        for (int index = 0; index < currentBatches.size(); index++) {
            TrinityCraftingBatch left = currentBatches.get(index);
            TrinityCraftingBatch right = captured.get(index);
            if (left.count() != right.count() || left.definitionId() != right.definitionId() ||
                    left.mergeable() != right.mergeable() || left.queuedTick() != right.queuedTick() ||
                    !left.route().equals(right.route()) || !stackListsMatch(left.inputs(), right.inputs())) {
                return false;
            }
        }
        return true;
    }

    private static boolean pendingOutputsMatch(
            Map<PatternRoute, ? extends List<TrinityItemAmount>> current,
            Map<PatternRoute, ? extends List<TrinityItemAmount>> captured) {
        return current.equals(captured);
    }

    private static CompoundTag writeDefinition(TrinityPatternDefinition definition,
                                               HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putLong(DEFINITION_ID_TAG, definition.id());
        data.put(STACK_TAG, definition.pattern().saveOptional(registries));
        data.putString(RESOLVER_ID_TAG, definition.resolution().resolverId().toString());
        data.putString(RECIPE_ID_TAG, definition.resolution().recipeId().toString());
        return data;
    }

    private static TrinityPatternDefinition readDefinition(CompoundTag data, HolderLookup.Provider registries) {
        if (!data.contains(DEFINITION_ID_TAG, Tag.TAG_LONG) || !data.contains(STACK_TAG, Tag.TAG_COMPOUND) ||
                !data.contains(RESOLVER_ID_TAG, Tag.TAG_STRING) || !data.contains(RECIPE_ID_TAG, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Trinity pattern definition is incomplete");
        }
        TrinityPatternRecipeIdResolution resolution = new TrinityPatternRecipeIdResolution(
                ResourceLocation.parse(data.getString(RESOLVER_ID_TAG)),
                ResourceLocation.parse(data.getString(RECIPE_ID_TAG)));
        long definitionId = data.getLong(DEFINITION_ID_TAG);
        ItemStack pattern = normalizePattern(ItemStack.parseOptional(registries, data.getCompound(STACK_TAG)));
        return TrinityPatternDefinition.resolved(definitionId, pattern, resolution);
    }

    private static ListTag compoundList(CompoundTag data, String name) {
        if (!(data.get(name) instanceof ListTag entries) ||
                !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Trinity pattern slot requires compound list '" + name + "'");
        }
        return entries;
    }

    private static boolean stackListsMatch(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.matches(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack normalizePattern(ItemStack pattern) {
        if (pattern.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack normalized = pattern.copy();
        normalized.setCount(1);
        return normalized;
    }

    record WorkState(List<TrinityCraftingBatch> batches,
                     Map<PatternRoute, List<TrinityItemAmount>> pendingOutputs) {

        WorkState {
            batches = batches.stream().map(TrinityCraftingBatch::copy).toList();
            LinkedHashMap<PatternRoute, List<TrinityItemAmount>> outputCopy = new LinkedHashMap<>();
            for (Map.Entry<PatternRoute, List<TrinityItemAmount>> entry : pendingOutputs.entrySet()) {
                outputCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            pendingOutputs = Collections.unmodifiableMap(outputCopy);
        }
    }

    /**
     * Separates queued and pending host membership so sparse indexes are refreshed during queue-to-output handoff.
     */
    private record WorkMembership(Set<UUID> queuedHosts, Set<UUID> pendingOutputHosts) {
    }

    private final class PendingOutputCursorImpl implements TrinityPatternOutputRouter.PendingOutputCursor {

        private final PatternRoute route;
        @Nullable
        private final ArrayList<TrinityItemAmount> outputs;
        @Nullable
        private final ListIterator<TrinityItemAmount> iterator;
        @Nullable
        private TrinityItemAmount current;
        private boolean currentSelected;
        private boolean closed;

        private PendingOutputCursorImpl(PatternRoute route, @Nullable ArrayList<TrinityItemAmount> outputs) {
            this.route = route;
            this.outputs = outputs;
            this.iterator = outputs == null ? null : outputs.listIterator();
        }

        @Override
        public boolean advance() {
            ensureOpen();
            this.current = null;
            this.currentSelected = false;
            if (this.iterator == null || !this.iterator.hasNext()) {
                return false;
            }
            this.current = this.iterator.next();
            this.currentSelected = true;
            return true;
        }

        @Override
        public TrinityItemAmount current() {
            ensureCurrent();
            return this.current;
        }

        @Override
        public void consumeCurrent(long amount) {
            ensureCurrent();
            if (amount <= 0L || amount > this.current.amount()) {
                throw new IllegalArgumentException(
                        "Consumed Trinity pending-output amount must be between one and " + this.current.amount());
            }
            boolean removedRoute = false;
            WorkMembership previousWork = TrinityPatternSlotImpl.this.workMembership;
            if (amount == this.current.amount()) {
                this.iterator.remove();
                this.current = null;
                this.currentSelected = false;
                if (this.outputs.isEmpty()) {
                    if (!TrinityPatternSlotImpl.this.pendingOutputs.remove(this.route, this.outputs)) {
                        throw new IllegalStateException("Missing Trinity pending-output route " + this.route);
                    }
                    removedRoute = true;
                    if (decrementHostCount(
                            TrinityPatternSlotImpl.this.pendingRoutesByHost,
                            this.route.hostId())) {
                        refreshWorkMembership();
                    }
                }
            } else {
                this.current = this.current.withAmount(this.current.amount() - amount);
                this.iterator.set(this.current);
            }
            changed(ChangeKind.PERSISTENT);
            if (removedRoute) {
                notifyWorkMembershipChanged(previousWork);
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.current = null;
            this.currentSelected = false;
            if (TrinityPatternSlotImpl.this.activePendingOutputCursor != this) {
                throw new IllegalStateException("Trinity pending-output cursor ownership was lost");
            }
            TrinityPatternSlotImpl.this.activePendingOutputCursor = null;
        }

        private void ensureOpen() {
            if (this.closed) {
                throw new IllegalStateException("Trinity pending-output cursor is closed");
            }
        }

        private void ensureCurrent() {
            ensureOpen();
            if (!this.currentSelected) {
                throw new IllegalStateException("Trinity pending-output cursor has no selected entry");
            }
        }
    }
}
