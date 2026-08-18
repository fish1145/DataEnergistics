package com.fish_dan_.data_energistics.integration.jei.ui;

import com.fish_dan_.data_energistics.client.gui.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.integration.jei.ingredient.DataResourceJeiIngredient;
import com.fish_dan_.data_energistics.client.screen.machine.OrderPackageScreen;
import com.fish_dan_.data_energistics.menu.storage.OrderPackageMenu;

import net.minecraft.client.renderer.Rect2i;

import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;

/**
 * Accepts JEI ghost ingredients into the order package's generic target slot.
 */
public final class OrderPackageJeiGhostIngredientHandler implements IGhostIngredientHandler<OrderPackageScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(OrderPackageScreen screen, ITypedIngredient<I> ingredient,
                                               boolean doStart) {
        var converted = toGenericStack(ingredient);
        if (converted == null) {
            return List.of();
        }

        var slots = screen.getMenu().getSlots(OrderPackageMenu.TARGET);
        if (slots.size() != 1 || !(slots.getFirst() instanceof FakeSlot targetSlot)) {
            return List.of();
        }

        var filter = OrderPackageGhostIngredient.wrapFilter(converted.what());
        if (!targetSlot.canSetFilterTo(filter)) {
            return List.of();
        }

        Rect2i area = new Rect2i(
                screen.getGuiLeft() + targetSlot.x,
                screen.getGuiTop() + targetSlot.y,
                16,
                16);
        return List.of(new Target<>() {

            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I droppedIngredient) {
                var dropped = toGenericStack(droppedIngredient);
                if (dropped == null) {
                    return;
                }
                var droppedFilter = OrderPackageGhostIngredient.wrapFilter(dropped.what());
                if (targetSlot.canSetFilterTo(droppedFilter)) {
                    targetSlot.setFilterTo(droppedFilter);
                }
            }
        });
    }

    private static <I> GenericStack toGenericStack(ITypedIngredient<I> ingredient) {
        return toGenericStack(ingredient.getIngredient());
    }

    private static GenericStack toGenericStack(Object ingredient) {
        if (ingredient instanceof DataResourceJeiIngredient dataResourceIngredient) {
            return dataResourceIngredient.asGenericStack();
        }
        return OrderPackageGhostIngredient.toGenericStack(ingredient);
    }

    @Override
    public void onComplete() {}
}
