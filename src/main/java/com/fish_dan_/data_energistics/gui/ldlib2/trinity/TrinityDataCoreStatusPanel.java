package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus.StructureStatus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Creates the separated LDLib2 host-summary surfaces for the Trinity Data Core UI.
 */
final class TrinityDataCoreStatusPanel {

    static final String ONLINE_ID = "trinity_status_online";
    static final String MAIN_STRUCTURE_ID = "trinity_status_main_structure";
    static final String FAILURE_ID = "trinity_status_failure";
    static final String CPU_PARTITIONS_ID = "trinity_status_cpu_partitions";
    static final String CRAFTING_ID = "trinity_status_crafting";

    private static final int LABEL_COLOR = 0x080C1B;
    private static final int VALUE_COLOR = 0x246082;
    private static final int SUCCESS_COLOR = 0x207A35;
    private static final int WARNING_COLOR = 0x9A5A00;
    private static final int ERROR_COLOR = 0xA12424;
    private static final int BUSY_COLOR = 0x8A6300;

    private TrinityDataCoreStatusPanel() {}

    /** Builds both status regions from the LDLib2-synchronized host snapshot. */
    static UIElement create(IDataProvider<TrinityDataCoreHostStatus> statusProvider) {
        if (statusProvider == null) {
            throw new IllegalArgumentException("Trinity host status provider is required");
        }
        requireStatus(statusProvider.getValue());

        UIElement panel = TrinityUiXmlLayouts.loadRoot("data_core_status");
        panel.setAllowHitTest(false);
        bind(TrinityUiXmlLayouts.require(panel, ONLINE_ID, Label.class), statusProvider, TrinityDataCoreStatusPanel::onlineLine);
        bind(TrinityUiXmlLayouts.require(panel, MAIN_STRUCTURE_ID, Label.class), statusProvider,
                TrinityDataCoreStatusPanel::mainStructureLine);
        bind(TrinityUiXmlLayouts.require(panel, "trinity_status_main_blocks", Label.class), statusProvider,
                TrinityDataCoreStatusPanel::mainBlockLine);
        bind(TrinityUiXmlLayouts.require(panel, "trinity_status_cpu_structure", Label.class), statusProvider,
                TrinityDataCoreStatusPanel::cpuStructureLine);
        bind(TrinityUiXmlLayouts.require(panel, "trinity_status_crafting_structure", Label.class), statusProvider,
                TrinityDataCoreStatusPanel::craftingStructureLine);
        Label failure = TrinityUiXmlLayouts.require(panel, FAILURE_ID, Label.class);
        bind(failure, statusProvider, TrinityDataCoreStatusPanel::failureLineText);
        failure.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> tooltip = failureTooltip(requireStatus(statusProvider.getValue()));
            if (!tooltip.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(tooltip, null, null, null);
            }
        });
        bind(TrinityUiXmlLayouts.require(panel, "trinity_status_busy_cpus", Label.class), statusProvider,
                TrinityDataCoreStatusPanel::busyCpuLine);
        bind(TrinityUiXmlLayouts.require(panel, CPU_PARTITIONS_ID, Label.class), statusProvider,
                TrinityDataCoreStatusPanel::cpuPartitionLine);
        bind(TrinityUiXmlLayouts.require(panel, "trinity_status_cpu_storage", Label.class), statusProvider,
                TrinityDataCoreStatusPanel::cpuStorageLine);
        bind(TrinityUiXmlLayouts.require(panel, "trinity_status_cpu_coprocessors", Label.class), statusProvider,
                TrinityDataCoreStatusPanel::cpuCoprocessorLine);
        bind(TrinityUiXmlLayouts.require(panel, CRAFTING_ID, Label.class), statusProvider,
                TrinityDataCoreStatusPanel::craftingLine);
        return panel;
    }

    /** Binds behavior to a declaratively positioned status label. */
    private static void bind(Label label,
                             IDataProvider<TrinityDataCoreHostStatus> statusProvider,
                             Function<TrinityDataCoreHostStatus, Component> text) {
        label.bindDataSource(SupplierDataSource
                .of(() -> requireStatus(statusProvider.getValue()))
                .map(text));
    }

    static Component onlineLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.status_label",
                Component.translatable(status.online() ?
                        "screen.data_energistics.trinity_data_core.status_online" :
                        "screen.data_energistics.trinity_data_core.status_offline"),
                statusColor(status.online()));
    }

    static Component mainStructureLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.main_structure_label",
                formedText(status.mainStructure().formed()),
                statusColor(status.mainStructure().formed()));
    }

    static Component cpuPartitionLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_partitions_label",
                Component.literal(formatCpuPartitions(status)),
                status.busyCpuPartitionCount() > 0 ? BUSY_COLOR : VALUE_COLOR);
    }

    static Component craftingLine(TrinityDataCoreHostStatus status) {
        int color = !status.craftingStructure().formed() ?
                ERROR_COLOR : status.craftingTarget().isPresent() ? BUSY_COLOR : SUCCESS_COLOR;
        return keyValue(
                "screen.data_energistics.trinity_data_core.molecular_label",
                molecularStatus(status),
                color);
    }

    static String formatCpuPartitions(TrinityDataCoreHostStatus status) {
        return status.busyCpuPartitionCount() + "/" + status.cpuPartitionCount();
    }

    static StructureStatus latestFailure(TrinityDataCoreHostStatus status) {
        if (status.mainStructure().hasFailure()) {
            return status.mainStructure();
        }
        if (status.cpuStructure().hasFailure()) {
            return status.cpuStructure();
        }
        if (status.craftingStructure().hasFailure()) {
            return status.craftingStructure();
        }
        return StructureStatus.EMPTY;
    }

    static String compactNumber(String value) {
        return TrinityAmountFormatter.format(value);
    }

    private static Component mainBlockLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.matched_blocks_label",
                Component.literal(compactNumber(Integer.toString(status.mainStructure().matchedBlocks()))),
                VALUE_COLOR);
    }

    private static Component cpuStructureLine(TrinityDataCoreHostStatus status) {
        return structureLine(
                "screen.data_energistics.trinity_data_core.cpu_structure_label",
                status.cpuStructure());
    }

    private static Component craftingStructureLine(TrinityDataCoreHostStatus status) {
        return structureLine(
                "screen.data_energistics.trinity_data_core.crafting_structure_label",
                status.craftingStructure());
    }

    private static Component structureLine(String labelKey, StructureStatus structure) {
        return keyValue(
                labelKey,
                structure.formed() ?
                        Component.literal(compactNumber(Integer.toString(structure.matchedBlocks()))) : formedText(false),
                statusColor(structure.formed()));
    }

    private static Component failureLineText(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.last_failure_label",
                failureSummary(status),
                status.hasAnyFailure() ? ERROR_COLOR : SUCCESS_COLOR);
    }

    private static Component busyCpuLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_label",
                Component.literal(Integer.toString(status.busyCraftingCpuCount())),
                status.busyCraftingCpuCount() > 0 ? BUSY_COLOR : VALUE_COLOR);
    }

    private static Component cpuStorageLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_storage_label",
                Component.literal(compactNumber(Long.toString(status.cpuStorageBytes()))),
                VALUE_COLOR);
    }

    private static Component cpuCoprocessorLine(TrinityDataCoreHostStatus status) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_coprocessors_label",
                Component.literal(compactNumber(Integer.toString(status.cpuCoProcessors()))),
                VALUE_COLOR);
    }

    private static Component molecularStatus(TrinityDataCoreHostStatus status) {
        if (!status.craftingStructure().formed()) {
            return Component.translatable("screen.data_energistics.trinity_data_core.molecular_unavailable");
        }
        return status.craftingTarget().orElseGet(() -> Component.translatable("screen.data_energistics.trinity_data_core.molecular_idle"));
    }

    private static Component formedText(boolean formed) {
        return Component.translatable(formed ?
                "screen.data_energistics.trinity_data_core.formed.yes" :
                "screen.data_energistics.trinity_data_core.formed.no");
    }

    private static Component failureSummary(TrinityDataCoreHostStatus status) {
        StructureStatus failure = latestFailure(status);
        return failure.hasFailure() ?
                MultiBlockFailureText.describeTrinityDataCore(failure.failureReason()) :
                Component.translatable("screen.data_energistics.trinity_data_core.no_failure");
    }

    private static List<Component> failureTooltip(TrinityDataCoreHostStatus status) {
        List<Component> tooltip = new ArrayList<>();
        addFailure(
                tooltip,
                status.mainStructure(),
                "screen.data_energistics.trinity_data_core.last_failure",
                "screen.data_energistics.trinity_data_core.failure_position");
        addFailure(
                tooltip,
                status.cpuStructure(),
                "screen.data_energistics.trinity_data_core.cpu_failure",
                "screen.data_energistics.trinity_data_core.cpu_failure_position");
        addFailure(
                tooltip,
                status.craftingStructure(),
                "screen.data_energistics.trinity_data_core.crafting_failure",
                "screen.data_energistics.trinity_data_core.crafting_failure_position");
        return tooltip;
    }

    private static void addFailure(List<Component> tooltip,
                                   StructureStatus structure,
                                   String reasonKey,
                                   String positionKey) {
        if (!structure.hasFailure()) {
            return;
        }
        tooltip.add(Component.translatable(
                reasonKey,
                MultiBlockFailureText.describeTrinityDataCore(structure.failureReason())));
        if (!structure.failurePosition().isBlank()) {
            tooltip.add(Component.translatable(positionKey, structure.failurePosition()).withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component keyValue(String labelKey, Component value, int valueColor) {
        return Component.empty()
                .append(Component.translatable(labelKey).withStyle(style -> style.withColor(LABEL_COLOR)))
                .append(value.copy().withStyle(style -> style.withColor(valueColor)));
    }

    private static int statusColor(boolean ok) {
        return ok ? SUCCESS_COLOR : WARNING_COLOR;
    }

    private static TrinityDataCoreHostStatus requireStatus(TrinityDataCoreHostStatus status) {
        if (status == null) {
            throw new IllegalStateException("Trinity host status provider returned null");
        }
        return status;
    }
}
