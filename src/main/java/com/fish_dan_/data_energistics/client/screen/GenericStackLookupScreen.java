package com.fish_dan_.data_energistics.client.screen;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import org.jspecify.annotations.Nullable;

public interface GenericStackLookupScreen {

    @Nullable
    StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY);

    @Nullable
    default GenericStack dataEnergistics$getGenericStackValueUnderMouse(double mouseX, double mouseY) {
        StackWithBounds stack = dataEnergistics$getGenericStackUnderMouse(mouseX, mouseY);
        return stack != null ? stack.stack() : null;
    }
}
