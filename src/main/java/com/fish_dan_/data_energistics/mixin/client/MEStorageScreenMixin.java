package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MEStorageScreen.class)
public abstract class MEStorageScreenMixin {

    @Inject(method = "handleGridInventoryEntryMouseClick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$handleDigitalStorageDepotBucketModeClick(@Nullable GridInventoryEntry entry,
                                                                          int mouseButton,
                                                                          ClickType clickType,
                                                                          CallbackInfo ci) {
        MEStorageMenu menu = ((MEStorageScreen<?>) (Object) this).getMenu();
        ItemStack carried = menu.getCarried();
        if (!DigitalStorageDepotBlockItem.isDepotStack(carried) || !DigitalStorageDepotBlockItem.isBucketMode(carried)) {
            return;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        if (mouseButton == 0 && entry != null) {
            AEKey clickedKey = entry.getWhat();
            if (clickedKey == null || clickedKey instanceof AEItemKey) {
                ci.cancel();
                return;
            }

            long canInsert = DigitalStorageDepotBlockItem.insertIntoSelectedTerminalSlot(
                    carried,
                    level.registryAccess(),
                    clickedKey,
                    clickType == ClickType.QUICK_MOVE ? Long.MAX_VALUE : clickedKey.getAmountPerUnit(),
                    appeng.api.config.Actionable.SIMULATE);
            if (canInsert <= 0) {
                ci.cancel();
                return;
            }

            menu.handleInteraction(
                    entry.getSerial(),
                    clickType == ClickType.QUICK_MOVE ? InventoryAction.FILL_ENTIRE_ITEM : InventoryAction.FILL_ITEM);
            ci.cancel();
            return;
        }

        if (mouseButton == 1) {
            GenericStack content = DigitalStorageDepotBlockItem.getSelectedTerminalStack(carried, level.registryAccess());
            if (content == null || content.what() == null || content.amount() <= 0 || !menu.isKeyVisible(content.what())) {
                ci.cancel();
                return;
            }

            menu.handleInteraction(
                    -1,
                    clickType == ClickType.QUICK_MOVE ? InventoryAction.EMPTY_ENTIRE_ITEM : InventoryAction.EMPTY_ITEM);
            ci.cancel();
            return;
        }

        ci.cancel();
    }
}
