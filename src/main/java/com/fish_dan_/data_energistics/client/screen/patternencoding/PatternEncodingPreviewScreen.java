package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.client.screen.base.AETextFieldInteraction;
import com.fish_dan_.data_energistics.client.widget.PatternRecipeTypeToggleButton;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.patternencoding.BlankPatternProxyMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.patternencoding.source.PatternEncodingSourceHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.definitions.AEItems;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ReadableNumberConverter;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.List;
import java.util.Objects;

public class PatternEncodingPreviewScreen<T extends PatternEncodingTermMenu> extends PatternEncodingTermScreen<T>
                                         implements Ae2NativeSlotHighlight, PreviewLayerTooltipScreen,
                                         PatternProviderLeafPanelHost {

    private static final ResourceLocation AE2_UPLOAD_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/upload.png");
    private static final ResourceLocation AE2_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/gui/sprites/button.png");
    private static final ResourceLocation AE2_BUTTON_HIGHLIGHTED_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/gui/sprites/button_highlighted.png");
    private static final ResourceLocation AE2_BUTTON_DISABLED_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/gui/sprites/button_disabled.png");
    private static final ResourceLocation AE2_SMALL_SCROLLBAR_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "small_scroller");
    private static final ResourceLocation AE2_SMALL_SCROLLBAR_DISABLED_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "small_scroller_disabled");
    private static final float PREVIEW_LAYER_Z = 400.0F;
    private static final Component PANEL_TITLE = Component.translatable("screen.data_energistics.pattern_writer_preview.panel_title");
    private static final int COLOR_PANEL_TITLE = 0x000000;
    private static final Component EMPTY_STATE_TEXT = Component.translatable("screen.data_energistics.pattern_writer_preview.empty_state");
    private static final Component ENCODE_BUTTON_HINT = Component.translatable("screen.data_energistics.pattern_writer_preview.encode_button_hint");
    private static final int PREVIEW_PANEL_WIDTH = 128;
    private static final int PREVIEW_PANEL_HEIGHT = 128;
    private static final int PREVIEW_TEXTURE_WIDTH = 128;
    private static final int PREVIEW_TEXTURE_HEIGHT = 128;
    private static final int PREVIEW_TEXTURE_U = 0;
    private static final int PREVIEW_TEXTURE_V = 0;
    private static final int BUTTON_TEXTURE_WIDTH = 200;
    private static final int BUTTON_TEXTURE_HEIGHT = 20;
    private static final int BUTTON_SLICE_BORDER = 4;
    private static final int DEFAULT_PREVIEW_PANEL_MARGIN = 0;
    private static final int DEFAULT_PREVIEW_PANEL_X_OFFSET = 0;
    private static final int DEFAULT_PREVIEW_PANEL_Y_OFFSET = 105;
    private static final int DEFAULT_PANEL_CONTENT_X = 10;
    private static final int DEFAULT_PANEL_CONTENT_RIGHT = 6;
    private static final int DEFAULT_PANEL_CONTENT_BOTTOM = 6;
    private static final int DEFAULT_PANEL_TITLE_Y = 4;
    private static final int DEFAULT_SEARCH_BOX_X = 42;
    private static final int DEFAULT_SEARCH_BOX_Y = 6;
    private static final int DEFAULT_SEARCH_BOX_WIDTH = 55;
    private static final int DEFAULT_SEARCH_BOX_HEIGHT = 12;
    private static final int PREVIEW_DRAG_BUTTON_RIGHT_PADDING = 4;
    private static final int PREVIEW_DRAG_BUTTON_TOP_PADDING = -7;
    private static final int DEFAULT_PREVIEW_SCROLLBAR_X = 114;
    private static final int PREVIEW_SCROLLBAR_Y_OFFSET = -3;
    private static final int DEFAULT_PROVIDER_LIST_Y = 20;
    private static final int DEFAULT_PROVIDER_BUTTON_GAP = -1;
    private static final int DEFAULT_PROVIDER_VISIBLE_ROWS = 5;
    private static final int DEFAULT_PROVIDER_BUTTON_WIDTH = 95;
    private static final int DEFAULT_PROVIDER_BUTTON_HEIGHT = 20;
    private static final int PROVIDER_NAME_X_PADDING = 4;
    private static final int PROVIDER_ICON_SIZE = 16;
    private static final int PROVIDER_ICON_X_PADDING = 2;
    private static final int PROVIDER_COUNT_RIGHT_PADDING = 4;
    private static final int PROVIDER_TEXT_Y_OFFSET = 5;
    private static final float PROVIDER_TEXT_SCALE = 0.75F;
    private static final float PROVIDER_COUNT_TEXT_SCALE = 0.68F;
    private static final int COLOR_PANEL_TEXT = 0xE7E7E7;
    private static final int COLOR_EMPTY_STATE_TEXT = 0x000000;
    private static final int COLOR_PROVIDER_COUNT_NORMAL = 0x9CD3FF;
    private static final int COLOR_PROVIDER_COUNT_WARNING = 0xC83A32;
    private static final int COLOR_BUTTON = 0x6A111111;
    private static final int COLOR_BUTTON_HOVER = 0x88333333;
    private static final int COLOR_BUTTON_SELECTED = 0xAA5F7991;
    private static final int COLOR_BUTTON_BORDER = 0xB0909090;
    private static final Component SEARCH_BOX_HINT = Component.translatable("screen.data_energistics.pattern_writer_preview.search_hint");

    private boolean previewVisible;
    private boolean renderingPreviewTooltip;
    private boolean previewScrollbarDragging;
    private long selectedPatternProviderId = -1L;
    private long renamingProviderId = -1L;
    private boolean suppressRenameKeyChar;
    private ResourceLocation lastLocatedWorkstationId;
    private final Scrollbar previewScrollbar = new Scrollbar(Scrollbar.SMALL);
    private AbstractWidget encodePatternWidget;
    private Component originalEncodePatternMessage;
    private AETextField providerSearchBox;
    private AETextField providerRenameBox;
    private PatternRecipeTypeToggleButton recipeTypeToggleButton;
    private PatternEncodingPreviewDragButton previewDragButton;
    private ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> cachedVisibleProviders = ObjectLists.emptyList();
    private boolean visibleProvidersCacheDirty = true;
    private boolean previewPanelDragging;
    private int previewPanelDragOffsetX;
    private int previewPanelDragOffsetY;
    private int previewPanelCurrentOffsetX;
    private int previewPanelCurrentOffsetY;
    private Rect2i previewPanelDragBaseBounds;
    private boolean previewLayerWidgetRenderingDeferred;
    private String lastPreferenceLeafDigestSignature = "";
    private final PatternProviderLeafPanel leafPanel;
    private String pendingParentSelectionLeafDigest;
    private int pendingParentSelectionTicks;

    public PatternEncodingPreviewScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.leafPanel = new PatternProviderLeafPanel(this);
        this.previewScrollbar.setCaptureMouseWheel(false);
        this.previewScrollbar.setRange(0, 0, 1);
    }

    @Override
    public void init() {
        super.init();
        PatternEncodingPreferencesClient.initializeMenu(this.menu);
        this.encodePatternWidget = resolveEncodePatternWidget();
        if (this.originalEncodePatternMessage == null && this.encodePatternWidget != null) {
            this.originalEncodePatternMessage = this.encodePatternWidget.getMessage();
        }
        applyEncodeButtonHint();
        initProviderSearchBox();
        initProviderRenameBox();
        initRecipeTypeToggleButton();
        initPreviewDragButton();
        this.leafPanel.init();
        invalidateVisibleProvidersCache();
        updateProviderSearchBox();
        updateProviderRenameBox();
        updateRecipeTypeToggleButton();
        updatePreviewDragButton();
        updatePreviewScrollbar();
        syncPreferenceSnapshotIfProvidersChanged();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateProviderSearchBox();
        updateProviderRenameBox();
        updateRecipeTypeToggleButton();
        updatePreviewDragButton();
        invalidateVisibleProvidersCache();
        syncProviderSelection();
        this.leafPanel.updateProviderSnapshot();
        updatePreviewScrollbar();
        syncPreferenceSnapshotIfProvidersChanged();
        applyEncodeButtonHint();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleBlankPatternSlotClick(mouseX, mouseY, button)) {
            return true;
        }

        if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button) && triggerBlankPatternAutoCraft(mouseX, mouseY)) {
            return true;
        }

        if (this.leafPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (this.providerRenameBox != null && AETextFieldInteraction.clearOnRightClick(this.providerRenameBox, mouseX, mouseY, button)) {
            return true;
        }

        if (this.providerSearchBox != null && AETextFieldInteraction.clearOnRightClick(this.providerSearchBox, mouseX, mouseY, button)) {
            return true;
        }

        if (isRenamingProvider() && this.providerRenameBox != null && this.providerRenameBox.isMouseOver(mouseX, mouseY)) {
            return this.providerRenameBox.mouseClicked(mouseX, mouseY, button);
        }

        if (this.previewVisible && this.previewDragButton != null && this.previewDragButton.isMouseOver(mouseX, mouseY)) {
            if (button == 0) {
                Rect2i previewBounds = getPreviewPanelBounds();
                Rect2i defaultBounds = getDefaultPreviewPanelBounds();
                this.previewPanelDragging = true;
                this.previewPanelDragOffsetX = (int) Math.round(mouseX) - previewBounds.getX();
                this.previewPanelDragOffsetY = (int) Math.round(mouseY) - previewBounds.getY();
                this.previewPanelCurrentOffsetX = previewBounds.getX() - defaultBounds.getX();
                this.previewPanelCurrentOffsetY = previewBounds.getY() - defaultBounds.getY();
                this.previewPanelDragBaseBounds = defaultBounds;
                return true;
            }
            if (button == 1) {
                PatternEncodingPreferencesClient.setPreviewPanelOffset(this.menu, 0, 0);
                this.previewPanelCurrentOffsetX = 0;
                this.previewPanelCurrentOffsetY = 0;
                this.previewPanelDragging = false;
                this.previewPanelDragBaseBounds = null;
                updatePreviewDragButton();
                updatePreviewScrollbar();
                updateProviderSearchBox();
                updateProviderRenameBox();
                return true;
            }
        }

        if (button == 0 && isOverEncodeButton(mouseX, mouseY)) {
            if (!isUploadEnabled()) {
                closePreviewPanels();
                return super.mouseClicked(mouseX, mouseY, button);
            }

            if (hasShiftDown()) {
                closePreviewPanels();
                return true;
            }

            this.previewVisible = true;
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            return handled || isOverEncodeButton(mouseX, mouseY);
        }

        if (button == 1 && isOverEncodeButton(mouseX, mouseY)) {
            if (!isUploadEnabled()) {
                closePreviewPanels();
                return super.mouseClicked(mouseX, mouseY, button);
            }

            if (this.previewVisible) {
                if (hasShiftDown()) {
                    closePreviewPanels();
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
                if (isUploadEnabled()) {
                    previewBridge().data_energistics$transferEncodedPatternToProvider(hit.provider().id());
                }
                return true;
            }
        }

        if (this.previewVisible && isProviderOpenEnabled() && DEKeyMappings.OPEN_PATTERN_PROVIDER.matchesMouse(button)) {
            var hit = getProviderButtonHit(mouseX, mouseY);
            if (hit != null) {
                if (isRenamingProvider() && this.renamingProviderId != hit.provider().id()) {
                    cancelProviderRename();
                }
                this.selectedPatternProviderId = hit.provider().id();
                openProviderOrLeafPanel(hit.provider());
                return true;
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.leafPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
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

        if (this.previewVisible && isProviderRenameEnabled() && DEKeyMappings.RENAME_PATTERN_PROVIDER.matches(keyCode, scanCode)) {
            var hit = getProviderButtonHit(this.minecraft.mouseHandler.xpos() * (double) this.width / this.minecraft.getWindow().getScreenWidth(),
                    this.minecraft.mouseHandler.ypos() * (double) this.height / this.minecraft.getWindow().getScreenHeight());
            if (hit != null && hit.provider().renameable()) {
                beginProviderRename(hit.provider());
                this.suppressRenameKeyChar = true;
                return true;
            }
        }

        if (this.previewVisible && isProviderOpenEnabled() && DEKeyMappings.OPEN_PATTERN_PROVIDER.matches(keyCode, scanCode)) {
            var hit = getProviderButtonHit(this.minecraft.mouseHandler.xpos() * (double) this.width / this.minecraft.getWindow().getScreenWidth(),
                    this.minecraft.mouseHandler.ypos() * (double) this.height / this.minecraft.getWindow().getScreenHeight());
            if (hit != null) {
                if (isRenamingProvider() && this.renamingProviderId != hit.provider().id()) {
                    cancelProviderRename();
                }
                this.selectedPatternProviderId = hit.provider().id();
                openProviderOrLeafPanel(hit.provider());
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.leafPanel.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.suppressRenameKeyChar) {
            this.suppressRenameKeyChar = false;
            return true;
        }

        if (isRenamingProvider() && this.providerRenameBox != null && this.providerRenameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.leafPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (this.previewPanelDragging) {
            this.previewPanelDragging = false;
            savePreviewPanelDragOffset(mouseX, mouseY);
            return true;
        }
        if (this.previewScrollbarDragging) {
            this.previewScrollbar.onMouseUp(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)), button);
            this.previewScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        if (this.leafPanel.mouseDragged(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (this.previewVisible && this.previewPanelDragging) {
            updatePreviewPanelDragOffset(mouseX, mouseY);
            return true;
        }
        if (this.previewVisible && this.previewScrollbarDragging && this.previewScrollbar.onMouseDrag(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)),
                mouseButton)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.leafPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (this.previewVisible && (isOverPreviewScrollbar(mouseX, mouseY) || isOverProviderList(mouseX, mouseY)) && this.previewScrollbar.onMouseWheel(new Point((int) Math.round(mouseX), (int) Math.round(mouseY)),
                scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        deferPreviewLayerWidgets();
        try {
            super.render(guiGraphics, mouseX, mouseY, partialTicks);
        } finally {
            restorePreviewLayerWidgets();
        }

        if (this.previewVisible) {
            renderPreviewLayer(guiGraphics, mouseX, mouseY, partialTicks);
            renderPreviewLayerTooltips(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN ||
                !usesNetworkBackedBlankPatternSlot()) {
            super.renderSlot(guiGraphics, slot);
            return;
        }

        GridInventoryEntry blankPatternEntry = findBlankPatternEntry();
        long networkStored = blankPatternEntry != null ? blankPatternEntry.getStoredAmount() : 0;
        boolean networkCraftable = blankPatternEntry != null && (blankPatternEntry.isCraftable() || blankPatternEntry.getRequestableAmount() > 0);
        int localBlankPatternCount = AEItems.BLANK_PATTERN.is(slot.getItem()) ? slot.getItem().getCount() : 0;
        long displayedCount = networkStored > 0 ? networkStored : localBlankPatternCount;
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

        PoseStack poseStack = guiGraphics.pose();
        if (networkCraftable) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 100.0F);
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, (float) (slot.x - 11), (float) (slot.y - 11),
                    "+", false);
            poseStack.popPose();
        }
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        ObjectArrayList<Rect2i> zones = new ObjectArrayList<>(super.getExclusionZones());
        if (this.previewVisible) {
            zones.addAll(getPreviewInteractiveBounds());
            zones.addAll(this.leafPanel.getInteractiveBounds());
        }
        return zones;
    }

    @Override
    public boolean shouldSuppressUnderlyingTooltip(int mouseX, int mouseY) {
        return this.previewVisible && !this.renderingPreviewTooltip &&
                (isOverPreviewLayer(mouseX, mouseY) || this.leafPanel.isOver(mouseX, mouseY));
    }

    private boolean isOverPreviewLayer(int mouseX, int mouseY) {
        if (getPreviewPanelBounds().contains(mouseX, mouseY)) {
            return true;
        }
        return this.previewDragButton != null &&
                mouseX >= this.previewDragButton.getX() && mouseX < this.previewDragButton.getX() + this.previewDragButton.getWidth() &&
                mouseY >= this.previewDragButton.getY() && mouseY < this.previewDragButton.getY() + this.previewDragButton.getHeight();
    }

    private ObjectList<Rect2i> getPreviewInteractiveBounds() {
        ObjectArrayList<Rect2i> zones = new ObjectArrayList<>();
        zones.add(getPreviewPanelBounds());
        zones.add(getProviderListBounds());
        zones.add(this.previewScrollbar.getBounds());

        if (this.previewDragButton != null) {
            zones.add(new Rect2i(
                    this.previewDragButton.getX(), this.previewDragButton.getY(),
                    this.previewDragButton.getWidth(), this.previewDragButton.getHeight()));
        }

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

    private void deferPreviewLayerWidgets() {
        if (!this.previewVisible) {
            return;
        }

        this.previewLayerWidgetRenderingDeferred = true;
        this.leafPanel.setLayerWidgetsDeferred(true);
    }

    private void restorePreviewLayerWidgets() {
        if (!this.previewLayerWidgetRenderingDeferred) {
            return;
        }

        this.previewLayerWidgetRenderingDeferred = false;
        this.leafPanel.setLayerWidgetsDeferred(false);
        updateProviderSearchBox();
        updateProviderRenameBox();
        updatePreviewDragButton();
        updatePreviewScrollbar();
    }

    private void renderPreviewLayer(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, PREVIEW_LAYER_Z);
        try {
            Rect2i previewBounds = getPreviewPanelBounds();
            guiGraphics.blit(AE2_UPLOAD_TEXTURE,
                    previewBounds.getX(), previewBounds.getY(),
                    0,
                    PREVIEW_TEXTURE_U, PREVIEW_TEXTURE_V,
                    previewBounds.getWidth(), previewBounds.getHeight(),
                    PREVIEW_TEXTURE_WIDTH, PREVIEW_TEXTURE_HEIGHT);

            drawProviderButtons(guiGraphics, mouseX, mouseY);
            drawPreviewScrollbarHandle(guiGraphics);
            renderPreviewLayerWidget(this.providerSearchBox, guiGraphics, mouseX, mouseY, partialTicks);
            renderPreviewLayerWidget(this.providerRenameBox, guiGraphics, mouseX, mouseY, partialTicks);
            renderPreviewLayerWidget(this.previewDragButton, guiGraphics, mouseX, mouseY, partialTicks);
            this.leafPanel.render(guiGraphics, mouseX, mouseY, partialTicks);
        } finally {
            poseStack.popPose();
        }
    }

    private void renderPreviewLayerTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PoseStack poseStack = guiGraphics.pose();
        boolean wasRenderingPreviewTooltip = this.renderingPreviewTooltip;
        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, 0.0F, PREVIEW_LAYER_Z);
            this.renderingPreviewTooltip = true;
            if (this.leafPanel.isOver(mouseX, mouseY)) {
                this.leafPanel.renderTooltips(guiGraphics, mouseX, mouseY);
            } else {
                renderProviderTooltips(guiGraphics, mouseX, mouseY);
                renderPreviewLayerWidgetTooltips(guiGraphics, mouseX, mouseY);
            }
        } finally {
            this.renderingPreviewTooltip = wasRenderingPreviewTooltip;
            poseStack.popPose();
        }
    }

    private void renderPreviewLayerWidget(AbstractWidget widget, GuiGraphics guiGraphics, int mouseX, int mouseY,
                                          float partialTicks) {
        if (widget != null && widget.visible) {
            widget.render(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    private void renderPreviewLayerWidgetTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.previewDragButton != null && this.previewDragButton.visible && this.previewDragButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, this.previewDragButton.getMessage(), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.leafPanel.closeForScreenTeardown();
        super.onClose();
    }

    @Override
    public void removed() {
        this.leafPanel.closeForScreenTeardown();
        super.removed();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        PatternEncodingPreferencesClient.flushDeferredSnapshot(this.menu);
        this.leafPanel.tick();
        this.suppressRenameKeyChar = false;
        if (this.previewVisible) {
            this.previewScrollbar.tick();
        }
    }

    private PatternEncodingPreviewMenu previewBridge() {
        if (this.menu instanceof PatternEncodingPreviewMenu bridge) {
            return bridge;
        }
        throw new IllegalStateException("Pattern encoding menu does not implement preview bridge: " + this.menu.getClass().getName());
    }

    private PatternEncodingPreviewLayoutAware previewLayout() {
        if (this.menu instanceof PatternEncodingPreviewLayoutAware layoutAware) {
            return layoutAware;
        }
        throw new IllegalStateException("Pattern encoding menu does not implement preview layout: " + this.menu.getClass().getName());
    }

    private void drawProviderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Rect2i previewBounds = getPreviewPanelBounds();
        guiGraphics.drawString(this.font, PANEL_TITLE,
                previewBounds.getX() + getPanelContentX(),
                previewBounds.getY() + getPanelTitleY(),
                COLOR_PANEL_TITLE,
                false);

        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> visibleProviders = getVisibleProviders();
        if (visibleProviders.isEmpty()) {
            drawScaledText(guiGraphics, EMPTY_STATE_TEXT.getString(),
                    previewBounds.getX() + getPanelContentX(),
                    previewBounds.getY() + getProviderListY() + 2 + PROVIDER_TEXT_Y_OFFSET,
                    COLOR_EMPTY_STATE_TEXT,
                    PROVIDER_TEXT_SCALE);
            return;
        }

        int start = this.previewScrollbar.getCurrentScroll();
        int end = Math.min(visibleProviders.size(), start + getProviderVisibleRows());
        for (int rowIndex = start; rowIndex < end; rowIndex++) {
            var provider = visibleProviders.get(rowIndex);
            int visibleRow = rowIndex - start;
            Rect2i providerButtonBounds = getProviderButtonBounds(visibleRow);
            boolean selected = provider.id() == this.selectedPatternProviderId;
            boolean hoveredProvider = providerButtonBounds.contains(mouseX, mouseY);

            drawProviderButtonBackground(guiGraphics, providerButtonBounds, provider, selected, hoveredProvider);

            ItemStack providerIcon = getProviderIconStack(provider);
            int nameStartX = providerButtonBounds.getX() + PROVIDER_NAME_X_PADDING;
            if (!providerIcon.isEmpty()) {
                int iconX = providerButtonBounds.getX() + PROVIDER_ICON_X_PADDING;
                int iconY = providerButtonBounds.getY() + (providerButtonBounds.getHeight() - PROVIDER_ICON_SIZE) / 2;
                guiGraphics.renderItem(providerIcon, iconX, iconY);
                nameStartX = iconX + PROVIDER_ICON_SIZE + 2;
            }

            String countText = provider.usedPatternSlotCount() + "/" + provider.patternSlotCount();
            int countWidth = getScaledTextWidth(countText, PROVIDER_COUNT_TEXT_SCALE);
            int maxNameWidth = providerButtonBounds.getX() + providerButtonBounds.getWidth() - PROVIDER_COUNT_RIGHT_PADDING - countWidth - 4 - nameStartX;
            String providerName = trimToWidth(provider.displayName().getString(), Math.max(10, maxNameWidth),
                    PROVIDER_TEXT_SCALE);

            drawScaledText(guiGraphics, providerName,
                    nameStartX,
                    providerButtonBounds.getY() + 2 + PROVIDER_TEXT_Y_OFFSET,
                    COLOR_PANEL_TEXT,
                    PROVIDER_TEXT_SCALE);
            drawScaledText(guiGraphics, countText,
                    providerButtonBounds.getX() + providerButtonBounds.getWidth() - PROVIDER_COUNT_RIGHT_PADDING - countWidth,
                    providerButtonBounds.getY() + 2 + PROVIDER_TEXT_Y_OFFSET,
                    getProviderCountColor(provider),
                    PROVIDER_COUNT_TEXT_SCALE);
        }
    }

    private void renderProviderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var buttonHit = getProviderButtonHit(mouseX, mouseY);
        if (buttonHit == null) {
            return;
        }

        ObjectArrayList<Component> tooltip = new ObjectArrayList<>();
        tooltip.add(buttonHit.provider().displayName().copy());
        tooltip.add(Component.translatable("screen.data_energistics.pattern_writer_preview.provider.upload"));
        if (isProviderOpenEnabled()) {
            tooltip.add(getProviderOpenHint());
        }
        if (isProviderRenameEnabled() && buttonHit.provider().renameable()) {
            tooltip.add(getProviderRenameHint());
        }
        tooltip.add(Component.translatable(
                "screen.data_energistics.pattern_writer_preview.provider.slots",
                buttonHit.provider().usedPatternSlotCount(),
                buttonHit.provider().patternSlotCount()));
        List<FormattedCharSequence> lines = tooltip.stream()
                .map(Component::getVisualOrderText)
                .toList();
        Point tooltipPosition = getPreviewTooltipPosition(mouseX, mouseY, getPreviewPanelBounds());
        guiGraphics.renderTooltip(this.font, lines, tooltipPosition.getX(), tooltipPosition.getY());
    }

    private Component getProviderRenameHint() {
        return Component.translatable(
                "screen.data_energistics.pattern_writer_preview.provider.rename",
                DEKeyMappings.RENAME_PATTERN_PROVIDER.getTranslatedKeyMessage());
    }

    private Component getProviderOpenHint() {
        return Component.translatable(
                "screen.data_energistics.pattern_writer_preview.provider.open",
                DEKeyMappings.OPEN_PATTERN_PROVIDER.getTranslatedKeyMessage());
    }

    private void updatePreviewScrollbar() {
        int hiddenRows = getHiddenProviderRows();
        Rect2i scrollbarBounds = getPreviewScrollbarBounds();
        this.previewScrollbar.setPosition(new Point(scrollbarBounds.getX(), scrollbarBounds.getY()));
        this.previewScrollbar.setHeight(scrollbarBounds.getHeight());
        this.previewScrollbar.setSize(scrollbarBounds.getWidth(), scrollbarBounds.getHeight());
        this.previewScrollbar.setRange(0, hiddenRows, 1);
        this.previewScrollbar.setVisible(this.previewVisible && hiddenRows > 0);
        this.previewScrollbar.setCurrentScroll(Math.min(this.previewScrollbar.getCurrentScroll(), hiddenRows));
    }

    private void syncProviderSelection() {
        syncProviderLocationFromRecordedWorkstation();
        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = getVisibleProviders();
        if (providers.isEmpty()) {
            this.selectedPatternProviderId = -1L;
            return;
        }

        if (selectPendingRenamedProvider()) {
            return;
        }

        if (getPatternProvider(this.selectedPatternProviderId) == null) {
            this.selectedPatternProviderId = providers.getFirst().id();
        }
    }

    private boolean selectPendingRenamedProvider() {
        if (this.pendingParentSelectionLeafDigest == null) {
            return false;
        }
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : previewBridge().data_energistics$getSyncedPatternProviders()) {
            if (provider.leaves().size() == 1 && provider.leaves().getFirst().providerDigest().equals(
                    this.pendingParentSelectionLeafDigest)) {
                ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> visible = getVisibleProviders();
                int index = visible.indexOf(provider);
                if (index < 0) {
                    this.providerSearchBox.setValue("");
                    this.previewScrollbar.setCurrentScroll(0);
                    invalidateVisibleProvidersCache();
                    visible = getVisibleProviders();
                    index = visible.indexOf(provider);
                }
                this.selectedPatternProviderId = provider.id();
                this.previewScrollbar.setCurrentScroll(Math.max(0, index - getProviderVisibleRows() + 1));
                this.pendingParentSelectionLeafDigest = null;
                this.pendingParentSelectionTicks = 0;
                return true;
            }
        }
        this.pendingParentSelectionTicks++;
        if (this.pendingParentSelectionTicks > 40) {
            this.pendingParentSelectionLeafDigest = null;
            this.pendingParentSelectionTicks = 0;
            this.minecraft.player.displayClientMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.leaf_unavailable"), false);
        }
        return false;
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

    private PatternEncodingPreviewMenu.SyncedPatternProvider getPatternProvider(long providerId) {
        for (var provider : getVisibleProviders()) {
            if (provider.id() == providerId) {
                return provider;
            }
        }
        return null;
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

    private ProviderButtonHit getProviderButtonHit(double mouseX, double mouseY) {
        if (!isOverProviderList(mouseX, mouseY)) {
            return null;
        }

        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> providers = getVisibleProviders();
        int start = this.previewScrollbar.getCurrentScroll();
        int end = Math.min(providers.size(), start + getProviderVisibleRows());
        for (int rowIndex = start; rowIndex < end; rowIndex++) {
            int visibleRow = rowIndex - start;
            var provider = providers.get(rowIndex);
            if (getProviderButtonBounds(visibleRow).contains((int) mouseX, (int) mouseY)) {
                return new ProviderButtonHit(provider);
            }
        }
        return null;
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

    private void openProviderOrLeafPanel(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        if (provider.leaves().size() > 1) {
            this.leafPanel.open(provider);
            return;
        }
        PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf = provider.leaves().getFirst();
        if (!leaf.openable()) {
            this.minecraft.player.displayClientMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.leaf_open_unavailable"), false);
            return;
        }
        previewBridge().data_energistics$openPatternProviderLeafMenu(provider.id(), leaf.id());
    }

    private void closePreviewPanels() {
        this.previewVisible = false;
        this.leafPanel.close();
    }

    private AbstractWidget resolveEncodePatternWidget() {
        return this.widgets.widgets.get("encodePattern");
    }

    protected void applyEncodeButtonHint() {
        if (this.encodePatternWidget != null) {
            this.encodePatternWidget.setMessage(getEncodeButtonHint());
        }
    }

    protected Component getEncodeButtonHint() {
        if (isUploadEnabled()) {
            return ENCODE_BUTTON_HINT;
        }
        return this.originalEncodePatternMessage != null ? this.originalEncodePatternMessage : ENCODE_BUTTON_HINT;
    }

    private void initProviderSearchBox() {
        String currentText = this.providerSearchBox != null ? this.providerSearchBox.getValue() : "";
        this.providerSearchBox = new AETextField(this.getStyle(), this.font, 0, 0,
                getSearchBoxWidth(), getSearchBoxHeight());
        this.providerSearchBox.setMaxLength(40);
        this.providerSearchBox.setBordered(false);
        this.providerSearchBox.setVisible(false);
        this.providerSearchBox.setCanLoseFocus(true);
        this.providerSearchBox.setPlaceholder(SEARCH_BOX_HINT);
        this.providerSearchBox.setResponder(value -> {
            this.previewScrollbar.setCurrentScroll(0);
            invalidateVisibleProvidersCache();
        });
        this.providerSearchBox.setValue(currentText);
        this.addRenderableWidget(this.providerSearchBox);
    }

    private void initProviderRenameBox() {
        String currentText = this.providerRenameBox != null ? this.providerRenameBox.getValue() : "";
        this.providerRenameBox = new AETextField(this.getStyle(), this.font, 0, 0,
                getSearchBoxWidth(), Math.max(12, getSearchBoxHeight()));
        this.providerRenameBox.setMaxLength(40);
        this.providerRenameBox.setBordered(false);
        this.providerRenameBox.setVisible(false);
        this.providerRenameBox.setCanLoseFocus(false);
        this.providerRenameBox.setValue(currentText);
        this.addRenderableWidget(this.providerRenameBox);
    }

    private void initRecipeTypeToggleButton() {
        if (!(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }

        this.recipeTypeToggleButton = new PatternRecipeTypeToggleButton(
                enabled -> PatternEncodingPreferencesClient.setPatternSourceEnabled(this.menu, enabled));
        this.recipeTypeToggleButton.setState(sourceAware.data_energistics$isPatternSourceEnabled());
        this.addRenderableWidget(this.recipeTypeToggleButton);
    }

    private void initPreviewDragButton() {
        this.previewDragButton = new PatternEncodingPreviewDragButton();
        this.previewDragButton.setVisibility(false);
        this.addRenderableWidget(this.previewDragButton);
    }

    private void updateProviderSearchBox() {
        if (this.providerSearchBox == null) {
            return;
        }

        Rect2i previewBounds = getPreviewPanelBounds();
        this.providerSearchBox.setX(previewBounds.getX() + getSearchBoxX());
        this.providerSearchBox.setY(previewBounds.getY() + getSearchBoxY());
        this.providerSearchBox.setWidth(getSearchBoxWidth());
        this.providerSearchBox.setHeight(getSearchBoxHeight());
        boolean visible = this.previewVisible && !this.previewLayerWidgetRenderingDeferred && !isRenamingProvider();
        this.providerSearchBox.setVisible(visible);
        this.providerSearchBox.active = visible;
        if (!visible && !this.previewLayerWidgetRenderingDeferred) {
            this.providerSearchBox.setFocused(false);
        }
    }

    private void updateProviderRenameBox() {
        if (this.providerRenameBox == null) {
            return;
        }

        var provider = getPatternProvider(this.renamingProviderId);
        boolean visible = this.previewVisible && !this.previewLayerWidgetRenderingDeferred && isProviderRenameEnabled() && provider != null && provider.renameable();
        this.providerRenameBox.setVisible(visible);
        this.providerRenameBox.active = visible;
        if (!visible && !this.previewLayerWidgetRenderingDeferred) {
            this.providerRenameBox.setFocused(false);
            return;
        }

        Rect2i previewBounds = getPreviewPanelBounds();
        this.providerRenameBox.setX(previewBounds.getX() + getSearchBoxX());
        this.providerRenameBox.setY(previewBounds.getY() + getSearchBoxY());
        this.providerRenameBox.setWidth(getSearchBoxWidth());
        this.providerRenameBox.setHeight(Math.max(12, getSearchBoxHeight()));
    }

    private void updateRecipeTypeToggleButton() {
        if (this.recipeTypeToggleButton == null || !(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }

        this.recipeTypeToggleButton.setState(sourceAware.data_energistics$isPatternSourceEnabled());
        var rankingContext = previewBridge().data_energistics$getSyncedPatternProviderState().rankingContext();
        if (rankingContext != null) {
            this.recipeTypeToggleButton.setDetailLine(Component.translatable(
                    "button.data_energistics.pattern_encoding_recipe_type_toggle.detail",
                    PatternProviderRecipeTypeNames.resolveDisplayName(rankingContext.recipeTypeId())));
        } else {
            this.recipeTypeToggleButton.setDetailLine(Component.translatable(
                    "button.data_energistics.pattern_encoding_recipe_type_toggle.detail.none"));
        }
        boolean visible = this.menu.getMode() == EncodingMode.PROCESSING;
        this.recipeTypeToggleButton.visible = visible;
        this.recipeTypeToggleButton.active = visible;
        WidgetStyle clearButtonStyle = this.getStyle().getWidget("processingClearPattern");
        Point clearButtonPosition = clearButtonStyle.resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        this.recipeTypeToggleButton.setX(clearButtonPosition.getX() + 0);
        this.recipeTypeToggleButton.setY(clearButtonPosition.getY() + 10);
    }

    private void updatePreviewDragButton() {
        if (this.previewDragButton == null) {
            return;
        }

        this.previewDragButton.setVisibility(this.previewVisible && !this.previewLayerWidgetRenderingDeferred);
        if (!this.previewVisible) {
            return;
        }

        Rect2i previewBounds = getPreviewPanelBounds();
        this.previewDragButton.setX(previewBounds.getX() + previewBounds.getWidth() - this.previewDragButton.getWidth() - PREVIEW_DRAG_BUTTON_RIGHT_PADDING);
        this.previewDragButton.setY(previewBounds.getY() + PREVIEW_DRAG_BUTTON_TOP_PADDING);
    }

    private void updatePreviewPanelDragOffset(double mouseX, double mouseY) {
        Rect2i defaultBounds = this.previewPanelDragBaseBounds != null ? this.previewPanelDragBaseBounds : getDefaultPreviewPanelBounds();
        Rect2i draggedBounds = clampPreviewPanelBounds(
                (int) Math.round(mouseX) - this.previewPanelDragOffsetX,
                (int) Math.round(mouseY) - this.previewPanelDragOffsetY,
                defaultBounds.getWidth(),
                defaultBounds.getHeight());
        this.previewPanelCurrentOffsetX = draggedBounds.getX() - defaultBounds.getX();
        this.previewPanelCurrentOffsetY = draggedBounds.getY() - defaultBounds.getY();
        updatePreviewDragButton();
        updatePreviewScrollbar();
        updateProviderSearchBox();
        updateProviderRenameBox();
    }

    private void savePreviewPanelDragOffset(double mouseX, double mouseY) {
        updatePreviewPanelDragOffset(mouseX, mouseY);
        PatternEncodingPreferencesClient.setPreviewPanelOffset(
                this.menu, this.previewPanelCurrentOffsetX, this.previewPanelCurrentOffsetY);
        this.previewPanelDragBaseBounds = null;
    }

    private void syncPreferenceSnapshotIfProvidersChanged() {
        ObjectArrayList<String> leafDigests = new ObjectArrayList<>();
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : previewBridge().data_energistics$getSyncedPatternProviders()) {
            for (PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf : provider.leaves()) {
                leafDigests.add(leaf.providerDigest());
            }
        }
        leafDigests.sort(String::compareTo);
        StringBuilder leafSignatureBuilder = new StringBuilder();
        leafDigests.forEach(digest -> leafSignatureBuilder.append('\0').append(digest));
        String leafSignature = leafSignatureBuilder.toString();
        String contextSignature = this.menu instanceof PatternEncodingPreferenceMenu preferenceMenu ? Objects.toString(preferenceMenu.data_energistics$getPreferenceSession().rankingContext(), "") : "";
        String signature = contextSignature + '\1' + leafSignature;
        if (signature.equals(this.lastPreferenceLeafDigestSignature)) {
            return;
        }
        this.lastPreferenceLeafDigestSignature = signature;
        PatternEncodingPreferencesClient.sendSnapshot(this.menu);
    }

    private boolean isUploadEnabled() {
        return !(this.menu instanceof PatternEncodingSourceAware sourceAware) || sourceAware.data_energistics$isUploadEnabled();
    }

    protected boolean isProviderOpenEnabled() {
        return true;
    }

    protected boolean isProviderRenameEnabled() {
        return true;
    }

    private boolean triggerBlankPatternAutoCraft(double mouseX, double mouseY) {
        if (!usesNetworkBackedBlankPatternSlot()) {
            return false;
        }

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
        if (!(this.menu instanceof BlankPatternProxyMenu blankPatternProxyMenu) ||
                !blankPatternProxyMenu.data_energistics$usesNetworkBackedBlankPatternSlot()) {
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

    private boolean usesNetworkBackedBlankPatternSlot() {
        return this.menu instanceof BlankPatternProxyMenu blankPatternProxyMenu &&
                blankPatternProxyMenu.data_energistics$usesNetworkBackedBlankPatternSlot();
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

    private ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> getVisibleProviders() {
        if (!this.visibleProvidersCacheDirty) {
            return this.cachedVisibleProviders;
        }

        PatternEncodingPreviewMenu.SyncedPatternProviderList providerState = previewBridge().data_energistics$getSyncedPatternProviderState();
        String query = this.providerSearchBox != null ? this.providerSearchBox.getValue() : "";
        this.cachedVisibleProviders = PatternProviderDisplayOrder.order(
                providerState.providers(),
                query,
                this::getDefaultProviderName,
                PatternProviderRecipeTypeNames::resolve);
        this.visibleProvidersCacheDirty = false;
        return this.cachedVisibleProviders;
    }

    private void invalidateVisibleProvidersCache() {
        this.visibleProvidersCacheDirty = true;
    }

    private String getDefaultProviderName(ResourceLocation iconItemId) {
        return new ItemStack(BuiltInRegistries.ITEM.get(iconItemId)).getHoverName().getString();
    }

    private Rect2i getPreviewPanelBounds() {
        Rect2i defaultBounds = getDefaultPreviewPanelBounds();
        int offsetX = this.previewPanelDragging ? this.previewPanelCurrentOffsetX : previewLayout().data_energistics$getPreviewPanelOffsetX();
        int offsetY = this.previewPanelDragging ? this.previewPanelCurrentOffsetY : previewLayout().data_energistics$getPreviewPanelOffsetY();
        return clampPreviewPanelBounds(
                defaultBounds.getX() + offsetX,
                defaultBounds.getY() + offsetY,
                defaultBounds.getWidth(),
                defaultBounds.getHeight());
    }

    private Rect2i getDefaultPreviewPanelBounds() {
        int previewPanelWidth = getPreviewPanelWidth();
        int previewPanelHeight = getPreviewPanelHeight();
        Rect2i encodeButtonBounds = getEncodeButtonBounds();
        int preferredY = this.topPos + getPreviewPanelYOffset();
        int margin = getPreviewPanelMargin();
        int horizontalOffset = getPreviewPanelXOffset();
        return PatternEncodingPreviewPlacement.findBestBounds(
                encodeButtonBounds,
                previewPanelWidth,
                previewPanelHeight,
                preferredY,
                margin + horizontalOffset,
                horizontalOffset - margin,
                margin,
                this.width,
                this.height,
                getOccupiedPreviewAnchorZones());
    }

    private Rect2i getPreviewScrollbarBounds() {
        Rect2i listBounds = getProviderListBounds();
        int scrollbarWidth = this.previewScrollbar.getBounds().getWidth();
        return new Rect2i(
                getPreviewPanelBounds().getX() + getPreviewScrollbarX(),
                Math.max(4, listBounds.getY() - 1 + PREVIEW_SCROLLBAR_Y_OFFSET),
                scrollbarWidth,
                Math.max(1, listBounds.getHeight() + 2));
    }

    private Point getPreviewTooltipPosition(int mouseX, int mouseY, Rect2i previewBounds) {
        int minX = previewBounds.getX() + 4;
        int minY = previewBounds.getY() + 4;
        int maxX = Math.max(minX, this.width - 12);
        int maxY = Math.max(minY, this.height - 12);
        int x = Math.max(minX, Math.min(mouseX + 12, maxX));
        int y = Math.max(minY, Math.min(mouseY - 12, maxY));
        return new Point(x, y);
    }

    private Rect2i getEncodeButtonBounds() {
        if (this.encodePatternWidget != null && this.encodePatternWidget.visible) {
            return new Rect2i(
                    this.encodePatternWidget.getX(),
                    this.encodePatternWidget.getY(),
                    this.encodePatternWidget.getWidth(),
                    this.encodePatternWidget.getHeight());
        }

        WidgetStyle buttonStyle = this.getStyle().getWidget("encodePattern");
        Point position = buttonStyle.resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        int width = buttonStyle.getWidth() > 0 ? buttonStyle.getWidth() : 16;
        int height = buttonStyle.getHeight() > 0 ? buttonStyle.getHeight() : 16;
        return new Rect2i(position.getX(), position.getY(), width, height);
    }

    private Rect2i clampPreviewPanelBounds(int x, int y, int width, int height) {
        int clampedX = Math.max(4, Math.min(x, this.width - width - 4));
        int clampedY = Math.max(4, Math.min(y, this.height - height - 4));
        return new Rect2i(clampedX, clampedY, width, height);
    }

    private ObjectList<Rect2i> getOccupiedPreviewAnchorZones() {
        ObjectArrayList<Rect2i> zones = new ObjectArrayList<>(super.getExclusionZones());
        zones.add(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        ReferenceSet<AbstractWidget> seenWidgets = new ReferenceOpenHashSet<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                addOccupiedPreviewAnchorWidget(zones, seenWidgets, widget);
            }
        }
        for (AbstractWidget widget : this.widgets.widgets.values()) {
            addOccupiedPreviewAnchorWidget(zones, seenWidgets, widget);
        }
        return zones;
    }

    private void addOccupiedPreviewAnchorWidget(
                                                ObjectList<Rect2i> zones,
                                                ReferenceSet<AbstractWidget> seenWidgets,
                                                AbstractWidget widget) {
        if (!seenWidgets.add(widget) || !widget.visible || shouldIgnorePreviewAnchorWidget(widget)) {
            return;
        }
        zones.add(new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
    }

    private boolean shouldIgnorePreviewAnchorWidget(AbstractWidget widget) {
        return widget == this.encodePatternWidget || widget == this.providerSearchBox ||
                widget == this.providerRenameBox || widget == this.recipeTypeToggleButton ||
                widget == this.previewDragButton || this.leafPanel.ownsWidget(widget);
    }

    private Rect2i getProviderButtonBounds(int visibleRow) {
        Rect2i listBounds = getProviderListBounds();
        int x = listBounds.getX();
        int y = listBounds.getY() + visibleRow * (getProviderButtonHeight() + getProviderButtonGap());
        return new Rect2i(x, y, getProviderButtonWidth(), getProviderButtonHeight());
    }

    private void drawPreviewScrollbarHandle(GuiGraphics guiGraphics) {
        if (!this.previewScrollbar.isVisible()) {
            return;
        }

        Rect2i scrollbarBounds = getPreviewScrollbarBounds();
        int range = getHiddenProviderRows();
        int handleYOffset = 0;
        if (range > 0) {
            int availableHeight = scrollbarBounds.getHeight() - getPreviewScrollbarHandleHeight();
            handleYOffset = this.previewScrollbar.getCurrentScroll() * availableHeight / range;
        }

        ResourceLocation sprite = range == 0 ? AE2_SMALL_SCROLLBAR_DISABLED_TEXTURE : AE2_SMALL_SCROLLBAR_TEXTURE;
        Blitter.guiSprite(sprite)
                .dest(scrollbarBounds.getX(), scrollbarBounds.getY() + handleYOffset)
                .blit(guiGraphics);
    }

    private int getPreviewScrollbarHandleHeight() {
        return Scrollbar.SMALL.handleHeight();
    }

    private boolean isOverPreviewScrollbar(double mouseX, double mouseY) {
        Rect2i bounds = this.previewScrollbar.getBounds();
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth() && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    private int getHiddenProviderRows() {
        return Math.max(0, getVisibleProviders().size() - getProviderVisibleRows());
    }

    private boolean isOverProviderList(double mouseX, double mouseY) {
        Rect2i listBounds = getProviderListBounds();
        return mouseX >= listBounds.getX() && mouseX < listBounds.getX() + listBounds.getWidth() && mouseY >= listBounds.getY() && mouseY < listBounds.getY() + listBounds.getHeight();
    }

    private void drawPanelButton(GuiGraphics guiGraphics, Rect2i bounds, int fillColor) {
        guiGraphics.fill(bounds.getX(), bounds.getY(),
                bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight(),
                fillColor);
        guiGraphics.renderOutline(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
                COLOR_BUTTON_BORDER);
    }

    private void drawProviderButtonBackground(GuiGraphics guiGraphics, Rect2i bounds,
                                              PatternEncodingPreviewMenu.SyncedPatternProvider provider,
                                              boolean selected, boolean hovered) {
        if (isMePatternProvider(provider)) {
            ResourceLocation texture = selected ? AE2_BUTTON_DISABLED_TEXTURE : hovered ? AE2_BUTTON_HIGHLIGHTED_TEXTURE : AE2_BUTTON_TEXTURE;
            drawNineSlicedTexture(guiGraphics, texture, bounds,
                    BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT,
                    BUTTON_SLICE_BORDER, BUTTON_SLICE_BORDER, BUTTON_SLICE_BORDER, BUTTON_SLICE_BORDER);
            return;
        }

        drawPanelButton(guiGraphics, bounds,
                selected ? COLOR_BUTTON_SELECTED : hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
    }

    private Rect2i getProviderListBounds() {
        Rect2i panelBounds = getPreviewPanelBounds();
        int x = panelBounds.getX() + getPanelContentX();
        int y = panelBounds.getY() + getProviderListY();
        int width = panelBounds.getWidth() - getPanelContentX() - getPanelContentRight();
        int height = panelBounds.getHeight() - getProviderListY() - getPanelContentBottom();
        return new Rect2i(x, y, Math.max(1, width), Math.max(1, height));
    }

    private boolean isMePatternProvider(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        return provider.useAeButtonStyle();
    }

    protected int getPreviewPanelWidth() {
        return PREVIEW_PANEL_WIDTH;
    }

    protected int getPreviewPanelHeight() {
        return PREVIEW_PANEL_HEIGHT;
    }

    protected int getPreviewPanelMargin() {
        return DEFAULT_PREVIEW_PANEL_MARGIN;
    }

    protected int getPreviewPanelXOffset() {
        return DEFAULT_PREVIEW_PANEL_X_OFFSET;
    }

    protected int getPreviewPanelYOffset() {
        return DEFAULT_PREVIEW_PANEL_Y_OFFSET;
    }

    protected int getPanelContentX() {
        return DEFAULT_PANEL_CONTENT_X;
    }

    protected int getPanelContentRight() {
        return DEFAULT_PANEL_CONTENT_RIGHT;
    }

    protected int getPanelContentBottom() {
        return DEFAULT_PANEL_CONTENT_BOTTOM;
    }

    protected int getPanelTitleY() {
        return DEFAULT_PANEL_TITLE_Y;
    }

    protected int getSearchBoxX() {
        return DEFAULT_SEARCH_BOX_X;
    }

    protected int getSearchBoxY() {
        return DEFAULT_SEARCH_BOX_Y;
    }

    protected int getSearchBoxWidth() {
        return DEFAULT_SEARCH_BOX_WIDTH;
    }

    protected int getSearchBoxHeight() {
        return DEFAULT_SEARCH_BOX_HEIGHT;
    }

    protected int getProviderListY() {
        return DEFAULT_PROVIDER_LIST_Y;
    }

    protected int getProviderButtonGap() {
        return DEFAULT_PROVIDER_BUTTON_GAP;
    }

    protected int getProviderVisibleRows() {
        return DEFAULT_PROVIDER_VISIBLE_ROWS;
    }

    protected int getProviderButtonWidth() {
        return DEFAULT_PROVIDER_BUTTON_WIDTH;
    }

    protected int getProviderButtonHeight() {
        return DEFAULT_PROVIDER_BUTTON_HEIGHT;
    }

    protected int getPreviewScrollbarX() {
        return DEFAULT_PREVIEW_SCROLLBAR_X;
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
        guiGraphics.drawString(this.font, text,
                Math.round(x / scale),
                Math.round(y / scale),
                color,
                false);
        poseStack.popPose();
    }

    private int getScaledTextWidth(String text, float scale) {
        return (int) Math.ceil(this.font.width(text) * scale);
    }

    private int getProviderCountColor(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        int total = provider.patternSlotCount();
        if (total <= 0) {
            return COLOR_PROVIDER_COUNT_NORMAL;
        }

        int remaining = Math.max(0, total - provider.usedPatternSlotCount());
        return remaining * 9 < total * 2 ? COLOR_PROVIDER_COUNT_WARNING : COLOR_PROVIDER_COUNT_NORMAL;
    }

    private String trimToWidth(String text, int maxWidth, float scale) {
        if (getScaledTextWidth(text, scale) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = getScaledTextWidth("...", scale);
        int rawWidthLimit = Math.max(0, (int) Math.floor((maxWidth - ellipsisWidth) / scale));
        return this.font.plainSubstrByWidth(text, rawWidthLimit) + "...";
    }

    private ItemStack getProviderIconStack(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {
        return new ItemStack(BuiltInRegistries.ITEM.get(provider.iconItemId()));
    }

    @Override
    public PatternEncodingPreviewMenu leafPanelMenu() {
        return previewBridge();
    }

    @Override
    public Font leafPanelFont() {
        return this.font;
    }

    @Override
    public ScreenStyle leafPanelStyle() {
        return this.getStyle();
    }

    @Override
    public int leafPanelScreenWidth() {
        return this.width;
    }

    @Override
    public int leafPanelScreenHeight() {
        return this.height;
    }

    @Override
    public int leafPanelGuiLeft() {
        return this.leftPos;
    }

    @Override
    public int leafPanelGuiTop() {
        return this.topPos;
    }

    @Override
    public Rect2i leafPanelParentBounds() {
        return getPreviewPanelBounds();
    }

    @Override
    public ObjectList<Rect2i> leafPanelOccupiedZones() {
        return getOccupiedPreviewAnchorZones();
    }

    @Override
    public boolean leafPanelUploadEnabled() {
        return isUploadEnabled();
    }

    @Override
    public boolean leafPanelOpenEnabled() {
        return isProviderOpenEnabled();
    }

    @Override
    public boolean leafPanelRenameEnabled() {
        return isProviderRenameEnabled();
    }

    @Override
    public <W extends AbstractWidget> W registerLeafPanelWidget(W widget) {
        return this.addRenderableWidget(widget);
    }

    @Override
    public void selectRenamedProviderLeaf(String providerDigest) {
        this.pendingParentSelectionLeafDigest = providerDigest;
        this.pendingParentSelectionTicks = 0;
    }

    private record ProviderButtonHit(PatternEncodingPreviewMenu.SyncedPatternProvider provider) {}
}
