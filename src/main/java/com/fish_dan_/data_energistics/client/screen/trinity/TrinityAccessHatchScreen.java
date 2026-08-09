package com.fish_dan_.data_energistics.client.screen.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.menu.trinity.TrinityAccessHatchMenu;
import com.fish_dan_.data_energistics.mixin.client.PatternAccessTermScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.me.patternaccess.PatternSlot;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pattern-access terminal for the Trinity ME access hatch, with structure management actions and EAE-style search
 * scopes.
 */
public class TrinityAccessHatchScreen extends PatternAccessTermScreen<TrinityAccessHatchMenu>
                                      implements Ae2NativeSlotHighlight {

    private static final int PANEL_BACKGROUND = 0xFFE3E3EA;
    private static final int PANEL_BORDER = 0xFF696D88;
    private static final int PANEL_TEXT = 0xFF404040;
    private static final int MATCHED_PATTERN_TINT = 0x406FCE8E;
    private static final int MATCHED_PATTERN_BORDER = 0xFF4F9C70;
    private static final int UNMATCHED_PATTERN_TINT = 0x90000000;
    private static final String REFUND_PATTERNS_SUBJECT_KEY = "message.data_energistics.trinity_data_core.refund.subject.patterns";
    private static final String REFUND_RETAINED_ITEMS_SUBJECT_KEY = "message.data_energistics.trinity_data_core.refund.subject.retained_items";
    private static final TrinityPatternSearchMatcher SEARCH_MATCHER = new TrinityPatternSearchMatcher();
    private static final TrinityAccessHatchLayout LAYOUT = TrinityAccessHatchLayout.load();

    private final Map<AEItemKey, PatternSearchNames> patternSearchNamesByDefinition = new HashMap<>();
    private TrinityPatternSearchMode searchMode = TrinityPatternSearchMode.INPUT_OUTPUT;
    private boolean incrementalSearchRefreshPending;
    @Nullable
    private ManagementButton searchModeButton;
    @Nullable
    private ManagementButton refundPatternsButton;
    @Nullable
    private ManagementButton refundRetainedItemsButton;

    /**
     * Creates a standard AE2 pattern-access screen and reserves a right-hand management column.
     */
    public TrinityAccessHatchScreen(TrinityAccessHatchMenu menu,
                                    Inventory playerInventory,
                                    Component title,
                                    ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = LAYOUT.screenWidth();
    }

    /**
     * Recreates the management controls after AE2 changes terminal row layout.
     */
    @Override
    public void init() {
        super.init();
        TrinityAccessHatchLayout.Button searchModeLayout = LAYOUT.searchModeButton();
        this.searchModeButton = addRenderableWidget(new ManagementButton(
                managementButtonX(searchModeLayout),
                this.topPos + searchModeLayout.top(),
                searchModeLayout,
                searchModeMessage(),
                Component.translatable("button.data_energistics.trinity_access_hatch.search_mode.hint"),
                this::cycleSearchMode));
        TrinityAccessHatchLayout.Button refundPatternsLayout = LAYOUT.refundPatternsButton();
        this.refundPatternsButton = addRenderableWidget(new ManagementButton(
                managementButtonX(refundPatternsLayout),
                this.topPos + refundPatternsLayout.top(),
                refundPatternsLayout,
                Component.translatable("button.data_energistics.trinity_data_core.refund_patterns"),
                Component.translatable("button.data_energistics.trinity_data_core.refund_patterns"),
                this.menu::requestRefundPatterns));
        TrinityAccessHatchLayout.Button refundRetainedItemsLayout = LAYOUT.refundRetainedItemsButton();
        this.refundRetainedItemsButton = addRenderableWidget(new ManagementButton(
                managementButtonX(refundRetainedItemsLayout),
                this.topPos + refundRetainedItemsLayout.top(),
                refundRetainedItemsLayout,
                Component.translatable("button.data_energistics.trinity_data_core.refund_retained_items"),
                Component.translatable("button.data_energistics.trinity_data_core.refund_retained_items"),
                this.menu::requestRefundRetainedItems));
        refreshRefundButtonStates();
    }

    /**
     * Draws the management panel without replacing AE2's native terminal texture, rows, scrollbar, or slots.
     */
    @Override
    public void drawBG(GuiGraphics guiGraphics,
                       int offsetX,
                       int offsetY,
                       int mouseX,
                       int mouseY,
                       float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        int panelLeft = offsetX + LAYOUT.managementPanel().left();
        int panelRight = panelLeft + LAYOUT.managementPanel().width();
        int panelBottom = offsetY + this.imageHeight;
        guiGraphics.fill(panelLeft, offsetY, panelRight, panelBottom, PANEL_BORDER);
        guiGraphics.fill(panelLeft + 1, offsetY + 1, panelRight - 1, panelBottom - 1, PANEL_BACKGROUND);
    }

    /**
     * Labels the added panel in the same foreground pass as AE2's provider rows.
     */
    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        drawPatternSearchFeedback(guiGraphics);
        guiGraphics.drawString(
                this.font,
                Component.translatable("screen.data_energistics.trinity_access_hatch.management"),
                LAYOUT.title().left(),
                LAYOUT.title().top(),
                PANEL_TEXT,
                false);
    }

    /**
     * Polls the two independent refund acknowledgements and keeps each button disabled only for its own request.
     */
    @Override
    public void containerTick() {
        super.containerTick();
        refreshSearchAfterIncrementalUpdate();
        refreshRefundButtonStates();
        displayRefundResult(
                REFUND_PATTERNS_SUBJECT_KEY,
                this.menu.consumeRefundPatternsResult());
        displayRefundResult(
                REFUND_RETAINED_ITEMS_SUBJECT_KEY,
                this.menu.consumeRefundRetainedItemsResult());
    }

    /**
     * Builds the localized pattern candidate text selected by the current search mode.
     *
     * <p>
     * This is the narrow entry point used by the client mixin for AE2's private search-text cache. An encoded stack
     * that AE2 cannot decode contributes an empty candidate, matching the native access-terminal behavior for invalid
     * patterns.
     * </p>
     *
     * @param stack encoded pattern synchronized by the native terminal menu
     * @return normalized candidate text for AE2's existing search filter
     */
    public String buildPatternSearchText(ItemStack stack) {
        AEItemKey definition = Objects.requireNonNull(
                AEItemKey.of(stack),
                "encoded pattern definition must not be empty");
        PatternSearchNames names = this.patternSearchNamesByDefinition.computeIfAbsent(
                definition,
                ignored -> decodePatternSearchNames(stack));
        return SEARCH_MATCHER.createSearchText(names.inputs(), names.outputs(), this.searchMode);
    }

    /**
     * Applies EAE's ordered token matching to one cached pattern candidate.
     *
     * @param stack encoded pattern synchronized by AE2
     * @param query current native search term
     * @return whether one selected input or output name matches all ordered query tokens
     */
    public boolean matchesPatternSearch(ItemStack stack, String query) {
        PatternAccessTermScreenAccessor accessor = (PatternAccessTermScreenAccessor) this;
        String searchText = accessor.dataEnergistics$getPatternSearchText()
                .computeIfAbsent(stack, this::buildPatternSearchText);
        return SEARCH_MATCHER.matchesSearchText(searchText, query);
    }

    /**
     * Normalizes search input identically for native provider filtering and per-pattern feedback.
     *
     * @param value current native search-box value
     * @return trimmed, locale-stable lower-case text
     */
    public String normalizeSearchQuery(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Clears native and decoded-name caches before replacing the complete provider directory.
     */
    @Override
    public void clear() {
        this.patternSearchNamesByDefinition.clear();
        this.incrementalSearchRefreshPending = false;
        clearNativeSearchCaches();
        super.clear();
    }

    /**
     * Coalesces one or more AE2 slot deltas into a single list rebuild on the next client tick.
     */
    @Override
    public void postIncrementalUpdate(long inventoryId, Int2ObjectMap<ItemStack> slots) {
        super.postIncrementalUpdate(inventoryId, slots);
        this.patternSearchNamesByDefinition.clear();
        clearNativeSearchCaches();
        this.incrementalSearchRefreshPending = true;
    }

    private void cycleSearchMode() {
        this.searchMode = this.searchMode.next();
        if (this.searchModeButton == null) {
            throw new IllegalStateException("Search-mode button is unavailable after screen initialization");
        }
        this.searchModeButton.setMessage(searchModeMessage());

        PatternAccessTermScreenAccessor accessor = (PatternAccessTermScreenAccessor) this;
        clearNativeSearchCaches();
        accessor.dataEnergistics$refreshList();
    }

    /** Resolves one XML-declared button's local x-coordinate against the current native screen origin. */
    private int managementButtonX(TrinityAccessHatchLayout.Button layout) {
        return this.leftPos + LAYOUT.managementPanel().left() + layout.left();
    }

    private void drawPatternSearchFeedback(GuiGraphics guiGraphics) {
        PatternAccessTermScreenAccessor accessor = (PatternAccessTermScreenAccessor) this;
        AETextField searchField = accessor.dataEnergistics$getSearchField();
        String query = normalizeSearchQuery(searchField.getValue());
        if (query.isEmpty()) {
            return;
        }

        for (Slot slot : this.menu.slots) {
            if (!(slot instanceof PatternSlot patternSlot) || !patternSlot.hasItem()) {
                continue;
            }
            ItemStack encodedPattern = patternSlot.getItem();
            boolean providerMatches = patternSlot.getMachineInv()
                    .getSearchName()
                    .toLowerCase(Locale.ROOT)
                    .contains(query);
            boolean patternMatches = matchesPatternSearch(encodedPattern, query);
            if (patternMatches) {
                guiGraphics.fill(
                        patternSlot.x,
                        patternSlot.y,
                        patternSlot.x + 16,
                        patternSlot.y + 16,
                        MATCHED_PATTERN_TINT);
                guiGraphics.renderOutline(
                        patternSlot.x - 1,
                        patternSlot.y - 1,
                        18,
                        18,
                        MATCHED_PATTERN_BORDER);
            } else if (!providerMatches) {
                guiGraphics.fill(
                        patternSlot.x,
                        patternSlot.y,
                        patternSlot.x + 16,
                        patternSlot.y + 16,
                        UNMATCHED_PATTERN_TINT);
            }
        }
    }

    private PatternSearchNames decodePatternSearchNames(ItemStack stack) {
        try {
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, this.menu.getPlayer().level());
            if (pattern == null) {
                return PatternSearchNames.EMPTY;
            }

            List<String> inputNames = new ArrayList<>();
            IPatternDetails.IInput[] inputs = pattern.getInputs();
            if (inputs != null) {
                for (IPatternDetails.IInput input : inputs) {
                    if (input == null) {
                        continue;
                    }
                    GenericStack[] alternatives = input.getPossibleInputs();
                    if (alternatives != null && alternatives.length > 0 && alternatives[0] != null) {
                        inputNames.add(displayName(alternatives[0]));
                    }
                }
            }

            List<String> outputNames = new ArrayList<>();
            List<GenericStack> outputs = pattern.getOutputs();
            if (outputs != null) {
                for (GenericStack output : outputs) {
                    if (output != null) {
                        outputNames.add(displayName(output));
                    }
                }
            }
            return new PatternSearchNames(inputNames, outputNames);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.warn(
                    "Skipping malformed pattern search data for {}",
                    stack,
                    exception);
            return PatternSearchNames.EMPTY;
        }
    }

    private static String displayName(GenericStack stack) {
        return Objects.requireNonNull(
                stack.what(),
                "decoded pattern key must not be null").getDisplayName().getString();
    }

    private void clearNativeSearchCaches() {
        PatternAccessTermScreenAccessor accessor = (PatternAccessTermScreenAccessor) this;
        accessor.dataEnergistics$getPatternSearchText().clear();
        accessor.dataEnergistics$getCachedSearches().clear();
    }

    private void refreshSearchAfterIncrementalUpdate() {
        if (!this.incrementalSearchRefreshPending) {
            return;
        }
        this.incrementalSearchRefreshPending = false;
        PatternAccessTermScreenAccessor accessor = (PatternAccessTermScreenAccessor) this;
        accessor.dataEnergistics$refreshList();
    }

    private Component searchModeMessage() {
        String key = switch (this.searchMode) {
            case INPUT -> "button.data_energistics.trinity_access_hatch.search_mode.input";
            case OUTPUT -> "button.data_energistics.trinity_access_hatch.search_mode.output";
            case INPUT_OUTPUT -> "button.data_energistics.trinity_access_hatch.search_mode.input_output";
        };
        return Component.translatable(key);
    }

    private void refreshRefundButtonStates() {
        if (this.refundPatternsButton == null || this.refundRetainedItemsButton == null) {
            return;
        }
        boolean connected = this.menu.getLinkStatus().connected();
        this.refundPatternsButton.active = connected && !this.menu.isRefundPatternsPending();
        this.refundRetainedItemsButton.active = connected && !this.menu.isRefundRetainedItemsPending();
    }

    private void displayRefundResult(String subjectTranslationKey, @Nullable TrinityHostedActionStatus status) {
        if (status == null) {
            return;
        }
        String resultTranslationKey = switch (status) {
            case COMPLETED -> "message.data_energistics.trinity_data_core.refund.completed";
            case NO_OP -> "message.data_energistics.trinity_data_core.refund.no_op";
            case STALE_STATE -> "message.data_energistics.trinity_data_core.refund.stale_state";
            case DELIVERY_FAILED -> "message.data_energistics.trinity_data_core.refund.delivery_failed";
            case INTERNAL_ERROR -> "message.data_energistics.trinity_data_core.refund.internal_error";
            case REJECTED -> "message.data_energistics.trinity_data_core.refund.rejected";
        };
        this.menu.getPlayer().displayClientMessage(
                Component.translatable(
                        resultTranslationKey,
                        Component.translatable(subjectTranslationKey)),
                true);
    }

    /**
     * Immutable localized candidates decoded once per encoded pattern definition.
     */
    private record PatternSearchNames(List<String> inputs, List<String> outputs) {

        private static final PatternSearchNames EMPTY = new PatternSearchNames(List.of(), List.of());

        private PatternSearchNames {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }

    /**
     * Compact left-aligned button that preserves the three gray-blue states used by the former Data Core panel.
     */
    private static final class ManagementButton extends AbstractButton {

        private static final int BASE_FILL = 0xFFA7ABBA;
        private static final int BASE_BORDER = 0xFF696D88;
        private static final int HOVER_FILL = 0xFFC0C4D0;
        private static final int HOVER_BORDER = 0xFF4D5168;
        private static final int PRESSED_FILL = 0xFF9297A9;
        private static final int PRESSED_BORDER = 0xFF35394D;
        private static final int DISABLED_FILL = 0xFF9295A1;
        private static final int DISABLED_BORDER = 0xFF686B78;
        private static final int ACTIVE_TEXT = 0xFF2C3040;
        private static final int DISABLED_TEXT = 0xFF555866;
        private final Runnable action;
        private final int textLeftPadding;
        private final float textScale;

        private ManagementButton(int x,
                                 int y,
                                 TrinityAccessHatchLayout.Button layout,
                                 Component message,
                                 Component tooltip,
                                 Runnable action) {
            super(x, y, layout.width(), layout.height(), message);
            this.action = action;
            this.textLeftPadding = layout.textLeftPadding();
            this.textScale = layout.textScale();
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        public void onPress() {
            this.action.run();
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean pressed = this.active && this.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed();
            int fillColor;
            int borderColor;
            if (!this.active) {
                fillColor = DISABLED_FILL;
                borderColor = DISABLED_BORDER;
            } else if (pressed) {
                fillColor = PRESSED_FILL;
                borderColor = PRESSED_BORDER;
            } else if (this.isHoveredOrFocused()) {
                fillColor = HOVER_FILL;
                borderColor = HOVER_BORDER;
            } else {
                fillColor = BASE_FILL;
                borderColor = BASE_BORDER;
            }

            guiGraphics.fill(
                    this.getX(),
                    this.getY(),
                    this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(),
                    borderColor);
            guiGraphics.fill(
                    this.getX() + 1,
                    this.getY() + 1,
                    this.getX() + this.getWidth() - 1,
                    this.getY() + this.getHeight() - 1,
                    fillColor);
            renderButtonText(guiGraphics, this.active ? ACTIVE_TEXT : DISABLED_TEXT);
        }

        private void renderButtonText(GuiGraphics guiGraphics, int color) {
            Font font = Minecraft.getInstance().font;
            int rawTextWidth = Math.max(
                    0,
                    (int) Math.floor((this.getWidth() - this.textLeftPadding * 2) / this.textScale));
            String text = font.plainSubstrByWidth(this.getMessage().getString(), rawTextWidth);
            float scaledTextHeight = font.lineHeight * this.textScale;
            float textX = this.getX() + this.textLeftPadding;
            float textY = this.getY() + (this.getHeight() - scaledTextHeight) / 2.0F;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(textX, textY, 0.0F);
            guiGraphics.pose().scale(this.textScale, this.textScale, 1.0F);
            guiGraphics.drawString(font, text, 0, 0, color, false);
            guiGraphics.pose().popPose();
        }
    }
}
