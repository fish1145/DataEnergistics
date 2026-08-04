package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.client.OrderPackageGhostIngredient;
import com.fish_dan_.data_energistics.client.screen.OrderPackageScreen;
import com.fish_dan_.data_energistics.menu.OrderPackageMenu;

import net.minecraft.client.renderer.Rect2i;

import appeng.menu.slot.FakeSlot;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;

/**
 * Accepts JEI ghost ingredients into the order package's generic target slot.
 */
final class OrderPackageJeiGhostIngredientHandler implements IGhostIngredientHandler<OrderPackageScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(OrderPackageScreen screen, ITypedIngredient<I> ingredient,
                                               boolean doStart) {
        var converted = OrderPackageGhostIngredient.toGenericStack(ingredient.getIngredient());
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
                var dropped = OrderPackageGhostIngredient.toGenericStack(droppedIngredient);
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

    @Override
    public void onComplete() {}
}
