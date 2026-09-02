package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistration;
import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalData;
import com.fish_dan_.data_energistics.item.terminal.UniversalTerminalItemData;
import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuLocator;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalStateSyncPayload;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IPatternAccessTermMenuHost;
import appeng.api.storage.ISubMenuHost;
import appeng.api.util.AEColor;
import appeng.api.util.IConfigManager;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.items.parts.PartModels;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class UniversalTerminalPart extends AbstractTerminalPart implements IPatternTerminalLogicHost, IPatternTerminalMenuHost, IPatternAccessTermMenuHost, ISubMenuHost {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String TAG_ACTIVE_TERMINAL = "universal_terminal_active";
    private static final String TAG_CRAFTING_GRID = "universal_terminal_crafting_grid";
    private static final String TAG_TERMINAL_DATA = "universal_terminal_data";
    private static final String TAG_INSTALLED_TERMINALS = "installed_terminals";
    private static final String TAG_TERMINAL_NAME = "name";
    private static final String TAG_APPLIEDE_SHIFT_TO_TRANSMUTE = "appliede_shift_to_transmute";
    private static final String TAG_PATTERN_SOURCE_PENDING = "pattern_source_pending";
    @PartModels
    public static final ResourceLocation MODEL_OFF = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/universal_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "part/universal_terminal_on");
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, 9);
    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private final Object2ObjectMap<String, IConfigManager> adapterConfigManagers = new Object2ObjectOpenHashMap<>();
    private final ObjectSet<String> missingAdapterConfigManagers = new ObjectOpenHashSet<>();
    private String activeTerminal = UniversalTerminalData.TERMINAL_ITEM;
    private CompoundTag terminalData = new CompoundTag();

    public UniversalTerminalPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (player.isShiftKeyDown()) {
            if (!this.isClientSide()) {
                switchToNextTerminal(player, true);
            }
            return true;
        }

        if (InteractionUtil.canWrenchRotate(player.getInventory().getSelected())) {
            return super.onUseWithoutItem(player, pos);
        }

        if (usesCustomMenuLocator()) {
            if (!player.level().isClientSide) {
                openActiveTerminal(player, false);
            }
        } else if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            openActiveTerminal(player, false);
        }

        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        return super.onUseItemOn(heldItem, player, hand, pos);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);

        for (ItemStack stack : this.craftingGrid) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }

        for (ItemStack stack : this.logic.getBlankPatternInv()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }

        for (ItemStack stack : this.logic.getEncodedPatternInv()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.craftingGrid.clear();
        this.logic.getBlankPatternInv().clear();
        this.logic.getEncodedPatternInv().clear();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.craftingGrid.readFromNBT(data, TAG_CRAFTING_GRID, registries);
        this.logic.readFromNBT(data, registries);
        this.terminalData = data.contains(TAG_TERMINAL_DATA, CompoundTag.TAG_COMPOUND) ? data.getCompound(TAG_TERMINAL_DATA).copy() : new CompoundTag();
        readAdapterConfigManagers(data, registries);
        setActiveTerminal(data.getString(TAG_ACTIVE_TERMINAL));
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.craftingGrid.writeToNBT(data, TAG_CRAFTING_GRID, registries);
        this.logic.writeToNBT(data, registries);
        writeAdapterConfigManagers(data, registries);
        data.putString(TAG_ACTIVE_TERMINAL, this.activeTerminal);
        if (!this.terminalData.isEmpty()) {
            data.put(TAG_TERMINAL_DATA, this.terminalData.copy());
        }
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeUtf(this.activeTerminal);
        ByteBufCodecs.COMPOUND_TAG.encode(data, this.terminalData.copy());
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        String incomingTerminal = data.readUtf();
        CompoundTag incomingTerminalData = ByteBufCodecs.COMPOUND_TAG.decode(data);
        if (!incomingTerminal.equals(this.activeTerminal)) {
            setActiveTerminal(incomingTerminal);
            changed = true;
        }
        if (!incomingTerminalData.equals(this.terminalData)) {
            this.terminalData = incomingTerminalData.copy();
            changed = true;
        }
        return changed;
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return UniversalTerminalData.getMenuType(resolveActiveTerminalName());
    }

    @Override
    public IConfigManager getConfigManager() {
        IConfigManager adapterConfigManager = getAdapterConfigManager(this.activeTerminal);
        return adapterConfigManager != null ? adapterConfigManager : super.getConfigManager();
    }

    @Override
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        return id.equals(CraftingTerminalPart.INV_CRAFTING) ? this.craftingGrid : super.getSubInventory(id);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return this.logic;
    }

    @Override
    public void markForSave() {
        this.getHost().markForSave();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        ItemStack icon = UniversalTerminalData.getMenuIcon(resolveActiveTerminalName());
        return icon.isEmpty() ? new ItemStack(this.getPartItem().asItem()) : icon;
    }

    public boolean switchToNextTerminal(@Nullable Player player, boolean announceChange) {
        return cycleTerminal(player, false, announceChange);
    }

    public boolean cycleTerminal(@Nullable Player player, boolean reverse, boolean announceChange) {
        if (this.getLevel() == null) {
            LOGGER.debug("UniversalTerminalPart.cycleTerminal aborted: level is null");
            return false;
        }

        List<String> terminals = getInstalledTerminalNames(this.getLevel().registryAccess());
        if (terminals.isEmpty()) {
            LOGGER.debug("UniversalTerminalPart.cycleTerminal aborted: no installed terminals");
            return false;
        }

        String resolvedActive = resolveActiveTerminalName();
        int currentIndex = terminals.indexOf(resolvedActive);
        int offset = reverse ? -1 : 1;
        String nextTerminal = terminals.get((currentIndex + offset + terminals.size()) % terminals.size());
        LOGGER.debug("UniversalTerminalPart.cycleTerminal reverse={} current={} next={} installed={}",
                reverse, resolvedActive, nextTerminal, terminals);
        return switchToTerminal(nextTerminal, player, announceChange);
    }

    public boolean switchToTerminal(String terminalName, @Nullable Player player, boolean announceChange) {
        if (this.getLevel() == null || terminalName.isEmpty()) {
            LOGGER.debug("UniversalTerminalPart.switchToTerminal aborted: invalid input terminal={}", terminalName);
            return false;
        }

        List<String> terminals = getInstalledTerminalNames(this.getLevel().registryAccess());
        if (!terminals.contains(terminalName)) {
            LOGGER.debug("UniversalTerminalPart.switchToTerminal aborted: terminal {} not in {}", terminalName, terminals);
            return false;
        }

        if (terminalName.equals(resolveActiveTerminalName())) {
            LOGGER.debug("UniversalTerminalPart.switchToTerminal aborted: terminal {} already active", terminalName);
            return false;
        }

        setActiveTerminal(terminalName);
        this.saveChanges();
        this.getHost().markForUpdate();
        LOGGER.debug("UniversalTerminalPart.switchToTerminal success: activeTerminal={}", this.activeTerminal);

        if (announceChange && player != null) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.universal_terminal.mode",
                            UniversalTerminalData.getTerminalDisplayName(this.activeTerminal)),
                    true);
        }

        return true;
    }

    public void openActiveTerminal(Player player) {
        openActiveTerminal(player, false);
    }

    public void openActiveTerminal(Player player, boolean returningFromSubmenu) {
        String menuTerminal = resolveActiveTerminalName();
        if (menuTerminal != null) {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new UniversalTerminalStateSyncPayload(
                        this.getInstalledTerminalNames(),
                        menuTerminal));
            }
            LOGGER.debug("UniversalTerminalPart.openActiveTerminal opening {} menuType={} returningFromSubmenu={}",
                    menuTerminal, UniversalTerminalData.getMenuType(menuTerminal), returningFromSubmenu);
            MenuOpener.open(
                    UniversalTerminalData.getMenuType(menuTerminal),
                    player,
                    usesCustomMenuLocator(menuTerminal) ? UniversalTerminalMenuLocator.forPart(this, menuTerminal) : MenuLocators.forPart(this),
                    returningFromSubmenu);
        } else {
            LOGGER.debug("UniversalTerminalPart.openActiveTerminal aborted: resolved terminal is null");
        }
    }

    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (!player.level().isClientSide) {
            openActiveTerminal(player, true);
        }
    }

    public boolean getShiftToTransmute() {
        return this.terminalData.getBoolean(TAG_APPLIEDE_SHIFT_TO_TRANSMUTE);
    }

    public void setShiftToTransmute(boolean shiftToTransmute) {
        this.terminalData.putBoolean(TAG_APPLIEDE_SHIFT_TO_TRANSMUTE, shiftToTransmute);
        this.saveChanges();
        if (this.getHost() != null) {
            this.getHost().markForUpdate();
        }
    }

    public @Nullable ResourceLocation getPersistentPendingPatternSource() {
        String value = this.terminalData.getString(TAG_PATTERN_SOURCE_PENDING);
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    public void setPersistentPendingPatternSource(@Nullable ResourceLocation workstationId) {
        if (workstationId == null) {
            this.terminalData.remove(TAG_PATTERN_SOURCE_PENDING);
        } else {
            this.terminalData.putString(TAG_PATTERN_SOURCE_PENDING, workstationId.toString());
        }
        persistTerminalDataChange();
    }

    @Override
    protected AEColor getColor() {
        return super.getColor();
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            UniversalTerminalItemData data = input.get(DEDataComponents.UNIVERSAL_TERMINAL.get());
            HolderLookup.Provider registries = player != null ? player.level().registryAccess() : this.getLevel() != null ? this.getLevel().registryAccess() : null;
            if (data != null && registries != null) {
                CompoundTag tag = data.toPartTag(registries);
                this.terminalData = tag.copy();
                readAdapterConfigManagers(tag, registries);
                String importedActive = data.activeTerminal();
                if (!importedActive.isEmpty()) {
                    setActiveTerminal(importedActive);
                } else if (this.getLevel() != null) {
                    String fallbackActive = resolveActiveTerminalName();
                    if (fallbackActive != null) {
                        setActiveTerminal(fallbackActive);
                    }
                }
                this.saveChanges();
                if (this.getHost() != null) {
                    this.getHost().markForUpdate();
                }
            }
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            ItemStack stack = new ItemStack(this.getPartItem().asItem());
            HolderLookup.Provider registries = this.getLevel() == null ? null : this.getLevel().registryAccess();
            if (!this.terminalData.isEmpty() || this.getLevel() != null) {
                CompoundTag tag = this.terminalData.copy();
                if (registries != null) {
                    writeAdapterConfigManagers(tag, registries);
                }
                List<UniversalTerminalItemData.TerminalEntryData> entries = registries == null ? List.of() : UniversalTerminalItemData.fromPartTag(tag, registries).terminals();
                stack.set(DEDataComponents.UNIVERSAL_TERMINAL.get(), new UniversalTerminalItemData(this.activeTerminal, entries, tag));
            }
            UniversalTerminalItemData data = stack.get(DEDataComponents.UNIVERSAL_TERMINAL.get());
            if (data != null) {
                builder.set(DEDataComponents.UNIVERSAL_TERMINAL.get(), data);
            }
        }
    }

    public int getInstalledTerminalMask() {
        if (this.getLevel() == null) {
            return 0;
        }

        int mask = 0;
        for (String terminalName : getInstalledTerminalNames(this.getLevel().registryAccess())) {
            int index = UniversalTerminalData.getTerminalIndex(terminalName);
            if (index >= 0) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    public int getActiveTerminalIndex() {
        return UniversalTerminalData.getTerminalIndex(resolveActiveTerminalName());
    }

    public List<String> getInstalledTerminalNames() {
        if (this.getLevel() == null) {
            return List.of();
        }

        return getInstalledTerminalNames(this.getLevel().registryAccess());
    }

    public List<UniversalTerminalData.TerminalEntry> getInstalledTerminalEntries() {
        if (this.getLevel() == null) {
            return List.of();
        }

        return getInstalledTerminalEntries(this.getLevel().registryAccess());
    }

    public @Nullable String getActiveTerminalName() {
        return this.getLevel() == null ? this.activeTerminal : resolveActiveTerminalName();
    }

    private List<String> getInstalledTerminalNames(HolderLookup.Provider registries) {
        ItemStack stack = buildDataStack();
        List<String> installed = UniversalTerminalData.getInstalledTerminalNames(stack, registries);
        return !installed.isEmpty() ? installed : getRawInstalledTerminalNames();
    }

    private List<UniversalTerminalData.TerminalEntry> getInstalledTerminalEntries(HolderLookup.Provider registries) {
        ItemStack stack = buildDataStack();
        List<UniversalTerminalData.TerminalEntry> entries = UniversalTerminalData.readEntries(stack, registries);
        if (!entries.isEmpty()) {
            return entries;
        }

        return getRawInstalledTerminalNames().stream()
                .map(name -> new UniversalTerminalData.TerminalEntry(name, UniversalTerminalData.getMenuIcon(name)))
                .toList();
    }

    private @Nullable String resolveActiveTerminalName() {
        List<String> installed = getInstalledTerminalNames(this.getLevel().registryAccess());
        if (installed.isEmpty()) {
            return UniversalTerminalData.getTerminalIndex(this.activeTerminal) >= 0 ? this.activeTerminal : null;
        }

        if (installed.contains(this.activeTerminal)) {
            return this.activeTerminal;
        }

        setActiveTerminal(installed.getFirst());
        return this.activeTerminal;
    }

    private List<String> getRawInstalledTerminalNames() {
        if (!this.terminalData.contains(TAG_INSTALLED_TERMINALS, CompoundTag.TAG_LIST)) {
            return List.of();
        }

        List<String> installed = new ObjectArrayList<>();
        var terminalList = this.terminalData.getList(TAG_INSTALLED_TERMINALS, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < terminalList.size(); i++) {
            String name = terminalList.getCompound(i).getString(TAG_TERMINAL_NAME);
            if (!name.isEmpty() && UniversalTerminalData.getTerminalIndex(name) >= 0 && !installed.contains(name)) {
                installed.add(name);
            }
        }
        return List.copyOf(installed);
    }

    private ItemStack buildDataStack() {
        ItemStack stack = new ItemStack(this.getPartItem().asItem());
        if (!this.terminalData.isEmpty() || this.getLevel() != null) {
            List<UniversalTerminalItemData.TerminalEntryData> entries = this.getLevel() == null ? List.of() : UniversalTerminalItemData.fromPartTag(this.terminalData, this.getLevel().registryAccess()).terminals();
            stack.set(DEDataComponents.UNIVERSAL_TERMINAL.get(), new UniversalTerminalItemData(this.activeTerminal, entries, this.terminalData.copy()));
        }
        return stack;
    }

    private void setActiveTerminal(String terminalName) {
        this.activeTerminal = terminalName.isEmpty() ? UniversalTerminalData.TERMINAL_ITEM : terminalName;
        this.terminalData.putString("active_terminal", this.activeTerminal);
    }

    private void persistTerminalDataChange() {
        this.saveChanges();
        if (this.getHost() != null) {
            this.getHost().markForUpdate();
        }
    }

    private boolean usesCustomMenuLocator() {
        return usesCustomMenuLocator(resolveActiveTerminalName());
    }

    private boolean usesCustomMenuLocator(@Nullable String terminalName) {
        if (terminalName == null) {
            return false;
        }

        return UniversalTerminalData.getDefinitions().stream()
                .filter(definition -> definition.name().equals(terminalName))
                .findFirst()
                .map(UniversalTerminalRegistration::requiresCustomMenuLocator)
                .orElse(false);
    }

    private void readAdapterConfigManagers(CompoundTag data, HolderLookup.Provider registries) {
        for (var definition : UniversalTerminalData.getDefinitions()) {
            IConfigManager configManager = getAdapterConfigManager(definition.name());
            if (configManager != null) {
                configManager.readFromNBT(data, registries);
            }
        }
    }

    private void writeAdapterConfigManagers(CompoundTag data, HolderLookup.Provider registries) {
        for (var definition : UniversalTerminalData.getDefinitions()) {
            IConfigManager configManager = getAdapterConfigManager(definition.name());
            if (configManager != null) {
                configManager.writeToNBT(data, registries);
            }
        }
    }

    private @Nullable IConfigManager getAdapterConfigManager(@Nullable String terminalName) {
        if (terminalName == null || this.missingAdapterConfigManagers.contains(terminalName)) {
            return null;
        }

        IConfigManager existing = this.adapterConfigManagers.get(terminalName);
        if (existing != null) {
            return existing;
        }

        IConfigManager created = UniversalTerminalData.createConfigManager(terminalName, this::saveChanges);
        if (created == null) {
            this.missingAdapterConfigManagers.add(terminalName);
            return null;
        }

        this.adapterConfigManagers.put(terminalName, created);
        return created;
    }
}
