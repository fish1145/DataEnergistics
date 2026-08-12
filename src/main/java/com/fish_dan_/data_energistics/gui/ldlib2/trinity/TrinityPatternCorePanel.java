package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.menu.TrinityPatternCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import java.util.List;

/** Owns the page-local pattern grid and controls backed by the existing AE2 menu protocol. */
final class TrinityPatternCorePanel extends UIElement {

    private static final IGuiTexture EMPTY_PATTERN_TEXTURE = SpriteTexture
            .of("data_energistics:textures/guis/states.png")
            .setSprite(48, 16, 16, 16);

    private final TrinityPatternCoreMenu menu;
    private final Button previousPageButton;
    private final Button nextPageButton;
    private final Button refundAllButton;

    private TrinityPatternCorePanel(TrinityPatternCoreMenu menu,
                                    Button previousPageButton,
                                    Button nextPageButton,
                                    Button refundAllButton) {
        this.menu = menu;
        this.previousPageButton = previousPageButton;
        this.nextPageButton = nextPageButton;
        this.refundAllButton = refundAllButton;
        configureIconButton(this.previousPageButton, Icons.LEFT_ARROW_NO_BAR, "screen.data_energistics.page.previous",
                () -> this.menu.sendSetPage(this.menu.pageIndex - 1));
        configureIconButton(this.nextPageButton, Icons.RIGHT_ARROW_NO_BAR, "screen.data_energistics.page.next",
                () -> this.menu.sendSetPage(this.menu.pageIndex + 1));
        configureIconButton(this.refundAllButton, Icons.REPLAY, "button.data_energistics.trinity_pattern_core.refund",
                this.menu::sendRefundAll);
        this.refundAllButton.style(style -> style.tooltips(
                Component.translatable("button.data_energistics.trinity_pattern_core.refund"),
                Component.translatable("button.data_energistics.trinity_pattern_core.refund.hint")));
    }

    /** Creates a fresh panel and wraps the exact 64 page slots before any player slot is wrapped. */
    static UIElement create(TrinityPatternCoreMenu menu, AeMenuBridge bridge, Component title) {
        if (menu == null || bridge == null || title == null) {
            throw invalid("menu, bridge, and title must all be present");
        }
        List<Slot> pageSlots = menu.pagePatternSlots();
        if (pageSlots.size() != TrinityPatternCoreMenu.SLOTS_PER_PAGE) {
            throw invalid("pattern page must contain exactly " + TrinityPatternCoreMenu.SLOTS_PER_PAGE + " slots");
        }

        UIElement template = TrinityUiXmlLayouts.loadRoot("pattern_core_panel");
        TrinityPatternCorePanel panel = new TrinityPatternCorePanel(
                menu,
                TrinityUiXmlLayouts.require(template, TrinityPatternCoreUi.PREVIOUS_PAGE_ID, Button.class),
                TrinityUiXmlLayouts.require(template, TrinityPatternCoreUi.NEXT_PAGE_ID, Button.class),
                TrinityUiXmlLayouts.require(template, TrinityPatternCoreUi.REFUND_ALL_ID, Button.class));
        panel.setId(TrinityPatternCoreUi.PANEL_ID);
        panel.addClass("trinity-pattern-core-panel");
        TrinityUiXmlLayouts.moveChildren(template, panel);
        title(TrinityUiXmlLayouts.require(panel, "trinity_pattern_core_title", Label.class), title);
        patternGrid(TrinityUiXmlLayouts.require(panel, TrinityPatternCoreUi.PATTERN_GRID_ID, UIElement.class), pageSlots, bridge);
        pageInfo(TrinityUiXmlLayouts.require(panel, TrinityPatternCoreUi.PAGE_INFO_ID, Label.class), menu);
        panel.refreshControlState();
        return panel;
    }

    /** Refreshes controls before child ticks so a newly confirmed page becomes interactive immediately. */
    @Override
    public void screenTick() {
        refreshControlState();
        super.screenTick();
    }

    private static void title(Label label, Component title) {
        label.setText(title);
    }

    private static void patternGrid(UIElement grid, List<Slot> pageSlots, AeMenuBridge bridge) {
        for (int index = 0; index < pageSlots.size(); index++) {
            AeItemSlot wrapper = bridge.wrap(pageSlots.get(index));
            wrapper.setId(TrinityPatternCoreUi.PATTERN_SLOT_ID_PREFIX + index);
            wrapper.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            wrapper.slotStyle(style -> style
                    .slotOverlay(EMPTY_PATTERN_TEXTURE)
                    .showSlotOverlayOnlyEmpty(true));
            wrapper.addClass("trinity-pattern-core-slot");
            grid.addChild(wrapper);
        }
    }

    private static void pageInfo(Label label, TrinityPatternCoreMenu menu) {
        label.bindDataSource(SupplierDataSource.of(() -> Component.translatable(
                "screen.data_energistics.page",
                menu.pageIndex + 1,
                menu.totalPages)));
    }

    private static void configureIconButton(Button button, IGuiTexture icon, String textKey, Runnable action) {
        button.setText(Component.translatable(textKey));
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> action.run());
        button.style(style -> style.tooltips(Component.translatable(textKey)));
    }

    private void refreshControlState() {
        boolean pageReady = this.menu.isPageSelectionConfirmed();
        boolean multiplePages = this.menu.totalPages > 1;
        this.previousPageButton.setVisible(multiplePages);
        this.nextPageButton.setVisible(multiplePages);
        this.previousPageButton.setActive(pageReady && multiplePages && this.menu.pageIndex > 0);
        this.nextPageButton.setActive(
                pageReady && multiplePages && this.menu.pageIndex + 1 < this.menu.totalPages);
        this.refundAllButton.setActive(pageReady && this.menu.hasRefundableState);
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Trinity Pattern Core LDLib2 panel invariant failed: {}", message);
        return new IllegalStateException(message);
    }
}
