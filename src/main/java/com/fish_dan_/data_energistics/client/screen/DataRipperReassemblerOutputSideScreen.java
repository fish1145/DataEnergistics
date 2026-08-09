package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.screen.base.TypedOutputSideScreen;
import com.fish_dan_.data_energistics.menu.DataRipperReassemblerMenu;

/** Data reassembler output configuration backed by the same AE2 sub-screen as the digital storage depot. */
public final class DataRipperReassemblerOutputSideScreen
                                                         extends TypedOutputSideScreen<DataRipperReassemblerMenu, DataRipperReassemblerScreen> {

    public DataRipperReassemblerOutputSideScreen(
                                                 DataRipperReassemblerScreen parent,
                                                 DataRipperReassemblerMenu menu,
                                                 DataRipperReassemblerBlockEntity host,
                                                 DigitalStorageDepotOutputType initialContentType) {
        super(parent, host, initialContentType, menu::getOutputSides, menu::sendSetOutputSide);
    }
}
