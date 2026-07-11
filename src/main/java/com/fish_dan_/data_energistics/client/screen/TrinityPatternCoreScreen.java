package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.TrinityPatternCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;

/** Local 8 by 8 pattern screen for one physical Trinity P core. */
public final class TrinityPatternCoreScreen extends AEBaseScreen<TrinityPatternCoreMenu> {

    private final ToggleButton previousPageButton;
    private final ToggleButton nextPageButton;
    private final OutputSideActionButton refundAllButton;

    /** Creates pagination and atomic-refund controls around the current 64-slot page. */
    public TrinityPatternCoreScreen(TrinityPatternCoreMenu menu, Inventory playerInventory, Component title,
                                    ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.previousPageButton = new ToggleButton(
                Icon.ARROW_LEFT,
                Icon.ARROW_LEFT,
                Component.translatable("screen.data_energistics.page.previous"),
                Component.translatable("screen.data_energistics.page.previous"),
                ignored -> this.menu.sendSetPage(this.menu.pageIndex - 1));
        this.nextPageButton = new ToggleButton(
                Icon.ARROW_RIGHT,
                Icon.ARROW_RIGHT,
                Component.translatable("screen.data_energistics.page.next"),
                Component.translatable("screen.data_energistics.page.next"),
                ignored -> this.menu.sendSetPage(this.menu.pageIndex + 1));
        this.refundAllButton = new OutputSideActionButton(
                ignored -> this.menu.sendRefundAll(),
                "button.data_energistics.trinity_pattern_core.refund",
                "button.data_energistics.trinity_pattern_core.refund.hint");
        this.refundAllButton.setIconName("TRINITY_REFUND");
        addToLeftToolbar(this.previousPageButton);
        addToLeftToolbar(this.nextPageButton);
        addToLeftToolbar(this.refundAllButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        boolean multiplePages = this.menu.totalPages > 1;
        boolean pageReady = this.menu.isPageSelectionConfirmed();
        this.previousPageButton.visible = multiplePages;
        this.nextPageButton.visible = multiplePages;
        this.previousPageButton.active = pageReady && multiplePages && this.menu.pageIndex > 0;
        this.nextPageButton.active = pageReady && multiplePages && this.menu.pageIndex + 1 < this.menu.totalPages;
        this.refundAllButton.active = this.menu.hasRefundableState;
        setTextContent("page_info", Component.translatable(
                "screen.data_energistics.page",
                this.menu.pageIndex + 1,
                this.menu.totalPages));
    }
}
