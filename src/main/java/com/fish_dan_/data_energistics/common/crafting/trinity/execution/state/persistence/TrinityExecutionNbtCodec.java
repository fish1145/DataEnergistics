package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.Firing;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.RepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.Stage;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.WaitKind;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict NBT codec for durable Trinity execution snapshots.
 */
public final class TrinityExecutionNbtCodec {

    private static final int LEGACY_SCHEMA = 2;
    private static final int SCHEMA = 3;
    private static final String PLAN_KIND = "trinity_compact";
    private static final String SCHEMA_TAG = "schema_version";
    private static final String PLAN_KIND_TAG = "plan_kind";
    private static final String CATALOG_REVISION_TAG = "catalog_revision";
    private static final String QUANTITY_MODE_TAG = "quantity_mode";
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
    private static final String REMAINING_COUNT_TAG = "remaining_count";
    private static final String INITIALIZED_TAG = "initialized";

    private static final String STAGE_ORDER_ENTRY_TAG = "stage_order";
    private static final String REMAINING_REPETITIONS_TAG = "remaining_repetitions";
    private static final String CURSOR_TAG = "cursor";
    private static final String WAVE_COUNT_TAG = "wave_count";

    private static final String KEY_TAG = "key";
    private static final String AMOUNT_TAG = "amount";

    private static final Set<String> ROOT_FIELDS = Set.of(
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
            DELIVERY_REMAINING_TAG,
            LEDGER_TAG,
            SAVED_AT_TICK_TAG,
            BUDGET_RETRY_AT_TAG);
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
            DELIVERY_REMAINING_TAG,
            LEDGER_TAG,
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
    private static final Set<String> FIRING_FIELDS = Set.of(
            DEFINITION_TAG,
            PUBLICATION_TAG,
            PRIMARY_OUTPUT_TAG,
            VARIANT_ORDINAL_TAG,
            PLANNED_COUNT_TAG,
            REMAINING_COUNT_TAG,
            INITIALIZED_TAG);
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
     * @return strict schema 2 NBT
     */
    public static CompoundTag encode(TrinityExecutionSnapshot snapshot, HolderLookup.Provider registries) {
        if (registries == null) {
            throw new IllegalArgumentException("Trinity execution persistence requires registries");
        }
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_TAG, SCHEMA);
        root.putString(PLAN_KIND_TAG, PLAN_KIND);
        root.putLong(CATALOG_REVISION_TAG, snapshot.catalogRevision());
        root.putString(QUANTITY_MODE_TAG, snapshot.quantityMode().name());
        root.put(TARGET_KEY_TAG, snapshot.targetKey().toTagGeneric(registries));
        root.putLong(TARGET_AMOUNT_TAG, snapshot.targetAmount());
        root.putString(STATUS_TAG, snapshot.status().name());
        root.putString(FAILURE_REASON_TAG, snapshot.failureReason());
        root.putLong(GENERATION_TAG, snapshot.generation());
        root.put(STAGES_TAG, saveStages(snapshot.stages(), registries));
        root.put(STAGE_ORDER_TAG, intArray(snapshot.stageOrder()));
        root.put(REPEAT_BLOCKS_TAG, saveRepeatBlocks(snapshot.repeatBlocks()));
        root.put(SEED_RESERVE_TAG, saveAmounts(snapshot.seedReserve(), registries));
        root.putBoolean(COMPLETION_SEALED_TAG, snapshot.completionSealed());
        root.putLong(COMPLETION_BUFFER_TAG, snapshot.completionBuffer());
        root.putLong(DELIVERY_REMAINING_TAG, snapshot.deliveryRemaining());
        root.put(LEDGER_TAG, TrinityBorrowingLedgerNbtCodec.encode(snapshot.borrowingEntries(), registries));
        root.putLong(SAVED_AT_TICK_TAG, snapshot.savedAtTick());
        root.putLong(BUDGET_RETRY_AT_TAG, snapshot.budgetRetryAt());
        return root;
    }

    /**
     * Decodes strict schema 2 NBT into an immutable persistence model.
     *
     * @param tag        encoded execution
     * @param registries server registry lookup used by AE key codecs
     * @return decoded durable snapshot
     */
    public static TrinityExecutionSnapshot decode(CompoundTag tag, HolderLookup.Provider registries) {
        if (registries == null) {
            throw new IllegalArgumentException("Trinity execution restoration requires registries");
        }
        requireType(tag, SCHEMA_TAG, Tag.TAG_INT, "execution schema");
        int schema = tag.getInt(SCHEMA_TAG);
        if (schema != LEGACY_SCHEMA && schema != SCHEMA) {
            throw new IllegalArgumentException("Unsupported Trinity execution schema");
        }
        requireFields(tag, schema == SCHEMA ? ROOT_FIELDS : LEGACY_ROOT_FIELDS, "execution root");
        requireType(tag, PLAN_KIND_TAG, Tag.TAG_STRING, "execution plan kind");
        requireType(tag, CATALOG_REVISION_TAG, Tag.TAG_LONG, "execution catalog revision");
        requireType(tag, QUANTITY_MODE_TAG, Tag.TAG_STRING, "execution quantity mode");
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
        if (schema == SCHEMA) {
            requireType(tag, SAVED_AT_TICK_TAG, Tag.TAG_LONG, "execution save tick");
        }
        requireType(tag, BUDGET_RETRY_AT_TAG, Tag.TAG_LONG, "execution budget retry");
        if (!PLAN_KIND.equals(tag.getString(PLAN_KIND_TAG))) {
            throw new IllegalArgumentException("Unsupported Trinity execution plan kind");
        }

        return new TrinityExecutionSnapshot(
                nonNegative(tag.getLong(CATALOG_REVISION_TAG), "catalog revision"),
                parseEnum(CraftingQuantityMode.class, tag.getString(QUANTITY_MODE_TAG), "quantity mode"),
                decodeKey(tag.getCompound(TARGET_KEY_TAG), registries, "execution target"),
                tag.getLong(TARGET_AMOUNT_TAG),
                parseEnum(TrinityPlanExecution.Status.class, tag.getString(STATUS_TAG), "execution status"),
                tag.getString(FAILURE_REASON_TAG),
                nonNegative(tag.getLong(GENERATION_TAG), "generation"),
                readStages(tag, registries),
                readStageOrder(tag),
                readRepeatBlocks(tag),
                readAmountMap(tag, SEED_RESERVE_TAG, registries, "seed reserve", false),
                tag.getBoolean(COMPLETION_SEALED_TAG),
                tag.getLong(COMPLETION_BUFFER_TAG),
                tag.getLong(DELIVERY_REMAINING_TAG),
                TrinityBorrowingLedgerNbtCodec.decode(tag.getCompound(LEDGER_TAG), registries),
                schema == SCHEMA ? nonNegative(tag.getLong(SAVED_AT_TICK_TAG), "save tick") : -1L,
                tag.getLong(BUDGET_RETRY_AT_TAG));
    }

    private static List<Integer> readStageOrder(CompoundTag root) {
        List<Integer> order = readIndexes(root, STAGE_ORDER_TAG, "stage order");
        if (order.isEmpty()) {
            throw new IllegalArgumentException("A Trinity execution requires a non-empty stage order");
        }
        return order;
    }

    private static List<Stage> readStages(CompoundTag root, HolderLookup.Provider registries) {
        ListTag encodedStages = requireCompoundList(root, STAGES_TAG, "execution stages");
        ArrayList<Stage> stages = new ArrayList<>();
        HashSet<Integer> indexes = new HashSet<>();
        for (Tag encoded : encodedStages) {
            Stage stage = readStage((CompoundTag) encoded, registries);
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

    private static Stage readStage(CompoundTag tag, HolderLookup.Provider registries) {
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
                readFirings(tag, registries),
                readAmountMap(tag, REQUIRED_AT_START_TAG, registries, "stage start requirement", false),
                readAmountMap(tag, NET_CHANGE_TAG, registries, "stage net change", true));
    }

    private static List<Firing> readFirings(CompoundTag stageTag, HolderLookup.Provider registries) {
        ListTag encodedFirings = requireCompoundList(stageTag, FIRINGS_TAG, "stage firings");
        ArrayList<Firing> firings = new ArrayList<>();
        for (Tag encoded : encodedFirings) {
            CompoundTag firingTag = (CompoundTag) encoded;
            requireFields(firingTag, FIRING_FIELDS, "stage firing");
            requireType(firingTag, DEFINITION_TAG, Tag.TAG_STRING, "firing definition identity");
            requireType(firingTag, PUBLICATION_TAG, Tag.TAG_STRING, "firing publication identity");
            requireType(firingTag, PRIMARY_OUTPUT_TAG, Tag.TAG_COMPOUND, "firing primary output");
            requireType(firingTag, VARIANT_ORDINAL_TAG, Tag.TAG_INT, "firing variant ordinal");
            requireType(firingTag, PLANNED_COUNT_TAG, Tag.TAG_LONG, "firing planned count");
            requireType(firingTag, REMAINING_COUNT_TAG, Tag.TAG_LONG, "firing remaining count");
            requireType(firingTag, INITIALIZED_TAG, Tag.TAG_BYTE, "firing initialized flag");
            firings.add(new Firing(
                    new TrinityPatternIdentity(
                            firingTag.getString(DEFINITION_TAG),
                            firingTag.getString(PUBLICATION_TAG)),
                    decodeKey(firingTag.getCompound(PRIMARY_OUTPUT_TAG), registries, "firing primary output"),
                    firingTag.getInt(VARIANT_ORDINAL_TAG),
                    firingTag.getLong(PLANNED_COUNT_TAG),
                    firingTag.getLong(REMAINING_COUNT_TAG),
                    firingTag.getBoolean(INITIALIZED_TAG)));
        }
        if (firings.isEmpty()) {
            throw new IllegalArgumentException("A Trinity stage requires at least one firing signature");
        }
        return firings;
    }

    private static List<RepeatBlock> readRepeatBlocks(CompoundTag root) {
        ListTag encodedRepeats = requireCompoundList(root, REPEAT_BLOCKS_TAG, "repeat blocks");
        ArrayList<RepeatBlock> repeats = new ArrayList<>();
        HashSet<Integer> indexes = new HashSet<>();
        for (Tag encoded : encodedRepeats) {
            CompoundTag repeatTag = (CompoundTag) encoded;
            requireFields(repeatTag, REPEAT_FIELDS, "repeat block");
            requireType(repeatTag, INDEX_TAG, Tag.TAG_INT, "repeat index");
            requireType(repeatTag, STAGE_ORDER_ENTRY_TAG, Tag.TAG_INT_ARRAY, "repeat stage order");
            requireType(repeatTag, REMAINING_REPETITIONS_TAG, Tag.TAG_LONG, "repeat remaining count");
            requireType(repeatTag, CURSOR_TAG, Tag.TAG_INT, "repeat cursor");
            requireType(repeatTag, WAVE_COUNT_TAG, Tag.TAG_LONG, "repeat wave count");
            RepeatBlock repeat = new RepeatBlock(
                    repeatTag.getInt(INDEX_TAG),
                    readIndexes(repeatTag, STAGE_ORDER_ENTRY_TAG, "repeat stage"),
                    repeatTag.getLong(REMAINING_REPETITIONS_TAG),
                    repeatTag.getInt(CURSOR_TAG),
                    repeatTag.getLong(WAVE_COUNT_TAG));
            if (!indexes.add(repeat.index())) {
                throw new IllegalArgumentException("A Trinity execution contains duplicate repeat indexes");
            }
            repeats.add(repeat);
        }
        return repeats;
    }

    private static Map<AEKey, Long> readAmountMap(CompoundTag root,
                                                  String field,
                                                  HolderLookup.Provider registries,
                                                  String role,
                                                  boolean signed) {
        ListTag entries = requireCompoundList(root, field, role);
        LinkedHashMap<AEKey, Long> target = new LinkedHashMap<>();
        for (Tag encoded : entries) {
            CompoundTag entry = (CompoundTag) encoded;
            requireFields(entry, AMOUNT_FIELDS, role + " entry");
            requireType(entry, KEY_TAG, Tag.TAG_COMPOUND, role + " key");
            requireType(entry, AMOUNT_TAG, Tag.TAG_LONG, role + " amount");
            AEKey key = decodeKey(entry.getCompound(KEY_TAG), registries, role);
            long amount = entry.getLong(AMOUNT_TAG);
            if ((signed ? amount == 0L : amount <= 0L) || target.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("A Trinity " + role + " requires unique valid entries");
            }
        }
        return target;
    }

    private static List<Integer> readIndexes(CompoundTag tag, String field, String role) {
        requireType(tag, field, Tag.TAG_INT_ARRAY, role + " indexes");
        ArrayList<Integer> indexes = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
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
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        for (Tag encoded : encodedKeys) {
            AEKey key = decodeKey((CompoundTag) encoded, registries, role);
            if (!keys.add(key)) {
                throw new IllegalArgumentException("A Trinity " + role + " list cannot contain duplicates");
            }
        }
        return keys;
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
        tag.put(REQUIRED_AT_START_TAG, saveAmounts(stage.requiredAtStart(), registries));
        tag.put(NET_CHANGE_TAG, saveAmounts(stage.netChange(), registries));
        return tag;
    }

    private static CompoundTag saveFiring(Firing firing, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DEFINITION_TAG, firing.patternIdentity().definitionEncoding());
        tag.putString(PUBLICATION_TAG, firing.patternIdentity().publicationEncoding());
        tag.put(PRIMARY_OUTPUT_TAG, firing.primaryOutput().toTagGeneric(registries));
        tag.putInt(VARIANT_ORDINAL_TAG, firing.variantOrdinal());
        tag.putLong(PLANNED_COUNT_TAG, firing.plannedCount());
        tag.putLong(REMAINING_COUNT_TAG, firing.remainingCount());
        tag.putBoolean(INITIALIZED_TAG, firing.initialized());
        return tag;
    }

    private static ListTag saveRepeatBlocks(List<RepeatBlock> repeats) {
        ListTag encoded = new ListTag();
        repeats.forEach(repeat -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt(INDEX_TAG, repeat.index());
            tag.put(STAGE_ORDER_ENTRY_TAG, intArray(repeat.stageOrder()));
            tag.putLong(REMAINING_REPETITIONS_TAG, repeat.remainingRepetitions());
            tag.putInt(CURSOR_TAG, repeat.cursor());
            tag.putLong(WAVE_COUNT_TAG, repeat.waveCount());
            encoded.add(tag);
        });
        return encoded;
    }

    private static ListTag saveAmounts(Map<AEKey, Long> amounts, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_TAG, key.toTagGeneric(registries));
            entry.putLong(AMOUNT_TAG, amount);
            encoded.add(entry);
        });
        return encoded;
    }

    private static ListTag saveKeys(Iterable<AEKey> keys, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        keys.forEach(key -> encoded.add(key.toTagGeneric(registries)));
        return encoded;
    }

    private static IntArrayTag intArray(Iterable<Integer> values) {
        ArrayList<Integer> copied = new ArrayList<>();
        values.forEach(copied::add);
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
        if (tag == null || !tag.getAllKeys().equals(fields)) {
            throw new IllegalArgumentException("Unexpected or missing fields in Trinity " + role);
        }
    }

    private static void requireType(CompoundTag tag, String field, int type, String role) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Missing or damaged Trinity " + role);
        }
    }
}
