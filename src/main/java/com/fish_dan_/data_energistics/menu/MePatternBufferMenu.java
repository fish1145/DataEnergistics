package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.CompartmentHostUi;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.world.entity.player.Inventory;

public class MePatternBufferMenu extends CompartmentMenu {

    public MePatternBufferMenu(int id, Inventory playerInventory, MePatternBufferBlockEntity host) {
        super(DEMenus.ME_PATTERN_BUFFER.get(), id, playerInventory, host);
        CompartmentHostUi.mountPatternBuffer(this);
    }
}
