package com.fish_dan_.data_energistics.common.crafting.trinity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ordered immutable synchronization snapshot for the CPUs currently published by one Trinity structure. */
public record TrinityCpuListStatus(List<TrinityCpuStatus> cpus) {

    public static final TrinityCpuListStatus EMPTY = new TrinityCpuListStatus(List.of());
    public static final Codec<TrinityCpuListStatus> CODEC = TrinityCpuStatus.CODEC.listOf().xmap(
            TrinityCpuListStatus::new,
            TrinityCpuListStatus::cpus);
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityCpuListStatus> STREAM_CODEC = StreamCodec.of(
            TrinityCpuListStatus::encode,
            TrinityCpuListStatus::decode);
    private static final int MAX_CPU_COUNT = TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT + 1;

    public TrinityCpuListStatus {
        if (cpus == null) {
            throw new IllegalArgumentException("Trinity CPU status list is required");
        }
        if (cpus.size() > MAX_CPU_COUNT) {
            throw new IllegalArgumentException("Trinity CPU status list exceeds the maximum published CPU count");
        }
        List<TrinityCpuStatus> sorted = new ArrayList<>(cpus);
        if (sorted.contains(null)) {
            throw new IllegalArgumentException("Trinity CPU status list must not contain null entries");
        }
        sorted.sort(Comparator.comparingInt(TrinityCpuStatus::number));
        for (int index = 0; index < sorted.size(); index++) {
            TrinityCpuStatus status = sorted.get(index);
            if (index > 0 && sorted.get(index - 1).number() == status.number()) {
                throw new IllegalArgumentException("Duplicate Trinity CPU number: " + status.number());
            }
        }
        cpus = List.copyOf(sorted);
    }

    /** Captures the runtime's exact AE2-visible publication snapshot. */
    public static TrinityCpuListStatus from(TrinityDataCoreCraftingRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("Trinity crafting runtime is required");
        }
        return fromPublishedCpus(runtime.publishedCpus());
    }

    /** Captures a caller-owned snapshot returned by {@link TrinityDataCoreCraftingRuntime#publishedCpus()}. */
    public static TrinityCpuListStatus fromPublishedCpus(List<TrinityDataCoreVirtualCpu> publishedCpus) {
        if (publishedCpus == null) {
            throw new IllegalArgumentException("Published Trinity CPU list is required");
        }
        return new TrinityCpuListStatus(publishedCpus.stream().map(TrinityCpuStatus::from).toList());
    }

    private static void encode(RegistryFriendlyByteBuf data, TrinityCpuListStatus status) {
        data.writeVarInt(status.cpus.size());
        for (TrinityCpuStatus cpu : status.cpus) {
            TrinityCpuStatus.STREAM_CODEC.encode(data, cpu);
        }
    }

    private static TrinityCpuListStatus decode(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        if (count < 0 || count > MAX_CPU_COUNT) {
            throw new IllegalArgumentException("Invalid synchronized Trinity CPU count: " + count);
        }
        List<TrinityCpuStatus> statuses = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            statuses.add(TrinityCpuStatus.STREAM_CODEC.decode(data));
        }
        return new TrinityCpuListStatus(statuses);
    }
}
