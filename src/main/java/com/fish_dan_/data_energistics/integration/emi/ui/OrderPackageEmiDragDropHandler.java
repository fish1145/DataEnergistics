package com.fish_dan_.data_energistics.integration.emi.ui;

import com.fish_dan_.data_energistics.client.gui.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.client.screen.machine.OrderPackageScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.integration.modules.itemlists.DropTargets;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Routes EMI generic stacks through the same AE2 fake-slot protocol as carried items and JEI ingredients.
 */
public final class OrderPackageEmiDragDropHandler implements EmiDragDropHandler<OrderPackageScreen> {

    @Override
    public boolean dropStack(OrderPackageScreen screen, EmiIngredient ingredient, int x, int y) {
        for (var target : DropTargets.getTargets(screen)) {
            if (!target.area().contains(x, y)) {
                continue;
            }
            for (var emiStack : ingredient.getEmiStacks()) {
                var genericStack = OrderPackageGhostIngredient.toGenericStack(
                        EmiStackHelper.toGenericStack(emiStack));
                if (genericStack != null && target.drop(genericStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void render(OrderPackageScreen screen, EmiIngredient dragged, GuiGraphics guiGraphics,
                       int mouseX, int mouseY, float delta) {
        Set<GenericStack> candidates = dragged.getEmiStacks().stream()
                .map(EmiStackHelper::toGenericStack)
                .map(OrderPackageGhostIngredient::toGenericStack)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (var target : DropTargets.getTargets(screen)) {
            if (candidates.stream().noneMatch(target::canDrop)) {
                continue;
            }
            fill(guiGraphics, target.area(), 0x8822BB33);
        }
    }

    private static void fill(GuiGraphics guiGraphics, Rect2i area, int color) {
        guiGraphics.fill(
                area.getX(),
                area.getY(),
                area.getX() + area.getWidth(),
                area.getY() + area.getHeight(),
                color);
    }
}
