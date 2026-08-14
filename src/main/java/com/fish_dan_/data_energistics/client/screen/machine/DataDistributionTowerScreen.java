package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.RangeAdjustmentMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferInfo;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerVirtualDeviceState;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerSelectionHighlighter;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.client.widget.DataDistributionTowerConnectionModeButton;
import com.fish_dan_.data_energistics.client.widget.DataDistributionTowerTextureToggleButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.DataDistributionTowerMenu;
import com.fish_dan_.data_energistics.network.tower.DataDistributionTowerTargetEntry;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DataDistributionTowerScreen extends AEBaseScreen<DataDistributionTowerMenu> {

    private static final int LIST_X = 13;
    private static final int LIST_Y = 52;
    private static final int LIST_WIDTH = 150;
    private static final int LIST_ROW_HEIGHT = 14;
    private static final int LIST_VISIBLE_ROWS = 5;
    private static final int SEARCH_X = 94;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_WIDTH = 70;
    private static final int SEARCH_HEIGHT = 12;
    private static final Component SEARCH_HINT = Component.translatable("screen.data_energistics.data_distribution_tower.search_hint");

    private final Scrollbar scrollbar;
    private final DataExtractorToggleButton rangeVisibleButton;
    private final DataDistributionTowerConnectionModeButton connectionModeButton;
    private final DataDistributionTowerTextureToggleButton rangeAdjustmentModeButton;
    private final DataDistributionTowerTextureToggleButton disabledTargetsButton;
    private List<BoundRow> allRows = List.of();
    private List<BoundRow> cachedRows = List.of();
    private AETextField searchBox;
    private String searchQuery = "";
    private boolean disabledTargetsOnly;

    public DataDistributionTowerScreen(DataDistributionTowerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.rangeVisibleButton = new DataExtractorToggleButton(
                Icon.PATTERN_TERMINAL_ALL,
                Icon.PATTERN_TERMINAL_VISIBLE,
                "button.data_energistics.range_visible",
                "button.data_energistics.data_distribution_tower.range_visible.enabled",
                "button.data_energistics.data_distribution_tower.range_visible.disabled",
                this.menu::sendSetRangeVisible);
        this.addToLeftToolbar(this.rangeVisibleButton);
        this.connectionModeButton = new DataDistributionTowerConnectionModeButton(this.menu::sendSetConnectionMode);
        this.addToLeftToolbar(this.connectionModeButton);
        this.rangeAdjustmentModeButton = new DataDistributionTowerTextureToggleButton(
                "POWER_UNIT_SCOPE",
                "POWER_UNIT_POINT",
                "button.data_energistics.data_distribution_tower.range_adjustment",
                "button.data_energistics.data_distribution_tower.range_adjustment.enabled",
                "button.data_energistics.data_distribution_tower.range_adjustment.disabled",
                this.menu::sendSetRangeAdjustmentMode);
        this.addToLeftToolbar(this.rangeAdjustmentModeButton);
        this.disabledTargetsButton = new DataDistributionTowerTextureToggleButton(
                "POWER_UNIT_BLACK_LIST",
                "POWER_UNIT_WHITE_LIST",
                "button.data_energistics.data_distribution_tower.disabled_targets",
                "button.data_energistics.data_distribution_tower.disabled_targets.enabled",
                "button.data_energistics.data_distribution_tower.disabled_targets.disabled",
                this::setDisabledTargetsOnly);
        this.addToLeftToolbar(this.disabledTargetsButton);
        refreshFromServer();
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new AETextField(this.getStyle(), this.font,
                this.leftPos + SEARCH_X,
                this.topPos + SEARCH_Y,
                SEARCH_WIDTH,
                SEARCH_HEIGHT);
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setPlaceholder(SEARCH_HINT);
        this.searchBox.setResponder(value -> {
            this.searchQuery = value;
            updateSearchSuggestion();
            applySearchFilter();
        });
        updateSearchSuggestion();
        this.addRenderableWidget(this.searchBox);
        applySearchFilter();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent("dialog_title", Component.translatable(
                this.menu.online ? "screen.data_energistics.data_distribution_tower.title.online" : "screen.data_energistics.data_distribution_tower.title.offline"));
        setTextContent("ae_channels", Component.translatable(
                "screen.data_energistics.data_distribution_tower.channel_overview",
                this.menu.unlimitedChannels ? "∞" : Long.toString(this.menu.maxChannels),
                this.menu.physicalChannels,
                this.menu.virtualChannels,
                this.menu.unlimitedChannels ? "∞" : Long.toString(this.menu.remainingChannels)));
        setTextContent("available_fe", Component.translatable(
                "screen.data_energistics.network_fe",
                TrinityAmountFormatter.format(this.menu.availableFe)));
        setTextContent("range", Component.translatable(
                "screen.data_energistics.range",
                formatRangeText(this.menu.chunkRadius)));
        setTextContent("range_visible", Component.translatable(
                this.menu.rangeVisible ? "screen.data_energistics.data_distribution_tower.range_visible.on" : "screen.data_energistics.data_distribution_tower.range_visible.off"));
        this.rangeVisibleButton.setState(this.menu.rangeVisible);
        this.connectionModeButton.setMode(ConnectionMode.fromOrdinal(this.menu.connectionMode));
        this.rangeAdjustmentModeButton.setState(RangeAdjustmentMode.fromOrdinal(this.menu.rangeAdjustmentMode) == RangeAdjustmentMode.SCOPE);
        this.disabledTargetsButton.setState(this.disabledTargetsOnly);
        setTextContent("bound_title", Component.translatable(
                "screen.data_energistics.data_distribution_tower.bound_title",
                this.menu.boundTargetCount));
        setTextContent("player_inventory_title", Component.empty());
    }

    private Component formatRangeText(int chunkRadius) {
        int diameter = chunkRadius * 2 + 1;
        return Component.translatable("text.data_energistics.data_distribution_tower.range.chunk_square", diameter, diameter);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        List<BoundRow> lines = this.cachedRows;
        if (lines.isEmpty()) {
            guiGraphics.drawString(this.font, getEmptyStateText(), LIST_X + 14, LIST_Y, 0xA8A8A8, false);
            return;
        }

        int start = this.scrollbar.getCurrentScroll();
        int end = Math.min(lines.size(), start + LIST_VISIBLE_ROWS);

        for (int i = start; i < end; i++) {
            int y = LIST_Y + (i - start) * LIST_ROW_HEIGHT;
            BoundRow row = lines.get(i);
            renderRowIcon(guiGraphics, row.iconStack(), y - 2);
            String line = row.displayText();
            if (this.font.width(line) > LIST_WIDTH) {
                line = this.font.plainSubstrByWidth(line, LIST_WIDTH - 6) + "...";
            }
            guiGraphics.drawString(this.font, line, LIST_X + 14, y, getRowColor(row), false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasFocused = this.searchBox != null && this.searchBox.isFocused();
        BoundRow hoveredRow = findHoveredRow(mouseX, mouseY);
        if (hoveredRow != null) {
            if (hoveredRow.placeholder()) {
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (hasShiftDown()) {
                    toggleTargetDisabled(hoveredRow);
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                if (hoveredRow.transferInfo() != null && hoveredRow.transferInfo().logicalDevice()) {
                    return true;
                }
                TargetTransferInfo transferInfo = hoveredRow.transferInfo();
                Direction deviceSide = transferInfo != null && transferInfo.deviceKey() != null && transferInfo.deviceKey().side() >= 0 ? Direction.values()[transferInfo.deviceKey().side()] : null;
                DataDistributionTowerSelectionHighlighter.highlight(hoveredRow.dimension(), hoveredRow.pos(), deviceSide);
                this.menu.sendFocusTarget(
                        hoveredRow.dimension().location().toString(),
                        hoveredRow.pos().getX(),
                        hoveredRow.pos().getY(),
                        hoveredRow.pos().getZ(),
                        hasShiftDown());
                if (hasShiftDown()) {
                    this.onClose();
                }
                return true;
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (this.searchBox != null && wasFocused != this.searchBox.isFocused()) {
            updateSearchSuggestion();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused() && Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        if (this.searchBox != null && this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            updateSearchSuggestion();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.charTyped(codePoint, modifiers)) {
            updateSearchSuggestion();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    public void refreshFromServer() {
        this.allRows = buildRows();
        applySearchFilter();
    }

    private void applySearchFilter() {
        String filter = PinyinUtil.normalizeSearch(this.searchQuery);
        if (filter.isEmpty()) {
            this.cachedRows = this.allRows.stream()
                    .filter(this::matchesDisabledTargetsFilter)
                    .toList();
        } else {
            this.cachedRows = this.allRows.stream()
                    .filter(this::matchesDisabledTargetsFilter)
                    .filter(row -> PinyinUtil.matchesSearch(row.displayText(), filter))
                    .toList();
        }

        int hiddenRows = Math.max(0, this.cachedRows.size() - LIST_VISIBLE_ROWS);
        this.scrollbar.setRange(0, hiddenRows, 1);
        this.scrollbar.setVisible(hiddenRows > 0);
        this.scrollbar.setCurrentScroll(Math.min(this.scrollbar.getCurrentScroll(), hiddenRows));
    }

    private List<BoundRow> buildRows() {
        if (this.menu.boundTargetEntries.isEmpty()) {
            return List.of(new BoundRow(new ItemStack(Items.BARRIER),
                    Component.translatable("screen.data_energistics.data_distribution_tower.bound_none").getString(),
                    "",
                    new TargetRef(Level.OVERWORLD, new BlockPos(0, 0, 0)),
                    RowKind.FE,
                    TargetMode.DISABLED,
                    null,
                    true));
        }

        ArrayList<BoundRow> rows = new ArrayList<>();
        for (DataDistributionTowerTargetEntry entry : this.menu.boundTargetEntries) {
            TargetTransferInfo transferInfo = entry.transferInfo();
            String stateText = Component.translatable(
                    "screen.data_energistics.data_distribution_tower.state." + transferInfo.state().name().toLowerCase(Locale.ROOT)).getString();
            String channelText = transferInfo.requestedChannels() == 0 ? "" : " " + transferInfo.channelConnections() + "/" + transferInfo.requestedChannels();
            String failureText = transferInfo.failure().isBlank() ? "" : " !" + transferInfo.failure();
            String displayText = entry.displayName() + (entry.count() > 1 ? " x" + entry.count() : "") + " [" + stateText + channelText + "]" + failureText;
            rows.add(new BoundRow(
                    toStack(entry.itemId()),
                    displayText,
                    entry.displayName(),
                    new TargetRef(ResourceKey.create(Registries.DIMENSION, entry.dimensionId()), entry.pos()),
                    entry.kind() == TargetKind.AE ? RowKind.AE : RowKind.FE,
                    toTargetMode(entry.transferMode()),
                    transferInfo,
                    false));
        }
        return rows;
    }

    private void setDisabledTargetsOnly(boolean disabledTargetsOnly) {
        this.disabledTargetsOnly = disabledTargetsOnly;
        applySearchFilter();
    }

    private boolean matchesDisabledTargetsFilter(BoundRow row) {
        return !this.disabledTargetsOnly || row.placeholder() || row.mode() == TargetMode.DISABLED;
    }

    private ItemStack toStack(ResourceLocation itemId) {
        var item = BuiltInRegistries.ITEM.get(itemId);
        return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
    }

    private TargetMode toTargetMode(TargetTransferMode mode) {
        return switch (mode) {
            case AUTO -> TargetMode.AUTO;
            case DISABLED -> TargetMode.DISABLED;
        };
    }

    private void renderRowIcon(GuiGraphics guiGraphics, ItemStack stack, int y) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(LIST_X, y, 0);
        pose.scale(0.75f, 0.75f, 1.0f);
        guiGraphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private int getRowColor(BoundRow row) {
        if (row.transferInfo() != null) {
            return switch (row.transferInfo().state()) {
                case ALLOCATED -> row.kind() == RowKind.AE ? 0xD58CFF : 0x9FFFA8;
                case WAITING_CHANNEL, WAITING_TARGET -> 0xFFE07A;
                case DISABLED -> 0x8A8A8A;
                case CONFLICT, BRIDGE_ERROR -> 0xFF7777;
            };
        }
        return row.kind() == RowKind.AE ? 0xD58CFF : 0x9FFFA8;
    }

    private void toggleTargetDisabled(BoundRow row) {
        TargetTransferInfo transferInfo = row.transferInfo();
        if (transferInfo != null && transferInfo.deviceKey() != null) {
            this.menu.sendSetVirtualDeviceDisabled(
                    transferInfo,
                    transferInfo.state() != TowerVirtualDeviceState.DISABLED);
            return;
        }
        TargetMode nextMode = row.mode() == TargetMode.DISABLED ? TargetMode.AUTO : TargetMode.DISABLED;
        this.menu.sendSetTargetTransferMode(
                row.dimension().location().toString(),
                row.pos().getX(),
                row.pos().getY(),
                row.pos().getZ(),
                TargetTransferMode.fromOrdinal(nextMode.ordinal()));
    }

    private String getEmptyStateText() {
        if (!PinyinUtil.normalizeSearch(this.searchQuery).isEmpty()) {
            return Component.translatable(
                    "screen.data_energistics.data_distribution_tower.search_no_match").getString();
        }
        if (this.disabledTargetsOnly) {
            return Component.translatable("screen.data_energistics.data_distribution_tower.disabled_none").getString();
        }
        return Component.translatable("screen.data_energistics.data_distribution_tower.bound_none").getString();
    }

    private void updateSearchSuggestion() {
        if (this.searchBox == null) {
            return;
        }

        this.searchBox.setPlaceholder(this.searchBox.isFocused() ? null : SEARCH_HINT);
    }

    private BoundRow findHoveredRow(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;

        List<BoundRow> rows = this.cachedRows;
        int start = this.scrollbar.getCurrentScroll();
        int end = Math.min(rows.size(), start + LIST_VISIBLE_ROWS);
        for (int i = start; i < end; i++) {
            int y = LIST_Y + (i - start) * LIST_ROW_HEIGHT;
            if (localX >= LIST_X && localX <= LIST_X + LIST_WIDTH && localY >= y - 2 && localY <= y + LIST_ROW_HEIGHT) {
                return rows.get(i);
            }
        }
        return null;
    }

    private record BoundRow(ItemStack iconStack,
                            String displayText,
                            String searchIndex,
                            TargetRef target,
                            RowKind kind,
                            TargetMode mode,
                            @Nullable TargetTransferInfo transferInfo,
                            boolean placeholder) {

        private ResourceKey<Level> dimension() {
            return target.dimension();
        }

        private BlockPos pos() {
            return target.pos();
        }
    }

    private record TargetRef(ResourceKey<Level> dimension, BlockPos pos) {}

    private enum RowKind {
        AE,
        FE
    }

    private enum TargetMode {

        AUTO,
        DISABLED
    }
}
