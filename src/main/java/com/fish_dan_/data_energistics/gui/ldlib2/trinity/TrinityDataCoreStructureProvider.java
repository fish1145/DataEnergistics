package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Creates one fresh draggable structure/status window for every accepted hosted OPEN generation.
 */
final class TrinityDataCoreStructureProvider implements HostSubUiProvider {

    private final TrinityDataCoreStructureDescriptor descriptor;
    private final StructurePreviewUiFactory previewFactory;
    private final BooleanSupplier logicalClient;
    @Nullable
    private final LongConsumer hostedRefundAction;
    @Nullable
    private final LongPredicate hostedRefundPending;

    TrinityDataCoreStructureProvider(TrinityDataCoreStructureDescriptor descriptor,
                                     StructurePreviewUiFactory previewFactory,
                                     BooleanSupplier logicalClient,
                                     @Nullable LongConsumer hostedRefundAction,
                                     @Nullable LongPredicate hostedRefundPending) {
        if (descriptor == null || previewFactory == null || logicalClient == null) {
            throw new IllegalArgumentException("Trinity structure provider arguments cannot be null");
        }
        this.descriptor = descriptor;
        this.previewFactory = previewFactory;
        this.logicalClient = logicalClient;
        this.hostedRefundAction = hostedRefundAction;
        this.hostedRefundPending = hostedRefundPending;
    }

    @Override
    public HostUiKey key() {
        return this.descriptor.key();
    }

    @Override
    public HostSubUi create(HostSubUiContext context) {
        if (context == null || !this.descriptor.key().equals(context.key())) {
            throw new IllegalArgumentException("Trinity structure provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        String windowId = TrinityDataCoreStructureProviders.windowId(this.descriptor.structureKey());
        root.setId(windowId);
        TrinityHostedWindowChrome.configureRoot(root, key());
        TrinityHostedWindowChrome.Chrome chrome = TrinityHostedWindowChrome.create(windowId, this.descriptor.title(), context);
        root.addChildren(chrome.dragHandle(), chrome.closeButton());

        StructurePreviewUi preview = this.previewFactory.create(
                ModVerticalMultiBlocks.trinityDataCoreId(),
                this.descriptor.structureKey(),
                windowId + "_preview",
                this.logicalClient.getAsBoolean());
        TrinityHostedWindowChrome.layoutPreview(preview.panel());
        root.addChild(preview.panel());

        TrinityDataCoreStructureStatusPanel status = new TrinityDataCoreStructureStatusPanel(
                this.descriptor,
                context,
                this.hostedRefundAction,
                this.hostedRefundPending);
        TrinityHostedWindowChrome.layoutSidePanel(status);
        root.addChild(status);
        return new HostSubUi(root, chrome.dragHandle());
    }
}
