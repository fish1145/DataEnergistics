package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.network.patternencoding.PatternUploadSource;
import com.fish_dan_.data_energistics.network.patternencoding.PatternUploadSucceededPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Merges authoritative upload counts and renders localized, command-suggesting client chat notifications.
 */
public final class PatternUploadSucceededClientHandler {

    private static final Set<SuccessEventKey> DELIVERED_EVENTS = new HashSet<>();

    private PatternUploadSucceededClientHandler() {}

    /**
     * Applies one success event idempotently and notifies only for Data Energistics owned uploads.
     */
    public static void handle(@NotNull PatternUploadSucceededPayload payload, @NotNull Player player) {
        PatternEncodingClientPreferences preferences = PatternEncodingClientPreferencesAccess.get();
        ResourceLocation confirmedWorkstation = payload.confirmedWorkstation();
        if (confirmedWorkstation != null) {
            preferences.setLastWorkstation(confirmedWorkstation);
            applyConfirmedWorkstationToCurrentMenu(player, payload, confirmedWorkstation);
        }
        if (payload.rankingContext() != null) {
            preferences.recordUpload(payload.rankingContext(), payload.providerDigest(), payload.newCount(),
                    payload.epochMillis());
        }
        if (payload.source() != PatternUploadSource.DATA_ENERGISTICS || !DELIVERED_EVENTS.add(SuccessEventKey.from(payload))) {
            return;
        }
        ResourceLocation dimensionId = payload.dimensionId();
        BlockPos position = payload.position();
        if (dimensionId == null || position == null) {
            player.sendSystemMessage(Component.empty()
                    .append(Component.translatable("message.data_energistics.pattern_upload.success")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.translatable("message.data_energistics.pattern_upload.target")
                            .withStyle(ChatFormatting.WHITE))
                    .append(payload.targetName().copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.translatable("message.data_energistics.pattern_upload.location_unavailable")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("message.data_energistics.pattern_upload.time")
                            .withStyle(ChatFormatting.WHITE))
                    .append(createTimestampComponent(payload.epochMillis())));
            return;
        }
        player.sendSystemMessage(createSuccessMessage(payload, dimensionId, position));
    }

    private static void applyConfirmedWorkstationToCurrentMenu(
                                                               @NotNull Player player,
                                                               @NotNull PatternUploadSucceededPayload payload,
                                                               @NotNull ResourceLocation confirmedWorkstation) {
        if (!(player.containerMenu instanceof PatternEncodingPreferenceMenu preferenceMenu) ||
                !(player.containerMenu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }
        if (!Objects.equals(
                preferenceMenu.data_energistics$getPreferenceSession().rankingContext(),
                payload.rankingContext())) {
            return;
        }
        preferenceMenu.data_energistics$getPreferenceSession()
                .initializeConfirmedWorkstation(confirmedWorkstation);
        sourceAware.data_energistics$setLastEncodedPatternSource(confirmedWorkstation);
    }

    /**
     * Clears connection-local event identities so a later server session starts independently.
     */
    public static void clear() {
        DELIVERED_EVENTS.clear();
    }

    private static Component createSuccessMessage(@NotNull PatternUploadSucceededPayload payload,
                                                  @NotNull ResourceLocation dimensionId,
                                                  @NotNull BlockPos position) {
        String coordinates = "(" + position.getX() + ", " + position.getY() + ", " + position.getZ() + ")";
        String dimension = dimensionId.toString();
        String tpCommand = "/tp @s " + format(position.getX() + 0.5D) + " " + (position.getY() + 1) + " " + format(position.getZ() + 0.5D);
        String dimensionCommand = "/execute in " + dimension + " run tp @s " + format(position.getX() + 0.5D) + " " + (position.getY() + 1) + " " + format(position.getZ() + 0.5D);
        Component coordinateComponent = Component.literal(coordinates)
                .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.data_energistics.pattern_upload.coordinates_hover")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand)));
        Component dimensionComponent = Component.literal(dimension)
                .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.data_energistics.pattern_upload.dimension_hover")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, dimensionCommand)));
        return Component.empty()
                .append(Component.translatable("message.data_energistics.pattern_upload.success")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("message.data_energistics.pattern_upload.target")
                        .withStyle(ChatFormatting.WHITE))
                .append(payload.targetName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("message.data_energistics.pattern_upload.coordinates")
                        .withStyle(ChatFormatting.WHITE))
                .append(coordinateComponent)
                .append(Component.translatable("message.data_energistics.pattern_upload.dimension")
                        .withStyle(ChatFormatting.WHITE))
                .append(dimensionComponent)
                .append(Component.translatable("message.data_energistics.pattern_upload.time")
                        .withStyle(ChatFormatting.WHITE))
                .append(createTimestampComponent(payload.epochMillis()));
    }

    private static Component createTimestampComponent(long epochMillis) {
        String timestamp = Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT));
        return Component.literal(timestamp).withStyle(ChatFormatting.GRAY);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record SuccessEventKey(@NotNull PatternUploadSource source,
                                   @Nullable PatternEncodingRankingContext rankingContext,
                                   @Nullable ResourceLocation confirmedWorkstation,
                                   @NotNull String providerDigest,
                                   long newCount,
                                   @NotNull String targetEncoding,
                                   @Nullable ResourceLocation dimensionId,
                                   @Nullable BlockPos position,
                                   long epochMillis) {

        private static SuccessEventKey from(PatternUploadSucceededPayload payload) {
            String targetEncoding = GsonHelper.toStableString(
                    ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, payload.targetName()).getOrThrow());
            return new SuccessEventKey(
                    payload.source(), payload.rankingContext(), payload.confirmedWorkstation(),
                    payload.providerDigest(), payload.newCount(),
                    targetEncoding, payload.dimensionId(), payload.position(), payload.epochMillis());
        }
    }
}
