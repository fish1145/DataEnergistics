package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.extendedaeplus.EaepPatternEncodingHandoff;
import com.fish_dan_.data_energistics.menu.common.LegacyPatternEncodingPreferences;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingInheritedState;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.common.PatternProviderMenuOpenHelper;
import com.fish_dan_.data_energistics.menu.common.PatternProviderSyncHelper;
import com.fish_dan_.data_energistics.menu.common.PatternProviderSyncTracker;
import com.fish_dan_.data_energistics.menu.common.PatternUploadRecorder;
import com.fish_dan_.data_energistics.network.PatternUploadSource;
import com.fish_dan_.data_energistics.network.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.util.PatternEncodingPreviewLayoutHelper;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class UniversalPatternEncodingTermMenu extends PatternEncodingTermMenu
                                              implements UniversalTerminalMenuBridge, PatternEncodingPreviewMenu, PatternEncodingSourceAware,
                                              PatternEncodingPreviewLayoutAware, PatternEncodingPreferenceMenu {

    private static final String ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER = "transferEncodedPatternToProvider";
    private static final String ACTION_OPEN_PATTERN_PROVIDER_MENU = "openPatternProviderMenu";
    private static final String ACTION_RENAME_PATTERN_PROVIDER = "renamePatternProvider";
    private static final String ACTION_CLEAR_PATTERN_SOURCE_STATE = "dataEnergistics$clearPatternSourceState";
    private static final String ACTION_SET_PATTERN_SOURCE_ENABLED = "dataEnergistics$setPatternSourceEnabled";
    private static final String ACTION_SET_UPLOAD_ENABLED = "dataEnergistics$setUploadEnabled";
    private static final int CRAFTING_GRID_WIDTH = 3;
    private static final int CRAFTING_GRID_HEIGHT = 3;
    private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;

    private final UniversalTerminalPart host;
    @GuiSync(890)
    public int availableTerminalMask;
    @GuiSync(891)
    public int activeTerminalIndex = -1;
    @GuiSync(892)
    public long networkBlankPatternCount;
    @GuiSync(893)
    public SyncedPatternProviderList syncedPatternProviders = SyncedPatternProviderList.EMPTY;
    @GuiSync(894)
    public boolean patternSourceEnabled = true;
    @GuiSync(895)
    @Nullable
    public ResourceLocation lastEncodedPatternSource;
    @GuiSync(896)
    public boolean uploadEnabled = true;
    @GuiSync(897)
    public int previewPanelOffsetX;
    @GuiSync(898)
    public int previewPanelOffsetY;

    private final Map<PatternContainer, Long> syncedPatternProviderIds = new IdentityHashMap<>();
    private final Map<Long, List<PatternContainer>> syncedPatternProvidersById = new HashMap<>();
    private final PatternProviderSyncTracker patternProviderSyncTracker = new PatternProviderSyncTracker();
    private long lastPreferenceRevision = -1L;
    private long nextSyncedPatternProviderId = 1;

    public UniversalPatternEncodingTermMenu(int id, Inventory playerInventory, UniversalTerminalPart host) {
        this(ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(), id, playerInventory, host, true);
    }

    public UniversalPatternEncodingTermMenu(MenuType<?> menuType, int id, Inventory playerInventory,
                                            UniversalTerminalPart host, boolean bindInventory) {
        super(menuType, id, playerInventory, host, bindInventory);
        this.host = host;
        registerClientAction(ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER, Long.class,
                this::transferEncodedPatternToProviderFromClient);
        registerClientAction(ACTION_OPEN_PATTERN_PROVIDER_MENU, Long.class,
                this::openPatternProviderMenuFromClient);
        registerClientAction(ACTION_RENAME_PATTERN_PROVIDER, String.class,
                this::renamePatternProviderFromClient);
        if (this.isServerSide()) {
            LegacyPatternEncodingPreferences legacyPreferences = LegacyPatternEncodingPreferences.capture(
                    this.getPlayer(),
                    true,
                    this.host.isPersistentPatternSourceEnabled(),
                    this.host.getPersistentLastEncodedPatternSource(),
                    this.host.getPersistentPreviewPanelOffsetX(),
                    this.host.getPersistentPreviewPanelOffsetY());
            this.patternSourceEnabled = legacyPreferences.patternSourceEnabled();
            this.uploadEnabled = legacyPreferences.uploadEnabled();
            this.lastEncodedPatternSource = legacyPreferences.lastWorkstation();
            data_energistics$getPreferenceSession().initializeConfirmedWorkstation(
                    this.lastEncodedPatternSource);
            this.previewPanelOffsetX = legacyPreferences.previewPanelOffsetX();
            this.previewPanelOffsetY = legacyPreferences.previewPanelOffsetY();
        }
        writeFallbackPatternSourceEnabled(this.patternSourceEnabled);
        writeFallbackUploadEnabled(this.uploadEnabled);
        writeFallbackPendingPatternSource(PatternEncodingSourceHelper.readPendingPatternSource(this.getPlayer()));
        writeFallbackLastEncodedPatternSource(this.lastEncodedPatternSource);
        syncTerminalState();
        syncBlankPatternCountFromNetwork();
        syncPatternProvidersIfNeeded(true);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            syncTerminalState();
            syncBlankPatternCountFromNetwork();
            syncPatternProvidersIfNeeded(false);
        }
        super.broadcastChanges();
    }

    @Override
    public void encode() {
        if (isClientSide()) {
            sendClientAction("encode");
            return;
        }

        EaepPatternEncodingHandoff handoff = this instanceof EaepPatternEncodingHandoff value ? value : null;
        boolean handoffStarted = false;
        if (handoff != null) {
            try {
                handoff.beginEaepEncodeHandoff(this.uploadEnabled);
                handoffStarted = true;
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to begin the ExtendedAE-Plus Universal pattern encoding handoff", exception);
            }
        }
        boolean encodedSuccessfully = false;
        try {
            syncPatternProvidersIfNeeded(true);
            PatternEncodingSourceHelper.applyPatternSource(this,
                    PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(this.mode));
            PatternEncodingSourceHelper.applyPendingTransferRecipeMetadata(this);
            ItemStack encodedPattern = encodePatternVirtual();
            if (encodedPattern == null) {
                clearEncodedPatternSlot();
                return;
            }

            var encodedPatternInv = this.host.getLogic().getEncodedPatternInv();
            ItemStack encodeOutput = encodedPatternInv.getStackInSlot(0);
            if (!encodeOutput.isEmpty() && !PatternDetailsHelper.isEncodedPattern(encodeOutput) && !AEItems.BLANK_PATTERN.is(encodeOutput)) {
                return;
            }

            if (encodeOutput.isEmpty() && !consumeOneBlankPattern()) {
                return;
            }

            encodedPatternInv.setItemDirect(0, encodedPattern);
            encodedSuccessfully = true;
        } finally {
            if (handoffStarted) {
                try {
                    handoff.finishEaepEncodeHandoff(encodedSuccessfully);
                } catch (RuntimeException | LinkageError exception) {
                    Data_Energistics.LOGGER.error(
                            "Failed to finish the ExtendedAE-Plus Universal pattern encoding handoff", exception);
                }
            }
        }
    }

    @Override
    public int getAvailableTerminalMask() {
        return this.availableTerminalMask;
    }

    @Override
    public int getActiveTerminalIndex() {
        return this.activeTerminalIndex;
    }

    @Override
    public UniversalTerminalPart getUniversalTerminalHost() {
        return this.host;
    }

    @Override
    public long data_energistics$getNetworkBlankPatternCount() {
        return this.networkBlankPatternCount;
    }

    @Override
    public @NotNull SyncedPatternProviderList data_energistics$getSyncedPatternProviderState() {
        if (!this.syncedPatternProviders.providers().isEmpty() ||
                this.syncedPatternProviders.rankingContext() != null) {
            return this.syncedPatternProviders;
        }

        return readFallbackSyncedPatternProviders();
    }

    @Override
    public void data_energistics$refreshSyncedPatternProviders() {
        if (this.isClientSide()) {
            throw new IllegalStateException("Pattern provider network refresh must run on the server");
        }
        syncPatternProvidersFromNetwork();
    }

    @Override
    public @NotNull EncodingMode data_energistics$getEncodingMode() {
        return this.getMode();
    }

    @Override
    public void data_energistics$transferEncodedPatternToProvider(long providerId) {
        if (this.isClientSide()) {
            sendClientAction(ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER, providerId);
            return;
        }

        if (!data_energistics$isUploadEnabled()) {
            return;
        }

        syncPatternProvidersFromNetwork();
        var providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            this.getPlayer().sendSystemMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.target_unavailable"));
            return;
        }

        var encodedPatternInv = this.host.getLogic().getEncodedPatternInv();
        ItemStack encodedPattern = encodedPatternInv.getStackInSlot(0);
        var uploadContext = PatternProviderSyncHelper.createPatternUploadContext(
                this,
                data_energistics$getPreferenceSession(),
                providerId);
        var transferResult = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(
                providers,
                encodedPattern,
                uploadContext);
        if (transferResult.rejected()) {
            this.getPlayer().sendSystemMessage(Component.translatable(
                    transferResult.rejection().messageKeyOrThrow()));
            return;
        }
        if (transferResult.duplicateFound()) {
            returnEncodedPatternAsBlankToNetwork();
            this.getPlayer().sendSystemMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.duplicate_cleared"));
            syncPatternProvidersFromNetwork();
            return;
        }

        ItemStack remainder = transferResult.remainder();
        if (!transferResult.transferred()) {
            return;
        }

        encodedPatternInv.setItemDirect(0, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        if (this.getPlayer() instanceof ServerPlayer serverPlayer) {
            PatternUploadRecorder.record(serverPlayer, this, transferResult.committedTarget(),
                    PatternUploadSource.DATA_ENERGISTICS);
        }
        syncPatternProvidersFromNetwork();
    }

    @Override
    public void data_energistics$openPatternProviderMenu(long providerId) {
        if (this.isClientSide()) {
            sendClientAction(ACTION_OPEN_PATTERN_PROVIDER_MENU, providerId);
            return;
        }

        var providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            syncPatternProvidersFromNetwork();
            providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
            if (providers == null || providers.isEmpty()) {
                return;
            }
        }

        PatternProviderMenuOpenHelper.openProviderGroup(providers, this.getPlayer());
    }

    @Override
    public void data_energistics$renamePatternProvider(long providerId, String name) {
        if (this.isClientSide()) {
            sendClientAction(ACTION_RENAME_PATTERN_PROVIDER, providerId + "\n" + (name == null ? "" : name));
            return;
        }

        var providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            syncPatternProvidersFromNetwork();
            providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
            if (providers == null || providers.isEmpty()) {
                return;
            }
        }

        renamePatternProviders(providers, name);
    }

    @Override
    public void sendCycleTerminal(boolean reverse) {
        PacketDistributor.sendToServer(new UniversalTerminalCyclePayload(reverse));
    }

    @Override
    public void data_energistics$setPendingPatternSource(@Nullable ResourceLocation workstationId) {
        ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(this.mode);
        if (fixedWorkstation != null) {
            data_energistics$getPreferenceSession().setRankingContext(
                    PatternEncodingSourceHelper.resolveFixedModeRankingContext(this.mode, fixedWorkstation));
        }
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_PATTERN_SOURCE,
                    workstationId != null ? workstationId.toString() : PatternEncodingSourceHelper.CLEAR_PATTERN_SOURCE);
            writeFallbackPendingPatternSource(workstationId);
        } else {
            writeFallbackPendingPatternSource(workstationId);
            PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), workstationId);
        }
    }

    @Override
    public @Nullable ResourceLocation data_energistics$getPendingPatternSource() {
        ResourceLocation fallback = readFallbackPendingPatternSource();
        return fallback != null ? fallback : PatternEncodingSourceHelper.readPendingPatternSource(this.getPlayer());
    }

    @Override
    public void data_energistics$clearPendingPatternSource() {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_PATTERN_SOURCE,
                    PatternEncodingSourceHelper.CLEAR_PATTERN_SOURCE);
            writeFallbackPendingPatternSource(null);
            return;
        }

        writeFallbackPendingPatternSource(null);
        PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), null);
    }

    @Override
    public void data_energistics$clearPatternSourceState() {
        if (this.isClientSide()) {
            sendClientAction(ACTION_CLEAR_PATTERN_SOURCE_STATE);
            writeFallbackPendingPatternSource(null);
            this.lastEncodedPatternSource = null;
            writeFallbackLastEncodedPatternSource(null);
            return;
        }

        writeFallbackPendingPatternSource(null);
        this.lastEncodedPatternSource = null;
        writeFallbackLastEncodedPatternSource(null);
        PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), null);
        PatternEncodingSourceHelper.writeLastEncodedPatternSource(this.getPlayer(), null);
    }

    @Override
    public @Nullable ResourceLocation data_energistics$getLastEncodedPatternSource() {
        if (this.lastEncodedPatternSource != null) {
            return this.lastEncodedPatternSource;
        }

        ResourceLocation fallback = readFallbackLastEncodedPatternSource();
        return fallback != null ? fallback : PatternEncodingSourceHelper.readLastEncodedPatternSource(this.getPlayer());
    }

    @Override
    public void data_energistics$setLastEncodedPatternSource(@Nullable ResourceLocation workstationId) {
        this.lastEncodedPatternSource = workstationId;
        writeFallbackLastEncodedPatternSource(workstationId);
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writeLastEncodedPatternSource(this.getPlayer(), workstationId);
        }
    }

    @Override
    public boolean data_energistics$isPatternSourceEnabled() {
        return readFallbackPatternSourceEnabled();
    }

    @Override
    public void data_energistics$setPatternSourceEnabled(boolean enabled) {
        if (this.isClientSide()) {
            sendClientAction(ACTION_SET_PATTERN_SOURCE_ENABLED, enabled);
        }
        this.patternSourceEnabled = enabled;
        writeFallbackPatternSourceEnabled(enabled);
        if (!enabled) {
            writeFallbackPendingPatternSource(null);
        }
    }

    @Override
    public boolean data_energistics$isUploadEnabled() {
        return readFallbackUploadEnabled();
    }

    @Override
    public void data_energistics$setUploadEnabled(boolean enabled) {
        if (this.isClientSide()) {
            sendClientAction(ACTION_SET_UPLOAD_ENABLED, enabled);
        }
        this.uploadEnabled = enabled;
        writeFallbackUploadEnabled(enabled);
    }

    @Override
    public int data_energistics$getPreviewPanelOffsetX() {
        return this.previewPanelOffsetX;
    }

    @Override
    public int data_energistics$getPreviewPanelOffsetY() {
        return this.previewPanelOffsetY;
    }

    @Override
    public void data_energistics$setPreviewPanelOffset(int offsetX, int offsetY) {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingPreviewLayoutHelper.ACTION_SET_PREVIEW_PANEL_OFFSET,
                    offsetX + "," + offsetY);
        }
        this.previewPanelOffsetX = offsetX;
        this.previewPanelOffsetY = offsetY;
        if (this.isServerSide()) {
            this.host.setSessionPreviewPanelOffset(offsetX, offsetY);
        }
    }

    @Override
    public void data_energistics$resetPreviewPanelOffset() {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingPreviewLayoutHelper.ACTION_RESET_PREVIEW_PANEL_OFFSET);
        }
        this.previewPanelOffsetX = 0;
        this.previewPanelOffsetY = 0;
        if (this.isServerSide()) {
            this.host.resetSessionPreviewPanelOffset();
        }
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        if (this.getSlotSemantic(slot) == SlotSemantics.BLANK_PATTERN) {
            return false;
        }
        return super.isValidForSlot(slot, stack);
    }

    private void syncTerminalState() {
        this.availableTerminalMask = this.host.getInstalledTerminalMask();
        this.activeTerminalIndex = this.host.getActiveTerminalIndex();
    }

    private void syncBlankPatternCountFromNetwork() {
        this.networkBlankPatternCount = 0;
        if (getActiveGrid() == null) {
            return;
        }

        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        if (blankPatternKey == null) {
            return;
        }

        this.networkBlankPatternCount = this.storage.getAvailableStacks().get(blankPatternKey);
    }

    private void syncPatternProvidersFromNetwork() {
        IGrid grid = getActiveGrid();
        if (grid == null) {
            clearSyncedPatternProviders();
            return;
        }

        syncPatternProvidersFromNetwork(grid);
    }

    private void syncPatternProvidersFromNetwork(IGrid grid) {
        syncPatternProvidersFromNetwork(
                grid,
                PatternProviderSyncTracker.capturePublicationVersion(grid),
                this.getPlayer().level().getGameTime(),
                data_energistics$getPreferenceSession().rankingContext());
    }

    private void syncPatternProvidersFromNetwork(
                                                 IGrid grid,
                                                 PatternProviderSyncTracker.PublicationVersion publication,
                                                 long currentTick,
                                                 @Nullable PatternEncodingRankingContext rankingContext) {
        this.syncedPatternProviders = PatternProviderSyncHelper.collectSyncedPatternProviders(
                grid,
                data_energistics$getEncodingMode(),
                this.syncedPatternProviderIds,
                this.syncedPatternProvidersById,
                () -> this.nextSyncedPatternProviderId++,
                rankingContext,
                data_energistics$getPreferenceSession().leafCounts());
        this.patternProviderSyncTracker.refreshed(
                publication,
                currentTick,
                rankingContext);
        this.lastPreferenceRevision = data_energistics$getPreferenceSession().revision();
    }

    private void syncPatternProvidersIfNeeded(boolean force) {
        IGrid grid = getActiveGrid();
        if (grid == null) {
            clearSyncedPatternProviders();
            return;
        }

        var publication = PatternProviderSyncTracker.capturePublicationVersion(grid);
        PatternEncodingRankingContext rankingContext = data_energistics$getPreferenceSession().rankingContext();
        long currentTick = this.getPlayer().level().getGameTime();
        long preferenceRevision = data_energistics$getPreferenceSession().revision();
        if (!force && preferenceRevision == this.lastPreferenceRevision && !this.patternProviderSyncTracker.needsRefresh(
                publication,
                currentTick,
                rankingContext)) {
            return;
        }

        syncPatternProvidersFromNetwork(
                grid,
                publication,
                currentTick,
                rankingContext);
    }

    private void clearSyncedPatternProviders() {
        this.syncedPatternProviderIds.clear();
        this.syncedPatternProvidersById.clear();
        this.syncedPatternProviders = SyncedPatternProviderList.EMPTY;
        this.patternProviderSyncTracker.clear();
        this.lastPreferenceRevision = -1L;
    }

    @Nullable
    private IGrid getActiveGrid() {
        IGridNode hostNode = this.getGridNode();
        if (hostNode == null && this.getHost() instanceof IActionHost actionHost) {
            hostNode = actionHost.getActionableNode();
        }
        if (hostNode != null && hostNode.isActive()) {
            return hostNode.getGrid();
        }
        return null;
    }

    private boolean consumeOneBlankPattern() {
        var blankPatternInv = this.host.getLogic().getBlankPatternInv();
        ItemStack localBlankPattern = blankPatternInv.getStackInSlot(0);
        if (AEItems.BLANK_PATTERN.is(localBlankPattern) && localBlankPattern.getCount() > 0) {
            ItemStack reduced = localBlankPattern.copy();
            reduced.shrink(1);
            blankPatternInv.setItemDirect(0, reduced.isEmpty() ? ItemStack.EMPTY : reduced);
            return true;
        }

        if (!this.canInteractWithGrid()) {
            return false;
        }

        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        return blankPatternKey != null && StorageHelper.poweredExtraction(
                this.energySource,
                this.storage,
                blankPatternKey,
                1,
                this.getActionSource()) > 0;
    }

    private void transferEncodedPatternToProviderFromClient(Long providerId) {
        if (providerId != null) {
            data_energistics$transferEncodedPatternToProvider(providerId);
        }
    }

    private void openPatternProviderMenuFromClient(Long providerId) {
        if (providerId != null) {
            data_energistics$openPatternProviderMenu(providerId);
        }
    }

    private void renamePatternProviderFromClient(String payload) {
        if (payload == null) {
            return;
        }
        int separator = payload.indexOf('\n');
        if (separator < 0) {
            return;
        }

        try {
            long providerId = Long.parseLong(payload.substring(0, separator));
            String name = payload.substring(separator + 1);
            data_energistics$renamePatternProvider(providerId, name);
        } catch (NumberFormatException exception) {
            Data_Energistics.LOGGER.warn("Rejected malformed Universal pattern provider rename payload: {}",
                    payload, exception);
        }
    }

    private void renamePatternProviders(List<PatternContainer> providers, @Nullable String name) {
        PatternProviderSyncHelper.renamePatternProviders(providers, name);
        syncPatternProvidersFromNetwork();
    }

    private PatternEncodingInheritedState inheritedState() {
        return (PatternEncodingInheritedState) this;
    }

    private SyncedPatternProviderList readFallbackSyncedPatternProviders() {
        return inheritedState().dataEnergistics$getInheritedSyncedPatternProviders();
    }

    @Nullable
    private ResourceLocation readFallbackPendingPatternSource() {
        return inheritedState().dataEnergistics$getInheritedPendingPatternSource();
    }

    private void writeFallbackPendingPatternSource(@Nullable ResourceLocation workstationId) {
        inheritedState().dataEnergistics$setInheritedPendingPatternSource(workstationId);
    }

    @Nullable
    private ResourceLocation readFallbackLastEncodedPatternSource() {
        return inheritedState().dataEnergistics$getInheritedLastEncodedPatternSource();
    }

    private void writeFallbackLastEncodedPatternSource(@Nullable ResourceLocation workstationId) {
        inheritedState().dataEnergistics$setInheritedLastEncodedPatternSource(workstationId);
    }

    private boolean readFallbackPatternSourceEnabled() {
        return inheritedState().dataEnergistics$isInheritedPatternSourceEnabled();
    }

    private void writeFallbackPatternSourceEnabled(boolean enabled) {
        inheritedState().dataEnergistics$setInheritedPatternSourceEnabled(enabled);
    }

    private boolean readFallbackUploadEnabled() {
        return inheritedState().dataEnergistics$isInheritedUploadEnabled();
    }

    private void writeFallbackUploadEnabled(boolean enabled) {
        inheritedState().dataEnergistics$setInheritedUploadEnabled(enabled);
    }

    private void clearEncodedPatternSlot() {
        var encodedPatternInv = this.host.getLogic().getEncodedPatternInv();
        ItemStack encodedPattern = encodedPatternInv.getStackInSlot(0);
        if (PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            encodedPatternInv.setItemDirect(0, AEItems.BLANK_PATTERN.stack(encodedPattern.getCount()));
        }
    }

    private void returnEncodedPatternAsBlankToNetwork() {
        var encodedPatternInv = this.host.getLogic().getEncodedPatternInv();
        ItemStack encodedPattern = encodedPatternInv.getStackInSlot(0);
        if (!PatternDetailsHelper.isEncodedPattern(encodedPattern) || encodedPattern.isEmpty() || !this.canInteractWithGrid()) {
            return;
        }

        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        if (blankPatternKey == null) {
            return;
        }

        long inserted = StorageHelper.poweredInsert(
                this.energySource,
                this.storage,
                blankPatternKey,
                encodedPattern.getCount(),
                this.getActionSource(),
                Actionable.MODULATE);
        if (inserted <= 0) {
            return;
        }

        if (inserted >= encodedPattern.getCount()) {
            encodedPatternInv.setItemDirect(0, ItemStack.EMPTY);
            return;
        }

        encodedPatternInv.setItemDirect(0, AEItems.BLANK_PATTERN.stack(encodedPattern.getCount() - (int) inserted));
    }

    @Nullable
    private ItemStack encodePatternVirtual() {
        return switch (this.mode) {
            case CRAFTING -> encodeCraftingPatternVirtual();
            case PROCESSING -> encodeProcessingPatternVirtual();
            case SMITHING_TABLE -> encodeSmithingTablePatternVirtual();
            case STONECUTTING -> encodeStonecuttingPatternVirtual();
        };
    }

    @Nullable
    private ItemStack encodeCraftingPatternVirtual() {
        var ingredients = new ItemStack[CRAFTING_GRID_SLOTS];
        boolean valid = false;
        for (int x = 0; x < ingredients.length; x++) {
            ingredients[x] = getEncodedCraftingIngredient(x);
            if (ingredients[x] == null) {
                return null;
            } else if (!ingredients[x].isEmpty()) {
                valid = true;
            }
        }
        if (!valid) {
            return null;
        }

        var recipe = resolveCurrentCraftingRecipe(ingredients);
        if (recipe == null) {
            return null;
        }

        var level = this.getPlayer().level();
        var items = NonNullList.withSize(CRAFTING_GRID_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < ingredients.length; i++) {
            items.set(i, ingredients[i]);
        }
        var input = CraftingInput.of(CRAFTING_GRID_WIDTH, CRAFTING_GRID_HEIGHT, items);
        var result = recipe.value().assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        return PatternDetailsHelper.encodeCraftingPattern(recipe, ingredients, result, this.isSubstitute(),
                this.isSubstituteFluids());
    }

    @Nullable
    private RecipeHolder<CraftingRecipe> resolveCurrentCraftingRecipe(ItemStack[] ingredients) {
        var level = this.getPlayer().level();
        var items = NonNullList.withSize(CRAFTING_GRID_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < ingredients.length; i++) {
            items.set(i, ingredients[i] == null ? ItemStack.EMPTY : ingredients[i]);
        }
        var input = CraftingInput.of(CRAFTING_GRID_WIDTH, CRAFTING_GRID_HEIGHT, items);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
    }

    @Nullable
    private ItemStack encodeProcessingPatternVirtual() {
        return PatternEncodingSourceHelper.encodeProcessingPattern(
                this.host.getLogic().getEncodedInputInv(),
                this.host.getLogic().getEncodedOutputInv());
    }

    @Nullable
    private ItemStack encodeSmithingTablePatternVirtual() {
        PatternEncodingLogic logic = this.host.getLogic();
        ConfigInventory encodedInputsInv = logic.getEncodedInputInv();

        if (!(encodedInputsInv.getKey(0) instanceof AEItemKey template) || !(encodedInputsInv.getKey(1) instanceof AEItemKey base) || !(encodedInputsInv.getKey(2) instanceof AEItemKey addition)) {
            return null;
        }

        var input = new SmithingRecipeInput(template.toStack(), base.toStack(), addition.toStack());
        var level = this.getPlayer().level();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level).orElse(null);
        if (recipe == null) {
            return null;
        }

        var output = AEItemKey.of(recipe.value().assemble(input, level.registryAccess()));
        if (output == null) {
            return null;
        }
        return PatternDetailsHelper.encodeSmithingTablePattern(recipe, template, base, addition, output,
                logic.isSubstitution());
    }

    @Nullable
    private ItemStack encodeStonecuttingPatternVirtual() {
        if (this.stonecuttingRecipeId == null) {
            return null;
        }

        ConfigInventory encodedInputsInv = this.host.getLogic().getEncodedInputInv();
        if (!(encodedInputsInv.getKey(0) instanceof AEItemKey input)) {
            return null;
        }

        var recipeInput = new SingleRecipeInput(input.toStack());
        var level = this.getPlayer().level();
        var recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.STONECUTTING, recipeInput, level, this.stonecuttingRecipeId)
                .orElse(null);
        if (recipe == null) {
            return null;
        }

        var output = AEItemKey.of(recipe.value().getResultItem(level.registryAccess()));
        if (output == null) {
            return null;
        }
        return PatternDetailsHelper.encodeStonecuttingPattern(recipe, input, output,
                this.host.getLogic().isSubstitution());
    }

    @Nullable
    private ItemStack getEncodedCraftingIngredient(int slot) {
        var what = this.host.getLogic().getEncodedInputInv().getKey(slot);
        if (what == null) {
            return ItemStack.EMPTY;
        } else if (what instanceof AEItemKey itemKey) {
            return itemKey.toStack(1);
        } else {
            return null;
        }
    }
}
