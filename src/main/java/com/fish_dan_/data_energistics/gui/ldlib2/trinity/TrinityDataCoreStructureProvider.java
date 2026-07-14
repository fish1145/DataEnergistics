package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Creates one fresh draggable structure/status window for every accepted hosted OPEN generation.
 */
final class TrinityDataCoreStructureProvider implements HostSubUiProvider {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 232;
    private static final int TITLE_HEIGHT = 18;

    private final TrinityDataCoreStructureDescriptor descriptor;
    private final StructurePreviewUiFactory previewFactory;
    private final BooleanSupplier logicalClient;
    @Nullable
    private final LongConsumer hostedRefundAction;
    @Nullable
    private final LongPredicate hostedRefundPending;

    TrinityDataCoreStructureProvider(TrinityDataCoreStructureDescriptor descriptor,
                                     StructurePreviewUiFactory previewFactory,
                                     BooleanSupplier logicalClient,
                                     @Nullable LongConsumer hostedRefundAction,
                                     @Nullable LongPredicate hostedRefundPending) {
        if (descriptor == null || previewFactory == null || logicalClient == null) {
            throw new IllegalArgumentException("Trinity structure provider arguments cannot be null");
        }
        this.descriptor = descriptor;
        this.previewFactory = previewFactory;
        this.logicalClient = logicalClient;
        this.hostedRefundAction = hostedRefundAction;
        this.hostedRefundPending = hostedRefundPending;
    }

    @Override
    public HostUiKey key() {
        return this.descriptor.key();
    }

    @Override
    public HostSubUi create(HostSubUiContext context) {
        if (context == null || !this.descriptor.key().equals(context.key())) {
            throw new IllegalArgumentException("Trinity structure provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        root.setId(TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey()));
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .width(WIDTH)
                .height(HEIGHT));
        root.style(style -> style.backgroundTexture(Sprites.BORDER));

        UIElement dragHandle = titleBar();
        Button close = closeButton(context);
        root.addChildren(dragHandle, close);

        StructurePreviewUi preview = this.previewFactory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                this.descriptor.structureKey(),
                TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey()) + "_preview",
                this.logicalClient.getAsBoolean());
        preview.panel().layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(20));
        root.addChild(preview.panel());

        TrinityDataCoreStructureStatusPanel status = new TrinityDataCoreStructureStatusPanel(
                this.descriptor,
                context,
                this.hostedRefundAction,
                this.hostedRefundPending);
        status.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(188)
                .top(20));
        root.addChild(status);
        return new HostSubUi(root, dragHandle);
    }

    private UIElement titleBar() {
        UIElement titleBar = new UIElement();
        titleBar.setId(TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey()) + "_drag_handle");
        titleBar.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(2)
                .width(WIDTH - TITLE_HEIGHT - 8)
                .height(TITLE_HEIGHT - 4));
        Label title = new Label();
        title.setText(this.descriptor.title());
        title.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        title.layout(layout -> layout.widthPercent(100).heightPercent(100));
        titleBar.addChild(title);
        return titleBar;
    }

    private Button closeButton(HostSubUiContext context) {
        Button close = new Button();
        close.setId(TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey()) + "_close");
        close.noText();
        close.addPreIcon(Icons.CLOSE);
        close.setOnClick(event -> context.requestClose());
        close.style(style -> style.tooltips(Component.literal("Close")));
        close.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(WIDTH - TITLE_HEIGHT)
                .top(2)
                .width(TITLE_HEIGHT - 4)
                .height(TITLE_HEIGHT - 4));
        return close;
    }
}
