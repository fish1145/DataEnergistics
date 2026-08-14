package com.fish_dan_.data_energistics.gui.ldlib2.trinity.autobuild;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/**
 * Creates Trinity's automatic-build hosted provider.
 */
public final class TrinityDataCoreStructureProviders {

    /**
     * Stable root id of the automatic-build hosted window.
     */
    public static final String AUTO_BUILD_WINDOW_ID = "trinity_auto_build_hosted_window";

    private static final StructurePreviewUiFactory PREVIEW_FACTORY = StructurePreviewUiFactory.createDefault();

    private TrinityDataCoreStructureProviders() {}

    /**
     * Returns a fresh provider for the multi-structure automatic-build editor and generation-aware submission.
     *
     * @param menu                   synchronized Trinity menu and logical-side source
     * @param hostedAutoBuildAction  action that sends one exact generation and immutable submission
     * @param hostedAutoBuildPending exact-generation predicate that blocks duplicate submissions
     */
    public static HostSubUiProvider autoBuild(
                                              TrinityDataCoreMenu menu,
                                              BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                              LongPredicate hostedAutoBuildPending) {
        if (menu == null || hostedAutoBuildAction == null || hostedAutoBuildPending == null) {
            throw new IllegalArgumentException("Automatic-build hosted provider dependencies cannot be null");
        }
        return autoBuildForTesting(
                menu::getAutoBuildPreviewSpec,
                PREVIEW_FACTORY,
                () -> menu.getPlayer().level().isClientSide,
                hostedAutoBuildAction,
                hostedAutoBuildPending);
    }

    static HostSubUiProvider autoBuildForTesting(
                                                 Supplier<MultiblockPreviewSpec> previewSpec,
                                                 StructurePreviewUiFactory previewFactory,
                                                 BooleanSupplier logicalClient,
                                                 BiConsumer<Long, TrinityAutoBuildSubmission> hostedAutoBuildAction,
                                                 LongPredicate hostedAutoBuildPending) {
        if (previewSpec == null || previewFactory == null || logicalClient == null ||
                hostedAutoBuildAction == null || hostedAutoBuildPending == null) {
            throw new IllegalArgumentException("Automatic-build provider test dependencies cannot be null");
        }
        return new TrinityDataCoreAutoBuildProvider(
                previewSpec,
                previewFactory,
                logicalClient,
                hostedAutoBuildAction,
                hostedAutoBuildPending);
    }
}
