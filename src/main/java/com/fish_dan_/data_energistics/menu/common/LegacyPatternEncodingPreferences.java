package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable legacy values captured before a menu starts applying client preference synchronization.
 */
public record LegacyPatternEncodingPreferences(
                                               boolean uploadEnabled,
                                               boolean patternSourceEnabled,
                                               @Nullable ResourceLocation lastWorkstation,
                                               int previewPanelOffsetX,
                                               int previewPanelOffsetY) {

    /**
     * Applies the migration precedence of player NBT, terminal-specific fallback, and defaults.
     * The session-backed last workstation returned by the helper takes precedence over both persisted sources.
     */
    public static LegacyPatternEncodingPreferences capture(Player player,
                                                           boolean fallbackUploadEnabled,
                                                           boolean fallbackPatternSourceEnabled,
                                                           @Nullable ResourceLocation fallbackLastWorkstation,
                                                           int fallbackOffsetX,
                                                           int fallbackOffsetY) {
        if (player == null) {
            throw new IllegalArgumentException("Legacy pattern preference player must not be null");
        }
        boolean uploadEnabled = PatternEncodingSourceHelper.hasLegacyUploadEnabled(player) ? PatternEncodingSourceHelper.readUploadEnabled(player) : fallbackUploadEnabled;
        boolean patternSourceEnabled = PatternEncodingSourceHelper.hasLegacyPatternSourceEnabled(player) ? PatternEncodingSourceHelper.readPatternSourceEnabled(player) : fallbackPatternSourceEnabled;
        ResourceLocation lastWorkstation = PatternEncodingSourceHelper.readLastEncodedPatternSource(player);
        if (lastWorkstation == null) {
            lastWorkstation = fallbackLastWorkstation;
        }
        validateOffset(fallbackOffsetX, fallbackOffsetY);
        return new LegacyPatternEncodingPreferences(uploadEnabled, patternSourceEnabled, lastWorkstation,
                fallbackOffsetX, fallbackOffsetY);
    }

    private static void validateOffset(int offsetX, int offsetY) {
        if (offsetX < -8192 || offsetX > 8192 || offsetY < -8192 || offsetY > 8192) {
            throw new IllegalArgumentException("Legacy pattern preview offset is outside [-8192, 8192]");
        }
    }
}
