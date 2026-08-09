package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture.WrapMode;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Native LDLib2 capacity component for item, fluid, and remaining AE key storage.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(
             name = "data_energistics:trinity_storage_capacity_bar",
             group = "data_energistics",
             registry = "ldlib2:ui_element")
public final class TrinityStorageCapacityBar extends BindableUIElement<TrinityDataCoreStorageStatus> {

    public static final String TRACK_ID = "trinity_storage_capacity_track";
    public static final String ITEM_SEGMENT_ID = "trinity_storage_capacity_item";
    public static final String FLUID_SEGMENT_ID = "trinity_storage_capacity_fluid";
    public static final String OTHER_SEGMENT_ID = "trinity_storage_capacity_other";
    public static final String NEUTRAL_SEGMENT_ID = "trinity_storage_capacity_neutral";

    private static final int DEFAULT_WIDTH = 116;
    private static final int DEFAULT_HEIGHT = 6;
    private static final int TRACK_BORDER = 1;
    private static final int FILL_HEIGHT = 4;
    private static final int NEUTRAL_COLOR = 0xFF8793A1;
    private static final SpriteTexture TRACK_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/trinity_data_core/storage_capacity_track.png")
            .setSprite(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private static final SpriteTexture ITEM_TEXTURE = fillTexture("storage_item_fill.png");
    private static final SpriteTexture FLUID_TEXTURE = fillTexture("storage_fluid_fill.png");
    private static final SpriteTexture OTHER_TEXTURE = fillTexture("storage_other_fill.png");

    private final UIElement itemSegment;
    private final UIElement fluidSegment;
    private final UIElement otherSegment;
    private final UIElement neutralSegment;
    private TrinityDataCoreStorageStatus value = TrinityDataCoreStorageStatus.EMPTY;

    /** Creates a registry-instantiable capacity component with its complete internal LDLib2 hierarchy. */
    public TrinityStorageCapacityBar() {
        addClass("trinity-storage-capacity-bar");
        UIElement track = createLayer(TRACK_ID, TRACK_TEXTURE, "trinity-storage-capacity-track");
        this.itemSegment = createLayer(ITEM_SEGMENT_ID, ITEM_TEXTURE, "trinity-storage-capacity-segment");
        this.fluidSegment = createLayer(FLUID_SEGMENT_ID, FLUID_TEXTURE, "trinity-storage-capacity-segment");
        this.otherSegment = createLayer(OTHER_SEGMENT_ID, OTHER_TEXTURE, "trinity-storage-capacity-segment");
        this.neutralSegment = createLayer(
                NEUTRAL_SEGMENT_ID,
                new ColorRectTexture(NEUTRAL_COLOR),
                "trinity-storage-capacity-segment");

        addChildren(track, this.itemSegment, this.fluidSegment, this.otherSegment, this.neutralSegment);
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                tooltipLines(), null, null, null));
        internalSetup();
        refreshSegments();
    }

    @Override
    public TrinityDataCoreStorageStatus getValue() {
        return this.value;
    }

    @Override
    public TrinityStorageCapacityBar setValue(@Nullable TrinityDataCoreStorageStatus value, boolean notify) {
        if (value == null) {
            throw new IllegalArgumentException("Trinity storage capacity value is required");
        }
        if (value.equals(this.value)) {
            return this;
        }
        this.value = value;
        refreshSegments();
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    @Override
    protected void onLayoutChanged() {
        super.onLayoutChanged();
        refreshSegments();
    }

    private void refreshSegments() {
        int width = Math.max(0, (int) Math.floor(getContentWidth()) - TRACK_BORDER * 2);
        TrinityStorageCapacityLayout capacityLayout = TrinityStorageCapacityLayout.calculate(
                width,
                this.value.itemAmount(),
                this.value.fluidAmount(),
                this.value.otherKeyAmount(),
                this.value.amountCapacity(),
                this.value.unlimited());

        int left = TRACK_BORDER;
        placeSegment(this.itemSegment, left, capacityLayout.itemWidth());
        left += capacityLayout.itemWidth();
        placeSegment(this.fluidSegment, left, capacityLayout.fluidWidth());
        left += capacityLayout.fluidWidth();
        placeSegment(this.otherSegment, left, capacityLayout.otherWidth());
        left += capacityLayout.otherWidth();
        placeSegment(this.neutralSegment, left, capacityLayout.neutralWidth());
    }

    private List<Component> tooltipLines() {
        Component capacity = this.value.unlimited() ?
                Component.translatable("gui.data_energistics.trinity.unlimited") :
                Component.literal(TrinityAmountFormatter.format(this.value.amountCapacity()));
        Component stored = Component.literal(TrinityAmountFormatter.format(this.value.totalAmount()) + "/")
                .append(capacity);
        return List.of(
                exactAmountLine("gui.ae2.Items", TrinityAmountFormatter.format(this.value.itemAmount())),
                exactAmountLine("gui.ae2.Fluids", TrinityAmountFormatter.format(this.value.fluidAmount())),
                Component.translatable(
                        "screen.data_energistics.trinity_data_core.storage_other_keys",
                        TrinityAmountFormatter.format(this.value.otherKeyAmount())).withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.data_energistics.trinity_data_core.storage_amount", stored)
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component exactAmountLine(String translationKey, String amount) {
        return Component.empty()
                .append(Component.translatable(translationKey))
                .append(": ")
                .append(amount)
                .withStyle(ChatFormatting.GRAY);
    }

    private static UIElement createLayer(String id, IGuiTexture texture, String layoutClass) {
        UIElement layer = new UIElement();
        layer.setId(id);
        layer.addClass(layoutClass);
        layer.setAllowHitTest(false);
        layer.getStyle().backgroundTexture(texture);
        return layer;
    }

    private static void placeSegment(UIElement segment, int left, int width) {
        segment.setVisible(width > 0);
        segment.layout(layout -> layout
                .left(left)
                .width(width));
    }

    private static SpriteTexture fillTexture(String fileName) {
        return SpriteTexture.of("data_energistics:textures/guis/trinity_data_core/" + fileName)
                .setSprite(0, 0, 2, FILL_HEIGHT)
                .setWrapMode(WrapMode.REPEAT);
    }
}
