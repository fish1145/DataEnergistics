package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.IntConsumer;

/**
 * Hosts the Data Core's aggregate installed-pattern catalog window.
 */
public final class TrinityAggregatePatternProvider implements HostSubUiProvider {

    private final IDataProvider<TrinityPatternCatalogView> catalogView;
    private final IntConsumer pageRequest;
    private final Level level;
    private final TrinityPatternSlotActionSender slotActionSender;
    private final Runnable openPriority;
    private final Runnable refundPatterns;
    private final Runnable refundRetained;

    public TrinityAggregatePatternProvider(IDataProvider<TrinityPatternCatalogView> catalogView,
                                           IntConsumer pageRequest,
                                           Level level,
                                           TrinityPatternSlotActionSender slotActionSender,
                                           Runnable openPriority,
                                           Runnable refundPatterns,
                                           Runnable refundRetained) {
        this.catalogView = catalogView;
        this.pageRequest = pageRequest;
        this.level = level;
        this.slotActionSender = slotActionSender;
        this.openPriority = openPriority;
        this.refundPatterns = refundPatterns;
        this.refundRetained = refundRetained;
    }

    @Override
    public HostUiKey key() {
        return TrinityDataCoreHostUiKeys.PATTERN;
    }

    @Override
    public HostSubUi create(HostSubUiContext context) {
        if (!key().equals(context.key())) {
            throw new IllegalArgumentException("Trinity aggregate pattern provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        TrinityUiNbtLayouts.initProjectStyled("pattern", root);
        TrinityAggregatePatternLayout.Controls controls = TrinityAggregatePatternLayout.bind(root);
        root.addChild(createTitle());

        TrinityAggregatePatternSlots patterns = new TrinityAggregatePatternSlots(
                TrinityAggregatePatternLayout.WINDOW_ID + "_slots",
                context.generation(),
                this.level,
                this.pageRequest,
                this.slotActionSender);
        patterns.bindDataSource(this.catalogView);
        controls.content().addChildAt(patterns, 0);
        patterns.bindControls(controls.scrollbar(), controls.search(), controls.searchMode());

        bindButton(controls.close(), Component.translatable("gui.close"), context::requestClose);
        bindButton(
                controls.priority(),
                Component.translatable("button.data_energistics.trinity_data_core.pattern.priority"),
                this.openPriority);
        bindButton(
                controls.refundPatterns(),
                Component.translatable("button.data_energistics.trinity_data_core.refund_patterns"),
                this.refundPatterns);
        bindButton(
                controls.refundRetained(),
                Component.translatable("button.data_energistics.trinity_data_core.refund_retained_items"),
                this.refundRetained);

        controls.migrate().setActive(false);
        controls.migrate().setAllowHitTest(false);
        Component unavailable = Component.translatable(
                "button.data_energistics.trinity_data_core.pattern_migrate.unavailable");
        controls.migrate().text.style(style -> style.tooltips(unavailable));
        controls.migrate().style(style -> style.tooltips(unavailable));
        return new HostSubUi(root, root);
    }

    private static Label createTitle() {
        Label title = new Label();
        title.setId(TrinityAggregatePatternLayout.TITLE_ID);
        title.setText(Component.translatable("screen.data_energistics.trinity_data_core.pattern.title"));
        title.setAllowHitTest(false);
        title.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(6)
                .top(3)
                .width(64)
                .height(8));
        return title;
    }

    private static void bindButton(Button button, Component tooltip, Runnable action) {
        button.setOnClick(event -> action.run());
        button.text.style(style -> style.tooltips(tooltip));
        button.style(style -> style.tooltips(tooltip));
    }
}
