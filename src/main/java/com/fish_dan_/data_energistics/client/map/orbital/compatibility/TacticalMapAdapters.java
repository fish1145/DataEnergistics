package com.fish_dan_.data_energistics.client.map.orbital.compatibility;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.UUID;

/** Deterministic client registry for the embedded map followed by every loaded optional map adapter. */
public final class TacticalMapAdapters {

    private static final Object2ObjectLinkedOpenHashMap<ResourceLocation, TacticalMapAdapter> ADAPTERS = new Object2ObjectLinkedOpenHashMap<>();
    private static final ObjectOpenHashSet<ResourceLocation> DISABLED = new ObjectOpenHashSet<>();

    static {
        register(BuiltinAdapter.INSTANCE);
    }

    private TacticalMapAdapters() {}

    /** Registers one process-lifetime adapter; duplicate provider IDs are rejected immediately. */
    public static void register(TacticalMapAdapter adapter) {
        TacticalMapAdapter previous = ADAPTERS.putIfAbsent(adapter.id(), adapter);
        if (previous != null) {
            throw new IllegalStateException("Duplicate tactical-map adapter id: " + adapter.id());
        }
    }

    /** Returns an immutable, registration-ordered snapshot excluding adapters disabled after a runtime API failure. */
    public static List<TacticalMapAdapter> available() {
        ObjectArrayList<TacticalMapAdapter> available = new ObjectArrayList<>(ADAPTERS.size());
        for (TacticalMapAdapter adapter : ADAPTERS.values()) {
            if (!DISABLED.contains(adapter.id())) {
                available.add(adapter);
            }
        }
        return List.copyOf(available);
    }

    /** Starts one adapter and permanently isolates a failing optional provider for the rest of the client process. */
    public static TacticalMapAdapter.SelectionStart start(
                                                          TacticalMapAdapter adapter,
                                                          Minecraft minecraft,
                                                          UUID sessionToken) {
        if (DISABLED.contains(adapter.id())) {
            return TacticalMapAdapter.SelectionStart.FAILED;
        }
        try {
            return adapter.startSelection(minecraft, sessionToken);
        } catch (RuntimeException | LinkageError exception) {
            DISABLED.add(adapter.id());
            Data_Energistics.LOGGER.error(
                    "Disabling tactical-map adapter {} after its client API failed",
                    adapter.id(),
                    exception);
            return TacticalMapAdapter.SelectionStart.FAILED;
        }
    }

    private enum BuiltinAdapter implements TacticalMapAdapter {

        INSTANCE;

        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "builtin_tactical_map");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public Component displayName() {
            return Component.translatable(
                    "screen.data_energistics.orbital_control_terminal.fire_control.map.provider.builtin");
        }

        @Override
        public SelectionStart startSelection(Minecraft minecraft, UUID sessionToken) {
            return SelectionStart.EMBEDDED;
        }
    }
}
