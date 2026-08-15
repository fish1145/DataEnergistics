package com.fish_dan_.data_energistics.client.screen.storage;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.screen.base.TypedOutputSideScreen;
import com.fish_dan_.data_energistics.menu.storage.DigitalStorageDepotMenu;

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
