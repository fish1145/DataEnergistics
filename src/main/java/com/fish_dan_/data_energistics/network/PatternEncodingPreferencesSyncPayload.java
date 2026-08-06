package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
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
                                                    ResourceLocation lastWorkstation,
                                                    int previewPanelOffsetX,
                                                    int previewPanelOffsetY,
                                                    PatternEncodingRankingContext rankingContext,
                                                    List<LeafStatistic> statistics)
        implements CustomPacketPayload {

    public static final int MAX_STATISTICS = 2048;
    public static final int MAX_DIGEST_LENGTH = 71;
    public static final int MAX_CONTEXT_BYTES = 256;
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
        validateOffset(previewPanelOffsetX, previewPanelOffsetY);
        if (rankingContext != null) {
            validateContext(rankingContext);
        }
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
     * One absolute count for a provider leaf in the current recipe/workstation context.
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
    public Type<PatternEncodingPreferencesSyncPayload> type() {
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

        PatternEncodingPreferenceSession session = preferenceMenu.data_energistics$getPreferenceSession();
        Set<String> visibleLeafDigests = new HashSet<>();
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : previewMenu.data_energistics$getSyncedPatternProviders()) {
            visibleLeafDigests.addAll(provider.leafDigests());
        }
        for (LeafStatistic statistic : payload.statistics) {
            if (!visibleLeafDigests.contains(statistic.providerDigest())) {
                Data_Energistics.LOGGER.warn("Rejected pattern preference statistic for non-visible provider leaf {}",
                        statistic.providerDigest());
                return;
            }
        }

        if (!PatternEncodingSourceHelper.isRankingContextValid(
                previewMenu, sourceAware, payload.rankingContext, serverPlayer.level())) {
            Data_Energistics.LOGGER.warn(
                    "Rejected pattern preference snapshot with a forged recipe or workstation context for container {}",
                    payload.containerId);
            return;
        }

        if (payload.rankingContext == null && !payload.statistics.isEmpty()) {
            Data_Energistics.LOGGER.warn("Rejected pattern preference statistics without a ranking context for container {}",
                    payload.containerId);
            return;
        }
        if (!session.acceptIncomingSequence(payload.sequence)) {
            Data_Energistics.LOGGER.warn("Rejected out-of-order pattern preference sequence {} for container {}",
                    payload.sequence, payload.containerId);
            return;
        }

        int migratedMask = PatternEncodingClientPreferenceMask.missingMask(payload.presentMask);
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.UPLOAD_ENABLED) != 0) {
            sourceAware.data_energistics$setUploadEnabled(payload.uploadEnabled);
        }
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.PATTERN_SOURCE_ENABLED) != 0) {
            sourceAware.data_energistics$setPatternSourceEnabled(payload.patternSourceEnabled);
        }
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.LAST_WORKSTATION) != 0) {
            sourceAware.data_energistics$setLastEncodedPatternSource(payload.lastWorkstation);
        }
        if ((payload.presentMask & PatternEncodingClientPreferenceMask.PREVIEW_PANEL) != 0) {
            layoutAware.data_energistics$setPreviewPanelOffset(payload.previewPanelOffsetX, payload.previewPanelOffsetY);
        }
        session.setRankingContext(payload.rankingContext);
        session.replaceLeafCounts(payload.statistics.stream()
                .collect(Collectors.toMap(LeafStatistic::providerDigest, LeafStatistic::count)));

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

    private static void validateContext(PatternEncodingRankingContext context) {
        if (context.recipeScope().getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES || context.workstation().toString().getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
            throw new IllegalArgumentException("Pattern preference ranking context is too long");
        }
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

    private static void writeNullableResourceLocation(RegistryFriendlyByteBuf buffer, ResourceLocation value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeResourceLocation(value);
        }
    }

    private static ResourceLocation readNullableResourceLocation(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }

    private static void writeContext(RegistryFriendlyByteBuf buffer, PatternEncodingRankingContext context) {
        buffer.writeBoolean(context != null);
        if (context != null) {
            buffer.writeUtf(context.recipeScope(), MAX_CONTEXT_BYTES);
            buffer.writeResourceLocation(context.workstation());
        }
    }

    private static PatternEncodingRankingContext readContext(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        return new PatternEncodingRankingContext(buffer.readUtf(MAX_CONTEXT_BYTES), buffer.readResourceLocation());
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
