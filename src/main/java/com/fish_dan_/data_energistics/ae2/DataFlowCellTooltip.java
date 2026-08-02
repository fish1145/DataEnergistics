package com.fish_dan_.data_energistics.ae2;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.GenericStack;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.items.storage.StorageCellTooltipComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Renders capacity, filter state, upgrades, and stored contents for multi-resource Data Flow cells.
 */
public final class DataFlowCellTooltip {

    private DataFlowCellTooltip() {}

    public static void addCellInformation(ItemStack stack, List<Component> lines) {
        DataFlowCellInventory inventory = DataFlowCellHandler.INSTANCE.getCellInventory(stack, null);
        if (inventory == null) {
            return;
        }

        lines.add(Tooltips.bytesUsed(inventory.getUsedBytes(), inventory.getTotalBytes()));
        lines.add(Tooltips.typesUsed(inventory.getStoredItemTypes(), inventory.getTotalItemTypes()));
        if (inventory.isPreformatted()) {
            Component mode = (inventory.getPartitionListMode() == IncludeExclude.WHITELIST ? GuiText.Included : GuiText.Excluded)
                    .text();
            lines.add(GuiText.Partitioned.withSuffix(" - ").append(mode).append(" ").append(
                    inventory.isFuzzy() ? GuiText.Fuzzy.text() : GuiText.Precise.text()));
        }
    }

    public static Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        DataFlowCellInventory inventory = DataFlowCellHandler.INSTANCE.getCellInventory(stack, null);
        if (inventory == null) {
            return Optional.empty();
        }

        List<ItemStack> upgrades = new ArrayList<>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            inventory.getUpgradesInventory().forEach(upgrades::add);
        }

        List<GenericStack> content = new ArrayList<>();
        boolean hasMoreContent = false;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            int maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();
            var availableStacks = inventory.getAvailableStacks();
            for (var entry : availableStacks) {
                content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
            if (content.size() < maxCountShown && inventory.getPartitionListMode() == IncludeExclude.WHITELIST) {
                var config = inventory.getConfigInventory();
                for (int slot = 0; slot < config.size(); slot++) {
                    var key = config.getKey(slot);
                    if (key != null && availableStacks.get(key) <= 0L) {
                        content.add(new GenericStack(key, 0L));
                    }
                    if (content.size() > maxCountShown) {
                        break;
                    }
                }
            }
            content.sort(Comparator.comparingLong(GenericStack::amount).reversed());
            hasMoreContent = content.size() > maxCountShown;
            if (hasMoreContent) {
                content.subList(maxCountShown, content.size()).clear();
            }
        }

        return Optional.of(new StorageCellTooltipComponent(upgrades, content, hasMoreContent, true));
    }
}
