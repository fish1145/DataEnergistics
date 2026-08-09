package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import appeng.menu.guisync.PacketWritable;
import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Exposes the server-authoritative pattern-provider preview and transfer operations of an encoding menu.
 */
public interface PatternEncodingPreviewMenu {

    long data_energistics$getNetworkBlankPatternCount();

    EncodingMode data_energistics$getEncodingMode();

    /**
     * Returns provider rows together with the exact ranking context used to resolve their workstation metadata.
     */
    SyncedPatternProviderList data_energistics$getSyncedPatternProviderState();

    /**
     * Returns the provider rows for existing preview consumers that do not need workstation resolution state.
     */
    default List<SyncedPatternProvider> data_energistics$getSyncedPatternProviders() {
        return data_energistics$getSyncedPatternProviderState().providers();
    }

    /**
     * Rebuilds the provider snapshot from the current server grid after ranking context or history changes.
     */
    void data_energistics$refreshSyncedPatternProviders();

    void data_energistics$transferEncodedPatternToProvider(long providerId);

    void data_energistics$openPatternProviderMenu(long providerId);

    void data_energistics$renamePatternProvider(long providerId, String name);

    /**
     * Context-bound provider snapshot synchronized through the menu.
     */
    record SyncedPatternProviderList(
                                     List<SyncedPatternProvider> providers,
                                     @Nullable PatternEncodingRankingContext rankingContext)
            implements PacketWritable {

        public static final SyncedPatternProviderList EMPTY = new SyncedPatternProviderList(List.of(), null);

        public SyncedPatternProviderList {
            providers = List.copyOf(providers);
        }

        public SyncedPatternProviderList(RegistryFriendlyByteBuf data) {
            this(readProviders(data), readRankingContext(data));
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeVarInt(this.providers.size());
            for (var provider : this.providers) {
                provider.writeToPacket(data);
            }
            writeRankingContext(data, this.rankingContext);
        }

        private static List<SyncedPatternProvider> readProviders(
                                                                 RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            List<SyncedPatternProvider> providers = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                providers.add(new SyncedPatternProvider(data));
            }
            return providers;
        }

        private static void writeRankingContext(
                                                RegistryFriendlyByteBuf data,
                                                @Nullable PatternEncodingRankingContext rankingContext) {
            data.writeBoolean(rankingContext != null);
            if (rankingContext == null) {
                return;
            }
            writeBoundedResourceLocation(data, rankingContext.recipeTypeId());
        }

        private static @Nullable PatternEncodingRankingContext readRankingContext(
                                                                                  RegistryFriendlyByteBuf data) {
            if (!data.readBoolean()) {
                return null;
            }
            return new PatternEncodingRankingContext(readBoundedResourceLocation(data, "recipe type id"));
        }

        private static void writeBoundedResourceLocation(
                                                         RegistryFriendlyByteBuf data,
                                                         ResourceLocation id) {
            data.writeUtf(id.toString(), PatternEncodingRankingContext.MAX_RESOURCE_LOCATION_BYTES);
        }

        private static ResourceLocation readBoundedResourceLocation(
                                                                    RegistryFriendlyByteBuf data,
                                                                    String label) {
            String encoded = data.readUtf(PatternEncodingRankingContext.MAX_RESOURCE_LOCATION_BYTES);
            ResourceLocation id = ResourceLocation.tryParse(encoded);
            if (id == null) {
                throw new IllegalArgumentException("Invalid pattern ranking " + label + ": " + encoded);
            }
            return id;
        }
    }

    /**
     * One displayed provider group and its preferred workstation for the enclosing snapshot context.
     */
    record SyncedPatternProvider(
                                 long id,
                                 Component displayName,
                                 ResourceLocation iconItemId,
                                 boolean useAeButtonStyle,
                                 boolean renameable,
                                 int patternSlotCount,
                                 int usedPatternSlotCount,
                                 List<String> leafDigests,
                                 @Nullable ResourceLocation preferredWorkstationId) {

        public SyncedPatternProvider {
            leafDigests = List.copyOf(leafDigests);
        }

        public SyncedPatternProvider(RegistryFriendlyByteBuf data) {
            this(
                    data.readLong(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(data),
                    data.readResourceLocation(),
                    data.readBoolean(),
                    data.readBoolean(),
                    data.readVarInt(),
                    data.readVarInt(),
                    readLeafDigests(data),
                    readNullableResourceLocation(data));
        }

        private void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeLong(this.id);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(data, this.displayName);
            data.writeResourceLocation(this.iconItemId);
            data.writeBoolean(this.useAeButtonStyle);
            data.writeBoolean(this.renameable);
            data.writeVarInt(this.patternSlotCount);
            data.writeVarInt(this.usedPatternSlotCount);
            data.writeVarInt(this.leafDigests.size());
            for (String digest : this.leafDigests) {
                data.writeUtf(digest, 71);
            }
            data.writeBoolean(this.preferredWorkstationId != null);
            if (this.preferredWorkstationId != null) {
                data.writeResourceLocation(this.preferredWorkstationId);
            }
        }

        private static List<String> readLeafDigests(
                                                    RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            if (size < 0 || size > 2048) {
                throw new IllegalArgumentException("Pattern provider leaf digest count is outside [0, 2048]: " + size);
            }
            List<String> digests = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                String digest = data.readUtf(71);
                if (!digest.matches("sha256:[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("Invalid pattern provider leaf digest: " + digest);
                }
                digests.add(digest);
            }
            return List.copyOf(digests);
        }

        private static @Nullable ResourceLocation readNullableResourceLocation(
                                                                               RegistryFriendlyByteBuf data) {
            return data.readBoolean() ? data.readResourceLocation() : null;
        }
    }
}
