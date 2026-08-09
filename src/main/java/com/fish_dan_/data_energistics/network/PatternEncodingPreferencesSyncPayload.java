package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * C2S snapshot of client preferences and the provider history visible in one pattern menu.
 */
public record PatternEncodingPreferencesSyncPayload(
                                                    int containerId,
                                                    long sequence,
                                                    int presentMask,
                                                    boolean uploadEnabled,
                                                    boolean patternSourceEnabled,
                                                    @Nullable ResourceLocation lastWorkstation,
                                                    int previewPanelOffsetX,
                                                    int previewPanelOffsetY,
                                                    @Nullable PatternEncodingRankingContext rankingContext,
                                                    @NotNull List<LeafStatistic> statistics)
        implements CustomPacketPayload {

    public static final int MAX_STATISTICS = 2048;
    public static final int MAX_DIGEST_LENGTH = 71;
    private static final int KNOWN_PRESENT_MASK = 0x0F;
    private static final Pattern DIGEST_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");
    public static final Type<PatternEncodingPreferencesSyncPayload> TYPE = new Type<>(
            Data_Energistics.id("pattern_encoding_preferences_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternEncodingPreferencesSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(PatternEncodingPreferencesSyncPayload::write,
            PatternEncodingPreferencesSyncPayload::new);

    /**
     * Validates bounded values before a snapshot can be sent or applied.
     */
    public PatternEncodingPreferencesSyncPayload {
        if (containerId < 0) {
            throw new IllegalArgumentException("Pattern preference container id must not be negative");
        }
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Pattern preference sequence must be positive");
        }
        if ((presentMask & ~KNOWN_PRESENT_MASK) != 0) {
            throw new IllegalArgumentException("Pattern preference present mask contains unknown bits: " + presentMask);
        }
        if (lastWorkstation != null && BuiltInRegistries.ITEM.get(lastWorkstation) == Items.AIR) {
            throw new IllegalArgumentException(
                    "Pattern preference last workstation is not a registered item: " + lastWorkstation);
        }
        validateOffset(previewPanelOffsetX, previewPanelOffsetY);
        statistics = List.copyOf(statistics);
        if (statistics.size() > MAX_STATISTICS) {
            throw new IllegalArgumentException("Pattern preference statistics exceed " + MAX_STATISTICS);
        }
        Set<String> seen = new HashSet<>();
        for (LeafStatistic statistic : statistics) {
            if (!seen.add(statistic.providerDigest())) {
                throw new IllegalArgumentException("Duplicate pattern provider statistic: " + statistic.providerDigest());
            }
        }
    }

    /**
     * One absolute count for a provider leaf in the current recipe-type context.
     */
    public record LeafStatistic(String providerDigest, long count) {

        public LeafStatistic {
            validateDigest(providerDigest);
            if (count < 0L) {
                throw new IllegalArgumentException("Pattern provider count must not be negative");
            }
        }
    }

    private PatternEncodingPreferencesSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarLong(), buffer.readUnsignedByte(), buffer.readBoolean(),
                buffer.readBoolean(), readNullableResourceLocation(buffer), buffer.readInt(), buffer.readInt(),
                readContext(buffer), readStatistics(buffer));
        requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeVarLong(this.sequence);
        buffer.writeByte(this.presentMask);
        buffer.writeBoolean(this.uploadEnabled);
        buffer.writeBoolean(this.patternSourceEnabled);
        writeNullableResourceLocation(buffer, this.lastWorkstation);
        buffer.writeInt(this.previewPanelOffsetX);
        buffer.writeInt(this.previewPanelOffsetY);
        writeContext(buffer, this.rankingContext);
        buffer.writeVarInt(this.statistics.size());
        for (LeafStatistic statistic : this.statistics) {
            buffer.writeUtf(statistic.providerDigest(), MAX_DIGEST_LENGTH);
            buffer.writeVarLong(statistic.count());
        }
    }

    @Override
    public @NotNull Type<PatternEncodingPreferencesSyncPayload> type() {
        return TYPE;
    }

    /**
     * Routes a validated snapshot to the server's current menu.
     */
    public static void handle(PatternEncodingPreferencesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleOnMainThread(payload, context.player()));
    }

    static void handleOnMainThread(PatternEncodingPreferencesSyncPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            Data_Energistics.LOGGER.warn("Rejected pattern preference snapshot outside a server player context");
            return;
        }
        AbstractContainerMenu menu = serverPlayer.containerMenu;
        if (menu.containerId != payload.containerId || !(menu instanceof PatternEncodingPreferenceMenu preferenceMenu) || !(menu instanceof PatternEncodingSourceAware sourceAware) || !(menu instanceof PatternEncodingPreviewLayoutAware layoutAware) || !(menu instanceof PatternEncodingPreviewMenu previewMenu)) {
            Data_Energistics.LOGGER.warn("Rejected pattern preference snapshot for stale or incompatible container {}",
                    payload.containerId);
            return;
        }

        if (!PatternEncodingSourceHelper.isRankingContextValid(previewMenu, payload.rankingContext)) {
            Data_Energistics.LOGGER.warn(
                    "Rejected pattern preference snapshot with a forged recipe type context for container {}",
                    payload.containerId);
            return;
        }

        if (payload.rankingContext == null && !payload.statistics.isEmpty()) {
            Data_Energistics.LOGGER.warn("Rejected pattern preference statistics without a ranking context for container {}",
                    payload.containerId);
            return;
        }
        PatternEncodingPreferenceSession session = preferenceMenu.data_energistics$getPreferenceSession();
        if (!session.canAcceptIncomingSequence(payload.sequence)) {
            Data_Energistics.LOGGER.warn("Rejected out-of-order pattern preference sequence {} for container {}",
                    payload.sequence, payload.containerId);
            return;
        }

        PatternEncodingRankingContext previousRankingContext = session.rankingContext();
        session.setRankingContext(payload.rankingContext);
        previewMenu.data_energistics$refreshSyncedPatternProviders();

        Set<String> visibleLeafDigests = new HashSet<>();
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : previewMenu.data_energistics$getSyncedPatternProviders()) {
            visibleLeafDigests.addAll(provider.leafDigests());
        }
        for (LeafStatistic statistic : payload.statistics) {
            if (!visibleLeafDigests.contains(statistic.providerDigest())) {
                Data_Energistics.LOGGER.warn("Rejected pattern preference statistic for non-visible provider leaf {}",
                        statistic.providerDigest());
                session.setRankingContext(previousRankingContext);
                previewMenu.data_energistics$refreshSyncedPatternProviders();
                return;
            }
        }
        if (!session.acceptIncomingSequence(payload.sequence)) {
            throw new IllegalStateException("Pattern preference sequence changed during main-thread validation");
        }

        int migratedMask = PatternEncodingClientPreferenceMask.missingMask(payload.presentMask);
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.UPLOAD_ENABLED) != 0) {
            sourceAware.data_energistics$setUploadEnabled(payload.uploadEnabled);
        }
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.PATTERN_SOURCE_ENABLED) != 0) {
            sourceAware.data_energistics$setPatternSourceEnabled(payload.patternSourceEnabled);
        }
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.PREVIEW_PANEL) != 0) {
            layoutAware.data_energistics$setPreviewPanelOffset(payload.previewPanelOffsetX, payload.previewPanelOffsetY);
        }
        session.replaceLeafCounts(payload.statistics.stream()
                .collect(Collectors.toMap(LeafStatistic::providerDigest, LeafStatistic::count)));
        previewMenu.data_energistics$refreshSyncedPatternProviders();
        if (previewMenu.data_energistics$getEncodingMode() == EncodingMode.PROCESSING) {
            PatternEncodingSourceHelper.applyPatternSource(sourceAware, null);
            if (menu instanceof PatternEncodingTermMenu patternMenu) {
                PatternEncodingSourceHelper.applyPendingTransferRecipeMetadata(patternMenu);
            }
        }

        PacketDistributor.sendToPlayer(serverPlayer, new PatternEncodingPreferencesAckPayload(
                payload.containerId,
                payload.sequence,
                migratedMask,
                sourceAware.data_energistics$isUploadEnabled(),
                sourceAware.data_energistics$isPatternSourceEnabled(),
                sourceAware.data_energistics$getLastEncodedPatternSource(),
                layoutAware.data_energistics$getPreviewPanelOffsetX(),
                layoutAware.data_energistics$getPreviewPanelOffsetY()));
    }

    private static void validateOffset(int x, int y) {
        if (x < -8192 || x > 8192 || y < -8192 || y > 8192) {
            throw new IllegalArgumentException("Pattern preference preview offset is outside [-8192, 8192]");
        }
    }

    private static void validateDigest(String digest) {
        if (digest == null || !DIGEST_PATTERN.matcher(digest).matches()) {
            throw new IllegalArgumentException("Invalid pattern provider digest: " + digest);
        }
    }

    private static void writeNullableResourceLocation(RegistryFriendlyByteBuf buffer,
                                                      @Nullable ResourceLocation value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeResourceLocation(value);
        }
    }

    private static @Nullable ResourceLocation readNullableResourceLocation(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }

    private static void writeContext(RegistryFriendlyByteBuf buffer,
                                     @Nullable PatternEncodingRankingContext context) {
        PatternEncodingRankingContextCodec.writeNullable(buffer, context);
    }

    private static @Nullable PatternEncodingRankingContext readContext(RegistryFriendlyByteBuf buffer) {
        return PatternEncodingRankingContextCodec.readNullable(buffer);
    }

    private static List<LeafStatistic> readStatistics(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_STATISTICS) {
            throw new IllegalArgumentException("Pattern preference statistics exceed " + MAX_STATISTICS);
        }
        List<LeafStatistic> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(new LeafStatistic(buffer.readUtf(MAX_DIGEST_LENGTH), buffer.readVarLong()));
        }
        return List.copyOf(result);
    }

    private static void requireFullyConsumed(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() != 0) {
            throw new IllegalArgumentException("Trailing bytes in pattern preference snapshot: " + buffer.readableBytes());
        }
    }

    /**
     * Shared bit values kept package-independent for the ack payload.
     */
    static final class PatternEncodingClientPreferenceMask {

        static final int UPLOAD_ENABLED = 1;
        static final int PATTERN_SOURCE_ENABLED = 1 << 1;
        static final int LAST_WORKSTATION = 1 << 2;
        static final int PREVIEW_PANEL = 1 << 3;

        private PatternEncodingClientPreferenceMask() {}

        static int missingMask(int presentMask) {
            return (UPLOAD_ENABLED | PATTERN_SOURCE_ENABLED | LAST_WORKSTATION | PREVIEW_PANEL) & ~presentMask;
        }
    }
}
