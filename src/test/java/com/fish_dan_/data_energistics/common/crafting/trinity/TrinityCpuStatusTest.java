package com.fish_dan_.data_energistics.common.crafting.trinity;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityCpuStatusTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initializeAeKeyTypes();
    }

    private static void initializeAeKeyTypes() {
        synchronized (AEKeyTypesInternal.class) {
            try {
                AEKeyTypesInternal.getRegistry();
                return;
            } catch (IllegalStateException notInitialized) {
                Registry<AEKeyType> registry = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY)
                        .disableRegistrationCheck()
                        .create();
                AEKeyTypesInternal.setRegistry(registry);
                Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
                Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
                ((MappedRegistry<AEKeyType>) registry).freeze();
            }
        }
    }

    @Test
    void packetRoundTripPreservesBusyAndIdleCpuFieldsAndConsumesBuffer() {
        RegistryFriendlyByteBuf data = buffer();
        try {
            GenericStack target = new GenericStack(itemKey(data.registryAccess(), Items.DIAMOND), 8_192L);
            TrinityCpuStatus busy = new TrinityCpuStatus(
                    7,
                    4_194_304L,
                    12,
                    Component.literal("Worker 7"),
                    CpuSelectionMode.PLAYER_ONLY,
                    target,
                    0.625F,
                    9_876_543_210L);
            TrinityCpuStatus idle = new TrinityCpuStatus(
                    0,
                    65_536L,
                    0,
                    null,
                    CpuSelectionMode.ANY,
                    null,
                    0.0F,
                    0L);
            TrinityCpuListStatus original = new TrinityCpuListStatus(List.of(busy, idle));

            TrinityCpuListStatus.STREAM_CODEC.encode(data, original);
            TrinityCpuListStatus decoded = TrinityCpuListStatus.STREAM_CODEC.decode(data);

            assertEquals(List.of(idle, busy), decoded.cpus());
            assertEquals(0, data.readableBytes());
        } finally {
            data.release();
        }
    }

    @Test
    void listSortsByStableNumberAndRejectsDuplicateNumbers() {
        TrinityCpuStatus cpu3 = idle(3);
        TrinityCpuStatus cpu1 = idle(1);
        TrinityCpuStatus cpu2 = idle(2);

        assertEquals(List.of(cpu1, cpu2, cpu3), new TrinityCpuListStatus(List.of(cpu3, cpu1, cpu2)).cpus());
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityCpuListStatus(List.of(cpu1, idle(1))));
    }

    @Test
    void codecRoundTripPreservesOrderedIdleCpuFields() {
        TrinityCpuListStatus original = new TrinityCpuListStatus(List.of(idle(9), idle(2)));

        JsonElement encoded = TrinityCpuListStatus.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        TrinityCpuListStatus decoded = TrinityCpuListStatus.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(original, decoded);
    }

    @Test
    void jobSnapshotKeepsRequestedTargetAmountAndProgressUsesTotalWork() {
        GenericStack target = new GenericStack(AEItemKey.of(Items.DIAMOND), 4L);
        CraftingJobStatus status = new CraftingJobStatus(target, 250L, 75L, 1_000L);

        GenericStack currentJob = TrinityCpuStatus.toCurrentJob(status);
        assertNotNull(currentJob);
        assertEquals(target, currentJob);
        assertEquals(4L, currentJob.amount());
        assertEquals(0.3F, TrinityCpuStatus.calculateProgress(250L, 75L));
        assertEquals(0.0F, TrinityCpuStatus.calculateProgress(250L, -1L));
        assertEquals(1.0F, TrinityCpuStatus.calculateProgress(250L, 251L));
        assertEquals(0.0F, TrinityCpuStatus.calculateProgress(0L, 1L));
    }

    private static TrinityCpuStatus idle(int number) {
        return new TrinityCpuStatus(
                number,
                1_024L,
                0,
                Component.literal("CPU " + number),
                CpuSelectionMode.ANY,
                null,
                0.0F,
                0L);
    }

    private static RegistryFriendlyByteBuf buffer() {
        MappedRegistry<Item> itemRegistry = (MappedRegistry<Item>) new RegistryBuilder<Item>(Registries.ITEM)
                .defaultKey(BuiltInRegistries.ITEM.getDefaultKey())
                .sync(true)
                .disableRegistrationCheck()
                .create();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceKey<Item> key = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
            itemRegistry.register(key, item, RegistrationInfo.BUILT_IN);
        }
        itemRegistry.freeze();
        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(List.of(itemRegistry)).freeze();
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER);
    }

    private static AEItemKey itemKey(RegistryAccess registries, Item item) {
        ResourceKey<Item> key = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
        Holder.Reference<Item> holder = registries.registryOrThrow(Registries.ITEM).getHolderOrThrow(key);
        AEItemKey itemKey = AEItemKey.of(new ItemStack(holder, 1, DataComponentPatch.EMPTY));
        if (itemKey == null) {
            throw new IllegalStateException("Test item key must not be empty");
        }
        return itemKey;
    }
}
