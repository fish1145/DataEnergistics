package com.fish_dan_.data_energistics.client.ui.machine;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.orientation.RelativeSide;
import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.EnumMap;
import java.util.List;

/**
 * Presents the existing six-direction configuration as a modal LDLib2 dialog on the machine UI root.
 */
final class DataReassemblerOutputSideDialog extends Dialog {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "textures/guis/set_output_sides.png");
    private final EnumMap<Direction, Boolean> selectedSides = new EnumMap<>(Direction.class);

    DataReassemblerOutputSideDialog(DataRipperReassemblerMachineUiState state, Runnable onClosed) {
        this.overlay.clearAllChildren();
        this.overlay.getLayout().width(93);
        this.overlay.getLayout().height(92);
        this.overlay.style(style -> style.backgroundTexture(
                SpriteTexture.of(BACKGROUND).setSprite(0, 0, 93, 92)));
        setOnClose(onClosed);

        this.overlay.addChild(position(new Label()
                .setText(Component.translatable("gui.data_energistics.set_output_sides"))
                .textStyle(style -> style.textColor(0x404040).textShadow(false)), 8, 6, 78, 9));

        this.overlay.addChild(position(new DataReassemblerIconButtonElement(
                Icon.BACK::getBlitter,
                () -> ItemStack.EMPTY,
                () -> List.of(state.machineName()),
                () -> false,
                false,
                this::close), 69, -5, 16, 16));

        this.overlay.addChild(position(new DataReassemblerIconButtonElement(
                Icon.S_CLEAR::getBlitter,
                () -> ItemStack.EMPTY,
                () -> List.of(Component.translatable("gui.data_energistics.set_output_sides.clear")),
                () -> false,
                true,
                () -> {
                    for (Direction side : Direction.values()) {
                        this.selectedSides.put(side, false);
                        state.setOutputSideEnabled(side, false);
                    }
                }), 74, 72, 8, 8));

        for (RelativeSide relativeSide : RelativeSide.values()) {
            var absoluteSide = state.resolveSide(relativeSide);
            this.selectedSides.put(absoluteSide, state.isOutputSideEnabled(absoluteSide));
            int[] position = position(relativeSide);
            this.overlay.addChild(position(new DataReassemblerIconButtonElement(
                    () -> null,
                    () -> state.outputSideIcon(absoluteSide),
                    () -> List.of(Component.translatable(this.selectedSides.get(absoluteSide) ? "gui.data_energistics.set_output_sides.on" : "gui.data_energistics.set_output_sides.off")),
                    () -> this.selectedSides.get(absoluteSide),
                    false,
                    () -> {
                        boolean enabled = !this.selectedSides.get(absoluteSide);
                        this.selectedSides.put(absoluteSide, enabled);
                        state.setOutputSideEnabled(absoluteSide, enabled);
                    }),
                    position[0],
                    position[1],
                    16,
                    16));
        }
    }

    private static <T extends UIElement> T position(T element, int x, int y, int width, int height) {
        element.getLayout().positionType(TaffyPosition.ABSOLUTE);
        element.getLayout().left(x);
        element.getLayout().top(y);
        element.getLayout().width(width);
        element.getLayout().height(height);
        return element;
    }

    static int[] position(RelativeSide side) {
        return switch (side) {
            case TOP -> new int[] { 34, 16 };
            case FRONT -> new int[] { 34, 38 };
            case LEFT -> new int[] { 14, 38 };
            case RIGHT -> new int[] { 54, 38 };
            case BACK -> new int[] { 54, 60 };
            case BOTTOM -> new int[] { 34, 60 };
        };
    }
}
