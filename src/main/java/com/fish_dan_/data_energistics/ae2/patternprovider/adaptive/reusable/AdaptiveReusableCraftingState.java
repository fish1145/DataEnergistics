package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.reusable;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.ReusableCraftingEndpointNbtCodec;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistent native-slot ownership, with total-input admissions and no duplicate asset ledger. */
public final class AdaptiveReusableCraftingState {

    public static final String NBT_KEY = "adaptive_reusable_state";
    public static final ResourceLocation MODE = ResourceLocation.fromNamespaceAndPath("data_energistics", "meteorite");
    private static final int SCHEMA = 1;

    public static final class Slot {

        private final int index;
        private final AEItemKey pattern;
        private final ResourceLocation recipe;
        private final PersistentReusableCraftingEndpoint endpoint;
        private boolean closing;

        private Slot(int index, AEItemKey pattern, ResourceLocation recipe, PersistentReusableCraftingEndpoint endpoint, boolean closing) {
            this.index = index;
            this.pattern = pattern;
            this.recipe = recipe;
            this.endpoint = endpoint;
            this.closing = closing;
        }

        public int index() {
            return index;
        }

        public AEItemKey pattern() {
            return pattern;
        }

        public ResourceLocation recipe() {
            return recipe;
        }

        public PersistentReusableCraftingEndpoint endpoint() {
            return endpoint;
        }

        public boolean closing() {
            return closing;
        }

        public void requestClose() {
            closing = true;
        }

        public void close(Host host) {
            endpoint.residentSessionId().ifPresent(id -> endpoint.close(id, host));
            closing = false;
            host.persistChanges();
        }
    }

    private final UUID providerId;
    private final Int2ObjectLinkedOpenHashMap<Slot> slots = new Int2ObjectLinkedOpenHashMap<>();
    private boolean handoffPrepared;

    public AdaptiveReusableCraftingState() {
        this(UUID.randomUUID());
    }

    private AdaptiveReusableCraftingState(UUID providerId) {
        this.providerId = providerId;
    }

    public String targetIdentity(int slot) {
        return "adaptive-provider:" + providerId + "/meteorite/slot:" + slot;
    }

    public @Nullable Slot slot(int index) {
        return slots.get(index);
    }

    public List<Slot> slots() {
        return List.copyOf(slots.values());
    }

    public boolean handoffPrepared() {
        return handoffPrepared;
    }

    public boolean hasResidents() {
        for (Slot slot : slots.values()) {
            if (slot.endpoint.hasResidentSession()) {
                return true;
            }
        }
        return false;
    }

    public long pendingOperations() {
        long pending = 0;
        for (Slot slot : slots.values()) {
            Optional<UUID> resident = slot.endpoint.residentSessionId();
            if (resident.isPresent()) {
                ReusableCraftingSessionView view = slot.endpoint.query(resident.orElseThrow()).orElseThrow();
                pending = Math.addExact(pending, view.accepted() - view.completed() - view.cancelled());
            }
        }
        return pending;
    }

    public @Nullable Slot locate(UUID sessionId) {
        if (handoffPrepared) {
            return null;
        }
        for (Slot slot : slots.values()) {
            if (slot.endpoint.query(sessionId).isPresent()) {
                return slot;
            }
        }
        return null;
    }

    /** Prepares within the caller's shared remaining round budget; no reservation is taken before commit. */
    public @Nullable ReusableCraftingAdmission prepare(int index, ResourceLocation recipe, ReusableCraftingRequest request,
                                                       long currentTick, long availableCount, Host host) {
        if (handoffPrepared || index < 0 || !request.target().persistentIdentity().equals(targetIdentity(index))) {
            return null;
        }
        Slot existing = slots.get(index);
        if (existing != null) {
            var accepted = existing.endpoint.acceptedAppend(request.sessionId(), request.sequence());
            if (accepted.isPresent()) {
                var append = accepted.orElseThrow();
                if (request.requestedCount() < append.operations()) {
                    return null;
                }
                List<SlotStack> supplied = append.deliveredTools().stream().map(tool -> new SlotStack(tool.slot(), tool.stack())).toList();
                return existing.endpoint.prepare(bounded(request, append.operations(), supplied), currentTick, host);
            }
            if (existing.endpoint.hasResidentSession() &&
                    (existing.closing || !existing.pattern.equals(request.pattern().getDefinition()) || !existing.recipe.equals(recipe))) {
                return null;
            }
        }
        long count = Math.min(request.requestedCount(), availableCount);
        if (count <= 0) {
            return null;
        }
        PersistentReusableCraftingEndpoint endpoint = existing == null ? new PersistentReusableCraftingEndpoint(targetIdentity(index)) : existing.endpoint;
        List<SlotStack> supplied = requiredTools(request, endpoint, endpoint.query(request.sessionId()), count);
        ReusableCraftingAdmission prepared = endpoint.prepare(bounded(request, count, supplied), currentTick, host);
        if (prepared == null) {
            return null;
        }
        Slot target = new Slot(index, request.pattern().getDefinition(), recipe, endpoint, false);
        return new ReusableCraftingAdmission() {

            @Override
            public long count() {
                return prepared.count();
            }

            @Override
            public List<SlotStack> physicalInputs() {
                return prepared.physicalInputs();
            }

            @Override
            public boolean replay() {
                return prepared.replay();
            }

            @Override
            public boolean hasTransferredInputOwnership() {
                return prepared.hasTransferredInputOwnership();
            }

            @Override
            public boolean commit(KeyCounter[] delivery) {
                if (handoffPrepared || slots.get(index) != existing || existing != null && existing.closing && existing.endpoint.hasResidentSession()) {
                    return false;
                }
                try {
                    return prepared.commit(delivery);
                } finally {
                    if (prepared.hasTransferredInputOwnership()) {
                        slots.put(index, target);
                        host.persistChanges();
                    }
                }
            }
        };
    }

    private static ReusableCraftingRequest bounded(ReusableCraftingRequest request, long count, List<SlotStack> tools) {
        return new ReusableCraftingRequest(request.sessionId(), request.jobId(), request.cpuOwner(), request.sequence(), request.target(),
                request.pattern(), request.inputs(), tools, count, request.recipeId(), request.actionSource(), request.level());
    }

    private static List<SlotStack> requiredTools(ReusableCraftingRequest request, PersistentReusableCraftingEndpoint endpoint,
                                                 Optional<ReusableCraftingSessionView> current, long count) {
        long reserved = count;
        List<SlotStack> held = List.of();
        if (current.isPresent()) {
            ReusableCraftingSessionView view = current.orElseThrow();
            reserved = Math.addExact(reserved, view.accepted() - view.completed() - view.cancelled());
            held = view.heldTools();
        }
        List<SlotStack> result = new ObjectArrayList<>();
        for (var input : request.inputs()) {
            if (input.tool().isEmpty()) {
                continue;
            }
            var tool = input.tool().orElseThrow();
            if (tool.operationState().isPresent()) {
                AEItemKey expected = tool.operationState().orElseThrow();
                boolean unchanged = expected.equals(tool.rule().advance(expected, 1).successor());
                long operations = unchanged ? 1 : count;
                if (!unchanged && current.isPresent()) {
                    operations = Math.addExact(operations, endpoint.reservedToolUses(request.sessionId(), input.slot(), expected));
                }
                long needed = Math.multiplyExact(operations, tool.heldAmount());
                for (SlotStack asset : held) {
                    if (asset.slot() == input.slot() && asset.stack().what().equals(expected)) {
                        needed -= Math.min(needed, asset.stack().amount());
                    }
                }
                for (SlotStack offered : request.offeredTools()) {
                    if (needed == 0) {
                        break;
                    }
                    if (offered.slot() == input.slot() && offered.stack().what().equals(expected)) {
                        long units = Math.min(needed, offered.stack().amount());
                        result.add(new SlotStack(input.slot(), new GenericStack(expected, units)));
                        needed -= units;
                    }
                }
                continue;
            }
            long needed = Math.multiplyExact(reserved, tool.heldAmount());
            for (SlotStack asset : held) {
                if (asset.slot() == input.slot()) {
                    long uses = Math.min(reserved, tool.rule().guaranteedUses((AEItemKey) asset.stack().what()));
                    needed = subtractCapacity(needed, asset.stack().amount(), uses);
                }
            }
            for (SlotStack offered : request.offeredTools()) {
                if (needed == 0) {
                    break;
                }
                if (offered.slot() == input.slot()) {
                    if (!(offered.stack().what() instanceof AEItemKey key)) {
                        throw new IllegalArgumentException("Reusable native tools must be item keys");
                    }
                    long uses = Math.min(reserved, tool.rule().guaranteedUses(key));
                    long units = Math.min(offered.stack().amount(), needed / uses + (needed % uses == 0 ? 0 : 1));
                    result.add(new SlotStack(input.slot(), new GenericStack(key, units)));
                    needed = subtractCapacity(needed, units, uses);
                }
            }
        }
        return List.copyOf(result);
    }

    private static long subtractCapacity(long needed, long units, long uses) {
        long enough = needed / uses + (needed % uses == 0 ? 0 : 1);
        return units >= enough ? 0 : needed - units * uses;
    }

    public CompoundTag writeToTag(HolderLookup.Provider registries) {
        return encode(registries, false);
    }

    /** Freezes the source until its real clearContent handoff; the destination is marked for safe closure. */
    public CompoundTag prepareItemHandoff(HolderLookup.Provider registries) {
        CompoundTag payload = encode(registries, true);
        handoffPrepared = true;
        return payload;
    }

    public void ensureCanClear() {
        if (hasResidents() && !handoffPrepared) {
            throw new IllegalStateException("Cannot erase resident reusable assets without a prepared provider-item handoff");
        }
    }

    private CompoundTag encode(HolderLookup.Provider registries, boolean itemHandoff) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putUUID("provider", providerId);
        tag.putBoolean("handoff_prepared", !itemHandoff && handoffPrepared);
        ListTag encoded = new ListTag();
        for (Slot slot : slots.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot.index);
            entry.put("pattern", slot.pattern.toTagGeneric(registries));
            entry.putString("recipe", slot.recipe.toString());
            entry.putBoolean("closing", itemHandoff || slot.closing);
            entry.put("endpoint", ReusableCraftingEndpointNbtCodec.encode(slot.endpoint, registries));
            encoded.add(entry);
        }
        tag.put("slots", encoded);
        return tag;
    }

    public static AdaptiveReusableCraftingState readFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA || !tag.hasUUID("provider") ||
                !tag.contains("handoff_prepared", Tag.TAG_BYTE) || !(tag.get("slots") instanceof ListTag entries) ||
                !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Malformed adaptive reusable state");
        }
        AdaptiveReusableCraftingState result = new AdaptiveReusableCraftingState(tag.getUUID("provider"));
        result.handoffPrepared = tag.getBoolean("handoff_prepared");
        for (Tag encoded : entries) {
            CompoundTag entry = (CompoundTag) encoded;
            if (!entry.contains("slot", Tag.TAG_INT) || entry.getInt("slot") < 0 || !entry.contains("pattern", Tag.TAG_COMPOUND) ||
                    !entry.contains("recipe", Tag.TAG_STRING) || !entry.contains("closing", Tag.TAG_BYTE) || !entry.contains("endpoint", Tag.TAG_COMPOUND) ||
                    !(AEKey.fromTagGeneric(registries, entry.getCompound("pattern")) instanceof AEItemKey pattern)) {
                throw new IllegalArgumentException("Malformed adaptive reusable native slot");
            }
            int slot = entry.getInt("slot");
            PersistentReusableCraftingEndpoint endpoint = ReusableCraftingEndpointNbtCodec.decode(entry.getCompound("endpoint"), registries);
            if (!endpoint.targetIdentity().equals(result.targetIdentity(slot))) {
                throw new IllegalArgumentException("Adaptive reusable endpoint does not belong to its provider and slot");
            }
            Slot restored = new Slot(slot, pattern, ResourceLocation.parse(entry.getString("recipe")), endpoint, entry.getBoolean("closing"));
            if (result.slots.put(slot, restored) != null) {
                throw new IllegalArgumentException("Duplicate adaptive reusable native slot");
            }
        }
        return result;
    }
}
