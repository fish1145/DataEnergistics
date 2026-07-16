package com.fish_dan_.data_energistics.client.ui.machine;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRipperReassemblerMachineUiStateImplTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        if (ModList.get() == null) {
            ModList.of(List.of(), List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void progressFractionUsesSynchronizedRange() {
        assertEquals(0.0D, DataRipperReassemblerMachineUiStateImpl.validateProgress(0, 200));
        assertEquals(0.5D, DataRipperReassemblerMachineUiStateImpl.validateProgress(100, 200));
        assertEquals(1.0D, DataRipperReassemblerMachineUiStateImpl.validateProgress(200, 200));
        assertEquals(0.0D, DataRipperReassemblerMachineUiStateImpl.validateProgress(0, 0));
    }

    @Test
    void invalidProgressFailsInsteadOfBeingClamped() {
        assertThrows(
                IllegalStateException.class,
                () -> DataRipperReassemblerMachineUiStateImpl.validateProgress(-1, 200));
        assertThrows(
                IllegalStateException.class,
                () -> DataRipperReassemblerMachineUiStateImpl.validateProgress(201, 200));
        assertThrows(
                IllegalStateException.class,
                () -> DataRipperReassemblerMachineUiStateImpl.validateProgress(0, -1));
        assertThrows(
                IllegalStateException.class,
                () -> DataRipperReassemblerMachineUiStateImpl.validateProgress(1, 0));
    }

    @Test
    void outputMaskUsesAbsoluteDirectionOrdinals() {
        int mask = (1 << Direction.UP.ordinal()) | (1 << Direction.WEST.ordinal());

        assertTrue(DataRipperReassemblerMachineUiStateImpl.isSideEnabled(mask, Direction.UP));
        assertTrue(DataRipperReassemblerMachineUiStateImpl.isSideEnabled(mask, Direction.WEST));
        assertFalse(DataRipperReassemblerMachineUiStateImpl.isSideEnabled(mask, Direction.DOWN));
        assertFalse(DataRipperReassemblerMachineUiStateImpl.isSideEnabled(mask, Direction.EAST));
        assertFalse(DataRipperReassemblerMachineUiStateImpl.isSideEnabled(mask, Direction.NORTH));
        assertFalse(DataRipperReassemblerMachineUiStateImpl.isSideEnabled(mask, Direction.SOUTH));
    }

    @Test
    void relativeSidesFollowEveryHostOrientation() {
        for (BlockOrientation orientation : BlockOrientation.values()) {
            for (RelativeSide side : RelativeSide.values()) {
                assertEquals(
                        orientation.getSide(side),
                        DataRipperReassemblerMachineUiStateImpl.resolveSide(orientation, side));
            }
        }
    }

    @Test
    void nonEmptyNonWrapperKeySlotFailsFast() {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.STONE));
        Slot slot = new Slot(container, 0, 0, 0);

        assertThrows(IllegalStateException.class, () -> DataRipperReassemblerMachineUiStateImpl.decodeKey(slot));
    }
}
