package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
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

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/** Creates a fresh automatic-build draft, preview, and draggable window for every accepted hosted OPEN generation. */
final class TrinityDataCoreAutoBuildProvider implements HostSubUiProvider {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 232;
    private static final int TITLE_HEIGHT = 18;

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
        root.setId(TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .width(WIDTH)
                .height(HEIGHT));
        root.style(style -> style.backgroundTexture(Sprites.BORDER));

        UIElement dragHandle = titleBar();
        root.addChildren(dragHandle, closeButton(context));

        MultiblockPreviewSpec spec = this.previewSpec.get();
        if (spec == null) {
            throw new IllegalStateException("Trinity automatic-build preview supplier returned null");
        }
        TrinityAutoBuildDraft draft = TrinityAutoBuildDraft.initial(spec);
        StructurePreviewUi preview = this.previewFactory.create(
                spec,
                draft.previewSelection(),
                draft.structureKeys(),
                TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_preview",
                this.logicalClient.getAsBoolean());
        preview.panel().layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(20));
        root.addChild(preview.panel());

        TrinityDataCoreAutoBuildPanel controls = new TrinityDataCoreAutoBuildPanel(
                preview,
                draft,
                context,
                this.hostedAutoBuildAction,
                this.hostedAutoBuildPending);
        controls.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(188)
                .top(20));
        root.addChild(controls);
        return new HostSubUi(root, dragHandle);
    }

    private UIElement titleBar() {
        UIElement titleBar = new UIElement();
        titleBar.setId(TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_drag_handle");
        titleBar.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(2)
                .width(WIDTH - TITLE_HEIGHT - 8)
                .height(TITLE_HEIGHT - 4));
        Label title = new Label();
        title.setText(Component.translatable("screen.data_energistics.trinity_data_core.auto_build.title"));
        title.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        title.layout(layout -> layout.widthPercent(100).heightPercent(100));
        titleBar.addChild(title);
        return titleBar;
    }

    private Button closeButton(HostSubUiContext context) {
        Button close = new Button();
        close.setId(TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID + "_close");
        close.noText();
        close.addPreIcon(Icons.CLOSE);
        close.setOnClick(event -> context.requestClose());
        close.style(style -> style.tooltips(
                Component.translatable("screen.data_energistics.multiblock_auto_build.close")));
        close.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(WIDTH - TITLE_HEIGHT)
                .top(2)
                .width(TITLE_HEIGHT - 4)
                .height(TITLE_HEIGHT - 4));
        return close;
    }
}
