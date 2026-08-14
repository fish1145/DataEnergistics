package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Immutable server-authoritative progress and measured work cost of Data Core pattern maintenance.
 *
 * @param operation         operation that owns the catalog, or idle
 * @param stage             current bounded phase
 * @param completedUnits    actual work units completed across the operation
 * @param totalUnits        current authoritative total work units
 * @param installedPatterns patterns currently installed in the catalog
 * @param patternCapacity   total catalog capacity
 * @param succeededUnits    successful terminal units
 * @param failedUnits       failed terminal units
 * @param lastTickNanos     elapsed time spent in the latest maintenance task tick
 * @param lastTickWorkUnits increase in completed units during that same maintenance tick
 */
public record TrinityPatternMaintenanceSnapshot(Operation operation,
                                                Stage stage,
                                                long completedUnits,
                                                long totalUnits,
                                                int installedPatterns,
                                                int patternCapacity,
                                                int succeededUnits,
                                                int failedUnits,
                                                long lastTickNanos,
                                                long lastTickWorkUnits) {

    private static final Codec<Operation> OPERATION_CODEC = Codec.STRING.xmap(Operation::valueOf, Operation::name);
    private static final Codec<Stage> STAGE_CODEC = Codec.STRING.xmap(Stage::valueOf, Stage::name);
    public static final Codec<TrinityPatternMaintenanceSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    OPERATION_CODEC.fieldOf("operation").forGetter(TrinityPatternMaintenanceSnapshot::operation),
                    STAGE_CODEC.fieldOf("stage").forGetter(TrinityPatternMaintenanceSnapshot::stage),
                    Codec.LONG.fieldOf("completed_units").forGetter(TrinityPatternMaintenanceSnapshot::completedUnits),
                    Codec.LONG.fieldOf("total_units").forGetter(TrinityPatternMaintenanceSnapshot::totalUnits),
                    Codec.INT.fieldOf("installed_patterns").forGetter(TrinityPatternMaintenanceSnapshot::installedPatterns),
                    Codec.INT.fieldOf("pattern_capacity").forGetter(TrinityPatternMaintenanceSnapshot::patternCapacity),
                    Codec.INT.fieldOf("succeeded_units").forGetter(TrinityPatternMaintenanceSnapshot::succeededUnits),
                    Codec.INT.fieldOf("failed_units").forGetter(TrinityPatternMaintenanceSnapshot::failedUnits),
                    Codec.LONG.fieldOf("last_tick_nanos").forGetter(TrinityPatternMaintenanceSnapshot::lastTickNanos),
                    Codec.LONG.fieldOf("last_tick_work_units")
                            .forGetter(TrinityPatternMaintenanceSnapshot::lastTickWorkUnits))
            .apply(instance, TrinityPatternMaintenanceSnapshot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityPatternMaintenanceSnapshot> STREAM_CODEC = StreamCodec.of(TrinityPatternMaintenanceSnapshot::encode, TrinityPatternMaintenanceSnapshot::decode);

    public TrinityPatternMaintenanceSnapshot {
        if (completedUnits < 0L || totalUnits < 0L || completedUnits > totalUnits || installedPatterns < 0 ||
                patternCapacity < 0 || installedPatterns > patternCapacity || succeededUnits < 0 || failedUnits < 0 ||
                lastTickNanos < 0L || lastTickWorkUnits < 0L) {
            throw new IllegalArgumentException("Invalid Trinity pattern maintenance progress");
        }
        if (operation == Operation.IDLE && stage != Stage.IDLE) {
            throw new IllegalArgumentException("Idle Trinity pattern maintenance requires the idle stage");
        }
    }

    /** Returns the capacity-only state used when no maintenance result is retained. */
    public static TrinityPatternMaintenanceSnapshot idle(int installedPatterns, int patternCapacity) {
        return new TrinityPatternMaintenanceSnapshot(
                Operation.IDLE,
                Stage.IDLE,
                0L,
                0L,
                installedPatterns,
                patternCapacity,
                0,
                0,
                0L,
                0L);
    }

    /** Returns whether one migration or installed-pattern refund currently owns the catalog. */
    public boolean active() {
        return this.operation != Operation.IDLE && !this.stage.terminal();
    }

    /** Returns the displayed bottom-up fill fraction for capacity or active maintenance progress. */
    public float progress() {
        if (this.stage.terminal()) {
            return 1.0F;
        }
        boolean maintenance = this.operation != Operation.IDLE;
        long completed = maintenance ? this.completedUnits : this.installedPatterns;
        long total = maintenance ? this.totalUnits : this.patternCapacity;
        return total <= 0L ? 0.0F : (float) Math.clamp(completed / (double) total, 0.0D, 1.0D);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TrinityPatternMaintenanceSnapshot value) {
        buffer.writeEnum(value.operation);
        buffer.writeEnum(value.stage);
        buffer.writeVarLong(value.completedUnits);
        buffer.writeVarLong(value.totalUnits);
        buffer.writeVarInt(value.installedPatterns);
        buffer.writeVarInt(value.patternCapacity);
        buffer.writeVarInt(value.succeededUnits);
        buffer.writeVarInt(value.failedUnits);
        buffer.writeVarLong(value.lastTickNanos);
        buffer.writeVarLong(value.lastTickWorkUnits);
    }

    private static TrinityPatternMaintenanceSnapshot decode(RegistryFriendlyByteBuf buffer) {
        return new TrinityPatternMaintenanceSnapshot(
                buffer.readEnum(Operation.class),
                buffer.readEnum(Stage.class),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarLong());
    }

    /** Pattern-maintenance operation shown by the aggregate pattern view. */
    public enum Operation {
        IDLE,
        MIGRATION,
        REFUND_PATTERNS
    }

    /** Bounded task phase synchronized to the aggregate pattern view. */
    public enum Stage {

        IDLE(false),
        SCANNING(false),
        STORAGE(false),
        PATTERN_CONTAINERS(false),
        PREPARING_REFUND(false),
        WAITING_FOR_PLAYER(false),
        COMMITTING(false),
        COMPLETED(true),
        FAILED(true),
        CANCELLED(true);

        private final boolean terminal;

        Stage(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean terminal() {
            return this.terminal;
        }
    }
}
