package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectionMode;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetTransferMode;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerSelectionHighlighter;
import com.fish_dan_.data_energistics.client.widget.DataDistributionTowerConnectionModeButton;
import com.fish_dan_.data_energistics.client.widget.DataExtractorToggleButton;
import com.fish_dan_.data_energistics.menu.DataDistributionTowerMenu;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    private static final int LIST_X = 13;
    private static final int LIST_Y = 52;
    private static final int LIST_WIDTH = 150;
    private static final int LIST_ROW_HEIGHT = 14;
    private static final int LIST_VISIBLE_ROWS = 5;
    private static final int POPUP_X = 20;
    private static final int POPUP_Y = 62;
    private static final int POPUP_WIDTH = 156;
    private static final int POPUP_HEIGHT = 86;
    private static final int POPUP_BUTTON_Y = POPUP_Y + 21;
    private static final int POPUP_BUTTON_WIDTH = 68;
    private static final int POPUP_BUTTON_HEIGHT = 14;
    private static final int SEARCH_X = 94;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_WIDTH = 70;
    private static final int SEARCH_HEIGHT = 12;
    private static final Component SEARCH_HINT = Component.translatable("screen.data_energistics.data_distribution_tower.search_hint");

    private final Scrollbar scrollbar;
    private final DataExtractorToggleButton rangeVisibleButton;
    private final DataDistributionTowerConnectionModeButton connectionModeButton;
    private List<BoundRow> allRows = List.of();
    private List<BoundRow> cachedRows = List.of();
    private TargetRef popupTarget;
    private AETextField searchBox;
    private String searchQuery = "";

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
            renderRowIcon(guiGraphics, row.iconStack(), LIST_X, y - 2);
            String line = row.displayText();
            if (this.font.width(line) > LIST_WIDTH) {
                line = this.font.plainSubstrByWidth(line, LIST_WIDTH - 6) + "...";
            }
            guiGraphics.drawString(this.font, line, LIST_X + 14, y, getRowColor(row.kind()), false);
        }

        renderTargetPopup(guiGraphics);
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

    private void applySearchFilter() {
        String filter = PinyinUtil.normalizeSearch(this.searchQuery);
        if (filter.isEmpty()) {
            this.cachedRows = List.copyOf(this.allRows);
        } else {
            this.cachedRows = this.allRows.stream()
                    .filter(row -> PinyinUtil.matchesSearch(row.displayText(), filter))
                    .toList();
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

        guiGraphics.fill(POPUP_X, POPUP_Y, POPUP_X + POPUP_WIDTH, POPUP_Y + POPUP_HEIGHT, 0xEE10141A);
        guiGraphics.fill(POPUP_X, POPUP_Y, POPUP_X + POPUP_WIDTH, POPUP_Y + 1, 0xFF7DA7C7);
        guiGraphics.fill(POPUP_X, POPUP_Y + POPUP_HEIGHT - 1, POPUP_X + POPUP_WIDTH, POPUP_Y + POPUP_HEIGHT, 0xFF314250);
        guiGraphics.fill(POPUP_X, POPUP_Y, POPUP_X + 1, POPUP_Y + POPUP_HEIGHT, 0xFF314250);
        guiGraphics.fill(POPUP_X + POPUP_WIDTH - 1, POPUP_Y, POPUP_X + POPUP_WIDTH, POPUP_Y + POPUP_HEIGHT, 0xFF314250);

        String title = popupRow.displayText();
        if (this.font.width(title) > POPUP_WIDTH - 25) {
            title = this.font.plainSubstrByWidth(title, POPUP_WIDTH - 31) + "...";
        }
        guiGraphics.drawString(this.font, title, POPUP_X + 7, POPUP_Y + 6, getRowColor(popupRow.kind()), false);
        guiGraphics.drawString(this.font, "x", POPUP_X + POPUP_WIDTH - 11, POPUP_Y + 6, 0xB8B8B8, false);

        int channelButtonX = POPUP_X + 7;
        int energyButtonX = POPUP_X + 81;
        drawToggleButton(guiGraphics,
                Component.translatable("screen.data_energistics.data_distribution_tower.target_channel_toggle").getString(),
                popupRow.mode().allowsAe(),
                channelButtonX,
                POPUP_BUTTON_Y,
                POPUP_BUTTON_WIDTH,
                POPUP_BUTTON_HEIGHT,
                0xD58CFF);
        drawToggleButton(guiGraphics,
                Component.translatable("screen.data_energistics.data_distribution_tower.target_energy_toggle").getString(),
                popupRow.mode().allowsFe(),
                energyButtonX,
                POPUP_BUTTON_Y,
                POPUP_BUTTON_WIDTH,
                POPUP_BUTTON_HEIGHT,
                0x9FFFA8);

        TransferInfo info = popupRow.transferInfo();
        int y = POPUP_Y + 39;
        drawPopupLine(guiGraphics, Component.translatable(
                "screen.data_energistics.data_distribution_tower.target_mode",
                Component.translatable("button.data_energistics.data_distribution_tower.target_mode." + popupRow.mode().serializedName())).getString(), y, getModeColor(popupRow.mode()));
        y += 10;
        drawPopupLine(guiGraphics, Component.translatable(
                "screen.data_energistics.data_distribution_tower.target_channels",
                info.channelConnections(),
                info.hasAeTarget() ? Component.translatable("screen.data_energistics.data_distribution_tower.available") : Component.translatable("screen.data_energistics.data_distribution_tower.unavailable")).getString(), y, 0xD58CFF);
        y += 10;
        String feText = info.hasEnergyTarget() ? formatFeAmount(info.storedFe()) + " / " + formatFeAmount(info.capacityFe()) + " FE" : Component.translatable("screen.data_energistics.data_distribution_tower.unavailable").getString();
        drawPopupLine(guiGraphics, Component.translatable(
                "screen.data_energistics.data_distribution_tower.target_energy",
                feText).getString(), y, 0x9FFFA8);
        y += 10;
        drawPopupLine(guiGraphics, Component.translatable(
                "screen.data_energistics.data_distribution_tower.target_energy_io",
                formatBoolean(info.canExtractFe()),
                formatBoolean(info.canReceiveFe())).getString(), y, 0xA8A8A8);
    }

    private void drawToggleButton(GuiGraphics guiGraphics, String label, boolean enabled, int x, int y, int width, int height, int color) {
        int background = enabled ? 0x552D5E52 : 0x5526282D;
        int border = enabled ? color : 0xFF555B62;
        guiGraphics.fill(x, y, x + width, y + height, background);
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);

        String state = Component.translatable(enabled ? "screen.data_energistics.data_distribution_tower.on" : "screen.data_energistics.data_distribution_tower.off").getString();
        String text = label + ":" + state;
        if (this.font.width(text) > width - 6) {
            text = this.font.plainSubstrByWidth(text, width - 12) + "...";
        }
        guiGraphics.drawString(this.font, text, x + 4, y + 3, enabled ? 0xFFFFFF : 0xA8A8A8, false);
    }

    private void drawPopupLine(GuiGraphics guiGraphics, String line, int y, int color) {
        if (this.font.width(line) > POPUP_WIDTH - 14) {
            line = this.font.plainSubstrByWidth(line, POPUP_WIDTH - 20) + "...";
        }
        guiGraphics.drawString(this.font, line, POPUP_X + 7, y, color, false);
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
        if (localX >= POPUP_X + POPUP_WIDTH - 14 && localX <= POPUP_X + POPUP_WIDTH - 4 && localY >= POPUP_Y + 4 && localY <= POPUP_Y + 16) {
            this.popupTarget = null;
            return true;
        }

        if (isInRect(localX, localY, POPUP_X + 7, POPUP_BUTTON_Y, POPUP_BUTTON_WIDTH, POPUP_BUTTON_HEIGHT)) {
            setPopupTargetMode(popupRow, popupRow.mode().withAe(!popupRow.mode().allowsAe()));
            return true;
        }

        if (isInRect(localX, localY, POPUP_X + 81, POPUP_BUTTON_Y, POPUP_BUTTON_WIDTH, POPUP_BUTTON_HEIGHT)) {
            setPopupTargetMode(popupRow, popupRow.mode().withFe(!popupRow.mode().allowsFe()));
            return true;
        }

        return true;
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
        return isInRect(localX, localY, POPUP_X, POPUP_Y, POPUP_WIDTH, POPUP_HEIGHT);
    }

    private boolean isInRect(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && x <= rectX + width && y >= rectY && y <= rectY + height;
    }

    private String getEmptyStateText() {
        if (!PinyinUtil.normalizeSearch(this.searchQuery).isEmpty()) {
            return Component.translatable(
                    "screen.data_energistics.data_distribution_tower.search_no_match").getString();
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
