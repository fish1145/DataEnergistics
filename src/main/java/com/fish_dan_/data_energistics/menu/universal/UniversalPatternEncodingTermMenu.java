package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.common.PatternProviderMenuOpenHelper;
import com.fish_dan_.data_energistics.menu.common.PatternProviderSyncHelper;
import com.fish_dan_.data_energistics.network.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.util.PatternEncodingPreviewLayoutHelper;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class UniversalPatternEncodingTermMenu extends PatternEncodingTermMenu
                                              implements UniversalTerminalMenuBridge, PatternEncodingPreviewMenu, PatternEncodingSourceAware,
                                              PatternEncodingPreviewLayoutAware {

    private static final String ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER = "transferEncodedPatternToProvider";
    private static final String ACTION_OPEN_PATTERN_PROVIDER_MENU = "openPatternProviderMenu";
    private static final String ACTION_RENAME_PATTERN_PROVIDER = "renamePatternProvider";
    private static final String ACTION_CLEAR_PATTERN_SOURCE_STATE = "dataEnergistics$clearPatternSourceState";
    @Nullable
    private static final VarHandle FALLBACK_NETWORK_BLANK_PATTERN_COUNT_FIELD = resolveInheritedField("dataEnergistics$networkBlankPatternCount");
    @Nullable
    private static final VarHandle FALLBACK_SYNCED_PATTERN_PROVIDERS_FIELD = resolveInheritedField("dataEnergistics$syncedPatternProviders");
    @Nullable
    private static final VarHandle FALLBACK_PENDING_PATTERN_SOURCE_FIELD = resolveInheritedField("dataEnergistics$pendingPatternSource");
    @Nullable
    private static final VarHandle FALLBACK_LAST_ENCODED_PATTERN_SOURCE_FIELD = resolveInheritedField("dataEnergistics$lastEncodedPatternSource");
    @Nullable
    private static final VarHandle FALLBACK_PATTERN_SOURCE_ENABLED_FIELD = resolveInheritedField("dataEnergistics$patternSourceEnabled");
    @Nullable
    private static final VarHandle FALLBACK_UPLOAD_ENABLED_FIELD = resolveInheritedField("dataEnergistics$uploadEnabled");
    private static final String ACTION_SET_PATTERN_SOURCE_ENABLED = "dataEnergistics$setPatternSourceEnabled";
    private static final String ACTION_SET_UPLOAD_ENABLED = "dataEnergistics$setUploadEnabled";
    private static final int CRAFTING_GRID_WIDTH = 3;
    private static final int CRAFTING_GRID_HEIGHT = 3;
    private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;
    private static final int PATTERN_PROVIDER_SYNC_INTERVAL_TICKS = 5;

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
    private long nextSyncedPatternProviderId = 1;
    private int lastPatternProviderSyncTick = Integer.MIN_VALUE;

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
        registerClientAction(PatternEncodingPreviewLayoutHelper.ACTION_SET_PREVIEW_PANEL_OFFSET, String.class,
                payload -> PatternEncodingPreviewLayoutHelper.applySetOffsetAction(this, payload));
        registerClientAction(PatternEncodingPreviewLayoutHelper.ACTION_RESET_PREVIEW_PANEL_OFFSET,
                this::data_energistics$resetPreviewPanelOffset);
        this.patternSourceEnabled = PatternEncodingSourceHelper.readPatternSourceEnabled(this.getPlayer());
        this.uploadEnabled = PatternEncodingSourceHelper.readUploadEnabled(this.getPlayer());
        this.lastEncodedPatternSource = PatternEncodingSourceHelper.readLastEncodedPatternSource(this.getPlayer());
        this.previewPanelOffsetX = this.host.getPersistentPreviewPanelOffsetX();
        this.previewPanelOffsetY = this.host.getPersistentPreviewPanelOffsetY();
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writeLastEncodedPatternSource(this.getPlayer(), this.lastEncodedPatternSource);
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

        if (this instanceof PatternEncodingSourceAware sourceAware) {
            PatternEncodingSourceHelper.applyPatternSource(encodedPattern, sourceAware,
                    PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(this.mode));
        }
        encodedPatternInv.setItemDirect(0, encodedPattern);
        syncPatternProvidersIfNeeded(true);
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
        if (this.networkBlankPatternCount > 0) {
            return this.networkBlankPatternCount;
        }

        Long fallbackCount = readFallbackNetworkBlankPatternCount();
        return fallbackCount != null ? fallbackCount : this.networkBlankPatternCount;
    }

    @Override
    public List<SyncedPatternProvider> data_energistics$getSyncedPatternProviders() {
        if (!this.syncedPatternProviders.providers().isEmpty()) {
            return this.syncedPatternProviders.providers();
        }

        SyncedPatternProviderList fallbackProviders = readFallbackSyncedPatternProviders();
        return fallbackProviders != null ? fallbackProviders.providers() : this.syncedPatternProviders.providers();
    }

    @Override
    public EncodingMode data_energistics$getEncodingMode() {
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

        var providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            syncPatternProvidersFromNetwork();
            providers = PatternProviderSyncHelper.findProvidersById(this.syncedPatternProvidersById, providerId);
            if (providers == null || providers.isEmpty()) {
                return;
            }
        }

        var encodedPatternInv = this.host.getLogic().getEncodedPatternInv();
        ItemStack encodedPattern = encodedPatternInv.getStackInSlot(0);
        var transferResult = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(providers, encodedPattern);
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
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_PATTERN_SOURCE,
                    workstationId != null ? workstationId.toString() : PatternEncodingSourceHelper.CLEAR_PATTERN_SOURCE);
            writeFallbackPendingPatternSource(workstationId);
        } else {
            writeFallbackPendingPatternSource(workstationId);
            PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), workstationId);
            if (data_energistics$isPatternSourceEnabled()) {
                data_energistics$setLastEncodedPatternSource(workstationId);
            }
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
        Boolean fallback = readFallbackPatternSourceEnabled();
        return fallback != null ? fallback : PatternEncodingSourceHelper.readPatternSourceEnabled(this.getPlayer());
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
            this.lastEncodedPatternSource = null;
            writeFallbackLastEncodedPatternSource(null);
        }
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writePatternSourceEnabled(this.getPlayer(), enabled);
        }
    }

    @Override
    public boolean data_energistics$isUploadEnabled() {
        Boolean fallback = readFallbackUploadEnabled();
        return fallback != null ? fallback : PatternEncodingSourceHelper.readUploadEnabled(this.getPlayer());
    }

    @Override
    public void data_energistics$setUploadEnabled(boolean enabled) {
        if (this.isClientSide()) {
            sendClientAction(ACTION_SET_UPLOAD_ENABLED, enabled);
        }
        this.uploadEnabled = enabled;
        writeFallbackUploadEnabled(enabled);
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writeUploadEnabled(this.getPlayer(), enabled);
        }
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
            this.host.setPersistentPreviewPanelOffset(offsetX, offsetY);
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
            this.host.resetPersistentPreviewPanelOffset();
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
        var grid = getActiveGrid();
        if (grid == null) {
            clearSyncedPatternProviders();
            return;
        }

        this.syncedPatternProviders = PatternProviderSyncHelper.collectSyncedPatternProviders(
                grid,
                this.syncedPatternProviderIds,
                this.syncedPatternProvidersById,
                () -> this.nextSyncedPatternProviderId++,
                PatternEncodingSourceHelper.resolvePreferredWorkstationId(this),
                this.getMode(),
                this.host.getLogic().getEncodedPatternInv().getStackInSlot(0));
    }

    private void syncPatternProvidersIfNeeded(boolean force) {
        if (getActiveGrid() == null) {
            clearSyncedPatternProviders();
            this.lastPatternProviderSyncTick = Integer.MIN_VALUE;
            return;
        }

        int currentTick = this.getPlayer().tickCount;
        if (!force && currentTick - this.lastPatternProviderSyncTick < PATTERN_PROVIDER_SYNC_INTERVAL_TICKS) {
            return;
        }

        syncPatternProvidersFromNetwork();
        this.lastPatternProviderSyncTick = currentTick;
    }

    private void clearSyncedPatternProviders() {
        this.syncedPatternProviderIds.clear();
        this.syncedPatternProvidersById.clear();
        this.syncedPatternProviders = SyncedPatternProviderList.EMPTY;
        this.lastPatternProviderSyncTick = Integer.MIN_VALUE;
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
        } catch (NumberFormatException ignored) {}
    }

    private void renamePatternProviders(List<PatternContainer> providers, @Nullable String name) {
        PatternProviderSyncHelper.renamePatternProviders(providers, name);
        syncPatternProvidersFromNetwork();
    }

    @Nullable
    private static VarHandle resolveInheritedField(String fieldName) {
        return ReflectionAccess.findField(PatternEncodingTermMenu.class, fieldName).orElse(null);
    }

    @Nullable
    private static Object readInheritedField(@Nullable VarHandle field, Object target) {
        if (field == null) {
            return null;
        }

        try {
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeInheritedField(@Nullable VarHandle field, Object target, @Nullable Object value) {
        if (field == null) {
            return;
        }

        try {
            field.set(target, value);
        } catch (Throwable ignored) {}
    }

    @Nullable
    private Long readFallbackNetworkBlankPatternCount() {
        Object value = readInheritedField(FALLBACK_NETWORK_BLANK_PATTERN_COUNT_FIELD, this);
        return value instanceof Long count ? count : null;
    }

    @Nullable
    private SyncedPatternProviderList readFallbackSyncedPatternProviders() {
        Object value = readInheritedField(FALLBACK_SYNCED_PATTERN_PROVIDERS_FIELD, this);
        return value instanceof SyncedPatternProviderList providers ? providers : null;
    }

    @Nullable
    private ResourceLocation readFallbackPendingPatternSource() {
        Object value = readInheritedField(FALLBACK_PENDING_PATTERN_SOURCE_FIELD, this);
        return value instanceof ResourceLocation id ? id : null;
    }

    private void writeFallbackPendingPatternSource(@Nullable ResourceLocation workstationId) {
        writeInheritedField(FALLBACK_PENDING_PATTERN_SOURCE_FIELD, this, workstationId);
    }

    @Nullable
    private ResourceLocation readFallbackLastEncodedPatternSource() {
        Object value = readInheritedField(FALLBACK_LAST_ENCODED_PATTERN_SOURCE_FIELD, this);
        return value instanceof ResourceLocation id ? id : null;
    }

    private void writeFallbackLastEncodedPatternSource(@Nullable ResourceLocation workstationId) {
        writeInheritedField(FALLBACK_LAST_ENCODED_PATTERN_SOURCE_FIELD, this, workstationId);
    }

    @Nullable
    private Boolean readFallbackPatternSourceEnabled() {
        Object value = readInheritedField(FALLBACK_PATTERN_SOURCE_ENABLED_FIELD, this);
        return value instanceof Boolean enabled ? enabled : null;
    }

    private void writeFallbackPatternSourceEnabled(boolean enabled) {
        writeInheritedField(FALLBACK_PATTERN_SOURCE_ENABLED_FIELD, this, enabled);
    }

    @Nullable
    private Boolean readFallbackUploadEnabled() {
        Object value = readInheritedField(FALLBACK_UPLOAD_ENABLED_FIELD, this);
        return value instanceof Boolean enabled ? enabled : null;
    }

    private void writeFallbackUploadEnabled(boolean enabled) {
        writeInheritedField(FALLBACK_UPLOAD_ENABLED_FIELD, this, enabled);
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
