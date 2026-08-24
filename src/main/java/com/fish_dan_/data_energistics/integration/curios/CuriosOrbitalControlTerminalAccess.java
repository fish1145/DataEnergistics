package com.fish_dan_.data_energistics.integration.curios;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

/** Isolates all optional Curios types used by the orbital control terminal. */
public final class CuriosOrbitalControlTerminalAccess {

    public static final String SLOT_ID = "data_energistics_orbital_terminal";

    private static final int SLOT_INDEX = 0;
    private static final TagKey<Item> TERMINAL_SLOT_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CuriosApi.MODID, SLOT_ID));
    private static volatile boolean failed;

    private CuriosOrbitalControlTerminalAccess() {}

    /** Registers the validator referenced by the dedicated terminal slot data. */
    public static void register() {
        if (failed) {
            return;
        }
        try {
            CuriosApi.registerCurioPredicate(
                    ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "orbital_terminal_slot"),
                    slotResult -> slotResult.stack().is(TERMINAL_SLOT_TAG));
        } catch (RuntimeException | LinkageError exception) {
            disable("slot validator registration", exception);
        }
    }

    /** Returns the active terminal stack from the dedicated slot without copying it. */
    public static Optional<ItemStack> find(Player player) {
        if (failed) {
            return Optional.empty();
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.findCurio(SLOT_ID, SLOT_INDEX, false))
                    .filter(result -> result.stack().is(DEItems.ORBITAL_CONTROL_TERMINAL.get()))
                    .map(SlotResult::stack);
        } catch (RuntimeException | LinkageError exception) {
            disable("inventory lookup", exception);
            return Optional.empty();
        }
    }

    private static synchronized void disable(String operation, Throwable exception) {
        if (!failed) {
            failed = true;
            Data_Energistics.LOGGER.error(
                    "Disabling orbital terminal Curios access after {} failed",
                    operation,
                    exception);
        }
    }
}
