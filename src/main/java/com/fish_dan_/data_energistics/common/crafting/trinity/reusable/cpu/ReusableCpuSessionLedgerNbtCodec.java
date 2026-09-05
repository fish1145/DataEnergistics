package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityBoundInputSnapshotCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.SessionSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Snapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Submission;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.SubmissionEntry;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persists CPU custody independently of the current job; receipt metadata is never decoded as physical stock. */
public final class ReusableCpuSessionLedgerNbtCodec {

    private ReusableCpuSessionLedgerNbtCodec() {}

    public static CompoundTag encode(ReusableCpuSessionLedger ledger, HolderLookup.Provider registries) {
        Snapshot snapshot = ledger.snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putUUID("owner", snapshot.owner());
        ListTag sessions = new ListTag();
        for (SessionSnapshot session : snapshot.sessions()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", session.id());
            entry.putUUID("job", session.jobId());
            entry.putString("target", session.target().persistentIdentity());
            entry.putString("route", session.target().route().stableIdentity());
            session.target().route().machineIdentity().ifPresent(value -> entry.putString("machine", value));
            session.target().mode().ifPresent(value -> entry.putString("mode", value.toString()));
            entry.put("pattern", session.pattern().toTagGeneric(registries));
            entry.putString("definition", session.publication().definitionEncoding());
            entry.putString("publication", session.publication().publicationEncoding());
            entry.put("bindings", TrinityBoundInputSnapshotCodec.write(session.bindings(), registries));
            entry.putLong("next_sequence", session.nextSequence());
            entry.putBoolean("closing", session.closing());
            if (session.settlementFingerprint() != null) {
                entry.putString("settlement", session.settlementFingerprint());
            }
            ListTag submissions = new ListTag();
            for (SubmissionEntry saved : session.submissions()) {
                Submission submission = saved.submission();
                CompoundTag item = new CompoundTag();
                item.putLong("sequence", saved.sequence());
                item.put("work", writeWork(submission.work(), registries));
                item.putLong("count", submission.count());
                item.putLong("offer", submission.logicalOffer());
                item.putDouble("energy", submission.energy());
                item.put("outputs", writeAssets(submission.expectedOutputs(), registries));
                ListTag escrow = new ListTag();
                for (SlotStack asset : submission.escrow()) {
                    CompoundTag value = GenericStack.writeTag(registries, asset.stack());
                    value.putInt("input_slot", asset.slot());
                    escrow.add(value);
                }
                item.put("escrow", escrow);
                item.putBoolean("transferred", submission.transferred());
                item.putBoolean("accounted", submission.accounted());
                submissions.add(item);
            }
            entry.put("submissions", submissions);
            sessions.add(entry);
        }
        tag.put("sessions", sessions);
        return tag;
    }

    public static ReusableCpuSessionLedger decode(CompoundTag tag, HolderLookup.Provider registries) {
        if (integer(tag, "schema") != 1) {
            throw new IllegalArgumentException("Unsupported reusable CPU ledger schema");
        }
        UUID owner = uuid(tag, "owner");
        List<SessionSnapshot> sessions = new ObjectArrayList<>();
        for (Tag value : list(tag, "sessions")) {
            CompoundTag entry = (CompoundTag) value;
            Target target = new Target(string(entry, "target"),
                    new CountedCraftingTarget(false, string(entry, "route"), optionalString(entry, "machine")),
                    optionalString(entry, "mode").map(ResourceLocation::parse));
            if (!(key(compound(entry, "pattern"), registries) instanceof AEItemKey pattern)) {
                throw new IllegalArgumentException("Reusable CPU pattern must be an item key");
            }
            List<SubmissionEntry> submissions = new ObjectArrayList<>();
            for (Tag item : list(entry, "submissions")) {
                CompoundTag stored = (CompoundTag) item;
                List<SlotStack> escrow = new ObjectArrayList<>();
                for (Tag asset : list(stored, "escrow")) {
                    CompoundTag owned = (CompoundTag) asset;
                    escrow.add(new SlotStack(integer(owned, "input_slot"), stack(owned, registries)));
                }
                require(stored, "energy", Tag.TAG_DOUBLE);
                submissions.add(new SubmissionEntry(number(stored, "sequence"), new Submission(
                        readWork(compound(stored, "work"), registries), number(stored, "count"), number(stored, "offer"),
                        stored.getDouble("energy"), readAssets(list(stored, "outputs"), registries), escrow,
                        bool(stored, "transferred"), bool(stored, "accounted"))));
            }
            sessions.add(new SessionSnapshot(uuid(entry, "id"), uuid(entry, "job"), target, pattern,
                    new TrinityPatternIdentity(string(entry, "definition"), string(entry, "publication")),
                    TrinityBoundInputSnapshotCodec.read(list(entry, "bindings"), registries), submissions,
                    number(entry, "next_sequence"), bool(entry, "closing"), optionalString(entry, "settlement").orElse(null)));
        }
        return ReusableCpuSessionLedger.restore(new Snapshot(owner, sessions));
    }

    private static CompoundTag writeWork(Work work, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("generation", work.generation());
        tag.putInt("stage", work.stageIndex());
        tag.putInt("firing", work.firingIndex());
        tag.putString("definition", work.patternIdentity().definitionEncoding());
        tag.putString("publication", work.patternIdentity().publicationEncoding());
        tag.put("output", work.primaryOutput().toTagGeneric(registries));
        tag.putInt("variant", work.plannedVariantOrdinal());
        tag.putLong("maximum", work.maximumLogicalFirings());
        tag.putBoolean("cycle", work.cycle());
        tag.put("bindings", TrinityBoundInputSnapshotCodec.write(work.exactBindings(), registries));
        return tag;
    }

    private static Work readWork(CompoundTag tag, HolderLookup.Provider registries) {
        return new Work(number(tag, "generation"), integer(tag, "stage"), integer(tag, "firing"),
                new TrinityPatternIdentity(string(tag, "definition"), string(tag, "publication")),
                key(compound(tag, "output"), registries), integer(tag, "variant"), number(tag, "maximum"), bool(tag, "cycle"),
                TrinityBoundInputSnapshotCodec.read(list(tag, "bindings"), registries));
    }

    private static ListTag writeAssets(List<GenericStack> assets, HolderLookup.Provider registries) {
        ListTag tag = new ListTag();
        assets.forEach(asset -> tag.add(GenericStack.writeTag(registries, asset)));
        return tag;
    }

    private static List<GenericStack> readAssets(ListTag tag, HolderLookup.Provider registries) {
        List<GenericStack> result = new ObjectArrayList<>(tag.size());
        for (Tag value : tag) {
            result.add(stack((CompoundTag) value, registries));
        }
        return result;
    }

    private static GenericStack stack(CompoundTag tag, HolderLookup.Provider registries) {
        GenericStack stack = GenericStack.readTag(registries, tag);
        if (stack == null || stack.amount() <= 0L) {
            throw new IllegalArgumentException("Invalid reusable CPU asset");
        }
        return stack;
    }

    private static AEKey key(CompoundTag tag, HolderLookup.Provider registries) {
        AEKey key = AEKey.fromTagGeneric(registries, tag);
        if (key == null) {
            throw new IllegalArgumentException("Unknown reusable CPU key");
        }
        return key;
    }

    private static CompoundTag compound(CompoundTag tag, String field) {
        if (!(tag.get(field) instanceof CompoundTag value)) {
            throw new IllegalArgumentException("Missing reusable CPU compound: " + field);
        }
        return value;
    }

    private static ListTag list(CompoundTag tag, String field) {
        if (!(tag.get(field) instanceof ListTag value) || !value.isEmpty() && value.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Missing reusable CPU compound list: " + field);
        }
        return value;
    }

    private static UUID uuid(CompoundTag tag, String field) {
        if (!tag.hasUUID(field)) {
            throw new IllegalArgumentException("Invalid reusable CPU identity: " + field);
        }
        return tag.getUUID(field);
    }

    private static String string(CompoundTag tag, String field) {
        require(tag, field, Tag.TAG_STRING);
        return tag.getString(field);
    }

    private static Optional<String> optionalString(CompoundTag tag, String field) {
        return tag.contains(field) ? Optional.of(string(tag, field)) : Optional.empty();
    }

    private static long number(CompoundTag tag, String field) {
        require(tag, field, Tag.TAG_LONG);
        return tag.getLong(field);
    }

    private static int integer(CompoundTag tag, String field) {
        require(tag, field, Tag.TAG_INT);
        return tag.getInt(field);
    }

    private static boolean bool(CompoundTag tag, String field) {
        require(tag, field, Tag.TAG_BYTE);
        byte value = tag.getByte(field);
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Invalid reusable CPU boolean: " + field);
        }
        return value != 0;
    }

    private static void require(CompoundTag tag, String field, int type) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Missing or invalid reusable CPU field: " + field);
        }
    }
}
