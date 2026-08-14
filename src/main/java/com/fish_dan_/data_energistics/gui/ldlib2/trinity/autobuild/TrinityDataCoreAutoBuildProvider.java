package com.fish_dan_.data_energistics.gui.ldlib2.trinity.autobuild;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildDraft;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild.AutoBuildComposition;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/**
 * Creates a fresh automatic-build draft, preview, and draggable window for every accepted hosted OPEN generation.
 */
final class TrinityDataCoreAutoBuildProvider implements HostSubUiProvider {

    private final Supplier<MultiblockPreviewSpec> previewSpec;
    private final StructurePreviewUiFactory previewFactory;
    private final BooleanSupplier logicalClient;
    private final BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction;
    private final LongPredicate hostedAutoBuildPending;

    TrinityDataCoreAutoBuildProvider(@NotNull Supplier<MultiblockPreviewSpec> previewSpec,
                                     @NotNull StructurePreviewUiFactory previewFactory,
                                     @NotNull BooleanSupplier logicalClient,
                                     @NotNull BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                     @NotNull LongPredicate hostedAutoBuildPending) {
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
    public HostSubUi create(@NotNull HostSubUiContext context) {
        if (!key().equals(context.key())) {
            throw new IllegalArgumentException("Trinity automatic-build provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        String windowId = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID;
        TrinityUiNbtLayouts.init("auto_build", root);
        TrinityDataCoreAutoBuildPanel.Layout controls = TrinityDataCoreAutoBuildPanel.requireLayout(root);
        TrinityHostedWindowChrome.bindExisting(root, context);

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
        AutoBuildComposition composition = AutoBuildComposition.builder(
                preview,
                controls.elements())
                .geometry(controls.geometry())
                .materials(windowId + "_material_grid", TrinityAmountFormatter::format)
                .build();

        new TrinityDataCoreAutoBuildPanel(
                controls,
                preview,
                draft,
                context,
                this.hostedAutoBuildAction,
                this.hostedAutoBuildPending,
                composition);
        return new HostSubUi(root, root);
    }
}
