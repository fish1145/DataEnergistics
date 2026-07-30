package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.TaffyPosition;

/**
 * Always-mounted direct actions for the two independently sequenced Trinity refunds.
 */
final class TrinityDataCoreRefundPanel extends UIElement {

    static final String PANEL_ID = "trinity_data_core_refund_actions";
    static final String REFUND_PATTERNS_ID = "trinity_data_core_refund_patterns";
    static final String REFUND_RETAINED_ITEMS_ID = "trinity_data_core_refund_retained_items";

    private static final int LEFT = 134;
    private static final int TOP = 115;
    private static final int WIDTH = 117;
    private static final int HEIGHT = 12;
    private static final int GAP = 2;
    private static final int BUTTON_WIDTH = (WIDTH - GAP) / 2;
    private static final long STATIC_ACTION_GENERATION = 1L;
    private static final String REFUND_PATTERNS_SUBJECT_KEY = "message.data_energistics.trinity_data_core.refund.subject.patterns";
    private static final String REFUND_RETAINED_ITEMS_SUBJECT_KEY = "message.data_energistics.trinity_data_core.refund.subject.retained_items";

    private final TrinityDataCoreMenu menu;
    private final Button refundPatternsButton;
    private final Button refundRetainedItemsButton;

    private TrinityDataCoreRefundPanel(TrinityDataCoreMenu menu) {
        this.menu = menu;
        this.refundPatternsButton = actionButton(
                REFUND_PATTERNS_ID,
                "button.data_energistics.trinity_data_core.refund_patterns",
                TrinityDataCoreHostUiKeys.REFUND_PATTERNS,
                menu::sendRefundPatterns,
                0);
        this.refundRetainedItemsButton = actionButton(
                REFUND_RETAINED_ITEMS_ID,
                "button.data_energistics.trinity_data_core.refund_retained_items",
                TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS,
                menu::sendRefundRetainedItems,
                BUTTON_WIDTH + GAP);
    }

    /**
     * Creates two statically mounted buttons without registering artificial hosted child windows.
     */
    static UIElement create(TrinityDataCoreMenu menu) {
        TrinityDataCoreRefundPanel panel = new TrinityDataCoreRefundPanel(menu);
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(LEFT)
                .top(TOP)
                .width(WIDTH)
                .height(HEIGHT));
        panel.addChildren(
                panel.refundPatternsButton,
                panel.refundRetainedItemsButton);
        return panel;
    }

    /**
     * Keeps each static action's pending state and ACK result isolated from the other refund action.
     */
    @Override
    public void screenTick() {
        if (this.menu.getPlayer().level().isClientSide()) {
            refreshRefundAction(
                    TrinityDataCoreHostUiKeys.REFUND_PATTERNS,
                    this.refundPatternsButton,
                    REFUND_PATTERNS_SUBJECT_KEY);
            refreshRefundAction(
                    TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS,
                    this.refundRetainedItemsButton,
                    REFUND_RETAINED_ITEMS_SUBJECT_KEY);
        }
        super.screenTick();
    }

    private void refreshRefundAction(HostUiKey key, Button button, String subjectTranslationKey) {
        button.setActive(!this.menu.isHostedActionPending(key, STATIC_ACTION_GENERATION));
        TrinityHostedActionResult result = this.menu.consumeHostedActionResult(key, STATIC_ACTION_GENERATION);
        if (result != null) {
            this.menu.getPlayer().displayClientMessage(
                    refundResultMessage(subjectTranslationKey, result.status()),
                    true);
        }
    }

    private static Component refundResultMessage(String subjectTranslationKey, TrinityHostedActionStatus status) {
        String resultTranslationKey = switch (status) {
            case COMPLETED -> "message.data_energistics.trinity_data_core.refund.completed";
            case NO_OP -> "message.data_energistics.trinity_data_core.refund.no_op";
            case STALE_STATE -> "message.data_energistics.trinity_data_core.refund.stale_state";
            case DELIVERY_FAILED -> "message.data_energistics.trinity_data_core.refund.delivery_failed";
            case INTERNAL_ERROR -> "message.data_energistics.trinity_data_core.refund.internal_error";
            case REJECTED -> "message.data_energistics.trinity_data_core.refund.rejected";
        };
        return Component.translatable(resultTranslationKey, Component.translatable(subjectTranslationKey));
    }

    private Button actionButton(String id,
                                String translationKey,
                                HostUiKey key,
                                Runnable action,
                                int left) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(translationKey));
        button.addPreIcon(Icons.REPLAY);
        button.setOnClick(event -> {
            action.run();
            button.setActive(!this.menu.isHostedActionPending(key, STATIC_ACTION_GENERATION));
        });
        button.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textWrap(TextWrap.HOVER_ROLL));
        button.setOverflowVisible(false);
        button.style(style -> style.tooltips(Component.translatable(translationKey)));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(0)
                .width(BUTTON_WIDTH)
                .height(HEIGHT));
        return button;
    }
}
