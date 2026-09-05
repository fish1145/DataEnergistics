package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.EntrySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSessionNbtCodec;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Optional;

/** Native endpoint metadata plus complete session escrows; embedded directly in the owning core/provider state. */
public final class ReusableCraftingEndpointNbtCodec {

    private static final int SCHEMA = 1;

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
            sessions.add(entry);
        }
        tag.put("sessions", sessions);
        return tag;
    }

    /** Decodes into detached state; the owner swaps it into its live core only after all fields validate. */
    public static PersistentReusableCraftingEndpoint decode(CompoundTag tag, HolderLookup.Provider registries) {
        requireType(tag, "schema", Tag.TAG_INT);
        requireType(tag, "target", Tag.TAG_STRING);
        if (tag.getInt("schema") != SCHEMA) {
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
            snapshots.add(new EntrySnapshot(binding, session, entry.getLong("revision"), entry.getLong("not_before"),
                    entry.getBoolean("acknowledged"), entry.getString("failure")));
        }
        return PersistentReusableCraftingEndpoint.restore(tag.getString("target"), snapshots);
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
