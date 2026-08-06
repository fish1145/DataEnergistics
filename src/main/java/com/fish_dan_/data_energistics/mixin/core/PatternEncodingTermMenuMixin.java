package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.integration.extendedaeplus.EaepPatternEncodingHandoff;
import com.fish_dan_.data_energistics.menu.common.BlankPatternProxyMenu;
import com.fish_dan_.data_energistics.menu.common.LegacyPatternEncodingPreferences;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingMultiblockTransferState;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingMultiblockTransferTarget;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingTransferKeyAware;
import com.fish_dan_.data_energistics.menu.common.PatternProviderMenuOpenHelper;
import com.fish_dan_.data_energistics.menu.common.PatternProviderSyncHelper;
import com.fish_dan_.data_energistics.menu.common.PatternProviderSyncTracker;
import com.fish_dan_.data_energistics.menu.common.PatternUploadRecorder;
import com.fish_dan_.data_energistics.network.MultiblockPatternTransferPayload;
import com.fish_dan_.data_energistics.network.PatternUploadSource;
import com.fish_dan_.data_energistics.util.PatternEncodingPreviewLayoutHelper;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Apply after EAEP's default-priority TAIL hook so this cancellable encode path bypasses its uploader.
@Mixin(value = PatternEncodingTermMenu.class, priority = 900)
public abstract class PatternEncodingTermMenuMixin extends MEStorageMenu
                                                   implements PatternEncodingPreviewMenu, PatternEncodingSourceAware, PatternEncodingTransferKeyAware,
                                                   PatternEncodingPreviewLayoutAware,
                                                   BlankPatternProxyMenu, PatternEncodingMultiblockTransferTarget,
                                                   PatternEncodingPreferenceMenu {

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER = "dataEnergistics$transferEncodedPatternToProvider";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_MENU = "dataEnergistics$openPatternProviderMenu";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER = "dataEnergistics$renamePatternProvider";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_PATTERN_SOURCE_ENABLED = "dataEnergistics$setPatternSourceEnabled";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_UPLOAD_ENABLED = "dataEnergistics$setUploadEnabled";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_CLEAR_PATTERN_SOURCE_STATE = "dataEnergistics$clearPatternSourceState";
    @GuiSync(795)
    @Unique
    public int dataEnergistics$previewPanelOffsetX;
    @GuiSync(796)
    @Unique
    public int dataEnergistics$previewPanelOffsetY;
    @GuiSync(794)
    @Unique
    public boolean dataEnergistics$uploadEnabled = true;
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_DEPOSIT_CARRIED_BLANK_PATTERNS = "dataEnergistics$depositCarriedBlankPatterns";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_PICKUP_BLANK_PATTERNS = "dataEnergistics$pickupBlankPatterns";
    @GuiSync(791)
    @Unique
    public SyncedPatternProviderList dataEnergistics$syncedPatternProviders = SyncedPatternProviderList.EMPTY;

    @GuiSync(792)
    @Unique
    public boolean dataEnergistics$patternSourceEnabled = true;

    @Unique
    private final Map<PatternContainer, Long> dataEnergistics$syncedPatternProviderIds = new IdentityHashMap<>();
    @Unique
    private final Map<Long, List<PatternContainer>> dataEnergistics$syncedPatternProvidersById = new HashMap<>();

    @Unique
    private long dataEnergistics$nextSyncedPatternProviderId = 1;

    @Unique
    private final PatternProviderSyncTracker dataEnergistics$patternProviderSyncTracker = new PatternProviderSyncTracker();
    @Unique
    private long dataEnergistics$lastPreferenceRevision = -1L;
    @Unique
    @Nullable
    private ResourceLocation dataEnergistics$pendingPatternSource;
    @GuiSync(793)
    @Unique
    @Nullable
    private ResourceLocation dataEnergistics$lastEncodedPatternSource;
    @Unique
    @Nullable
    private String dataEnergistics$displayTransferKeyInputSerialized;
    @Unique
    @Nullable
    private String dataEnergistics$displayTransferKeyOutputSerialized;

    @Shadow
    @Final
    private RestrictedInputSlot blankPatternSlot;

    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;

    @Shadow
    public EncodingMode mode;

    protected PatternEncodingTermMenuMixin(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host,
                                           boolean bindInventory) {
        super(menuType, id, ip, host, bindInventory);
    }

    @Invoker("encodePattern")
    protected abstract ItemStack dataEnergistics$invokeEncodePattern();

    @Invoker("clearPattern")
    protected abstract void dataEnergistics$invokeClearPattern();

    @Override
    public List<SyncedPatternProvider> data_energistics$getSyncedPatternProviders() {
        return this.dataEnergistics$syncedPatternProviders.providers();
    }

    @Override
    public EncodingMode data_energistics$getEncodingMode() {
        return this.mode;
    }

    @Override
    public void data_energistics$requestMultiblockTransfer(MultiblockRecipeView recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer recipe cannot be null");
        }
        if (!this.isClientSide()) {
            throw new IllegalStateException("Multiblock pattern transfer requests must originate on the client");
        }
        PacketDistributor.sendToServer(new MultiblockPatternTransferPayload(
                this.containerId,
                recipe.registeredRecipeId(),
                recipe.projectionFingerprint()));
    }

    @Override
    public ConfigInventory data_energistics$getMultiblockTransferInputInventory() {
        return dataEnergistics$getMultiblockTransferLogic().getEncodedInputInv();
    }

    @Override
    public ConfigInventory data_energistics$getMultiblockTransferOutputInventory() {
        return dataEnergistics$getMultiblockTransferLogic().getEncodedOutputInv();
    }

    @Override
    public EncodingMode data_energistics$getMultiblockTransferEncodingMode() {
        return dataEnergistics$getMultiblockTransferLogic().getMode();
    }

    @Override
    public void data_energistics$setMultiblockTransferEncodingMode(EncodingMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Multiblock pattern transfer encoding mode cannot be null");
        }
        dataEnergistics$getMultiblockTransferLogic().setMode(mode);
    }

    @Override
    public PatternEncodingMultiblockTransferState data_energistics$snapshotMultiblockTransferState() {
        if (!this.isServerSide()) {
            throw new IllegalStateException("Multiblock transfer state can only be snapshotted on the server");
        }
        return new PatternEncodingMultiblockTransferState(
                data_energistics$getPendingPatternSource(),
                data_energistics$getLastEncodedPatternSource(),
                PatternEncodingSourceHelper.readPendingTransferKeyInput(this.getPlayer()),
                PatternEncodingSourceHelper.readPendingTransferKeyOutput(this.getPlayer()),
                PatternEncodingSourceHelper.readPendingTransferFluidInputs(this.getPlayer()),
                PatternEncodingSourceHelper.readPendingTransferFluidOutputs(this.getPlayer()),
                this.dataEnergistics$displayTransferKeyInputSerialized,
                this.dataEnergistics$displayTransferKeyOutputSerialized);
    }

    @Override
    public void data_energistics$clearMultiblockTransferState() {
        data_energistics$restoreMultiblockTransferState(PatternEncodingMultiblockTransferState.cleared());
    }

    @Override
    public void data_energistics$restoreMultiblockTransferState(PatternEncodingMultiblockTransferState state) {
        if (state == null) {
            throw new IllegalArgumentException("Multiblock transfer state cannot be null");
        }
        if (!this.isServerSide()) {
            throw new IllegalStateException("Multiblock transfer state can only be changed on the server");
        }

        data_energistics$clearPatternSourceState();
        if (state.pendingPatternSource() != null) {
            data_energistics$setPendingPatternSource(state.pendingPatternSource());
        }
        data_energistics$setLastEncodedPatternSource(state.lastEncodedPatternSource());
        PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), state.pendingKeyInput());
        PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), state.pendingKeyOutput());
        PatternEncodingSourceHelper.writePendingTransferFluidInputs(this.getPlayer(), state.pendingFluidInputs());
        PatternEncodingSourceHelper.writePendingTransferFluidOutputs(this.getPlayer(), state.pendingFluidOutputs());
        this.dataEnergistics$displayTransferKeyInputSerialized = state.displayedKeyInputSerialized();
        this.dataEnergistics$displayTransferKeyOutputSerialized = state.displayedKeyOutputSerialized();
    }

    @Override
    public void data_energistics$invalidateMultiblockTransferTarget() {
        this.setValidMenu(false);
    }

    @Override
    public long data_energistics$getNetworkBlankPatternCount() {
        if (!this.canInteractWithGrid()) {
            return 0;
        }

        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        if (blankPatternKey == null) {
            return 0;
        }

        return this.storage.getAvailableStacks().get(blankPatternKey);
    }

    @Override
    public void data_energistics$depositCarriedBlankPatterns(boolean single) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_DEPOSIT_CARRIED_BLANK_PATTERNS, single);
            return;
        }

        ItemStack carried = this.getCarried();
        if (!AEItems.BLANK_PATTERN.is(carried) || carried.isEmpty() || !this.canInteractWithGrid()) {
            return;
        }

        int amountToInsert = single ? 1 : carried.getCount();
        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        if (blankPatternKey == null) {
            return;
        }

        long inserted = StorageHelper.poweredInsert(
                this.energySource,
                this.storage,
                blankPatternKey,
                amountToInsert,
                this.getActionSource(),
                Actionable.MODULATE);
        if (inserted <= 0) {
            return;
        }

        ItemStack updated = carried.copy();
        updated.shrink((int) inserted);
        this.setCarried(updated.isEmpty() ? ItemStack.EMPTY : updated);
    }

    @Override
    public void data_energistics$pickupBlankPatterns(boolean single) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_PICKUP_BLANK_PATTERNS, single);
            return;
        }

        ItemStack carried = this.getCarried();
        if (!carried.isEmpty() && !AEItems.BLANK_PATTERN.is(carried)) {
            return;
        }

        int maxStackSize = AEItems.BLANK_PATTERN.stack().getMaxStackSize();
        int currentCount = carried.isEmpty() ? 0 : carried.getCount();
        int remainingToPickup = single ? 1 : maxStackSize - currentCount;
        if (remainingToPickup <= 0) {
            return;
        }

        ItemStack updated = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();

        if (remainingToPickup > 0 && this.canInteractWithGrid()) {
            var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
            if (blankPatternKey != null) {
                long extracted = StorageHelper.poweredExtraction(
                        this.energySource,
                        this.storage,
                        blankPatternKey,
                        remainingToPickup,
                        this.getActionSource(),
                        Actionable.MODULATE);
                if (extracted > 0) {
                    if (updated.isEmpty()) {
                        updated = AEItems.BLANK_PATTERN.stack((int) extracted);
                    } else {
                        updated.grow((int) extracted);
                    }
                }
            }
        }

        if (!updated.isEmpty()) {
            this.setCarried(updated);
        }
    }

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$encodeUsingNetworkBlankPatterns(CallbackInfo ci) {
        if (this.isClientSide()) {
            return;
        }

        EaepPatternEncodingHandoff handoff = this instanceof EaepPatternEncodingHandoff value ? value : null;
        boolean handoffStarted = false;
        if (handoff != null) {
            try {
                handoff.beginEaepEncodeHandoff(this.dataEnergistics$uploadEnabled);
                handoffStarted = true;
            } catch (RuntimeException | LinkageError exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to begin the ExtendedAE-Plus pattern encoding handoff", exception);
            }
        }
        boolean encodedSuccessfully = false;
        try {
            PatternEncodingSourceHelper.resolveAndApplyDataRipperRecipeKeyInput((PatternEncodingTermMenu) (Object) this);
            PatternEncodingSourceHelper.applyPendingTransferKeyInput((PatternEncodingTermMenu) (Object) this);
            PatternEncodingSourceHelper.applyPendingTransferKeyOutput((PatternEncodingTermMenu) (Object) this);

            ItemStack encodedPattern = this.mode == EncodingMode.PROCESSING ? dataEnergistics$encodeProcessingPatternWithGenericStacks() : this.dataEnergistics$invokeEncodePattern();
            if (encodedPattern == null) {
                this.dataEnergistics$invokeClearPattern();
                PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
                PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
                ci.cancel();
                return;
            }

            ItemStack encodeOutput = this.encodedPatternSlot.getItem();
            if (!encodeOutput.isEmpty() && !PatternDetailsHelper.isEncodedPattern(encodeOutput) && !AEItems.BLANK_PATTERN.is(encodeOutput)) {
                PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
                PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
                ci.cancel();
                return;
            }

            if (encodeOutput.isEmpty() && !dataEnergistics$consumeOneBlankPatternFromNetwork()) {
                PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
                PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
                ci.cancel();
                return;
            }

            if (this instanceof PatternEncodingSourceAware sourceAware) {
                PatternEncodingSourceHelper.applyPatternSource(encodedPattern, sourceAware,
                        PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(this.mode));
            }

            this.encodedPatternSlot.set(encodedPattern);
            encodedSuccessfully = true;
            dataEnergistics$forceSyncPatternProviders();
            PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
            PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
            ci.cancel();
        } finally {
            if (handoffStarted) {
                try {
                    handoff.finishEaepEncodeHandoff(encodedSuccessfully);
                } catch (RuntimeException | LinkageError exception) {
                    Data_Energistics.LOGGER.error(
                            "Failed to finish the ExtendedAE-Plus pattern encoding handoff", exception);
                }
            }
        }
    }

    @Unique
    @Nullable
    private ItemStack dataEnergistics$encodeProcessingPatternWithGenericStacks() {
        if (!(this.getHost() instanceof IPatternTerminalMenuHost host)) {
            return null;
        }

        return PatternEncodingSourceHelper.encodeProcessingPattern(
                host.getLogic().getEncodedInputInv(),
                host.getLogic().getEncodedOutputInv());
    }

    @Override
    public void data_energistics$transferEncodedPatternToProvider(long providerId) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER, providerId);
            return;
        }

        if (!this.dataEnergistics$uploadEnabled) {
            return;
        }

        var providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            dataEnergistics$syncPatternProvidersFromNetwork();
            providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
            if (providers == null || providers.isEmpty()) {
                return;
            }
        }

        ItemStack encodedPattern = this.encodedPatternSlot.getItem();
        var transferResult = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(providers, encodedPattern);
        if (transferResult.duplicateFound()) {
            dataEnergistics$returnEncodedPatternAsBlankToNetwork();
            this.getPlayer().sendSystemMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.duplicate_cleared"));
            dataEnergistics$syncPatternProvidersFromNetwork();
            return;
        }

        ItemStack remainder = transferResult.remainder();
        if (!transferResult.transferred()) {
            return;
        }

        this.encodedPatternSlot.set(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        if (transferResult.firstCommittedTarget() != null && this.getPlayer() instanceof ServerPlayer serverPlayer) {
            PatternUploadRecorder.record(serverPlayer, this, transferResult.firstCommittedTarget(),
                    PatternUploadSource.DATA_ENERGISTICS);
        }
        dataEnergistics$syncPatternProvidersFromNetwork();
    }

    @Override
    public void data_energistics$openPatternProviderMenu(long providerId) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_MENU, providerId);
            return;
        }

        var providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            dataEnergistics$syncPatternProvidersFromNetwork();
            providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
            if (providers == null || providers.isEmpty()) {
                return;
            }
        }

        PatternProviderMenuOpenHelper.openProviderGroup(providers, this.getPlayer());
    }

    @Override
    public void data_energistics$renamePatternProvider(long providerId, String name) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER,
                    providerId + "\n" + (name == null ? "" : name));
            return;
        }

        var providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
        if (providers == null || providers.isEmpty()) {
            dataEnergistics$syncPatternProvidersFromNetwork();
            providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
            if (providers == null || providers.isEmpty()) {
                return;
            }
        }

        dataEnergistics$renamePatternProviders(providers, name);
    }

    @Override
    public void data_energistics$setPendingPatternSource(@Nullable ResourceLocation workstationId) {
        ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(this.mode);
        data_energistics$getPreferenceSession().setRankingContext(
                PatternEncodingSourceHelper.resolveFixedModeRankingContext(this.mode, fixedWorkstation));
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_PATTERN_SOURCE,
                    workstationId != null ? workstationId.toString() : PatternEncodingSourceHelper.CLEAR_PATTERN_SOURCE);
            this.dataEnergistics$pendingPatternSource = workstationId;
        } else {
            this.dataEnergistics$pendingPatternSource = workstationId;
            PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), workstationId);
            if (this.dataEnergistics$patternSourceEnabled) {
                data_energistics$setLastEncodedPatternSource(workstationId);
            }
        }
    }

    @Override
    public @Nullable ResourceLocation data_energistics$getPendingPatternSource() {
        return this.dataEnergistics$pendingPatternSource;
    }

    @Override
    public void data_energistics$clearPendingPatternSource() {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_PATTERN_SOURCE,
                    PatternEncodingSourceHelper.CLEAR_PATTERN_SOURCE);
            this.dataEnergistics$pendingPatternSource = null;
            return;
        }

        this.dataEnergistics$pendingPatternSource = null;
        PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), null);
    }

    @Override
    public void data_energistics$clearPatternSourceState() {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_CLEAR_PATTERN_SOURCE_STATE);
            this.dataEnergistics$pendingPatternSource = null;
            this.dataEnergistics$lastEncodedPatternSource = null;
            return;
        }

        this.dataEnergistics$pendingPatternSource = null;
        this.dataEnergistics$lastEncodedPatternSource = null;
        PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), null);
        PatternEncodingSourceHelper.writeLastEncodedPatternSource(this.getPlayer(), null);
    }

    @Override
    public @Nullable ResourceLocation data_energistics$getLastEncodedPatternSource() {
        return this.dataEnergistics$lastEncodedPatternSource;
    }

    @Override
    public void data_energistics$setLastEncodedPatternSource(@Nullable ResourceLocation workstationId) {
        this.dataEnergistics$lastEncodedPatternSource = workstationId;
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writeLastEncodedPatternSource(this.getPlayer(), workstationId);
        }
    }

    @Override
    public void dataEnergistics$sendTransferKeyInputAction(@Nullable String serializedKeyInput) {
        if (this.isClientSide()) {
            this.dataEnergistics$displayTransferKeyInputSerialized = serializedKeyInput;
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_KEY_INPUT, serializedKeyInput);
        }
    }

    @Override
    public void dataEnergistics$sendTransferKeyOutputAction(@Nullable String serializedKeyOutput) {
        if (this.isClientSide()) {
            this.dataEnergistics$displayTransferKeyOutputSerialized = serializedKeyOutput;
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_KEY_OUTPUT, serializedKeyOutput);
        }
    }

    @Override
    public @Nullable GenericStack dataEnergistics$getDisplayedTransferKeyInput() {
        if (this.dataEnergistics$displayTransferKeyInputSerialized == null || this.dataEnergistics$displayTransferKeyInputSerialized.isEmpty()) {
            return PatternEncodingSourceHelper.readPendingTransferKeyInput(this.getPlayer());
        }
        try {
            return GenericStack.readTag(this.getPlayer().registryAccess(),
                    TagParser.parseTag(this.dataEnergistics$displayTransferKeyInputSerialized));
        } catch (CommandSyntaxException exception) {
            Data_Energistics.LOGGER.warn("Failed to read the displayed pattern transfer input key", exception);
            return PatternEncodingSourceHelper.readPendingTransferKeyInput(this.getPlayer());
        }
    }

    @Override
    public @Nullable GenericStack dataEnergistics$getDisplayedTransferKeyOutput() {
        if (this.dataEnergistics$displayTransferKeyOutputSerialized == null || this.dataEnergistics$displayTransferKeyOutputSerialized.isEmpty()) {
            return PatternEncodingSourceHelper.readPendingTransferKeyOutput(this.getPlayer());
        }
        try {
            return GenericStack.readTag(this.getPlayer().registryAccess(),
                    TagParser.parseTag(this.dataEnergistics$displayTransferKeyOutputSerialized));
        } catch (CommandSyntaxException exception) {
            Data_Energistics.LOGGER.warn("Failed to read the displayed pattern transfer output key", exception);
            return PatternEncodingSourceHelper.readPendingTransferKeyOutput(this.getPlayer());
        }
    }

    @Override
    public void dataEnergistics$setDisplayedTransferKeyInputSerialized(@Nullable String serializedKeyInput) {
        this.dataEnergistics$displayTransferKeyInputSerialized = serializedKeyInput;
    }

    @Override
    public void dataEnergistics$setDisplayedTransferKeyOutputSerialized(@Nullable String serializedKeyOutput) {
        this.dataEnergistics$displayTransferKeyOutputSerialized = serializedKeyOutput;
    }

    @Override
    public @Nullable String dataEnergistics$getDisplayedTransferKeyInputSerialized() {
        return this.dataEnergistics$displayTransferKeyInputSerialized;
    }

    @Override
    public @Nullable String dataEnergistics$getDisplayedTransferKeyOutputSerialized() {
        return this.dataEnergistics$displayTransferKeyOutputSerialized;
    }

    @Override
    public void dataEnergistics$sendTransferFluidInputsAction(@Nullable String serializedFluidInputs) {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_FLUID_INPUTS, serializedFluidInputs);
        }
    }

    @Override
    public void dataEnergistics$sendTransferFluidOutputsAction(@Nullable String serializedFluidOutputs) {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_FLUID_OUTPUTS, serializedFluidOutputs);
        }
    }

    @Override
    public boolean data_energistics$isPatternSourceEnabled() {
        return this.dataEnergistics$patternSourceEnabled;
    }

    @Override
    public void data_energistics$setPatternSourceEnabled(boolean enabled) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_SET_PATTERN_SOURCE_ENABLED, enabled);
        }
        this.dataEnergistics$patternSourceEnabled = enabled;
        if (!enabled) {
            this.dataEnergistics$pendingPatternSource = null;
        }
    }

    @Override
    public boolean data_energistics$isUploadEnabled() {
        return this.dataEnergistics$uploadEnabled;
    }

    @Override
    public void data_energistics$setUploadEnabled(boolean enabled) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_SET_UPLOAD_ENABLED, enabled);
        }
        this.dataEnergistics$uploadEnabled = enabled;
    }

    @Override
    public int data_energistics$getPreviewPanelOffsetX() {
        return this.dataEnergistics$previewPanelOffsetX;
    }

    @Override
    public int data_energistics$getPreviewPanelOffsetY() {
        return this.dataEnergistics$previewPanelOffsetY;
    }

    @Override
    public void data_energistics$setPreviewPanelOffset(int offsetX, int offsetY) {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingPreviewLayoutHelper.ACTION_SET_PREVIEW_PANEL_OFFSET,
                    offsetX + "," + offsetY);
        }
        this.dataEnergistics$previewPanelOffsetX = offsetX;
        this.dataEnergistics$previewPanelOffsetY = offsetY;
    }

    @Override
    public void data_energistics$resetPreviewPanelOffset() {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingPreviewLayoutHelper.ACTION_RESET_PREVIEW_PANEL_OFFSET);
        }
        this.dataEnergistics$previewPanelOffsetX = 0;
        this.dataEnergistics$previewPanelOffsetY = 0;
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
            at = @At("RETURN"))
    private void dataEnergistics$registerPatternSourceAction(MenuType<?> menuType, int id, Inventory ip,
                                                             IPatternTerminalMenuHost host, boolean bindInventory,
                                                             CallbackInfo ci) {
        registerClientAction(PatternEncodingSourceHelper.ACTION_SET_PATTERN_SOURCE, String.class,
                this::dataEnergistics$setPendingPatternSourceFromClient);
        registerClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_KEY_INPUT, String.class,
                serializedKeyInput -> PatternEncodingSourceHelper.applyTransferKeyInputAction(
                        (PatternEncodingTermMenu) (Object) this, serializedKeyInput));
        registerClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_KEY_OUTPUT, String.class,
                serializedKeyOutput -> PatternEncodingSourceHelper.applyTransferKeyOutputAction(
                        (PatternEncodingTermMenu) (Object) this, serializedKeyOutput));
        registerClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_FLUID_INPUTS, String.class,
                serializedFluidInputs -> PatternEncodingSourceHelper.applyTransferFluidInputsAction(
                        (PatternEncodingTermMenu) (Object) this, serializedFluidInputs));
        registerClientAction(PatternEncodingSourceHelper.ACTION_SET_TRANSFER_FLUID_OUTPUTS, String.class,
                serializedFluidOutputs -> PatternEncodingSourceHelper.applyTransferFluidOutputsAction(
                        (PatternEncodingTermMenu) (Object) this, serializedFluidOutputs));
        registerClientAction(DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER, Long.class,
                this::dataEnergistics$transferEncodedPatternToProviderFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_MENU, Long.class,
                this::dataEnergistics$openPatternProviderMenuFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER, String.class,
                this::dataEnergistics$renamePatternProviderFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_SET_PATTERN_SOURCE_ENABLED, Boolean.class,
                this::dataEnergistics$setPatternSourceEnabledFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_SET_UPLOAD_ENABLED, Boolean.class,
                this::dataEnergistics$setUploadEnabledFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_CLEAR_PATTERN_SOURCE_STATE,
                this::data_energistics$clearPatternSourceState);
        registerClientAction(PatternEncodingPreviewLayoutHelper.ACTION_SET_PREVIEW_PANEL_OFFSET, String.class,
                payload -> PatternEncodingPreviewLayoutHelper.applySetOffsetAction(this, payload));
        registerClientAction(PatternEncodingPreviewLayoutHelper.ACTION_RESET_PREVIEW_PANEL_OFFSET,
                this::data_energistics$resetPreviewPanelOffset);
        registerClientAction(DATA_ENERGISTICS_ACTION_DEPOSIT_CARRIED_BLANK_PATTERNS, Boolean.class,
                this::dataEnergistics$depositCarriedBlankPatternsFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_PICKUP_BLANK_PATTERNS, Boolean.class,
                this::dataEnergistics$pickupBlankPatternsFromClient);
        this.blankPatternSlot.setHideAmount(true);
        if (this.isServerSide()) {
            PatternEncodingPreviewLayoutAware legacyLayout = dataEnergistics$getLogicLayout();
            LegacyPatternEncodingPreferences legacyPreferences = LegacyPatternEncodingPreferences.capture(
                    this.getPlayer(),
                    true,
                    true,
                    null,
                    legacyLayout.data_energistics$getPreviewPanelOffsetX(),
                    legacyLayout.data_energistics$getPreviewPanelOffsetY());
            this.dataEnergistics$patternSourceEnabled = legacyPreferences.patternSourceEnabled();
            this.dataEnergistics$uploadEnabled = legacyPreferences.uploadEnabled();
            this.dataEnergistics$pendingPatternSource = PatternEncodingSourceHelper.readPendingPatternSource(this.getPlayer());
            this.dataEnergistics$lastEncodedPatternSource = legacyPreferences.lastWorkstation();
            this.dataEnergistics$previewPanelOffsetX = legacyPreferences.previewPanelOffsetX();
            this.dataEnergistics$previewPanelOffsetY = legacyPreferences.previewPanelOffsetY();
            dataEnergistics$flushBlankPatternSlotToNetwork();
            dataEnergistics$syncPatternProvidersFromNetwork();
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$syncPreviewDataBeforeBroadcast(CallbackInfo ci) {
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.sanitizeActiveDataRipperTransferLayout((PatternEncodingTermMenu) (Object) this);
            dataEnergistics$flushBlankPatternSlotToNetwork();
            dataEnergistics$syncPatternProvidersIfNeeded();
        }
    }

    @Inject(method = "setMode", at = @At("HEAD"))
    private void dataEnergistics$updatePendingPatternSourceOnModeChange(EncodingMode mode,
                                                                        CallbackInfo ci) {
        var fallbackWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode);
        this.dataEnergistics$pendingPatternSource = fallbackWorkstation;
        data_energistics$getPreferenceSession().setRankingContext(
                PatternEncodingSourceHelper.resolveFixedModeRankingContext(mode, fallbackWorkstation));
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), fallbackWorkstation);
        }
    }

    @Unique
    private void dataEnergistics$setPendingPatternSourceFromClient(String workstationId) {
        data_energistics$setPendingPatternSource(workstationId == null || workstationId.isEmpty() ? null : ResourceLocation.tryParse(workstationId));
    }

    @Unique
    private void dataEnergistics$transferEncodedPatternToProviderFromClient(Long providerId) {
        if (providerId != null) {
            data_energistics$transferEncodedPatternToProvider(providerId);
        }
    }

    @Unique
    private void dataEnergistics$openPatternProviderMenuFromClient(Long providerId) {
        if (providerId != null) {
            data_energistics$openPatternProviderMenu(providerId);
        }
    }

    @Unique
    private void dataEnergistics$renamePatternProviderFromClient(String payload) {
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
            Data_Energistics.LOGGER.warn("Rejected malformed pattern provider rename payload: {}", payload,
                    exception);
        }
    }

    @Unique
    private void dataEnergistics$setPatternSourceEnabledFromClient(Boolean enabled) {
        if (enabled != null) {
            data_energistics$setPatternSourceEnabled(enabled);
        }
    }

    @Unique
    private void dataEnergistics$setUploadEnabledFromClient(Boolean enabled) {
        if (enabled != null) {
            data_energistics$setUploadEnabled(enabled);
        }
    }

    @Unique
    private void dataEnergistics$depositCarriedBlankPatternsFromClient(Boolean single) {
        data_energistics$depositCarriedBlankPatterns(Boolean.TRUE.equals(single));
    }

    @Unique
    private void dataEnergistics$pickupBlankPatternsFromClient(Boolean single) {
        data_energistics$pickupBlankPatterns(Boolean.TRUE.equals(single));
    }

    @Unique
    private PatternEncodingPreviewLayoutAware dataEnergistics$getLogicLayout() {
        var logic = PatternEncodingPreviewLayoutHelper.getLogic((PatternEncodingTermMenu) (Object) this);
        if (logic instanceof PatternEncodingPreviewLayoutAware layoutAware) {
            return layoutAware;
        }
        throw new IllegalStateException("Pattern encoding logic does not implement preview layout storage: " + logic.getClass().getName());
    }

    @Unique
    private PatternEncodingLogic dataEnergistics$getMultiblockTransferLogic() {
        if (!(this.getHost() instanceof IPatternTerminalMenuHost host)) {
            throw new IllegalStateException("Pattern encoding menu host does not expose encoding logic");
        }
        PatternEncodingLogic logic = host.getLogic();
        if (logic == null) {
            throw new IllegalStateException("Pattern encoding menu host returned null encoding logic");
        }
        return logic;
    }

    @Unique
    private void dataEnergistics$renamePatternProviders(List<PatternContainer> providers, @Nullable String name) {
        PatternProviderSyncHelper.renamePatternProviders(providers, name);
        dataEnergistics$syncPatternProvidersFromNetwork();
    }

    @Unique
    private void dataEnergistics$syncPatternProvidersIfNeeded() {
        IGrid grid = dataEnergistics$getActiveGrid();
        if (grid == null) {
            dataEnergistics$clearSyncedPatternProviders();
            return;
        }

        var publication = PatternProviderSyncTracker.capturePublicationVersion(grid);
        ResourceLocation preferredWorkstationId = PatternEncodingSourceHelper.resolvePreferredWorkstationId(this);
        ItemStack encodedPattern = this.encodedPatternSlot.getItem();
        long currentTick = this.getPlayer().level().getGameTime();
        long preferenceRevision = data_energistics$getPreferenceSession().revision();
        if (preferenceRevision == this.dataEnergistics$lastPreferenceRevision && !this.dataEnergistics$patternProviderSyncTracker.needsRefresh(
                publication,
                currentTick,
                preferredWorkstationId,
                this.mode,
                encodedPattern)) {
            return;
        }

        dataEnergistics$syncPatternProvidersFromNetwork(
                grid,
                publication,
                currentTick,
                preferredWorkstationId,
                encodedPattern);
    }

    @Unique
    private void dataEnergistics$forceSyncPatternProviders() {
        IGrid grid = dataEnergistics$getActiveGrid();
        if (grid == null) {
            dataEnergistics$clearSyncedPatternProviders();
            return;
        }

        dataEnergistics$syncPatternProvidersFromNetwork(grid);
    }

    @Unique
    private void dataEnergistics$syncPatternProvidersFromNetwork() {
        IGrid grid = dataEnergistics$getActiveGrid();
        if (grid == null) {
            dataEnergistics$clearSyncedPatternProviders();
            return;
        }

        dataEnergistics$syncPatternProvidersFromNetwork(grid);
    }

    @Unique
    private void dataEnergistics$syncPatternProvidersFromNetwork(IGrid grid) {
        dataEnergistics$syncPatternProvidersFromNetwork(
                grid,
                PatternProviderSyncTracker.capturePublicationVersion(grid),
                this.getPlayer().level().getGameTime(),
                PatternEncodingSourceHelper.resolvePreferredWorkstationId(this),
                this.encodedPatternSlot.getItem());
    }

    @Unique
    private void dataEnergistics$syncPatternProvidersFromNetwork(
                                                                 IGrid grid,
                                                                 PatternProviderSyncTracker.PublicationVersion publication,
                                                                 long currentTick,
                                                                 @Nullable ResourceLocation preferredWorkstationId,
                                                                 ItemStack encodedPattern) {
        this.dataEnergistics$syncedPatternProviders = PatternProviderSyncHelper.collectSyncedPatternProviders(
                grid,
                this.dataEnergistics$syncedPatternProviderIds,
                this.dataEnergistics$syncedPatternProvidersById,
                () -> this.dataEnergistics$nextSyncedPatternProviderId++,
                preferredWorkstationId,
                this.mode,
                encodedPattern,
                data_energistics$getPreferenceSession().leafCounts());
        this.dataEnergistics$patternProviderSyncTracker.refreshed(
                publication,
                currentTick,
                preferredWorkstationId,
                this.mode,
                encodedPattern);
        this.dataEnergistics$lastPreferenceRevision = data_energistics$getPreferenceSession().revision();
    }

    @Unique
    private void dataEnergistics$clearSyncedPatternProviders() {
        this.dataEnergistics$syncedPatternProviderIds.clear();
        this.dataEnergistics$syncedPatternProvidersById.clear();
        this.dataEnergistics$syncedPatternProviders = SyncedPatternProviderList.EMPTY;
        this.dataEnergistics$patternProviderSyncTracker.clear();
        this.dataEnergistics$lastPreferenceRevision = -1L;
    }

    @Unique
    private IGrid dataEnergistics$getActiveGrid() {
        IGridNode hostNode = dataEnergistics$tryResolveGridNode();
        if (hostNode != null && hostNode.isActive()) {
            return hostNode.getGrid();
        }
        return null;
    }

    @Unique
    @Nullable
    private IGridNode dataEnergistics$tryResolveGridNode() {
        try {
            IGridNode hostNode = this.getGridNode();
            if (hostNode != null) {
                return hostNode;
            }
        } catch (NullPointerException exception) {
            Data_Energistics.LOGGER.debug(
                    "Pattern terminal grid node is not initialized yet; wireless hosts initialize after the base menu",
                    exception);
        }

        try {
            if (this.getHost() instanceof IActionHost actionHost) {
                return actionHost.getActionableNode();
            }
        } catch (NullPointerException exception) {
            Data_Energistics.LOGGER.debug(
                    "Pattern terminal action host is not initialized yet; provider sync will retry after construction",
                    exception);
        }

        return null;
    }

    @Unique
    private boolean dataEnergistics$consumeOneBlankPatternFromNetwork() {
        if (!this.canInteractWithGrid()) {
            return false;
        }

        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        return blankPatternKey != null && StorageHelper.poweredExtraction(
                this.energySource,
                this.storage,
                blankPatternKey,
                1,
                this.getActionSource(),
                Actionable.MODULATE) > 0;
    }

    @Unique
    private void dataEnergistics$flushBlankPatternSlotToNetwork() {
        ItemStack slotStack = this.blankPatternSlot.getItem();
        if (!AEItems.BLANK_PATTERN.is(slotStack) || slotStack.isEmpty() || !this.canInteractWithGrid()) {
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
                slotStack.getCount(),
                this.getActionSource(),
                Actionable.MODULATE);
        if (inserted <= 0) {
            return;
        }

        ItemStack reduced = slotStack.copy();
        reduced.shrink((int) inserted);
        this.blankPatternSlot.set(reduced.isEmpty() ? ItemStack.EMPTY : reduced);
    }

    @Unique
    private void dataEnergistics$returnEncodedPatternAsBlankToNetwork() {
        ItemStack encodedPattern = this.encodedPatternSlot.getItem();
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
            this.encodedPatternSlot.set(ItemStack.EMPTY);
            return;
        }

        this.encodedPatternSlot.set(AEItems.BLANK_PATTERN.stack(encodedPattern.getCount() - (int) inserted));
    }
}
