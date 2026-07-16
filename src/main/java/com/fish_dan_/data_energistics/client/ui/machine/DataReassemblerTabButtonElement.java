package com.fish_dan_.data_energistics.client.ui.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/** Reproduces AE2's boxed back TabButton inside the LDLib2 output-side dialog. */
final class DataReassemblerTabButtonElement extends Button {

    private static final int ICON_X_OFFSET = 2;
    private static final int ICON_Y_OFFSET = 1;
    private final Supplier<Component> tooltipSupplier;
    private final Runnable onPress;

    DataReassemblerTabButtonElement(Supplier<Component> tooltipSupplier, Runnable onPress) {
        this.tooltipSupplier = tooltipSupplier;
        this.onPress = onPress;
        noText();
        setFocusable(true);
        setOnClick(event -> this.onPress.run());
        addEventListener(UIEvents.KEY_DOWN, this::onKeyDown);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(this.tooltipSupplier.get()),
                null,
                null,
                ItemStack.EMPTY));
        getLayout().width(22);
        getLayout().height(22);
        getLayout().paddingAll(0);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        backgroundIcon().getBlitter().dest(x, y).zOffset(2).blit(guiContext.graphics);
        Icon.BACK.getBlitter()
                .dest(x + ICON_X_OFFSET, y + ICON_Y_OFFSET)
                .zOffset(3)
                .blit(guiContext.graphics);
    }

    Icon backgroundIcon() {
        return isFocused() ? Icon.TAB_BUTTON_BACKGROUND_FOCUS : Icon.TAB_BUTTON_BACKGROUND;
    }

    int iconXOffset() {
        return ICON_X_OFFSET;
    }

    int iconYOffset() {
        return ICON_Y_OFFSET;
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        }
        super.onMouseDown(event);
    }

    private void onKeyDown(UIEvent event) {
        if (!isActive() || !isActivationKey(event.keyCode)) {
            return;
        }
        UISoundUtils.playButtonClickSound();
        this.onPress.run();
        event.stopPropagation();
    }

    private static boolean isActivationKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }
}
