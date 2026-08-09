package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.State.DEFERRED;
import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.State.INVALID;
import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.State.PENDING;
import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.State.VALID;
import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.Structure.CPU;
import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.Structure.CRAFTING;
import static com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityStructureValidation.Structure.MAIN;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityStructureValidationGameTest {

    private TrinityStructureValidationGameTest() {}

    @TestHolder("trinity_structure_validation_tracks_independent_deferred_states")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void validationTracksIndependentDeferredStates(GameTestHelper helper) {
        TrinityStructureValidation validation = new InMemoryTrinityStructureValidation();

        helper.assertValueEqual(validation.status(MAIN).state(), PENDING,
                "Main validation must initially be pending");
        helper.assertValueEqual(validation.status(CPU).state(), PENDING,
                "CPU validation must initially be pending");
        helper.assertValueEqual(validation.status(CRAFTING).state(), PENDING,
                "Crafting validation must initially be pending");

        validation.markValid(MAIN);
        validation.markInvalid(CPU);
        helper.assertValueEqual(validation.status(MAIN).state(), VALID,
                "Main validation must retain its independent valid state");
        helper.assertValueEqual(validation.status(CPU).state(), INVALID,
                "CPU validation must retain its independent invalid state");
        helper.assertValueEqual(validation.status(CRAFTING).state(), PENDING,
                "Crafting validation must remain independently pending");
        helper.assertTrue(validation.isValid(MAIN), "Valid main validation must publish as valid");
        helper.assertFalse(validation.isValid(CPU), "Invalid CPU validation must not publish as valid");

        BlockPos mainWaitingPosition = new BlockPos(12, 34, 56);
        PatternDiagnostic unloaded = PatternDiagnostic.of(
                "mdlib:unloaded",
                "Position is not loaded",
                mainWaitingPosition,
                List.of());
        helper.assertTrue(validation.deferIfUnloaded(MAIN, unloaded, null),
                "The exact MDLib unloaded diagnostic must defer main validation");
        helper.assertValueEqual(validation.status(MAIN).state(), DEFERRED,
                "Main validation must enter deferred state");
        helper.assertValueEqual(validation.status(MAIN).waitingPosition(), mainWaitingPosition,
                "Main validation must retain the diagnostic position");

        BlockPos craftingWaitingPosition = new BlockPos(-20, 70, 41);
        PatternDiagnostic mismatch = PatternDiagnostic.of(
                "predicate",
                "Wrong block",
                BlockPos.ZERO,
                List.of("test:block"));
        helper.assertTrue(validation.deferIfUnloaded(CRAFTING, mismatch, craftingWaitingPosition),
                "A tracked unloaded position must recover a fallback mismatch diagnostic");
        helper.assertValueEqual(validation.status(CRAFTING).state(), DEFERRED,
                "Crafting validation must enter deferred state independently");
        helper.assertValueEqual(validation.status(CRAFTING).waitingPosition(), craftingWaitingPosition,
                "Crafting validation must retain the tracked unloaded position");

        validation.markValid(CPU);
        helper.assertFalse(validation.deferIfUnloaded(CPU, mismatch, null),
                "An ordinary mismatch without an unloaded observation must not defer validation");
        helper.assertValueEqual(validation.status(CPU).state(), VALID,
                "An ordinary mismatch must not replace the CPU validation state");
        helper.assertTrue(validation.status(CPU).waitingPosition() == null,
                "An ordinary mismatch must not record a waiting position");

        AtomicInteger loadedChecks = new AtomicInteger();
        helper.assertFalse(validation.resumeIfLoaded(MAIN, position -> {
            loadedChecks.incrementAndGet();
            helper.assertValueEqual(position, mainWaitingPosition,
                    "Deferred validation must poll only its stored waiting position");
            return false;
        }), "An unloaded waiting position must not resume validation");
        helper.assertValueEqual(loadedChecks.get(), 1,
                "One deferred poll must perform one loaded-position check");
        helper.assertValueEqual(validation.status(MAIN).state(), DEFERRED,
                "An unloaded waiting position must remain deferred");

        helper.assertTrue(validation.resumeIfLoaded(MAIN, position -> {
            loadedChecks.incrementAndGet();
            helper.assertValueEqual(position, mainWaitingPosition,
                    "Resumed validation must poll the same stored waiting position");
            return true;
        }), "A loaded waiting position must schedule one validation retry");
        helper.assertValueEqual(loadedChecks.get(), 2,
                "Loading the waiting position must perform one additional check");
        helper.assertValueEqual(validation.status(MAIN).state(), PENDING,
                "Resumed validation must move to pending exactly once");
        helper.assertTrue(validation.status(MAIN).waitingPosition() == null,
                "Resumed validation must clear its waiting position");

        helper.assertFalse(validation.resumeIfLoaded(MAIN, position -> {
            loadedChecks.incrementAndGet();
            return true;
        }), "Pending validation must not schedule a duplicate retry");
        helper.assertValueEqual(loadedChecks.get(), 2,
                "Pending validation must not poll the former waiting position again");
        helper.succeed();
    }
}
