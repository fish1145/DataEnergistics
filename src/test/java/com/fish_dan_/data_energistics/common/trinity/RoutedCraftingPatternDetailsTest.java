package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class RoutedCraftingPatternDetailsTest {

    @Test
    void delegatesPatternContractAndKeepsRouteInIdentity() {
        StubPatternDetails delegate = new StubPatternDetails();
        UUID hostId = UUID.randomUUID();
        RoutedCraftingPatternDetails first = new RoutedCraftingPatternDetails(
                new PatternRoute(hostId, UUID.randomUUID(), 3),
                delegate);
        RoutedCraftingPatternDetails same = new RoutedCraftingPatternDetails(first.route(), delegate);
        RoutedCraftingPatternDetails sameDefinition = new RoutedCraftingPatternDetails(
                first.route(),
                new StubPatternDetails());
        RoutedCraftingPatternDetails otherSlot = new RoutedCraftingPatternDetails(
                new PatternRoute(hostId, first.route().coreId(), 4),
                delegate);

        assertSame(delegate.getDefinition(), first.getDefinition());
        assertSame(delegate.getInputs(), first.getInputs());
        assertSame(delegate.getPrimaryOutput(), first.getPrimaryOutput());
        assertSame(delegate.getOutputs(), first.getOutputs());
        assertFalse(first.supportsPushInputsToExternalInventory());
        assertSame(delegate.tooltip, first.getTooltip(null, null));
        KeyCounter[] inputs = { new KeyCounter() };
        first.pushInputsToExternalInventory(inputs, (key, amount) -> {});
        assertSame(inputs, delegate.pushedInputs);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertEquals(first, sameDefinition);
        assertNotEquals(first, otherSlot);
        assertNotEquals(first, delegate);
    }

    private static final class StubPatternDetails implements IPatternDetails {

        private final PatternDetailsTooltip tooltip = new PatternDetailsTooltip(Component.literal("test"));
        private final IInput[] inputs = {};
        private final List<GenericStack> outputs = List.of();
        private KeyCounter[] pushedInputs;

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return this.inputs;
        }

        @Override
        public GenericStack getPrimaryOutput() {
            return null;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return false;
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            this.pushedInputs = inputHolder;
        }

        @Override
        public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
            return this.tooltip;
        }
    }
}
