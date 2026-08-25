package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.screen.base.TypedOutputSideScreen;
import com.fish_dan_.data_energistics.menu.machine.DataRipperReassemblerMenu;

/** Data reassembler output configuration backed by the same AE2 sub-screen as the digital storage depot. */
public final class DataRipperReassemblerOutputSideScreen<M extends DataRipperReassemblerMenu>
                                                        extends TypedOutputSideScreen<M, DataRipperReassemblerScreen<M>> {

    public DataRipperReassemblerOutputSideScreen(
                                                 DataRipperReassemblerScreen<M> parent,
                                                 M menu,
                                                 DataRipperReassemblerBlockEntity host,
                                                 DigitalStorageDepotOutputType initialContentType) {
        super(parent, host, initialContentType, menu::getOutputSides, menu::sendSetOutputSide);
    }
}
