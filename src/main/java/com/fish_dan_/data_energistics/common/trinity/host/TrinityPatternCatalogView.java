package com.fish_dan_.data_energistics.common.trinity.host;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded authoritative page of aggregate Trinity pattern slots.
 */
public record TrinityPatternCatalogView(long layoutRevision,
                                        long catalogRevision,
                                        int slotCount,
                                        int firstGlobalSlot,
                                        List<ItemStack> patterns) {

    public static final int COLUMN_COUNT = 9;
    public static final int ROW_COUNT = 8;
    public static final int PAGE_SIZE = COLUMN_COUNT * ROW_COUNT;
    public static final TrinityPatternCatalogView EMPTY = new TrinityPatternCatalogView(0L, 0L, 0, 0, List.of());
    public static final Codec<TrinityPatternCatalogView> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.LONG.fieldOf("layout_revision").forGetter(TrinityPatternCatalogView::layoutRevision),
                    Codec.LONG.fieldOf("catalog_revision").forGetter(TrinityPatternCatalogView::catalogRevision),
                    Codec.INT.fieldOf("slot_count").forGetter(TrinityPatternCatalogView::slotCount),
                    Codec.INT.fieldOf("first_global_slot").forGetter(TrinityPatternCatalogView::firstGlobalSlot),
                    ItemStack.OPTIONAL_CODEC.listOf().fieldOf("patterns").forGetter(TrinityPatternCatalogView::patterns))
            .apply(instance, TrinityPatternCatalogView::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityPatternCatalogView> STREAM_CODEC = StreamCodec.of(
            TrinityPatternCatalogView::encode,
            TrinityPatternCatalogView::decode);

    public TrinityPatternCatalogView {
        if (layoutRevision < 0L || catalogRevision < 0L || slotCount < 0 ||
                firstGlobalSlot != normalizeFirstGlobalSlot(firstGlobalSlot, slotCount)) {
            throw new IllegalArgumentException("Invalid Trinity pattern catalog page identity");
        }
        if (patterns.size() > PAGE_SIZE || (long) firstGlobalSlot + patterns.size() > slotCount) {
            throw new IllegalArgumentException("Trinity pattern catalog page exceeds its bounded slot range");
        }
        List<ItemStack> copies = new ArrayList<>(patterns.size());
        for (ItemStack pattern : patterns) {
            copies.add(pattern.copy());
        }
        patterns = List.copyOf(copies);
    }

    /** Clamps a requested first slot so a full fixed viewport can reach the catalog tail. */
    public static int normalizeFirstGlobalSlot(int requested, int slotCount) {
        if (slotCount < 0) {
            throw new IllegalArgumentException("Trinity pattern slot count must not be negative");
        }
        return Math.min(Math.max(0, requested), Math.max(0, slotCount - PAGE_SIZE));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TrinityPatternCatalogView view) {
        buffer.writeVarLong(view.layoutRevision);
        buffer.writeVarLong(view.catalogRevision);
        buffer.writeVarInt(view.slotCount);
        buffer.writeVarInt(view.firstGlobalSlot);
        buffer.writeVarInt(view.patterns.size());
        for (ItemStack pattern : view.patterns) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, pattern);
        }
    }

    private static TrinityPatternCatalogView decode(RegistryFriendlyByteBuf buffer) {
        long layoutRevision = buffer.readVarLong();
        long catalogRevision = buffer.readVarLong();
        int slotCount = buffer.readVarInt();
        int firstGlobalSlot = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid synchronized Trinity pattern count: " + count);
        }
        List<ItemStack> patterns = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            patterns.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        return new TrinityPatternCatalogView(
                layoutRevision,
                catalogRevision,
                slotCount,
                firstGlobalSlot,
                patterns);
    }
}
