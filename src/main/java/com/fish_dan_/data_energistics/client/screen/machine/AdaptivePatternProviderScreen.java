package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.widget.AecsPullModeButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;
import com.fish_dan_.data_energistics.menu.AdaptivePatternProviderMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdaptivePatternProviderScreen extends AEBaseScreen<AdaptivePatternProviderMenu> {

    private static final int HIDDEN_SLOT_COORD = -9999;

    private final ToggleButton previousPageButton;
    private final ToggleButton nextPageButton;
    private final ToggleButton showInPatternAccessTerminalButton;
    private final ServerSettingToggleButton<YesNo> blockingModeButton;
    private final ServerSettingToggleButton<LockCraftingMode> lockCraftingModeButton;
    private final DataExtractorToggleButton filteredImportButton;
    private final AecsPullModeButton resonatingPullButton;
    private final PatternProviderRedstoneTuningButton redstoneTuningButton;
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

        this.redstoneTuningButton = new PatternProviderRedstoneTuningButton(menu);
        this.addToLeftToolbar(this.redstoneTuningButton);
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

        this.setTextContent("dialog_title",
                Component.translatable("block.data_energistics.adaptive_pattern_provider"));
        this.setTextContent("page_info", Component.translatable(
                "screen.data_energistics.page",
                this.menu.totalPages <= 0 ? 1 : this.menu.pageIndex + 1,
                Math.max(1, this.menu.totalPages)));
        this.redstoneTuningButton.syncFromMenu();
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        var semantic = this.menu.getSlotSemantic(slot);
        if (slot.isActive() && slot.getItem().isEmpty() && semantic == AdaptivePatternProviderMenu.PAGE_PATTERN) {
            Icon.BACKGROUND_BLANK_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        } else if (slot.isActive() && semantic == AdaptivePatternProviderMenu.PROVIDER_INPUT && slot.getItem().isEmpty()) {
            DataEnergisticsIcon.getBlitter("BACKGROUND_BLOCK")
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
            } else {
                String message = "Could not hide duplicate adaptive pattern provider slot: " + slot.getClass().getName();
                Data_Energistics.LOGGER.error(message);
                throw new IllegalStateException(message);
            }
            setSlotPosition(slot, HIDDEN_SLOT_COORD, HIDDEN_SLOT_COORD);
        }
    }

    private void installOrReplaceCompositeWidget(String id, ICompositeWidget widget) {
        this.widgets.compositeWidgets.put(id, widget);
    }

    private boolean hasWidget(String id) {
        return this.widgets.widgets.containsKey(id) || this.widgets.compositeWidgets.containsKey(id);
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
        slot.x = x;
        slot.y = y;
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
