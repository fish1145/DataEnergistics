package com.fish_dan_.data_energistics.integration.viewer.emi.ingredient;

import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;

import net.minecraft.client.gui.screens.Screen;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import dev.emi.emi.api.EmiStackProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;

public final class PatternEncodingGenericStackEmiProvider implements EmiStackProvider<Screen> {

    @Override
    public EmiStackInteraction getStackAt(Screen screen, int x, int y) {
        if (!(screen instanceof GenericStackLookupScreen lookupScreen)) {
            return EmiStackInteraction.EMPTY;
        }

        StackWithBounds hovered = lookupScreen.dataEnergistics$getGenericStackUnderMouse(x, y);
        if (hovered == null) {
            return EmiStackInteraction.EMPTY;
        }

        GenericStack stack = hovered.stack();
        EmiStack emiStack = EmiGenericStackIngredientResolver.resolve(stack);
        return new EmiStackInteraction(emiStack, null, true);
    }
}
