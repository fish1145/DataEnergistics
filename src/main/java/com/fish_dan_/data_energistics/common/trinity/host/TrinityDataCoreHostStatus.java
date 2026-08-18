package com.fish_dan_.data_energistics.common.trinity.host;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable host summary synchronized to the native LDLib2 Trinity Data Core UI.
 */
public record TrinityDataCoreHostStatus(Optional<UUID> hostId,
                                        boolean online,
                                        StructureStatus mainStructure,
                                        StructureStatus cpuStructure,
                                        StructureStatus craftingStructure,
                                        int busyCraftingCpuCount,
                                        int cpuPartitionCount,
                                        int busyCpuPartitionCount,
                                        long cpuStorageBytes,
                                        int cpuCoProcessors,
                                        Optional<Component> craftingTarget) {

    public static final Codec<TrinityDataCoreHostStatus> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    UUIDUtil.CODEC.optionalFieldOf("host_id").forGetter(TrinityDataCoreHostStatus::hostId),
                    Codec.BOOL.fieldOf("online").forGetter(TrinityDataCoreHostStatus::online),
                    StructureStatus.CODEC.fieldOf("main_structure").forGetter(TrinityDataCoreHostStatus::mainStructure),
                    StructureStatus.CODEC.fieldOf("cpu_structure").forGetter(TrinityDataCoreHostStatus::cpuStructure),
                    StructureStatus.CODEC.fieldOf("crafting_structure")
                            .forGetter(TrinityDataCoreHostStatus::craftingStructure),
                    Codec.INT.fieldOf("busy_crafting_cpu_count")
                            .forGetter(TrinityDataCoreHostStatus::busyCraftingCpuCount),
                    Codec.INT.fieldOf("cpu_partition_count").forGetter(TrinityDataCoreHostStatus::cpuPartitionCount),
                    Codec.INT.fieldOf("busy_cpu_partition_count")
                            .forGetter(TrinityDataCoreHostStatus::busyCpuPartitionCount),
                    Codec.LONG.fieldOf("cpu_storage_bytes").forGetter(TrinityDataCoreHostStatus::cpuStorageBytes),
                    Codec.INT.fieldOf("cpu_co_processors").forGetter(TrinityDataCoreHostStatus::cpuCoProcessors),
                    ComponentSerialization.CODEC.optionalFieldOf("crafting_target")
                            .forGetter(TrinityDataCoreHostStatus::craftingTarget))
            .apply(instance, TrinityDataCoreHostStatus::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityDataCoreHostStatus> STREAM_CODEC = StreamCodec.of(TrinityDataCoreHostStatus::encode, TrinityDataCoreHostStatus::decode);

    public static final TrinityDataCoreHostStatus EMPTY = new TrinityDataCoreHostStatus(
            Optional.empty(),
            false,
            StructureStatus.EMPTY,
            StructureStatus.EMPTY,
            StructureStatus.EMPTY,
            0,
            0,
            0,
            0L,
            0,
            Optional.empty());

    public TrinityDataCoreHostStatus {
        if (hostId == null) {
            throw new NullPointerException("Host ID must not be null");
        }
        requireStructure(mainStructure, "Main structure status");
        requireStructure(cpuStructure, "CPU structure status");
        requireStructure(craftingStructure, "Crafting structure status");
        requireNonNegative(busyCraftingCpuCount, "Busy crafting CPU count");
        requireNonNegative(cpuPartitionCount, "CPU partition count");
        requireNonNegative(busyCpuPartitionCount, "Busy CPU partition count");
        if (busyCpuPartitionCount > cpuPartitionCount) {
            throw new IllegalArgumentException("Busy CPU partition count must not exceed the partition count");
        }
        if (cpuStorageBytes < 0L) {
            throw new IllegalArgumentException("CPU storage must not be negative");
        }
        requireNonNegative(cpuCoProcessors, "CPU co-processor count");
        if (craftingTarget == null) {
            throw new NullPointerException("Crafting target must not be null");
        }
    }

    /** Returns whether any synchronized structure has a diagnostic to display. */
    public boolean hasAnyFailure() {
        return this.mainStructure.hasFailure() || this.cpuStructure.hasFailure() || this.craftingStructure.hasFailure();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TrinityDataCoreHostStatus value) {
        buffer.writeBoolean(value.hostId.isPresent());
        value.hostId.ifPresent(buffer::writeUUID);
        buffer.writeBoolean(value.online);
        StructureStatus.STREAM_CODEC.encode(buffer, value.mainStructure);
        StructureStatus.STREAM_CODEC.encode(buffer, value.cpuStructure);
        StructureStatus.STREAM_CODEC.encode(buffer, value.craftingStructure);
        buffer.writeVarInt(value.busyCraftingCpuCount);
        buffer.writeVarInt(value.cpuPartitionCount);
        buffer.writeVarInt(value.busyCpuPartitionCount);
        buffer.writeVarLong(value.cpuStorageBytes);
        buffer.writeVarInt(value.cpuCoProcessors);
        buffer.writeBoolean(value.craftingTarget.isPresent());
        value.craftingTarget.ifPresent(target -> ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, target));
    }

    private static TrinityDataCoreHostStatus decode(RegistryFriendlyByteBuf buffer) {
        Optional<UUID> hostId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        boolean online = buffer.readBoolean();
        StructureStatus mainStructure = StructureStatus.STREAM_CODEC.decode(buffer);
        StructureStatus cpuStructure = StructureStatus.STREAM_CODEC.decode(buffer);
        StructureStatus craftingStructure = StructureStatus.STREAM_CODEC.decode(buffer);
        int busyCraftingCpuCount = buffer.readVarInt();
        int cpuPartitionCount = buffer.readVarInt();
        int busyCpuPartitionCount = buffer.readVarInt();
        long cpuStorageBytes = buffer.readVarLong();
        int cpuCoProcessors = buffer.readVarInt();
        Optional<Component> craftingTarget = buffer.readBoolean() ?
                Optional.of(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer)) : Optional.empty();
        return new TrinityDataCoreHostStatus(
                hostId,
                online,
                mainStructure,
                cpuStructure,
                craftingStructure,
                busyCraftingCpuCount,
                cpuPartitionCount,
                busyCpuPartitionCount,
                cpuStorageBytes,
                cpuCoProcessors,
                craftingTarget);
    }

    private static void requireStructure(StructureStatus value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    /**
     * Snapshot of one independently formed multiblock section and its latest diagnostic.
     */
    public record StructureStatus(boolean formed,
                                  String failureReason,
                                  String failurePosition) {

        public static final Codec<StructureStatus> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        Codec.BOOL.fieldOf("formed").forGetter(StructureStatus::formed),
                        Codec.STRING.fieldOf("failure_reason").forGetter(StructureStatus::failureReason),
                        Codec.STRING.fieldOf("failure_position").forGetter(StructureStatus::failurePosition))
                .apply(instance, StructureStatus::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, StructureStatus> STREAM_CODEC = StreamCodec.of(StructureStatus::encode, StructureStatus::decode);

        public static final StructureStatus EMPTY = new StructureStatus(false, "", "");

        public StructureStatus {
            if (failureReason == null) {
                throw new NullPointerException("Structure failure reason must not be null");
            }
            if (failurePosition == null) {
                throw new NullPointerException("Structure failure position must not be null");
            }
            if (failureReason.isBlank() && !failurePosition.isBlank()) {
                throw new IllegalArgumentException("Structure failure position requires a failure reason");
            }
        }

        /** Returns whether this structure has a diagnostic to show. */
        public boolean hasFailure() {
            return !this.failureReason.isBlank();
        }

        private static void encode(RegistryFriendlyByteBuf buffer, StructureStatus value) {
            buffer.writeBoolean(value.formed);
            buffer.writeUtf(value.failureReason);
            buffer.writeUtf(value.failurePosition);
        }

        private static StructureStatus decode(RegistryFriendlyByteBuf buffer) {
            return new StructureStatus(
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readUtf());
        }
    }
}
