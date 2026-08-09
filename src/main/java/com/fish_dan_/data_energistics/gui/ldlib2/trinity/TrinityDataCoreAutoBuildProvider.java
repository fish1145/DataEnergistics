package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;

import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/** Creates a fresh automatic-build draft, preview, and draggable window for every accepted hosted OPEN generation. */
final class TrinityDataCoreAutoBuildProvider implements HostSubUiProvider {

    private final Supplier<MultiblockPreviewSpec> previewSpec;
    private final StructurePreviewUiFactory previewFactory;
    private final BooleanSupplier logicalClient;
    private final BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction;
    private final LongPredicate hostedAutoBuildPending;

    TrinityDataCoreAutoBuildProvider(Supplier<MultiblockPreviewSpec> previewSpec,
                                     StructurePreviewUiFactory previewFactory,
                                     BooleanSupplier logicalClient,
                                     BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                     LongPredicate hostedAutoBuildPending) {
        if (previewSpec == null || previewFactory == null || logicalClient == null ||
                hostedAutoBuildAction == null || hostedAutoBuildPending == null) {
            throw new IllegalArgumentException("Trinity automatic-build provider arguments cannot be null");
        }
        this.previewSpec = previewSpec;
        this.previewFactory = previewFactory;
        this.logicalClient = logicalClient;
        this.hostedAutoBuildAction = hostedAutoBuildAction;
        this.hostedAutoBuildPending = hostedAutoBuildPending;
    }

    @Override
    public HostUiKey key() {
        return TrinityDataCoreHostUiKeys.AUTO_BUILD;
    }

    @Override
    public HostSubUi create(HostSubUiContext context) {
        if (context == null || !key().equals(context.key())) {
            throw new IllegalArgumentException("Trinity automatic-build provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        String windowId = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID;
        root.setId(windowId);
        TrinityHostedWindowChrome.configureRoot(root, key());
        TrinityHostedWindowChrome.Chrome chrome = TrinityHostedWindowChrome.create(
                windowId,
                Component.translatable("screen.data_energistics.trinity_data_core.auto_build.title"),
                context);
        root.addChildren(chrome.dragHandle(), chrome.closeButton());

        MultiblockPreviewSpec spec = this.previewSpec.get();
        if (spec == null) {
            throw new IllegalStateException("Trinity automatic-build preview supplier returned null");
        }
        TrinityAutoBuildDraft draft = TrinityAutoBuildDraft.initial(spec);
        StructurePreviewUi preview = this.previewFactory.create(
                spec,
                draft.previewSelection(),
                draft.structureKeys(),
                windowId + "_preview",
                this.logicalClient.getAsBoolean());
        TrinityHostedWindowChrome.layoutPreview(preview.panel());
        root.addChild(preview.panel());

        TrinityDataCoreAutoBuildPanel controls = new TrinityDataCoreAutoBuildPanel(
                preview,
                draft,
                context,
                this.hostedAutoBuildAction,
                this.hostedAutoBuildPending);
        TrinityHostedWindowChrome.layoutSidePanel(controls);
        root.addChild(controls);
        return new HostSubUi(root, chrome.dragHandle());
    }
}
