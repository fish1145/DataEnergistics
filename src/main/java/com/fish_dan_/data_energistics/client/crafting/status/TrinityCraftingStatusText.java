package com.fish_dan_.data_energistics.client.crafting.status;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCraftingStatusEntry;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.core.localization.GuiText;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Exact CPU table amounts in the key's display units, with lossless full tooltip text. */
public final class TrinityCraftingStatusText {

    private TrinityCraftingStatusText() {}

    /** Returns the original key tooltip plus exact amounts, or just compact table lines. */
    public static List<Component> lines(TrinityCraftingStatusEntry entry, boolean tooltip) {
        AEKey key = Objects.requireNonNull(entry.getWhat(), "CPU status table requires a resolved material key");
        List<Component> lines = tooltip ? new ObjectArrayList<>(AEKeyRendering.getTooltip(key)) : new ObjectArrayList<>(3);
        add(lines, GuiText.FromStorage, key, entry.stored(), tooltip);
        add(lines, GuiText.Crafting, key, entry.active(), tooltip);
        add(lines, GuiText.Scheduled, key, entry.pending(), tooltip);
        return lines;
    }

    private static void add(List<Component> lines, GuiText label, AEKey key, BigInteger amount, boolean full) {
        if (amount.signum() > 0) {
            lines.add(label.text(format(key, amount, full)));
        }
    }

    private static String format(AEKey key, BigInteger amount, boolean full) {
        BigDecimal raw = new BigDecimal(amount);
        BigDecimal perUnit = BigDecimal.valueOf(key.getAmountPerUnit());
        if (!full) {
            return TrinityAmountFormatter.format(raw.divide(perUnit, MathContext.DECIMAL128));
        }
        String number;
        try {
            BigDecimal units = raw.divide(perUnit, MathContext.UNLIMITED).stripTrailingZeros();
            DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
            format.setMaximumFractionDigits(Math.max(0, units.scale()));
            number = format.format(units);
        } catch (ArithmeticException exception) {
            // A third-party key may use a unit divisor with a repeating decimal; keep an exact fraction instead.
            BigInteger[] parts = amount.divideAndRemainder(BigInteger.valueOf(key.getAmountPerUnit()));
            number = parts[0] + " + " + parts[1] + "/" + key.getAmountPerUnit();
        }
        String unit = key.getUnitSymbol();
        return unit == null ? number : number + " " + unit;
    }
}
