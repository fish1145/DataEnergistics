package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import com.fish_dan_.data_energistics.client.widget.Ae2LtTextureToggleButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.AdaptivePatternProviderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class AdaptivePatternProviderScreen extends PatternProviderScreen<AdaptivePatternProviderMenu> {
    private final ToggleButton previousPageButton;
    private final ToggleButton nextPageButton;
    private final Ae2LtTextureToggleButton ae2ltModeButton;
    private final Ae2LtTextureToggleButton ae2ltReturnModeButton;
    private final Ae2LtTextureToggleButton ae2ltWirelessStrategyButton;
    private final Ae2LtTextureToggleButton ae2ltWirelessSpeedButton;
    private final DataExtractorToggleButton filteredImportButton;

    public AdaptivePatternProviderScreen(AdaptivePatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.previousPageButton = new ToggleButton(
                Icon.BACK,
                Icon.BACK,
                Component.translatable("screen.data_energistics.adaptive_pattern_provider.page.previous"),
                Component.translatable("screen.data_energistics.adaptive_pattern_provider.page.previous"),
                this::goPreviousPage
        );
        this.nextPageButton = new ToggleButton(
                Icon.ARROW_RIGHT,
                Icon.ARROW_RIGHT,
                Component.translatable("screen.data_energistics.adaptive_pattern_provider.page.next"),
                Component.translatable("screen.data_energistics.adaptive_pattern_provider.page.next"),
                this::goNextPage
        );
        this.addToLeftToolbar(this.previousPageButton);
        this.addToLeftToolbar(this.nextPageButton);
        this.ae2ltModeButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.MODE,
                ignored -> this.menu.sendToggleAe2LtMode()
        );
        this.ae2ltModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        this.ae2ltModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        this.addToLeftToolbar(this.ae2ltModeButton);
        this.ae2ltReturnModeButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.AUTO_RETURN,
                ignored -> this.menu.sendToggleAe2LtReturnMode()
        );
        this.addToLeftToolbar(this.ae2ltReturnModeButton);
        this.ae2ltWirelessStrategyButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.WIRELESS_STRATEGY,
                ignored -> this.menu.sendToggleAe2LtWirelessDispatchMode()
        );
        this.ae2ltWirelessStrategyButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.wireless_strategy.even")));
        this.ae2ltWirelessStrategyButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.wireless_strategy.single")));
        this.addToLeftToolbar(this.ae2ltWirelessStrategyButton);
        this.ae2ltWirelessSpeedButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.SPEED,
                ignored -> this.menu.sendToggleAe2LtWirelessSpeedMode()
        );
        this.ae2ltWirelessSpeedButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.wireless_speed.fast")));
        this.ae2ltWirelessSpeedButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.wireless_speed.normal")));
        this.addToLeftToolbar(this.ae2ltWirelessSpeedButton);
        this.filteredImportButton = new DataExtractorToggleButton(
                Icon.FILTER_ON_EXTRACT_ENABLED,
                Icon.FILTER_ON_EXTRACT_DISABLED,
                "button.data_energistics.adaptive_pattern_provider.filtered_import",
                "button.data_energistics.adaptive_pattern_provider.filtered_import.enabled",
                "button.data_energistics.adaptive_pattern_provider.filtered_import.disabled",
                this::setFilteredImport
        );
        this.addToLeftToolbar(this.filteredImportButton);
    }

    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.previousPageButton.active = this.menu.pageIndex > 0;
        this.nextPageButton.active = this.menu.pageIndex + 1 < this.menu.totalPages;
        boolean showFilteredImport = this.menu.isAdvancedAeProviderSelected();
        this.filteredImportButton.visible = showFilteredImport;
        this.filteredImportButton.active = showFilteredImport;
        this.filteredImportButton.setState(this.menu.isAdvancedAeFilteredImportEnabled());
        boolean showAe2LtControls = this.menu.isAe2LtOverloadedProviderSelected();
        this.ae2ltModeButton.visible = showAe2LtControls;
        this.ae2ltModeButton.active = showAe2LtControls;
        this.ae2ltModeButton.setState(this.menu.isAe2LtWirelessMode());
        this.ae2ltReturnModeButton.visible = showAe2LtControls;
        this.ae2ltReturnModeButton.active = showAe2LtControls;
        this.ae2ltReturnModeButton.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.return_mode.off")));
        this.ae2ltReturnModeButton.setTooltipAt(1, List.of(Component.translatable("ae2lt.gui.return_mode.auto")));
        this.ae2ltReturnModeButton.setTooltipAt(2, List.of(Component.translatable("ae2lt.gui.return_mode.eject")));
        this.ae2ltReturnModeButton.setStateIndex(this.menu.getAe2LtReturnModeOrdinal());
        this.ae2ltWirelessStrategyButton.visible = showAe2LtControls && this.menu.isAe2LtWirelessMode();
        this.ae2ltWirelessStrategyButton.active = showAe2LtControls && this.menu.isAe2LtWirelessMode();
        this.ae2ltWirelessStrategyButton.setState(this.menu.isAe2LtEvenDistributionMode());
        this.ae2ltWirelessSpeedButton.visible = showAe2LtControls && this.menu.isAe2LtWirelessMode();
        this.ae2ltWirelessSpeedButton.active = showAe2LtControls && this.menu.isAe2LtWirelessMode();
        this.ae2ltWirelessSpeedButton.setState(this.menu.isAe2LtFastSpeedMode());
        this.previousPageButton.visible = true;
        this.nextPageButton.visible = true;
        this.setTextContent("dialog_title", this.menu.getProviderDisplayName());
        this.setTextContent("page_info", Component.translatable(
                "screen.data_energistics.adaptive_pattern_provider.page",
                this.menu.totalPages <= 0 ? 1 : this.menu.pageIndex + 1,
                Math.max(1, this.menu.totalPages)
        ));
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.isActive()
                && slot.getItem().isEmpty()
                && this.menu.getSlotSemantic(slot) == AdaptivePatternProviderMenu.PAGE_PATTERN) {
            Icon.BACKGROUND_ENCODED_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        }
        super.renderSlot(guiGraphics, slot);
    }

    private void goPreviousPage(boolean ignored) {
        this.menu.sendSetPage(this.menu.pageIndex - 1);
    }

    private void goNextPage(boolean ignored) {
        this.menu.sendSetPage(this.menu.pageIndex + 1);
    }

    private void setFilteredImport(boolean enabled) {
        this.filteredImportButton.setState(enabled);
        this.menu.sendSetAdvancedAeFilteredImport(enabled);
    }
}
