package com.fish_dan_.data_energistics.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class ConfigurableTntBlockItem extends BlockItem {

    private final Supplier<String> configuredNameSupplier;

    public ConfigurableTntBlockItem(Block block, Properties properties, Supplier<String> configuredNameSupplier) {
        super(block, properties);
        this.configuredNameSupplier = configuredNameSupplier;
    }

    @Override
    public Component getName(ItemStack stack) {
        String configuredName = this.configuredNameSupplier.get();
        if (configuredName != null && !configuredName.isBlank()) {
            return Component.literal(configuredName);
        }
        return super.getName(stack);
    }
}
