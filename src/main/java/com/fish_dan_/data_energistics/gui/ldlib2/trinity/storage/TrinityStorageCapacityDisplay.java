package com.fish_dan_.data_energistics.gui.ldlib2.trinity.storage;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Independent vertical capacity visualization for the Trinity storage hosted window.
 */
final class TrinityStorageCapacityDisplay extends BindableUIElement<TrinityDataCoreStorageView> {

    private static final int BAR_WIDTH = 20;
    private static final int BAR_HEIGHT = 135;
    private static final int TEXTURE_SLICE_HEIGHT = 2;

    private static final SpriteTexture ITEM_FILL = fillTexture("item_fill.png");
    private static final SpriteTexture FLUID_FILL = fillTexture("fluid_fill.png");
    private static final SpriteTexture OTHER_FILL = fillTexture("aekey_fill.png");

    private TrinityDataCoreStorageView value = TrinityDataCoreStorageView.EMPTY;

    TrinityStorageCapacityDisplay(String id) {
        setId(id);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(140)
                .top(8)
                .width(BAR_WIDTH)
                .height(BAR_HEIGHT));
        addEventListener(
                UIEvents.HOVER_TOOLTIPS,
                event -> event.hoverTooltips = new HoverTooltips(tooltip(this.value.status()), null, null, null));
        internalSetup();
    }

    @Override
    public @NotNull TrinityDataCoreStorageView getValue() {
        return this.value;
    }

    @Override
    public @NotNull TrinityStorageCapacityDisplay setValue(@Nullable TrinityDataCoreStorageView value, boolean notify) {
        TrinityDataCoreStorageView next = value == null ? TrinityDataCoreStorageView.EMPTY : value;
        if (this.value.equals(next)) {
            return this;
        }
        this.value = next;
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

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(@NotNull GUIContext guiContext) {
        SegmentHeights heights = SegmentHeights.calculate(this.value.status());
        int bottom = (int) (getContentY() + BAR_HEIGHT);
        bottom = drawSegment(guiContext, ITEM_FILL, bottom, heights.itemHeight());
        bottom = drawSegment(guiContext, FLUID_FILL, bottom, heights.fluidHeight());
        drawSegment(guiContext, OTHER_FILL, bottom, heights.otherHeight());
    }

    @OnlyIn(Dist.CLIENT)
    private int drawSegment(GUIContext guiContext, SpriteTexture texture, int bottom, int height) {
        int segmentBottom = bottom;
        int remaining = height;
        while (remaining > 0) {
            int sliceHeight = Math.min(TEXTURE_SLICE_HEIGHT, remaining);
            segmentBottom -= sliceHeight;
            texture.draw(
                    guiContext.graphics,
                    guiContext.localMouseX,
                    guiContext.localMouseY,
                    getContentX(),
                    segmentBottom,
                    BAR_WIDTH,
                    sliceHeight,
                    guiContext.partialTick);
            remaining -= sliceHeight;
        }
        return segmentBottom;
    }

    private static List<Component> tooltip(TrinityDataCoreStorageStatus status) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.amount_exact",
                TrinityAmountFormatter.format(status.totalAmount()),
                status.unlimited() ?
                        Component.translatable("gui.data_energistics.trinity.unlimited") :
                        Component.literal(TrinityAmountFormatter.format(status.amountCapacity()))));
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.item_amount",
                TrinityAmountFormatter.format(status.itemAmount())).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.fluid_amount",
                TrinityAmountFormatter.format(status.fluidAmount())).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.other_amount",
                TrinityAmountFormatter.format(status.otherKeyAmount())).withStyle(ChatFormatting.GREEN));
        return lines;
    }

    private static SpriteTexture fillTexture(String name) {
        return SpriteTexture.of("data_energistics:textures/guis/storage/" + name)
                .setSprite(0, 0, BAR_WIDTH, TEXTURE_SLICE_HEIGHT);
    }

    private record SegmentHeights(int itemHeight, int fluidHeight, int otherHeight) {

        private static SegmentHeights calculate(TrinityDataCoreStorageStatus status) {
            BigInteger total = status.totalAmount();
            if (total.signum() == 0) {
                return new SegmentHeights(0, 0, 0);
            }
            int filledHeight;
            if (status.unlimited() || status.amountCapacity().signum() == 0) {
                filledHeight = BAR_HEIGHT;
            } else {
                filledHeight = total.multiply(BigInteger.valueOf(BAR_HEIGHT))
                        .divide(status.amountCapacity())
                        .min(BigInteger.valueOf(BAR_HEIGHT))
                        .intValueExact();
            }
            int[] segments = distribute(
                    filledHeight,
                    total,
                    new BigInteger[] {
                            status.itemAmount(),
                            status.fluidAmount(),
                            status.otherKeyAmount()
                    });
            return new SegmentHeights(segments[0], segments[1], segments[2]);
        }

        private static int[] distribute(int filledHeight,
                                        BigInteger total,
                                        BigInteger[] amounts) {
            int[] heights = new int[amounts.length];
            BigInteger[] remainders = new BigInteger[amounts.length];
            BigInteger scaledHeight = BigInteger.valueOf(filledHeight);
            int allocated = 0;
            for (int index = 0; index < amounts.length; index++) {
                BigInteger[] quotientAndRemainder = amounts[index]
                        .multiply(scaledHeight)
                        .divideAndRemainder(total);
                heights[index] = quotientAndRemainder[0].intValueExact();
                remainders[index] = quotientAndRemainder[1];
                allocated += heights[index];
            }
            boolean[] assigned = new boolean[amounts.length];
            for (int remaining = filledHeight - allocated; remaining > 0; remaining--) {
                int selected = -1;
                for (int index = 0; index < remainders.length; index++) {
                    if (!assigned[index] &&
                            (selected < 0 || remainders[index].compareTo(remainders[selected]) > 0)) {
                        selected = index;
                    }
                }
                if (selected < 0) {
                    throw new IllegalStateException("Trinity storage capacity pixel allocation exceeded its segments");
                }
                heights[selected]++;
                assigned[selected] = true;
            }
            return heights;
        }
    }
}
