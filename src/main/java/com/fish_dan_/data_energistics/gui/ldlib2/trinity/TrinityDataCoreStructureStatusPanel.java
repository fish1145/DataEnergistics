package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Renders one descriptor's isolated status rows and optional generation-aware crafting action.
 */
final class TrinityDataCoreStructureStatusPanel extends UIElement {

    static final String REFUND_BUTTON_ID = "trinity_crafting_hosted_refund";

    private static final int WIDTH = TrinityHostedWindowChrome.SIDE_WIDTH;
    private static final int HEIGHT = TrinityHostedWindowChrome.CONTENT_HEIGHT;
    private static final int ROW_WIDTH = 80;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int ACTION_HEIGHT = 18;

    private final TrinityDataCoreStructureDescriptor descriptor;
    private final HostSubUiContext context;
    @Nullable
    private final Button refundButton;
    @Nullable
    private final LongConsumer hostedRefundAction;
    private final LongPredicate hostedRefundPending;

    TrinityDataCoreStructureStatusPanel(TrinityDataCoreStructureDescriptor descriptor,
                                        HostSubUiContext context,
                                        @Nullable LongConsumer hostedRefundAction,
                                        @Nullable LongPredicate hostedRefundPending) {
        if (descriptor == null || context == null) {
            throw new IllegalArgumentException("Trinity structure status panel arguments cannot be null");
        }
        boolean crafting = descriptor.key().equals(TrinityDataCoreHostUiKeys.CRAFTING);
        boolean hasRefundAction = hostedRefundAction != null && hostedRefundPending != null;
        if ((crafting && !hasRefundAction) ||
                (!crafting && (hostedRefundAction != null || hostedRefundPending != null))) {
            throw new IllegalArgumentException("Only the crafting structure may expose a hosted refund action");
        }
        this.descriptor = descriptor;
        this.context = context;
        this.hostedRefundAction = hostedRefundAction;
        this.hostedRefundPending = hostedRefundPending == null ? generation -> false : hostedRefundPending;
        this.refundButton = hostedRefundAction == null ?
                null : refundButton();

        setId(TrinityDataCoreStructureProviders.windowId(descriptor.structureKey()) + "_status");
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .width(WIDTH)
                .height(HEIGHT));
        addChild(statusScroller(this.refundButton == null ? HEIGHT : HEIGHT - ACTION_HEIGHT - ROW_GAP));
        if (this.refundButton != null) {
            addChild(this.refundButton);
        }
    }

    @Override
    public void screenTick() {
        if (this.refundButton != null) {
            long generation = this.context.generation();
            this.refundButton.setActive(
                    this.descriptor.refundAvailable().getAsBoolean() &&
                            this.context.canSendServerAction() &&
                            !this.hostedRefundPending.test(generation));
        }
        super.screenTick();
    }

    void requestRefund() {
        if (this.hostedRefundAction == null) {
            throw new IllegalStateException("Structure status panel does not expose a hosted refund action");
        }
        long generation = this.context.generation();
        if (this.descriptor.refundAvailable().getAsBoolean() &&
                this.context.canSendServerAction() &&
                !this.hostedRefundPending.test(generation)) {
            this.hostedRefundAction.accept(generation);
        }
    }

    private ScrollerView statusScroller(int height) {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey()) + "_status_lines");
        scroller.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(WIDTH)
                .height(height));
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .scrollerViewStyle(0));
        scroller.viewPort(viewPort -> viewPort.layout(layout -> layout.paddingAll(2)));
        for (TrinityDataCoreStructureDescriptor.StatusLine line : this.descriptor.statusLines()) {
            Label label = new Label();
            label.setId(TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey()) + "_" + line.id());
            label.bindDataSource(SupplierDataSource.of(line::text));
            label.textStyle(style -> style
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .fontSize(7.5f)
                    .textAlignVertical(Vertical.CENTER)
                    .textWrap(TextWrap.HOVER_ROLL)
                    .textShadow(false));
            label.setOverflowVisible(false);
            label.style(style -> style.backgroundTexture(Sprites.RECT_DARK));
            label.layout(layout -> layout
                    .width(ROW_WIDTH)
                    .height(ROW_HEIGHT)
                    .marginBottom(ROW_GAP)
                    .paddingHorizontal(3));
            scroller.addScrollViewChild(label);
        }
        return scroller;
    }

    private Button refundButton() {
        Button button = new Button();
        button.setId(REFUND_BUTTON_ID);
        button.noText();
        button.addPreIcon(Icons.REPLAY);
        button.setOnClick(event -> requestRefund());
        button.style(style -> style.tooltips(
                Component.translatable("button.data_energistics.trinity_pattern_core.refund"),
                Component.translatable("button.data_energistics.trinity_pattern_core.refund.hint")));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(HEIGHT - ACTION_HEIGHT)
                .width(WIDTH)
                .height(ACTION_HEIGHT));
        return button;
    }
}
