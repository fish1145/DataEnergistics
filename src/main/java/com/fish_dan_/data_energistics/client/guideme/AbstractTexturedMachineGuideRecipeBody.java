package com.fish_dan_.data_energistics.client.guideme;

import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import guideme.siteexport.ResourceExporter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

abstract class AbstractTexturedMachineGuideRecipeBody extends LytBlock implements InteractiveElement {

    protected static final int SLOT_SIZE = 18;
    protected static final int CONTENT_OFFSET = 1;
    protected static final long DISPLAY_CYCLE_MS = 2000L;
    protected static final float SMALL_OVERLAY_SCALE = 0.5F;

    private final ResourceLocation texture;
    private final int textureU;
    private final int textureV;
    private final int width;
    private final int height;
    private final @Nullable ResourceLocation progressTexture;
    private final int progressX;
    private final int progressY;
    private final int progressWidth;
    private final int progressHeight;

    protected AbstractTexturedMachineGuideRecipeBody(
                                                     ResourceLocation texture,
                                                     int textureU,
                                                     int textureV,
                                                     int width,
                                                     int height,
                                                     @Nullable ResourceLocation progressTexture,
                                                     int progressX,
                                                     int progressY,
                                                     int progressWidth,
                                                     int progressHeight) {
        this.texture = texture;
        this.textureU = textureU;
        this.textureV = textureV;
        this.width = width;
        this.height = height;
        this.progressTexture = progressTexture;
        this.progressX = progressX;
        this.progressY = progressY;
        this.progressWidth = progressWidth;
        this.progressHeight = progressHeight;
    }

    @Override
    protected final LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return new LytRect(x, y, this.width, this.height);
    }

    @Override
    protected final void onLayoutMoved(int deltaX, int deltaY) {}

    @Override
    public final void renderBatch(RenderContext context, MultiBufferSource buffers) {}

    @Override
    public final void render(RenderContext context) {
        if (!context.intersectsViewport(this.bounds)) {
            return;
        }

        GuiGraphics guiGraphics = context.guiGraphics();
        guiGraphics.blit(this.texture, this.bounds.x(), this.bounds.y(), 0,
                (float) this.textureU, (float) this.textureV, this.width, this.height, 256, 256);

        if (this.progressTexture != null) {
            renderProgress(guiGraphics);
        }

        renderBody(context);
    }

    @Override
    public final Optional<GuideTooltip> getTooltip(float x, float y) {
        if (this.progressTexture != null && progressRect().contains((int) x, (int) y)) {
            List<Component> lines = getProgressTooltipLines();
            if (!lines.isEmpty()) {
                return Optional.of(createLinesTooltip(ItemStack.EMPTY, lines));
            }
        }

        return getTooltipAt(x, y);
    }

    protected abstract void renderBody(RenderContext context);

    protected Optional<GuideTooltip> getTooltipAt(float x, float y) {
        return Optional.empty();
    }

    protected List<Component> getProgressTooltipLines() {
        return List.of();
    }

    protected int getProgressCycleMs() {
        return 2000;
    }

    protected final void renderProgress(GuiGraphics guiGraphics) {
        int cycleMs = Math.max(1, getProgressCycleMs());
        double progress = (System.currentTimeMillis() % cycleMs) / (double) cycleMs;
        int filled = (int) Math.round(progress * this.progressHeight);
        if (filled <= 0) {
            return;
        }

        int x = this.bounds.x() + this.progressX;
        int y = this.bounds.y() + this.progressY + this.progressHeight - filled;
        int v = this.progressHeight - filled;
        guiGraphics.blit(this.progressTexture, x, y, 0,
                176.0F, (float) v, this.progressWidth, filled, 256, 256);
    }

    protected final void renderItemStack(RenderContext context, ItemStack stack, int relativeX, int relativeY) {
        if (stack.isEmpty()) {
            return;
        }

        context.renderItem(
                stack,
                this.bounds.x() + relativeX + CONTENT_OFFSET,
                this.bounds.y() + relativeY + CONTENT_OFFSET,
                16,
                16);
    }

    protected final void renderGenericStack(RenderContext context, GenericStack stack, int relativeX, int relativeY) {
        GuiGraphics guiGraphics = context.guiGraphics();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        CustomKeyGuiRenderer.draw(
                Minecraft.getInstance(),
                guiGraphics,
                this.bounds.x() + relativeX,
                this.bounds.y() + relativeY,
                stack.what());
        guiGraphics.pose().popPose();
    }

    protected final void renderGenericStackAmount(
                                                  RenderContext context,
                                                  GenericStack stack,
                                                  int relativeX,
                                                  int relativeY,
                                                  String amountText) {
        var font = Minecraft.getInstance().font;
        var guiGraphics = context.guiGraphics();
        int textWidth = font.width(amountText);
        float scaledWidth = textWidth * SMALL_OVERLAY_SCALE;
        float scaledHeight = font.lineHeight * SMALL_OVERLAY_SCALE;
        float drawX = this.bounds.x() + relativeX + SLOT_SIZE - 1 - scaledWidth;
        float drawY = this.bounds.y() + relativeY + SLOT_SIZE - 1 - scaledHeight;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        guiGraphics.pose().scale(SMALL_OVERLAY_SCALE, SMALL_OVERLAY_SCALE, 1.0F);
        guiGraphics.drawString(
                font,
                amountText,
                Math.round(drawX / SMALL_OVERLAY_SCALE),
                Math.round(drawY / SMALL_OVERLAY_SCALE),
                16777215,
                true);
        guiGraphics.pose().popPose();
    }

    protected final ItemStack getDisplayedStack(ItemStack[] stacks) {
        if (stacks.length == 0) {
            return ItemStack.EMPTY;
        }

        long cycle = System.nanoTime() / TimeUnit.MILLISECONDS.toNanos(DISPLAY_CYCLE_MS);
        return stacks[(int) (cycle % stacks.length)].copy();
    }

    protected final Optional<GuideTooltip> getItemTooltipIfHovered(
                                                                   float mouseX,
                                                                   float mouseY,
                                                                   ItemStack stack,
                                                                   int relativeX,
                                                                   int relativeY) {
        if (!stack.isEmpty() && slotRect(relativeX, relativeY).contains((int) mouseX, (int) mouseY)) {
            return Optional.of(createItemTooltip(stack));
        }
        return Optional.empty();
    }

    protected final Optional<GuideTooltip> getGenericTooltipIfHovered(
                                                                      float mouseX,
                                                                      float mouseY,
                                                                      GenericStack stack,
                                                                      int relativeX,
                                                                      int relativeY,
                                                                      List<Component> extraLines) {
        if (slotRect(relativeX, relativeY).contains((int) mouseX, (int) mouseY)) {
            return Optional.of(createGenericStackTooltip(stack, extraLines));
        }
        return Optional.empty();
    }

    protected final GuideTooltip createItemTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        if (stack.getCount() > 1) {
            lines.add(Component.literal("x" + stack.getCount()));
        }
        return createLinesTooltip(stack.copy(), lines);
    }

    protected final GuideTooltip createGenericStackTooltip(GenericStack stack, List<Component> extraLines) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.what().getDisplayName());
        lines.add(Component.literal(formatFullAmount(stack)));
        lines.addAll(extraLines);
        return createLinesTooltip(stack.what().wrapForDisplayOrFilter(), lines);
    }

    protected final String formatFullAmount(GenericStack stack) {
        return stack.what().formatAmount(stack.amount(), AmountFormat.FULL);
    }

    protected final String formatCompactAmount(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey) {
            return GenericStackDisplayHelper.formatCompactFluidAmount(stack.amount());
        }
        return formatCompactAmount(stack.amount());
    }

    protected static String formatCompactAmount(long amount) {
        long abs = Math.abs(amount);
        if (abs < 1_000L) {
            return Long.toString(amount);
        }
        if (abs < 1_000_000L) {
            return compact(amount, 1_000D, "K");
        }
        if (abs < 1_000_000_000L) {
            return compact(amount, 1_000_000D, "M");
        }
        if (abs < 1_000_000_000_000L) {
            return compact(amount, 1_000_000_000D, "B");
        }
        return compact(amount, 1_000_000_000_000D, "T");
    }

    private static String compact(long amount, double divisor, String suffix) {
        double value = amount / divisor;
        double abs = Math.abs(value);
        if (abs >= 100 || Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.format(Locale.ROOT, "%.0f%s", value, suffix);
        }
        return String.format(Locale.ROOT, "%.1f%s", value, suffix);
    }

    protected final GuideTooltip createLinesTooltip(ItemStack icon, List<Component> lines) {
        return new LinesTooltip(icon, lines);
    }

    protected final LytRect slotRect(int relativeX, int relativeY) {
        return new LytRect(this.bounds.x() + relativeX, this.bounds.y() + relativeY, SLOT_SIZE, SLOT_SIZE);
    }

    protected final LytRect progressRect() {
        return new LytRect(
                this.bounds.x() + this.progressX,
                this.bounds.y() + this.progressY,
                this.progressWidth,
                this.progressHeight);
    }

    protected final record LinesTooltip(ItemStack icon, List<Component> textLines) implements GuideTooltip {

        @Override
        public ItemStack getIcon() {
            return this.icon;
        }

        @Override
        public List<ClientTooltipComponent> getLines() {
            return this.textLines.stream()
                    .map(Component::getVisualOrderText)
                    .map(ClientTextTooltip::new)
                    .map(ClientTooltipComponent.class::cast)
                    .toList();
        }

        @Override
        public void exportResources(ResourceExporter exporter) {}
    }
}
