package com.fish_dan_.data_energistics.integration.curios;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

/**
 * Isolates the optional Curios API used to register and read the dedicated distribution connector slot.
 */
public final class CuriosDataDistributionConnectorAccess {

    /** Identifies the single functional Curios slot reserved for distribution connectors. */
    private static final String SLOT_ID = "data_energistics_connector";

    /** Selects the only functional entry because the dedicated slot has a fixed size of one. */
    private static final int SLOT_INDEX = 0;

    /** Restricts the custom validator to the connector-specific Curios item tag. */
    private static final TagKey<Item> CONNECTOR_SLOT_TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CuriosApi.MODID, SLOT_ID));
    private static volatile boolean failed;

    /** Prevents construction because this class exposes only guarded optional-integration entry points. */
    private CuriosDataDistributionConnectorAccess() {}

    /**
     * Registers the validator referenced by the dedicated slot data after Curios has initialized.
     */
    public static void register() {
        if (failed) {
            return;
        }
        try {
            CuriosApi.registerCurioPredicate(Data_Energistics.id("connector_slot"),
                    slotResult -> slotResult.stack().is(CONNECTOR_SLOT_TAG));
        } catch (RuntimeException | LinkageError exception) {
            disable("slot validator registration", exception);
        }
    }

    /**
     * Returns the original stack stored in the first functional connector slot for the supplied player.
     */
    public static Optional<ItemStack> find(Player player) {
        if (failed) {
            return Optional.empty();
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.findCurio(SLOT_ID, SLOT_INDEX))
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
                    "Disabling distribution connector Curios access after {} failed",
                    operation,
                    exception);
        }
    }
}
