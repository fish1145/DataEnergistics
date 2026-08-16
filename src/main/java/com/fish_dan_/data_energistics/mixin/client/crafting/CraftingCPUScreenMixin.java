package com.fish_dan_.data_energistics.mixin.client.crafting;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.TimeUnit;

/**
 * Replaces AE2's remaining-time estimate with cumulative elapsed time for the selected Trinity CPU.
 */
@Mixin(CraftingCPUScreen.class)
public abstract class CraftingCPUScreenMixin extends AEBaseScreen<CraftingCPUMenu> {

    @Unique
    private static final String DATA_ENERGISTICS_TRINITY_CPU_NAME_KEY = "block.data_energistics.trinity_data_core";

    protected CraftingCPUScreenMixin(CraftingCPUMenu menu,
                                     Inventory playerInventory,
                                     Component title,
                                     ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$showTrinityElapsedTime(CallbackInfo ci) {
        if (!(this.menu instanceof CraftingStatusMenu statusMenu)) {
            return;
        }
        CraftingStatusMenu.CraftingCpuListEntry cpu = dataEnergistics$selectedTrinityCpu(statusMenu);
        if (cpu == null || cpu.currentJob() == null) {
            return;
        }

        Component title = this.getGuiDisplayName(GuiText.CraftingStatus.text());
        long elapsedMilliseconds = TimeUnit.MILLISECONDS.convert(
                cpu.elapsedTimeNanos(),
                TimeUnit.NANOSECONDS);
        String elapsedText = DurationFormatUtils.formatDuration(
                elapsedMilliseconds,
                GuiText.ETAFormat.getLocal());
        title = title.copy().append(" - " + elapsedText);
        if (this.menu.isCantStoreItems()) {
            title = title.copy()
                    .append(" - ")
                    .append(GuiText.CantStoreItems.text().withStyle(ChatFormatting.RED));
        }
        this.setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Unique
    private static CraftingStatusMenu.@Nullable CraftingCpuListEntry dataEnergistics$selectedTrinityCpu(
                                                                                                        CraftingStatusMenu menu) {
        int selectedSerial = menu.getSelectedCpuSerial();
        for (CraftingStatusMenu.CraftingCpuListEntry cpu : menu.cpuList.cpus()) {
            if (cpu.serial() == selectedSerial) {
                return dataEnergistics$containsTrinityNameKey(cpu.name()) ? cpu : null;
            }
        }
        return null;
    }

    @Unique
    private static boolean dataEnergistics$containsTrinityNameKey(@Nullable Component component) {
        if (component == null) {
            return false;
        }
        if (component.getContents() instanceof TranslatableContents contents &&
                DATA_ENERGISTICS_TRINITY_CPU_NAME_KEY.equals(contents.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (dataEnergistics$containsTrinityNameKey(sibling)) {
                return true;
            }
        }
        return false;
    }
}
