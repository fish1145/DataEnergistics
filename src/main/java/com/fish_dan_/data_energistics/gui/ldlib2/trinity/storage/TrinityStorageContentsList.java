package com.fish_dan_.data_energistics.gui.ldlib2.trinity.storage;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView.Entry;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Fixed-pool exact-key viewport used only by the Trinity storage hosted window.
 */
final class TrinityStorageContentsList extends BindableUIElement<TrinityDataCoreStorageView> {

    private static final int VISIBLE_ROW_COUNT = TrinityDataCoreStorageView.VISIBLE_ROW_COUNT;
    private static final int ROW_WIDTH = 132;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ENTRY_COUNT = TrinityDataCoreStorageView.PAGE_SIZE;
    private static final int ICON_OFFSET = 1;
    private static final float ICON_SCALE = 0.875F;
    private static final int ICON_DEPTH = 0;

    private final List<StorageRow> rows = new ArrayList<>(VISIBLE_ENTRY_COUNT);
    private final IntConsumer pageRequest;
    private TrinityDataCoreStorageView value = TrinityDataCoreStorageView.EMPTY;
    private int requestedFirstEntry;

    TrinityStorageContentsList(String id, IntConsumer pageRequest) {
        this.pageRequest = pageRequest;
        setId(id);
        setOverflowVisible(false);
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(3)
                .top(4)
                .width(ROW_WIDTH)
                .height(VISIBLE_ROW_COUNT * ROW_HEIGHT));
        for (int index = 0; index < VISIBLE_ENTRY_COUNT; index++) {
            StorageRow row = createRow(id, index);
            this.rows.add(row);
            addChild(row.root());
        }
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        internalSetup();
    }

    @Override
    public TrinityDataCoreStorageView getValue() {
        return this.value;
    }

    @Override
    public TrinityStorageContentsList setValue(@Nullable TrinityDataCoreStorageView value, boolean notify) {
        TrinityDataCoreStorageView next = value == null ? TrinityDataCoreStorageView.EMPTY : value;
        if (this.value.equals(next)) {
            return this;
        }
        this.value = next;
        int previousRequest = this.requestedFirstEntry;
        this.requestedFirstEntry = next.firstEntry();
        if (previousRequest != this.requestedFirstEntry) {
            this.pageRequest.accept(this.requestedFirstEntry);
        }
        refreshRows();
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    @Override
    protected void onRemoved() {
        for (var dataSource : List.copyOf(getBoundDataSources())) {
            unbindDataSource(dataSource);
        }
        super.onRemoved();
    }

    private StorageRow createRow(String idPrefix, int index) {
        UIElement root = new UIElement();
        root.setId(idPrefix + "_entry_" + index);
        root.setOverflowVisible(false);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(index * ROW_HEIGHT)
                .width(ROW_WIDTH)
                .height(ROW_HEIGHT));

        StorageKeyIcon icon = new StorageKeyIcon();
        icon.setId(root.getId() + "_icon");
        icon.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(ICON_OFFSET)
                .top(ICON_OFFSET)
                .width(ROW_HEIGHT - ICON_OFFSET * 2)
                .height(ROW_HEIGHT - ICON_OFFSET * 2));

        Label name = new Label();
        name.setId(root.getId() + "_name");
        name.addClass("trinity-storage-entry-name");
        name.setText(Component.empty());
        name.setAllowHitTest(false);
        name.setOverflowVisible(false);
        name.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(20)
                .top(1)
                .width(42)
                .height(16));

        Label amount = new Label();
        amount.setId(root.getId() + "_amount");
        amount.addClass("trinity-storage-entry-amount");
        amount.setText(Component.empty());
        amount.setAllowHitTest(false);
        amount.setOverflowVisible(false);
        amount.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(64)
                .top(1)
                .width(64)
                .height(16));

        root.addChildren(icon, name, amount);
        StorageRow row = new StorageRow(root, icon, name, amount);
        root.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            if (row.entry != null) {
                event.hoverTooltips = new HoverTooltips(tooltip(row.entry), null, null, null);
            }
        });
        deactivate(row);
        return row;
    }

    private void onMouseWheel(UIEvent event) {
        int maximum = maxFirstVisibleEntry();
        if (maximum == 0 || event.deltaY == 0.0F) {
            return;
        }
        int direction = event.deltaY > 0.0F ? -1 : 1;
        int requestedEntry = Math.clamp(this.requestedFirstEntry + direction, 0, maximum);
        if (requestedEntry == this.requestedFirstEntry) {
            return;
        }
        this.requestedFirstEntry = requestedEntry;
        this.pageRequest.accept(this.requestedFirstEntry);
        event.stopPropagation();
    }

    private int maxFirstVisibleEntry() {
        return Math.max(0, this.value.status().typeCount() - VISIBLE_ROW_COUNT);
    }

    private void refreshRows() {
        for (int visibleIndex = 0; visibleIndex < this.rows.size(); visibleIndex++) {
            StorageRow row = this.rows.get(visibleIndex);
            if (visibleIndex >= this.value.entries().size()) {
                deactivate(row);
            } else {
                activate(row, this.value.entries().get(visibleIndex));
            }
        }
    }

    private static void activate(StorageRow row, Entry entry) {
        row.icon().setKey(entry.key());
        row.name().setText(entry.key().getDisplayName());
        row.amount().setText(Component.literal(TrinityAmountFormatter.format(entry.amount())));
        row.entry = entry;
        row.root().setVisible(true);
    }

    private static void deactivate(StorageRow row) {
        row.icon().setKey(null);
        row.name().setText(Component.empty());
        row.amount().setText(Component.empty());
        row.entry = null;
        row.root().setVisible(false);
    }

    private static List<Component> tooltip(Entry entry) {
        List<Component> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(entry.key()));
        if (tooltip.isEmpty()) {
            tooltip.add(entry.key().getDisplayName());
        }
        String formattedAmount = TrinityAmountFormatter.format(entry.amount());
        tooltip.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.entry_amount",
                formattedAmount).withStyle(ChatFormatting.GRAY));
        if (!formattedAmount.equals(entry.amount().toString())) {
            tooltip.add(Component.translatable(
                    "screen.data_energistics.trinity_data_core.storage.entry_amount_exact",
                    entry.amount().toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    private static final class StorageRow {

        private final UIElement root;
        private final StorageKeyIcon icon;
        private final Label name;
        private final Label amount;
        @Nullable
        private Entry entry;

        private StorageRow(UIElement root, StorageKeyIcon icon, Label name, Label amount) {
            this.root = root;
            this.icon = icon;
            this.name = name;
            this.amount = amount;
        }

        private UIElement root() {
            return this.root;
        }

        private StorageKeyIcon icon() {
            return this.icon;
        }

        private Label name() {
            return this.name;
        }

        private Label amount() {
            return this.amount;
        }
    }

    /**
     * Draws one synchronized AE key without relying on item-wrapper rendering semantics.
     */
    private static final class StorageKeyIcon extends UIElement {

        @Nullable
        private AEKey key;

        private StorageKeyIcon() {
            setAllowHitTest(false);
        }

        private void setKey(@Nullable AEKey key) {
            this.key = key;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext guiContext) {
            if (this.key == null) {
                return;
            }
            guiContext.graphics.flush();
            guiContext.pose.pushPose();
            guiContext.pose.translate(getContentX(), getContentY(), ICON_DEPTH);
            guiContext.pose.scale(ICON_SCALE, ICON_SCALE, 1.0F);
            AEKeyRendering.drawInGui(guiContext.mc, guiContext.graphics, 0, 0, this.key);
            guiContext.pose.popPose();
        }
    }
}
