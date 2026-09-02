package com.fish_dan_.data_energistics.mixin.core.menu.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.EaepPatternEncodingHandoff;
import com.fish_dan_.data_energistics.menu.patternencoding.BlankPatternProxyMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.LegacyPatternEncodingPreferences;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingInheritedState;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferState;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferTarget;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutHelper;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingTransferKeyAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternOutputMatchMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternUploadRecorder;
import com.fish_dan_.data_energistics.menu.patternencoding.source.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderLeafActionTarget;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderMenuOpenHelper;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderSyncHelper;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderSyncTracker;
import com.fish_dan_.data_energistics.mixin.configuration.DataEnergisticsEarlyConfig;
import com.fish_dan_.data_energistics.mixin.configuration.DataEnergisticsEarlyConfig.Option;
import com.fish_dan_.data_energistics.network.patternencoding.MultiblockPatternTransferPayload;
import com.fish_dan_.data_energistics.network.patternencoding.PatternUploadSource;

import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
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
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;

// Apply after EAEP's default-priority TAIL hook so this cancellable encode path bypasses its uploader.
@Mixin(value = PatternEncodingTermMenu.class, priority = 900)
public abstract class PatternEncodingTermMenuMixin extends MEStorageMenu
                                                   implements PatternEncodingPreviewMenu, PatternEncodingSourceAware, PatternEncodingTransferKeyAware,
                                                   PatternEncodingPreviewLayoutAware,
                                                   BlankPatternProxyMenu, PatternEncodingMultiblockTransferTarget,
                                                   PatternEncodingPreferenceMenu, PatternEncodingInheritedState,
                                                   PatternOutputMatchMenu {

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER = "dataEnergistics$transferEncodedPatternToProvider";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_MENU = "dataEnergistics$openPatternProviderMenu";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER = "dataEnergistics$renamePatternProvider";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER_LEAF = "dataEnergistics$transferEncodedPatternToProviderLeaf";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_LEAF_MENU = "dataEnergistics$openPatternProviderLeafMenu";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER_LEAF = "dataEnergistics$renamePatternProviderLeaf";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_PATTERN_SOURCE_ENABLED = "dataEnergistics$setPatternSourceEnabled";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_UPLOAD_ENABLED = "dataEnergistics$setUploadEnabled";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_CLEAR_PATTERN_SOURCE_STATE = "dataEnergistics$clearPatternSourceState";
    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_PROCESSING_OUTPUT_SAME_ITEM = "dataEnergistics$setProcessingOutputSameItem";
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
    private final Reference2LongOpenHashMap<PatternContainer> dataEnergistics$syncedPatternProviderIds = new Reference2LongOpenHashMap<>();
    @Unique
    private final Long2ObjectOpenHashMap<ObjectList<PatternContainer>> dataEnergistics$syncedPatternProvidersById = new Long2ObjectOpenHashMap<>();

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
    public ResourceLocation dataEnergistics$lastEncodedPatternSource;
    @Unique
    @Nullable
    private String dataEnergistics$displayTransferKeyInputSerialized;
    @Unique
    @Nullable
    private String dataEnergistics$displayTransferKeyOutputSerialized;
    @GuiSync(797)
    @Unique
    public boolean dataEnergistics$processingOutputSameItem;
    @GuiSync(798)
    @Unique
    public boolean dataEnergistics$networkBackedBlankPatternSlot = DataEnergisticsEarlyConfig.get().isEnabled(Option.PATTERN_ENCODING_NETWORK_BACKED_BLANK_PATTERN_SLOT);
    @Unique
    @Nullable
    private AEItemKey dataEnergistics$observedEncodedPattern;

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
    public boolean data_energistics$isProcessingOutputSameItem() {
        return this.dataEnergistics$processingOutputSameItem;
    }

    @Override
    public void data_energistics$setProcessingOutputSameItem(boolean enabled) {
        if (enabled && !dataEnergistics$canUseSameItemOutput()) {
            if (this.isServerSide()) {
                Data_Energistics.LOGGER.warn(
                        "Rejected SAME_ITEM processing-output action from {} because output slot zero is not an item",
                        this.getPlayer().getGameProfile().getName());
            }
            this.dataEnergistics$processingOutputSameItem = false;
            return;
        }
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_SET_PROCESSING_OUTPUT_SAME_ITEM, enabled);
        }
        this.dataEnergistics$processingOutputSameItem = enabled;
    }

    @Unique
    private boolean dataEnergistics$canUseSameItemOutput() {
        if (this.mode != EncodingMode.PROCESSING) {
            return false;
        }
        var outputs = ((PatternEncodingTermMenu) (Object) this).getProcessingOutputSlots();
        if (outputs.length == 0) {
            return false;
        }
        GenericStack output = GenericStack.fromItemStack(outputs[0].getItem());
        return output != null && output.what() instanceof AEItemKey;
    }

    @Unique
    @Override
    public SyncedPatternProviderList dataEnergistics$getInheritedSyncedPatternProviders() {
        return this.dataEnergistics$syncedPatternProviders;
    }

    @Unique
    @Override
    public @Nullable ResourceLocation dataEnergistics$getInheritedPendingPatternSource() {
        return this.dataEnergistics$pendingPatternSource;
    }

    @Unique
    @Override
    public void dataEnergistics$setInheritedPendingPatternSource(@Nullable ResourceLocation workstationId) {
        this.dataEnergistics$pendingPatternSource = workstationId;
    }

    @Unique
    @Override
    public @Nullable ResourceLocation dataEnergistics$getInheritedLastEncodedPatternSource() {
        return this.dataEnergistics$lastEncodedPatternSource;
    }

    @Unique
    @Override
    public void dataEnergistics$setInheritedLastEncodedPatternSource(@Nullable ResourceLocation workstationId) {
        this.dataEnergistics$lastEncodedPatternSource = workstationId;
    }

    @Unique
    @Override
    public boolean dataEnergistics$isInheritedPatternSourceEnabled() {
        return this.dataEnergistics$patternSourceEnabled;
    }

    @Unique
    @Override
    public void dataEnergistics$setInheritedPatternSourceEnabled(boolean enabled) {
        this.dataEnergistics$patternSourceEnabled = enabled;
    }

    @Unique
    @Override
    public boolean dataEnergistics$isInheritedUploadEnabled() {
        return this.dataEnergistics$uploadEnabled;
    }

    @Unique
    @Override
    public void dataEnergistics$setInheritedUploadEnabled(boolean enabled) {
        this.dataEnergistics$uploadEnabled = enabled;
    }

    @Override
    public SyncedPatternProviderList data_energistics$getSyncedPatternProviderState() {
        return this.dataEnergistics$syncedPatternProviders;
    }

    @Override
    public void data_energistics$refreshSyncedPatternProviders() {
        if (this.isClientSide()) {
            throw new IllegalStateException("Pattern provider network refresh must run on the server");
        }
        dataEnergistics$syncPatternProvidersFromNetwork();
    }

    @Override
    public EncodingMode data_energistics$getEncodingMode() {
        return this.mode;
    }

    @Override
    public @Nullable AEItemKey data_energistics$getEncodedPatternDefinition() {
        return AEItemKey.of(this.encodedPatternSlot.getItem());
    }

    @Override
    public void data_energistics$requestMultiblockTransfer(MultiblockRecipeView recipe) {
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
        if (!data_energistics$usesNetworkBackedBlankPatternSlot()) {
            return 0;
        }
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
    public boolean data_energistics$usesNetworkBackedBlankPatternSlot() {
        return this.dataEnergistics$networkBackedBlankPatternSlot;
    }

    @Override
    public void data_energistics$depositCarriedBlankPatterns(boolean single) {
        if (!data_energistics$usesNetworkBackedBlankPatternSlot()) {
            return;
        }
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
        if (!data_energistics$usesNetworkBackedBlankPatternSlot()) {
            return;
        }
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

        if (this.canInteractWithGrid()) {
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
            data_energistics$getPreferenceSession().restoreEncodedPattern(this, this.getPlayer().level());
            dataEnergistics$forceSyncPatternProviders();
            PatternEncodingSourceHelper.applyPatternSource(this,
                    PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(this.mode));
            PatternEncodingSourceHelper.applyPendingTransferRecipeMetadata(
                    (PatternEncodingTermMenu) (Object) this);

            ItemStack encodedPattern = this.dataEnergistics$invokeEncodePattern();
            if (this.mode == EncodingMode.PROCESSING && encodedPattern != null &&
                    AEItems.PROCESSING_PATTERN.is(encodedPattern)) {
                encodedPattern = dataEnergistics$encodeProcessingPatternWithGenericStacks();
            }
            if (encodedPattern == null) {
                this.dataEnergistics$invokeClearPattern();
                PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
                PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
                ci.cancel();
                return;
            }

            EncodedPatternDynamicOutput.apply(
                    encodedPattern,
                    this.mode == EncodingMode.PROCESSING && this.dataEnergistics$processingOutputSameItem);
            EncodedPatternRecipeReference.applyProcessingRecipeType(
                    encodedPattern,
                    PatternEncodingSourceHelper.resolveProcessingPatternRecipeType(
                            this,
                            data_energistics$getPreferenceSession(),
                            this));

            ItemStack encodeOutput = this.encodedPatternSlot.getItem();
            if (!encodeOutput.isEmpty() && !PatternDetailsHelper.isEncodedPattern(encodeOutput) && !AEItems.BLANK_PATTERN.is(encodeOutput)) {
                PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
                PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
                ci.cancel();
                return;
            }

            if (encodeOutput.isEmpty() && !dataEnergistics$consumeOneBlankPattern()) {
                PatternEncodingSourceHelper.writePendingTransferKeyInput(this.getPlayer(), null);
                PatternEncodingSourceHelper.writePendingTransferKeyOutput(this.getPlayer(), null);
                ci.cancel();
                return;
            }

            this.encodedPatternSlot.set(encodedPattern);
            encodedSuccessfully = true;
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

        dataEnergistics$syncPatternProvidersFromNetwork();
        var providers = PatternProviderSyncHelper.findProvidersById(
                this.dataEnergistics$syncedPatternProvidersById,
                providerId);
        if (providers == null || providers.isEmpty()) {
            dataEnergistics$sendProviderUnavailable();
            return;
        }
        dataEnergistics$transferEncodedPatternToProviders(providerId, providers);
    }

    @Unique
    private void dataEnergistics$transferEncodedPatternToProviders(
                                                                   long providerId,
                                                                   ObjectList<PatternContainer> providers) {
        ItemStack encodedPattern = this.encodedPatternSlot.getItem();
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
            dataEnergistics$returnEncodedPatternAsBlank();
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
        if (this.getPlayer() instanceof ServerPlayer serverPlayer) {
            PatternUploadRecorder.record(serverPlayer, this, transferResult.committedTarget(),
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

        dataEnergistics$syncPatternProvidersFromNetwork();
        var providers = PatternProviderSyncHelper.findProvidersById(this.dataEnergistics$syncedPatternProvidersById, providerId);
        if (providers == null || providers.size() != 1) {
            return;
        }

        PatternProviderMenuOpenHelper.openProviderGroup(providers, this.getPlayer());
    }

    @Override
    public void data_energistics$renamePatternProvider(long providerId, String name) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER,
                    providerId + "\n" + name);
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
    public void data_energistics$transferEncodedPatternToProviderLeaf(long groupId, long leafId) {
        dataEnergistics$transferEncodedPatternToProviderLeaf(new PatternProviderLeafActionTarget(groupId, leafId));
    }

    @Unique
    private void dataEnergistics$transferEncodedPatternToProviderLeaf(PatternProviderLeafActionTarget target) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER_LEAF, target.encode());
            return;
        }
        if (!this.dataEnergistics$uploadEnabled) {
            return;
        }
        dataEnergistics$syncPatternProvidersFromNetwork();
        PatternContainer provider = dataEnergistics$findProviderLeaf(target);
        if (provider == null) {
            dataEnergistics$sendProviderUnavailable();
            return;
        }
        dataEnergistics$transferEncodedPatternToProviders(target.groupId(), ObjectLists.singleton(provider));
    }

    @Override
    public void data_energistics$openPatternProviderLeafMenu(long groupId, long leafId) {
        dataEnergistics$openPatternProviderLeafMenu(new PatternProviderLeafActionTarget(groupId, leafId));
    }

    @Unique
    private void dataEnergistics$openPatternProviderLeafMenu(PatternProviderLeafActionTarget target) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_LEAF_MENU, target.encode());
            return;
        }
        dataEnergistics$syncPatternProvidersFromNetwork();
        PatternContainer provider = dataEnergistics$findProviderLeaf(target);
        if (provider == null) {
            dataEnergistics$sendProviderUnavailable();
            return;
        }
        if (!PatternProviderMenuOpenHelper.openProviderGroup(ObjectLists.singleton(provider), this.getPlayer())) {
            this.getPlayer().sendSystemMessage(Component.translatable(
                    "message.data_energistics.pattern_provider.leaf_open_unavailable"));
        }
    }

    @Override
    public void data_energistics$renamePatternProviderLeaf(long groupId, long leafId, String name) {
        dataEnergistics$renamePatternProviderLeaf(new PatternProviderLeafActionTarget(groupId, leafId), name);
    }

    @Unique
    private void dataEnergistics$renamePatternProviderLeaf(PatternProviderLeafActionTarget target, String name) {
        if (this.isClientSide()) {
            sendClientAction(DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER_LEAF,
                    target.encodeRename(name));
            return;
        }
        dataEnergistics$syncPatternProvidersFromNetwork();
        PatternContainer provider = dataEnergistics$findProviderLeaf(target);
        if (provider == null) {
            dataEnergistics$sendProviderUnavailable();
            return;
        }
        dataEnergistics$renamePatternProviders(ObjectLists.singleton(provider), name);
    }

    @Unique
    @Nullable
    private PatternContainer dataEnergistics$findProviderLeaf(PatternProviderLeafActionTarget target) {
        return PatternProviderSyncHelper.findProviderLeafById(
                this.dataEnergistics$syncedPatternProviderIds,
                this.dataEnergistics$syncedPatternProvidersById,
                target.groupId(),
                target.leafId());
    }

    @Unique
    private void dataEnergistics$sendProviderUnavailable() {
        this.getPlayer().sendSystemMessage(Component.translatable(
                "message.data_energistics.pattern_provider.leaf_unavailable"));
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
            this.dataEnergistics$pendingPatternSource = workstationId;
        } else {
            this.dataEnergistics$pendingPatternSource = workstationId;
            PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), workstationId);
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
    public void dataEnergistics$sendDataRipperTransferMetadataAction(@Nullable String serializedMetadata) {
        if (this.isClientSide()) {
            sendClientAction(PatternEncodingSourceHelper.ACTION_SET_DATA_RIPPER_TRANSFER_METADATA, serializedMetadata);
        }
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
        registerClientAction(PatternEncodingSourceHelper.ACTION_SET_DATA_RIPPER_TRANSFER_METADATA, String.class,
                serializedMetadata -> PatternEncodingSourceHelper.applyDataRipperTransferMetadataAction(
                        (PatternEncodingTermMenu) (Object) this, serializedMetadata));
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
        registerClientAction(DATA_ENERGISTICS_ACTION_TRANSFER_ENCODED_PATTERN_TO_PROVIDER_LEAF, String.class,
                this::dataEnergistics$transferEncodedPatternToProviderLeafFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_OPEN_PATTERN_PROVIDER_LEAF_MENU, String.class,
                this::dataEnergistics$openPatternProviderLeafMenuFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_RENAME_PATTERN_PROVIDER_LEAF, String.class,
                this::dataEnergistics$renamePatternProviderLeafFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_SET_PATTERN_SOURCE_ENABLED, Boolean.class,
                this::dataEnergistics$setPatternSourceEnabledFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_SET_UPLOAD_ENABLED, Boolean.class,
                this::dataEnergistics$setUploadEnabledFromClient);
        registerClientAction(DATA_ENERGISTICS_ACTION_SET_PROCESSING_OUTPUT_SAME_ITEM, Boolean.class,
                this::data_energistics$setProcessingOutputSameItem);
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
        if (data_energistics$usesNetworkBackedBlankPatternSlot()) {
            this.blankPatternSlot.setHideAmount(true);
        }
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
            data_energistics$getPreferenceSession().initializeConfirmedWorkstation(
                    this.dataEnergistics$lastEncodedPatternSource);
            this.dataEnergistics$previewPanelOffsetX = legacyPreferences.previewPanelOffsetX();
            this.dataEnergistics$previewPanelOffsetY = legacyPreferences.previewPanelOffsetY();
            if (data_energistics$usesNetworkBackedBlankPatternSlot()) {
                dataEnergistics$flushBlankPatternSlotToNetwork();
            }
            dataEnergistics$syncPatternProvidersFromNetwork();
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void dataEnergistics$syncPreviewDataBeforeBroadcast(CallbackInfo ci) {
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.sanitizeActiveDataRipperTransferLayout((PatternEncodingTermMenu) (Object) this);
            if (data_energistics$usesNetworkBackedBlankPatternSlot()) {
                dataEnergistics$flushBlankPatternSlotToNetwork();
            }
            dataEnergistics$refreshProcessingOutputMatchFromEncodedPattern();
        }
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void dataEnergistics$restoreRecipeContextAfterModeSync(CallbackInfo ci) {
        if (this.isServerSide()) {
            data_energistics$getPreferenceSession().restoreEncodedPattern(this, this.getPlayer().level());
            dataEnergistics$syncPatternProvidersIfNeeded();
        }
    }

    @Inject(method = "onSlotChange", at = @At("TAIL"))
    private void dataEnergistics$restoreClientRecipeContext(Slot slot, CallbackInfo ci) {
        if (this.isClientSide() && slot == this.encodedPatternSlot) {
            var session = data_energistics$getPreferenceSession();
            EncodingMode restoredMode = session.restoreEncodedPattern(this, this.getPlayer().level());
            if (restoredMode != null) {
                session.deferSnapshotUntil(restoredMode);
            }
        }
    }

    @Inject(method = "setMode", at = @At("HEAD"))
    private void dataEnergistics$updatePendingPatternSourceOnModeChange(EncodingMode mode,
                                                                        CallbackInfo ci) {
        if (this.isClientSide()) {
            data_energistics$getPreferenceSession().rememberEncodedPattern(this);
            data_energistics$getPreferenceSession().deferSnapshotUntil(mode);
        }
        var fallbackWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode);
        if (mode != EncodingMode.PROCESSING) {
            this.dataEnergistics$processingOutputSameItem = false;
        }
        this.dataEnergistics$pendingPatternSource = fallbackWorkstation;
        data_energistics$getPreferenceSession().setRankingContext(
                PatternEncodingSourceHelper.resolveFixedModeRankingContext(mode, fallbackWorkstation));
        if (this.isServerSide()) {
            PatternEncodingSourceHelper.writePendingPatternSource(this.getPlayer(), fallbackWorkstation);
        }
    }

    @Unique
    private void dataEnergistics$refreshProcessingOutputMatchFromEncodedPattern() {
        AEItemKey definition = AEItemKey.of(this.encodedPatternSlot.getItem());
        if (Objects.equals(definition, this.dataEnergistics$observedEncodedPattern)) {
            return;
        }
        this.dataEnergistics$observedEncodedPattern = definition;
        this.dataEnergistics$processingOutputSameItem = definition != null &&
                EncodedPatternDynamicOutput.isMarked(definition);
    }

    @Unique
    private void dataEnergistics$setPendingPatternSourceFromClient(@Nullable String workstationId) {
        data_energistics$setPendingPatternSource(workstationId == null || workstationId.isEmpty() ? null : ResourceLocation.tryParse(workstationId));
    }

    @Unique
    private void dataEnergistics$transferEncodedPatternToProviderFromClient(@Nullable Long providerId) {
        if (providerId != null) {
            data_energistics$transferEncodedPatternToProvider(providerId);
        }
    }

    @Unique
    private void dataEnergistics$openPatternProviderMenuFromClient(@Nullable Long providerId) {
        if (providerId != null) {
            data_energistics$openPatternProviderMenu(providerId);
        }
    }

    @Unique
    private void dataEnergistics$renamePatternProviderFromClient(@Nullable String payload) {
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
    private void dataEnergistics$transferEncodedPatternToProviderLeafFromClient(@Nullable String payload) {
        PatternProviderLeafActionTarget target = PatternProviderLeafActionTarget.decode(payload);
        if (target != null) {
            dataEnergistics$transferEncodedPatternToProviderLeaf(target);
        }
    }

    @Unique
    private void dataEnergistics$openPatternProviderLeafMenuFromClient(@Nullable String payload) {
        PatternProviderLeafActionTarget target = PatternProviderLeafActionTarget.decode(payload);
        if (target != null) {
            dataEnergistics$openPatternProviderLeafMenu(target);
        }
    }

    @Unique
    private void dataEnergistics$renamePatternProviderLeafFromClient(@Nullable String payload) {
        PatternProviderLeafActionTarget.Rename rename = PatternProviderLeafActionTarget.decodeRename(payload);
        if (rename != null) {
            dataEnergistics$renamePatternProviderLeaf(rename.target(), rename.name());
        }
    }

    @Unique
    private void dataEnergistics$setPatternSourceEnabledFromClient(@Nullable Boolean enabled) {
        if (enabled != null) {
            data_energistics$setPatternSourceEnabled(enabled);
        }
    }

    @Unique
    private void dataEnergistics$setUploadEnabledFromClient(@Nullable Boolean enabled) {
        if (enabled != null) {
            data_energistics$setUploadEnabled(enabled);
        }
    }

    @Unique
    private void dataEnergistics$depositCarriedBlankPatternsFromClient(@Nullable Boolean single) {
        data_energistics$depositCarriedBlankPatterns(Boolean.TRUE.equals(single));
    }

    @Unique
    private void dataEnergistics$pickupBlankPatternsFromClient(@Nullable Boolean single) {
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
        PatternEncodingRankingContext rankingContext = data_energistics$getPreferenceSession().rankingContext();
        long currentTick = this.getPlayer().level().getGameTime();
        long preferenceRevision = data_energistics$getPreferenceSession().revision();
        if (preferenceRevision == this.dataEnergistics$lastPreferenceRevision && !this.dataEnergistics$patternProviderSyncTracker.needsRefresh(
                publication,
                currentTick,
                rankingContext)) {
            return;
        }

        dataEnergistics$syncPatternProvidersFromNetwork(
                grid,
                publication,
                currentTick,
                rankingContext);
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
                data_energistics$getPreferenceSession().rankingContext());
    }

    @Unique
    private void dataEnergistics$syncPatternProvidersFromNetwork(
                                                                 IGrid grid,
                                                                 PatternProviderSyncTracker.PublicationVersion publication,
                                                                 long currentTick,
                                                                 @Nullable PatternEncodingRankingContext rankingContext) {
        this.dataEnergistics$syncedPatternProviders = PatternProviderSyncHelper.collectSyncedPatternProviders(
                grid,
                this.dataEnergistics$syncedPatternProviderIds,
                this.dataEnergistics$syncedPatternProvidersById,
                () -> this.dataEnergistics$nextSyncedPatternProviderId++,
                rankingContext,
                data_energistics$getPreferenceSession().leafCounts());
        this.dataEnergistics$patternProviderSyncTracker.refreshed(
                publication,
                currentTick,
                rankingContext);
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
    @Nullable
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
        if (this.getHost() instanceof IActionHost actionHost) {
            return actionHost.getActionableNode();
        }

        return this.getGridNode();
    }

    @Unique
    private boolean dataEnergistics$consumeOneBlankPattern() {
        if (!data_energistics$usesNetworkBackedBlankPatternSlot()) {
            ItemStack localBlankPattern = this.blankPatternSlot.getItem();
            if (!AEItems.BLANK_PATTERN.is(localBlankPattern) || localBlankPattern.isEmpty()) {
                return false;
            }

            ItemStack reduced = localBlankPattern.copy();
            reduced.shrink(1);
            this.blankPatternSlot.set(reduced.isEmpty() ? ItemStack.EMPTY : reduced);
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
    private void dataEnergistics$returnEncodedPatternAsBlank() {
        ItemStack encodedPattern = this.encodedPatternSlot.getItem();
        if (!PatternDetailsHelper.isEncodedPattern(encodedPattern) || encodedPattern.isEmpty()) {
            return;
        }

        if (!data_energistics$usesNetworkBackedBlankPatternSlot()) {
            this.encodedPatternSlot.set(AEItems.BLANK_PATTERN.stack(encodedPattern.getCount()));
            return;
        }

        if (!this.canInteractWithGrid()) {
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
