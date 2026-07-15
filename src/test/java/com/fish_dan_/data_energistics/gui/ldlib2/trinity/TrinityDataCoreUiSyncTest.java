package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuStatus;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

public final class TrinityDataCoreUiSyncTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void firstSnapshotPublishesImmediately() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus first = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L));

        assertSame(first, provider.select(first, 100L));
    }

    @Test
    void progressSnapshotRemainsPublishedUntilTheTwentiethTick() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus first = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.1F, 10L));
        TrinityCpuListStatus tickNineteen = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.5F, 20L));
        TrinityCpuListStatus tickTwenty = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.75F, 30L));

        assertSame(first, provider.select(first, 100L));
        assertSame(first, provider.select(tickNineteen, 119L));
        assertSame(tickTwenty, provider.select(tickTwenty, 120L));
    }

    @Test
    void cpuAdditionsAndRemovalsPublishImmediately() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuStatus cpuZero = cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L);
        TrinityCpuStatus cpuOne = cpu(1, 4_096L, 1, "CPU 1", CpuSelectionMode.MACHINE_ONLY, 0.0F, 0L);
        TrinityCpuListStatus first = status(cpuZero);
        TrinityCpuListStatus added = status(cpuZero, cpuOne);
        TrinityCpuListStatus removed = status(cpuOne);

        assertSame(first, provider.select(first, 100L));
        assertSame(added, provider.select(added, 101L));
        assertSame(removed, provider.select(removed, 102L));
    }

    @Test
    void storageChangePublishesImmediately() {
        assertTopologyChangePublishesImmediately(
                cpu(0, 16_384L, 2, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L));
    }

    @Test
    void coProcessorChangePublishesImmediately() {
        assertTopologyChangePublishesImmediately(
                cpu(0, 8_192L, 3, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L));
    }

    @Test
    void nameChangePublishesImmediately() {
        assertTopologyChangePublishesImmediately(
                cpu(0, 8_192L, 2, "Renamed CPU", CpuSelectionMode.ANY, 0.0F, 0L));
    }

    @Test
    void selectionModeChangePublishesImmediately() {
        assertTopologyChangePublishesImmediately(
                cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.PLAYER_ONLY, 0.0F, 0L));
    }

    @Test
    void gameTimeRollbackPublishesImmediately() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus first = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.1F, 10L));
        TrinityCpuListStatus afterRollback = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.2F, 20L));

        assertSame(first, provider.select(first, 100L));
        assertSame(afterRollback, provider.select(afterRollback, 99L));
    }

    @Test
    void taskStartPublishesImmediately() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus idle = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L));
        TrinityCpuListStatus busy = status(cpu(
                0,
                8_192L,
                2,
                "CPU 0",
                CpuSelectionMode.ANY,
                job(64L),
                0.0F,
                1L));

        assertSame(idle, provider.select(idle, 100L));
        assertSame(busy, provider.select(busy, 101L));
    }

    @Test
    void taskCompletionPublishesImmediately() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus busy = status(cpu(
                0,
                8_192L,
                2,
                "CPU 0",
                CpuSelectionMode.ANY,
                job(64L),
                0.9F,
                20L));
        TrinityCpuListStatus idle = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L));

        assertSame(busy, provider.select(busy, 100L));
        assertSame(idle, provider.select(idle, 101L));
    }

    @Test
    void taskTargetChangePublishesImmediately() {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus first = status(cpu(
                0,
                8_192L,
                2,
                "CPU 0",
                CpuSelectionMode.ANY,
                job(64L),
                0.1F,
                10L));
        TrinityCpuListStatus changed = status(cpu(
                0,
                8_192L,
                2,
                "CPU 0",
                CpuSelectionMode.ANY,
                job(32L),
                0.0F,
                1L));

        assertSame(first, provider.select(first, 100L));
        assertSame(changed, provider.select(changed, 101L));
    }

    private static void assertTopologyChangePublishesImmediately(TrinityCpuStatus changedCpu) {
        TrinityDataCoreUiSync.CpuStatusSnapshotProvider provider = snapshotProvider();
        TrinityCpuListStatus first = status(cpu(0, 8_192L, 2, "CPU 0", CpuSelectionMode.ANY, 0.0F, 0L));
        TrinityCpuListStatus changed = status(changedCpu);

        assertSame(first, provider.select(first, 100L));
        assertSame(changed, provider.select(changed, 101L));
    }

    private static TrinityDataCoreUiSync.CpuStatusSnapshotProvider snapshotProvider() {
        return new TrinityDataCoreUiSync.CpuStatusSnapshotProvider();
    }

    private static TrinityCpuListStatus status(TrinityCpuStatus... cpus) {
        return new TrinityCpuListStatus(List.of(cpus));
    }

    private static TrinityCpuStatus cpu(int number,
                                        long storage,
                                        int coProcessors,
                                        String name,
                                        CpuSelectionMode mode,
                                        float progress,
                                        long elapsedTimeNanos) {
        return cpu(number, storage, coProcessors, name, mode, null, progress, elapsedTimeNanos);
    }

    private static TrinityCpuStatus cpu(int number,
                                        long storage,
                                        int coProcessors,
                                        String name,
                                        CpuSelectionMode mode,
                                        GenericStack currentJob,
                                        float progress,
                                        long elapsedTimeNanos) {
        return new TrinityCpuStatus(
                number,
                storage,
                coProcessors,
                Component.literal(name),
                mode,
                currentJob,
                progress,
                elapsedTimeNanos);
    }

    private static GenericStack job(long amount) {
        return new GenericStack(AEItemKey.of(Items.DIAMOND), amount);
    }
}
