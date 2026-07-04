package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;

public class MePatternBufferMenu extends CompartmentMenu {

    public MePatternBufferMenu(int id, Inventory playerInventory, MePatternBufferBlockEntity host) {
        super(ModMenus.ME_PATTERN_BUFFER.get(), id, playerInventory, host);
    }
}
