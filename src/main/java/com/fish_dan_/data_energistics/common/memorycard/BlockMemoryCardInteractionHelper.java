package com.fish_dan_.data_energistics.common.memorycard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.items.tools.MemoryCardItem;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import org.jspecify.annotations.Nullable;

public final class BlockMemoryCardInteractionHelper {

    private BlockMemoryCardInteractionHelper() {}

    public static ItemInteractionResult useOnBlockEntity(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof IMemoryCard memoryCard)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AEBaseBlockEntity aeBlockEntity)) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return ItemInteractionResult.CONSUME;
        }

        if (InteractionUtil.isInAlternateUseMode(player)) {
            DataComponentMap settings = aeBlockEntity.exportSettings(SettingsFrom.MEMORY_CARD, player);
            if (!settings.isEmpty()) {
                MemoryCardItem.clearCard(stack);
                stack.applyComponents(settings);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            } else {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            }
        } else {
            Component storedName = stack.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
            Component currentName = getCurrentSettingsSource(aeBlockEntity, player);
            if (currentName != null && currentName.equals(storedName)) {
                aeBlockEntity.importSettings(SettingsFrom.MEMORY_CARD, stack.getComponents(), player);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            } else {
                MemoryCardItem.importGenericSettingsAndNotify(aeBlockEntity, stack.getComponents(), player);
            }
        }

        return ItemInteractionResult.CONSUME;
    }

    private static @Nullable Component getCurrentSettingsSource(AEBaseBlockEntity blockEntity, Player player) {
        return blockEntity.exportSettings(SettingsFrom.MEMORY_CARD, player).get(AEComponents.EXPORTED_SETTINGS_SOURCE);
    }
}
