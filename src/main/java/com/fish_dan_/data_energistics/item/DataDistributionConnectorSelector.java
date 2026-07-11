package com.fish_dan_.data_energistics.item;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Selects the connector stack used by binding workflows while keeping equipment integrations outside core item logic.
 */
public interface DataDistributionConnectorSelector {

    /**
     * Creates the default selector so callers depend on this contract instead of its internal implementation.
     *
     * @return a selector that gives the offhand connector priority over the equipped connector
     */
    static DataDistributionConnectorSelector create() {
        return new DataDistributionConnectorSelectorImpl();
    }

    /**
     * Chooses the original mutable connector stack, consulting equipped items only when the offhand is not a connector.
     *
     * @param offhandStack          current offhand stack whose connector takes absolute priority
     * @param equippedStackSupplier deferred optional-equipment lookup that is invoked at most once
     * @return the original selected connector stack, or an empty optional when neither source contains one
     */
    Optional<ItemStack> select(ItemStack offhandStack, Supplier<Optional<ItemStack>> equippedStackSupplier);
}

/**
 * Implements deterministic hand-first connector selection without exposing optional equipment APIs to consumers.
 */
final class DataDistributionConnectorSelectorImpl implements DataDistributionConnectorSelector {

    /**
     * Preserves source stack identity so subsequent binding mutations update the player's actual connector.
     *
     * @param offhandStack          current offhand stack whose connector takes absolute priority
     * @param equippedStackSupplier deferred optional-equipment lookup that is invoked at most once
     * @return the original selected connector stack, or an empty optional when neither source contains one
     */
    @Override
    public Optional<ItemStack> select(ItemStack offhandStack,
                                      Supplier<Optional<ItemStack>> equippedStackSupplier) {
        if (offhandStack.getItem() instanceof DataDistributionConnectorItem) {
            return Optional.of(offhandStack);
        }
        return equippedStackSupplier.get()
                .filter(stack -> stack.getItem() instanceof DataDistributionConnectorItem);
    }
}
