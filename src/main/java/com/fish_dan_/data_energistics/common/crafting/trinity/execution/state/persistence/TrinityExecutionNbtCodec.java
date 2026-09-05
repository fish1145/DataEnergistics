package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.Firing;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.RepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.Stage;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.WaitKind;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict NBT codec for durable Trinity execution snapshots.
 */
public final class TrinityExecutionNbtCodec {

    private static final int LONG_AMOUNT_SCHEMA = 5;
    private static final int BIG_INTEGER_SCHEMA = 6;
    private static final int SAME_ITEM_SCHEMA = 7;
    private static final int SCHEMA = 8;
    private static final String EXACT_BINDINGS_TAG = "exact_bindings";
    private static final int MAX_BIG_INTEGER_BYTES = 512;
    private static final String PLAN_KIND = "trinity_compact";
    private static final String SCHEMA_TAG = "schema_version";
    private static final String PLAN_KIND_TAG = "plan_kind";
    private static final String CATALOG_REVISION_TAG = "catalog_revision";
    private static final String QUANTITY_MODE_TAG = "quantity_mode";
    private static final String SAME_ITEM_POLICY_TAG = "same_item_policy";
    private static final String TARGET_KEY_TAG = "target_key";
    private static final String TARGET_AMOUNT_TAG = "target_amount";
    private static final String STATUS_TAG = "status";
    private static final String FAILURE_REASON_TAG = "failure_reason";
    private static final String GENERATION_TAG = "generation";
    private static final String STAGES_TAG = "stages";
    private static final String STAGE_ORDER_TAG = "stage_order";
    private static final String REPEAT_BLOCKS_TAG = "repeat_blocks";
    private static final String SEED_RESERVE_TAG = "seed_reserve";
    private static final String COMPLETION_SEALED_TAG = "completion_sealed";
    private static final String COMPLETION_BUFFER_TAG = "completion_buffer";
    private static final String ACTUAL_FINAL_OUTPUTS_TAG = "actual_final_outputs";
    private static final String DELIVERY_REMAINING_TAG = "delivery_remaining";
    private static final String LEDGER_TAG = "borrowing_ledger";
    private static final String SAVED_AT_TICK_TAG = "saved_at_tick";
    private static final String BUDGET_RETRY_AT_TAG = "budget_retry_at";

    private static final String INDEX_TAG = "index";
    private static final String CYCLE_TAG = "cycle";
    private static final String DEPENDENCIES_TAG = "dependencies";
    private static final String CURRENT_FIRING_TAG = "current_firing";
    private static final String COMPLETED_TAG = "completed";
    private static final String INPUT_KEYS_TAG = "input_keys";
    private static final String WAITING_KEYS_TAG = "waiting_keys";
    private static final String WAIT_KIND_TAG = "wait_kind";
    private static final String RETRY_AT_TAG = "retry_at";
    private static final String NEXT_DYNAMIC_DELAY_TAG = "next_dynamic_delay";
    private static final String NEXT_PROVIDER_DELAY_TAG = "next_provider_delay";
    private static final String RETRY_VERSION_TAG = "retry_version";
    private static final String FIRINGS_TAG = "firings";
    private static final String REQUIRED_AT_START_TAG = "required_at_start";
    private static final String NET_CHANGE_TAG = "net_change";

    private static final String DEFINITION_TAG = "definition";
    private static final String PUBLICATION_TAG = "publication";
    private static final String PRIMARY_OUTPUT_TAG = "primary_output";
    private static final String VARIANT_ORDINAL_TAG = "variant_ordinal";
    private static final String PLANNED_COUNT_TAG = "planned_count";
    private static final String OUTPUTS_TAG = "outputs";
    private static final String REMAINING_COUNT_TAG = "remaining_count";
    private static final String INITIALIZED_TAG = "initialized";

    private static final String STAGE_ORDER_ENTRY_TAG = "stage_order";
    private static final String REMAINING_REPETITIONS_TAG = "remaining_repetitions";
    private static final String CURSOR_TAG = "cursor";
    private static final String WAVE_COUNT_TAG = "wave_count";

    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";

    private static final Set<String> LEGACY_ROOT_FIELDS = Set.of(
            SCHEMA_TAG,
            PLAN_KIND_TAG,
            CATALOG_REVISION_TAG,
            QUANTITY_MODE_TAG,
            TARGET_KEY_TAG,
            TARGET_AMOUNT_TAG,
            STATUS_TAG,
            FAILURE_REASON_TAG,
            GENERATION_TAG,
            STAGES_TAG,
            STAGE_ORDER_TAG,
            REPEAT_BLOCKS_TAG,
            SEED_RESERVE_TAG,
            COMPLETION_SEALED_TAG,
            COMPLETION_BUFFER_TAG,
            ACTUAL_FINAL_OUTPUTS_TAG,
            DELIVERY_REMAINING_TAG,
            LEDGER_TAG,
            SAVED_AT_TICK_TAG,
            BUDGET_RETRY_AT_TAG);
    private static final Set<String> ROOT_FIELDS = Set.of(
            SCHEMA_TAG,
            PLAN_KIND_TAG,
            CATALOG_REVISION_TAG,
            QUANTITY_MODE_TAG,
            SAME_ITEM_POLICY_TAG,
            TARGET_KEY_TAG,
            TARGET_AMOUNT_TAG,
            STATUS_TAG,
            FAILURE_REASON_TAG,
            GENERATION_TAG,
            STAGES_TAG,
            STAGE_ORDER_TAG,
            REPEAT_BLOCKS_TAG,
            SEED_RESERVE_TAG,
            COMPLETION_SEALED_TAG,
            COMPLETION_BUFFER_TAG,
            ACTUAL_FINAL_OUTPUTS_TAG,
            DELIVERY_REMAINING_TAG,
            LEDGER_TAG,
            SAVED_AT_TICK_TAG,
            BUDGET_RETRY_AT_TAG);
    private static final Set<String> STAGE_FIELDS = Set.of(
            INDEX_TAG,
            CYCLE_TAG,
            DEPENDENCIES_TAG,
            CURRENT_FIRING_TAG,
            COMPLETED_TAG,
            INPUT_KEYS_TAG,
            WAITING_KEYS_TAG,
            WAIT_KIND_TAG,
            RETRY_AT_TAG,
            NEXT_DYNAMIC_DELAY_TAG,
            NEXT_PROVIDER_DELAY_TAG,
            RETRY_VERSION_TAG,
            FIRINGS_TAG,
            REQUIRED_AT_START_TAG,
            NET_CHANGE_TAG);
    private static final Set<String> LEGACY_FIRING_FIELDS = Set.of(
            DEFINITION_TAG,
            PUBLICATION_TAG,
            PRIMARY_OUTPUT_TAG,
            VARIANT_ORDINAL_TAG,
            PLANNED_COUNT_TAG,
            OUTPUTS_TAG,
            REMAINING_COUNT_TAG,
            INITIALIZED_TAG);
    private static final Set<String> FIRING_FIELDS = Set.of(
            DEFINITION_TAG, PUBLICATION_TAG, PRIMARY_OUTPUT_TAG, VARIANT_ORDINAL_TAG,
            PLANNED_COUNT_TAG, OUTPUTS_TAG, REMAINING_COUNT_TAG, INITIALIZED_TAG, EXACT_BINDINGS_TAG);
    private static final Set<String> REPEAT_FIELDS = Set.of(
            INDEX_TAG,
            STAGE_ORDER_ENTRY_TAG,
            REMAINING_REPETITIONS_TAG,
            CURSOR_TAG,
            WAVE_COUNT_TAG);
    private static final Set<String> AMOUNT_FIELDS = Set.of(KEY_TAG, AMOUNT_TAG);

    private TrinityExecutionNbtCodec() {}

    /**
     * Encodes every durable execution field without transient queues or indexes.
     *
     * @param snapshot   validated execution snapshot
     * @param registries server registry lookup used by AE key codecs
     * @return strict current-schema NBT
     */
    public static CompoundTag encode(TrinityExecutionSnapshot snapshot, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_TAG, SCHEMA);
        root.putString(PLAN_KIND_TAG, PLAN_KIND);
        root.putLong(CATALOG_REVISION_TAG, snapshot.catalogRevision());
        root.putString(QUANTITY_MODE_TAG, snapshot.quantityMode().name());
        root.put(SAME_ITEM_POLICY_TAG, saveKeys(snapshot.sameItemPolicy().representatives(), registries));
        root.put(TARGET_KEY_TAG, snapshot.targetKey().toTagGeneric(registries));
        root.putLong(TARGET_AMOUNT_TAG, snapshot.targetAmount());
        root.putString(STATUS_TAG, snapshot.status().name());
        root.putString(FAILURE_REASON_TAG, snapshot.failureReason());
        root.putLong(GENERATION_TAG, snapshot.generation());
        root.put(STAGES_TAG, saveStages(snapshot.stages(), registries));
        root.put(STAGE_ORDER_TAG, intArray(snapshot.stageOrder()));
        root.put(REPEAT_BLOCKS_TAG, saveRepeatBlocks(snapshot.repeatBlocks()));
        root.put(SEED_RESERVE_TAG, saveBigAmounts(snapshot.seedReserve(), registries));
        root.putBoolean(COMPLETION_SEALED_TAG, snapshot.completionSealed());
        root.putLong(COMPLETION_BUFFER_TAG, snapshot.completionBuffer());
        root.put(ACTUAL_FINAL_OUTPUTS_TAG, saveLongAmounts(snapshot.actualFinalOutputs(), registries));
        root.putLong(DELIVERY_REMAINING_TAG, snapshot.deliveryRemaining());
        root.put(LEDGER_TAG, TrinityBorrowingLedgerNbtCodec.encode(snapshot.borrowingEntries(), registries));
        root.putLong(SAVED_AT_TICK_TAG, snapshot.savedAtTick());
        root.putLong(BUDGET_RETRY_AT_TAG, snapshot.budgetRetryAt());
        return root;
    }

    /**
     * Decodes supported strict execution NBT into an immutable persistence model.
     *
     * @param tag        encoded execution
     * @param registries server registry lookup used by AE key codecs
     * @return decoded durable snapshot
     */
    public static TrinityExecutionSnapshot decode(CompoundTag tag, HolderLookup.Provider registries) {
        requireType(tag, SCHEMA_TAG, Tag.TAG_INT, "execution schema");
        int schema = tag.getInt(SCHEMA_TAG);
        if (schema != LONG_AMOUNT_SCHEMA && schema != BIG_INTEGER_SCHEMA && schema != SAME_ITEM_SCHEMA && schema != SCHEMA) {
            throw new IllegalArgumentException("Unsupported Trinity execution schema");
        }
        requireFields(tag, schema >= SAME_ITEM_SCHEMA ? ROOT_FIELDS : LEGACY_ROOT_FIELDS, "execution root");
        requireType(tag, PLAN_KIND_TAG, Tag.TAG_STRING, "execution plan kind");
        requireType(tag, CATALOG_REVISION_TAG, Tag.TAG_LONG, "execution catalog revision");
        requireType(tag, QUANTITY_MODE_TAG, Tag.TAG_STRING, "execution quantity mode");
        if (schema >= SAME_ITEM_SCHEMA) {
            requireType(tag, SAME_ITEM_POLICY_TAG, Tag.TAG_LIST, "execution same-item policy");
        }
        requireType(tag, ACTUAL_FINAL_OUTPUTS_TAG, Tag.TAG_LIST, "execution actual final outputs");
        requireType(tag, TARGET_KEY_TAG, Tag.TAG_COMPOUND, "execution target key");
        requireType(tag, TARGET_AMOUNT_TAG, Tag.TAG_LONG, "execution target amount");
        requireType(tag, STATUS_TAG, Tag.TAG_STRING, "execution status");
        requireType(tag, FAILURE_REASON_TAG, Tag.TAG_STRING, "execution failure reason");
        requireType(tag, GENERATION_TAG, Tag.TAG_LONG, "execution generation");
        requireType(tag, STAGE_ORDER_TAG, Tag.TAG_INT_ARRAY, "execution stage order");
        requireType(tag, COMPLETION_SEALED_TAG, Tag.TAG_BYTE, "execution completion seal");
        requireType(tag, COMPLETION_BUFFER_TAG, Tag.TAG_LONG, "execution completion buffer");
        requireType(tag, DELIVERY_REMAINING_TAG, Tag.TAG_LONG, "execution delivery remainder");
        requireType(tag, LEDGER_TAG, Tag.TAG_COMPOUND, "execution borrowing ledger");
        requireType(tag, SAVED_AT_TICK_TAG, Tag.TAG_LONG, "execution save tick");
        requireType(tag, BUDGET_RETRY_AT_TAG, Tag.TAG_LONG, "execution budget retry");
        if (!PLAN_KIND.equals(tag.getString(PLAN_KIND_TAG))) {
            throw new IllegalArgumentException("Unsupported Trinity execution plan kind");
        }

        return new TrinityExecutionSnapshot(
                nonNegative(tag.getLong(CATALOG_REVISION_TAG), "catalog revision"),
                parseEnum(CraftingQuantityMode.class, tag.getString(QUANTITY_MODE_TAG), "quantity mode"),
                schema >= SAME_ITEM_SCHEMA ? readSameItemPolicy(tag, registries) : TrinitySameItemPolicy.empty(),
                decodeKey(tag.getCompound(TARGET_KEY_TAG), registries, "execution target"),
                tag.getLong(TARGET_AMOUNT_TAG),
                parseEnum(TrinityPlanExecution.Status.class, tag.getString(STATUS_TAG), "execution status"),
                tag.getString(FAILURE_REASON_TAG),
                nonNegative(tag.getLong(GENERATION_TAG), "generation"),
                readStages(tag, registries, schema),
                readStageOrder(tag),
                readRepeatBlocks(tag, schema),
                readBigAmountMap(tag, SEED_RESERVE_TAG, registries, "seed reserve", false, schema),
                tag.getBoolean(COMPLETION_SEALED_TAG),
                tag.getLong(COMPLETION_BUFFER_TAG),
                readLongAmountMap(tag, ACTUAL_FINAL_OUTPUTS_TAG, registries, "actual final output"),
                tag.getLong(DELIVERY_REMAINING_TAG),
                TrinityBorrowingLedgerNbtCodec.decode(tag.getCompound(LEDGER_TAG), registries),
                nonNegative(tag.getLong(SAVED_AT_TICK_TAG), "save tick"),
                tag.getLong(BUDGET_RETRY_AT_TAG));
    }

    /**
     * Conservatively recovers item ownership from a plan snapshot whose enclosing job could not be restored.
     *
     * <p>
     * Each actual variant is decoded independently so one damaged entry cannot discard other
     * machine-returned items. The numeric exact remainder is recovered only when the complete actual-variant list is
     * trustworthy.
     * </p>
     *
     * @param tag        persisted execution snapshot
     * @param registries server registry lookup used by AE key codecs
     * @return immutable keyed contents safe to move into CPU recovery inventory
     */
    public static Object2LongMap<AEKey> recoverCompletionContents(CompoundTag tag,
                                                                  HolderLookup.Provider registries) {
        if (!tag.contains(SCHEMA_TAG, Tag.TAG_INT)) {
            return Object2LongMaps.emptyMap();
        }
        int schema = tag.getInt(SCHEMA_TAG);
        if (schema != LONG_AMOUNT_SCHEMA && schema != BIG_INTEGER_SCHEMA && schema != SAME_ITEM_SCHEMA && schema != SCHEMA) {
            return Object2LongMaps.emptyMap();
        }

        Object2LongMap<AEKey> recovered = new Object2LongLinkedOpenHashMap<>();
        long actualAmount = 0L;
        boolean actualLedgerComplete = true;
        Tag rawActualOutputs = tag.get(ACTUAL_FINAL_OUTPUTS_TAG);
        if (!(rawActualOutputs instanceof ListTag actualOutputs) ||
                (!actualOutputs.isEmpty() && actualOutputs.getElementType() != Tag.TAG_COMPOUND)) {
            Data_Energistics.LOGGER.error(
                    "Cannot completely recover a damaged Trinity actual final-output list");
            actualLedgerComplete = false;
        } else {
            for (Tag encoded : actualOutputs) {
                try {
                    if (!(encoded instanceof CompoundTag entry) ||
                            !entry.getAllKeys().equals(AMOUNT_FIELDS) ||
                            !entry.contains(KEY_TAG, Tag.TAG_COMPOUND) ||
                            !entry.contains(AMOUNT_TAG, Tag.TAG_LONG)) {
                        throw new IllegalArgumentException("Damaged actual final-output recovery entry");
                    }
                    AEKey key = decodeKey(
                            entry.getCompound(KEY_TAG),
                            registries,
                            "actual final-output recovery");
                    long amount = entry.getLong(AMOUNT_TAG);
                    if (amount <= 0L || recovered.containsKey(key)) {
                        throw new IllegalArgumentException(
                                "Actual final-output recovery requires unique positive entries");
                    }
                    actualAmount = Math.addExact(actualAmount, amount);
                    recovered.put(key, amount);
                } catch (RuntimeException exception) {
                    actualLedgerComplete = false;
                    Data_Energistics.LOGGER.error(
                            "Skipped one damaged Trinity actual final-output recovery entry",
                            exception);
                }
            }
        }

        if (!tag.contains(COMPLETION_SEALED_TAG, Tag.TAG_BYTE) ||
                !tag.getBoolean(COMPLETION_SEALED_TAG) ||
                !tag.contains(COMPLETION_BUFFER_TAG, Tag.TAG_LONG)) {
            return TrinityLongAmountSnapshot.owned(recovered);
        }
        long completionBuffer = tag.getLong(COMPLETION_BUFFER_TAG);
        if (completionBuffer <= 0L || !actualLedgerComplete) {
            return TrinityLongAmountSnapshot.owned(recovered);
        }
        long exactAmount;
        try {
            exactAmount = Math.subtractExact(completionBuffer, actualAmount);
        } catch (ArithmeticException exception) {
            Data_Energistics.LOGGER.error(
                    "Cannot recover an overflowing Trinity exact completion remainder",
                    exception);
            return TrinityLongAmountSnapshot.owned(recovered);
        }
        if (exactAmount <= 0L || !tag.contains(TARGET_KEY_TAG, Tag.TAG_COMPOUND)) {
            return TrinityLongAmountSnapshot.owned(recovered);
        }
        try {
            AEKey targetKey = decodeKey(
                    tag.getCompound(TARGET_KEY_TAG),
                    registries,
                    "completion recovery target");
            recovered.mergeLong(targetKey, exactAmount, Math::addExact);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Could not recover the exact Trinity completion remainder",
                    exception);
        }
        return TrinityLongAmountSnapshot.owned(recovered);
    }

    private static List<Integer> readStageOrder(CompoundTag root) {
        List<Integer> order = readIndexes(root, STAGE_ORDER_TAG, "stage order");
        if (order.isEmpty()) {
            throw new IllegalArgumentException("A Trinity execution requires a non-empty stage order");
        }
        return order;
    }

    private static List<Stage> readStages(CompoundTag root,
                                          HolderLookup.Provider registries,
                                          int schema) {
        ListTag encodedStages = requireCompoundList(root, STAGES_TAG, "execution stages");
        ObjectArrayList<Stage> stages = new ObjectArrayList<>();
        IntOpenHashSet indexes = new IntOpenHashSet();
        for (Tag encoded : encodedStages) {
            Stage stage = readStage((CompoundTag) encoded, registries, schema);
            if (!indexes.add(stage.index())) {
                throw new IllegalArgumentException("A Trinity execution contains duplicate stage indexes");
            }
            stages.add(stage);
        }
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("A Trinity execution requires at least one stage");
        }
        return stages;
    }

    private static Stage readStage(CompoundTag tag,
                                   HolderLookup.Provider registries,
                                   int schema) {
        requireFields(tag, STAGE_FIELDS, "execution stage");
        requireType(tag, INDEX_TAG, Tag.TAG_INT, "stage index");
        requireType(tag, CYCLE_TAG, Tag.TAG_BYTE, "stage cycle flag");
        requireType(tag, DEPENDENCIES_TAG, Tag.TAG_INT_ARRAY, "stage dependencies");
        requireType(tag, CURRENT_FIRING_TAG, Tag.TAG_INT, "stage firing cursor");
        requireType(tag, COMPLETED_TAG, Tag.TAG_BYTE, "stage completion flag");
        requireType(tag, WAIT_KIND_TAG, Tag.TAG_STRING, "stage wait kind");
        requireType(tag, RETRY_AT_TAG, Tag.TAG_LONG, "stage retry tick");
        requireType(tag, NEXT_DYNAMIC_DELAY_TAG, Tag.TAG_INT, "stage dynamic retry delay");
        requireType(tag, NEXT_PROVIDER_DELAY_TAG, Tag.TAG_INT, "stage provider retry delay");
        requireType(tag, RETRY_VERSION_TAG, Tag.TAG_LONG, "stage retry version");
        return new Stage(
                tag.getInt(INDEX_TAG),
                tag.getBoolean(CYCLE_TAG),
                readIndexes(tag, DEPENDENCIES_TAG, "stage dependency"),
                tag.getInt(CURRENT_FIRING_TAG),
                tag.getBoolean(COMPLETED_TAG),
                readKeys(tag, INPUT_KEYS_TAG, registries, "stage input key"),
                readKeys(tag, WAITING_KEYS_TAG, registries, "stage waiting key"),
                parseEnum(WaitKind.class, tag.getString(WAIT_KIND_TAG), "stage wait kind"),
                tag.getLong(RETRY_AT_TAG),
                tag.getInt(NEXT_DYNAMIC_DELAY_TAG),
                tag.getInt(NEXT_PROVIDER_DELAY_TAG),
                tag.getLong(RETRY_VERSION_TAG),
                readFirings(tag, registries, schema),
                readBigAmountMap(tag, REQUIRED_AT_START_TAG, registries, "stage start requirement", false, schema),
                readBigAmountMap(tag, NET_CHANGE_TAG, registries, "stage net change", true, schema));
    }

    private static List<Firing> readFirings(CompoundTag stageTag,
                                            HolderLookup.Provider registries,
                                            int schema) {
        ListTag encodedFirings = requireCompoundList(stageTag, FIRINGS_TAG, "stage firings");
        ObjectArrayList<Firing> firings = new ObjectArrayList<>();
        for (Tag encoded : encodedFirings) {
            CompoundTag firingTag = (CompoundTag) encoded;
            requireFields(firingTag, schema >= SCHEMA ? FIRING_FIELDS : LEGACY_FIRING_FIELDS, "stage firing");
            requireType(firingTag, DEFINITION_TAG, Tag.TAG_STRING, "firing definition identity");
            requireType(firingTag, PUBLICATION_TAG, Tag.TAG_STRING, "firing publication identity");
            requireType(firingTag, PRIMARY_OUTPUT_TAG, Tag.TAG_COMPOUND, "firing primary output");
            requireType(firingTag, VARIANT_ORDINAL_TAG, Tag.TAG_INT, "firing variant ordinal");
            requireType(
                    firingTag,
                    PLANNED_COUNT_TAG,
                    schema >= BIG_INTEGER_SCHEMA ? Tag.TAG_BYTE_ARRAY : Tag.TAG_LONG,
                    "firing planned count");
            requireType(firingTag, OUTPUTS_TAG, Tag.TAG_LIST, "firing outputs");
            requireType(
                    firingTag,
                    REMAINING_COUNT_TAG,
                    schema >= BIG_INTEGER_SCHEMA ? Tag.TAG_BYTE_ARRAY : Tag.TAG_LONG,
                    "firing remaining count");
            requireType(firingTag, INITIALIZED_TAG, Tag.TAG_BYTE, "firing initialized flag");
            AEKey primaryOutput = decodeKey(
                    firingTag.getCompound(PRIMARY_OUTPUT_TAG),
                    registries,
                    "firing primary output");
            firings.add(new Firing(
                    new TrinityPatternIdentity(
                            firingTag.getString(DEFINITION_TAG),
                            firingTag.getString(PUBLICATION_TAG)),
                    primaryOutput,
                    firingTag.getInt(VARIANT_ORDINAL_TAG),
                    readBigInteger(firingTag, PLANNED_COUNT_TAG, schema),
                    readBigAmountMap(firingTag, OUTPUTS_TAG, registries, "firing output", false, schema),
                    readBigInteger(firingTag, REMAINING_COUNT_TAG, schema),
                    firingTag.getBoolean(INITIALIZED_TAG),
                    schema >= SCHEMA ? TrinityBoundInputSnapshotCodec.read(
                            requireCompoundList(firingTag, EXACT_BINDINGS_TAG, "exact firing bindings"), registries) : List.of()));
        }
        if (firings.isEmpty()) {
            throw new IllegalArgumentException("A Trinity stage requires at least one firing signature");
        }
        return firings;
    }

    private static List<RepeatBlock> readRepeatBlocks(CompoundTag root, int schema) {
        ListTag encodedRepeats = requireCompoundList(root, REPEAT_BLOCKS_TAG, "repeat blocks");
        ObjectArrayList<RepeatBlock> repeats = new ObjectArrayList<>();
        IntOpenHashSet indexes = new IntOpenHashSet();
        for (Tag encoded : encodedRepeats) {
            CompoundTag repeatTag = (CompoundTag) encoded;
            requireFields(repeatTag, REPEAT_FIELDS, "repeat block");
            requireType(repeatTag, INDEX_TAG, Tag.TAG_INT, "repeat index");
            requireType(repeatTag, STAGE_ORDER_ENTRY_TAG, Tag.TAG_INT_ARRAY, "repeat stage order");
            requireType(
                    repeatTag,
                    REMAINING_REPETITIONS_TAG,
                    schema >= BIG_INTEGER_SCHEMA ? Tag.TAG_BYTE_ARRAY : Tag.TAG_LONG,
                    "repeat remaining count");
            requireType(repeatTag, CURSOR_TAG, Tag.TAG_INT, "repeat cursor");
            requireType(
                    repeatTag,
                    WAVE_COUNT_TAG,
                    schema >= BIG_INTEGER_SCHEMA ? Tag.TAG_BYTE_ARRAY : Tag.TAG_LONG,
                    "repeat wave count");
            RepeatBlock repeat = new RepeatBlock(
                    repeatTag.getInt(INDEX_TAG),
                    readIndexes(repeatTag, STAGE_ORDER_ENTRY_TAG, "repeat stage"),
                    readBigInteger(repeatTag, REMAINING_REPETITIONS_TAG, schema),
                    repeatTag.getInt(CURSOR_TAG),
                    readBigInteger(repeatTag, WAVE_COUNT_TAG, schema));
            if (!indexes.add(repeat.index())) {
                throw new IllegalArgumentException("A Trinity execution contains duplicate repeat indexes");
            }
            repeats.add(repeat);
        }
        return repeats;
    }

    private static Map<AEKey, BigInteger> readBigAmountMap(
                                                           CompoundTag root,
                                                           String field,
                                                           HolderLookup.Provider registries,
                                                           String role,
                                                           boolean signed,
                                                           int schema) {
        ListTag entries = requireCompoundList(root, field, role);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> target = new Object2ObjectLinkedOpenHashMap<>();
        for (Tag encoded : entries) {
            CompoundTag entry = (CompoundTag) encoded;
            requireFields(entry, AMOUNT_FIELDS, role + " entry");
            requireType(entry, KEY_TAG, Tag.TAG_COMPOUND, role + " key");
            requireType(
                    entry,
                    AMOUNT_TAG,
                    schema >= BIG_INTEGER_SCHEMA ? Tag.TAG_BYTE_ARRAY : Tag.TAG_LONG,
                    role + " amount");
            AEKey key = decodeKey(entry.getCompound(KEY_TAG), registries, role);
            BigInteger amount = readBigInteger(entry, AMOUNT_TAG, schema);
            if ((signed ? amount.signum() == 0 : amount.signum() <= 0) ||
                    target.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("A Trinity " + role + " requires unique valid entries");
            }
        }
        return target;
    }

    private static Object2LongMap<AEKey> readLongAmountMap(
                                                           CompoundTag root,
                                                           String field,
                                                           HolderLookup.Provider registries,
                                                           String role) {
        ListTag entries = requireCompoundList(root, field, role);
        Object2LongMap<AEKey> target = new Object2LongLinkedOpenHashMap<>();
        for (Tag encoded : entries) {
            CompoundTag entry = (CompoundTag) encoded;
            requireFields(entry, AMOUNT_FIELDS, role + " entry");
            requireType(entry, KEY_TAG, Tag.TAG_COMPOUND, role + " key");
            requireType(entry, AMOUNT_TAG, Tag.TAG_LONG, role + " amount");
            AEKey key = decodeKey(entry.getCompound(KEY_TAG), registries, role);
            long amount = entry.getLong(AMOUNT_TAG);
            if (amount <= 0L || target.containsKey(key)) {
                throw new IllegalArgumentException("A Trinity " + role + " requires unique valid entries");
            }
            target.put(key, amount);
        }
        return target;
    }

    private static List<Integer> readIndexes(CompoundTag tag, String field, String role) {
        requireType(tag, field, Tag.TAG_INT_ARRAY, role + " indexes");
        IntArrayList indexes = new IntArrayList();
        IntOpenHashSet seen = new IntOpenHashSet();
        for (int index : tag.getIntArray(field)) {
            if (index < 0 || !seen.add(index)) {
                throw new IllegalArgumentException("A Trinity " + role + " requires unique non-negative indexes");
            }
            indexes.add(index);
        }
        return indexes;
    }

    private static Set<AEKey> readKeys(CompoundTag tag,
                                       String field,
                                       HolderLookup.Provider registries,
                                       String role) {
        ListTag encodedKeys = requireCompoundList(tag, field, role + " list");
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        for (Tag encoded : encodedKeys) {
            AEKey key = decodeKey((CompoundTag) encoded, registries, role);
            if (!keys.add(key)) {
                throw new IllegalArgumentException("A Trinity " + role + " list cannot contain duplicates");
            }
        }
        return keys;
    }

    private static TrinitySameItemPolicy readSameItemPolicy(CompoundTag tag,
                                                            HolderLookup.Provider registries) {
        ObjectArrayList<AEItemKey> representatives = new ObjectArrayList<>();
        for (AEKey key : readKeys(tag, SAME_ITEM_POLICY_TAG, registries, "same-item representative")) {
            if (!(key instanceof AEItemKey itemKey)) {
                throw new IllegalArgumentException("A Trinity same-item policy requires item representatives");
            }
            representatives.add(itemKey);
        }
        return TrinitySameItemPolicy.ofRepresentatives(representatives);
    }

    private static ListTag saveStages(List<Stage> stages, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        stages.forEach(stage -> encoded.add(saveStage(stage, registries)));
        return encoded;
    }

    private static CompoundTag saveStage(Stage stage, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(INDEX_TAG, stage.index());
        tag.putBoolean(CYCLE_TAG, stage.cycle());
        tag.put(DEPENDENCIES_TAG, intArray(stage.dependencies()));
        tag.putInt(CURRENT_FIRING_TAG, stage.currentFiring());
        tag.putBoolean(COMPLETED_TAG, stage.completed());
        tag.put(INPUT_KEYS_TAG, saveKeys(stage.inputKeys(), registries));
        tag.put(WAITING_KEYS_TAG, saveKeys(stage.waitingKeys(), registries));
        tag.putString(WAIT_KIND_TAG, stage.waitKind().name());
        tag.putLong(RETRY_AT_TAG, stage.retryAt());
        tag.putInt(NEXT_DYNAMIC_DELAY_TAG, stage.nextDynamicDelay());
        tag.putInt(NEXT_PROVIDER_DELAY_TAG, stage.nextProviderDelay());
        tag.putLong(RETRY_VERSION_TAG, stage.retryVersion());
        ListTag encodedFirings = new ListTag();
        stage.firings().forEach(firing -> encodedFirings.add(saveFiring(firing, registries)));
        tag.put(FIRINGS_TAG, encodedFirings);
        tag.put(REQUIRED_AT_START_TAG, saveBigAmounts(stage.requiredAtStart(), registries));
        tag.put(NET_CHANGE_TAG, saveBigAmounts(stage.netChange(), registries));
        return tag;
    }

    private static CompoundTag saveFiring(Firing firing, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DEFINITION_TAG, firing.patternIdentity().definitionEncoding());
        tag.putString(PUBLICATION_TAG, firing.patternIdentity().publicationEncoding());
        tag.put(PRIMARY_OUTPUT_TAG, firing.primaryOutput().toTagGeneric(registries));
        tag.putInt(VARIANT_ORDINAL_TAG, firing.variantOrdinal());
        putBigInteger(tag, PLANNED_COUNT_TAG, firing.plannedCount());
        tag.put(OUTPUTS_TAG, saveBigAmounts(firing.outputs(), registries));
        putBigInteger(tag, REMAINING_COUNT_TAG, firing.remainingCount());
        tag.putBoolean(INITIALIZED_TAG, firing.initialized());
        tag.put(EXACT_BINDINGS_TAG, TrinityBoundInputSnapshotCodec.write(firing.exactBindings(), registries));
        return tag;
    }

    private static ListTag saveRepeatBlocks(List<RepeatBlock> repeats) {
        ListTag encoded = new ListTag();
        repeats.forEach(repeat -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt(INDEX_TAG, repeat.index());
            tag.put(STAGE_ORDER_ENTRY_TAG, intArray(repeat.stageOrder()));
            putBigInteger(tag, REMAINING_REPETITIONS_TAG, repeat.remainingRepetitions());
            tag.putInt(CURSOR_TAG, repeat.cursor());
            putBigInteger(tag, WAVE_COUNT_TAG, repeat.waveCount());
            encoded.add(tag);
        });
        return encoded;
    }

    private static ListTag saveBigAmounts(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_TAG, key.toTagGeneric(registries));
            putBigInteger(entry, AMOUNT_TAG, amount);
            encoded.add(entry);
        });
        return encoded;
    }

    private static ListTag saveLongAmounts(Object2LongMap<AEKey> amounts, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        for (Object2LongMap.Entry<AEKey> amount : amounts.object2LongEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_TAG, amount.getKey().toTagGeneric(registries));
            entry.putLong(AMOUNT_TAG, amount.getLongValue());
            encoded.add(entry);
        }
        return encoded;
    }

    private static void putBigInteger(CompoundTag tag, String field, BigInteger value) {
        byte[] encoded = value.toByteArray();
        if (encoded.length > MAX_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException("A Trinity execution amount exceeds the persistence byte limit");
        }
        tag.putByteArray(field, encoded);
    }

    private static BigInteger readBigInteger(CompoundTag tag, String field, int schema) {
        if (schema == LONG_AMOUNT_SCHEMA) {
            return BigInteger.valueOf(tag.getLong(field));
        }
        byte[] encoded = tag.getByteArray(field);
        if (encoded.length == 0 || encoded.length > MAX_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException("A Trinity execution amount has invalid persistence bytes");
        }
        return new BigInteger(encoded);
    }

    private static ListTag saveKeys(Iterable<? extends AEKey> keys, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        keys.forEach(key -> encoded.add(key.toTagGeneric(registries)));
        return encoded;
    }

    private static IntArrayTag intArray(Iterable<Integer> values) {
        IntArrayList copied = new IntArrayList();
        for (int value : values) {
            copied.add(value);
        }
        return new IntArrayTag(copied);
    }

    private static ListTag requireCompoundList(CompoundTag tag, String field, String role) {
        requireType(tag, field, Tag.TAG_LIST, role);
        Tag encoded = tag.get(field);
        if (!(encoded instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("A Trinity " + role + " must contain compound entries");
        }
        return list;
    }

    private static AEKey decodeKey(CompoundTag tag, HolderLookup.Provider registries, String role) {
        AEKey key = AEKey.fromTagGeneric(registries, tag);
        if (key == null) {
            throw new IllegalArgumentException("A Trinity " + role + " contains an unknown AE key");
        }
        return key;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String role) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Trinity " + role + " '" + value + "'", exception);
        }
    }

    private static long nonNegative(long value, String role) {
        if (value < 0L) {
            throw new IllegalArgumentException("A Trinity " + role + " cannot be negative");
        }
        return value;
    }

    private static void requireFields(CompoundTag tag, Set<String> fields, String role) {
        if (!tag.getAllKeys().equals(fields)) {
            throw new IllegalArgumentException("Unexpected or missing fields in Trinity " + role);
        }
    }

    private static void requireType(CompoundTag tag, String field, int type, String role) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Missing or damaged Trinity " + role);
        }
    }
}
