package com.fish_dan_.data_energistics.orbital.attack.entity.strike;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread journal owned by one attack, including its subsequent payload work. UUID deduplication lasts only
 * as long as that attack: a respawned object cannot be hit twice by it, while later independent attacks remain valid.
 * The owner must mark its SavedData dirty before handing this mutable journal to a terrain/entity worker.
 */
public final class OrbitalErasureStrike {

    private final UUID strikeId;
    private final @Nullable UUID initiatorId;
    private final Set<UUID> exemptions;
    private final Object2ObjectOpenHashMap<UUID, Attempt> attempts = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<UUID> encounters = new ObjectOpenHashSet<>();
    private boolean usable = true;

    public OrbitalErasureStrike(UUID strikeId, @Nullable UUID initiatorId, Set<UUID> exemptions) {
        this.strikeId = strikeId;
        this.initiatorId = initiatorId;
        this.exemptions = Set.copyOf(exemptions);
    }

    public UUID strikeId() {
        return this.strikeId;
    }

    public @Nullable UUID initiatorId() {
        return this.initiatorId;
    }

    public boolean exempts(UUID subject) {
        return this.exemptions.contains(subject);
    }

    /** Claims one subject before callbacks run, preventing both reentrancy and post-respawn repeat hits. */
    public @Nullable UUID begin(UUID subject) {
        if (!this.usable) {
            throw new IllegalStateException("An unreadable erasure journal cannot replay entity work");
        }
        if (this.attempts.containsKey(subject)) {
            return null;
        }
        UUID execution = UUID.randomUUID();
        this.attempts.put(subject, new Attempt(execution, OrbitalErasureOutcome.IN_PROGRESS));
        return execution;
    }

    public void complete(UUID subject, UUID execution, OrbitalErasureOutcome outcome) {
        Attempt previous = this.attempts.get(subject);
        if (previous == null || !previous.executionId().equals(execution) || previous.outcome() != OrbitalErasureOutcome.IN_PROGRESS || outcome == OrbitalErasureOutcome.IN_PROGRESS) {
            throw new IllegalStateException("Erasure result does not match the claimed execution");
        }
        this.attempts.put(subject, new Attempt(execution, outcome));
    }

    public Optional<Attempt> result(UUID subject) {
        return Optional.ofNullable(this.attempts.get(subject));
    }

    public boolean usable() {
        return this.usable;
    }

    /** Uses the encounter's persistent identity, separately from the current guardian entity UUID. */
    public boolean claimEncounter(UUID encounterId) {
        if (!this.usable) {
            throw new IllegalStateException("An unreadable erasure journal cannot replay encounter work");
        }
        return this.encounters.add(encounterId);
    }

    /** Keeps a corrupt attack diagnosable and refundable without guessing which subjects it already terminated. */
    public static OrbitalErasureStrike quarantined(UUID strikeId, Set<UUID> exemptions) {
        OrbitalErasureStrike strike = new OrbitalErasureStrike(strikeId, null, exemptions);
        strike.usable = false;
        return strike;
    }

    /** Writes only strike-scoped identities/results; no player-life lock or live entity reference is persisted. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("unusable", !this.usable);
        if (this.initiatorId != null) {
            tag.putUUID("initiator", this.initiatorId);
        }
        ListTag subjects = new ListTag();
        this.attempts.forEach((subject, attempt) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("subject", subject);
            entry.putUUID("execution", attempt.executionId());
            entry.putString("outcome", attempt.outcome().name());
            subjects.add(entry);
        });
        tag.put("subjects", subjects);
        ListTag encounters = new ListTag();
        for (UUID encounter : this.encounters) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", encounter);
            encounters.add(entry);
        }
        tag.put("encounters", encounters);
        return tag;
    }

    /** Missing journals are old attacks; malformed present journals fail at the persistence boundary. */
    public static OrbitalErasureStrike load(UUID strikeId, Set<UUID> exemptions, @Nullable CompoundTag tag) {
        if (tag == null) {
            return new OrbitalErasureStrike(strikeId, null, exemptions);
        }
        if (tag.getBoolean("unusable")) {
            return quarantined(strikeId, exemptions);
        }
        if ((tag.contains("initiator") && !tag.hasUUID("initiator")) || !tag.contains("subjects", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Invalid orbital erasure journal");
        }
        OrbitalErasureStrike strike = new OrbitalErasureStrike(strikeId, tag.hasUUID("initiator") ? tag.getUUID("initiator") : null, exemptions);
        ListTag subjects = (ListTag) tag.get("subjects");
        for (Tag raw : subjects) {
            if (!(raw instanceof CompoundTag entry) || !entry.hasUUID("subject") || !entry.hasUUID("execution") || !entry.contains("outcome", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Invalid orbital erasure subject record");
            }
            OrbitalErasureOutcome outcome = OrbitalErasureOutcome.valueOf(entry.getString("outcome"));
            // A crash inside a callback leaves an uncertain result, never permission to replay its side effects.
            if (outcome == OrbitalErasureOutcome.IN_PROGRESS) {
                outcome = OrbitalErasureOutcome.PARTIAL_FAILURE;
            }
            if (strike.attempts.putIfAbsent(entry.getUUID("subject"), new Attempt(entry.getUUID("execution"), outcome)) != null) {
                throw new IllegalArgumentException("Duplicate subject in orbital erasure journal");
            }
        }
        if (tag.contains("encounters")) {
            if (!(tag.get("encounters") instanceof ListTag encounters)) {
                throw new IllegalArgumentException("Invalid orbital encounter journal");
            }
            for (Tag raw : encounters) {
                if (!(raw instanceof CompoundTag entry) || !entry.hasUUID("id") || !strike.encounters.add(entry.getUUID("id"))) {
                    throw new IllegalArgumentException("Invalid or duplicate orbital encounter identity");
                }
            }
        }
        return strike;
    }

    public record Attempt(UUID executionId, OrbitalErasureOutcome outcome) {}
}
