package com.fish_dan_.data_energistics.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;

import java.util.Locale;

public final class GenericStackDisplayHelper {

    private static final float SMALL_OVERLAY_SCALE = 0.5F;

    private GenericStackDisplayHelper() {}

    public static String formatCompactAmount(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey) {
            return formatCompactFluidAmount(stack.amount());
        }
        return formatCompactAmount(stack.amount());
    }

    public static String formatCompactAmount(long amount) {
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

    public static String formatCompactFluidAmount(long amount) {
        long abs = Math.abs(amount);
        if (abs < 1_000L) {
            return amount + "mB";
        }
        long buckets = amount / 1_000L;
        return buckets + "B";
    }

    public static Component createAmountTooltip(GenericStack stack) {
        return Component.translatable("tooltip.data_energistics.amount")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL))
                        .withStyle(ChatFormatting.GRAY));
    }

    public static void renderSmallOverlay(GuiGraphics guiGraphics, int x, int y, String text) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        float scaledWidth = textWidth * SMALL_OVERLAY_SCALE;
        float scaledHeight = font.lineHeight * SMALL_OVERLAY_SCALE;
        float drawX = x + 18 - 1 - scaledWidth;
        float drawY = y + 18 - 1 - scaledHeight;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        guiGraphics.pose().scale(SMALL_OVERLAY_SCALE, SMALL_OVERLAY_SCALE, 1.0F);
        guiGraphics.drawString(
                font,
                text,
                Math.round(drawX / SMALL_OVERLAY_SCALE),
                Math.round(drawY / SMALL_OVERLAY_SCALE),
                16777215,
                true);
        guiGraphics.pose().popPose();
    }

    private static String compact(long amount, double divisor, String suffix) {
        double value = amount / divisor;
        double abs = Math.abs(value);
        if (abs >= 100 || Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.format(Locale.ROOT, "%.0f%s", value, suffix);
        }
        return String.format(Locale.ROOT, "%.1f%s", value, suffix);
    }
}
