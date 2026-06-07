package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import java.util.List;

public class OutputSideActionButton extends IconButton {

    private String messageKey;
    private String iconName = "PLACEMENT_TOOLBOX";

    public OutputSideActionButton(Button.OnPress onPress) {
        this(onPress, "gui.data_energistics.set_output_sides.open");
    }

    public OutputSideActionButton(Button.OnPress onPress, String messageKey) {
        super(onPress);
        setMessageKey(messageKey);
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
        this.setMessage(Component.translatable(messageKey));
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    @Override
    protected appeng.client.gui.Icon getIcon() {
        return null;
    }

    @Override
    public void renderWidget(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }
        int yOffset = this.isHovered() ? 1 : 0;
        var bgIcon = this.isHovered() ? appeng.client.gui.Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : (this.isFocused() ? appeng.client.gui.Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : appeng.client.gui.Icon.TOOLBAR_BUTTON_BACKGROUND);
        bgIcon.getBlitter().dest(this.getX() - 1, this.getY() + yOffset, 18, 20).zOffset(2).blit(guiGraphics);
        getBlitterIcon().dest(this.getX(), this.getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable(this.messageKey));
    }

    private Blitter getBlitterIcon() {
        return DataEnergisticsIcon.getBlitter(this.iconName);
    }
}
