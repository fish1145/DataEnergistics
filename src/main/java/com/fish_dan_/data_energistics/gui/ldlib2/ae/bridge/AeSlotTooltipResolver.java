package com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge;

import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Maps AE2 slot-tooltip precedence and content onto one LDLib2 hover-tooltip value.
 */
final class AeSlotTooltipResolver {

    private AeSlotTooltipResolver() {}

    /**
     * Resolves AE2's emptying and custom tooltip extensions before requesting the ordinary item tooltip.
     *
     * @param slot                    wrapped menu slot
     * @param carried                 stack currently carried by the menu
     * @param ordinaryTooltipSupplier lazy LDLib2 ordinary item-tooltip factory
     * @return resolved LDLib2 tooltip, or null when the slot has no tooltip
     */
    @Nullable
    static HoverTooltips resolve(Slot slot,
                                 ItemStack carried,
                                 Supplier<HoverTooltips> ordinaryTooltipSupplier) {
        List<Component> emptyingTooltip = emptyingTooltip(slot, carried);
        Selection selection = select(
                emptyingTooltip,
                () -> customTooltip(slot, carried),
                carried.isEmpty());
        return switch (selection.kind()) {
            case EMPTYING, CUSTOM -> textOnly(selection.texts());
            case ORDINARY -> ordinaryTooltipSupplier.get();
            case NONE -> null;
        };
    }

    /**
     * Selects the same precedence as AE2 without eagerly building lower-priority tooltip content.
     *
     * @param emptyingTooltip        accepted emptying-action tooltip, when available
     * @param customTooltipSupplier  AppEng slot custom-tooltip lookup
     * @param ordinaryTooltipAllowed whether the menu's carried stack permits a normal slot tooltip
     * @return the selected semantic source, including empty custom text used to suppress fallback
     */
    static Selection select(@Nullable List<Component> emptyingTooltip,
                            Supplier<List<Component>> customTooltipSupplier,
                            boolean ordinaryTooltipAllowed) {
        if (emptyingTooltip != null) {
            return Selection.text(Kind.EMPTYING, emptyingTooltip);
        }
        List<Component> customTooltip = customTooltipSupplier.get();
        if (customTooltip != null) {
            return Selection.text(Kind.CUSTOM, customTooltip);
        }
        return ordinaryTooltipAllowed ? Selection.ordinary() : Selection.none();
    }

    /**
     * Reproduces AEBaseScreen's eligibility check before exposing an empty-container action.
     */
    @Nullable
    private static List<Component> emptyingTooltip(Slot slot, ItemStack carried) {
        if (!(slot instanceof AppEngSlot appEngSlot) || carried.isEmpty()) {
            return null;
        }
        if (!(appEngSlot.getInventory() instanceof ConfigMenuInventory configInventory)) {
            return null;
        }
        EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
        if (emptyingAction == null) {
            return null;
        }
        ItemStack wrappedStack = GenericStack.wrapInItemStack(new GenericStack(emptyingAction.what(), 1));
        if (!configInventory.isItemValid(slot.getContainerSlot(), wrappedStack)) {
            return null;
        }
        return Tooltips.getEmptyingTooltip(ButtonToolTips.SetAction, carried, emptyingAction);
    }

    /**
     * Returns an AppEng slot's replacement tooltip, preserving empty-list suppression semantics.
     */
    @Nullable
    private static List<Component> customTooltip(Slot slot, ItemStack carried) {
        return slot instanceof AppEngSlot appEngSlot ? appEngSlot.getCustomTooltip(carried) : null;
    }

    /**
     * Converts AE2 text-only tooltips without attaching an unrelated item image or tooltip component.
     */
    private static HoverTooltips textOnly(List<Component> tooltip) {
        return new HoverTooltips(List.copyOf(tooltip), null, null, ItemStack.EMPTY);
    }

    /**
     * Identifies which AE2-compatible tooltip branch must supply the LDLib2 hover content.
     */
    enum Kind {
        EMPTYING,
        CUSTOM,
        ORDINARY,
        NONE
    }

    /**
     * Immutable selection result that keeps precedence independently testable from the client tooltip renderer.
     */
    record Selection(Kind kind, List<Component> texts) {

        private static Selection text(Kind kind, List<Component> texts) {
            return new Selection(kind, List.copyOf(texts));
        }

        private static Selection ordinary() {
            return new Selection(Kind.ORDINARY, List.of());
        }

        private static Selection none() {
            return new Selection(Kind.NONE, List.of());
        }
    }
}
