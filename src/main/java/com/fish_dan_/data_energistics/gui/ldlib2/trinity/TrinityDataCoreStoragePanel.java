package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import java.util.function.Function;

/** Composes the synchronized storage summary and native Trinity capacity component. */
final class TrinityDataCoreStoragePanel {

    static final String TYPES_ID = "trinity_storage_types";
    static final String AMOUNT_ID = "trinity_storage_amount";

    private TrinityDataCoreStoragePanel() {}

    /** Creates the complete capacity region from one LDLib2 synchronized provider. */
    static UIElement create(IDataProvider<TrinityDataCoreStorageStatus> statusProvider) {
        if (statusProvider == null) {
            throw new IllegalArgumentException("Trinity storage status provider is required");
        }
        requireStatus(statusProvider.getValue());

        UIElement panel = TrinityUiXmlLayouts.loadRoot("data_core_storage");
        bind(TrinityUiXmlLayouts.require(panel, AMOUNT_ID, Label.class), statusProvider,
                TrinityDataCoreStoragePanel::amountLine);
        bind(TrinityUiXmlLayouts.require(panel, TYPES_ID, Label.class), statusProvider,
                TrinityDataCoreStoragePanel::typesLine);
        panel.addChild(capacityBar(statusProvider));
        return panel;
    }

    static Component typesLine(TrinityDataCoreStorageStatus status) {
        Object value = status.unlimited() ?
                Component.literal(status.typeCount() + "/")
                        .append(Component.translatable("gui.data_energistics.trinity.unlimited")) :
                status.typeCount() + "/" + status.typeCapacity();
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.storage_types",
                value);
    }

    static Component amountLine(TrinityDataCoreStorageStatus status) {
        String amount = TrinityDataCoreStatusPanel.compactNumber(status.totalAmount().toString());
        Object value = status.unlimited() ?
                Component.literal(amount + "/")
                        .append(Component.translatable("gui.data_energistics.trinity.unlimited")) :
                amount + "/" + TrinityDataCoreStatusPanel.compactNumber(status.amountCapacity().toString());
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.storage_amount",
                value);
    }

    private static void bind(Label label,
                             IDataProvider<TrinityDataCoreStorageStatus> statusProvider,
                             Function<TrinityDataCoreStorageStatus, Component> text) {
        label.bindDataSource(SupplierDataSource
                .of(() -> requireStatus(statusProvider.getValue()))
                .map(text));
    }

    private static TrinityStorageCapacityBar capacityBar(
                                                         IDataProvider<TrinityDataCoreStorageStatus> statusProvider) {
        TrinityStorageCapacityBar bar = new TrinityStorageCapacityBar();
        bar.bindDataSource(statusProvider);
        bar.addClass("trinity-storage-capacity-bar");
        return bar;
    }

    private static TrinityDataCoreStorageStatus requireStatus(TrinityDataCoreStorageStatus status) {
        if (status == null) {
            throw new IllegalStateException("Trinity storage status provider returned null");
        }
        return status;
    }
}
