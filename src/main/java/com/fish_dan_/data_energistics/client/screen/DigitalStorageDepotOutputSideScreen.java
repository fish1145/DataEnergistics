package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.menu.DigitalStorageDepotMenu;

import appeng.blockentity.AEBaseBlockEntity;

public class DigitalStorageDepotOutputSideScreen
                                                 extends TypedOutputSideScreen<DigitalStorageDepotMenu, DigitalStorageDepotScreen> {

    public DigitalStorageDepotOutputSideScreen(
                                               DigitalStorageDepotScreen parent,
                                               DigitalStorageDepotMenu menu,
                                               AEBaseBlockEntity host,
                                               DigitalStorageDepotOutputType initialContentType) {
        super(parent, host, initialContentType, menu::getOutputSides, menu::sendSetOutputSide);
    }
}
