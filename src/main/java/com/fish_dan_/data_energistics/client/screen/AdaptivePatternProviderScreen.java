package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.widget.Ae2LtTextureToggleButton;
import com.fish_dan_.data_energistics.client.widget.AecsPullModeButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.AdaptivePatternProviderMenu;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.Upgrades;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.client.gui.Tooltip;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.core.localization.InGameTooltip;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.ConfigButtonPacket;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdaptivePatternProviderScreen extends AEBaseScreen<AdaptivePatternProviderMenu> {

    private static final int HIDDEN_SLOT_COORD = -9999;
    private static final ResourceLocation EXTRA_PANELS_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/extra_panels.png");
    private static final int EXTRA_PANELS_TEXTURE_SIZE = 128;
    private static final int AE2LTPP_PANEL_U = 0;
    private static final int AE2LTPP_PANEL_V = 0;
    private static final int AE2LTPP_PANEL_WIDTH = 23;
    private static final int AE2LTPP_PANEL_HEIGHT = 30;
    private static final List<Component> AE2LT_RETURN_MODE_TOOLTIP_OFF = List.of(Component.translatable("ae2lt.gui.return_mode.off"));
    private static final List<Component> AE2LT_RETURN_MODE_TOOLTIP_AUTO = List.of(Component.translatable("ae2lt.gui.return_mode.auto"));
    private static final List<Component> AE2LT_RETURN_MODE_TOOLTIP_EJECT = List.of(Component.translatable("ae2lt.gui.return_mode.eject"));
    private static final Optional<VarHandle> SLOT_X_FIELD = resolveField(Slot.class, "x");
    private static final Optional<VarHandle> SLOT_Y_FIELD = resolveField(Slot.class, "y");
    private static final Optional<VarHandle> WIDGET_CONTAINER_WIDGETS_FIELD = resolveField(WidgetContainer.class, "widgets");
    private static final Optional<VarHandle> WIDGET_CONTAINER_COMPOSITE_WIDGETS_FIELD = resolveField(WidgetContainer.class, "compositeWidgets");

    private final ToggleButton previousPageButton;
    private final ToggleButton nextPageButton;
    private final ToggleButton showInPatternAccessTerminalButton;
    private final ServerSettingToggleButton<YesNo> blockingModeButton;
    private final ServerSettingToggleButton<LockCraftingMode> lockCraftingModeButton;
    private final Ae2LtTextureToggleButton ae2ltModeButton;
    private final Ae2LtTextureToggleButton ae2ltReturnModeButton;
    private final Ae2LtTextureToggleButton ae2ltWirelessStrategyButton;
    private final Ae2LtTextureToggleButton ae2ltWirelessSpeedButton;
    private final DataExtractorToggleButton filteredImportButton;
    private final AecsPullModeButton resonatingPullButton;
    private final AdaptivePatternProviderLockReason lockReason;
    private final List<Slot> duplicateUpgradeSlots;
    private final List<Slot> duplicateToolboxSlots;

    public AdaptivePatternProviderScreen(AdaptivePatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.blockingModeButton = new ServerSettingToggleButton<>(Settings.BLOCKING_MODE, YesNo.NO);
        this.addToLeftToolbar(this.blockingModeButton);
        this.lockCraftingModeButton = new ServerSettingToggleButton<>(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
        this.addToLeftToolbar(this.lockCraftingModeButton);
        this.widgets.addOpenPriorityButton();
        this.showInPatternAccessTerminalButton = new ToggleButton(
                Icon.PATTERN_ACCESS_SHOW,
                Icon.PATTERN_ACCESS_HIDE,
                GuiText.PatternAccessTerminal.text(),
                GuiText.PatternAccessTerminalHint.text(),
                btn -> this.selectNextPatternProviderMode());
        this.addToLeftToolbar(this.showInPatternAccessTerminalButton);
        this.lockReason = new AdaptivePatternProviderLockReason(this);
        this.widgets.add("lockReason", this.lockReason);

        var upgradeSlots = splitUniqueSlots(menu.getSlots(SlotSemantics.UPGRADE));
        var toolboxSlots = splitUniqueSlots(menu.getSlots(SlotSemantics.TOOLBOX));
        this.duplicateUpgradeSlots = upgradeSlots.duplicates();
        this.duplicateToolboxSlots = toolboxSlots.duplicates();

        installOrReplaceCompositeWidget("upgrades", new UpgradesPanel(upgradeSlots.unique(), this::getCompatibleUpgrades));
        if (menu.getToolbox().isPresent() && !hasWidget("toolbox")) {
            this.widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }

        this.previousPageButton = new ToggleButton(
                Icon.BACK,
                Icon.BACK,
                Component.translatable("screen.data_energistics.page.previous"),
                Component.translatable("screen.data_energistics.page.previous"),
                this::goPreviousPage);
        this.nextPageButton = new ToggleButton(
                Icon.ARROW_RIGHT,
                Icon.ARROW_RIGHT,
                Component.translatable("screen.data_energistics.page.next"),
                Component.translatable("screen.data_energistics.page.next"),
                this::goNextPage);
        this.addToLeftToolbar(this.previousPageButton);
        this.addToLeftToolbar(this.nextPageButton);

        this.ae2ltModeButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.MODE,
                ignored -> this.menu.sendToggleAe2LtMode());
        this.ae2ltModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        this.ae2ltModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        this.addToLeftToolbar(this.ae2ltModeButton);

        this.ae2ltReturnModeButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.AUTO_RETURN,
                ignored -> this.menu.sendToggleAe2LtReturnMode());
        this.addToLeftToolbar(this.ae2ltReturnModeButton);

        this.ae2ltWirelessStrategyButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.WIRELESS_STRATEGY,
                ignored -> this.menu.sendToggleAe2LtWirelessDispatchMode());
        this.ae2ltWirelessStrategyButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.wireless_strategy.even")));
        this.ae2ltWirelessStrategyButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.wireless_strategy.single")));
        this.addToLeftToolbar(this.ae2ltWirelessStrategyButton);

        this.ae2ltWirelessSpeedButton = new Ae2LtTextureToggleButton(
                Ae2LtTextureToggleButton.ButtonType.SPEED,
                ignored -> this.menu.sendToggleAe2LtWirelessSpeedMode());
        this.ae2ltWirelessSpeedButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.wireless_speed.fast")));
        this.ae2ltWirelessSpeedButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.wireless_speed.normal")));
        this.addToLeftToolbar(this.ae2ltWirelessSpeedButton);

        this.filteredImportButton = new DataExtractorToggleButton(
                Icon.FILTER_ON_EXTRACT_ENABLED,
                Icon.FILTER_ON_EXTRACT_DISABLED,
                "button.data_energistics.adaptive_pattern_provider.filtered_import",
                "button.data_energistics.adaptive_pattern_provider.filtered_import.enabled",
                "button.data_energistics.adaptive_pattern_provider.filtered_import.disabled",
                this::setFilteredImport);
        this.addToLeftToolbar(this.filteredImportButton);

        this.resonatingPullButton = new AecsPullModeButton(
                "button.data_energistics.adaptive_pattern_provider.resonating_pull",
                "button.data_energistics.adaptive_pattern_provider.resonating_pull.enabled",
                "button.data_energistics.adaptive_pattern_provider.resonating_pull.disabled",
                this::setResonatingPull);
        this.addToLeftToolbar(this.resonatingPullButton);
    }

    @Override
    protected void init() {
        super.init();
        hideDuplicatedAuxiliarySlots();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.lockReason.setVisible(this.menu.getLockCraftingMode() != LockCraftingMode.NONE);
        this.blockingModeButton.set(this.menu.getBlockingMode());
        this.lockCraftingModeButton.set(this.menu.getLockCraftingMode());
        this.showInPatternAccessTerminalButton.setState(this.menu.getShowInAccessTerminal() == YesNo.YES);

        boolean multiplePages = this.menu.totalPages > 1;
        this.previousPageButton.visible = multiplePages;
        this.nextPageButton.visible = multiplePages;
        this.previousPageButton.active = multiplePages && this.menu.pageIndex > 0;
        this.nextPageButton.active = multiplePages && this.menu.pageIndex + 1 < this.menu.totalPages;

        boolean showFilteredImport = this.menu.isAdvancedAeProviderSelected();
        this.filteredImportButton.visible = showFilteredImport;
        this.filteredImportButton.active = showFilteredImport;
        this.filteredImportButton.setState(this.menu.isAdvancedAeFilteredImportEnabled());

        boolean showResonatingPull = this.menu.isResonatingProviderSelected();
        this.resonatingPullButton.setVisibility(showResonatingPull);
        this.resonatingPullButton.setState(this.menu.isResonatingPullEnabled());

        boolean showAe2LtControls = this.menu.isAe2LtProviderFamilySelected();
        boolean showAe2LtMode = this.menu.isAe2LtModeSwitchVisible();
        boolean showAe2LtWirelessControls = this.menu.isAe2LtWirelessControlsVisible();
        this.ae2ltModeButton.visible = showAe2LtMode;
        this.ae2ltModeButton.active = showAe2LtMode;
        this.ae2ltModeButton.setState(this.menu.isAe2LtWirelessMode());

        this.ae2ltReturnModeButton.visible = showAe2LtControls;
        this.ae2ltReturnModeButton.active = showAe2LtControls;
        this.ae2ltReturnModeButton.setTooltipAt(0, AE2LT_RETURN_MODE_TOOLTIP_OFF);
        this.ae2ltReturnModeButton.setTooltipAt(1, AE2LT_RETURN_MODE_TOOLTIP_AUTO);
        this.ae2ltReturnModeButton.setTooltipAt(2, AE2LT_RETURN_MODE_TOOLTIP_EJECT);
        this.ae2ltReturnModeButton.setStateIndex(this.menu.getAe2LtReturnModeOrdinal());

        this.ae2ltWirelessStrategyButton.visible = showAe2LtWirelessControls;
        this.ae2ltWirelessStrategyButton.active = showAe2LtWirelessControls;
        this.ae2ltWirelessStrategyButton.setState(this.menu.isAe2LtEvenDistributionMode());

        this.ae2ltWirelessSpeedButton.visible = showAe2LtWirelessControls;
        this.ae2ltWirelessSpeedButton.active = showAe2LtWirelessControls;
        this.ae2ltWirelessSpeedButton.setState(this.menu.isAe2LtFastSpeedMode());

        this.setTextContent("dialog_title",
                Component.translatable("block.data_energistics.adaptive_pattern_provider"));
        this.setTextContent("page_info", Component.translatable(
                "screen.data_energistics.page",
                this.menu.totalPages <= 0 ? 1 : this.menu.pageIndex + 1,
                Math.max(1, this.menu.totalPages)));
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        var semantic = this.menu.getSlotSemantic(slot);
        if (slot.isActive() && semantic == AdaptivePatternProviderMenu.AE2LTPP_ADAPTER) {
            guiGraphics.blit(
                    EXTRA_PANELS_TEXTURE,
                    slot.x - 2,
                    slot.y - 6,
                    0,
                    AE2LTPP_PANEL_U,
                    AE2LTPP_PANEL_V,
                    AE2LTPP_PANEL_WIDTH,
                    AE2LTPP_PANEL_HEIGHT,
                    EXTRA_PANELS_TEXTURE_SIZE,
                    EXTRA_PANELS_TEXTURE_SIZE);
        }

        if (slot.isActive() && slot.getItem().isEmpty() && semantic == AdaptivePatternProviderMenu.PAGE_PATTERN) {
            Icon.BACKGROUND_ENCODED_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        } else if (slot.isActive() && (semantic == AdaptivePatternProviderMenu.PROVIDER_INPUT && slot.getItem().isEmpty() || semantic == AdaptivePatternProviderMenu.AE2LTPP_ADAPTER)) {
            String backgroundIcon = semantic == AdaptivePatternProviderMenu.AE2LTPP_ADAPTER ? "AE2LTPP_PROVIDER_COSE_BASE" : "BACKGROUND_BLOCK";
            DataEnergisticsIcon.getBlitter(backgroundIcon)
                    .dest(
                            semantic == AdaptivePatternProviderMenu.AE2LTPP_ADAPTER ? slot.x - 1 : slot.x,
                            semantic == AdaptivePatternProviderMenu.AE2LTPP_ADAPTER ? slot.y - 1 : slot.y)
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

    private void setResonatingPull(boolean enabled) {
        this.resonatingPullButton.setState(enabled);
        this.menu.sendSetResonatingPullEnabled(enabled);
    }

    private void selectNextPatternProviderMode() {
        boolean backwards = this.isHandlingRightClick();
        ServerboundPacket message = new ConfigButtonPacket(Settings.PATTERN_ACCESS_TERMINAL, backwards);
        PacketDistributor.sendToServer(message);
    }

    private List<Component> getCompatibleUpgrades() {
        ArrayList<Component> list = new ArrayList<>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(this.menu.getUpgrades().getUpgradableItem()));
        return list;
    }

    private void hideDuplicatedAuxiliarySlots() {
        hideSlots(this.duplicateUpgradeSlots);
        hideSlots(this.duplicateToolboxSlots);
    }

    private static void hideSlots(List<Slot> slots) {
        for (var slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(false);
                appEngSlot.setSlotEnabled(false);
            }
            setSlotPosition(slot, HIDDEN_SLOT_COORD, HIDDEN_SLOT_COORD);
        }
    }

    @SuppressWarnings("unchecked")
    private void installOrReplaceCompositeWidget(String id, Object widget) {
        Map<String, Object> compositeWidgets = (Map<String, Object>) ReflectionAccess.getField(WIDGET_CONTAINER_COMPOSITE_WIDGETS_FIELD, this.widgets);
        if (compositeWidgets == null) {
            throw new IllegalStateException("Could not replace AE2 composite widget: " + id);
        }
        compositeWidgets.put(id, widget);
    }

    @SuppressWarnings("unchecked")
    private boolean hasWidget(String id) {
        Map<String, AbstractWidget> widgets = (Map<String, AbstractWidget>) ReflectionAccess.getField(WIDGET_CONTAINER_WIDGETS_FIELD, this.widgets);
        Map<String, ?> compositeWidgets = (Map<String, ?>) ReflectionAccess.getField(WIDGET_CONTAINER_COMPOSITE_WIDGETS_FIELD, this.widgets);
        if (widgets == null || compositeWidgets == null) {
            throw new IllegalStateException("Could not inspect AE2 widget container");
        }

        if (widgets.containsKey(id)) {
            return true;
        }
        return compositeWidgets.containsKey(id);
    }

    private static SlotBuckets splitUniqueSlots(List<Slot> slots) {
        Map<String, Slot> uniqueByBackingSlot = new LinkedHashMap<>();
        List<Slot> duplicates = new ArrayList<>();
        for (var slot : slots) {
            String key = System.identityHashCode(slot.container) + ":" + slot.getContainerSlot();
            if (uniqueByBackingSlot.putIfAbsent(key, slot) != null) {
                duplicates.add(slot);
            }
        }
        return new SlotBuckets(List.copyOf(uniqueByBackingSlot.values()), List.copyOf(duplicates));
    }

    private static void setSlotPosition(Slot slot, int x, int y) {
        if (!ReflectionAccess.setField(SLOT_X_FIELD, slot, x) || !ReflectionAccess.setField(SLOT_Y_FIELD, slot, y)) {
            throw new IllegalStateException("Could not reposition duplicate slot");
        }
    }

    private static Optional<VarHandle> resolveField(Class<?> owner, String name) {
        Optional<VarHandle> field = ReflectionAccess.findField(owner, name);
        if (field.isEmpty()) {
            throw new IllegalStateException("Could not resolve field " + owner.getSimpleName() + "." + name);
        }
        return field;
    }

    private record SlotBuckets(List<Slot> unique, List<Slot> duplicates) {}

    private static final class AdaptivePatternProviderLockReason implements ICompositeWidget {

        private final AdaptivePatternProviderScreen screen;
        private boolean visible;
        private int x;
        private int y;

        private AdaptivePatternProviderLockReason(AdaptivePatternProviderScreen screen) {
            this.screen = screen;
        }

        public void setPosition(Point position) {
            this.x = position.getX();
            this.y = position.getY();
        }

        public void setSize(int width, int height) {}

        public Rect2i getBounds() {
            return new Rect2i(this.x, this.y, 126, 16);
        }

        public boolean isVisible() {
            return this.visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
            Icon icon;
            Component lockStatusText;
            if (this.screen.menu.getCraftingLockedReason() == LockCraftingMode.NONE) {
                icon = Icon.UNLOCKED;
                lockStatusText = GuiText.CraftingLockIsUnlocked.text()
                        .setStyle(Style.EMPTY.withColor(Mth.color(0.49019608F, 0.6627451F, 0.8235294F)));
            } else {
                icon = Icon.LOCKED;
                lockStatusText = GuiText.CraftingLockIsLocked.text()
                        .setStyle(Style.EMPTY.withColor(Mth.color(0.75686276F, 0.25882354F, 0.29411766F)));
            }

            icon.getBlitter().dest(this.x, this.y).blit(guiGraphics);
            guiGraphics.drawString(Minecraft.getInstance().font, lockStatusText, this.x + 15, this.y + 5, -1, false);
        }

        public @Nullable Tooltip getTooltip(int mouseX, int mouseY) {
            MutableComponent tooltip = switch (this.screen.menu.getCraftingLockedReason()) {
                case NONE -> null;
                case LOCK_UNTIL_PULSE -> InGameTooltip.CraftingLockedUntilPulse.text();
                case LOCK_WHILE_HIGH -> InGameTooltip.CraftingLockedByRedstoneSignal.text();
                case LOCK_WHILE_LOW -> InGameTooltip.CraftingLockedByLackOfRedstoneSignal.text();
                case LOCK_UNTIL_RESULT -> {
                    GenericStack stack = this.screen.menu.getUnlockStack();
                    Component stackName;
                    Component stackAmount;
                    if (stack != null) {
                        stackName = AEKeyRendering.getDisplayName(stack.what());
                        stackAmount = Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL));
                    } else {
                        stackName = Component.literal("ERROR");
                        stackAmount = Component.literal("ERROR");
                    }
                    yield InGameTooltip.CraftingLockedUntilResult.text(stackName, stackAmount);
                }
            };
            return tooltip != null ? new Tooltip(tooltip) : null;
        }
    }
}
