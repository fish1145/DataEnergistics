package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityAutoBuildSubmissionResolverGameTest {

    private TrinityAutoBuildSubmissionResolverGameTest() {}

    @TestHolder("trinity_hosted_auto_build_resolver_reconstructs_and_rejects_invalid_fields")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reconstructsAndRejectsInvalidFields(GameTestHelper helper) {
        MultiblockPreviewSpec spec = DEVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                .require(DEVerticalMultiBlocks.trinityDataCoreId());
        TrinityAutoBuildSubmissionResolver resolver = new TrinityAutoBuildSubmissionResolver();

        TrinityAutoBuildDraft mainDraft = TrinityAutoBuildDraft.initial(spec);
        TrinityAutoBuildRequest main = resolver.resolve(spec, mainDraft.submission());
        assertEquals(TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX, main.structureIndex());
        assertEquals(1, main.options().repeatCount());
        assertTrue(main.options().buildRequested());

        TrinityAutoBuildDraft cpuDraft = mainDraft
                .select(DEVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME)
                .withBuildRequested(false)
                .withTier(4);
        int variableUnit = cpuDraft.activeVariableRepeatUnit().orElseThrow();
        cpuDraft = cpuDraft.withRepeat(variableUnit, 7);
        TrinityAutoBuildRequest cpu = resolver.resolve(spec, cpuDraft.submission());
        assertEquals(TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX, cpu.structureIndex());
        assertEquals(7, cpu.options().repeatCount());
        assertFalse(cpu.options().buildRequested());
        assertEquals(4, cpu.options().tierSelections().get(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE));

        ProjectionFingerprint fingerprint = mainDraft.submission().projectionFingerprint();
        assertRejected(resolver, spec, submission(withRevision(fingerprint, fingerprint.definitionRevision() + 1L)));
        assertRejected(resolver, spec, submission(withStructure(
                fingerprint,
                new JsonMultiBlockStructureKey(fingerprint.controllerId(), "unknown"))));
        assertRejected(resolver, spec, submission(withVariant(fingerprint, 1)));
        assertRejected(resolver, spec, submission(withTiers(fingerprint, Map.of("unknown", 1))));

        List<Integer> invalidRepeats = new ArrayList<>(fingerprint.repeatCounts());
        invalidRepeats.set(0, 2);
        assertRejected(resolver, spec, submission(withRepeats(fingerprint, invalidRepeats)));
        Map<PreviewPredicateKey, Integer> candidateSelections = Map.of(new PreviewPredicateKey(0, 0, 0), 0);
        TrinityAutoBuildRequest candidateRequest = resolver.resolve(
                spec,
                submission(withCandidates(fingerprint, candidateSelections)));
        assertEquals(candidateSelections, candidateRequest.options().candidateSelections());
        helper.succeed();
    }

    private static void assertRejected(TrinityAutoBuildSubmissionResolver resolver,
                                       MultiblockPreviewSpec spec,
                                       TrinityAutoBuildSubmission submission) {
        try {
            resolver.resolve(spec, submission);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new GameTestAssertException("Expected Trinity auto-build submission rejection");
    }

    private static TrinityAutoBuildSubmission submission(ProjectionFingerprint fingerprint) {
        return new TrinityAutoBuildSubmission(fingerprint, true);
    }

    private static ProjectionFingerprint withRevision(ProjectionFingerprint source, long revision) {
        return fingerprint(
                source,
                revision,
                source.structureKey(),
                source.variantIndex(),
                source.repeatCounts(),
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withStructure(ProjectionFingerprint source,
                                                       JsonMultiBlockStructureKey structureKey) {
        return fingerprint(
                source,
                source.definitionRevision(),
                structureKey,
                source.variantIndex(),
                source.repeatCounts(),
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withVariant(ProjectionFingerprint source, int variant) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                variant,
                source.repeatCounts(),
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withRepeats(ProjectionFingerprint source, List<Integer> repeats) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                source.variantIndex(),
                repeats,
                source.tierSelections(),
                source.candidateSelections());
    }

    private static ProjectionFingerprint withTiers(ProjectionFingerprint source, Map<String, Integer> tiers) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                source.variantIndex(),
                source.repeatCounts(),
                tiers,
                source.candidateSelections());
    }

    private static ProjectionFingerprint withCandidates(ProjectionFingerprint source,
                                                        Map<PreviewPredicateKey, Integer> candidates) {
        return fingerprint(
                source,
                source.definitionRevision(),
                source.structureKey(),
                source.variantIndex(),
                source.repeatCounts(),
                source.tierSelections(),
                candidates);
    }

    private static ProjectionFingerprint fingerprint(ProjectionFingerprint source,
                                                     long revision,
                                                     JsonMultiBlockStructureKey structureKey,
                                                     int variant,
                                                     List<Integer> repeats,
                                                     Map<String, Integer> tiers,
                                                     Map<PreviewPredicateKey, Integer> candidates) {
        return new ProjectionFingerprint(
                source.controllerId(),
                revision,
                structureKey,
                variant,
                repeats,
                new LinkedHashMap<>(tiers),
                new LinkedHashMap<>(candidates));
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new GameTestAssertException("Expected condition to be false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
