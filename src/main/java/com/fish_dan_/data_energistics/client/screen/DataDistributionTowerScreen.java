package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.ConnectionMode;
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
    private AETextField searchBox;
    private String searchQuery = "";

    public DataDistributionTowerScreen(DataDistributionTowerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.rangeVisibleButton = new DataExtractorToggleButton(
                Icon.PATTERN_TERMINAL_ALL,
                Icon.PATTERN_TERMINAL_VISIBLE,
                "button.data_energistics.data_distribution_tower.range_visible",
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
                "screen.data_energistics.data_distribution_tower.ae_channels",
                this.menu.usedChannels,
                this.menu.maxChannels));
        setTextContent("available_fe", Component.translatable(
                "screen.data_energistics.data_distribution_tower.available_fe",
                formatFeAmount(this.menu.availableFe)));
        setTextContent("range", Component.translatable(
                "screen.data_energistics.data_distribution_tower.range",
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasFocused = this.searchBox != null && this.searchBox.isFocused();
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            BoundRow hoveredRow = findHoveredRow(mouseX, mouseY);
            if (hoveredRow != null) {
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
                    RowKind.FE));
        }

        String[] names = this.menu.boundTargets.split("\\n");
        String[] icons = this.menu.boundTargetIcons == null || this.menu.boundTargetIcons.isBlank() ? new String[0] : this.menu.boundTargetIcons.split("\\n");
        String[] metas = this.menu.boundTargetMeta == null || this.menu.boundTargetMeta.isBlank() ? new String[0] : this.menu.boundTargetMeta.split("\\n");
        String[] kinds = this.menu.boundTargetKinds == null || this.menu.boundTargetKinds.isBlank() ? new String[0] : this.menu.boundTargetKinds.split("\\n");

        ArrayList<BoundRow> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            rows.add(new BoundRow(
                    i < icons.length ? toStack(icons[i]) : new ItemStack(Items.BARRIER),
                    names[i],
                    names[i],
                    i < metas.length ? parseMeta(metas[i]) : new TargetRef(Level.OVERWORLD, new net.minecraft.core.BlockPos(0, 0, 0)),
                    i < kinds.length ? parseKind(kinds[i]) : RowKind.FE));
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

    private record BoundRow(ItemStack iconStack, String displayText, String searchIndex, TargetRef target, RowKind kind) {

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
}
