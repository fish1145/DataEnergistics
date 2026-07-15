package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.Function;

/** Composes the synchronized storage summary and native Trinity capacity component. */
final class TrinityDataCoreStoragePanel {

    static final String PANEL_ID = "trinity_data_core_storage_status";
    static final String TYPES_ID = "trinity_storage_types";
    static final String AMOUNT_ID = "trinity_storage_amount";
    static final int LEFT = 134;
    static final int TOP = 82;
    static final int WIDTH = 117;
    static final int HEIGHT = 32;

    private static final int TEXT_COLOR = 0xFF080C1B;

    private TrinityDataCoreStoragePanel() {}

    /** Creates the complete capacity region from one LDLib2 synchronized provider. */
    static UIElement create(IDataProvider<TrinityDataCoreStorageStatus> statusProvider) {
        if (statusProvider == null) {
            throw new IllegalArgumentException("Trinity storage status provider is required");
        }
        requireStatus(statusProvider.getValue());

        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(LEFT)
                .top(TOP)
                .width(WIDTH)
                .height(HEIGHT));
        panel.style(style -> style.backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(0xFFA7ADBF),
                new ColorBorderTexture(-1, 0xFFF2F2F2))));
        panel.addChildren(
                label(AMOUNT_ID, statusProvider, TrinityDataCoreStoragePanel::amountLine, 2),
                label(TYPES_ID, statusProvider, TrinityDataCoreStoragePanel::typesLine, 12),
                capacityBar(statusProvider));
        return panel;
    }

    static Component typesLine(TrinityDataCoreStorageStatus status) {
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.storage_types",
                status.typeCount() + "/" + (status.unlimited() ? "MAX" : status.typeCapacity()));
    }

    static Component amountLine(TrinityDataCoreStorageStatus status) {
        String amount = TrinityDataCoreStatusPanel.compactNumber(status.totalAmount().toString());
        String capacity = status.unlimited() ?
                "MAX" : TrinityDataCoreStatusPanel.compactNumber(status.amountCapacity().toString());
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.storage_amount",
                amount + "/" + capacity);
    }

    private static Label label(String id,
                               IDataProvider<TrinityDataCoreStorageStatus> statusProvider,
                               Function<TrinityDataCoreStorageStatus, Component> text,
                               int top) {
        Label label = new Label();
        label.setId(id);
        label.bindDataSource(SupplierDataSource
                .of(() -> requireStatus(statusProvider.getValue()))
                .map(text));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .textColor(TEXT_COLOR)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(2)
                .top(top)
                .width(WIDTH - 4)
                .height(9));
        return label;
    }

    private static TrinityStorageCapacityBar capacityBar(
                                                         IDataProvider<TrinityDataCoreStorageStatus> statusProvider) {
        TrinityStorageCapacityBar bar = new TrinityStorageCapacityBar();
        bar.bindDataSource(statusProvider);
        bar.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(24)
                .width(116)
                .height(6));
        return bar;
    }

    private static TrinityDataCoreStorageStatus requireStatus(TrinityDataCoreStorageStatus status) {
        if (status == null) {
            throw new IllegalStateException("Trinity storage status provider returned null");
        }
        return status;
    }
}
