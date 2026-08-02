package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus.StructureStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrinityDataCoreStatusPanelTest {

    @Test
    void usesGregTechMoreMachineDecimalCapacityUnits() {
        assertEquals("0", TrinityDataCoreStatusPanel.compactNumber(""));
        assertEquals("0", TrinityDataCoreStatusPanel.compactNumber("0"));
        assertEquals("999", TrinityDataCoreStatusPanel.compactNumber("999"));
        assertEquals("1K", TrinityDataCoreStatusPanel.compactNumber("1000"));
        assertEquals("1K", TrinityDataCoreStatusPanel.compactNumber("1024"));
        assertEquals("1.5K", TrinityDataCoreStatusPanel.compactNumber("1536"));
        assertEquals("10.2K", TrinityDataCoreStatusPanel.compactNumber("10240"));
        assertEquals("-1.5K", TrinityDataCoreStatusPanel.compactNumber("-1536"));
    }

    @Test
    void rejectsMalformedCapacityInsteadOfGuessing() {
        assertThrows(NumberFormatException.class, () -> TrinityDataCoreStatusPanel.compactNumber("not-a-number"));
    }

    @Test
    void formatsBusyAndTotalCpuPartitionsWithoutReadingAMenu() {
        TrinityDataCoreHostStatus status = status(
                StructureStatus.EMPTY,
                StructureStatus.EMPTY,
                StructureStatus.EMPTY,
                12,
                5);

        assertEquals("5/12", TrinityDataCoreStatusPanel.formatCpuPartitions(status));
    }

    @Test
    void failureSummaryUsesStableMainCpuCraftingPriority() {
        StructureStatus main = new StructureStatus(false, 2, "Main failure", "1, 2, 3");
        StructureStatus cpu = new StructureStatus(false, 3, "CPU failure", "2, 3, 4");
        StructureStatus crafting = new StructureStatus(false, 4, "Crafting failure", "3, 4, 5");

        assertEquals(main, TrinityDataCoreStatusPanel.latestFailure(status(main, cpu, crafting, 0, 0)));
        assertEquals(cpu, TrinityDataCoreStatusPanel.latestFailure(
                status(StructureStatus.EMPTY, cpu, crafting, 0, 0)));
        assertEquals(crafting, TrinityDataCoreStatusPanel.latestFailure(
                status(StructureStatus.EMPTY, StructureStatus.EMPTY, crafting, 0, 0)));
        assertEquals(StructureStatus.EMPTY, TrinityDataCoreStatusPanel.latestFailure(
                status(StructureStatus.EMPTY, StructureStatus.EMPTY, StructureStatus.EMPTY, 0, 0)));
    }

    @Test
    void storageSummaryFormatsFiniteTypeAndAmountCapacities() {
        TrinityDataCoreStorageStatus status = storageStatus(false);

        assertEquals(
                Component.translatable("screen.data_energistics.trinity_data_core.storage_types", "3/8"),
                TrinityDataCoreStoragePanel.typesLine(status));
        assertEquals(
                Component.translatable("screen.data_energistics.trinity_data_core.storage_amount", "1.5K/2K"),
                TrinityDataCoreStoragePanel.amountLine(status));
    }

    @Test
    void storageSummaryUsesMaxForUnlimitedCapacities() {
        TrinityDataCoreStorageStatus status = storageStatus(true);

        assertEquals(
                Component.translatable(
                        "screen.data_energistics.trinity_data_core.storage_types",
                        Component.literal("3/")
                                .append(Component.translatable("gui.data_energistics.trinity.unlimited"))),
                TrinityDataCoreStoragePanel.typesLine(status));
        assertEquals(
                Component.translatable(
                        "screen.data_energistics.trinity_data_core.storage_amount",
                        Component.literal("1.5K/")
                                .append(Component.translatable("gui.data_energistics.trinity.unlimited"))),
                TrinityDataCoreStoragePanel.amountLine(status));
    }

    @Test
    void cpuSelectionDispatchesTheHostIdFromTheLdlib2Snapshot() {
        UUID syncedHostId = UUID.fromString("e7dc4a57-2334-43ba-bb86-a2db9fbab0f0");
        TrinityDataCoreHostStatus status = status(
                Optional.of(syncedHostId),
                StructureStatus.EMPTY,
                StructureStatus.EMPTY,
                StructureStatus.EMPTY,
                0,
                0);
        AtomicReference<UUID> capturedHostId = new AtomicReference<>();
        AtomicInteger capturedCpuNumber = new AtomicInteger(-1);

        assertTrue(TrinityDataCoreHostUi.dispatchCpuSelection(status, 7, (hostId, cpuNumber) -> {
            capturedHostId.set(hostId);
            capturedCpuNumber.set(cpuNumber);
        }));
        assertEquals(syncedHostId, capturedHostId.get());
        assertEquals(7, capturedCpuNumber.get());
        assertFalse(TrinityDataCoreHostUi.dispatchCpuSelection(
                TrinityDataCoreHostStatus.EMPTY,
                7,
                (hostId, cpuNumber) -> {
                    throw new AssertionError("Empty host status must not emit a CPU request");
                }));
    }

    private static TrinityDataCoreHostStatus status(StructureStatus main,
                                                    StructureStatus cpu,
                                                    StructureStatus crafting,
                                                    int partitions,
                                                    int busyPartitions) {
        return status(Optional.empty(), main, cpu, crafting, partitions, busyPartitions);
    }

    private static TrinityDataCoreHostStatus status(Optional<UUID> hostId,
                                                    StructureStatus main,
                                                    StructureStatus cpu,
                                                    StructureStatus crafting,
                                                    int partitions,
                                                    int busyPartitions) {
        return new TrinityDataCoreHostStatus(
                hostId,
                true,
                main,
                cpu,
                crafting,
                1,
                partitions,
                busyPartitions,
                1_024L,
                2,
                Optional.of(Component.literal("Target")));
    }

    private static TrinityDataCoreStorageStatus storageStatus(boolean unlimited) {
        return new TrinityDataCoreStorageStatus(
                3,
                8,
                BigInteger.valueOf(1_024L),
                BigInteger.valueOf(512L),
                BigInteger.ZERO,
                BigInteger.valueOf(2_048L),
                unlimited);
    }
}
