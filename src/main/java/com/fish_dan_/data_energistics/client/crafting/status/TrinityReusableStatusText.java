package com.fish_dan_.data_energistics.client.crafting.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityReusableStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityReusableStatus.Phase;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Client localization of bounded server observations; no world or provider access occurs while rendering. */
public final class TrinityReusableStatusText {

    private TrinityReusableStatusText() {}

    public static Component phase(TrinityReusableStatus status) {
        return Component.translatable(status.phase().translationKey());
    }

    public static List<Component> tooltip(TrinityReusableStatus status) {
        List<Component> lines = new ObjectArrayList<>();
        lines.add(Component.translatable("gui.data_energistics.reusable_status.heading", phase(status)));
        if (status.sessions() > 0) {
            NumberFormat format = NumberFormat.getIntegerInstance(Locale.ROOT);
            lines.add(Component.translatable("gui.data_energistics.reusable_status.sessions", status.sessions()));
            lines.add(Component.translatable("gui.data_energistics.reusable_status.held_total", format.format(status.heldTools())));
            lines.add(Component.translatable("gui.data_energistics.reusable_status.spares", format.format(status.spareTools())));
        }
        lines.add(Component.translatable("gui.data_energistics.reusable_status.not_cpu_inventory").withStyle(ChatFormatting.GRAY));
        if (status.phase() == Phase.RECONCILIATION || status.phase() == Phase.UNREACHABLE) {
            lines.add(Component.translatable("gui.data_energistics.reusable_status.unverified").withStyle(ChatFormatting.YELLOW));
        }
        if (!status.diagnostic().isEmpty()) {
            lines.add(Component.translatable("gui.data_energistics.reusable_status.diagnostic", status.diagnostic()).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
