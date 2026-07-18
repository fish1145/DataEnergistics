package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.widgets.AETextField;

/**
 * Handles the upload preview text-field interaction shared by native and wireless terminals.
 */
final class PatternEncodingTextFieldHelper {

    private PatternEncodingTextFieldHelper() {}

    static boolean clearOnRightClick(AETextField textField, double mouseX, double mouseY, int button) {
        if (button != 1 || !textField.visible || !textField.active || !textField.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        textField.setValue("");
        return textField.mouseClicked(mouseX, mouseY, 0);
    }
}
