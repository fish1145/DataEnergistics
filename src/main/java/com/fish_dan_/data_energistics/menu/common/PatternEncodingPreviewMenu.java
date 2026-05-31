package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import appeng.menu.guisync.PacketWritable;
import appeng.parts.encoding.EncodingMode;

import java.util.ArrayList;
import java.util.List;

public interface PatternEncodingPreviewMenu {

    long data_energistics$getNetworkBlankPatternCount();

    EncodingMode data_energistics$getEncodingMode();

    List<SyncedPatternProvider> data_energistics$getSyncedPatternProviders();

    void data_energistics$transferEncodedPatternToProvider(long providerId);

    void data_energistics$openPatternProviderMenu(long providerId);

    void data_energistics$renamePatternProvider(long providerId, String name);

    record SyncedPatternProviderList(List<SyncedPatternProvider> providers) implements PacketWritable {

        public static final SyncedPatternProviderList EMPTY = new SyncedPatternProviderList(List.of());

        public SyncedPatternProviderList {
            providers = List.copyOf(providers);
        }

        public SyncedPatternProviderList(RegistryFriendlyByteBuf data) {
            this(readProviders(data));
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeVarInt(this.providers.size());
            for (var provider : this.providers) {
                provider.writeToPacket(data);
            }
        }

        private static List<SyncedPatternProvider> readProviders(RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            List<SyncedPatternProvider> providers = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                providers.add(new SyncedPatternProvider(data));
            }
            return providers;
        }
    }

    record SyncedPatternProvider(
                                 long id,
                                 Component displayName,
                                 ResourceLocation iconItemId,
                                 boolean useAeButtonStyle,
                                 boolean renameable,
                                 int patternSlotCount,
                                 int usedPatternSlotCount) {

        public SyncedPatternProvider(RegistryFriendlyByteBuf data) {
            this(
                    data.readLong(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(data),
                    data.readResourceLocation(),
                    data.readBoolean(),
                    data.readBoolean(),
                    data.readVarInt(),
                    data.readVarInt());
        }

        private void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeLong(this.id);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(data, this.displayName);
            data.writeResourceLocation(this.iconItemId);
            data.writeBoolean(this.useAeButtonStyle);
            data.writeBoolean(this.renameable);
            data.writeVarInt(this.patternSlotCount);
            data.writeVarInt(this.usedPatternSlotCount);
        }
    }
}
