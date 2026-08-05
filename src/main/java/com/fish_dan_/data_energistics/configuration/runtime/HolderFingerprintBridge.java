package com.fish_dan_.data_energistics.configuration.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot;
import com.fish_dan_.data_energistics.configuration.snapshot.SnapshotAssembler;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.IConfigValueReadable;
import dev.toma.configuration.config.value.ObjectValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publishes a strictly validated immutable snapshot after the framework watcher changes its holder instance. */
public final class HolderFingerprintBridge {

    private final ConfigHolder<DataEnergisticsConfiguration> holder;
    private final Path source;
    private HolderState publishedState;
    private String lastRejection = "";

    HolderFingerprintBridge(
                            ConfigHolder<DataEnergisticsConfiguration> holder,
                            Path source) {
        this.holder = holder;
        this.source = source;
        synchronized (holder.getLock()) {
            this.publishedState = capture(holder);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTickPre(ServerTickEvent.Pre event) {
        refresh();
    }

    void refresh() {
        refreshMainConfiguration();
        DataExtractorRulesConfiguration.refresh();
    }

    private void refreshMainConfiguration() {
        try {
            HolderState before;
            HolderState after;
            ConfigurationSnapshot snapshot;
            long revision;
            synchronized (this.holder.getLock()) {
                before = capture(this.holder);
                if (before.values().equals(this.publishedState.values())) {
                    return;
                }
                revision = DataEnergisticsConfiguration.INSTANCE.revision() + 1L;
                snapshot = SnapshotAssembler.assemble(
                        this.holder.getConfigInstance(),
                        this.source,
                        revision);
                after = capture(this.holder);
                if (!before.values().equals(after.values())) {
                    return;
                }
                DataEnergisticsConfiguration.publish(snapshot);
                this.publishedState = after;
                this.lastRejection = "";
            }
            Data_Energistics.LOGGER.info(
                    "Published Configuration YAML {} at revision {} with holder fingerprint {}",
                    this.source,
                    revision,
                    before.fingerprint());
        } catch (IOException | RuntimeException exception) {
            reject(exception.toString(), exception);
            return;
        }
    }

    private void reject(String reason) {
        if (reason.equals(this.lastRejection)) {
            return;
        }
        this.lastRejection = reason;
        Data_Energistics.LOGGER.error(
                "Rejected runtime Configuration YAML {}; gameplay keeps revision {}: {}",
                this.source,
                DataEnergisticsConfiguration.INSTANCE.revision(),
                reason);
    }

    private void reject(String reason, Throwable cause) {
        if (reason.equals(this.lastRejection)) {
            return;
        }
        this.lastRejection = reason;
        Data_Energistics.LOGGER.error(
                "Rejected runtime Configuration YAML {}; gameplay keeps revision {}: {}",
                this.source,
                DataEnergisticsConfiguration.INSTANCE.revision(),
                reason,
                cause);
    }

    private static HolderState capture(ConfigHolder<DataEnergisticsConfiguration> holder) {
        Map<String, Object> values = new LinkedHashMap<>();
        collect(holder.getValueMap(), values);
        Map<String, Object> immutable = Map.copyOf(values);
        return new HolderState(immutable, immutable.hashCode());
    }

    private static void collect(Map<String, ConfigValue<?>> source, Map<String, Object> destination) {
        for (ConfigValue<?> value : source.values()) {
            if (value instanceof ObjectValue objectValue) {
                collect(objectValue.get(IConfigValueReadable.Mode.PENDING), destination);
            } else {
                destination.put(value.getPath(), immutableValue(value.get(IConfigValueReadable.Mode.PENDING)));
            }
        }
    }

    private static Object immutableValue(Object value) {
        if (value instanceof String[] strings) {
            return List.of(strings);
        }
        if (value instanceof double[] doubles) {
            return Arrays.stream(doubles).boxed().toList();
        }
        if (value instanceof Object[] array) {
            List<Object> copy = new ArrayList<>(array.length);
            for (Object entry : array) {
                copy.add(entry instanceof Enum<?> enumeration ? enumeration.name() : entry);
            }
            return List.copyOf(copy);
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        return value;
    }

    private record HolderState(Map<String, Object> values, int fingerprint) {}
}
