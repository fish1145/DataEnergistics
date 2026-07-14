package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityAutoBuildDraftGameTest {

    private TrinityAutoBuildDraftGameTest() {}

    @TestHolder("trinity_auto_build_draft_retains_independent_revision_bound_choices")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsIndependentRevisionBoundChoices(GameTestHelper helper) {
        MultiblockPreviewSpec spec = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                .require(ModVerticalMultiBlocks.trinityDataCoreId());
        TrinityAutoBuildDraft initial = TrinityAutoBuildDraft.initial(spec);

        helper.assertValueEqual(initial.structureKeys().size(), 3,
                "Trinity auto-build draft must expose all three structures");
        helper.assertValueEqual(initial.previewSelection().activeSubstructureId(),
                JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME,
                "Trinity auto-build draft must start on main");
        helper.assertTrue(initial.activeBuildRequested(), "Main auto-build must retain its enabled default");
        helper.assertValueEqual(initial.activeRepeatCount(), 1, "Main repeat count must remain fixed at one");
        helper.assertTrue(initial.activeVariableRepeatUnit().isEmpty(),
                "Main structure must not expose a repeat stepper");

        TrinityAutoBuildDraft cpu = initial
                .select(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME)
                .withBuildRequested(true);
        int cpuRepeatUnit = cpu.activeVariableRepeatUnit().orElseThrow();
        cpu = cpu.withRepeat(cpuRepeatUnit, TrinityAutoBuildOptions.MAX_REPEAT_COUNT).withTier(10);
        helper.assertTrue(cpu.activeBuildRequested(), "CPU build choice must be independently enabled");
        helper.assertValueEqual(cpu.activeRepeatCount(), TrinityAutoBuildOptions.MAX_REPEAT_COUNT,
                "CPU repeat selection must retain the maximum value");
        helper.assertValueEqual(cpu.activeTierValue(), 10, "CPU tier selection must retain its own value");

        TrinityAutoBuildDraft crafting = cpu
                .select(ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME)
                .withTier(3);
        helper.assertFalse(crafting.activeBuildRequested(),
                "Crafting build choice must retain its independent disabled default");
        helper.assertValueEqual(crafting.activeTierValue(), 3,
                "Crafting tier selection must not reuse CPU tier state");

        TrinityAutoBuildDraft restoredCpu = crafting.select(
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME);
        helper.assertTrue(restoredCpu.activeBuildRequested(),
                "Switching structures must retain the CPU build choice");
        helper.assertValueEqual(restoredCpu.activeRepeatCount(), TrinityAutoBuildOptions.MAX_REPEAT_COUNT,
                "Switching structures must retain the CPU repeat choice");
        helper.assertValueEqual(restoredCpu.activeTierValue(), 10,
                "Switching structures must retain the CPU tier choice");

        TrinityAutoBuildSubmission submission = restoredCpu.submission();
        helper.assertValueEqual(submission.projectionFingerprint().definitionRevision(), spec.definitionRevision(),
                "Auto-build submission must retain the definition revision");
        helper.assertValueEqual(submission.projectionFingerprint().structureKey().structureName(),
                ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME,
                "Auto-build submission must use the stable CPU structure key");
        helper.assertValueEqual(submission.projectionFingerprint().variantIndex(), 0,
                "Current Trinity auto-build submission must carry explicit variant zero");
        helper.assertTrue(submission.buildRequested(), "Submission must retain the active build choice");

        TrinityAutoBuildRequest request = restoredCpu.toLegacyRequest();
        helper.assertValueEqual(request.structureIndex(), TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                "Legacy adapter must map the stable CPU key to its existing builder index");
        helper.assertValueEqual(request.options().repeatCount(), TrinityAutoBuildOptions.MAX_REPEAT_COUNT,
                "Legacy adapter must retain the CPU repeat count");
        helper.assertValueEqual(
                request.options().tierSelections().get(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE),
                10,
                "Legacy adapter must retain the CPU tier");
        helper.succeed();
    }
}
