package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import appeng.api.stacks.GenericStack;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Defines one hosted structure's exact menu-field boundary and player-facing status rows.
 *
 * @param key             hosted lifecycle identity
 * @param structureKey    fixed preview structure identity
 * @param title           window title
 * @param statusLines     dynamic rows that read only this structure's documented fields
 * @param refundAvailable whether the crafting refund action is currently meaningful
 */
record TrinityDataCoreStructureDescriptor(HostUiKey key,
                                          String structureKey,
                                          Component title,
                                          List<StatusLine> statusLines,
                                          BooleanSupplier refundAvailable) {

    TrinityDataCoreStructureDescriptor {
        if (key == null || structureKey == null || structureKey.isBlank() || title == null ||
                statusLines == null || refundAvailable == null) {
            throw new IllegalArgumentException("Trinity structure descriptor arguments cannot be null or blank");
        }
        title = title.copy();
        statusLines = List.copyOf(statusLines);
        if (statusLines.isEmpty()) {
            throw new IllegalArgumentException("Trinity structure descriptor requires status rows");
        }
    }

    /**
     * Returns a detached title for one fresh window tree.
     */
    @Override
    public Component title() {
        return this.title.copy();
    }

    /**
     * Evaluates the complete status surface for direct boundary tests.
     */
    List<Component> statusSnapshot() {
        return this.statusLines.stream().map(StatusLine::text).toList();
    }

    static TrinityDataCoreStructureDescriptor main(TrinityDataCoreMenu menu) {
        requireMenu(menu);
        return new TrinityDataCoreStructureDescriptor(
                TrinityDataCoreHostUiKeys.MAIN,
                "main",
                Component.translatable("screen.data_energistics.trinity_data_core.auto_build.structure.main"),
                List.of(
                        line("main_online", () -> Component.translatable(
                                menu.online ?
                                        "screen.data_energistics.trinity_data_core.status_online" :
                                        "screen.data_energistics.trinity_data_core.status_offline")),
                        line("main_formed", () -> formed(menu.structureFormed)),
                        line("main_matched", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.matched_blocks",
                                menu.matchedBlockCount)),
                        line("main_failure", () -> failure(
                                "screen.data_energistics.trinity_data_core.last_failure",
                                "screen.data_energistics.trinity_data_core.failure_position",
                                menu.lastFailureReason,
                                menu.lastFailurePosition)),
                        line("main_storage_types", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.storage_types",
                                menu.storedTypeCount + "/" + menu.storedTypeCapacityText)),
                        line("main_storage_amount", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.storage_amount",
                                menu.storedAmountText + "/" + menu.storedAmountCapacityText))),
                () -> false);
    }

    static TrinityDataCoreStructureDescriptor cpu(TrinityDataCoreMenu menu) {
        requireMenu(menu);
        return new TrinityDataCoreStructureDescriptor(
                TrinityDataCoreHostUiKeys.CPU,
                "cpu",
                Component.translatable("screen.data_energistics.trinity_data_core.auto_build.structure.cpu"),
                List.of(
                        line("cpu_formed", () -> formed(menu.cpuStructureFormed)),
                        line("cpu_matched", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.matched_blocks",
                                menu.cpuStructureMatchedBlockCount)),
                        line("cpu_failure", () -> failure(
                                "screen.data_energistics.trinity_data_core.cpu_failure",
                                "screen.data_energistics.trinity_data_core.cpu_failure_position",
                                menu.cpuLastFailureReason,
                                menu.cpuLastFailurePosition)),
                        line("cpu_partitions", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.cpu_partitions",
                                menu.busyCpuPartitionCount,
                                menu.cpuPartitionCount)),
                        line("cpu_storage", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.cpu_storage",
                                menu.cpuStorageBytes)),
                        line("cpu_coprocessors", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.cpu_coprocessors",
                                menu.cpuCoProcessors)),
                        line("cpu_network_busy", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.busy_cpus",
                                menu.busyCraftingCpuCount))),
                () -> false);
    }

    static TrinityDataCoreStructureDescriptor crafting(TrinityDataCoreMenu menu) {
        requireMenu(menu);
        return new TrinityDataCoreStructureDescriptor(
                TrinityDataCoreHostUiKeys.CRAFTING,
                "crafting",
                Component.translatable("screen.data_energistics.trinity_data_core.auto_build.structure.crafting"),
                List.of(
                        line("crafting_formed", () -> formed(menu.craftingStructureFormed)),
                        line("crafting_matched", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.matched_blocks",
                                menu.craftingStructureMatchedBlockCount)),
                        line("crafting_failure", () -> failure(
                                "screen.data_energistics.trinity_data_core.crafting_failure",
                                "screen.data_energistics.trinity_data_core.crafting_failure_position",
                                menu.craftingLastFailureReason,
                                menu.craftingLastFailurePosition)),
                        line("crafting_cores", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.crafting_pattern_capacity",
                                menu.craftingPatternCoreCount + "/" + menu.craftingPatternCapacity)),
                        line("crafting_target", () -> craftingTarget(menu)),
                        line("crafting_busy", () -> Component.translatable(
                                "screen.data_energistics.trinity_data_core.busy_cpus",
                                menu.busyCraftingCpuCount)),
                        line("crafting_refundable", () -> Component.empty()
                                .append(Component.translatable(
                                        "button.data_energistics.trinity_pattern_core.refund"))
                                .append(Component.literal(": "))
                                .append(formed(menu.hasRefundablePatternState)))),
                () -> menu.hasRefundablePatternState);
    }

    private static StatusLine line(String id, Supplier<Component> text) {
        return new StatusLine(id, text);
    }

    private static Component formed(boolean formed) {
        return Component.translatable(formed ?
                "screen.data_energistics.trinity_data_core.formed.yes" :
                "screen.data_energistics.trinity_data_core.formed.no");
    }

    private static Component failure(String reasonKey, String positionKey, String reason, String position) {
        Component description = reason.isBlank() ?
                Component.translatable("screen.data_energistics.trinity_data_core.no_failure") :
                MultiBlockFailureText.describe(reason);
        MutableComponent result = Component.translatable(reasonKey, description);
        if (!position.isBlank()) {
            result.append(Component.literal(" ")).append(Component.translatable(positionKey, position));
        }
        return result;
    }

    private static Component craftingTarget(TrinityDataCoreMenu menu) {
        GenericStack target = menu.getCraftingTarget();
        if (target == null) {
            return Component.translatable(
                    "screen.data_energistics.trinity_data_core.crafting_target",
                    Component.translatable("screen.data_energistics.trinity_data_core.molecular_idle"));
        }
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.crafting_target",
                target.what().getDisplayName().copy().append(Component.literal(" x" + target.amount())));
    }

    private static void requireMenu(TrinityDataCoreMenu menu) {
        if (menu == null) {
            throw new IllegalArgumentException("Trinity structure descriptor requires a menu");
        }
    }

    /**
     * One stable status-row identity and its live menu-backed text.
     */
    record StatusLine(String id, Supplier<Component> textSupplier) {

        StatusLine {
            if (id == null || id.isBlank() || textSupplier == null) {
                throw new IllegalArgumentException("Trinity status line arguments cannot be null or blank");
            }
        }

        Component text() {
            Component text = this.textSupplier.get();
            if (text == null) {
                throw new IllegalStateException("Trinity status line returned null: " + this.id);
            }
            return text;
        }
    }
}
