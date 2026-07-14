package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/**
 * Creates Trinity's three fixed-structure providers and independent automatic-build provider.
 */
public final class TrinityDataCoreStructureProviders {

    /**
     * Stable root id of the main structure hosted window.
     */
    public static final String MAIN_WINDOW_ID = windowId("main");
    /**
     * Stable root id of the CPU structure hosted window.
     */
    public static final String CPU_WINDOW_ID = windowId("cpu");
    /**
     * Stable root id of the crafting structure hosted window.
     */
    public static final String CRAFTING_WINDOW_ID = windowId("crafting");
    /**
     * Stable root id of the independent automatic-build window.
     */
    public static final String AUTO_BUILD_WINDOW_ID = windowId("auto_build");

    private static final StructurePreviewUiFactory PREVIEW_FACTORY = StructurePreviewUiFactory.createDefault();

    private TrinityDataCoreStructureProviders() {}

    /**
     * Returns a fresh provider for the main structure status and preview window.
     */
    public static HostSubUiProvider main(TrinityDataCoreMenu menu) {
        return provider(TrinityDataCoreStructureDescriptor.main(menu), menu, null, null);
    }

    /**
     * Returns a fresh provider for the CPU structure status and preview window.
     */
    public static HostSubUiProvider cpu(TrinityDataCoreMenu menu) {
        return provider(TrinityDataCoreStructureDescriptor.cpu(menu), menu, null, null);
    }

    /**
     * Returns a fresh provider for the crafting structure and its generation-aware refund action.
     *
     * @param menu                synchronized Trinity menu state
     * @param hostedRefundAction  action that sends the supplied current hosted generation
     * @param hostedRefundPending exact-generation predicate that blocks duplicate action submission
     */
    public static HostSubUiProvider crafting(TrinityDataCoreMenu menu,
                                             LongConsumer hostedRefundAction,
                                             LongPredicate hostedRefundPending) {
        if (hostedRefundAction == null || hostedRefundPending == null) {
            throw new IllegalArgumentException("Crafting hosted refund action dependencies cannot be null");
        }
        return provider(
                TrinityDataCoreStructureDescriptor.crafting(menu),
                menu,
                hostedRefundAction,
                hostedRefundPending);
    }

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
                () -> ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                        .require(ModVerticalMultiBlocks.trinityDataCoreId()),
                PREVIEW_FACTORY,
                () -> menu.getPlayer().level().isClientSide,
                hostedAutoBuildAction,
                hostedAutoBuildPending);
    }

    static List<HostSubUiProvider> createForTesting(TrinityDataCoreMenu menu,
                                                    StructurePreviewUiFactory previewFactory,
                                                    BooleanSupplier logicalClient,
                                                    LongConsumer hostedRefundAction,
                                                    LongPredicate hostedRefundPending) {
        if (menu == null || previewFactory == null || logicalClient == null || hostedRefundAction == null ||
                hostedRefundPending == null) {
            throw new IllegalArgumentException("Trinity provider test dependencies cannot be null");
        }
        return List.of(
                new TrinityDataCoreStructureProvider(
                        TrinityDataCoreStructureDescriptor.main(menu), previewFactory, logicalClient, null, null),
                new TrinityDataCoreStructureProvider(
                        TrinityDataCoreStructureDescriptor.cpu(menu), previewFactory, logicalClient, null, null),
                new TrinityDataCoreStructureProvider(
                        TrinityDataCoreStructureDescriptor.crafting(menu),
                        previewFactory,
                        logicalClient,
                        hostedRefundAction,
                        hostedRefundPending));
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

    static String windowId(String structureKey) {
        if (structureKey == null || structureKey.isBlank()) {
            throw new IllegalArgumentException("Trinity hosted structure key cannot be blank");
        }
        return "trinity_" + structureKey + "_hosted_window";
    }

    private static HostSubUiProvider provider(TrinityDataCoreStructureDescriptor descriptor,
                                              TrinityDataCoreMenu menu,
                                              LongConsumer hostedRefundAction,
                                              LongPredicate hostedRefundPending) {
        if (menu == null) {
            throw new IllegalArgumentException("Trinity structure provider requires a menu");
        }
        return new TrinityDataCoreStructureProvider(
                descriptor,
                PREVIEW_FACTORY,
                () -> menu.getPlayer().level().isClientSide,
                hostedRefundAction,
                hostedRefundPending);
    }
}
