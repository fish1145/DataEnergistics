package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.status.TrinityCraftingStatusAccess;
import com.fish_dan_.data_energistics.client.crafting.status.TrinityCraftingStatusState;
import com.fish_dan_.data_energistics.client.crafting.status.TrinityReusableStatusText;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityReusableStatus.Phase;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.TextAlignment;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Replaces AE2's remaining-time estimate with cumulative elapsed time for the selected Trinity CPU.
 */
@Mixin(CraftingCPUScreen.class)
public abstract class CraftingCPUScreenMixin extends AEBaseScreen<CraftingCPUMenu> implements TrinityCraftingStatusAccess {

    @Unique
    private final TrinityCraftingStatusState dataEnergistics$exactStatus = new TrinityCraftingStatusState();

    @Override
    public TrinityCraftingStatusState data_energistics$craftingStatusState() {
        return this.dataEnergistics$exactStatus;
    }

    @Inject(method = "postUpdate", at = @At("HEAD"))
    private void dataEnergistics$resetNativeStatus(CraftingStatus status, CallbackInfo ci) {
        this.dataEnergistics$exactStatus.onNativeUpdate();
    }

    @Unique
    private Component dataEnergistics$statusCaption = Component.empty();

    protected CraftingCPUScreenMixin(CraftingCPUMenu menu,
                                     Inventory playerInventory,
                                     Component title,
                                     ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$showTrinityElapsedTime(CallbackInfo ci) {
        var header = this.dataEnergistics$exactStatus.header();
        if (header == null) {
            return;
        }

        Component title = this.getGuiDisplayName(header.reusable().phase() == Phase.NONE ? GuiText.CraftingStatus.text() :
                TrinityReusableStatusText.phase(header.reusable()));
        long elapsedMilliseconds = TimeUnit.MILLISECONDS.convert(
                header.elapsedTime(),
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
        this.dataEnergistics$statusCaption = title;
        this.setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void dataEnergistics$showResidentDetails(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var header = this.dataEnergistics$exactStatus.header();
        var text = this.style.getText().get(TEXT_ID_DIALOG_TITLE);
        if (header == null || header.reusable().phase() == Phase.NONE || text == null) {
            return;
        }
        var position = text.getPosition().resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        var lines = text.getMaxWidth() > 0 ? this.font.split(this.dataEnergistics$statusCaption, text.getMaxWidth()) :
                List.of(this.dataEnergistics$statusCaption.getVisualOrderText());
        int y = position.getY();
        for (var line : lines) {
            int width = Math.round(this.font.width(line) * text.getScale());
            int x = position.getX();
            if (text.getAlign() == TextAlignment.CENTER) x -= width / 2;
            else if (text.getAlign() == TextAlignment.RIGHT) x -= width;
            int height = (int) (this.font.lineHeight * text.getScale());
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                graphics.renderComponentTooltip(this.font, TrinityReusableStatusText.tooltip(header.reusable()), mouseX, mouseY);
                return;
            }
            y += height;
        }
    }
}
