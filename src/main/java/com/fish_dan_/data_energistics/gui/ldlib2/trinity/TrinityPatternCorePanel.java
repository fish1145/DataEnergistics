package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.AeItemSlot;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.menu.TrinityPatternCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;

/** Owns the page-local pattern grid and controls backed by the existing AE2 menu protocol. */
final class TrinityPatternCorePanel extends UIElement {

    private static final int COLUMN_COUNT = 8;
    private static final int SLOT_PITCH = 18;
    private static final int GRID_SIZE = COLUMN_COUNT * SLOT_PITCH;
    private static final int CONTROL_TOP = 160;
    private static final int CONTROL_WIDTH = 16;
    private static final int CONTROL_HEIGHT = 12;
    private static final IGuiTexture EMPTY_PATTERN_TEXTURE = SpriteTexture
            .of("data_energistics:textures/guis/states.png")
            .setSprite(48, 16, 16, 16);

    private final TrinityPatternCoreMenu menu;
    private final Button previousPageButton;
    private final Button nextPageButton;
    private final Button refundAllButton;

    private TrinityPatternCorePanel(TrinityPatternCoreMenu menu) {
        this.menu = menu;
        this.previousPageButton = iconButton(
                TrinityPatternCoreUi.PREVIOUS_PAGE_ID,
                Icons.LEFT_ARROW_NO_BAR,
                "screen.data_energistics.page.previous",
                () -> this.menu.sendSetPage(this.menu.pageIndex - 1));
        this.nextPageButton = iconButton(
                TrinityPatternCoreUi.NEXT_PAGE_ID,
                Icons.RIGHT_ARROW_NO_BAR,
                "screen.data_energistics.page.next",
                () -> this.menu.sendSetPage(this.menu.pageIndex + 1));
        this.refundAllButton = iconButton(
                TrinityPatternCoreUi.REFUND_ALL_ID,
                Icons.REPLAY,
                "button.data_energistics.trinity_pattern_core.refund",
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

        TrinityPatternCorePanel panel = new TrinityPatternCorePanel(menu);
        panel.setId(TrinityPatternCoreUi.PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(176)
                .height(172));
        panel.addChild(title(title));
        panel.addChild(patternGrid(pageSlots, bridge));
        panel.addChild(positionControl(panel.previousPageButton, 16));
        panel.addChild(pageInfo(menu));
        panel.addChild(positionControl(panel.nextPageButton, 124));
        panel.addChild(positionControl(panel.refundAllButton, 144));
        panel.refreshControlState();
        return panel;
    }

    /** Refreshes controls before child ticks so a newly confirmed page becomes interactive immediately. */
    @Override
    public void screenTick() {
        refreshControlState();
        super.screenTick();
    }

    private static Label title(Component title) {
        Label label = new Label();
        label.setText(title);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(5)
                .width(160)
                .height(9));
        return label;
    }

    private static UIElement patternGrid(List<Slot> pageSlots, AeMenuBridge bridge) {
        UIElement grid = new UIElement();
        grid.setId(TrinityPatternCoreUi.PATTERN_GRID_ID);
        grid.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(16)
                .top(16)
                .width(GRID_SIZE)
                .height(GRID_SIZE));

        for (int index = 0; index < pageSlots.size(); index++) {
            AeItemSlot wrapper = bridge.wrap(pageSlots.get(index));
            wrapper.setId(TrinityPatternCoreUi.PATTERN_SLOT_ID_PREFIX + index);
            wrapper.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            wrapper.slotStyle(style -> style
                    .slotOverlay(EMPTY_PATTERN_TEXTURE)
                    .showSlotOverlayOnlyEmpty(true));
            int slotLeft = index % COLUMN_COUNT * SLOT_PITCH;
            int slotTop = index / COLUMN_COUNT * SLOT_PITCH;
            wrapper.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(slotLeft)
                    .top(slotTop));
            grid.addChild(wrapper);
        }
        return grid;
    }

    private static Label pageInfo(TrinityPatternCoreMenu menu) {
        Label label = new Label();
        label.setId(TrinityPatternCoreUi.PAGE_INFO_ID);
        label.bindDataSource(SupplierDataSource.of(() -> Component.translatable(
                "screen.data_energistics.page",
                menu.pageIndex + 1,
                menu.totalPages)));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(36)
                .top(161)
                .width(84)
                .height(10));
        return label;
    }

    private static Button iconButton(String id, IGuiTexture icon, String textKey, Runnable action) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(textKey));
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> action.run());
        button.style(style -> style.tooltips(Component.translatable(textKey)));
        return button;
    }

    private static UIElement positionControl(UIElement element, int left) {
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(CONTROL_TOP)
                .width(CONTROL_WIDTH)
                .height(CONTROL_HEIGHT));
        return element;
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
