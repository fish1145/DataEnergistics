package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuMixin {

    @Unique
    private static final int MAX_DEPOT_TERMINAL_TRANSFER_ITERATIONS = 256;

    @Shadow
    @Final
    protected MEStorage storage;

    @Shadow
    @Final
    protected IEnergySource energySource;

    @Inject(method = "handleNetworkInteraction", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$handleDigitalStorageDepotTerminalInteraction(ServerPlayer player,
                                                                              @Nullable AEKey clickedKey,
                                                                              InventoryAction action,
                                                                              CallbackInfo ci) {
        ItemStack carried = data_energistics$carriedStack();
        if (!DigitalStorageDepotBlockItem.isDepotStack(carried) || !DigitalStorageDepotBlockItem.isBucketMode(carried)) {
            return;
        }

        HolderLookup.Provider registries = player.level().registryAccess();
        boolean handled = switch (action) {
            case FILL_ITEM -> data_energistics$fillDepotSlotFromNetwork(carried, registries, clickedKey, false);
            case FILL_ITEM_MOVE_TO_PLAYER -> data_energistics$fillDepotSlotFromNetwork(carried, registries, clickedKey, false);
            case FILL_ENTIRE_ITEM -> data_energistics$fillDepotSlotFromNetwork(carried, registries, clickedKey, true);
            case FILL_ENTIRE_ITEM_MOVE_TO_PLAYER -> data_energistics$fillDepotSlotFromNetwork(carried, registries, clickedKey, true);
            case EMPTY_ITEM -> data_energistics$emptyDepotSlotToNetwork(carried, registries, false);
            case EMPTY_ENTIRE_ITEM -> data_energistics$emptyDepotSlotToNetwork(carried, registries, true);
            default -> false;
        };

        if (handled) {
            data_energistics$setCarriedStack(carried);
        }
        ci.cancel();
    }

    @Unique
    private boolean data_energistics$fillDepotSlotFromNetwork(ItemStack depotStack, HolderLookup.Provider registries,
                                                              @Nullable AEKey clickedKey, boolean fillAll) {
        if (clickedKey == null) {
            return false;
        }

        long amount = fillAll ? Long.MAX_VALUE : clickedKey.getAmountPerUnit();
        int maxIterations = fillAll ? MAX_DEPOT_TERMINAL_TRANSFER_ITERATIONS : 1;
        boolean changed = false;

        while (maxIterations-- > 0) {
            long canPull = StorageHelper.poweredExtraction(
                    this.energySource,
                    this.storage,
                    clickedKey,
                    amount,
                    data_energistics$actionSource(),
                    Actionable.SIMULATE);
            if (canPull <= 0) {
                break;
            }

            long canInsert = DigitalStorageDepotBlockItem.insertIntoSelectedTerminalSlot(
                    depotStack,
                    registries,
                    clickedKey,
                    canPull,
                    Actionable.SIMULATE);
            if (canInsert <= 0) {
                break;
            }

            long extracted = StorageHelper.poweredExtraction(
                    this.energySource,
                    this.storage,
                    clickedKey,
                    canInsert,
                    data_energistics$actionSource(),
                    Actionable.MODULATE);
            if (extracted <= 0) {
                break;
            }

            long inserted = DigitalStorageDepotBlockItem.insertIntoSelectedTerminalSlot(
                    depotStack,
                    registries,
                    clickedKey,
                    extracted,
                    Actionable.MODULATE);
            if (inserted <= 0) {
                StorageHelper.poweredInsert(
                        this.energySource,
                        this.storage,
                        clickedKey,
                        extracted,
                        data_energistics$actionSource(),
                        Actionable.MODULATE);
                break;
            }

            changed = true;
            if (!fillAll || inserted < extracted) {
                break;
            }
        }

        return changed;
    }

    @Unique
    private boolean data_energistics$emptyDepotSlotToNetwork(ItemStack depotStack, HolderLookup.Provider registries, boolean emptyAll) {
        GenericStack content = DigitalStorageDepotBlockItem.getSelectedTerminalStack(depotStack, registries);
        if (content == null || content.what() == null || content.amount() <= 0) {
            return false;
        }

        AEKey what = content.what();
        long amount = emptyAll ? Long.MAX_VALUE : what.getAmountPerUnit();
        int maxIterations = emptyAll ? MAX_DEPOT_TERMINAL_TRANSFER_ITERATIONS : 1;
        boolean changed = false;

        while (maxIterations-- > 0) {
            long canExtract = DigitalStorageDepotBlockItem.extractFromSelectedTerminalSlot(
                    depotStack,
                    registries,
                    what,
                    amount,
                    Actionable.SIMULATE);
            if (canExtract <= 0) {
                break;
            }

            long canInsert = StorageHelper.poweredInsert(
                    this.energySource,
                    this.storage,
                    what,
                    canExtract,
                    data_energistics$actionSource(),
                    Actionable.SIMULATE);
            if (canInsert <= 0) {
                break;
            }

            long extracted = DigitalStorageDepotBlockItem.extractFromSelectedTerminalSlot(
                    depotStack,
                    registries,
                    what,
                    canInsert,
                    Actionable.MODULATE);
            if (extracted <= 0) {
                break;
            }

            long inserted = StorageHelper.poweredInsert(
                    this.energySource,
                    this.storage,
                    what,
                    extracted,
                    data_energistics$actionSource(),
                    Actionable.MODULATE);
            if (inserted <= 0) {
                DigitalStorageDepotBlockItem.insertIntoSelectedTerminalSlot(
                        depotStack,
                        registries,
                        what,
                        extracted,
                        Actionable.MODULATE);
                break;
            }

            changed = true;
            if (!emptyAll || inserted < extracted) {
                break;
            }
        }

        return changed;
    }

    @Unique
    private ItemStack data_energistics$carriedStack() {
        return ((AbstractContainerMenu) (Object) this).getCarried();
    }

    @Unique
    private void data_energistics$setCarriedStack(ItemStack stack) {
        ((AbstractContainerMenu) (Object) this).setCarried(stack);
    }

    @Unique
    private IActionSource data_energistics$actionSource() {
        return ((AEBaseMenu) (Object) this).getActionSource();
    }
}
