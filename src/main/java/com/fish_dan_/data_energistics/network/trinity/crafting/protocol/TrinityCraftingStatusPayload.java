package com.fish_dan_.data_energistics.network.trinity.crafting.protocol;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCraftingStatusEntry;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityReusableStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityReusableStatus.Phase;
import com.fish_dan_.data_energistics.network.trinity.crafting.client.TrinityCraftingStatusClientHandler;

import appeng.api.stacks.AEKey;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;

/** One bounded part of an exact CPU status update. Quantities never pass through the native long packet codec. */
public record TrinityCraftingStatusPayload(int containerId, long sequence, int batchIndex, int totalEntries,
                                           Header header, List<TrinityCraftingStatusEntry> entries)
        implements CustomPacketPayload {

    public static final int MAX_ENTRIES_PER_BATCH = 64;
    private static final int MAX_TOTAL_ENTRIES = 1_048_576;
    public static final Type<TrinityCraftingStatusPayload> TYPE = new Type<>(Data_Energistics.id("trinity_crafting_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityCraftingStatusPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityCraftingStatusPayload::write, TrinityCraftingStatusPayload::new);

    public TrinityCraftingStatusPayload {
        entries = List.copyOf(entries);
        if (containerId < 0 || sequence < 0 || totalEntries < 0 || totalEntries > MAX_TOTAL_ENTRIES || batchIndex < 0 ||
                batchIndex >= Math.max(1, (totalEntries + MAX_ENTRIES_PER_BATCH - 1) / MAX_ENTRIES_PER_BATCH) ||
                entries.size() != Math.min(MAX_ENTRIES_PER_BATCH, totalEntries - batchIndex * MAX_ENTRIES_PER_BATCH)) {
            throw new IllegalArgumentException("Invalid Trinity CPU status batch metadata");
        }
    }

    private TrinityCraftingStatusPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                new Header(buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readBoolean(),
                        new TrinityReusableStatus(buffer.readEnum(Phase.class), buffer.readVarInt(), readAmount(buffer), readAmount(buffer),
                                buffer.readUtf(TrinityReusableStatus.MAX_DIAGNOSTIC_LENGTH))),
                readEntries(buffer));
    }

    /** Splits one server-thread capture; sequence is monotonic for the entire menu, including CPU switches. */
    public static List<TrinityCraftingStatusPayload> batches(int containerId, long sequence, Header header,
                                                             List<TrinityCraftingStatusEntry> entries) {
        int count = Math.max(1, (entries.size() + MAX_ENTRIES_PER_BATCH - 1) / MAX_ENTRIES_PER_BATCH);
        List<TrinityCraftingStatusPayload> result = new ObjectArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * MAX_ENTRIES_PER_BATCH;
            result.add(new TrinityCraftingStatusPayload(containerId, sequence, index, entries.size(), header,
                    entries.subList(from, Math.min(entries.size(), from + MAX_ENTRIES_PER_BATCH))));
        }
        return result;
    }

    @Override
    public Type<TrinityCraftingStatusPayload> type() {
        return TYPE;
    }

    /** Delivers only on the client game thread, after the owning screen and menu are checked. */
    public static void handle(TrinityCraftingStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityCraftingStatusClientHandler.receive(payload, context.player()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.sequence);
        buffer.writeVarInt(this.batchIndex);
        buffer.writeVarInt(this.totalEntries);
        buffer.writeBoolean(this.header.full());
        buffer.writeVarLong(this.header.elapsedTime());
        buffer.writeVarLong(this.header.remainingWork());
        buffer.writeVarLong(this.header.startWork());
        buffer.writeBoolean(this.header.suspended());
        TrinityReusableStatus reusable = this.header.reusable();
        buffer.writeEnum(reusable.phase());
        buffer.writeVarInt(reusable.sessions());
        writeAmount(buffer, reusable.heldTools());
        writeAmount(buffer, reusable.spareTools());
        buffer.writeUtf(reusable.diagnostic(), TrinityReusableStatus.MAX_DIAGNOSTIC_LENGTH);
        buffer.writeVarInt(this.entries.size());
        for (TrinityCraftingStatusEntry entry : this.entries) {
            buffer.writeVarLong(entry.getSerial());
            AEKey.writeOptionalKey(buffer, entry.getWhat());
            writeAmount(buffer, entry.stored());
            writeAmount(buffer, entry.active());
            writeAmount(buffer, entry.pending());
            writeAmount(buffer, entry.resident());
        }
    }

    private static List<TrinityCraftingStatusEntry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES_PER_BATCH) {
            throw new IllegalArgumentException("Invalid Trinity CPU status batch size");
        }
        List<TrinityCraftingStatusEntry> entries = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new TrinityCraftingStatusEntry(buffer.readVarLong(), AEKey.readOptionalKey(buffer),
                    readAmount(buffer), readAmount(buffer), readAmount(buffer), readAmount(buffer)));
        }
        return entries;
    }

    private static void writeAmount(RegistryFriendlyByteBuf buffer, BigInteger amount) {
        buffer.writeByteArray(TrinityBigIntegerEncoding.encode(amount, "CPU status amount"));
    }

    private static BigInteger readAmount(RegistryFriendlyByteBuf buffer) {
        return TrinityBigIntegerEncoding.decode(buffer.readByteArray(TrinityBigIntegerEncoding.MAX_BYTES), "CPU status amount");
    }

    /** Time and AE2's normalized progress scale, not material counts or an ownership ledger. */
    public record Header(boolean full, long elapsedTime, long remainingWork, long startWork, boolean suspended, TrinityReusableStatus reusable) {

        public Header {
            if (elapsedTime < 0 || remainingWork < 0 || startWork < remainingWork) {
                throw new IllegalArgumentException("Invalid Trinity CPU status progress header");
            }
        }
    }
}
