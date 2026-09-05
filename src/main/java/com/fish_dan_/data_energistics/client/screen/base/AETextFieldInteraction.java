package com.fish_dan_.data_energistics.client.screen.base;

import appeng.client.gui.widgets.AETextField;

import org.lwjgl.glfw.GLFW;

/**
 * Provides the shared mouse behavior used by the mod's AE-style text fields.
 */
public final class AETextFieldInteraction {

    private AETextFieldInteraction() {}

    /**
     * Clears and focuses an active text field when it receives a right click.
     *
     * @return whether the click was handled by the text field
     */
    public static boolean clearOnRightClick(AETextField textField, double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT ||
                !textField.visible ||
                !textField.active ||
                !textField.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        textField.setValue("");
        return textField.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
}
