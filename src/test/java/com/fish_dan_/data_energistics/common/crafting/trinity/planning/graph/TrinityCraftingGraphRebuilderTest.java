package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCraftingGraphRebuilderTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void spreadsCaptureAcrossBudgetsAndPublishesOnlyTheCompleteSnapshot() {
        TestPattern first = pattern(Items.PAPER, Items.IRON_INGOT, Items.GOLD_NUGGET);
        TestPattern duplicateFirst = pattern(Items.PAPER, Items.IRON_INGOT, Items.GOLD_NUGGET);
        TestPattern second = pattern(Items.MAP, Items.IRON_INGOT, Items.DIAMOND);
        MutableSource source = new MutableSource(7L, List.of(first, duplicateFirst, second));
        StepClock clock = new StepClock(10L);
        TrinityCraftingGraphRebuilder rebuilder = new TrinityCraftingGraphRebuilder(source, clock);

        ArrayList<TrinityCraftingGraphRebuilder.AdvanceResult> advances = new ArrayList<>();
        TrinityCraftingGraphRebuilder.AdvanceResult result;
        do {
            result = rebuilder.advance(10L);
            advances.add(result);
            if (result.status() != TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED) {
                assertTrue(rebuilder.publishedSnapshot().isEmpty());
            }
        } while (result.status() != TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED);

        assertTrue(advances.size() >= 4);
        assertEquals(TrinityCraftingGraphRebuilder.AdvanceStatus.IN_PROGRESS, advances.getFirst().status());
        TrinityCraftingGraphSnapshot snapshot = rebuilder.publishedSnapshot().orElseThrow();
        assertEquals(7L, snapshot.revision());
        assertEquals(2, snapshot.patterns().size());
        assertEquals(2, snapshot.patternsProducing(AEItemKey.of(Items.IRON_INGOT)).size());
        assertEquals(1, snapshot.patternsProducing(AEItemKey.of(Items.GOLD_NUGGET)).size());
        assertEquals(
                TrinityCraftingGraphRebuilder.AdvanceStatus.CURRENT,
                rebuilder.advance(10L).status());
    }

    @Test
    void discardsAChangedRevisionAndKeepsThePreviousSnapshotUntilReplacementCompletes() {
        TestPattern original = pattern(Items.PAPER, Items.IRON_INGOT, Items.GOLD_NUGGET);
        MutableSource source = new MutableSource(1L, List.of(original));
        AtomicLong time = new AtomicLong();
        TrinityCraftingGraphRebuilder rebuilder = new TrinityCraftingGraphRebuilder(source, time::getAndIncrement);

        assertEquals(
                TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED,
                rebuilder.advance(1_000L).status());
        TrinityCraftingGraphSnapshot firstSnapshot = rebuilder.publishedSnapshot().orElseThrow();

        TestPattern stale = pattern(Items.MAP, Items.GOLD_INGOT, Items.EMERALD);
        source.replace(2L, List.of(stale));
        TestPattern replacement = pattern(Items.BOOK, Items.COPPER_INGOT, Items.DIAMOND);
        source.afterNextPatternListCapture(() -> source.replace(3L, List.of(replacement)));

        TrinityCraftingGraphRebuilder.AdvanceResult restarted = rebuilder.advance(1_000L);

        assertEquals(TrinityCraftingGraphRebuilder.AdvanceStatus.RESTARTED, restarted.status());
        assertEquals(3L, restarted.targetRevision());
        assertSame(firstSnapshot, rebuilder.publishedSnapshot().orElseThrow());
        assertEquals(1L, rebuilder.publishedSnapshot().orElseThrow().revision());

        assertEquals(
                TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED,
                rebuilder.advance(1_000L).status());
        TrinityCraftingGraphSnapshot replacementSnapshot = rebuilder.publishedSnapshot().orElseThrow();
        assertEquals(3L, replacementSnapshot.revision());
        assertEquals(List.of(AEItemKey.of(Items.BOOK)), replacementSnapshot.patterns().stream()
                .map(TrinityCraftingGraphPattern::definition)
                .toList());
        assertTrue(replacementSnapshot.patternsProducing(AEItemKey.of(Items.GOLD_INGOT)).isEmpty());
        assertEquals(1, replacementSnapshot.patternsProducing(AEItemKey.of(Items.COPPER_INGOT)).size());
        assertEquals(1, replacementSnapshot.patternsProducing(AEItemKey.of(Items.DIAMOND)).size());
    }

    @Test
    void detectsRevisionChangesWhileCapturingTheInitialKeyList() {
        MutableSource source = new MutableSource(10L, List.of(pattern(Items.PAPER, Items.IRON_INGOT, Items.DIAMOND)));
        source.afterNextKeyCapture(() -> source.replace(
                11L,
                List.of(pattern(Items.MAP, Items.GOLD_INGOT, Items.EMERALD))));
        AtomicLong time = new AtomicLong();
        TrinityCraftingGraphRebuilder rebuilder = new TrinityCraftingGraphRebuilder(source, time::getAndIncrement);

        TrinityCraftingGraphRebuilder.AdvanceResult first = rebuilder.advance(1_000L);

        assertEquals(TrinityCraftingGraphRebuilder.AdvanceStatus.RESTARTED, first.status());
        assertEquals(11L, first.targetRevision());
        assertTrue(rebuilder.publishedSnapshot().isEmpty());
        assertEquals(
                TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED,
                rebuilder.advance(1_000L).status());
        assertEquals(
                List.of(AEItemKey.of(Items.MAP)),
                rebuilder.publishedSnapshot().orElseThrow().patterns().stream()
                        .map(TrinityCraftingGraphPattern::definition)
                        .toList());
    }

    @Test
    void captureFailureDiscardsPartialBuildAndCannotSkipTheFailingPatternOnRetry() {
        MutableSource source = new MutableSource(1L, List.of(pattern(Items.PAPER, Items.IRON_INGOT, Items.DIAMOND)));
        AtomicLong time = new AtomicLong();
        TrinityCraftingGraphRebuilder rebuilder = new TrinityCraftingGraphRebuilder(source, time::getAndIncrement);
        assertEquals(
                TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED,
                rebuilder.advance(1_000L).status());
        TrinityCraftingGraphSnapshot previous = rebuilder.publishedSnapshot().orElseThrow();

        TestPattern first = pattern(Items.MAP, Items.GOLD_INGOT, Items.EMERALD);
        FlakyInputsPattern flaky = new FlakyInputsPattern(pattern(Items.BOOK, Items.GOLD_INGOT, Items.REDSTONE));
        TestPattern last = pattern(Items.COMPASS, Items.GOLD_INGOT, Items.LAPIS_LAZULI);
        source.replace(2L, List.of(first, flaky, last));

        assertThrows(IllegalStateException.class, () -> rebuilder.advance(1_000L));
        assertSame(previous, rebuilder.publishedSnapshot().orElseThrow());
        assertEquals(1L, rebuilder.publishedSnapshot().orElseThrow().revision());

        assertEquals(
                TrinityCraftingGraphRebuilder.AdvanceStatus.PUBLISHED,
                rebuilder.advance(1_000L).status());
        TrinityCraftingGraphSnapshot replacement = rebuilder.publishedSnapshot().orElseThrow();
        assertEquals(2L, replacement.revision());
        assertEquals(3, replacement.patterns().size());
        assertEquals(
                List.of(
                        AEItemKey.of(Items.BOOK),
                        AEItemKey.of(Items.COMPASS),
                        AEItemKey.of(Items.MAP)),
                replacement.patterns().stream()
                        .map(TrinityCraftingGraphPattern::definition)
                        .sorted(Comparator.comparing(AEItemKey::getId))
                        .toList());
    }

    @Test
    void failsFastWhenTheInjectedClockMovesBackwardsAndDropsThePartialBuild() {
        MutableSource source = new MutableSource(1L, List.of(pattern(Items.PAPER, Items.IRON_INGOT, Items.DIAMOND)));
        long[] samples = { 100L, 90L };
        AtomicInteger sampleIndex = new AtomicInteger();
        LongSupplier backwardsClock = () -> samples[Math.min(sampleIndex.getAndIncrement(), samples.length - 1)];
        TrinityCraftingGraphRebuilder rebuilder = new TrinityCraftingGraphRebuilder(source, backwardsClock);

        assertThrows(IllegalStateException.class, () -> rebuilder.advance(10L));
        assertTrue(rebuilder.publishedSnapshot().isEmpty());
    }

    private static TestPattern pattern(ItemLike definition, ItemLike primaryOutput, ItemLike byproduct) {
        return new TestPattern(
                AEItemKey.of(definition),
                new IPatternDetails.IInput[] {
                        new TestInput(2L, new GenericStack[] {
                                new GenericStack(AEItemKey.of(Items.REDSTONE), 1L),
                                new GenericStack(AEItemKey.of(Items.GLOWSTONE_DUST), 1L)
                        })
                },
                List.of(
                        new GenericStack(AEItemKey.of(primaryOutput), 1L),
                        new GenericStack(AEItemKey.of(byproduct), 1L)));
    }

    private static final class StepClock implements LongSupplier {

        private final AtomicLong time = new AtomicLong();
        private final long step;

        private StepClock(long step) {
            this.step = step;
        }

        @Override
        public long getAsLong() {
            return this.time.getAndAdd(this.step);
        }
    }

    private static final class MutableSource implements TrinityCraftingGraphCaptureSource {

        private long revision;
        private LinkedHashMap<AEKey, List<IPatternDetails>> patternsByPrimaryOutput;
        @Nullable
        private Runnable keyCaptureCallback;
        @Nullable
        private Runnable patternCaptureCallback;

        private MutableSource(long revision, List<? extends IPatternDetails> patterns) {
            replace(revision, patterns);
        }

        private void replace(long revision, List<? extends IPatternDetails> patterns) {
            this.revision = revision;
            LinkedHashMap<AEKey, List<IPatternDetails>> replacement = new LinkedHashMap<>();
            for (IPatternDetails pattern : patterns) {
                replacement.computeIfAbsent(
                        pattern.getPrimaryOutput().what(),
                        ignored -> new ArrayList<>()).add(pattern);
            }
            replacement.replaceAll((ignored, value) -> List.copyOf(value));
            this.patternsByPrimaryOutput = replacement;
        }

        private void afterNextKeyCapture(Runnable callback) {
            this.keyCaptureCallback = callback;
        }

        private void afterNextPatternListCapture(Runnable callback) {
            this.patternCaptureCallback = callback;
        }

        @Override
        public long revision() {
            return this.revision;
        }

        @Override
        public HolderLookup.Provider registries() {
            return RegistryAccess.EMPTY;
        }

        @Override
        public List<AEKey> captureCraftableKeys() {
            List<AEKey> captured = List.copyOf(this.patternsByPrimaryOutput.keySet());
            Runnable callback = this.keyCaptureCallback;
            this.keyCaptureCallback = null;
            if (callback != null) {
                callback.run();
            }
            return captured;
        }

        @Override
        public List<IPatternDetails> capturePatternsFor(AEKey primaryOutput) {
            List<IPatternDetails> captured = List.copyOf(this.patternsByPrimaryOutput.getOrDefault(primaryOutput, List.of()));
            Runnable callback = this.patternCaptureCallback;
            this.patternCaptureCallback = null;
            if (callback != null) {
                callback.run();
            }
            return captured;
        }
    }

    private record TestInput(long multiplier, GenericStack[] alternatives) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return this.alternatives;
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            for (GenericStack alternative : this.alternatives) {
                if (alternative.what().equals(input)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private record TestPattern(AEItemKey definition,
                               IPatternDetails.IInput[] inputs,
                               List<GenericStack> outputs)
            implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IPatternDetails.IInput[] getInputs() {
            return this.inputs;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }
    }

    private static final class FlakyInputsPattern implements IPatternDetails {

        private final TestPattern delegate;
        private final AtomicBoolean failNextCapture = new AtomicBoolean(true);

        private FlakyInputsPattern(TestPattern delegate) {
            this.delegate = delegate;
        }

        @Override
        public AEItemKey getDefinition() {
            return this.delegate.getDefinition();
        }

        @Override
        public IPatternDetails.IInput[] getInputs() {
            if (this.failNextCapture.compareAndSet(true, false)) {
                throw new IllegalStateException("intentional capture failure");
            }
            return this.delegate.getInputs();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.delegate.getOutputs();
        }
    }
}
