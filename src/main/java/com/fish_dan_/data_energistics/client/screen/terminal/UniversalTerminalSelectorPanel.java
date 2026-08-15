package com.fish_dan_.data_energistics.client.screen.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;

import java.util.List;
import java.util.function.Supplier;

public class UniversalTerminalSelectorPanel extends AbstractWidget {

    private static final ResourceLocation AE2_PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/universal_terminal_selector.png");
    private static final ResourceLocation CUSTOM_PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/gui/universal_terminal_selector.png");
    private static final ResourceLocation FALLBACK_PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/part/entity_speed_ticker_back.png");
    private static final ResourceLocation SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/item/portable_cell_screen.png");
    private static ResourceLocation resolvedPanelTexture;
    private static final int TEXTURE_SIZE = 16;
    private static final int PANEL_WIDTH = 96;
    private static final int PANEL_HEIGHT = 112;
    private static final int CARD_SIZE = 18;
    private static final int CARD_SPACING = 4;
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_ROWS = 3;
    private static final int PAGE_SIZE = GRID_COLUMNS * GRID_ROWS;
    private static final int GRID_X = 17;
    private static final int GRID_Y = 19;
    private static final int PAGE_BUTTON_SIZE = 12;
    private static final int PREV_X = 24;
    private static final int NEXT_X = 60;
    private static final int PAGE_BUTTON_Y = 92;
    private static final int PANEL_Y_OFFSET = 32;

    private final Screen screen;
    private final Supplier<AEBaseMenu> menuSupplier;
    private UniversalTerminalCycleButton anchorButton;
    private boolean open;
    private int page;

    public UniversalTerminalSelectorPanel(Screen screen, Supplier<AEBaseMenu> menuSupplier,
                                          UniversalTerminalCycleButton anchorButton) {
        super(0, 0, PANEL_WIDTH, PANEL_HEIGHT, Component.empty());
        this.screen = screen;
        this.menuSupplier = menuSupplier;
        this.anchorButton = anchorButton;
        this.visible = false;
        this.active = false;
    }

    public void setAnchorButton(UniversalTerminalCycleButton anchorButton) {
        this.anchorButton = anchorButton;
    }

    public void toggleOpen() {
        setOpen(!this.open);
    }

    public boolean isOpen() {
        return this.open;
    }

    public void restoreState(boolean open, int page) {
        this.page = Math.max(0, page);
        setOpen(open);
    }

    public Rect2i getExclusionArea() {
        updatePosition();
        return new Rect2i(this.getX(), this.getY(), this.width, this.height);
    }

    public void setOpen(boolean open) {
        AEBaseMenu menu = getMenu();
        if (open && (menu == null || !UniversalTerminalClientHelper.supportsUniversalTerminal(menu))) {
            open = false;
        }
        this.open = open;
        this.visible = open;
        this.active = open;
        clampPage();
        UniversalTerminalScreenHook.rememberSelectorState(this.open, this.page);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.open) {
            return;
        }

        updatePosition();
        clampPage();

        renderPanelBackground(guiGraphics);
        renderCards(guiGraphics, mouseX, mouseY);
        renderPageButtons(guiGraphics, mouseX, mouseY);
        renderPageLabel(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open || button != 0 || !this.active) {
            return false;
        }

        updatePosition();
        clampPage();

        if (isOverPrevButton(mouseX, mouseY) && this.page > 0) {
            this.page--;
            UniversalTerminalScreenHook.rememberSelectorState(this.open, this.page);
            return true;
        }

        if (isOverNextButton(mouseX, mouseY) && this.page < getPageCount() - 1) {
            this.page++;
            UniversalTerminalScreenHook.rememberSelectorState(this.open, this.page);
            return true;
        }

        AEBaseMenu menu = getMenu();
        if (menu == null) {
            setOpen(false);
            return true;
        }

        String terminalName = getTerminalAt(mouseX, mouseY);
        if (terminalName != null) {
            if (!terminalName.equals(UniversalTerminalClientHelper.getActiveTerminalName(menu))) {
                UniversalTerminalClientHelper.rememberMousePosition();
                UniversalTerminalClientHelper.sendSelectTerminal(terminalName);
            }
            setOpen(false);
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.open && mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}

    private void renderCards(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AEBaseMenu menu = getMenu();
        List<UniversalTerminalData.TerminalEntry> entries = menu != null ? UniversalTerminalClientHelper.getInstalledTerminalEntries(menu) : List.of();
        String activeTerminal = menu != null ? UniversalTerminalClientHelper.getActiveTerminalName(menu) : null;
        int startIndex = this.page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int terminalIndex = startIndex + i;
            int cardX = getCardX(i);
            int cardY = getCardY(i);
            boolean hasTerminal = terminalIndex < entries.size();
            UniversalTerminalData.TerminalEntry entry = hasTerminal ? entries.get(terminalIndex) : null;
            String terminalName = entry != null ? entry.name() : null;
            boolean hovered = contains(mouseX, mouseY, cardX, cardY, CARD_SIZE, CARD_SIZE);
            boolean selected = terminalName != null && terminalName.equals(activeTerminal);

            Icon background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : (selected ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND);
            background.getBlitter().dest(cardX - 1, cardY - 1, 20, 20).zOffset(18).blit(guiGraphics);
            guiGraphics.blit(SLOT_TEXTURE, cardX, cardY, 0, 0.0F, 0.0F, CARD_SIZE, CARD_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

            if (terminalName != null) {
                ItemStack icon = !entry.stack().isEmpty() ? entry.stack().copy() : UniversalTerminalData.getMenuIcon(terminalName);
                if (!icon.isEmpty()) {
                    guiGraphics.renderItem(icon, cardX + 1, cardY + 1, 0, 20);
                }
                if (selected) {
                    Icon.OVERLAY_ON.getBlitter().dest(cardX + 10, cardY + 10, 8, 8).zOffset(21).blit(guiGraphics);
                }
            }
        }
    }

    private void renderPageButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int prevX = this.getX() + PREV_X;
        int nextX = this.getX() + NEXT_X;
        int buttonY = this.getY() + PAGE_BUTTON_Y;

        renderPageButton(guiGraphics, prevX, buttonY, isOverPrevButton(mouseX, mouseY), this.page > 0, false);
        renderPageButton(guiGraphics, nextX, buttonY, isOverNextButton(mouseX, mouseY), this.page < getPageCount() - 1, true);
    }

    private void renderPageButton(GuiGraphics guiGraphics, int x, int y, boolean hovered, boolean enabled, boolean forward) {
        Icon background;
        if (!enabled) {
            background = Icon.TAB_BUTTON_BACKGROUND;
        } else if (hovered) {
            background = Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER;
        } else {
            background = Icon.TOOLBAR_BUTTON_BACKGROUND;
        }
        background.getBlitter().dest(x - 1, y - 1, PAGE_BUTTON_SIZE + 2, PAGE_BUTTON_SIZE + 2).zOffset(18).blit(guiGraphics);
        guiGraphics.blit(SLOT_TEXTURE, x, y, 0, 0.0F, 0.0F, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        (forward ? Icon.ARROW_RIGHT : Icon.ARROW_LEFT)
                .getBlitter()
                .dest(x, y, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE)
                .zOffset(19)
                .blit(guiGraphics);
    }

    private void renderPageLabel(GuiGraphics guiGraphics) {
        Font font = Minecraft.getInstance().font;
        int totalPages = getPageCount();
        Component label = Component.translatable("screen.data_energistics.page", this.page + 1, totalPages);
        int textX = this.getX() + (this.width - font.width(label)) / 2;
        guiGraphics.drawString(font, label, textX, this.getY() + 93, 0xFFE6EDF3, false);
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (isOverPrevButton(mouseX, mouseY) && this.page > 0) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font,
                    Component.translatable("screen.data_energistics.page.previous"),
                    mouseX, mouseY);
            return;
        }

        if (isOverNextButton(mouseX, mouseY) && this.page < getPageCount() - 1) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font,
                    Component.translatable("screen.data_energistics.page.next"),
                    mouseX, mouseY);
            return;
        }

        UniversalTerminalData.TerminalEntry hoveredEntry = getTerminalEntryAt(mouseX, mouseY);
        if (hoveredEntry != null) {
            ItemStack icon = !hoveredEntry.stack().isEmpty() ? hoveredEntry.stack().copy() : UniversalTerminalData.getMenuIcon(hoveredEntry.name());
            if (!icon.isEmpty()) {
                guiGraphics.renderTooltip(Minecraft.getInstance().font, icon, mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(Minecraft.getInstance().font,
                        UniversalTerminalData.getTerminalDisplayName(hoveredEntry.name()), mouseX, mouseY);
            }
        }
    }

    private void updatePosition() {
        if (this.anchorButton == null) {
            return;
        }
        int preferredX = this.anchorButton.getX() - this.width - 4;
        int preferredY = this.anchorButton.getY() - PANEL_Y_OFFSET;

        if (preferredX < 4) {
            preferredX = this.anchorButton.getX() + this.anchorButton.getWidth() + 4;
        }

        preferredX = Math.max(4, Math.min(preferredX, this.screen.width - this.width - 4));
        preferredY = Math.max(4, Math.min(preferredY, this.screen.height - this.height - 4));
        this.setX(preferredX);
        this.setY(preferredY);
    }

    private void renderPanelBackground(GuiGraphics guiGraphics) {
        ResourceLocation panelTexture = resolvePanelTexture();
        if (panelTexture == AE2_PANEL_TEXTURE || panelTexture == CUSTOM_PANEL_TEXTURE) {
            guiGraphics.blit(
                    panelTexture,
                    this.getX(),
                    this.getY(),
                    0,
                    0.0F,
                    0.0F,
                    this.width,
                    this.height,
                    this.width,
                    this.height);
            return;
        }

        for (int tileX = 0; tileX < this.width; tileX += TEXTURE_SIZE) {
            for (int tileY = 0; tileY < this.height; tileY += TEXTURE_SIZE) {
                int drawWidth = Math.min(TEXTURE_SIZE, this.width - tileX);
                int drawHeight = Math.min(TEXTURE_SIZE, this.height - tileY);
                guiGraphics.blit(
                        panelTexture,
                        this.getX() + tileX,
                        this.getY() + tileY,
                        0,
                        0.0F,
                        0.0F,
                        drawWidth,
                        drawHeight,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE);
            }
        }
    }

    private static ResourceLocation resolvePanelTexture() {
        if (resolvedPanelTexture != null) {
            return resolvedPanelTexture;
        }

        var resourceManager = Minecraft.getInstance().getResourceManager();
        if (resourceManager.getResource(AE2_PANEL_TEXTURE).isPresent()) {
            resolvedPanelTexture = AE2_PANEL_TEXTURE;
            return resolvedPanelTexture;
        }
        if (resourceManager.getResource(CUSTOM_PANEL_TEXTURE).isPresent()) {
            resolvedPanelTexture = CUSTOM_PANEL_TEXTURE;
            return resolvedPanelTexture;
        }
        resolvedPanelTexture = FALLBACK_PANEL_TEXTURE;
        return resolvedPanelTexture;
    }

    private void clampPage() {
        int pageCount = getPageCount();
        if (this.page >= pageCount) {
            this.page = pageCount - 1;
        }
        if (this.page < 0) {
            this.page = 0;
        }
        UniversalTerminalScreenHook.rememberSelectorState(this.open, this.page);
    }

    private int getPageCount() {
        AEBaseMenu menu = getMenu();
        int size = menu != null ? UniversalTerminalClientHelper.getInstalledTerminalNames(menu).size() : 0;
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int getCardX(int slotIndex) {
        return this.getX() + GRID_X + (slotIndex % GRID_COLUMNS) * (CARD_SIZE + CARD_SPACING);
    }

    private int getCardY(int slotIndex) {
        return this.getY() + GRID_Y + (slotIndex / GRID_COLUMNS) * (CARD_SIZE + CARD_SPACING);
    }

    private boolean isOverPrevButton(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, this.getX() + PREV_X, this.getY() + PAGE_BUTTON_Y, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE);
    }

    private boolean isOverNextButton(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, this.getX() + NEXT_X, this.getY() + PAGE_BUTTON_Y, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE);
    }

    private String getTerminalAt(double mouseX, double mouseY) {
        UniversalTerminalData.TerminalEntry entry = getTerminalEntryAt(mouseX, mouseY);
        return entry != null ? entry.name() : null;
    }

    private UniversalTerminalData.TerminalEntry getTerminalEntryAt(double mouseX, double mouseY) {
        AEBaseMenu menu = getMenu();
        List<UniversalTerminalData.TerminalEntry> terminals = menu != null ? UniversalTerminalClientHelper.getInstalledTerminalEntries(menu) : List.of();
        int startIndex = this.page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = startIndex + i;
            if (index >= terminals.size()) {
                break;
            }
            if (contains(mouseX, mouseY, getCardX(i), getCardY(i), CARD_SIZE, CARD_SIZE)) {
                return terminals.get(index);
            }
        }
        return null;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private AEBaseMenu getMenu() {
        return this.menuSupplier.get();
    }
}
