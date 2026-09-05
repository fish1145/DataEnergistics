package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public class OutputSideActionButton extends IconButton {

    private String iconName = "PLACEMENT_TOOLBOX";
    /** Ordered localized lines shown while the button is hovered. */
    private List<Component> tooltipMessage;

    public OutputSideActionButton(Button.OnPress onPress) {
        this(onPress, "gui.data_energistics.set_output_sides.open");
    }

    public OutputSideActionButton(Button.OnPress onPress, String messageKey) {
        super(onPress);
        setMessageKey(messageKey);
    }

    /** Creates an action button with a title and a second explanatory tooltip line. */
    public OutputSideActionButton(Button.OnPress onPress, String messageKey, String hintKey) {
        this(onPress, messageKey);
        setHintKey(hintKey);
    }

    public void setMessageKey(String messageKey) {
        this.setMessage(Component.translatable(messageKey));
        this.tooltipMessage = List.of(this.getMessage());
    }

    /** Replaces the single-line tooltip with the current title followed by the supplied hint. */
    public void setHintKey(String hintKey) {
        this.tooltipMessage = List.of(this.getMessage(), Component.translatable(hintKey));
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }
        int yOffset = this.isHovered() ? 1 : 0;
        var bgIcon = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : (this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND);
        bgIcon.getBlitter().dest(this.getX() - 1, this.getY() + yOffset, 18, 20).zOffset(2).blit(guiGraphics);
        getBlitterIcon().dest(this.getX(), this.getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return this.tooltipMessage;
    }

    private Blitter getBlitterIcon() {
        return DataEnergisticsIcon.getBlitter(this.iconName);
    }
}
