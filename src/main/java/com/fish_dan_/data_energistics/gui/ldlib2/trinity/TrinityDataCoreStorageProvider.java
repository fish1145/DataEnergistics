package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUi;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiRoot;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;

/**
 * Creates the independent exact-key storage window from its editor-authored NBT layout.
 */
final class TrinityDataCoreStorageProvider implements HostSubUiProvider {

    static final String WINDOW_ID = "trinity_storage_hosted_window";
    private static final String CONTENT_ID = WINDOW_ID + "_content";
    private static final String TYPE_CAPACITY_ID = WINDOW_ID + "_type_capacity";
    private static final String CLOSE_ID = WINDOW_ID + "_close";
    private static final String PRIORITY_ID = WINDOW_ID + "_priority";
    private static final String TITLE_ID = WINDOW_ID + "_title";

    private final IDataProvider<TrinityDataCoreStorageView> storageView;
    private final IntConsumer storagePageRequest;

    TrinityDataCoreStorageProvider(@NotNull IDataProvider<TrinityDataCoreStorageView> storageView,
                                   @NotNull IntConsumer storagePageRequest) {
        this.storageView = storageView;
        this.storagePageRequest = storagePageRequest;
    }

    @Override
    public HostUiKey key() {
        return TrinityDataCoreHostUiKeys.STORAGE;
    }

    @Override
    public HostSubUi create(@NotNull HostSubUiContext context) {
        if (!key().equals(context.key())) {
            throw new IllegalArgumentException("Trinity storage provider received the wrong host context");
        }
        HostSubUiRoot root = context.createRoot();
        TrinityUiNbtLayouts.init("storage", root);
        if (!WINDOW_ID.equals(root.getId())) {
            throw new IllegalStateException("Trinity storage NBT root has unexpected id " + root.getId());
        }

        UIElement content = TrinityUiXmlLayouts.require(root, CONTENT_ID, UIElement.class);
        Label typeCapacity = TrinityUiXmlLayouts.require(root, TYPE_CAPACITY_ID, Label.class);
        typeCapacity.addClass("trinity-storage-type-capacity");
        typeCapacity.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(132)
                .top(144)
                .width(47)
                .height(23));
        Button close = TrinityUiXmlLayouts.require(root, CLOSE_ID, Button.class);
        Button priority = TrinityUiXmlLayouts.require(root, PRIORITY_ID, Button.class);
        Label title = createTitle();
        root.addChild(title);

        TrinityStorageContentsList contents = new TrinityStorageContentsList(
                WINDOW_ID + "_entries",
                this.storagePageRequest);
        TrinityStorageCapacityDisplay capacity = new TrinityStorageCapacityDisplay(WINDOW_ID + "_capacity_bar");
        content.addChildren(contents, capacity);
        contents.bindDataSource(this.storageView);
        capacity.bindDataSource(this.storageView);
        bindTypeCapacity(typeCapacity);

        close.setOnClick(event -> context.requestClose());
        Component closeTooltip = Component.translatable("screen.data_energistics.multiblock_preview.window.close");
        close.text.style(style -> style.tooltips(closeTooltip));
        close.style(style -> style.tooltips(closeTooltip));

        // TODO(storage-priority): connect this editor-authored control when storage priority becomes server state.
        Component priorityTooltip = Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.priority_unavailable");
        priority.text.style(style -> style.tooltips(priorityTooltip));
        priority.style(style -> style.tooltips(priorityTooltip));
        return new HostSubUi(root, root);
    }

    private static Label createTitle() {
        Label title = new Label();
        title.setId(TITLE_ID);
        title.setText(Component.translatable("screen.data_energistics.trinity_data_core.storage.title"));
        title.setAllowHitTest(false);
        title.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(6)
                .top(3)
                .width(120)
                .height(8));
        return title;
    }

    private void bindTypeCapacity(Label label) {
        var subscription = this.storageView.registerListener(value -> label.setText(typeCapacity(value)));
        label.addEventListener(UIEvents.REMOVED, ignored -> subscription.unsubscribe());
        label.setText(typeCapacity(this.storageView.getValue()));
    }

    private static Component typeCapacity(TrinityDataCoreStorageView view) {
        return Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.type_capacity",
                view.status().typeCount(),
                view.status().unlimited() ?
                        Component.translatable("gui.data_energistics.trinity.unlimited") :
                        Component.literal(Integer.toString(view.status().typeCapacity())));
    }
}
