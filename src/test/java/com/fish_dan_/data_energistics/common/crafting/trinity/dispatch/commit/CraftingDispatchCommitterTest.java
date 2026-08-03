package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchAccountingDelta;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class CraftingDispatchCommitterTest {

    private static final long LOGICAL_CRAFTS = 3L;
    private static final UUID JOB_ID = new UUID(0L, 1L);
    private static final ICraftingProvider PROVIDER = new TestProvider();
    private static final IPatternDetails PATTERN = new TestPattern();
    private static final CraftingDispatchTarget TARGET = new CraftingDispatchTarget("test-target");

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void settlesEveryOwnershipBoundaryExactlyOnce(Scenario scenario) {
        AtomicLong nanoClock = new AtomicLong();
        CraftingDispatchWindow window = CraftingDispatchWindow.create(
                new CraftingDispatchLimits(8, 8, 100L),
                nanoClock::get);
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        KeyCounter[] prototype = {new KeyCounter()};
        TestAdmission admission = new TestAdmission(scenario);

        CraftingDispatchResult result;
        try (CraftingDispatchWindow.SubmissionScope submission = window.beginSubmission(PROVIDER, PATTERN)) {
            if (scenario.budgetStale()) {
                nanoClock.set(100L);
            }
            CraftingDispatchAccountingDelta accounting = CraftingDispatchAccountingDelta.create(
                    LOGICAL_CRAFTS,
                    () -> {
                        applied.incrementAndGet();
                        if (scenario.applyFailure()) {
                            throw new IllegalStateException("apply failure");
                        }
                    },
                    released::incrementAndGet);
            result = CraftingDispatchCommitter.create().commit(new CraftingDispatchCommitRequest(
                    7,
                    JOB_ID,
                    PROVIDER,
                    PATTERN,
                    TARGET,
                    admission,
                    prototype,
                    window,
                    submission,
                    accounting));
        }

        assertEquals(scenario.expectedStatus(), result.status());
        assertEquals(scenario.expectedLogicalCrafts(), result.logicalCrafts());
        assertEquals(scenario.physicalAttempted(), result.physicalAttempted());
        assertEquals(scenario.ownershipTransferred(), result.inputOwnershipTransferred());
        assertEquals(scenario.accountingSettled(), result.accountingSettled());
        assertEquals(scenario.expectedApplied(), applied.get());
        assertEquals(scenario.expectedReleased(), released.get());
        assertEquals(scenario.physicalAttempted() ? 1 : 0, window.attemptCount());
        assertEquals(BigInteger.valueOf(scenario.expectedLogicalCrafts()), window.committedLogicalCrafts());
        assertEquals(scenario.recordedResult() ? 1 : 0, window.resultCount(scenario.expectedStatus()));
        assertEquals(scenario.ownershipTransferred() && scenario.accountingSettled(), result.dispatched());
        assertEquals(!scenario.accountingSettled(), result.requiresJobAbort());
        assertEquals(scenario.commitInvoked() ? 1 : 0, admission.commitCalls);
    }

    private static Stream<Scenario> scenarios() {
        return Stream.of(
                new Scenario("accepted", CommitBehavior.ACCEPT, OwnershipBehavior.FALSE, false, false,
                        CraftingDispatchStatus.ACCEPTED, LOGICAL_CRAFTS, true, true, true, 1, 0, true, true),
                new Scenario("rejected before ownership", CommitBehavior.REJECT, OwnershipBehavior.FALSE, false, false,
                        CraftingDispatchStatus.REJECTED, 0L, true, false, true, 0, 1, true, true),
                new Scenario("exception before ownership", CommitBehavior.THROW, OwnershipBehavior.FALSE, false, false,
                        CraftingDispatchStatus.FAILED_BEFORE_OWNERSHIP, 0L, true, false, true, 0, 1, true, true),
                new Scenario("exception after declared ownership", CommitBehavior.THROW, OwnershipBehavior.TRUE, false, false,
                        CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP, LOGICAL_CRAFTS, true, true, true, 1, 0, true, true),
                new Scenario("accounting failure after ownership aborts", CommitBehavior.ACCEPT, OwnershipBehavior.FALSE, false, true,
                        CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP, LOGICAL_CRAFTS, true, true, false, 1, 0, true, true),
                new Scenario("budget exhaustion never calls provider", CommitBehavior.ACCEPT, OwnershipBehavior.FALSE, true, false,
                        CraftingDispatchStatus.STALE, 0L, false, false, true, 0, 1, false, false));
    }

    private enum CommitBehavior {
        ACCEPT,
        REJECT,
        THROW
    }

    private enum OwnershipBehavior {
        TRUE,
        FALSE
    }

    private record Scenario(
            String name,
            CommitBehavior commitBehavior,
            OwnershipBehavior ownershipBehavior,
            boolean budgetStale,
            boolean applyFailure,
            CraftingDispatchStatus expectedStatus,
            long expectedLogicalCrafts,
            boolean physicalAttempted,
            boolean ownershipTransferred,
            boolean accountingSettled,
            int expectedApplied,
            int expectedReleased,
            boolean recordedResult,
            boolean commitInvoked) {

        @Override
        public String toString() {
            return this.name;
        }
    }

    private static final class TestAdmission implements CountedCraftingAdmission {

        private final Scenario scenario;
        private int commitCalls;

        private TestAdmission(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public long count() {
            return LOGICAL_CRAFTS;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return switch (this.scenario.ownershipBehavior()) {
                case TRUE -> true;
                case FALSE -> false;
            };
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            this.commitCalls++;
            return switch (this.scenario.commitBehavior()) {
                case ACCEPT -> true;
                case REJECT -> false;
                case THROW -> throw new IllegalStateException("provider failure");
            };
        }
    }

    private static final class TestProvider implements ICraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            throw new UnsupportedOperationException("Committer test uses the prepared admission directly");
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class TestPattern implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }
}
