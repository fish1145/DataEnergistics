package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerSelectionHighlighter;
import com.fish_dan_.data_energistics.client.widget.DataDistributionTowerConnectionModeButton;
import com.fish_dan_.data_energistics.client.widget.DataDistributionTowerTextureToggleButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.DataDistributionTowerMenu;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DataDistributionTowerScreen extends AEBaseScreen<DataDistributionTowerMenu> {

    private static final ResourceLocation LIST_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/list.png");
    private static final int LIST_TEXTURE_SIZE = 256;
    private static final int LIST_PANEL_SOURCE_SIZE = 128;
    private static final int LIST_PANEL_SLICE = 6;
    private static final int LIST_X = 13;
    private static final int LIST_Y = 52;
    private static final int LIST_WIDTH = 150;
    private static final int LIST_ROW_HEIGHT = 14;
    private static final int LIST_VISIBLE_ROWS = 5;
    private static final int POPUP_MARGIN = -12;
    private static final int POPUP_Y = 48;
    private static final int POPUP_WIDTH = 192;
    private static final int POPUP_HEIGHT = 124;
    private static final int POPUP_TITLE_X = 12;
    private static final int POPUP_TITLE_Y = 6;
    private static final int POPUP_BUTTON_Y_OFFSET = 34;
    private static final int POPUP_INFO_Y_OFFSET = 60;
    private static final int POPUP_LINE_GAP = 12;
    private static final float POPUP_TEXT_SCALE = 1.0F;
    private static final int POPUP_BUTTON_SCALE = 1;
    private static final int POPUP_CLOSE_SIZE = 16;
    private static final int POPUP_CLOSE_RIGHT = 14;
    private static final int POPUP_CLOSE_Y = 6;
    private static final int POPUP_Z_OFFSET = 300;
    private static final int POPUP_EXCLUSION_PADDING = 4;
    private static final int SELECTED_ROW_COLOR = 0x803976D8;
    private static final int SEARCH_X = 94;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_WIDTH = 70;
    private static final int SEARCH_HEIGHT = 12;
    private static final Component SEARCH_HINT = Component.translatable("screen.data_energistics.data_distribution_tower.search_hint");

    private final Scrollbar scrollbar;
    private final DataExtractorToggleButton rangeVisibleButton;
    private final DataDistributionTowerConnectionModeButton connectionModeButton;
    private final DataDistributionTowerTextureToggleButton disabledTargetsButton;
    private DataDistributionTowerTextureToggleButton targetChannelButton;
    private DataDistributionTowerTextureToggleButton targetEnergyButton;
    private List<BoundRow> allRows = List.of();
    private List<BoundRow> cachedRows = List.of();
    private TargetRef popupTarget;
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
        initTargetPopupButtons();
        applySearchFilter();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        setTextContent("dialog_title", Component.translatable(
                this.menu.online ? "screen.data_energistics.data_distribution_tower.title.online" : "screen.data_energistics.data_distribution_tower.title.offline"));
        setTextContent("ae_channels", Component.translatable(
                "screen.data_energistics.ae_channels",
                this.menu.usedChannels,
                this.menu.maxChannels));
        setTextContent("available_fe", Component.translatable(
                "screen.data_energistics.network_fe",
                formatFeAmount(this.menu.availableFe)));
        setTextContent("range", Component.translatable(
                "screen.data_energistics.range",
                formatRangeText(this.menu.chunkRadius)));
        setTextContent("range_visible", Component.translatable(
                this.menu.rangeVisible ? "screen.data_energistics.data_distribution_tower.range_visible.on" : "screen.data_energistics.data_distribution_tower.range_visible.off"));
        this.rangeVisibleButton.setState(this.menu.rangeVisible);
        this.connectionModeButton.setMode(ConnectionMode.fromOrdinal(this.menu.connectionMode));
        this.disabledTargetsButton.setState(this.disabledTargetsOnly);
        setTextContent("bound_title", Component.translatable(
                "screen.data_energistics.data_distribution_tower.bound_title",
                this.menu.boundTargetCount));
        setTextContent("player_inventory_title", Component.empty());
        updateTargetPopupButtons();
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
            if (isPopupTarget(row)) {
                guiGraphics.fill(LIST_X, y - 3, LIST_X + LIST_WIDTH - 12, y + LIST_ROW_HEIGHT, SELECTED_ROW_COLOR);
            }
            renderRowIcon(guiGraphics, row.iconStack(), LIST_X, y - 2);
            String line = row.displayText();
            if (this.font.width(line) > LIST_WIDTH) {
                line = this.font.plainSubstrByWidth(line, LIST_WIDTH - 6) + "...";
            }
            guiGraphics.drawString(this.font, line, LIST_X + 14, y, getRowColor(row.kind()), false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTargetPopupOverlay(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTargetPopupOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (getPopupRow() == null) {
            return;
        }

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(this.leftPos, this.topPos, 0.0F);
        try {
            renderTargetPopup(guiGraphics);
        } finally {
            pose.popPose();
        }

        if (this.targetChannelButton != null) {
            this.targetChannelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.targetEnergyButton != null) {
            this.targetEnergyButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasFocused = this.searchBox != null && this.searchBox.isFocused();
        BoundRow popupRow = getPopupRow();
        if (popupRow != null && handleTargetPopupClick(mouseX, mouseY, button, popupRow)) {
            return true;
        }

        BoundRow hoveredRow = findHoveredRow(mouseX, mouseY);
        if (hoveredRow != null) {
            if (hoveredRow.placeholder()) {
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.popupTarget = hoveredRow.target();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                this.popupTarget = null;
                DataDistributionTowerSelectionHighlighter.highlight(hoveredRow.dimension(), hoveredRow.pos());
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
        if (this.popupTarget != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !isInsideTargetPopup(mouseX, mouseY)) {
            this.popupTarget = null;
        }
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

    @Override
    public List<Rect2i> getExclusionZones() {
        List<Rect2i> zones = new ArrayList<>(super.getExclusionZones());
        Rect2i popupExclusionArea = getTargetPopupExclusionArea();
        if (popupExclusionArea != null) {
            zones.add(popupExclusionArea);
        }
        return zones;
    }

    public Rect2i getTargetPopupExclusionArea() {
        return getPopupRow() == null ? null : getPopupExclusionBounds();
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

        if (this.popupTarget != null && this.cachedRows.stream().noneMatch(row -> row.target().equals(this.popupTarget))) {
            this.popupTarget = null;
        }

        int hiddenRows = Math.max(0, this.cachedRows.size() - LIST_VISIBLE_ROWS);
        this.scrollbar.setRange(0, hiddenRows, 1);
        this.scrollbar.setVisible(hiddenRows > 0);
        this.scrollbar.setCurrentScroll(Math.min(this.scrollbar.getCurrentScroll(), hiddenRows));
    }

    private List<BoundRow> buildRows() {
        if (this.menu.boundTargets == null || this.menu.boundTargets.isBlank()) {
            return List.of(new BoundRow(new ItemStack(Items.BARRIER),
                    Component.translatable("screen.data_energistics.data_distribution_tower.bound_none").getString(),
                    "",
                    new TargetRef(Level.OVERWORLD, new net.minecraft.core.BlockPos(0, 0, 0)),
                    RowKind.FE,
                    TargetMode.DISABLED,
                    TransferInfo.EMPTY,
                    true));
        }

        String[] names = this.menu.boundTargets.split("\\n");
        String[] icons = this.menu.boundTargetIcons == null || this.menu.boundTargetIcons.isBlank() ? new String[0] : this.menu.boundTargetIcons.split("\\n");
        String[] metas = this.menu.boundTargetMeta == null || this.menu.boundTargetMeta.isBlank() ? new String[0] : this.menu.boundTargetMeta.split("\\n");
        String[] kinds = this.menu.boundTargetKinds == null || this.menu.boundTargetKinds.isBlank() ? new String[0] : this.menu.boundTargetKinds.split("\\n");
        String[] modes = this.menu.boundTargetModes == null || this.menu.boundTargetModes.isBlank() ? new String[0] : this.menu.boundTargetModes.split("\\n");
        String[] transferInfo = this.menu.boundTargetTransferInfo == null || this.menu.boundTargetTransferInfo.isBlank() ? new String[0] : this.menu.boundTargetTransferInfo.split("\\n");

        ArrayList<BoundRow> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            rows.add(new BoundRow(
                    i < icons.length ? toStack(icons[i]) : new ItemStack(Items.BARRIER),
                    names[i],
                    names[i],
                    i < metas.length ? parseMeta(metas[i]) : new TargetRef(Level.OVERWORLD, new net.minecraft.core.BlockPos(0, 0, 0)),
                    i < kinds.length ? parseKind(kinds[i]) : RowKind.FE,
                    i < modes.length ? parseMode(modes[i]) : TargetMode.AE_AND_FE,
                    i < transferInfo.length ? parseTransferInfo(transferInfo[i]) : TransferInfo.EMPTY,
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

    private ItemStack toStack(String itemId) {
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
        } catch (Exception ignored) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private void renderRowIcon(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.75f, 0.75f, 1.0f);
        guiGraphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private int getRowColor(RowKind kind) {
        return kind == RowKind.AE ? 0xD58CFF : 0x9FFFA8;
    }

    private int getModeColor(TargetMode mode) {
        return switch (mode) {
            case AE_AND_FE -> 0xFFFFFF;
            case AE_ONLY -> 0xD58CFF;
            case FE_ONLY -> 0x9FFFA8;
            case DISABLED -> 0x777777;
        };
    }

    private void renderTargetPopup(GuiGraphics guiGraphics) {
        BoundRow popupRow = getPopupRow();
        if (popupRow == null) {
            return;
        }

        Rect2i popupBounds = getPopupBounds();
        int popupX = popupBounds.getX() - this.leftPos;
        int popupY = popupBounds.getY() - this.topPos;
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, POPUP_Z_OFFSET);

        try {
            drawListPanel(guiGraphics, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT);
            enablePopupScissor(guiGraphics);
            String title = trimTextToWidth(popupRow.displayText(), POPUP_WIDTH - POPUP_CLOSE_SIZE - 30);
            guiGraphics.drawString(this.font, title, popupX + POPUP_TITLE_X, popupY + POPUP_TITLE_Y, getRowColor(popupRow.kind()), false);
            Icon.CLEAR.getBlitter()
                    .dest(popupX + POPUP_WIDTH - POPUP_CLOSE_SIZE - POPUP_CLOSE_RIGHT, popupY + POPUP_CLOSE_Y, POPUP_CLOSE_SIZE, POPUP_CLOSE_SIZE)
                    .zOffset(4)
                    .blit(guiGraphics);

            TransferInfo info = popupRow.transferInfo();
            int y = popupY + POPUP_INFO_Y_OFFSET;
            drawPopupLine(guiGraphics, popupX, Component.translatable(
                    "screen.data_energistics.data_distribution_tower.target_mode",
                    Component.translatable("button.data_energistics.data_distribution_tower.target_mode." + popupRow.mode().serializedName())).getString(), y, getModeColor(popupRow.mode()));
            y += POPUP_LINE_GAP;
            drawPopupLine(guiGraphics, popupX, Component.translatable(
                    "screen.data_energistics.data_distribution_tower.target_channels",
                    info.channelConnections(),
                    info.hasAeTarget() ? Component.translatable("screen.data_energistics.data_distribution_tower.available") : Component.translatable("screen.data_energistics.data_distribution_tower.unavailable")).getString(), y, 0xD58CFF);
            y += POPUP_LINE_GAP;
            String feText = info.hasEnergyTarget() ? formatFeAmount(info.storedFe()) + " / " + formatFeAmount(info.capacityFe()) + " FE" : Component.translatable("screen.data_energistics.data_distribution_tower.unavailable").getString();
            drawPopupLine(guiGraphics, popupX, Component.translatable(
                    "screen.data_energistics.data_distribution_tower.target_energy",
                    feText).getString(), y, 0x9FFFA8);
            y += POPUP_LINE_GAP;
            drawPopupLine(guiGraphics, popupX, Component.translatable(
                    "screen.data_energistics.data_distribution_tower.target_energy_io",
                    formatBoolean(info.canExtractFe()),
                    formatBoolean(info.canReceiveFe())).getString(), y, 0xDADADA);
        } finally {
            guiGraphics.disableScissor();
            pose.popPose();
        }
    }

    private void drawPopupLine(GuiGraphics guiGraphics, int popupX, String line, int y, int color) {
        line = trimTextToWidth(line, POPUP_WIDTH - 24, POPUP_TEXT_SCALE);
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.scale(POPUP_TEXT_SCALE, POPUP_TEXT_SCALE, 1.0F);
        guiGraphics.drawString(this.font, line,
                Math.round((popupX + 12) / POPUP_TEXT_SCALE),
                Math.round(y / POPUP_TEXT_SCALE),
                color,
                false);
        pose.popPose();
    }

    private String trimTextToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        int ellipsisWidth = this.font.width("...");
        if (maxWidth <= ellipsisWidth) {
            return "";
        }
        return this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + "...";
    }

    private String trimTextToWidth(String text, int maxWidth, float scale) {
        int scaledWidth = (int) Math.floor(maxWidth / scale);
        return trimTextToWidth(text, scaledWidth);
    }

    private Component formatBoolean(boolean value) {
        return Component.translatable(value ? "screen.data_energistics.data_distribution_tower.yes" : "screen.data_energistics.data_distribution_tower.no");
    }

    private BoundRow getPopupRow() {
        if (this.popupTarget == null) {
            return null;
        }
        for (BoundRow row : this.allRows) {
            if (row.target().equals(this.popupTarget)) {
                return row;
            }
        }
        this.popupTarget = null;
        return null;
    }

    private boolean handleTargetPopupClick(double mouseX, double mouseY, int button, BoundRow popupRow) {
        if (!isInsideTargetPopup(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.popupTarget = null;
            }
            return false;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        int popupX = getPopupLocalX();
        int popupY = getPopupLocalY();
        if (isInRect(localX, localY, popupX + POPUP_WIDTH - POPUP_CLOSE_SIZE - POPUP_CLOSE_RIGHT, popupY + POPUP_CLOSE_Y, POPUP_CLOSE_SIZE, POPUP_CLOSE_SIZE)) {
            this.popupTarget = null;
            return true;
        }

        return false;
    }

    private void setPopupTargetMode(BoundRow row, TargetMode mode) {
        this.menu.sendSetTargetTransferMode(
                row.dimension().location().toString(),
                row.pos().getX(),
                row.pos().getY(),
                row.pos().getZ(),
                TargetTransferMode.fromOrdinal(mode.ordinal()));
    }

    private boolean isInsideTargetPopup(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        return isInRect(localX, localY, getPopupLocalX(), getPopupLocalY(), POPUP_WIDTH, POPUP_HEIGHT);
    }

    private boolean isInRect(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && x <= rectX + width && y >= rectY && y <= rectY + height;
    }

    private void enablePopupScissor(GuiGraphics guiGraphics) {
        Rect2i popupBounds = getPopupBounds();
        guiGraphics.enableScissor(
                popupBounds.getX(),
                popupBounds.getY(),
                popupBounds.getX() + POPUP_WIDTH,
                popupBounds.getY() + POPUP_HEIGHT);
    }

    private Rect2i getPopupExclusionBounds() {
        Rect2i popupBounds = getPopupBounds();
        return new Rect2i(
                popupBounds.getX() - POPUP_EXCLUSION_PADDING,
                popupBounds.getY() - POPUP_EXCLUSION_PADDING,
                POPUP_WIDTH + POPUP_EXCLUSION_PADDING * 2,
                POPUP_HEIGHT + POPUP_EXCLUSION_PADDING * 2);
    }

    private void initTargetPopupButtons() {
        this.targetChannelButton = new DataDistributionTowerTextureToggleButton(
                "POWER_UNIT_I",
                "POWER_UNIT_NO",
                "screen.data_energistics.data_distribution_tower.target_channel_toggle",
                "screen.data_energistics.data_distribution_tower.on",
                "screen.data_energistics.data_distribution_tower.off",
                enabled -> {
                    BoundRow row = getPopupRow();
                    if (row != null) {
                        setPopupTargetMode(row, row.mode().withAe(enabled));
                    }
                });
        this.targetChannelButton.visible = false;
        this.targetChannelButton.active = false;
        this.addRenderableWidget(this.targetChannelButton);

        this.targetEnergyButton = new DataDistributionTowerTextureToggleButton(
                "POWER_UNIT_F",
                "POWER_UNIT_NO",
                "screen.data_energistics.data_distribution_tower.target_energy_toggle",
                "screen.data_energistics.data_distribution_tower.on",
                "screen.data_energistics.data_distribution_tower.off",
                enabled -> {
                    BoundRow row = getPopupRow();
                    if (row != null) {
                        setPopupTargetMode(row, row.mode().withFe(enabled));
                    }
                });
        this.targetEnergyButton.visible = false;
        this.targetEnergyButton.active = false;
        this.addRenderableWidget(this.targetEnergyButton);
    }

    private void updateTargetPopupButtons() {
        if (this.targetChannelButton == null || this.targetEnergyButton == null) {
            return;
        }

        BoundRow popupRow = getPopupRow();
        boolean visible = popupRow != null;
        this.targetChannelButton.visible = visible;
        this.targetChannelButton.active = visible;
        this.targetEnergyButton.visible = visible;
        this.targetEnergyButton.active = visible;
        if (!visible) {
            return;
        }

        Rect2i popupBounds = getPopupBounds();
        int popupX = popupBounds.getX();
        int buttonY = popupBounds.getY() + POPUP_BUTTON_Y_OFFSET;
        this.targetChannelButton.setVisualScale(POPUP_BUTTON_SCALE);
        this.targetEnergyButton.setVisualScale(POPUP_BUTTON_SCALE);
        this.targetChannelButton.setVisualZOffset(POPUP_Z_OFFSET);
        this.targetEnergyButton.setVisualZOffset(POPUP_Z_OFFSET);
        this.targetChannelButton.setX(popupX + 12);
        this.targetChannelButton.setY(buttonY);
        this.targetChannelButton.setState(popupRow.mode().allowsAe());
        this.targetEnergyButton.setX(popupX + 36);
        this.targetEnergyButton.setY(buttonY);
        this.targetEnergyButton.setState(popupRow.mode().allowsFe());
    }

    private boolean isPopupTarget(BoundRow row) {
        return this.popupTarget != null && this.popupTarget.equals(row.target());
    }

    private void drawListPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawNineSlicedListPanel(guiGraphics, x, y, width, height,
                LIST_PANEL_SLICE, LIST_PANEL_SLICE, LIST_PANEL_SLICE, LIST_PANEL_SLICE);
    }

    private void drawNineSlicedListPanel(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         int left, int top, int right, int bottom) {
        int centerDstWidth = Math.max(0, width - left - right);
        int centerDstHeight = Math.max(0, height - top - bottom);
        int centerSrcWidth = LIST_PANEL_SOURCE_SIZE - left - right;
        int centerSrcHeight = LIST_PANEL_SOURCE_SIZE - top - bottom;

        drawScaledListRegion(guiGraphics, x, y, 0, 0, left, top, left, top);
        drawScaledListRegion(guiGraphics, x + width - right, y, LIST_PANEL_SOURCE_SIZE - right, 0, right, top, right, top);
        drawScaledListRegion(guiGraphics, x, y + height - bottom, 0, LIST_PANEL_SOURCE_SIZE - bottom, left, bottom, left, bottom);
        drawScaledListRegion(guiGraphics, x + width - right, y + height - bottom,
                LIST_PANEL_SOURCE_SIZE - right, LIST_PANEL_SOURCE_SIZE - bottom, right, bottom, right, bottom);

        if (centerDstWidth > 0) {
            drawScaledListRegion(guiGraphics, x + left, y, left, 0, centerSrcWidth, top, centerDstWidth, top);
            drawScaledListRegion(guiGraphics, x + left, y + height - bottom,
                    left, LIST_PANEL_SOURCE_SIZE - bottom, centerSrcWidth, bottom, centerDstWidth, bottom);
        }

        if (centerDstHeight > 0) {
            drawScaledListRegion(guiGraphics, x, y + top, 0, top, left, centerSrcHeight, left, centerDstHeight);
            drawScaledListRegion(guiGraphics, x + width - right, y + top,
                    LIST_PANEL_SOURCE_SIZE - right, top, right, centerSrcHeight, right, centerDstHeight);
        }

        if (centerDstWidth > 0 && centerDstHeight > 0) {
            drawScaledListRegion(guiGraphics, x + left, y + top, left, top,
                    centerSrcWidth, centerSrcHeight, centerDstWidth, centerDstHeight);
        }
    }

    private void drawScaledListRegion(GuiGraphics guiGraphics, int x, int y, int srcX, int srcY,
                                      int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return;
        }

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(dstWidth / (float) srcWidth, dstHeight / (float) srcHeight, 1.0F);
        guiGraphics.blit(LIST_TEXTURE, 0, 0, 0, srcX, srcY, srcWidth, srcHeight,
                LIST_TEXTURE_SIZE, LIST_TEXTURE_SIZE);
        pose.popPose();
    }

    private Rect2i getPopupBounds() {
        int preferredX = this.leftPos + this.imageWidth + POPUP_MARGIN;
        int preferredY = this.topPos + POPUP_Y;
        int x = Math.max(4, Math.min(preferredX, this.width - POPUP_WIDTH - 4));
        int y = Math.max(4, Math.min(preferredY, this.height - POPUP_HEIGHT - 4));
        return new Rect2i(x, y, POPUP_WIDTH, POPUP_HEIGHT);
    }

    private int getPopupLocalX() {
        return getPopupBounds().getX() - this.leftPos;
    }

    private int getPopupLocalY() {
        return getPopupBounds().getY() - this.topPos;
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

    private static String formatFeAmount(long amount) {
        if (amount >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", amount / 1_000.0);
        }
        return Long.toString(amount);
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

    private TargetRef parseMeta(String meta) {
        try {
            String[] parts = meta.split("\\|");
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(parts[0]));
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
            return new TargetRef(dimension, pos);
        } catch (Exception ignored) {
            return new TargetRef(Level.OVERWORLD, new net.minecraft.core.BlockPos(0, 0, 0));
        }
    }

    private RowKind parseKind(String kind) {
        return "AE".equalsIgnoreCase(kind) ? RowKind.AE : RowKind.FE;
    }

    private TargetMode parseMode(String mode) {
        try {
            return TargetMode.valueOf(mode);
        } catch (Exception ignored) {
            return TargetMode.AE_AND_FE;
        }
    }

    private TransferInfo parseTransferInfo(String info) {
        try {
            String[] parts = info.split("\\|");
            return new TransferInfo(
                    Integer.parseInt(parts[0]),
                    Boolean.parseBoolean(parts[1]),
                    Boolean.parseBoolean(parts[2]),
                    Long.parseLong(parts[3]),
                    Long.parseLong(parts[4]),
                    Boolean.parseBoolean(parts[5]),
                    Boolean.parseBoolean(parts[6]));
        } catch (Exception ignored) {
            return TransferInfo.EMPTY;
        }
    }

    private record BoundRow(ItemStack iconStack, String displayText, String searchIndex, TargetRef target, RowKind kind,
                            TargetMode mode, TransferInfo transferInfo, boolean placeholder) {

        private ResourceKey<Level> dimension() {
            return target.dimension();
        }

        private net.minecraft.core.BlockPos pos() {
            return target.pos();
        }
    }

    private record TargetRef(ResourceKey<Level> dimension, net.minecraft.core.BlockPos pos) {}

    private enum RowKind {
        AE,
        FE
    }

    private enum TargetMode {

        AE_AND_FE("af"),
        AE_ONLY("ae"),
        FE_ONLY("fe"),
        DISABLED("off");

        private final String serializedName;

        TargetMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private String serializedName() {
            return this.serializedName;
        }

        private boolean allowsAe() {
            return this == AE_AND_FE || this == AE_ONLY;
        }

        private boolean allowsFe() {
            return this == AE_AND_FE || this == FE_ONLY;
        }

        private TargetMode withAe(boolean enabled) {
            return fromFlags(enabled, allowsFe());
        }

        private TargetMode withFe(boolean enabled) {
            return fromFlags(allowsAe(), enabled);
        }

        private static TargetMode fromFlags(boolean ae, boolean fe) {
            if (ae && fe) {
                return AE_AND_FE;
            }
            if (ae) {
                return AE_ONLY;
            }
            if (fe) {
                return FE_ONLY;
            }
            return DISABLED;
        }
    }

    private record TransferInfo(int channelConnections, boolean hasAeTarget, boolean hasEnergyTarget, long storedFe,
                                long capacityFe, boolean canExtractFe, boolean canReceiveFe) {

        private static final TransferInfo EMPTY = new TransferInfo(0, false, false, 0L, 0L, false, false);
    }
}
