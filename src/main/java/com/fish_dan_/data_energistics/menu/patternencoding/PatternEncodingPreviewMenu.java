package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import appeng.menu.guisync.PacketWritable;
import appeng.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

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

    void data_energistics$transferEncodedPatternToProviderLeaf(long groupId, long leafId);

    void data_energistics$openPatternProviderLeafMenu(long groupId, long leafId);

    void data_energistics$renamePatternProviderLeaf(long groupId, long leafId, String name);

    /**
     * Context-bound provider snapshot synchronized through the menu.
     */
    record SyncedPatternProviderList(
                                     List<SyncedPatternProvider> providers,
                                     @Nullable PatternEncodingRankingContext rankingContext,
                                     List<ResourceLocation> viewerWorkstationIds)
            implements PacketWritable {

        private static final int MAX_VIEWER_WORKSTATION_IDS = 2048;
        public static final SyncedPatternProviderList EMPTY = new SyncedPatternProviderList(List.of(), null, List.of());

        public SyncedPatternProviderList {
            providers = List.copyOf(providers);
            viewerWorkstationIds = List.copyOf(viewerWorkstationIds);
            if (rankingContext == null && !viewerWorkstationIds.isEmpty()) {
                throw new IllegalArgumentException("Viewer workstations require a synchronized ranking context");
            }
        }

        public SyncedPatternProviderList(RegistryFriendlyByteBuf data) {
            this(readProviders(data), readRankingContext(data), readViewerWorkstationIds(data));
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeVarInt(this.providers.size());
            for (var provider : this.providers) {
                provider.writeToPacket(data);
            }
            writeRankingContext(data, this.rankingContext);
            data.writeVarInt(this.viewerWorkstationIds.size());
            for (ResourceLocation workstationId : this.viewerWorkstationIds) {
                writeBoundedResourceLocation(data, workstationId);
            }
        }

        private static List<SyncedPatternProvider> readProviders(
                                                                 RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            List<SyncedPatternProvider> providers = new ObjectArrayList<>(size);
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

        private static List<ResourceLocation> readViewerWorkstationIds(RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            if (size < 0 || size > MAX_VIEWER_WORKSTATION_IDS) {
                throw new IllegalArgumentException(
                        "Pattern viewer workstation count is outside [0, " + MAX_VIEWER_WORKSTATION_IDS + "]: " + size);
            }
            List<ResourceLocation> workstationIds = new ObjectArrayList<>(size);
            for (int index = 0; index < size; index++) {
                workstationIds.add(readBoundedResourceLocation(data, "workstation id"));
            }
            return List.copyOf(workstationIds);
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
     * One displayed provider group, its advertised recipe types, and its preferred workstation for the enclosing
     * snapshot context.
     */
    record SyncedPatternProvider(
                                 long id,
                                 Component displayName,
                                 ResourceLocation iconItemId,
                                 boolean useAeButtonStyle,
                                 boolean renameable,
                                 int patternSlotCount,
                                 int usedPatternSlotCount,
                                 ObjectList<SyncedPatternProviderLeaf> leaves,
                                 List<ResourceLocation> supportedRecipeTypeIds,
                                 boolean exactViewerMatch,
                                 @Nullable ResourceLocation preferredWorkstationId) {

        public SyncedPatternProvider {
            leaves = ObjectLists.unmodifiable(new ObjectArrayList<>(leaves));
            if (leaves.isEmpty()) {
                throw new IllegalArgumentException("A synchronized pattern provider group requires at least one leaf");
            }
            LongOpenHashSet leafIds = new LongOpenHashSet();
            ObjectOpenHashSet<String> leafDigests = new ObjectOpenHashSet<>();
            for (SyncedPatternProviderLeaf leaf : leaves) {
                if (!leafIds.add(leaf.id())) {
                    throw new IllegalArgumentException("Duplicate synchronized pattern provider leaf id: " + leaf.id());
                }
                if (!leafDigests.add(leaf.providerDigest())) {
                    throw new IllegalArgumentException(
                            "Duplicate synchronized pattern provider leaf digest: " + leaf.providerDigest());
                }
            }
            supportedRecipeTypeIds = List.copyOf(supportedRecipeTypeIds);
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
                    readLeaves(data),
                    readSupportedRecipeTypeIds(data),
                    data.readBoolean(),
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
            data.writeVarInt(this.leaves.size());
            for (SyncedPatternProviderLeaf leaf : this.leaves) {
                leaf.writeToPacket(data);
            }
            data.writeVarInt(this.supportedRecipeTypeIds.size());
            for (ResourceLocation recipeTypeId : this.supportedRecipeTypeIds) {
                data.writeResourceLocation(recipeTypeId);
            }
            data.writeBoolean(this.exactViewerMatch);
            data.writeBoolean(this.preferredWorkstationId != null);
            if (this.preferredWorkstationId != null) {
                data.writeResourceLocation(this.preferredWorkstationId);
            }
        }

        /** Returns the single authoritative leaf digest projection used by ranking and upload history. */
        public ObjectList<String> leafDigests() {
            ObjectArrayList<String> digests = new ObjectArrayList<>(this.leaves.size());
            this.leaves.forEach(leaf -> digests.add(leaf.providerDigest()));
            return ObjectLists.unmodifiable(digests);
        }

        private static ObjectList<SyncedPatternProviderLeaf> readLeaves(RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            if (size < 0 || size > 2048) {
                throw new IllegalArgumentException("Pattern provider leaf digest count is outside [0, 2048]: " + size);
            }
            ObjectArrayList<SyncedPatternProviderLeaf> leaves = new ObjectArrayList<>(size);
            for (int index = 0; index < size; index++) {
                leaves.add(new SyncedPatternProviderLeaf(data));
            }
            return ObjectLists.unmodifiable(leaves);
        }

        private static List<ResourceLocation> readSupportedRecipeTypeIds(
                                                                         RegistryFriendlyByteBuf data) {
            int size = data.readVarInt();
            if (size < 0 || size > 2048) {
                throw new IllegalArgumentException(
                        "Pattern provider supported recipe type count is outside [0, 2048]: " + size);
            }
            List<ResourceLocation> recipeTypeIds = new ObjectArrayList<>(size);
            for (int index = 0; index < size; index++) {
                recipeTypeIds.add(data.readResourceLocation());
            }
            return List.copyOf(recipeTypeIds);
        }

        private static @Nullable ResourceLocation readNullableResourceLocation(
                                                                               RegistryFriendlyByteBuf data) {
            return data.readBoolean() ? data.readResourceLocation() : null;
        }
    }

    /** One exact physical or integration-owned upload target inside a synchronized aggregate row. */
    record SyncedPatternProviderLeaf(
                                     long id,
                                     String providerDigest,
                                     Component displayName,
                                     ResourceLocation iconItemId,
                                     boolean renameable,
                                     boolean openable,
                                     int patternSlotCount,
                                     int usedPatternSlotCount,
                                     SyncedPatternProviderLeafLocation location) {

        public SyncedPatternProviderLeaf {
            if (id <= 0L || !providerDigest.matches("sha256:[0-9a-f]{64}") ||
                    patternSlotCount <= 0 || usedPatternSlotCount < 0 ||
                    usedPatternSlotCount > patternSlotCount) {
                throw new IllegalArgumentException("Invalid synchronized pattern provider leaf");
            }
        }

        private SyncedPatternProviderLeaf(RegistryFriendlyByteBuf data) {
            this(
                    data.readLong(),
                    data.readUtf(71),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(data),
                    data.readResourceLocation(),
                    data.readBoolean(),
                    data.readBoolean(),
                    data.readVarInt(),
                    data.readVarInt(),
                    new SyncedPatternProviderLeafLocation(data));
        }

        private void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeLong(this.id);
            data.writeUtf(this.providerDigest, 71);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(data, this.displayName);
            data.writeResourceLocation(this.iconItemId);
            data.writeBoolean(this.renameable);
            data.writeBoolean(this.openable);
            data.writeVarInt(this.patternSlotCount);
            data.writeVarInt(this.usedPatternSlotCount);
            this.location.writeToPacket(data);
        }
    }

    /** Location projection used only to distinguish synchronized provider leaves in the client UI. */
    record SyncedPatternProviderLeafLocation(
                                             SyncedPatternProviderLeafLocationKind kind,
                                             @Nullable ResourceLocation dimensionId,
                                             @Nullable BlockPos blockPos,
                                             @Nullable Direction mountedSide) {

        public SyncedPatternProviderLeafLocation {
            boolean located = dimensionId != null && blockPos != null;
            if ((dimensionId == null) != (blockPos == null) ||
                    (kind == SyncedPatternProviderLeafLocationKind.UNLOCATED) == located ||
                    kind == SyncedPatternProviderLeafLocationKind.BLOCK && mountedSide != null) {
                throw new IllegalArgumentException("Invalid synchronized pattern provider leaf location");
            }
            blockPos = blockPos == null ? null : blockPos.immutable();
        }

        public static SyncedPatternProviderLeafLocation block(ResourceLocation dimensionId, BlockPos blockPos) {
            return new SyncedPatternProviderLeafLocation(
                    SyncedPatternProviderLeafLocationKind.BLOCK, dimensionId, blockPos, null);
        }

        public static SyncedPatternProviderLeafLocation part(
                                                             ResourceLocation dimensionId,
                                                             BlockPos blockPos,
                                                             @Nullable Direction mountedSide) {
            return new SyncedPatternProviderLeafLocation(
                    SyncedPatternProviderLeafLocationKind.PART, dimensionId, blockPos, mountedSide);
        }

        public static SyncedPatternProviderLeafLocation unlocated() {
            return new SyncedPatternProviderLeafLocation(
                    SyncedPatternProviderLeafLocationKind.UNLOCATED, null, null, null);
        }

        private SyncedPatternProviderLeafLocation(RegistryFriendlyByteBuf data) {
            this(readFromPacket(data));
        }

        private SyncedPatternProviderLeafLocation(SyncedPatternProviderLeafLocation decoded) {
            this(decoded.kind, decoded.dimensionId, decoded.blockPos, decoded.mountedSide);
        }

        private void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeByte(switch (this.kind) {
                case BLOCK -> 0;
                case PART -> 1;
                case UNLOCATED -> 2;
            });
            if (this.kind == SyncedPatternProviderLeafLocationKind.UNLOCATED) {
                return;
            }
            data.writeResourceLocation(this.dimensionId);
            data.writeBlockPos(this.blockPos);
            if (this.kind == SyncedPatternProviderLeafLocationKind.PART) {
                data.writeByte(this.mountedSide == null ? 0 : this.mountedSide.get3DDataValue() + 1);
            }
        }

        private static SyncedPatternProviderLeafLocationKind readKind(RegistryFriendlyByteBuf data) {
            return switch (data.readUnsignedByte()) {
                case 0 -> SyncedPatternProviderLeafLocationKind.BLOCK;
                case 1 -> SyncedPatternProviderLeafLocationKind.PART;
                case 2 -> SyncedPatternProviderLeafLocationKind.UNLOCATED;
                default -> throw new IllegalArgumentException("Unknown synchronized pattern provider location kind");
            };
        }

        private static SyncedPatternProviderLeafLocation readFromPacket(RegistryFriendlyByteBuf data) {
            SyncedPatternProviderLeafLocationKind kind = readKind(data);
            if (kind == SyncedPatternProviderLeafLocationKind.UNLOCATED) {
                return unlocated();
            }
            ResourceLocation dimensionId = data.readResourceLocation();
            BlockPos blockPos = data.readBlockPos();
            if (kind == SyncedPatternProviderLeafLocationKind.BLOCK) {
                return block(dimensionId, blockPos);
            }
            int encodedSide = data.readUnsignedByte();
            if (encodedSide > Direction.values().length) {
                throw new IllegalArgumentException(
                        "Invalid synchronized pattern provider mounted side: " + encodedSide);
            }
            Direction mountedSide = encodedSide == 0 ? null : Direction.from3DDataValue(encodedSide - 1);
            return part(dimensionId, blockPos, mountedSide);
        }
    }

    enum SyncedPatternProviderLeafLocationKind {
        BLOCK,
        PART,
        UNLOCATED
    }
}
