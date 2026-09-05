package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.ReusableInputRuleNbtCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Append;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.AppendSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ReturnBatch;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Snapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.State;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolDelivery;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Complete session escrow encoding. Unknown versions and malformed asset/progress relationships fail at load. */
public final class ReusableInputSessionNbtCodec {

    private static final int SCHEMA = 1;

    private ReusableInputSessionNbtCodec() {}

    public static CompoundTag encode(ReusableInputSession session, HolderLookup.Provider registries) {
        Snapshot snapshot = session.snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        Identity identity = snapshot.identity();
        tag.putUUID("session_id", identity.sessionId());
        tag.putUUID("job_id", identity.jobId());
        tag.putString("cpu_owner", identity.cpuOwner());
        tag.putString("target", identity.target());
        tag.put("pattern", identity.pattern().toTagGeneric(registries));
        identity.mode().ifPresent(mode -> tag.putString("mode", mode));
        tag.putString("state", snapshot.state().name());
        ListTag contracts = new ListTag();
        for (SlotContract contract : snapshot.contracts()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", contract.slot());
            entry.putLong("held", contract.heldAmount());
            entry.putString("ownership", contract.ownership().name());
            entry.put("rule", ReusableInputRuleNbtCodec.encode(contract.rule(), registries));
            contracts.add(entry);
        }
        tag.put("contracts", contracts);
        ListTag appends = new ListTag();
        for (AppendSnapshot append : snapshot.appends()) {
            CompoundTag entry = encodeAppend(append.request(), registries);
            entry.putLong("completed", append.completed());
            entry.putLong("cancelled", append.cancelled());
            entry.put("remaining", encodeAssets(append.remainingMaterials(), registries));
            appends.add(entry);
        }
        tag.put("appends", appends);
        tag.put("tools", encodeTools(snapshot.tools(), registries));
        if (snapshot.active() != null) {
            Operation active = snapshot.active();
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", active.id());
            entry.putLong("append", active.appendSequence());
            entry.put("consumed", encodeInputs(active.consumed(), registries));
            entry.put("tools", encodeTools(active.tools(), registries));
            tag.put("active", entry);
        }
        tag.put("outputs", encodeAssets(snapshot.outputs(), registries));
        tag.put("returns", encodeReturns(snapshot.returns(), registries));
        tag.put("acknowledged", encodeReturns(snapshot.acknowledged(), registries));
        tag.put("machine_released", encodeTools(snapshot.machineOwnedReleased(), registries));
        tag.putLong("next_operation", snapshot.nextOperation());
        tag.putLong("next_return", snapshot.nextReturn());
        tag.putLong("idle_since", snapshot.idleSince());
        tag.putLong("yield_requested_at", snapshot.yieldRequestedAt());
        tag.putLong("exhausted", snapshot.exhaustedTools());
        tag.putString("fault", snapshot.fault());
        return tag;
    }

    /** Restores all idempotency records and assets; interrupted native effects remain quarantined. */
    public static ReusableInputSession decode(CompoundTag tag, HolderLookup.Provider registries) {
        if (integer(tag, "schema") != SCHEMA) {
            throw new IllegalArgumentException("Unsupported reusable session schema");
        }
        Identity identity = new Identity(uuid(tag, "session_id"), uuid(tag, "job_id"), string(tag, "cpu_owner"),
                string(tag, "target"), item(tag, "pattern", registries),
                tag.contains("mode") ? Optional.of(string(tag, "mode")) : Optional.empty());
        List<SlotContract> contracts = new ObjectArrayList<>();
        for (CompoundTag entry : compounds(tag, "contracts")) {
            contracts.add(new SlotContract(integer(entry, "slot"), number(entry, "held"),
                    Ownership.valueOf(string(entry, "ownership")),
                    ReusableInputRuleNbtCodec.decode(compound(entry, "rule"), registries)));
        }
        List<AppendSnapshot> appends = new ObjectArrayList<>();
        for (CompoundTag entry : compounds(tag, "appends")) {
            appends.add(new AppendSnapshot(decodeAppend(entry, registries), number(entry, "completed"),
                    number(entry, "cancelled"), decodeAssets(entry, "remaining", registries)));
        }
        Operation active = null;
        if (tag.contains("active")) {
            CompoundTag entry = compound(tag, "active");
            active = new Operation(number(entry, "id"), number(entry, "append"),
                    decodeInputs(entry, "consumed", registries), decodeTools(entry, "tools", registries));
        }
        return ReusableInputSession.restore(new Snapshot(identity, contracts, State.valueOf(string(tag, "state")), appends,
                decodeTools(tag, "tools", registries), active, decodeAssets(tag, "outputs", registries),
                decodeReturns(tag, "returns", registries), decodeReturns(tag, "acknowledged", registries),
                decodeTools(tag, "machine_released", registries), number(tag, "next_operation"), number(tag, "next_return"),
                number(tag, "idle_since"), number(tag, "yield_requested_at"), number(tag, "exhausted"), string(tag, "fault")));
    }

    private static CompoundTag encodeAppend(Append append, HolderLookup.Provider registries) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("sequence", append.sequence());
        entry.putLong("operations", append.operations());
        entry.put("consumed", encodeInputs(append.consumedPerOperation(), registries));
        entry.put("delivered_materials", encodeAssets(append.deliveredMaterials(), registries));
        entry.put("delivered_tools", encodeTools(append.deliveredTools(), registries));
        ListTag states = new ListTag();
        for (var state : append.operationStates().int2ObjectEntrySet()) {
            CompoundTag value = new CompoundTag();
            value.putInt("slot", state.getIntKey());
            value.put("state", state.getValue().toTagGeneric(registries));
            states.add(value);
        }
        entry.put("operation_states", states);
        return entry;
    }

    private static Append decodeAppend(CompoundTag tag, HolderLookup.Provider registries) {
        Int2ObjectMap<AEItemKey> states = new Int2ObjectLinkedOpenHashMap<>();
        for (CompoundTag entry : compounds(tag, "operation_states")) {
            if (states.putIfAbsent(integer(entry, "slot"), item(entry, "state", registries)) != null) {
                throw new IllegalArgumentException("Duplicate exact operation-state slot");
            }
        }
        return new Append(number(tag, "sequence"), number(tag, "operations"), decodeInputs(tag, "consumed", registries),
                decodeAssets(tag, "delivered_materials", registries), decodeTools(tag, "delivered_tools", registries), states);
    }

    private static ListTag encodeInputs(List<SlotInput> inputs, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (SlotInput input : inputs) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", input.slot());
            entry.put("stack", GenericStack.writeTag(registries, input.stack()));
            result.add(entry);
        }
        return result;
    }

    private static List<SlotInput> decodeInputs(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<SlotInput> result = new ObjectArrayList<>();
        for (CompoundTag entry : compounds(tag, field)) {
            result.add(new SlotInput(integer(entry, "slot"), stack(entry, "stack", registries)));
        }
        return List.copyOf(result);
    }

    private static ListTag encodeTools(List<ToolDelivery> tools, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (ToolDelivery tool : tools) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", tool.slot());
            entry.put("stack", GenericStack.writeTag(registries, tool.stack()));
            result.add(entry);
        }
        return result;
    }

    private static List<ToolDelivery> decodeTools(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<ToolDelivery> result = new ObjectArrayList<>();
        for (CompoundTag entry : compounds(tag, field)) {
            result.add(new ToolDelivery(integer(entry, "slot"), stack(entry, "stack", registries)));
        }
        return List.copyOf(result);
    }

    private static ListTag encodeReturns(List<ReturnBatch> batches, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (ReturnBatch batch : batches) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("sequence", batch.sequence());
            entry.put("assets", encodeAssets(batch.assets(), registries));
            result.add(entry);
        }
        return result;
    }

    private static List<ReturnBatch> decodeReturns(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<ReturnBatch> result = new ObjectArrayList<>();
        for (CompoundTag entry : compounds(tag, field)) {
            result.add(new ReturnBatch(number(entry, "sequence"), decodeAssets(entry, "assets", registries)));
        }
        return List.copyOf(result);
    }

    private static ListTag encodeAssets(List<GenericStack> assets, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        assets.forEach(asset -> result.add(GenericStack.writeTag(registries, asset)));
        return result;
    }

    private static List<GenericStack> decodeAssets(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<GenericStack> result = new ObjectArrayList<>();
        for (CompoundTag entry : compounds(tag, field)) {
            GenericStack stack = GenericStack.readTag(registries, entry);
            if (stack == null || stack.amount() <= 0) {
                throw new IllegalArgumentException("Invalid persisted session asset");
            }
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static GenericStack stack(CompoundTag tag, String field, HolderLookup.Provider registries) {
        GenericStack stack = GenericStack.readTag(registries, compound(tag, field));
        if (stack == null || stack.amount() <= 0) {
            throw new IllegalArgumentException("Invalid persisted session stack: " + field);
        }
        return stack;
    }

    private static AEItemKey item(CompoundTag tag, String field, HolderLookup.Provider registries) {
        if (!(AEKey.fromTagGeneric(registries, compound(tag, field)) instanceof AEItemKey item)) {
            throw new IllegalArgumentException("Invalid persisted session item: " + field);
        }
        return item;
    }

    private static List<CompoundTag> compounds(CompoundTag tag, String field) {
        if (!(tag.get(field) instanceof ListTag list)) {
            throw new IllegalArgumentException("Session field must be a list: " + field);
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Session list does not contain compounds: " + field);
        }
        List<CompoundTag> result = new ObjectArrayList<>(list.size());
        for (Tag entry : list) {
            result.add((CompoundTag) entry);
        }
        return result;
    }

    private static CompoundTag compound(CompoundTag tag, String field) {
        requireType(tag, field, Tag.TAG_COMPOUND);
        return tag.getCompound(field);
    }

    private static String string(CompoundTag tag, String field) {
        requireType(tag, field, Tag.TAG_STRING);
        return tag.getString(field);
    }

    private static int integer(CompoundTag tag, String field) {
        requireType(tag, field, Tag.TAG_INT);
        return tag.getInt(field);
    }

    private static long number(CompoundTag tag, String field) {
        requireType(tag, field, Tag.TAG_LONG);
        return tag.getLong(field);
    }

    private static UUID uuid(CompoundTag tag, String field) {
        if (!tag.hasUUID(field)) {
            throw new IllegalArgumentException("Invalid session UUID: " + field);
        }
        return tag.getUUID(field);
    }

    private static void requireType(CompoundTag tag, String field, int type) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Session field has an unexpected or missing type: " + field);
        }
    }
}
