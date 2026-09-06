package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.EntrySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.RecordedNativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSessionNbtCodec;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Native endpoint metadata plus complete session escrows; embedded directly in the owning core/provider state. */
public final class ReusableCraftingEndpointNbtCodec {

    private static final int SCHEMA = 2;

    private ReusableCraftingEndpointNbtCodec() {}

    public static CompoundTag encode(PersistentReusableCraftingEndpoint endpoint, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("target", endpoint.targetIdentity());
        ListTag sessions = new ListTag();
        for (EntrySnapshot snapshot : endpoint.snapshot()) {
            CompoundTag entry = new CompoundTag();
            entry.put("session", ReusableInputSessionNbtCodec.encode(snapshot.session(), registries));
            entry.putInt("input_slots", snapshot.binding().inputSlots());
            entry.putString("publication_definition", snapshot.binding().publicationIdentity().definitionEncoding());
            entry.putString("publication_semantics", snapshot.binding().publicationIdentity().publicationEncoding());
            ListTag consumed = new ListTag();
            for (SlotInput input : snapshot.binding().consumed()) {
                CompoundTag material = new CompoundTag();
                material.putInt("slot", input.slot());
                material.put("stack", GenericStack.writeTag(registries, input.stack()));
                consumed.add(material);
            }
            entry.put("consumed", consumed);
            snapshot.binding().recipeId().ifPresent(recipe -> entry.putString("recipe", recipe));
            entry.putLong("revision", snapshot.revision());
            entry.putLong("not_before", snapshot.notBefore());
            entry.putBoolean("acknowledged", snapshot.settlementAcknowledged());
            entry.putString("failure", snapshot.failure());
            entry.put("native_result", encodeResult(snapshot.recordedResult(), registries));
            sessions.add(entry);
        }
        tag.put("sessions", sessions);
        return tag;
    }

    /** Decodes into detached state; the owner swaps it into its live core only after all fields validate. */
    public static PersistentReusableCraftingEndpoint decode(CompoundTag tag, HolderLookup.Provider registries) {
        requireType(tag, "schema", Tag.TAG_INT);
        requireType(tag, "target", Tag.TAG_STRING);
        int schema = tag.getInt("schema");
        if (schema != 1 && schema != SCHEMA) {
            throw new IllegalArgumentException("Unsupported native reusable endpoint schema");
        }
        List<EntrySnapshot> snapshots = new ObjectArrayList<>();
        for (Tag encoded : compounds(tag, "sessions")) {
            CompoundTag entry = (CompoundTag) encoded;
            requireType(entry, "session", Tag.TAG_COMPOUND);
            ReusableInputSession session = ReusableInputSessionNbtCodec.decode(entry.getCompound("session"), registries);
            requireType(entry, "input_slots", Tag.TAG_INT);
            List<SlotInput> consumed = new ObjectArrayList<>();
            for (Tag inputTag : compounds(entry, "consumed")) {
                CompoundTag input = (CompoundTag) inputTag;
                requireType(input, "slot", Tag.TAG_INT);
                requireType(input, "stack", Tag.TAG_COMPOUND);
                GenericStack stack = GenericStack.readTag(registries, input.getCompound("stack"));
                if (stack == null) {
                    throw new IllegalArgumentException("Unknown persisted native material key");
                }
                consumed.add(new SlotInput(input.getInt("slot"), stack));
            }
            Optional<String> recipe = Optional.empty();
            if (entry.contains("recipe")) {
                requireType(entry, "recipe", Tag.TAG_STRING);
                recipe = Optional.of(ResourceLocation.parse(entry.getString("recipe")).toString());
            }
            requireType(entry, "publication_definition", Tag.TAG_STRING);
            requireType(entry, "publication_semantics", Tag.TAG_STRING);
            TrinityPatternIdentity publication = new TrinityPatternIdentity(entry.getString("publication_definition"),
                    entry.getString("publication_semantics"));
            Binding binding = new Binding(session.identity(), publication, entry.getInt("input_slots"), consumed, session.slotContracts(), recipe);
            requireType(entry, "revision", Tag.TAG_LONG);
            requireType(entry, "not_before", Tag.TAG_LONG);
            requireType(entry, "acknowledged", Tag.TAG_BYTE);
            requireType(entry, "failure", Tag.TAG_STRING);
            RecordedNativeResult recorded = null;
            if (schema == SCHEMA) {
                requireType(entry, "native_result", Tag.TAG_COMPOUND);
                recorded = decodeResult(entry.getCompound("native_result"), registries);
            } else if (entry.contains("native_result")) {
                throw new IllegalArgumentException("Legacy endpoint schema cannot contain a native result checkpoint");
            }
            snapshots.add(new EntrySnapshot(binding, session, entry.getLong("revision"), entry.getLong("not_before"),
                    entry.getBoolean("acknowledged"), entry.getString("failure"), recorded));
        }
        return PersistentReusableCraftingEndpoint.restore(tag.getString("target"), snapshots);
    }

    private static CompoundTag encodeResult(@Nullable RecordedNativeResult recorded, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (recorded == null) return tag;
        tag.putUUID("epoch", recorded.loadedEpoch());
        tag.putLong("operation", recorded.operationId());
        NativeResult result = recorded.result();
        tag.putBoolean("executed", result.executed());
        tag.putString("failure", result.failure().orElse(""));
        tag.put("outputs", stacks(result.outputs(), registries));
        ListTag tools = new ListTag();
        for (ToolOutcome outcome : result.tools()) {
            CompoundTag tool = new CompoundTag();
            tool.putInt("slot", outcome.slot());
            tool.put("successors", stacks(outcome.successors(), registries));
            tool.put("byproducts", stacks(outcome.byproducts(), registries));
            tools.add(tool);
        }
        tag.put("tools", tools);
        return tag;
    }

    private static @Nullable RecordedNativeResult decodeResult(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.isEmpty()) return null;
        if (!tag.hasUUID("epoch")) throw new IllegalArgumentException("Native result checkpoint has no loaded epoch");
        requireType(tag, "operation", Tag.TAG_LONG);
        requireType(tag, "executed", Tag.TAG_BYTE);
        requireType(tag, "failure", Tag.TAG_STRING);
        List<ToolOutcome> tools = new ObjectArrayList<>();
        for (Tag encoded : compounds(tag, "tools")) {
            CompoundTag tool = (CompoundTag) encoded;
            requireType(tool, "slot", Tag.TAG_INT);
            tools.add(new ToolOutcome(tool.getInt("slot"), readStacks(tool, "successors", registries), readStacks(tool, "byproducts", registries)));
        }
        String failure = tag.getString("failure");
        return new RecordedNativeResult(tag.getUUID("epoch"), tag.getLong("operation"), new NativeResult(tag.getBoolean("executed"), tools,
                readStacks(tag, "outputs", registries), failure.isEmpty() ? Optional.empty() : Optional.of(failure)));
    }

    private static ListTag stacks(List<GenericStack> stacks, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (GenericStack stack : stacks) result.add(GenericStack.writeTag(registries, stack));
        return result;
    }

    private static List<GenericStack> readStacks(CompoundTag tag, String field, HolderLookup.Provider registries) {
        List<GenericStack> result = new ObjectArrayList<>();
        for (Tag encoded : compounds(tag, field)) {
            GenericStack stack = GenericStack.readTag(registries, (CompoundTag) encoded);
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid recorded native asset in " + field);
            result.add(stack);
        }
        return result;
    }

    private static ListTag compounds(CompoundTag tag, String field) {
        if (!(tag.get(field) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Expected native endpoint compound list: " + field);
        }
        return list;
    }

    private static void requireType(CompoundTag tag, String field, int type) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Missing or malformed native endpoint field: " + field);
        }
    }
}
