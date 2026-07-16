package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.ae2.DataKeyType;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRipperReassemblerScreenTest {

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
        Registry<AEKeyType> registry = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY)
                .sync(true)
                .maxId(127)
                .create();
        AEKeyTypesInternal.setRegistry(registry);
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        Registry.register(registry, DataKeyType.TYPE.getId(), DataKeyType.TYPE);
        Registry.register(registry, DataFlowKeyType.TYPE.getId(), DataFlowKeyType.TYPE);
        registry.freeze();
    }

    @Test
    void acceptsContainedFluidForAFluidConfigSlot() {
        var inventory = new GenericStackInv(
                Set.of(AEKeyType.fluids()),
                null,
                GenericStackInv.Mode.CONFIG_STACKS,
                1);
        var configInventory = new ConfigMenuInventory(inventory);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        var action = new EmptyingAction(Component.literal("Water"), water, 1_000L);

        var accepted = DataRipperReassemblerScreen.validateEmptyingAction(configInventory, 0, action);

        assertSame(action, accepted);
    }

    @Test
    void rejectsContainersForNonConfigAndEmptyCarriedSlots() {
        var vanillaSlot = new Slot(new SimpleContainer(1), 0, 0, 0);

        assertNull(DataRipperReassemblerScreen.getEmptyingAction(
                vanillaSlot,
                new ItemStack(Items.WATER_BUCKET)));
        assertNull(DataRipperReassemblerScreen.getEmptyingAction(vanillaSlot, ItemStack.EMPTY));
    }

    @Test
    void usesSwapPacketOnlyForSlotsWithoutVanillaStackLimit() {
        var vanillaSlot = new Slot(new SimpleContainer(2), 0, 0, 0) {

            @Override
            public int getMaxStackSize() {
                return 64;
            }
        };
        var limitedSlot = new Slot(new SimpleContainer(2), 1, 0, 0) {

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };

        assertFalse(DataRipperReassemblerScreen.requiresSwapSlotsPacket(vanillaSlot));
        assertTrue(DataRipperReassemblerScreen.requiresSwapSlotsPacket(limitedSlot));
    }

    @Test
    void treatsHoveredModularUiPanelsAsInsideTheContainer() {
        assertFalse(DataRipperReassemblerScreen.remainsOutside(true, new UIElement()));
        assertFalse(DataRipperReassemblerScreen.remainsOutside(false, null));
        assertTrue(DataRipperReassemblerScreen.remainsOutside(true, null));
    }
}
