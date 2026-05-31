package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.ModKeyMappings;
import com.fish_dan_.data_energistics.client.widget.PatternSourceToggleButton;
import com.fish_dan_.data_energistics.menu.common.BlankPatternProxyMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.definitions.AEItems;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ReadableNumberConverter;
import com.mojang.blaze3d.vertex.PoseStack;
import de.mari_023.ae2wtlib.wet.WETMenu;
import de.mari_023.ae2wtlib.wet.WETScreen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WirelessPatternEncodingTermScreen extends WETScreen {

    private static final Field WIDGET_CONTAINER_WIDGETS_FIELD = resolveField(WidgetContainer.class, "widgets");
    private static final ResourceLocation AE2_UPLOAD_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/upload.png");
    private static final ResourceLocation AE2_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/gui/sprites/button.png");
    private static final ResourceLocation AE2_BUTTON_HIGHLIGHTED_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/gui/sprites/button_highlighted.png");
    private static final ResourceLocation AE2_BUTTON_DISABLED_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/gui/sprites/button_disabled.png");
    private static final Component PANEL_TITLE = Component.translatable("screen.data_energistics.pattern_writer_preview.panel_title");
    private static final Component EMPTY_STATE_TEXT = Component.translatable("screen.data_energistics.pattern_writer_preview.empty_state");
    private static final Component ENCODE_BUTTON_HINT = Component.translatable("screen.data_energistics.pattern_writer_preview.encode_button_hint");
    private static final int PANEL_WIDTH = 128;
    private static final int PANEL_HEIGHT = 128;
    private static final int PANEL_TEXTURE_WIDTH = 128;
    private static final int PANEL_TEXTURE_HEIGHT = 128;
    private static final int BUTTON_TEXTURE_WIDTH = 200;
    private static final int BUTTON_TEXTURE_HEIGHT = 20;
    private static final int BUTTON_SLICE_BORDER = 4;
    private static final int PANEL_TITLE_COLOR = 0x000000;
    private static final int PANEL_EMPTY_TEXT_COLOR = 0x000000;
    private static final int PANEL_TEXT_COLOR = 0xE7E7E7;
    private static final int PANEL_COUNT_NORMAL_COLOR = 0x9CD3FF;
    private static final int PANEL_COUNT_WARNING_COLOR = 0xC83A32;
    private static final int PANEL_BUTTON_COLOR = 0x6A111111;
    private static final int PANEL_BUTTON_HOVER_COLOR = 0x88333333;
    private static final int PANEL_BUTTON_SELECTED_COLOR = 0xAA5F7991;
    private static final int PANEL_BUTTON_BORDER_COLOR = 0xB0909090;
    private static final int PANEL_X_OFFSET = 0;
    private static final int PANEL_Y_OFFSET = 105;
    private static final int PANEL_SCROLLBAR_X = 309;
    private static final int PANEL_SCROLLBAR_Y = 119;
    private static final int PANEL_SCROLLBAR_HEIGHT = 104;
    private static final int PANEL_TOOLTIP_X = 190;
    private static final int PANEL_TOOLTIP_Y = 60;
    private static final int PANEL_CONTENT_X = 10;
    private static final int PANEL_CONTENT_RIGHT = 6;
    private static final int PANEL_CONTENT_BOTTOM = 6;
    private static final int PANEL_TITLE_Y = 4;
    private static final int PANEL_SEARCH_X = 42;
    private static final int PANEL_SEARCH_Y = 6;
    private static final int PANEL_SEARCH_WIDTH = 70;
    private static final int PANEL_SEARCH_HEIGHT = 12;
    private static final int PROVIDER_LIST_Y = 20;
    private static final int PROVIDER_VISIBLE_ROWS = 5;
    private static final int PROVIDER_BUTTON_WIDTH = 95;
    private static final int PROVIDER_BUTTON_HEIGHT = 20;
    private static final int PROVIDER_BUTTON_GAP = -1;
    private static final int PROVIDER_ICON_SIZE = 16;
    private static final int PROVIDER_ICON_X_PADDING = 2;
    private static final int PROVIDER_NAME_X_PADDING = 4;
    private static final int PROVIDER_COUNT_RIGHT_PADDING = 4;
    private static final int PROVIDER_TEXT_Y_OFFSET = 5;
    private static final float PROVIDER_TEXT_SCALE = 0.75F;
    private static final float PROVIDER_COUNT_TEXT_SCALE = 0.68F;

    private final Scrollbar previewScrollbar = new Scrollbar(Scrollbar.SMALL);
    private boolean previewVisible;
    private boolean previewScrollbarDragging;
    private long selectedPatternProviderId = -1L;
    private long renamingProviderId = -1L;
    private ResourceLocation lastLocatedWorkstationId;
    private AbstractWidget encodePatternWidget;
    private AETextField providerSearchBox;
    private AETextField providerRenameBox;
    private PatternSourceToggleButton patternSourceToggleButton;
    private List<PatternEncodingPreviewMenu.SyncedPatternProvider> cachedVisibleProviders = List.of();
    private boolean visibleProvidersCacheDirty = true;

    public WirelessPatternEncodingTermScreen(WETMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.previewScrollbar.setCaptureMouseWheel(false);
        this.previewScrollbar.setRange(0, 0, 1);
    }

    @Override
    public void init() {
        super.init();
        this.encodePatternWidget = resolveEncodePatternWidget();
        applyEncodeButtonHint();
        initProviderSearchBox();
        initProviderRenameBox();
        initPatternSourceToggleButton();
        invalidateVisibleProvidersCache();
        updateProviderSearchBox();
        updateProviderRenameBox();
        updatePatternSourceToggleButton();
        updatePreviewScrollbar();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateProviderSearchBox();
        updateProviderRenameBox();
        updatePatternSourceToggleButton();
        invalidateVisibleProvidersCache();
        syncProviderSelection();
        updatePreviewScrollbar();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.previewVisible) {
            this.previewScrollbar.tick();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleBlankPatternSlotClick(mouseX, mouseY, button)) {
            return true;
        }

        if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button) && triggerBlankPatternAutoCraft(mouseX, mouseY)) {
            return true;
        }

        if (isRenamingProvider() && this.providerRenameBox != null && this.providerRenameBox.isMouseOver(mouseX, mouseY)) {
            return this.providerRenameBox.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0 && isOverEncodeButton(mouseX, mouseY)) {
            if (hasShiftDown()) {
                this.previewVisible = false;
                return true;
            }

            this.previewVisible = true;
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            return handled || isOverEncodeButton(mouseX, mouseY);
        }

        if (button == 1 && isOverEncodeButton(mouseX, mouseY)) {
            if (this.previewVisible) {
                if (hasShiftDown()) {
                    this.previewVisible = false;
                } else {
                    this.menu.encode();
                }
            } else {
                this.previewVisible = true;
                this.menu.encode();
            }
            return true;
        }

        if (this.previewVisible && isOverPreviewScrollbar(mouseX, mouseY)) {
            boolean handled = this.previewScrollbar.onMouseDown(
                    new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), button);
            if (handled) {
                this.previewScrollbarDragging = true;
                return true;
            }
        }

        if (this.previewVisible && button == 0) {
            var hit = getProviderButtonHit(mouseX, mouseY);
            if (hit != null) {
                if (isRenamingProvider() && this.renamingProviderId != hit.provider().id()) {
                    cancelProviderRename();
                }
                this.selectedPatternProviderId = hit.provider().id();
                previewBridge().data_energistics$transferEncodedPatternToProvider(hit.provider().id());
                return true;
            }
        }

        if (this.previewVisible && ModKeyMappings.OPEN_PATTERN_PROVIDER.matchesMouse(button)) {
            var hit = getProviderButtonHit(mouseX, mouseY);
            if (hit != null) {
                if (isRenamingProvider() && this.renamingProviderId != hit.provider().id()) {
                    cancelProviderRename();
                }
                this.selectedPatternProviderId = hit.provider().id();
                previewBridge().data_energistics$openPatternProviderMenu(hit.provider().id());
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isRenamingProvider()) {
            if (keyCode == 256) {
                cancelProviderRename();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                commitProviderRename();
                return true;
            }
            if (this.providerRenameBox != null && this.providerRenameBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (this.previewVisible && ModKeyMappings.RENAME_PATTERN_PROVIDER.matches(keyCode, scanCode)) {
            var hit = getProviderButtonHit(getMouseGuiX(), getMouseGuiY());
            if (hit != null && hit.provider().renameable()) {
                beginProviderRename(hit.provider());
                return true;
            }
        }

        if (this.previewVisible && ModKeyMappings.OPEN_PATTERN_PROVIDER.matches(keyCode, scanCode)) {
            var hit = getProviderButtonHit(getMouseGuiX(), getMouseGuiY());
            if (hit != null) {
                if (isRenamingProvider() && this.renamingProviderId != hit.provider().id()) {
                    cancelProviderRename();
                }
                this.selectedPatternProviderId = hit.provider().id();
                previewBridge().data_energistics$openPatternProviderMenu(hit.provider().id());
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isRenamingProvider() && this.providerRenameBox != null && this.providerRenameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.previewScrollbarDragging) {
            this.previewScrollbar.onMouseUp(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), button);
            this.previewScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        if (this.previewVisible && this.previewScrollbarDragging && this.previewScrollbar.onMouseDrag(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), mouseButton)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.previewVisible && (isOverPreviewScrollbar(mouseX, mouseY) || isOverProviderList(mouseX, mouseY)) && this.previewScrollbar.onMouseWheel(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        if (!this.previewVisible) {
            return;
        }

        Rect2i previewBounds = getPreviewPanelBounds();
        guiGraphics.blit(AE2_UPLOAD_TEXTURE,
                previewBounds.getX(), previewBounds.getY(),
                0,
                0, 0,
                previewBounds.getWidth(), previewBounds.getHeight(),
                PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT);
        drawProviderButtons(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        if (!this.previewVisible) {
            return;
        }

        this.previewScrollbar.drawForegroundLayer(guiGraphics, getPreviewPanelBounds(), new Point(mouseX, mouseY));
        renderProviderTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN) {
            super.renderSlot(guiGraphics, slot);
            return;
        }

        GridInventoryEntry blankPatternEntry = findBlankPatternEntry();
        long networkStored = blankPatternEntry != null ? blankPatternEntry.getStoredAmount() : 0;
        boolean networkCraftable = blankPatternEntry != null && (blankPatternEntry.isCraftable() || blankPatternEntry.getRequestableAmount() > 0);
        int localBlankPatternCount = AEItems.BLANK_PATTERN.is(slot.getItem()) ? slot.getItem().getCount() : 0;
        long displayedCount = networkStored + localBlankPatternCount;
        boolean hasBlankPatterns = displayedCount > 0;

        if (slot.getItem().isEmpty() && !hasBlankPatterns) {
            Icon.BACKGROUND_ENCODED_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        } else {
            ItemStack displayStack = slot.getItem().isEmpty() ? AEItems.BLANK_PATTERN.stack() : slot.getItem().copyWithCount(1);
            guiGraphics.renderItem(displayStack, slot.x, slot.y);
            guiGraphics.renderItemDecorations(this.font, displayStack, slot.x, slot.y, "");
        }

        if (displayedCount > 0) {
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, slot.x, slot.y,
                    ReadableNumberConverter.format(displayedCount, 4));
        }

        if (networkCraftable) {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 100.0F);
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, (float) (slot.x - 11), (float) (slot.y - 11),
                    "+", false);
            poseStack.popPose();
        }
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        List<Rect2i> zones = new ArrayList<>(super.getExclusionZones());
        if (this.previewVisible) {
            zones.addAll(getPreviewInteractiveBounds());
        }
        return zones;
    }

    private List<Rect2i> getPreviewInteractiveBounds() {
        List<Rect2i> zones = new ArrayList<>();
        zones.add(getProviderListBounds());
        zones.add(this.previewScrollbar.getBounds());

        if (this.providerSearchBox != null && this.providerSearchBox.isVisible()) {
            zones.add(new Rect2i(
                    this.providerSearchBox.getX(),
                    this.providerSearchBox.getY(),
                    this.providerSearchBox.getWidth(),
                    this.providerSearchBox.getHeight()));
        }

        if (this.providerRenameBox != null && this.providerRenameBox.isVisible()) {
            zones.add(new Rect2i(
                    this.providerRenameBox.getX(),
                    this.providerRenameBox.getY(),
                    this.providerRenameBox.getWidth(),
                    this.providerRenameBox.getHeight()));
        }

        return zones;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
    }

    private PatternEncodingPreviewMenu previewBridge() {
        if (this.menu instanceof PatternEncodingPreviewMenu bridge) {
            return bridge;
        }
        throw new IllegalStateException("Pattern encoding menu does not implement preview bridge: " + this.menu.getClass().getName());
    }

    private void drawProviderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Rect2i previewBounds = getPreviewPanelBounds();
        guiGraphics.drawString(this.font, PANEL_TITLE,
                previewBounds.getX() + PANEL_CONTENT_X,
                previewBounds.getY() + PANEL_TITLE_Y,
                PANEL_TITLE_COLOR,
                false);

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = getVisibleProviders();
        if (providers.isEmpty()) {
            drawScaledText(guiGraphics, EMPTY_STATE_TEXT.getString(),
                    previewBounds.getX() + PANEL_CONTENT_X,
                    previewBounds.getY() + PROVIDER_LIST_Y + 2 + PROVIDER_TEXT_Y_OFFSET,
                    PANEL_EMPTY_TEXT_COLOR,
                    PROVIDER_TEXT_SCALE);
            return;
        }

        int start = this.previewScrollbar.getCurrentScroll();
        int end = Math.min(providers.size(), start + PROVIDER_VISIBLE_ROWS);
        for (int rowIndex = start; rowIndex < end; rowIndex++) {
            var provider = providers.get(rowIndex);
            int visibleRow = rowIndex - start;
            Rect2i bounds = getProviderButtonBounds(visibleRow);
            boolean hovered = bounds.contains(mouseX, mouseY);
            boolean selected = provider.id() == this.selectedPatternProviderId;

            drawProviderButtonBackground(guiGraphics, bounds, provider, selected, hovered);

            ItemStack iconStack = getProviderIconStack(provider);
            int nameStartX = bounds.getX() + PROVIDER_NAME_X_PADDING;
            if (!iconStack.isEmpty()) {
                int iconX = bounds.getX() + PROVIDER_ICON_X_PADDING;
                int iconY = bounds.getY() + (bounds.getHeight() - PROVIDER_ICON_SIZE) / 2;
                guiGraphics.renderItem(iconStack, iconX, iconY);
                nameStartX = iconX + PROVIDER_ICON_SIZE + 2;
            }

            String countText = provider.usedPatternSlotCount() + "/" + provider.patternSlotCount();
            int countWidth = getScaledTextWidth(countText, PROVIDER_COUNT_TEXT_SCALE);
            int maxNameWidth = bounds.getX() + bounds.getWidth() - PROVIDER_COUNT_RIGHT_PADDING - countWidth - 4 - nameStartX;
            String providerName = trimToWidth(provider.displayName().getString(), Math.max(10, maxNameWidth), PROVIDER_TEXT_SCALE);

            drawScaledText(guiGraphics, providerName, nameStartX, bounds.getY() + 2 + PROVIDER_TEXT_Y_OFFSET, PANEL_TEXT_COLOR, PROVIDER_TEXT_SCALE);
            drawScaledText(guiGraphics, countText,
                    bounds.getX() + bounds.getWidth() - PROVIDER_COUNT_RIGHT_PADDING - countWidth,
                    bounds.getY() + 2 + PROVIDER_TEXT_Y_OFFSET,
                    getProviderCountColor(provider),
                    PROVIDER_COUNT_TEXT_SCALE);
        }
    }

    private void renderProviderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var hit = getProviderButtonHit(mouseX, mouseY);
        if (hit == null) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(hit.provider().displayName().copy());
        tooltip.add(Component.translatable("screen.data_energistics.pattern_writer_preview.provider.upload"));
        tooltip.add(getProviderOpenHint());
        if (hit.provider().renameable()) {
            tooltip.add(getProviderRenameHint());
        }
        tooltip.add(Component.translatable(
                "screen.data_energistics.pattern_writer_preview.provider.slots",
                hit.provider().usedPatternSlotCount(),
                hit.provider().patternSlotCount()));
        guiGraphics.renderTooltip(this.font,
                tooltip.stream().map(Component::getVisualOrderText).toList(),
                PANEL_TOOLTIP_X, PANEL_TOOLTIP_Y);
    }

    private Component getProviderRenameHint() {
        return Component.translatable(
                "screen.data_energistics.pattern_writer_preview.provider.rename",
                ModKeyMappings.RENAME_PATTERN_PROVIDER.getTranslatedKeyMessage());
    }

    private Component getProviderOpenHint() {
        return Component.translatable(
                "screen.data_energistics.pattern_writer_preview.provider.open",
                ModKeyMappings.OPEN_PATTERN_PROVIDER.getTranslatedKeyMessage());
    }

    private void initProviderSearchBox() {
        this.providerSearchBox = new AETextField(this.getStyle(), this.font, 0, 0, PANEL_SEARCH_WIDTH, PANEL_SEARCH_HEIGHT);
        this.providerSearchBox.setMaxLength(40);
        this.providerSearchBox.setBordered(false);
        this.providerSearchBox.setVisible(false);
        this.providerSearchBox.setCanLoseFocus(true);
        this.providerSearchBox.setPlaceholder(
                Component.translatable("screen.data_energistics.pattern_writer_preview.search_hint"));
        this.providerSearchBox.setResponder(value -> {
            this.previewScrollbar.setCurrentScroll(0);
            invalidateVisibleProvidersCache();
        });
        this.addRenderableWidget(this.providerSearchBox);
    }

    private void initProviderRenameBox() {
        String currentText = this.providerRenameBox != null ? this.providerRenameBox.getValue() : "";
        this.providerRenameBox = new AETextField(this.getStyle(), this.font, 0, 0, PANEL_SEARCH_WIDTH, PANEL_SEARCH_HEIGHT);
        this.providerRenameBox.setMaxLength(40);
        this.providerRenameBox.setBordered(false);
        this.providerRenameBox.setVisible(false);
        this.providerRenameBox.setCanLoseFocus(false);
        this.providerRenameBox.setValue(currentText);
        this.addRenderableWidget(this.providerRenameBox);
    }

    private void updateProviderSearchBox() {
        if (this.providerSearchBox == null) {
            return;
        }
        Rect2i previewBounds = getPreviewPanelBounds();
        this.providerSearchBox.setX(previewBounds.getX() + PANEL_SEARCH_X);
        this.providerSearchBox.setY(previewBounds.getY() + PANEL_SEARCH_Y);
        boolean visible = this.previewVisible && !isRenamingProvider();
        this.providerSearchBox.setVisible(visible);
        this.providerSearchBox.active = visible;
        if (!visible) {
            this.providerSearchBox.setFocused(false);
        }
    }

    private void updateProviderRenameBox() {
        if (this.providerRenameBox == null) {
            return;
        }

        var provider = getPatternProvider(this.renamingProviderId);
        boolean visible = this.previewVisible && provider != null && provider.renameable();
        this.providerRenameBox.setVisible(visible);
        this.providerRenameBox.active = visible;
        if (!visible) {
            this.providerRenameBox.setFocused(false);
            return;
        }

        Rect2i previewBounds = getPreviewPanelBounds();
        this.providerRenameBox.setX(previewBounds.getX() + PANEL_SEARCH_X);
        this.providerRenameBox.setY(previewBounds.getY() + PANEL_SEARCH_Y);
        this.providerRenameBox.setWidth(PANEL_SEARCH_WIDTH);
        this.providerRenameBox.setHeight(PANEL_SEARCH_HEIGHT);
    }

    private void initPatternSourceToggleButton() {
        if (!(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }

        this.patternSourceToggleButton = new PatternSourceToggleButton(sourceAware::setPatternSourceEnabled);
        this.patternSourceToggleButton.setState(sourceAware.data_energistics$isPatternSourceEnabled());
        this.addRenderableWidget(this.patternSourceToggleButton);
    }

    private void updatePatternSourceToggleButton() {
        if (this.patternSourceToggleButton == null || !(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }

        this.patternSourceToggleButton.setState(sourceAware.data_energistics$isPatternSourceEnabled());
        ResourceLocation workstationId = PatternEncodingSourceHelper.resolvePreferredWorkstationId(sourceAware);
        if (workstationId != null) {
            this.patternSourceToggleButton.setDetailLine(Component.translatable(
                    "button.data_energistics.pattern_encoding_source_toggle.detail",
                    PatternEncodingSourceHelper.resolveWorkstationDisplayName(workstationId)));
        } else {
            this.patternSourceToggleButton.setDetailLine(Component.translatable(
                    "button.data_energistics.pattern_encoding_source_toggle.detail.none"));
        }

        boolean visible = this.menu.getMode() == EncodingMode.PROCESSING;
        this.patternSourceToggleButton.visible = visible;
        this.patternSourceToggleButton.active = visible;
        WidgetStyle clearButtonStyle = this.getStyle().getWidget("processingClearPattern");
        Point clearButtonPosition = clearButtonStyle.resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        this.patternSourceToggleButton.setX(clearButtonPosition.getX());
        this.patternSourceToggleButton.setY(clearButtonPosition.getY() + 10);
    }

    private void updatePreviewScrollbar() {
        int hiddenRows = Math.max(0, getVisibleProviders().size() - PROVIDER_VISIBLE_ROWS);
        this.previewScrollbar.setPosition(new Point(PANEL_SCROLLBAR_X, PANEL_SCROLLBAR_Y));
        this.previewScrollbar.setHeight(PANEL_SCROLLBAR_HEIGHT);
        this.previewScrollbar.setSize(this.previewScrollbar.getBounds().getWidth(), PANEL_SCROLLBAR_HEIGHT);
        this.previewScrollbar.setRange(0, hiddenRows, 1);
        this.previewScrollbar.setVisible(this.previewVisible && hiddenRows > 0);
        this.previewScrollbar.setCurrentScroll(Math.min(this.previewScrollbar.getCurrentScroll(), hiddenRows));
    }

    private void syncProviderSelection() {
        syncProviderLocationFromRecordedWorkstation();
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = getVisibleProviders();
        if (providers.isEmpty()) {
            this.selectedPatternProviderId = -1L;
            this.renamingProviderId = -1L;
            return;
        }
        boolean found = false;
        for (var provider : providers) {
            if (provider.id() == this.selectedPatternProviderId) {
                found = true;
                break;
            }
        }
        if (!found) {
            this.selectedPatternProviderId = providers.getFirst().id();
        }
        if (isRenamingProvider() && getPatternProvider(this.renamingProviderId) == null) {
            cancelProviderRename();
        }
    }

    private void syncProviderLocationFromRecordedWorkstation() {
        if (!(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            this.lastLocatedWorkstationId = null;
            return;
        }

        ResourceLocation workstationId = PatternEncodingSourceHelper.resolvePreferredWorkstationId(sourceAware);
        if (Objects.equals(this.lastLocatedWorkstationId, workstationId)) {
            return;
        }

        this.lastLocatedWorkstationId = workstationId;
        this.previewScrollbar.setCurrentScroll(0);
        this.selectedPatternProviderId = -1L;
    }

    private ProviderButtonHit getProviderButtonHit(double mouseX, double mouseY) {
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = getVisibleProviders();
        int start = this.previewScrollbar.getCurrentScroll();
        int end = Math.min(providers.size(), start + PROVIDER_VISIBLE_ROWS);
        for (int rowIndex = start; rowIndex < end; rowIndex++) {
            int visibleRow = rowIndex - start;
            var provider = providers.get(rowIndex);
            if (getProviderButtonBounds(visibleRow).contains((int) mouseX, (int) mouseY)) {
                return new ProviderButtonHit(provider);
            }
        }
        return null;
    }

    private List<PatternEncodingPreviewMenu.SyncedPatternProvider> getVisibleProviders() {
        if (!this.visibleProvidersCacheDirty) {
            return this.cachedVisibleProviders;
        }

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = previewBridge().data_energistics$getSyncedPatternProviders();
        if (isRenamingProvider()) {
            this.cachedVisibleProviders = providers;
            this.visibleProvidersCacheDirty = false;
            return providers;
        }

        String query = this.providerSearchBox != null ? this.providerSearchBox.getValue().trim().toLowerCase() : "";
        if (query.isEmpty()) {
            this.cachedVisibleProviders = providers;
            this.visibleProvidersCacheDirty = false;
            return providers;
        }

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> filtered = new ArrayList<>();
        for (var provider : providers) {
            String display = provider.displayName().getString().toLowerCase();
            String iconId = provider.iconItemId().toString().toLowerCase();
            if (display.contains(query) || iconId.contains(query)) {
                filtered.add(provider);
            }
        }
        this.cachedVisibleProviders = List.copyOf(filtered);
        this.visibleProvidersCacheDirty = false;
        return this.cachedVisibleProviders;
    }

    private void invalidateVisibleProvidersCache() {
        this.visibleProvidersCacheDirty = true;
    }

    private Rect2i getPreviewPanelBounds() {
        int preferredX = this.leftPos + this.imageWidth + PANEL_X_OFFSET;
        if (preferredX + PANEL_WIDTH > this.width - 4) {
            preferredX = this.leftPos - PANEL_WIDTH + PANEL_X_OFFSET;
        }
        int x = Math.max(4, Math.min(preferredX, this.width - PANEL_WIDTH - 4));
        int preferredY = this.topPos + PANEL_Y_OFFSET;
        int y = Math.max(4, Math.min(preferredY, this.height - PANEL_HEIGHT - 4));
        return new Rect2i(x, y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    private Rect2i getProviderButtonBounds(int visibleRow) {
        Rect2i listBounds = getProviderListBounds();
        int x = listBounds.getX();
        int y = listBounds.getY() + visibleRow * (PROVIDER_BUTTON_HEIGHT + PROVIDER_BUTTON_GAP);
        return new Rect2i(x, y, PROVIDER_BUTTON_WIDTH, PROVIDER_BUTTON_HEIGHT);
    }

    private Rect2i getProviderListBounds() {
        Rect2i bounds = getPreviewPanelBounds();
        int x = bounds.getX() + PANEL_CONTENT_X;
        int y = bounds.getY() + PROVIDER_LIST_Y;
        int width = bounds.getWidth() - PANEL_CONTENT_X - PANEL_CONTENT_RIGHT;
        int height = bounds.getHeight() - PROVIDER_LIST_Y - PANEL_CONTENT_BOTTOM;
        return new Rect2i(x, y, Math.max(1, width), Math.max(1, height));
    }

    private boolean isOverPreviewScrollbar(double mouseX, double mouseY) {
        Rect2i bounds = this.previewScrollbar.getBounds();
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth() && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    private boolean isOverProviderList(double mouseX, double mouseY) {
        Rect2i bounds = getProviderListBounds();
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth() && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    private boolean isOverEncodeButton(double mouseX, double mouseY) {
        if (this.encodePatternWidget != null && this.encodePatternWidget.visible) {
            return this.encodePatternWidget.isMouseOver(mouseX, mouseY);
        }

        WidgetStyle buttonStyle = this.getStyle().getWidget("encodePattern");
        Point position = buttonStyle.resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        int width = buttonStyle.getWidth() > 0 ? buttonStyle.getWidth() : 16;
        int height = buttonStyle.getHeight() > 0 ? buttonStyle.getHeight() : 16;
        return mouseX >= position.getX() && mouseX < position.getX() + width && mouseY >= position.getY() && mouseY < position.getY() + height;
    }

    @SuppressWarnings("unchecked")
    private AbstractWidget resolveEncodePatternWidget() {
        try {
            Map<String, AbstractWidget> widgetsById = (Map<String, AbstractWidget>) WIDGET_CONTAINER_WIDGETS_FIELD.get(this.widgets);
            return widgetsById.get("encodePattern");
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private void applyEncodeButtonHint() {
        if (this.encodePatternWidget != null) {
            this.encodePatternWidget.setMessage(ENCODE_BUTTON_HINT);
        }
    }

    private boolean triggerBlankPatternAutoCraft(double mouseX, double mouseY) {
        Slot slot = this.hoveredSlot;
        if (slot == null || this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN) {
            return false;
        }
        if (!isMouseOverSlot(slot, mouseX, mouseY)) {
            return false;
        }

        GridInventoryEntry blankPatternEntry = findBlankPatternEntry();
        if (blankPatternEntry == null || !blankPatternEntry.isCraftable()) {
            return false;
        }

        this.menu.handleInteraction(blankPatternEntry.getSerial(), InventoryAction.AUTO_CRAFT);
        return true;
    }

    private boolean handleBlankPatternSlotClick(double mouseX, double mouseY, int button) {
        if (!(this.menu instanceof BlankPatternProxyMenu blankPatternProxyMenu)) {
            return false;
        }

        Slot slot = this.hoveredSlot;
        if (slot == null || this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN || !isMouseOverSlot(slot, mouseX, mouseY)) {
            return false;
        }

        if (button == 0) {
            if (this.menu.getCarried().isEmpty()) {
                blankPatternProxyMenu.data_energistics$pickupBlankPatterns(false);
            } else if (AEItems.BLANK_PATTERN.is(this.menu.getCarried())) {
                blankPatternProxyMenu.data_energistics$depositCarriedBlankPatterns(false);
            } else {
                return false;
            }
            return true;
        }

        if (button == 1) {
            if (this.menu.getCarried().isEmpty()) {
                blankPatternProxyMenu.data_energistics$pickupBlankPatterns(true);
            } else if (AEItems.BLANK_PATTERN.is(this.menu.getCarried())) {
                blankPatternProxyMenu.data_energistics$depositCarriedBlankPatterns(true);
            } else {
                return false;
            }
            return true;
        }

        return false;
    }

    private GridInventoryEntry findBlankPatternEntry() {
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        if (blankPatternKey == null) {
            return null;
        }

        GridInventoryEntry fallback = null;
        for (GridInventoryEntry entry : this.repo.getAllEntries()) {
            if (!blankPatternKey.equals(entry.getWhat())) {
                continue;
            }
            if (entry.isMeaningful()) {
                return entry;
            }
            if (fallback == null) {
                fallback = entry;
            }
        }

        return fallback;
    }

    private boolean isMouseOverSlot(Slot slot, double mouseX, double mouseY) {
        return mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + 16 && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + 16;
    }

    private boolean isRenamingProvider() {
        return this.renamingProviderId >= 0L;
    }

    private void beginProviderRename(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        this.selectedPatternProviderId = provider.id();
        this.renamingProviderId = provider.id();
        invalidateVisibleProvidersCache();
        if (this.providerSearchBox != null) {
            this.providerSearchBox.setFocused(false);
        }
        if (this.providerRenameBox != null) {
            this.providerRenameBox.setValue(provider.displayName().getString());
            this.providerRenameBox.setVisible(true);
            this.providerRenameBox.active = true;
            this.providerRenameBox.setFocused(true);
        }
    }

    private void cancelProviderRename() {
        this.renamingProviderId = -1L;
        invalidateVisibleProvidersCache();
        if (this.providerRenameBox != null) {
            this.providerRenameBox.setFocused(false);
            this.providerRenameBox.setVisible(false);
        }
    }

    private void commitProviderRename() {
        if (!isRenamingProvider() || this.providerRenameBox == null) {
            return;
        }

        previewBridge().data_energistics$renamePatternProvider(this.renamingProviderId, this.providerRenameBox.getValue());
        cancelProviderRename();
    }

    private PatternEncodingPreviewMenu.SyncedPatternProvider getPatternProvider(long providerId) {
        for (var provider : getVisibleProviders()) {
            if (provider.id() == providerId) {
                return provider;
            }
        }
        return null;
    }

    private double getMouseGuiX() {
        return this.minecraft.mouseHandler.xpos() * (double) this.width / this.minecraft.getWindow().getScreenWidth();
    }

    private double getMouseGuiY() {
        return this.minecraft.mouseHandler.ypos() * (double) this.height / this.minecraft.getWindow().getScreenHeight();
    }

    private void drawProviderButtonBackground(GuiGraphics guiGraphics, Rect2i bounds,
                                              PatternEncodingPreviewMenu.SyncedPatternProvider provider,
                                              boolean selected, boolean hovered) {
        if (provider.useAeButtonStyle()) {
            ResourceLocation texture = selected ? AE2_BUTTON_DISABLED_TEXTURE : hovered ? AE2_BUTTON_HIGHLIGHTED_TEXTURE : AE2_BUTTON_TEXTURE;
            drawNineSlicedTexture(guiGraphics, texture, bounds,
                    BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT,
                    BUTTON_SLICE_BORDER, BUTTON_SLICE_BORDER, BUTTON_SLICE_BORDER, BUTTON_SLICE_BORDER);
            return;
        }

        guiGraphics.fill(bounds.getX(), bounds.getY(),
                bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight(),
                selected ? PANEL_BUTTON_SELECTED_COLOR : hovered ? PANEL_BUTTON_HOVER_COLOR : PANEL_BUTTON_COLOR);
        guiGraphics.renderOutline(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
                PANEL_BUTTON_BORDER_COLOR);
    }

    private void drawNineSlicedTexture(GuiGraphics guiGraphics, ResourceLocation texture, Rect2i bounds,
                                       int textureWidth, int textureHeight,
                                       int left, int top, int right, int bottom) {
        int centerDstWidth = Math.max(0, bounds.getWidth() - left - right);
        int centerDstHeight = Math.max(0, bounds.getHeight() - top - bottom);
        int x = bounds.getX();
        int y = bounds.getY();
        int width = bounds.getWidth();
        int height = bounds.getHeight();

        guiGraphics.blit(texture, x, y, 0, 0, 0, left, top, textureWidth, textureHeight);
        guiGraphics.blit(texture, x + width - right, y, 0,
                textureWidth - right, 0, right, top, textureWidth, textureHeight);
        guiGraphics.blit(texture, x, y + height - bottom, 0,
                0, textureHeight - bottom, left, bottom, textureWidth, textureHeight);
        guiGraphics.blit(texture, x + width - right, y + height - bottom, 0,
                textureWidth - right, textureHeight - bottom, right, bottom, textureWidth, textureHeight);

        if (centerDstWidth > 0) {
            guiGraphics.blit(texture, x + left, y, 0,
                    left, 0, centerDstWidth, top, textureWidth, textureHeight);
            guiGraphics.blit(texture, x + left, y + height - bottom, 0,
                    left, textureHeight - bottom, centerDstWidth, bottom, textureWidth, textureHeight);
        }

        if (centerDstHeight > 0) {
            guiGraphics.blit(texture, x, y + top, 0,
                    0, top, left, centerDstHeight, textureWidth, textureHeight);
            guiGraphics.blit(texture, x + width - right, y + top, 0,
                    textureWidth - right, top, right, centerDstHeight, textureWidth, textureHeight);
        }

        if (centerDstWidth > 0 && centerDstHeight > 0) {
            guiGraphics.blit(texture, x + left, y + top, 0,
                    left, top, centerDstWidth, centerDstHeight, textureWidth, textureHeight);
        }
    }

    private void drawScaledText(GuiGraphics guiGraphics, String text, int x, int y, int color, float scale) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, Math.round(x / scale), Math.round(y / scale), color, false);
        poseStack.popPose();
    }

    private int getScaledTextWidth(String text, float scale) {
        return (int) Math.ceil(this.font.width(text) * scale);
    }

    private String trimToWidth(String text, int maxWidth, float scale) {
        if (getScaledTextWidth(text, scale) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = getScaledTextWidth("...", scale);
        int rawWidthLimit = Math.max(0, (int) Math.floor((maxWidth - ellipsisWidth) / scale));
        return this.font.plainSubstrByWidth(text, rawWidthLimit) + "...";
    }

    private int getProviderCountColor(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        int total = provider.patternSlotCount();
        if (total <= 0) {
            return PANEL_COUNT_NORMAL_COLOR;
        }
        int remaining = Math.max(0, total - provider.usedPatternSlotCount());
        return remaining * 9 < total * 2 ? PANEL_COUNT_WARNING_COLOR : PANEL_COUNT_NORMAL_COLOR;
    }

    private ItemStack getProviderIconStack(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        var item = BuiltInRegistries.ITEM.get(provider.iconItemId());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Field resolveField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve field " + owner.getName() + "#" + name, e);
        }
    }

    private record ProviderButtonHit(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {}
}
