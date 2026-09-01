package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.screen.base.AETextFieldInteraction;
import com.fish_dan_.data_energistics.client.util.PinyinUtil;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

/** Shared physical-provider drill-down panel for native, universal and wireless pattern encoding screens. */
final class PatternProviderLeafPanel {

    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/upload.png");
    private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/gui/sprites/button.png");
    private static final ResourceLocation BUTTON_HIGHLIGHTED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/gui/sprites/button_highlighted.png");
    private static final ResourceLocation BUTTON_DISABLED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/gui/sprites/button_disabled.png");
    private static final ResourceLocation SCROLLBAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "small_scroller");
    private static final ResourceLocation SCROLLBAR_DISABLED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "small_scroller_disabled");
    private static final Component TITLE = Component.translatable(
            "screen.data_energistics.pattern_writer_preview.leaf_panel_title");
    private static final Component EMPTY = Component.translatable(
            "screen.data_energistics.pattern_writer_preview.leaf_empty_state");
    private static final Component SEARCH_HINT = Component.translatable(
            "screen.data_energistics.pattern_writer_preview.leaf_search_hint");
    private static final int WIDTH = 128;
    private static final int HEIGHT = 128;
    private static final int CONTENT_X = 10;
    private static final int CONTENT_RIGHT = 6;
    private static final int CONTENT_BOTTOM = 6;
    private static final int TITLE_Y = 4;
    private static final int SEARCH_X = 34;
    private static final int SEARCH_Y = 6;
    private static final int SEARCH_WIDTH = 58;
    private static final int SEARCH_HEIGHT = 12;
    private static final int CLOSE_X = 96;
    private static final int CLOSE_Y = 2;
    private static final int LIST_Y = 20;
    private static final int ROW_WIDTH = 95;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = -1;
    private static final int VISIBLE_ROWS = 5;
    private static final int SCROLLBAR_X = 114;
    private static final int SCROLLBAR_Y_OFFSET = -3;
    private static final int PANEL_MARGIN = 4;
    private static final int DRAG_RIGHT_PADDING = 4;
    private static final int DRAG_TOP_PADDING = -7;
    private static final int BUTTON_TEXTURE_WIDTH = 200;
    private static final int BUTTON_TEXTURE_HEIGHT = 20;
    private static final int BUTTON_SLICE_BORDER = 4;
    private static final int ICON_SIZE = 16;
    private static final int ICON_X_PADDING = 2;
    private static final int NAME_X_PADDING = 4;
    private static final int COUNT_RIGHT_PADDING = 4;
    private static final float NAME_SCALE = 0.68F;
    private static final float COUNT_SCALE = 0.62F;
    private static final float LOCATION_SCALE = 0.52F;
    private static final int COLOR_TITLE = 0x000000;
    private static final int COLOR_TEXT = 0xE7E7E7;
    private static final int COLOR_LOCATION = 0xB8B8B8;
    private static final int COLOR_EMPTY = 0x000000;
    private static final int COLOR_COUNT = 0x9CD3FF;
    private static final int COLOR_COUNT_WARNING = 0xC83A32;
    private static final int COLOR_BUTTON = 0x6A111111;
    private static final int COLOR_BUTTON_HOVER = 0x88333333;
    private static final int COLOR_BUTTON_SELECTED = 0xAA5F7991;
    private static final int COLOR_BUTTON_BORDER = 0xB0909090;

    private final PatternProviderLeafPanelHost host;
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
    private ObjectList<LeafRow> allRows = ObjectLists.emptyList();
    private ObjectList<LeafRow> visibleRows = ObjectLists.emptyList();
    private PatternEncodingPreviewMenu.SyncedPatternProvider openedGroup;
    private AETextField searchBox;
    private AETextField renameBox;
    private PatternEncodingPreviewDragButton dragButton;
    private LeafPanelCloseButton closeButton;
    private boolean visible;
    private boolean rowsDirty = true;
    private boolean layerWidgetsDeferred;
    private boolean scrollbarDragging;
    private boolean dragging;
    private boolean positioned;
    private boolean suppressRenameKeyChar;
    private long renamingLeafId = -1L;
    private String renamingLeafDigest;
    private int relativeX;
    private int relativeY;
    private int dragOffsetX;
    private int dragOffsetY;

    PatternProviderLeafPanel(PatternProviderLeafPanelHost host) {
        this.host = host;
        this.scrollbar.setCaptureMouseWheel(false);
        this.scrollbar.setRange(0, 0, 1);
    }

    void init() {
        PatternEncodingPreferencesClient.providerDetailPanelPosition().ifPresentOrElse(position -> {
            this.relativeX = position.relativeX();
            this.relativeY = position.relativeY();
            this.positioned = true;
        }, () -> this.positioned = false);
        this.searchBox = new AETextField(
                this.host.leafPanelStyle(), this.host.leafPanelFont(), 0, 0, SEARCH_WIDTH, SEARCH_HEIGHT);
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setPlaceholder(SEARCH_HINT);
        this.searchBox.setResponder(ignored -> {
            this.scrollbar.setCurrentScroll(0);
            this.rowsDirty = true;
        });
        this.host.registerLeafPanelWidget(this.searchBox);

        this.renameBox = new AETextField(
                this.host.leafPanelStyle(), this.host.leafPanelFont(), 0, 0, SEARCH_WIDTH, SEARCH_HEIGHT);
        this.renameBox.setMaxLength(40);
        this.renameBox.setBordered(false);
        this.renameBox.setCanLoseFocus(false);
        this.host.registerLeafPanelWidget(this.renameBox);

        this.dragButton = this.host.registerLeafPanelWidget(new PatternEncodingPreviewDragButton(Component.translatable(
                "screen.data_energistics.pattern_writer_preview.leaf_drag_handle")));
        this.closeButton = this.host.registerLeafPanelWidget(new LeafPanelCloseButton(this::close));
        updateWidgets();
    }

    void open(PatternEncodingPreviewMenu.SyncedPatternProvider group) {
        boolean changedGroup = !this.visible || this.openedGroup.id() != group.id();
        this.openedGroup = group;
        this.visible = true;
        rebuildRows(group);
        if (changedGroup) {
            cancelRename();
            this.searchBox.setValue("");
            this.scrollbar.setCurrentScroll(0);
        }
        if (!this.positioned) {
            placeBesideParent();
        }
        updateWidgets();
    }

    void updateProviderSnapshot() {
        if (!this.visible) {
            return;
        }
        PatternEncodingPreviewMenu.SyncedPatternProvider refreshed = findGroup(this.openedGroup.id());
        if (refreshed == null || refreshed.leaves().size() < 2) {
            close();
            return;
        }
        this.openedGroup = refreshed;
        rebuildRows(refreshed);
        if (isRenaming() && findLeafByDigest(this.renamingLeafDigest) == null) {
            cancelRename();
        }
        updateWidgets();
    }

    void setLayerWidgetsDeferred(boolean deferred) {
        this.layerWidgetsDeferred = deferred;
        updateWidgets();
    }

    void tick() {
        this.suppressRenameKeyChar = false;
        if (this.visible) {
            this.scrollbar.tick();
        }
    }

    void closeForScreenTeardown() {
        close();
    }

    void close() {
        this.visible = false;
        this.openedGroup = null;
        this.allRows = ObjectLists.emptyList();
        this.visibleRows = ObjectLists.emptyList();
        this.dragging = false;
        this.scrollbarDragging = false;
        cancelRename();
    }

    boolean isVisible() {
        return this.visible;
    }

    boolean ownsWidget(AbstractWidget widget) {
        return widget == this.searchBox || widget == this.renameBox ||
                widget == this.dragButton || widget == this.closeButton;
    }

    boolean isOver(double mouseX, double mouseY) {
        if (!this.visible) {
            return false;
        }
        if (getBounds().contains((int) mouseX, (int) mouseY)) {
            return true;
        }
        return isMouseOver(this.dragButton, mouseX, mouseY);
    }

    ObjectList<Rect2i> getInteractiveBounds() {
        if (!this.visible) {
            return ObjectLists.emptyList();
        }
        ObjectArrayList<Rect2i> bounds = new ObjectArrayList<>();
        bounds.add(getBounds());
        bounds.add(new Rect2i(
                this.dragButton.getX(), this.dragButton.getY(),
                this.dragButton.getWidth(), this.dragButton.getHeight()));
        return ObjectLists.unmodifiable(bounds);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) {
            return false;
        }
        if (this.renameBox.visible && AETextFieldInteraction.clearOnRightClick(
                this.renameBox, mouseX, mouseY, button)) {
            return true;
        }
        if (this.searchBox.visible && AETextFieldInteraction.clearOnRightClick(
                this.searchBox, mouseX, mouseY, button)) {
            return true;
        }
        if (this.renameBox.visible && this.renameBox.isMouseOver(mouseX, mouseY)) {
            return this.renameBox.mouseClicked(mouseX, mouseY, button);
        }
        if (this.searchBox.visible && this.searchBox.isMouseOver(mouseX, mouseY)) {
            return this.searchBox.mouseClicked(mouseX, mouseY, button);
        }
        if (isMouseOver(this.closeButton, mouseX, mouseY)) {
            return this.closeButton.mouseClicked(mouseX, mouseY, button);
        }
        if (isMouseOver(this.dragButton, mouseX, mouseY)) {
            if (button == 0) {
                Rect2i bounds = getBounds();
                this.dragging = true;
                this.dragOffsetX = (int) Math.round(mouseX) - bounds.getX();
                this.dragOffsetY = (int) Math.round(mouseY) - bounds.getY();
                return true;
            }
            if (button == 1) {
                this.positioned = false;
                PatternEncodingPreferencesClient.clearProviderDetailPanelPosition();
                updateWidgets();
                return true;
            }
        }
        if (isOverScrollbar(mouseX, mouseY) && this.scrollbar.onMouseDown(
                new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), button)) {
            this.scrollbarDragging = true;
            return true;
        }
        LeafRow row = getRowUnderMouse(mouseX, mouseY);
        if (row == null) {
            return getBounds().contains((int) mouseX, (int) mouseY);
        }
        if (button == 0) {
            if (this.host.leafPanelUploadEnabled()) {
                this.host.leafPanelMenu().data_energistics$transferEncodedPatternToProviderLeaf(
                        this.openedGroup.id(), row.leaf().id());
            }
            return true;
        }
        if (DEKeyMappings.OPEN_PATTERN_PROVIDER.matchesMouse(button)) {
            openLeaf(row);
            return true;
        }
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.visible) {
            return false;
        }
        if (isRenaming()) {
            if (keyCode == 256) {
                cancelRename();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                commitRename();
                return true;
            }
            if (this.renameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        LeafRow hovered = getHoveredRow();
        if (hovered != null) {
            if (this.host.leafPanelRenameEnabled() && hovered.leaf().renameable() &&
                    DEKeyMappings.RENAME_PATTERN_PROVIDER.matches(keyCode, scanCode)) {
                beginRename(hovered);
                this.suppressRenameKeyChar = true;
                return true;
            }
            if (DEKeyMappings.OPEN_PATTERN_PROVIDER.matches(keyCode, scanCode)) {
                openLeaf(hovered);
                return true;
            }
        }
        return this.searchBox.visible && this.searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (!this.visible) {
            return false;
        }
        if (this.suppressRenameKeyChar) {
            this.suppressRenameKeyChar = false;
            return true;
        }
        if (this.renameBox.visible && this.renameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return this.searchBox.visible && this.searchBox.charTyped(codePoint, modifiers);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging) {
            this.dragging = false;
            updateDraggedPosition(mouseX, mouseY);
            PatternEncodingPreferencesClient.setProviderDetailPanelPosition(this.relativeX, this.relativeY);
            return true;
        }
        if (this.scrollbarDragging) {
            this.scrollbar.onMouseUp(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), button);
            this.scrollbarDragging = false;
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (this.dragging && button == 0) {
            updateDraggedPosition(mouseX, mouseY);
            return true;
        }
        if (this.scrollbarDragging) {
            return this.scrollbar.onMouseDrag(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), button);
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.visible || !getListBounds().contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        return this.scrollbar.onMouseWheel(
                new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), scrollY);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        Rect2i bounds = getBounds();
        graphics.blit(PANEL_TEXTURE, bounds.getX(), bounds.getY(), 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        graphics.drawString(this.host.leafPanelFont(), TITLE, bounds.getX() + CONTENT_X,
                bounds.getY() + TITLE_Y, COLOR_TITLE, false);
        drawRows(graphics, mouseX, mouseY);
        drawScrollbar(graphics);
        renderWidget(this.searchBox, graphics, mouseX, mouseY, partialTick);
        renderWidget(this.renameBox, graphics, mouseX, mouseY, partialTick);
        renderWidget(this.closeButton, graphics, mouseX, mouseY, partialTick);
        renderWidget(this.dragButton, graphics, mouseX, mouseY, partialTick);
    }

    void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        LeafRow row = getRowUnderMouse(mouseX, mouseY);
        if (row != null) {
            ObjectArrayList<Component> tooltip = new ObjectArrayList<>();
            tooltip.add(row.leaf().displayName().copy());
            tooltip.add(locationTooltip(row));
            tooltip.add(Component.translatable("screen.data_energistics.pattern_writer_preview.provider.upload"));
            tooltip.add(row.leaf().openable() ? Component.translatable(
                    "screen.data_energistics.pattern_writer_preview.provider.open",
                    DEKeyMappings.OPEN_PATTERN_PROVIDER.getTranslatedKeyMessage()) :
                    Component.translatable(
                            "screen.data_energistics.pattern_writer_preview.leaf_open_unavailable"));
            if (row.leaf().renameable()) {
                tooltip.add(Component.translatable(
                        "screen.data_energistics.pattern_writer_preview.provider.rename",
                        DEKeyMappings.RENAME_PATTERN_PROVIDER.getTranslatedKeyMessage()));
            }
            tooltip.add(Component.translatable(
                    "screen.data_energistics.pattern_writer_preview.provider.slots",
                    row.leaf().usedPatternSlotCount(), row.leaf().patternSlotCount()));
            ObjectArrayList<FormattedCharSequence> formattedTooltip = new ObjectArrayList<>(tooltip.size());
            tooltip.forEach(line -> formattedTooltip.add(line.getVisualOrderText()));
            graphics.renderTooltip(this.host.leafPanelFont(), formattedTooltip, mouseX, mouseY);
            return;
        }
        if (isMouseOver(this.closeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.host.leafPanelFont(), this.closeButton.getMessage(), mouseX, mouseY);
        } else if (isMouseOver(this.dragButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.host.leafPanelFont(), this.dragButton.getMessage(), mouseX, mouseY);
        }
    }

    private void openLeaf(LeafRow row) {
        if (!this.host.leafPanelOpenEnabled()) {
            return;
        }
        if (!row.leaf().openable()) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.leaf_open_unavailable"), false);
            return;
        }
        this.host.leafPanelMenu().data_energistics$openPatternProviderLeafMenu(
                this.openedGroup.id(), row.leaf().id());
    }

    private void beginRename(LeafRow row) {
        this.renamingLeafId = row.leaf().id();
        this.renamingLeafDigest = row.leaf().providerDigest();
        this.searchBox.setFocused(false);
        this.renameBox.setValue(row.leaf().displayName().getString());
        this.renameBox.setFocused(true);
        updateWidgets();
    }

    private void cancelRename() {
        this.renamingLeafId = -1L;
        this.renamingLeafDigest = null;
        this.renameBox.setFocused(false);
        this.renameBox.setValue("");
        updateWidgets();
    }

    private void commitRename() {
        String digest = this.renamingLeafDigest;
        this.host.leafPanelMenu().data_energistics$renamePatternProviderLeaf(
                this.openedGroup.id(), this.renamingLeafId, this.renameBox.getValue());
        close();
        this.host.selectRenamedProviderLeaf(digest);
    }

    private boolean isRenaming() {
        return this.renamingLeafId > 0L;
    }

    private void rebuildRows(PatternEncodingPreviewMenu.SyncedPatternProvider group) {
        ObjectArrayList<LeafRow> rows = new ObjectArrayList<>(group.leaves().size());
        for (int index = 0; index < group.leaves().size(); index++) {
            rows.add(new LeafRow(group.leaves().get(index), index + 1));
        }
        this.allRows = ObjectLists.unmodifiable(rows);
        this.rowsDirty = true;
    }

    private void ensureRows() {
        if (rebuildVisibleRows()) {
            updateScrollbar();
        }
    }

    private boolean matchesSearch(LeafRow row, String normalizedQuery) {
        String name = row.leaf().displayName().getString();
        String iconId = row.leaf().iconItemId().toString();
        String ordinal = "#" + row.ordinal();
        String location = locationSearchText(row);
        String normalizedSource = PinyinUtil.normalizeSearch(name) + PinyinUtil.normalizeSearch(iconId) +
                PinyinUtil.normalizeSearch(ordinal) + PinyinUtil.normalizeSearch(location);
        if (normalizedSource.contains(normalizedQuery)) {
            return true;
        }
        return PinyinUtil.matchesNormalizedJech(name, normalizedQuery) ||
                PinyinUtil.matchesNormalizedJech(iconId, normalizedQuery) ||
                PinyinUtil.matchesNormalizedJech(ordinal, normalizedQuery) ||
                PinyinUtil.matchesNormalizedJech(location, normalizedQuery);
    }

    private String locationSearchText(LeafRow row) {
        PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation location = row.leaf().location();
        if (location.kind() == PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocationKind.UNLOCATED) {
            return "unlocated #" + row.ordinal();
        }
        BlockPos position = location.blockPos();
        String mount = location.kind() == PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocationKind.PART ?
                location.mountedSide() == null ? "center" : location.mountedSide().getName() : "block";
        return location.dimensionId() + " " + position.getX() + " " + position.getY() + " " + position.getZ() +
                " " + mount;
    }

    private String locationSummary(LeafRow row) {
        PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation location = row.leaf().location();
        if (location.kind() == PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocationKind.UNLOCATED) {
            return Component.translatable(
                    "screen.data_energistics.pattern_writer_preview.leaf_location.unlocated", row.ordinal()).getString();
        }
        BlockPos position = location.blockPos();
        String summary = location.dimensionId() + " · " + position.getX() + " " + position.getY() + " " + position.getZ();
        if (location.kind() == PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocationKind.PART) {
            summary += " · " + mountName(location.mountedSide()).getString();
        }
        return summary;
    }

    private Component locationTooltip(LeafRow row) {
        PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocation location = row.leaf().location();
        if (location.kind() == PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocationKind.UNLOCATED) {
            return Component.translatable(
                    "screen.data_energistics.pattern_writer_preview.leaf_location.unlocated", row.ordinal());
        }
        BlockPos position = location.blockPos();
        Component base = Component.translatable(
                "screen.data_energistics.pattern_writer_preview.leaf_location.block",
                location.dimensionId(), position.getX(), position.getY(), position.getZ());
        return location.kind() == PatternEncodingPreviewMenu.SyncedPatternProviderLeafLocationKind.PART ?
                Component.translatable(
                        "screen.data_energistics.pattern_writer_preview.leaf_location.part", base,
                        mountName(location.mountedSide())) :
                base;
    }

    private static Component mountName(@Nullable Direction side) {
        return side == null ? Component.translatable(
                "screen.data_energistics.pattern_writer_preview.leaf_mount.center") :
                Component.translatable(
                        "screen.data_energistics.pattern_writer_preview.leaf_mount." + side.getName());
    }

    private void drawRows(GuiGraphics graphics, int mouseX, int mouseY) {
        ensureRows();
        if (this.visibleRows.isEmpty()) {
            drawScaledText(graphics, EMPTY.getString(), getBounds().getX() + CONTENT_X,
                    getBounds().getY() + LIST_Y + 7, COLOR_EMPTY, NAME_SCALE);
            return;
        }
        int start = this.scrollbar.getCurrentScroll();
        int end = Math.min(this.visibleRows.size(), start + VISIBLE_ROWS);
        for (int index = start; index < end; index++) {
            LeafRow row = this.visibleRows.get(index);
            Rect2i bounds = getRowBounds(index - start);
            drawRowBackground(graphics, bounds, row.leaf().id() == this.renamingLeafId,
                    bounds.contains(mouseX, mouseY));
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(row.leaf().iconItemId()));
            int nameX = bounds.getX() + NAME_X_PADDING;
            if (!icon.isEmpty()) {
                int iconX = bounds.getX() + ICON_X_PADDING;
                int iconY = bounds.getY() + (ROW_HEIGHT - ICON_SIZE) / 2;
                graphics.renderItem(icon, iconX, iconY);
                nameX = iconX + ICON_SIZE + 2;
            }
            String count = row.leaf().usedPatternSlotCount() + "/" + row.leaf().patternSlotCount();
            int countWidth = scaledTextWidth(count, COUNT_SCALE);
            int nameWidth = bounds.getX() + bounds.getWidth() - COUNT_RIGHT_PADDING - countWidth - 3 - nameX;
            drawScaledText(graphics, trim(row.leaf().displayName().getString(), nameWidth, NAME_SCALE),
                    nameX, bounds.getY() + 2, COLOR_TEXT, NAME_SCALE);
            drawScaledText(graphics, count,
                    bounds.getX() + bounds.getWidth() - COUNT_RIGHT_PADDING - countWidth,
                    bounds.getY() + 2, countColor(row.leaf()), COUNT_SCALE);
            drawScaledText(graphics, trim(locationSummary(row), bounds.getWidth() - (nameX - bounds.getX()) - 3,
                    LOCATION_SCALE), nameX, bounds.getY() + 11, COLOR_LOCATION, LOCATION_SCALE);
        }
    }

    private void drawRowBackground(GuiGraphics graphics, Rect2i bounds, boolean selected, boolean hovered) {
        if (this.openedGroup.useAeButtonStyle()) {
            ResourceLocation texture = selected ? BUTTON_DISABLED_TEXTURE : hovered ?
                    BUTTON_HIGHLIGHTED_TEXTURE : BUTTON_TEXTURE;
            drawNineSlice(graphics, texture, bounds);
            return;
        }
        graphics.fill(bounds.getX(), bounds.getY(), bounds.getX() + bounds.getWidth(),
                bounds.getY() + bounds.getHeight(), selected ? COLOR_BUTTON_SELECTED : hovered ?
                        COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.renderOutline(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), COLOR_BUTTON_BORDER);
    }

    private void drawNineSlice(GuiGraphics graphics, ResourceLocation texture, Rect2i bounds) {
        int border = BUTTON_SLICE_BORDER;
        int centerWidth = bounds.getWidth() - border * 2;
        int centerHeight = bounds.getHeight() - border * 2;
        graphics.blit(texture, bounds.getX(), bounds.getY(), 0, 0, 0, border, border,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX() + bounds.getWidth() - border, bounds.getY(), 0,
                BUTTON_TEXTURE_WIDTH - border, 0, border, border, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX(), bounds.getY() + bounds.getHeight() - border, 0,
                0, BUTTON_TEXTURE_HEIGHT - border, border, border, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX() + bounds.getWidth() - border,
                bounds.getY() + bounds.getHeight() - border, 0,
                BUTTON_TEXTURE_WIDTH - border, BUTTON_TEXTURE_HEIGHT - border, border, border,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX() + border, bounds.getY(), 0,
                border, 0, centerWidth, border, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX() + border, bounds.getY() + bounds.getHeight() - border, 0,
                border, BUTTON_TEXTURE_HEIGHT - border, centerWidth, border,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX(), bounds.getY() + border, 0,
                0, border, border, centerHeight, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX() + bounds.getWidth() - border, bounds.getY() + border, 0,
                BUTTON_TEXTURE_WIDTH - border, border, border, centerHeight,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        graphics.blit(texture, bounds.getX() + border, bounds.getY() + border, 0,
                border, border, centerWidth, centerHeight, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (!this.scrollbar.isVisible()) {
            return;
        }
        Rect2i bounds = getScrollbarBounds();
        int range = hiddenRows();
        int handleOffset = range == 0 ? 0 : this.scrollbar.getCurrentScroll() *
                (bounds.getHeight() - Scrollbar.SMALL.handleHeight()) / range;
        Blitter.guiSprite(range == 0 ? SCROLLBAR_DISABLED_TEXTURE : SCROLLBAR_TEXTURE)
                .dest(bounds.getX(), bounds.getY() + handleOffset)
                .blit(graphics);
    }

    private void updateWidgets() {
        Rect2i bounds = getBounds();
        boolean widgetsVisible = this.visible && !this.layerWidgetsDeferred;
        boolean searchVisible = widgetsVisible && !isRenaming();
        this.searchBox.setX(bounds.getX() + SEARCH_X);
        this.searchBox.setY(bounds.getY() + SEARCH_Y);
        this.searchBox.setWidth(SEARCH_WIDTH);
        this.searchBox.setHeight(SEARCH_HEIGHT);
        this.searchBox.setVisible(searchVisible);
        this.searchBox.active = searchVisible;
        if (!searchVisible && !this.layerWidgetsDeferred) {
            this.searchBox.setFocused(false);
        }
        this.renameBox.setX(bounds.getX() + SEARCH_X);
        this.renameBox.setY(bounds.getY() + SEARCH_Y);
        this.renameBox.setWidth(SEARCH_WIDTH);
        this.renameBox.setHeight(SEARCH_HEIGHT);
        this.renameBox.setVisible(widgetsVisible && isRenaming());
        this.renameBox.active = widgetsVisible && isRenaming();
        this.closeButton.setX(bounds.getX() + CLOSE_X);
        this.closeButton.setY(bounds.getY() + CLOSE_Y);
        this.closeButton.visible = widgetsVisible;
        this.closeButton.active = widgetsVisible;
        this.dragButton.setX(bounds.getX() + bounds.getWidth() - this.dragButton.getWidth() - DRAG_RIGHT_PADDING);
        this.dragButton.setY(bounds.getY() + DRAG_TOP_PADDING);
        this.dragButton.setVisibility(widgetsVisible);
        updateScrollbar();
    }

    private void updateScrollbar() {
        Rect2i bounds = getScrollbarBounds();
        int hidden = hiddenRows();
        this.scrollbar.setPosition(new Point(bounds.getX(), bounds.getY()));
        this.scrollbar.setHeight(bounds.getHeight());
        this.scrollbar.setSize(bounds.getWidth(), bounds.getHeight());
        this.scrollbar.setRange(0, hidden, 1);
        this.scrollbar.setCurrentScroll(Math.min(this.scrollbar.getCurrentScroll(), hidden));
        this.scrollbar.setVisible(this.visible && !this.layerWidgetsDeferred && hidden > 0);
    }

    private int hiddenRows() {
        rebuildVisibleRows();
        return Math.max(0, this.visibleRows.size() - VISIBLE_ROWS);
    }

    private boolean rebuildVisibleRows() {
        if (!this.rowsDirty) {
            return false;
        }
        String query = PinyinUtil.normalizeSearch(this.searchBox.getValue());
        if (query.isEmpty()) {
            this.visibleRows = this.allRows;
        } else {
            ObjectArrayList<LeafRow> filtered = new ObjectArrayList<>();
            this.allRows.forEach(row -> {
                if (matchesSearch(row, query)) {
                    filtered.add(row);
                }
            });
            this.visibleRows = ObjectLists.unmodifiable(filtered);
        }
        this.rowsDirty = false;
        return true;
    }

    private PatternEncodingPreviewMenu.@Nullable SyncedPatternProvider findGroup(long groupId) {
        for (PatternEncodingPreviewMenu.SyncedPatternProvider group : this.host.leafPanelMenu().data_energistics$getSyncedPatternProviders()) {
            if (group.id() == groupId) {
                return group;
            }
        }
        return null;
    }

    private PatternEncodingPreviewMenu.@Nullable SyncedPatternProviderLeaf findLeafByDigest(String digest) {
        for (PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf : this.openedGroup.leaves()) {
            if (leaf.providerDigest().equals(digest)) {
                return leaf;
            }
        }
        return null;
    }

    private @Nullable LeafRow getHoveredRow() {
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos() * this.host.leafPanelScreenWidth() /
                minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * this.host.leafPanelScreenHeight() /
                minecraft.getWindow().getScreenHeight();
        return getRowUnderMouse(mouseX, mouseY);
    }

    private @Nullable LeafRow getRowUnderMouse(double mouseX, double mouseY) {
        if (!this.visible || !getListBounds().contains((int) mouseX, (int) mouseY)) {
            return null;
        }
        ensureRows();
        int start = this.scrollbar.getCurrentScroll();
        int end = Math.min(this.visibleRows.size(), start + VISIBLE_ROWS);
        for (int index = start; index < end; index++) {
            if (getRowBounds(index - start).contains((int) mouseX, (int) mouseY)) {
                return this.visibleRows.get(index);
            }
        }
        return null;
    }

    private Rect2i getBounds() {
        if (!this.positioned) {
            return defaultBounds();
        }
        return clamp(this.host.leafPanelGuiLeft() + this.relativeX,
                this.host.leafPanelGuiTop() + this.relativeY);
    }

    private Rect2i defaultBounds() {
        Rect2i parent = this.host.leafPanelParentBounds();
        return PatternEncodingPreviewPlacement.findBestBounds(
                parent, WIDTH, HEIGHT, parent.getY(), PANEL_MARGIN, -PANEL_MARGIN, PANEL_MARGIN,
                this.host.leafPanelScreenWidth(), this.host.leafPanelScreenHeight(),
                this.host.leafPanelOccupiedZones());
    }

    private void placeBesideParent() {
        Rect2i bounds = defaultBounds();
        this.relativeX = bounds.getX() - this.host.leafPanelGuiLeft();
        this.relativeY = bounds.getY() - this.host.leafPanelGuiTop();
        this.positioned = true;
    }

    private void updateDraggedPosition(double mouseX, double mouseY) {
        Rect2i bounds = clamp((int) Math.round(mouseX) - this.dragOffsetX,
                (int) Math.round(mouseY) - this.dragOffsetY);
        this.relativeX = bounds.getX() - this.host.leafPanelGuiLeft();
        this.relativeY = bounds.getY() - this.host.leafPanelGuiTop();
        this.positioned = true;
        updateWidgets();
    }

    private Rect2i clamp(int x, int y) {
        return new Rect2i(
                Math.max(PANEL_MARGIN, Math.min(x, this.host.leafPanelScreenWidth() - WIDTH - PANEL_MARGIN)),
                Math.max(PANEL_MARGIN, Math.min(y, this.host.leafPanelScreenHeight() - HEIGHT - PANEL_MARGIN)),
                WIDTH,
                HEIGHT);
    }

    private Rect2i getListBounds() {
        Rect2i bounds = getBounds();
        return new Rect2i(bounds.getX() + CONTENT_X, bounds.getY() + LIST_Y,
                bounds.getWidth() - CONTENT_X - CONTENT_RIGHT, bounds.getHeight() - LIST_Y - CONTENT_BOTTOM);
    }

    private Rect2i getRowBounds(int visibleRow) {
        Rect2i list = getListBounds();
        return new Rect2i(list.getX(), list.getY() + visibleRow * (ROW_HEIGHT + ROW_GAP), ROW_WIDTH, ROW_HEIGHT);
    }

    private Rect2i getScrollbarBounds() {
        Rect2i list = getListBounds();
        return new Rect2i(getBounds().getX() + SCROLLBAR_X, Math.max(4, list.getY() - 1 + SCROLLBAR_Y_OFFSET),
                this.scrollbar.getBounds().getWidth(), Math.max(1, list.getHeight() + 2));
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return this.scrollbar.getBounds().contains((int) mouseX, (int) mouseY);
    }

    private int countColor(PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf) {
        int remaining = leaf.patternSlotCount() - leaf.usedPatternSlotCount();
        return remaining * 9 < leaf.patternSlotCount() * 2 ? COLOR_COUNT_WARNING : COLOR_COUNT;
    }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y, int color, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(this.host.leafPanelFont(), text, Math.round(x / scale), Math.round(y / scale), color, false);
        pose.popPose();
    }

    private int scaledTextWidth(String text, float scale) {
        return (int) Math.ceil(this.host.leafPanelFont().width(text) * scale);
    }

    private String trim(String text, int maxWidth, float scale) {
        if (scaledTextWidth(text, scale) <= maxWidth) {
            return text;
        }
        int ellipsis = scaledTextWidth("...", scale);
        int rawLimit = Math.max(0, (int) Math.floor((maxWidth - ellipsis) / scale));
        return this.host.leafPanelFont().plainSubstrByWidth(text, rawLimit) + "...";
    }

    private static void renderWidget(
                                     AbstractWidget widget,
                                     GuiGraphics graphics,
                                     int mouseX,
                                     int mouseY,
                                     float partialTick) {
        if (widget.visible) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static boolean isMouseOver(AbstractWidget widget, double mouseX, double mouseY) {
        return widget.visible && widget.isMouseOver(mouseX, mouseY);
    }

    private record LeafRow(PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf, int ordinal) {}

    private static final class LeafPanelCloseButton extends IconButton {

        private LeafPanelCloseButton(Runnable close) {
            super(button -> close.run());
            setMessage(Component.translatable("screen.data_energistics.pattern_writer_preview.leaf_close"));
        }

        @Override
        protected Icon getIcon() {
            return Icon.CLEAR;
        }
    }
}
