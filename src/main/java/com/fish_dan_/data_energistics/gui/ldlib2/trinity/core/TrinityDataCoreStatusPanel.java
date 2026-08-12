package com.fish_dan_.data_energistics.gui.ldlib2.trinity.core;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockFailureText;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreHostStatus.StructureStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** Binds one compact status overview into the Data Core's editor-authored home panel. */
final class TrinityDataCoreStatusPanel {

    static final String PANEL_ID = "trinity_data_core_home_panel";

    private static final String CONTENT_ID = "trinity_data_core_status_overview";
    private static final int WIDTH = 165;
    private static final int HEIGHT = 148;
    private static final int LABEL_LEFT = 6;
    private static final int LABEL_WIDTH = 60;
    private static final int VALUE_LEFT = LABEL_LEFT + LABEL_WIDTH;
    private static final int VALUE_WIDTH = WIDTH - VALUE_LEFT - 6;
    private static final int LINE_HEIGHT = 8;
    private static final float LABEL_FONT_SIZE = 6.5F;
    private static final float HEADING_FONT_SIZE = 7.0F;

    private static final int LABEL_COLOR = 0x413F54;
    private static final int HEADING_COLOR = 0x2F2E43;
    private static final int VALUE_COLOR = 0x246082;
    private static final int SUCCESS_COLOR = 0x207A35;
    private static final int WARNING_COLOR = 0x9A5A00;
    private static final int ERROR_COLOR = 0xA12424;
    private static final int BUSY_COLOR = 0x8A6300;

    private TrinityDataCoreStatusPanel() {}

    /** Adds the status content without replacing the NBT-authored panel background or geometry. */
    static void bindExisting(@NotNull UIElement homePanel,
                             @NotNull IDataProvider<TrinityDataCoreHostStatus> hostStatusProvider,
                             @NotNull IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider,
                             @NotNull IDataProvider<TrinityCpuListStatus> cpuListStatusProvider) {
        UIElement content = new UIElement();
        content.setId(CONTENT_ID);
        content.setOverflowVisible(false);
        content.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(WIDTH)
                .height(HEIGHT));

        row(
                content,
                "online",
                "screen.data_energistics.trinity_data_core.status_label",
                3,
                () -> onlineText(hostStatusProvider.getValue()));

        heading(content, "structure", "screen.data_energistics.trinity_data_core.section.structure", 14);
        row(
                content,
                "main_structure",
                "screen.data_energistics.trinity_data_core.main_structure_label",
                23,
                () -> structureText(hostStatusProvider.getValue().mainStructure()));
        row(
                content,
                "cpu_structure",
                "screen.data_energistics.trinity_data_core.cpu_structure_label",
                32,
                () -> structureText(hostStatusProvider.getValue().cpuStructure()));
        row(
                content,
                "crafting_structure",
                "screen.data_energistics.trinity_data_core.crafting_structure_label",
                41,
                () -> structureText(hostStatusProvider.getValue().craftingStructure()));
        row(
                content,
                "diagnostic",
                "screen.data_energistics.trinity_data_core.diagnostic_label",
                50,
                () -> diagnosticText(hostStatusProvider.getValue()));

        heading(content, "crafting", "screen.data_energistics.trinity_data_core.section.crafting", 62);
        row(
                content,
                "crafting_cpus",
                "screen.data_energistics.trinity_data_core.cpu_label",
                72,
                () -> workingCountText(
                        hostStatusProvider.getValue().busyCraftingCpuCount(),
                        cpuListStatusProvider.getValue().cpus().size()));
        row(
                content,
                "cpu_storage",
                "screen.data_energistics.trinity_data_core.cpu_storage_label",
                81,
                () -> cpuStorageText(hostStatusProvider.getValue().cpuStorageBytes()));
        row(
                content,
                "cpu_coprocessors",
                "screen.data_energistics.trinity_data_core.cpu_coprocessors_label",
                90,
                () -> cpuCoProcessorsText(hostStatusProvider.getValue().cpuCoProcessors()));

        heading(content, "storage", "screen.data_energistics.trinity_data_core.section.storage", 105);
        row(
                content,
                "storage_types",
                "screen.data_energistics.trinity_data_core.storage_types_label",
                114,
                () -> storageTypesText(storageStatusProvider.getValue()));
        row(
                content,
                "storage_capacity",
                "screen.data_energistics.trinity_data_core.storage_capacity_label",
                123,
                () -> storageCapacityText(storageStatusProvider.getValue()));

        homePanel.addChild(content);
    }

    private static void heading(UIElement content, String id, String translationKey, int top) {
        Label heading = new Label();
        heading.setId(CONTENT_ID + "_" + id + "_heading");
        heading.setText(Component.translatable(translationKey));
        heading.setAllowHitTest(false);
        heading.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(HEADING_FONT_SIZE)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.NONE)
                .textColor(HEADING_COLOR)
                .textShadow(false));
        heading.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(LABEL_LEFT)
                .top(top)
                .width(WIDTH - LABEL_LEFT * 2)
                .height(LINE_HEIGHT));
        content.addChild(heading);
    }

    private static void row(UIElement content,
                            String id,
                            String labelTranslationKey,
                            int top,
                            Supplier<Component> valueSupplier) {
        Label label = new Label();
        label.setId(CONTENT_ID + "_" + id + "_label");
        label.setText(Component.translatable(labelTranslationKey));
        label.setAllowHitTest(false);
        configureLine(label, LABEL_LEFT, LABEL_WIDTH, top, TextWrap.NONE, LABEL_COLOR);

        Label value = new Label();
        value.setId(CONTENT_ID + "_" + id + "_value");
        value.bindDataSource(SupplierDataSource.of(valueSupplier));
        configureLine(value, VALUE_LEFT, VALUE_WIDTH, top, TextWrap.HOVER_ROLL, VALUE_COLOR);

        content.addChildren(label, value);
    }

    private static void configureLine(Label label,
                                      int left,
                                      int width,
                                      int top,
                                      TextWrap textWrap,
                                      int color) {
        label.setOverflowVisible(false);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(LABEL_FONT_SIZE)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(textWrap)
                .textColor(color)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(LINE_HEIGHT));
    }

    private static Component onlineText(TrinityDataCoreHostStatus status) {
        return colored(
                Component.translatable(status.online() ?
                        "screen.data_energistics.trinity_data_core.status_online" :
                        "screen.data_energistics.trinity_data_core.status_offline"),
                status.online() ? SUCCESS_COLOR : WARNING_COLOR);
    }

    private static Component structureText(StructureStatus structure) {
        return colored(
                Component.translatable(structure.formed() ?
                        "screen.data_energistics.trinity_data_core.formed.yes" :
                        "screen.data_energistics.trinity_data_core.formed.no"),
                structure.formed() ? SUCCESS_COLOR : ERROR_COLOR);
    }

    private static Component diagnosticText(TrinityDataCoreHostStatus status) {
        if (status.mainStructure().hasFailure()) {
            return diagnosticText(
                    "screen.data_energistics.trinity_data_core.structure.main",
                    status.mainStructure());
        }
        if (status.cpuStructure().hasFailure()) {
            return diagnosticText(
                    "screen.data_energistics.trinity_data_core.structure.cpu",
                    status.cpuStructure());
        }
        if (status.craftingStructure().hasFailure()) {
            return diagnosticText(
                    "screen.data_energistics.trinity_data_core.structure.crafting",
                    status.craftingStructure());
        }
        return colored(
                Component.translatable("screen.data_energistics.trinity_data_core.no_failure"),
                SUCCESS_COLOR);
    }

    private static Component diagnosticText(String structureTranslationKey, StructureStatus structure) {
        Component structureName = Component.translatable(structureTranslationKey);
        Component reason = MultiBlockFailureText.describeTrinityDataCore(structure.failureReason());
        Component diagnostic = structure.failurePosition().isBlank() ?
                Component.translatable(
                        "screen.data_energistics.trinity_data_core.diagnostic_failure",
                        structureName,
                        reason) :
                Component.translatable(
                        "screen.data_energistics.trinity_data_core.diagnostic_failure_at",
                        structureName,
                        reason,
                        structure.failurePosition());
        return colored(diagnostic, ERROR_COLOR);
    }

    private static Component workingCountText(int working, int total) {
        return colored(
                Component.translatable(
                        "screen.data_energistics.trinity_data_core.working_count",
                        compact(working),
                        compact(total)),
                working > 0 ? BUSY_COLOR : VALUE_COLOR);
    }

    private static Component cpuStorageText(long storageBytes) {
        return storageBytes == Long.MAX_VALUE ? unlimitedText() : valueText(compact(storageBytes));
    }

    private static Component cpuCoProcessorsText(int coProcessors) {
        return coProcessors == Integer.MAX_VALUE ? unlimitedText() : valueText(compact(coProcessors));
    }

    private static Component storageTypesText(TrinityDataCoreStorageStatus status) {
        Component capacity = status.unlimited() ?
                Component.translatable("gui.data_energistics.trinity.unlimited") :
                Component.literal(compact(status.typeCapacity()));
        return colored(
                Component.empty()
                        .append(compact(status.typeCount()))
                        .append(" / ")
                        .append(capacity),
                VALUE_COLOR);
    }

    private static Component storageCapacityText(TrinityDataCoreStorageStatus status) {
        return status.unlimited() ? unlimitedText() : valueText(compact(status.amountCapacity().toString()));
    }

    private static Component unlimitedText() {
        return colored(Component.translatable("gui.data_energistics.trinity.unlimited"), VALUE_COLOR);
    }

    private static Component valueText(String value) {
        return colored(Component.literal(value), VALUE_COLOR);
    }

    private static Component colored(Component component, int color) {
        return component.copy().withStyle(style -> style.withColor(color));
    }

    private static String compact(int value) {
        return compact(Integer.toString(value));
    }

    private static String compact(long value) {
        return compact(Long.toString(value));
    }

    private static String compact(String value) {
        return TrinityAmountFormatter.format(value);
    }
}
