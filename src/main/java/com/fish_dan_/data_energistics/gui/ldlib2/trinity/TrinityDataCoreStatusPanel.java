package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Creates the LDLib2 status surface backed by the existing synchronized Trinity menu state.
 */
final class TrinityDataCoreStatusPanel {

    static final String PANEL_ID = "trinity_data_core_status";
    static final String ONLINE_ID = "trinity_status_online";
    static final String MAIN_STRUCTURE_ID = "trinity_status_main_structure";
    static final String FAILURE_ID = "trinity_status_failure";
    static final String CPU_PARTITIONS_ID = "trinity_status_cpu_partitions";
    static final String STORAGE_AMOUNT_ID = "trinity_status_storage_amount";
    static final String CRAFTING_ID = "trinity_status_crafting";

    private static final int LABEL_COLOR = 0x080C1B;
    private static final int VALUE_COLOR = 0x9CD3FF;
    private static final int SUCCESS_COLOR = 0x62D96B;
    private static final int WARNING_COLOR = 0xFFB347;
    private static final int ERROR_COLOR = 0xFF6B6B;
    private static final int BUSY_COLOR = 0xFFE066;
    private static final int PANEL_X = 14;
    private static final int PANEL_Y = 19;
    private static final int LEFT_X = 4;
    private static final int RIGHT_X = 118;
    private static final int LINE_HEIGHT = 10;
    private static final int LINE_WIDTH = 105;
    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1024L);
    private static final String[] COMPACT_UNITS = { "", "K", "M", "G", "T", "P", "E" };

    private TrinityDataCoreStatusPanel() {}

    static UIElement create(TrinityDataCoreMenu menu) {
        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(PANEL_X)
                .top(PANEL_Y)
                .width(228)
                .height(100));

        panel.addChildren(
                line(ONLINE_ID, () -> onlineLine(menu), LEFT_X, 4),
                line(MAIN_STRUCTURE_ID, () -> mainStructureLine(menu), LEFT_X, 4 + LINE_HEIGHT),
                line("trinity_status_main_blocks", () -> mainBlockLine(menu), LEFT_X, 4 + LINE_HEIGHT * 2),
                line("trinity_status_cpu_structure", () -> cpuStructureLine(menu), LEFT_X, 4 + LINE_HEIGHT * 3),
                line("trinity_status_crafting_structure", () -> craftingStructureLine(menu), LEFT_X, 4 + LINE_HEIGHT * 4),
                failureLine(menu),
                line("trinity_status_busy_cpus", () -> busyCpuLine(menu), RIGHT_X, 2),
                line(CPU_PARTITIONS_ID, () -> cpuPartitionLine(menu), RIGHT_X, 2 + LINE_HEIGHT),
                line("trinity_status_cpu_storage", () -> cpuStorageLine(menu), RIGHT_X, 2 + LINE_HEIGHT * 2),
                line("trinity_status_cpu_coprocessors", () -> cpuCoprocessorLine(menu), RIGHT_X, 2 + LINE_HEIGHT * 3),
                line("trinity_status_storage_types", () -> storageTypeLine(menu), RIGHT_X, 49),
                line(STORAGE_AMOUNT_ID, () -> storageAmountLine(menu), RIGHT_X, 59),
                craftingLabel(menu));
        return panel;
    }

    static Component onlineLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.status_label",
                Component.translatable(menu.online ?
                        "screen.data_energistics.trinity_data_core.status_online" :
                        "screen.data_energistics.trinity_data_core.status_offline"),
                statusColor(menu.online));
    }

    static Component mainStructureLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.main_structure_label",
                formedText(menu.structureFormed),
                statusColor(menu.structureFormed));
    }

    static Component cpuPartitionLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_partitions_label",
                Component.literal(menu.busyCpuPartitionCount + "/" + menu.cpuPartitionCount),
                menu.busyCpuPartitionCount > 0 ? BUSY_COLOR : VALUE_COLOR);
    }

    static Component storageAmountLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.storage_amount_label",
                Component.literal(formatCapacityPair(menu.storedAmountText, menu.storedAmountCapacityText)),
                VALUE_COLOR);
    }

    static Component craftingLine(TrinityDataCoreMenu menu) {
        int color = craftingUnavailable(menu) ? ERROR_COLOR : menu.hasCraftingTarget() ? BUSY_COLOR : SUCCESS_COLOR;
        return keyValue(
                "screen.data_energistics.trinity_data_core.molecular_label",
                molecularStatus(menu),
                color);
    }

    static String compactNumber(String value) {
        if (value.isBlank()) {
            return "0";
        }
        BigInteger amount = new BigInteger(value.trim());
        if (amount.signum() == 0) {
            return "0";
        }

        BigInteger absoluteAmount = amount.abs();
        BigInteger divisor = BigInteger.ONE;
        int unitIndex = 0;
        while (unitIndex < COMPACT_UNITS.length - 1 &&
                absoluteAmount.compareTo(divisor.multiply(UNIT_BASE)) >= 0) {
            divisor = divisor.multiply(UNIT_BASE);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return amount.toString();
        }

        BigInteger whole = absoluteAmount.divide(divisor);
        BigInteger fraction = absoluteAmount.remainder(divisor).multiply(BigInteger.TEN).divide(divisor);
        String sign = amount.signum() < 0 ? "-" : "";
        if (whole.compareTo(BigInteger.TEN) >= 0 || fraction.signum() == 0) {
            return sign + whole + COMPACT_UNITS[unitIndex];
        }
        return sign + whole + "." + fraction + COMPACT_UNITS[unitIndex];
    }

    private static Label line(String id, Supplier<Component> text, int left, int top) {
        Label label = new Label();
        label.setId(id);
        label.bindDataSource(SupplierDataSource.of(text));
        label.textStyle(style -> style
                .adaptiveHeight(false)
                .adaptiveWidth(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .textColor(0xFFFFFFFF)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(LINE_WIDTH)
                .height(9));
        return label;
    }

    private static Label failureLine(TrinityDataCoreMenu menu) {
        Label label = line(FAILURE_ID, () -> failureLineText(menu), LEFT_X, 4 + LINE_HEIGHT * 5);
        label.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> tooltip = failureTooltip(menu);
            if (!tooltip.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(tooltip, null, null, null);
            }
        });
        return label;
    }

    private static Label craftingLabel(TrinityDataCoreMenu menu) {
        Label label = line(CRAFTING_ID, () -> craftingLine(menu), RIGHT_X, 79);
        label.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            GenericStack target = menu.getCraftingTarget();
            if (target == null || craftingUnavailable(menu)) {
                return;
            }
            event.hoverTooltips = new HoverTooltips(List.of(
                    target.what().getDisplayName(),
                    Component.translatable("tooltip.data_energistics.amount")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(target.what().formatAmount(target.amount(), AmountFormat.FULL))
                                    .withStyle(ChatFormatting.GRAY)),
                    Component.translatable(
                            "screen.data_energistics.trinity_data_core.busy_cpus",
                            menu.busyCraftingCpuCount).withStyle(ChatFormatting.GRAY),
                    Component.translatable(
                            "screen.data_energistics.trinity_data_core.crafting_pattern_capacity",
                            menu.craftingPatternCapacity).withStyle(ChatFormatting.GRAY)),
                    null, null, null);
        });
        return label;
    }

    private static Component mainBlockLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.matched_blocks_label",
                Component.literal(compactNumber(Integer.toString(menu.matchedBlockCount))),
                VALUE_COLOR);
    }

    private static Component cpuStructureLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_structure_label",
                structureCountStatus(menu.cpuStructureFormed, menu.cpuStructureMatchedBlockCount),
                statusColor(menu.cpuStructureFormed));
    }

    private static Component craftingStructureLine(TrinityDataCoreMenu menu) {
        Component status = menu.craftingStructureFormed ?
                Component.literal(formatCapacityPair(
                        Integer.toString(menu.craftingPatternCoreCount),
                        Integer.toString(menu.craftingPatternCapacity))) :
                formedText(false);
        return keyValue(
                "screen.data_energistics.trinity_data_core.crafting_structure_label",
                status,
                statusColor(menu.craftingStructureFormed && menu.craftingPatternCapacity > 0));
    }

    private static Component failureLineText(TrinityDataCoreMenu menu) {
        boolean failed = hasAnyFailure(menu);
        return keyValue(
                "screen.data_energistics.trinity_data_core.last_failure_label",
                failureSummary(menu),
                failed ? ERROR_COLOR : SUCCESS_COLOR);
    }

    private static Component busyCpuLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_label",
                Component.literal(Integer.toString(menu.busyCraftingCpuCount)),
                menu.busyCraftingCpuCount > 0 ? BUSY_COLOR : VALUE_COLOR);
    }

    private static Component cpuStorageLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_storage_label",
                Component.literal(compactNumber(Long.toString(menu.cpuStorageBytes))),
                VALUE_COLOR);
    }

    private static Component cpuCoprocessorLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.cpu_coprocessors_label",
                Component.literal(compactNumber(Integer.toString(menu.cpuCoProcessors))),
                VALUE_COLOR);
    }

    private static Component storageTypeLine(TrinityDataCoreMenu menu) {
        return keyValue(
                "screen.data_energistics.trinity_data_core.storage_types_label",
                Component.literal(formatCapacityPair(
                        Integer.toString(menu.storedTypeCount),
                        menu.storedTypeCapacityText)),
                VALUE_COLOR);
    }

    private static Component molecularStatus(TrinityDataCoreMenu menu) {
        if (craftingUnavailable(menu)) {
            return Component.translatable("screen.data_energistics.trinity_data_core.molecular_unavailable");
        }
        GenericStack target = menu.getCraftingTarget();
        return target == null ?
                Component.translatable("screen.data_energistics.trinity_data_core.molecular_idle") :
                target.what().getDisplayName();
    }

    private static Component structureCountStatus(boolean formed, int matchedBlocks) {
        return formed ?
                Component.literal(compactNumber(Integer.toString(matchedBlocks))) :
                formedText(false);
    }

    private static Component formedText(boolean formed) {
        return Component.translatable(formed ?
                "screen.data_energistics.trinity_data_core.formed.yes" :
                "screen.data_energistics.trinity_data_core.formed.no");
    }

    private static Component failureSummary(TrinityDataCoreMenu menu) {
        if (hasFailure(menu)) {
            return MultiBlockFailureText.describe(menu.lastFailureReason);
        }
        if (hasCpuFailure(menu)) {
            return MultiBlockFailureText.describe(menu.cpuLastFailureReason);
        }
        if (hasCraftingFailure(menu)) {
            return MultiBlockFailureText.describe(menu.craftingLastFailureReason);
        }
        return Component.translatable("screen.data_energistics.trinity_data_core.no_failure");
    }

    private static List<Component> failureTooltip(TrinityDataCoreMenu menu) {
        List<Component> tooltip = new ArrayList<>();
        addFailure(
                tooltip,
                menu.lastFailureReason,
                menu.lastFailurePosition,
                "screen.data_energistics.trinity_data_core.last_failure",
                "screen.data_energistics.trinity_data_core.failure_position");
        addFailure(
                tooltip,
                menu.cpuLastFailureReason,
                menu.cpuLastFailurePosition,
                "screen.data_energistics.trinity_data_core.cpu_failure",
                "screen.data_energistics.trinity_data_core.cpu_failure_position");
        addFailure(
                tooltip,
                menu.craftingLastFailureReason,
                menu.craftingLastFailurePosition,
                "screen.data_energistics.trinity_data_core.crafting_failure",
                "screen.data_energistics.trinity_data_core.crafting_failure_position");
        return tooltip;
    }

    private static void addFailure(List<Component> tooltip,
                                   String reason,
                                   String position,
                                   String reasonKey,
                                   String positionKey) {
        if (reason.isBlank()) {
            return;
        }
        tooltip.add(Component.translatable(reasonKey, MultiBlockFailureText.describe(reason)));
        if (!position.isBlank()) {
            tooltip.add(Component.translatable(positionKey, position).withStyle(ChatFormatting.GRAY));
        }
    }

    private static Component keyValue(String labelKey, Component value, int valueColor) {
        return Component.empty()
                .append(Component.translatable(labelKey).withStyle(style -> style.withColor(LABEL_COLOR)))
                .append(value.copy().withStyle(style -> style.withColor(valueColor)));
    }

    private static String formatCapacityPair(String current, String capacity) {
        return compactCapacityNumber(current) + "/" + compactCapacityNumber(capacity);
    }

    private static String compactCapacityNumber(String value) {
        if (TrinityDataCoreMenuHost.UNLIMITED_STORAGE_CAPACITY.equals(value)) {
            return value;
        }
        return compactNumber(value);
    }

    private static int statusColor(boolean ok) {
        return ok ? SUCCESS_COLOR : WARNING_COLOR;
    }

    private static boolean hasFailure(TrinityDataCoreMenu menu) {
        return !menu.lastFailureReason.isBlank();
    }

    private static boolean hasCpuFailure(TrinityDataCoreMenu menu) {
        return !menu.cpuLastFailureReason.isBlank();
    }

    private static boolean hasCraftingFailure(TrinityDataCoreMenu menu) {
        return !menu.craftingLastFailureReason.isBlank();
    }

    private static boolean hasAnyFailure(TrinityDataCoreMenu menu) {
        return hasFailure(menu) || hasCpuFailure(menu) || hasCraftingFailure(menu);
    }

    private static boolean craftingUnavailable(TrinityDataCoreMenu menu) {
        return !menu.craftingStructureFormed || menu.craftingPatternCapacity <= 0;
    }
}
