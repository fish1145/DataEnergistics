package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.State.DEFERRED;
import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.State.INVALID;
import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.State.PENDING;
import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.State.VALID;
import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.Structure.CPU;
import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.Structure.CRAFTING;
import static com.fish_dan_.data_energistics.common.trinity.TrinityStructureValidation.Structure.MAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityStructureValidationImplTest {

    @Test
    void structuresKeepIndependentRuntimeStates() {
        TrinityStructureValidation validation = new TrinityStructureValidationImpl();

        assertEquals(PENDING, validation.status(MAIN).state());
        assertEquals(PENDING, validation.status(CPU).state());
        assertEquals(PENDING, validation.status(CRAFTING).state());

        validation.markValid(MAIN);
        validation.markInvalid(CPU);

        assertEquals(VALID, validation.status(MAIN).state());
        assertEquals(INVALID, validation.status(CPU).state());
        assertEquals(PENDING, validation.status(CRAFTING).state());
        assertTrue(validation.isValid(MAIN));
        assertFalse(validation.isValid(CPU));
    }

    @Test
    void exactMdlibUnloadedDiagnosticDefersWithoutRecordingMismatch() {
        TrinityStructureValidation validation = new TrinityStructureValidationImpl();
        BlockPos waitingPosition = new BlockPos(12, 34, 56);
        PatternDiagnostic unloaded = PatternDiagnostic.of(
                "mdlib:unloaded",
                "Position is not loaded",
                waitingPosition,
                List.of());

        assertTrue(validation.deferIfUnloaded(MAIN, unloaded, null));
        assertEquals(DEFERRED, validation.status(MAIN).state());
        assertEquals(waitingPosition, validation.status(MAIN).waitingPosition());
    }

    @Test
    void trackingViewObservationRecoversUnloadedFallbackDiagnostic() {
        TrinityStructureValidation validation = new TrinityStructureValidationImpl();
        BlockPos waitingPosition = new BlockPos(-20, 70, 41);
        PatternDiagnostic fallbackMismatch = PatternDiagnostic.of(
                "predicate",
                "Wrong block",
                BlockPos.ZERO,
                List.of("test:block"));

        assertTrue(validation.deferIfUnloaded(CRAFTING, fallbackMismatch, waitingPosition));
        assertEquals(DEFERRED, validation.status(CRAFTING).state());
        assertEquals(waitingPosition, validation.status(CRAFTING).waitingPosition());
    }

    @Test
    void ordinaryMismatchDoesNotEnterDeferredState() {
        TrinityStructureValidation validation = new TrinityStructureValidationImpl();
        validation.markValid(CPU);
        PatternDiagnostic mismatch = PatternDiagnostic.of(
                "predicate",
                "Wrong block",
                BlockPos.ZERO,
                List.of("test:block"));

        assertFalse(validation.deferIfUnloaded(CPU, mismatch, null));
        assertEquals(VALID, validation.status(CPU).state());
        assertNull(validation.status(CPU).waitingPosition());
    }

    @Test
    void deferredValidationPollsOnlyItsWaitingPositionAndRetriesOnce() {
        TrinityStructureValidation validation = new TrinityStructureValidationImpl();
        BlockPos waitingPosition = new BlockPos(8, 9, 10);
        AtomicInteger loadedChecks = new AtomicInteger();
        validation.deferIfUnloaded(
                MAIN,
                PatternDiagnostic.of("unloaded", "Position is not loaded", waitingPosition, List.of()),
                null);

        assertFalse(validation.resumeIfLoaded(MAIN, position -> {
            loadedChecks.incrementAndGet();
            assertEquals(waitingPosition, position);
            return false;
        }));
        assertEquals(1, loadedChecks.get());
        assertEquals(DEFERRED, validation.status(MAIN).state());

        assertTrue(validation.resumeIfLoaded(MAIN, position -> {
            loadedChecks.incrementAndGet();
            assertEquals(waitingPosition, position);
            return true;
        }));
        assertEquals(2, loadedChecks.get());
        assertEquals(PENDING, validation.status(MAIN).state());
        assertNull(validation.status(MAIN).waitingPosition());

        assertFalse(validation.resumeIfLoaded(MAIN, position -> {
            loadedChecks.incrementAndGet();
            return true;
        }));
        assertEquals(2, loadedChecks.get());
    }
}
