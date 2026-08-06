package com.fish_dan_.data_energistics.integration.extendedaeplus;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.common.PatternProviderSyncHelper;
import com.fish_dan_.data_energistics.menu.common.PatternUploadRecorder;
import com.fish_dan_.data_energistics.network.PatternUploadSource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;

/**
 * Tracks the single committed inventory write produced by one ExtendedAE-Plus encoder upload.
 */
public final class EaepPatternUploadScope {

    private static final ThreadLocal<Scope> ACTIVE_SCOPE = new ThreadLocal<>();

    private EaepPatternUploadScope() {}

    /**
     * Captures the exact menu session and ranking context before a server upload is queued.
     */
    public static UploadSnapshot capture(ServerPlayer player, PatternEncodingTermMenu menu,
                                         PatternEncodingRankingContext rankingContext) {
        if (player == null || menu == null || rankingContext == null) {
            throw new IllegalArgumentException("ExtendedAE-Plus upload snapshot requires a player, menu, and context");
        }
        if (menu.getPlayer() != player) {
            throw new IllegalArgumentException("ExtendedAE-Plus upload snapshot player does not own the encoding menu");
        }
        if (!(menu instanceof PatternEncodingPreferenceMenu preferenceMenu)) {
            throw new IllegalArgumentException("ExtendedAE-Plus encoding menu does not expose preference state");
        }

        return new UploadSnapshot(
                player, preferenceMenu.data_energistics$getPreferenceSession(), rankingContext);
    }

    /**
     * Opens a one-shot scope for a previously captured upload snapshot.
     */
    public static ScopeToken open(UploadSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("ExtendedAE-Plus upload snapshot must not be null");
        }
        if (ACTIVE_SCOPE.get() != null) {
            throw new IllegalStateException("An ExtendedAE-Plus upload scope is already active on this thread");
        }

        Scope scope = new Scope(snapshot);
        ACTIVE_SCOPE.set(scope);
        return new ScopeToken(scope);
    }

    /**
     * Records a provider write after ExtendedAE-Plus has accepted a pattern into that provider inventory.
     */
    public static void recordProviderUpload(ServerPlayer player, PatternContainer container, int slot) {
        if (player == null || container == null || slot < 0) {
            return;
        }

        Scope scope = consume(player);
        if (scope == null) {
            return;
        }

        try {
            record(scope, container);
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to record a committed ExtendedAE-Plus provider upload", exception);
        }
    }

    /**
     * Records a matrix write after resolving the exact matrix block identified by ExtendedAE-Plus.
     */
    public static void recordMatrixUpload(ServerPlayer player, BlockPos position, String dimension,
                                          boolean plus, int slot) {
        if (player == null || position == null || slot < 0) {
            return;
        }

        Scope scope = consume(player);
        if (scope == null) {
            return;
        }

        try {
            Level level = ExtendedAEPatternUploadUtil.findLevel(player, dimension);
            if (level == null) {
                Data_Energistics.LOGGER.warn(
                        "Could not resolve the ExtendedAE-Plus matrix upload dimension {} at {}",
                        dimension, position);
                return;
            }

            BlockEntity blockEntity = level.getBlockEntity(position);
            if (!(blockEntity instanceof TileAssemblerMatrixPattern matrix)) {
                Data_Energistics.LOGGER.warn(
                        "Could not resolve the ExtendedAE-Plus matrix upload target at {} in {}",
                        position, level.dimension().location());
                return;
            }

            boolean resolvedPlus = matrix instanceof PatternCorePlusBlockEntity;
            if (resolvedPlus != plus) {
                Data_Energistics.LOGGER.warn(
                        "ExtendedAE-Plus matrix upload metadata did not match the target at {} in {}: expectedPlus={}, resolvedPlus={}",
                        position, level.dimension().location(), plus, resolvedPlus);
                return;
            }

            record(scope, matrix);
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to record a committed ExtendedAE-Plus matrix upload", exception);
        }
    }

    /**
     * Removes a lingering scope owned by a player that is leaving the server.
     */
    public static void clearForPlayer(ServerPlayer player) {
        Scope scope = ACTIVE_SCOPE.get();
        if (scope != null && scope.player == player) {
            ACTIVE_SCOPE.remove();
        }
    }

    private static Scope consume(ServerPlayer player) {
        Scope scope = ACTIVE_SCOPE.get();
        if (scope == null || scope.player != player || scope.consumed) {
            return null;
        }

        scope.consumed = true;
        return scope;
    }

    private static void record(Scope scope, PatternContainer container) {
        PatternProviderSyncHelper.PatternUploadTarget target = PatternProviderSyncHelper.resolveProviderUploadTarget(container);
        PatternUploadRecorder.record(scope.player, scope.preferenceSession, scope.rankingContext,
                target, PatternUploadSource.EAEP);
    }

    private static final class Scope {

        private final ServerPlayer player;
        private final PatternEncodingPreferenceSession preferenceSession;
        private final PatternEncodingRankingContext rankingContext;
        private boolean consumed;

        private Scope(UploadSnapshot snapshot) {
            this.player = snapshot.player;
            this.preferenceSession = snapshot.preferenceSession;
            this.rankingContext = snapshot.rankingContext;
        }
    }

    /**
     * Immutable handoff data captured before the asynchronous ExtendedAE-Plus upload starts.
     */
    public static final class UploadSnapshot {

        private final ServerPlayer player;
        private final PatternEncodingPreferenceSession preferenceSession;
        private final PatternEncodingRankingContext rankingContext;

        private UploadSnapshot(ServerPlayer player,
                               PatternEncodingPreferenceSession preferenceSession,
                               PatternEncodingRankingContext rankingContext) {
            this.player = player;
            this.preferenceSession = preferenceSession;
            this.rankingContext = rankingContext;
        }
    }

    /**
     * Closes only the scope created by the corresponding {@link #open} call.
     */
    public static final class ScopeToken implements AutoCloseable {

        private final Scope scope;

        private ScopeToken(Scope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            Scope activeScope = ACTIVE_SCOPE.get();
            if (activeScope == null) {
                return;
            }
            if (activeScope != this.scope) {
                throw new IllegalStateException("ExtendedAE-Plus upload scopes were closed out of order");
            }
            ACTIVE_SCOPE.remove();
        }
    }
}
