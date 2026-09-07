package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.OutputContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Submission;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableCpuSessionLedgerGameTest {

    private static final UUID OWNER = UUID.fromString("56f5102b-5a64-42a9-9b65-d570571330a0");
    private static final UUID JOB = UUID.fromString("145e792b-119c-41bf-a35d-fe482c4be20c");
    private static final UUID SESSION = UUID.fromString("545437ed-2578-4c67-a4f9-4e23d5d08ee9");
    private static final AEItemKey PATTERN = AEItemKey.of(Items.CRAFTING_TABLE);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.IRON_NUGGET);
    private static final Target TARGET = new Target("test-reusable-cpu",
            CountedCraftingTarget.route("test-reusable-cpu-route"), Optional.empty());
    private static final TrinityPatternIdentity PUBLICATION = new TrinityPatternIdentity("test-definition", "test-publication");

    private ReusableCpuSessionLedgerGameTest() {}

    @TestHolder("reusable_cpu_local_escrow_must_be_released_before_restorable_settlement")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void localEscrowMustBeReleasedBeforeRestorableSettlement(GameTestHelper helper) {
        ReusableCpuSessionLedger ledger = openLedger();
        long sequence = ledger.prepare(SESSION, localSubmission());
        ReusableCpuSessionLedger.Snapshot before = ledger.snapshot();
        AtomicInteger receives = new AtomicInteger();

        expectState(helper, () -> ledger.settle(emptySettlement(), "local-escrow", ignored -> receives.incrementAndGet()),
                "Settlement must not acknowledge while the CPU still owns local escrow");
        helper.assertValueEqual(receives.get(), 0, "Rejected settlement must not invoke its receiver");
        helper.assertValueEqual(ledger.snapshot(), before, "Rejected settlement must not mutate the ledger snapshot");
        helper.assertValueEqual(ledger.reject(SESSION, sequence), localSubmission().escrow(),
                "Rejected local assets must be returned to the CPU caller");

        helper.assertTrue(ledger.settle(emptySettlement(), "after-rejection", ignored -> {}),
                "A session with no outstanding submissions must settle");
        ReusableCpuSessionLedger.Snapshot snapshot = ledger.snapshot();
        helper.assertValueEqual(ReusableCpuSessionLedger.restore(snapshot).snapshot(), snapshot,
                "A legally settled session must remain restorable");
        helper.assertValueEqual(ReusableCpuSessionLedgerNbtCodec.decode(ReusableCpuSessionLedgerNbtCodec.encode(ledger,
                helper.getLevel().registryAccess()), helper.getLevel().registryAccess()).snapshot(), snapshot,
                "Owner and rejected sequence history survive CPU custody persistence");
        helper.succeed();
    }

    @TestHolder("reusable_cpu_transferred_receipt_settlement_is_idempotent")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void transferredReceiptSettlementIsIdempotent(GameTestHelper helper) {
        ReusableCpuSessionLedger ledger = openLedger();
        long sequence = ledger.prepare(SESSION, localSubmission());
        ledger.transferred(SESSION, sequence);
        ledger = ReusableCpuSessionLedgerNbtCodec.decode(ReusableCpuSessionLedgerNbtCodec.encode(ledger,
                helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        Settlement settlement = new Settlement(SESSION, JOB, OWNER.toString(), TARGET.persistentIdentity(), 0L,
                List.of(), List.of(), 0L, List.of(new AppendReceipt(sequence, 1L, 1L, 0L)), Optional.empty());
        AtomicInteger receives = new AtomicInteger();

        helper.assertTrue(ledger.settle(settlement, "transferred-receipt", ignored -> receives.incrementAndGet()),
                "Matching transferred work must settle");
        helper.assertTrue(ledger.settle(settlement, "transferred-receipt", ignored -> receives.incrementAndGet()),
                "The same settlement fingerprint must acknowledge a replay");
        helper.assertValueEqual(receives.get(), 1, "A replay must not invoke the settlement receiver twice");
        helper.succeed();
    }

    private static ReusableCpuSessionLedger openLedger() {
        ReusableCpuSessionLedger ledger = new ReusableCpuSessionLedger(OWNER);
        ledger.open(SESSION, JOB, TARGET, PATTERN, PUBLICATION, List.of());
        return ledger;
    }

    private static Submission localSubmission() {
        Work work = new Work(0L, 0, 0, PUBLICATION, OUTPUT, 0, 1L, false, List.of());
        return new Submission(work, 1L, 1L, 0D, new OutputContract(List.of(new GenericStack(OUTPUT, 1L)), List.of(), List.of(), List.of()),
                List.of(new SlotStack(0, new GenericStack(AEItemKey.of(Items.WOODEN_AXE), 1L))), false, false, false, 0L);
    }

    private static Settlement emptySettlement() {
        return new Settlement(SESSION, JOB, OWNER.toString(), TARGET.persistentIdentity(), 0L,
                List.of(), List.of(), 0L, List.of(), Optional.empty());
    }

    private static void expectState(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail(message);
    }
}
