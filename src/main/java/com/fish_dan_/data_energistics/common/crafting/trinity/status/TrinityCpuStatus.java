package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Immutable client-facing snapshot of one Trinity crafting CPU.
 *
 * <p>
 * The number is the stable {@link TrinityDataCoreVirtualCpu#number()} rather than AE2's menu-local serial.
 */
public record TrinityCpuStatus(
                               int number,
                               long storage,
                               int coProcessors,
                               @Nullable Component name,
                               CpuSelectionMode mode,
                               @Nullable GenericStack currentJob,
                               float progress,
                               long elapsedTimeNanos) {

    private static final Codec<CpuSelectionMode> SELECTION_MODE_CODEC = Codec.STRING.xmap(
            CpuSelectionMode::valueOf,
            CpuSelectionMode::name);

    public static final Codec<TrinityCpuStatus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("number").forGetter(TrinityCpuStatus::number),
            Codec.LONG.fieldOf("storage").forGetter(TrinityCpuStatus::storage),
            Codec.INT.fieldOf("co_processors").forGetter(TrinityCpuStatus::coProcessors),
            ComponentSerialization.CODEC.optionalFieldOf("name")
                    .forGetter(status -> Optional.ofNullable(status.name())),
            SELECTION_MODE_CODEC.fieldOf("mode").forGetter(TrinityCpuStatus::mode),
            GenericStack.CODEC.optionalFieldOf("current_job")
                    .forGetter(status -> Optional.ofNullable(status.currentJob())),
            Codec.FLOAT.fieldOf("progress").forGetter(TrinityCpuStatus::progress),
            Codec.LONG.fieldOf("elapsed_time_nanos").forGetter(TrinityCpuStatus::elapsedTimeNanos)).apply(instance,
                    (number, storage, coProcessors, name, mode, currentJob, progress, elapsedTimeNanos) -> new TrinityCpuStatus(
                            number,
                            storage,
                            coProcessors,
                            name.orElse(null),
                            mode,
                            currentJob.orElse(null),
                            progress,
                            elapsedTimeNanos)));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityCpuStatus> STREAM_CODEC = StreamCodec.of(
            TrinityCpuStatus::encode,
            TrinityCpuStatus::decode);

    public TrinityCpuStatus {
        if (number < 0) {
            throw new IllegalArgumentException("Trinity CPU number must not be negative");
        }
        if (storage < 0L) {
            throw new IllegalArgumentException("Trinity CPU storage must not be negative");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("Trinity CPU co-processors must not be negative");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Trinity CPU selection mode is required");
        }
        if (currentJob != null && currentJob.amount() < 0L) {
            throw new IllegalArgumentException("Trinity CPU current job amount must not be negative");
        }
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Trinity CPU progress must be finite and between zero and one");
        }
        if (elapsedTimeNanos < 0L) {
            throw new IllegalArgumentException("Trinity CPU elapsed time must not be negative");
        }
    }

    /** Creates a snapshot exclusively from the public crafting CPU status contract. */
    public static TrinityCpuStatus from(TrinityDataCoreVirtualCpu cpu) {
        if (cpu == null) {
            throw new IllegalArgumentException("Trinity CPU is required");
        }
        CraftingJobStatus jobStatus = cpu.getJobStatus();
        return new TrinityCpuStatus(
                cpu.number(),
                cpu.getAvailableStorage(),
                cpu.getCoProcessors(),
                cpu.getName(),
                cpu.getSelectionMode(),
                toCurrentJob(jobStatus),
                jobStatus == null ? 0.0F : calculateProgress(jobStatus.totalItems(), jobStatus.progress()),
                jobStatus == null ? 0L : jobStatus.elapsedTimeNanos());
    }

    private static void encode(RegistryFriendlyByteBuf data, TrinityCpuStatus status) {
        data.writeVarInt(status.number);
        data.writeVarLong(status.storage);
        data.writeVarInt(status.coProcessors);
        data.writeBoolean(status.name != null);
        if (status.name != null) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(data, status.name);
        }
        data.writeEnum(status.mode);
        GenericStack.writeBuffer(status.currentJob, data);
        data.writeFloat(status.progress);
        data.writeVarLong(status.elapsedTimeNanos);
    }

    private static TrinityCpuStatus decode(RegistryFriendlyByteBuf data) {
        return new TrinityCpuStatus(
                data.readVarInt(),
                data.readVarLong(),
                data.readVarInt(),
                data.readBoolean() ? ComponentSerialization.TRUSTED_STREAM_CODEC.decode(data) : null,
                data.readEnum(CpuSelectionMode.class),
                GenericStack.readBuffer(data),
                data.readFloat(),
                data.readVarLong());
    }

    /** Returns whether the CPU currently owns a crafting job. */
    public boolean busy() {
        return this.currentJob != null;
    }

    @Nullable
    static GenericStack toCurrentJob(@Nullable CraftingJobStatus status) {
        if (status == null) {
            return null;
        }
        if (status.crafting() == null) {
            throw new IllegalStateException("Busy Trinity CPU status is missing its crafting target");
        }
        if (status.totalItems() < 0L) {
            throw new IllegalStateException("Busy Trinity CPU status has a negative total item count");
        }
        return status.crafting();
    }

    static float calculateProgress(long totalItems, long completedItems) {
        if (totalItems <= 0L) {
            return 0.0F;
        }
        double ratio = completedItems / (double) totalItems;
        return (float) Math.clamp(ratio, 0.0D, 1.0D);
    }
}
