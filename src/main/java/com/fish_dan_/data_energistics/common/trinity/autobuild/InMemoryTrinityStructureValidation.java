package com.fish_dan_.data_energistics.common.trinity.autobuild;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/** Default in-memory validation state used by one Trinity Data Core host. */
public final class InMemoryTrinityStructureValidation implements TrinityStructureValidation {

    /** Exact MDLib diagnostic emitted when a structure position is outside the loaded world view. */
    private static final ResourceLocation UNLOADED_DIAGNOSTIC = ResourceLocation.fromNamespaceAndPath("mdlib", "unloaded");

    /** Independent runtime status indexed by structure capability domain. */
    private final Map<Structure, Status> statuses = new EnumMap<>(Structure.class);

    /** Creates a validation set whose structures all require an initial pass. */
    public InMemoryTrinityStructureValidation() {
        reset();
    }

    @Override
    public Status status(Structure structure) {
        return this.statuses.get(structure);
    }

    @Override
    public boolean isValid(Structure structure) {
        return status(structure).state() == State.VALID;
    }

    @Override
    public void markPending(Structure structure) {
        this.statuses.put(structure, new Status(State.PENDING, null));
    }

    @Override
    public void markValid(Structure structure) {
        this.statuses.put(structure, new Status(State.VALID, null));
    }

    @Override
    public void markInvalid(Structure structure) {
        this.statuses.put(structure, new Status(State.INVALID, null));
    }

    @Override
    public boolean deferIfUnloaded(Structure structure,
                                   @Nullable PatternDiagnostic diagnostic,
                                   @Nullable BlockPos observedUnloadedPosition) {
        BlockPos waitingPosition = observedUnloadedPosition;
        if (diagnostic != null && UNLOADED_DIAGNOSTIC.equals(diagnostic.code())) {
            waitingPosition = diagnostic.position();
        } else if (waitingPosition == null) {
            return false;
        }
        this.statuses.put(structure, new Status(State.DEFERRED, waitingPosition.immutable()));
        return true;
    }

    @Override
    public boolean resumeIfLoaded(Structure structure, Predicate<BlockPos> isLoaded) {
        Status current = status(structure);
        if (current.state() != State.DEFERRED || !isLoaded.test(current.waitingPosition())) {
            return false;
        }
        markPending(structure);
        return true;
    }

    @Override
    public void reset() {
        for (Structure structure : Structure.values()) {
            markPending(structure);
        }
    }
}
