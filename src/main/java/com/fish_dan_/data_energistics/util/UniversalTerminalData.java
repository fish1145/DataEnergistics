package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import appeng.api.util.IConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UniversalTerminalData {

    private static final String TAG_TERMINALS = "installed_terminals";
    private static final String TAG_NAME = "name";
    private static final String TAG_STACK = "stack";
    private static final String TAG_ACTIVE = "active_terminal";

    public static final String TERMINAL_ITEM = "terminal";
    public static final String TERMINAL_CRAFTING = "crafting";
    public static final String TERMINAL_PATTERN_ACCESS = "pattern_access";
    public static final String TERMINAL_PATTERN_ENCODING = "pattern_encoding";

    private static final List<UniversalTerminalAdapter> TERMINAL_DEFINITIONS = new ArrayList<>();

    private UniversalTerminalData() {}

    public static List<UniversalTerminalAdapter> getDefinitions() {
        return List.copyOf(TERMINAL_DEFINITIONS);
    }

    public static void registerAdapter(UniversalTerminalAdapter adapter) {
        if (TERMINAL_DEFINITIONS.stream().anyMatch(existing -> existing.name().equals(adapter.name()))) {
            throw new IllegalArgumentException("Duplicate universal terminal adapter: " + adapter.name());
        }
        TERMINAL_DEFINITIONS.add(adapter);
    }

    public static ItemStack createCombinedTerminal(ItemStack resultTemplate, HolderLookup.Provider registries,
                                                   ItemStack firstTerminal, ItemStack secondTerminal) {
        ItemStack result = resultTemplate.copyWithCount(1);
        List<TerminalEntry> entries = mergeEntries(
                List.of(),
                List.of(terminalEntryFromStack(firstTerminal), terminalEntryFromStack(secondTerminal)));
        if (entries.size() < 2) {
            return ItemStack.EMPTY;
        }
        writeEntries(result, registries, entries);
        setActiveTerminal(result, entries.getFirst().name());
        return result;
    }

    public static ItemStack upgradeTerminal(ItemStack universalTerminal, ItemStack addedTerminal, HolderLookup.Provider registries) {
        ItemStack result = universalTerminal.copyWithCount(1);
        List<TerminalEntry> existing = readEntries(universalTerminal, registries);
        TerminalEntry addedEntry = terminalEntryFromStack(addedTerminal);
        List<TerminalEntry> merged = mergeEntries(existing, List.of(addedEntry));
        if (merged.size() == existing.size()) {
            return ItemStack.EMPTY;
        }
        writeEntries(result, registries, merged);
        String active = getActiveTerminalName(universalTerminal, registries);
        setActiveTerminal(result, active != null ? active : merged.getFirst().name());
        return result;
    }

    public static List<Component> getInstalledTerminalLines(ItemStack stack, HolderLookup.Provider registries) {
        List<TerminalEntry> entries = readEntries(stack, registries);
        List<Component> lines = new ArrayList<>();
        if (entries.isEmpty()) {
            return List.of();
        }

        lines.add(Component.translatable("item.data_energistics.universal_terminal.desc"));
        for (TerminalEntry entry : entries) {
            lines.add(Component.literal(" - ").append(entry.stack().getHoverName().copy()));
        }
        return List.copyOf(lines);
    }

    public static List<String> getInstalledTerminalNames(ItemStack stack, HolderLookup.Provider registries) {
        return readEntries(stack, registries).stream().map(TerminalEntry::name).toList();
    }

    @Nullable
    public static String getActiveTerminalName(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String active = tag.getString(TAG_ACTIVE);
        if (active.isEmpty() || getDefinition(active).isEmpty()) {
            List<TerminalEntry> entries = readEntries(stack, registries);
            return entries.isEmpty() ? null : entries.getFirst().name();
        }
        return active;
    }

    public static void setActiveTerminal(ItemStack stack, String terminalName) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_ACTIVE, terminalName));
    }

    public static boolean isUniversalTerminal(ItemStack stack) {
        return stack.is(ModItems.UNIVERSAL_TERMINAL.get());
    }

    public static boolean isSupportedTerminal(ItemStack stack) {
        return getAdapter(stack)
                .map(adapter -> adapter.canInstall(stack))
                .orElse(false);
    }

    @Nullable
    public static String getTerminalName(ItemStack stack) {
        return getAdapter(stack)
                .filter(adapter -> adapter.canInstall(stack))
                .map(UniversalTerminalAdapter::name)
                .orElse(null);
    }

    public static ItemStack getMenuIcon(String terminalName) {
        return getDefinition(terminalName)
                .map(UniversalTerminalAdapter::createIcon)
                .orElse(ItemStack.EMPTY);
    }

    public static Component getTerminalDisplayName(String terminalName) {
        ItemStack icon = getMenuIcon(terminalName);
        return icon.isEmpty() ? Component.literal(terminalName) : icon.getHoverName().copy();
    }

    public static MenuType<?> getMenuType(String terminalName) {
        Optional<UniversalTerminalAdapter> definition = getDefinition(terminalName);
        return definition.isPresent() ? definition.get().getMenuType() : ModMenus.UNIVERSAL_CRAFTING_TERM.get();
    }

    public static UniversalTerminalConfigProfile getConfigProfile(@Nullable String terminalName) {
        return getDefinition(terminalName)
                .map(UniversalTerminalAdapter::configProfile)
                .orElse(UniversalTerminalConfigProfile.STANDARD);
    }

    public static @Nullable IConfigManager createConfigManager(String terminalName, Runnable saveAction) {
        return getDefinition(terminalName)
                .map(adapter -> adapter.createConfigManager(saveAction))
                .orElse(null);
    }

    public static int getTerminalIndex(@Nullable String terminalName) {
        if (terminalName == null) {
            return -1;
        }

        for (int i = 0; i < TERMINAL_DEFINITIONS.size(); i++) {
            if (TERMINAL_DEFINITIONS.get(i).name().equals(terminalName)) {
                return i;
            }
        }
        return -1;
    }

    public static @Nullable String getTerminalNameByIndex(int index) {
        return index >= 0 && index < TERMINAL_DEFINITIONS.size() ? TERMINAL_DEFINITIONS.get(index).name() : null;
    }

    public static int getDefinitionCount() {
        return TERMINAL_DEFINITIONS.size();
    }

    public static void writeEntries(ItemStack stack, HolderLookup.Provider registries, List<TerminalEntry> entries) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag terminalList = new ListTag();
            for (TerminalEntry entry : entries) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString(TAG_NAME, entry.name());
                entryTag.put(TAG_STACK, entry.stack().saveOptional(registries));
                terminalList.add(entryTag);
            }
            tag.put(TAG_TERMINALS, terminalList);
        });
    }

    public static List<TerminalEntry> readEntries(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag terminalList = tag.getList(TAG_TERMINALS, CompoundTag.TAG_COMPOUND);
        List<TerminalEntry> entries = new ArrayList<>(terminalList.size());
        for (int i = 0; i < terminalList.size(); i++) {
            CompoundTag entryTag = terminalList.getCompound(i);
            String name = entryTag.getString(TAG_NAME);
            if (name.isEmpty() || getDefinition(name).isEmpty()) {
                continue;
            }
            ItemStack terminalStack = ItemStack.parseOptional(registries, entryTag.getCompound(TAG_STACK));
            if (!terminalStack.isEmpty()) {
                entries.add(new TerminalEntry(name, terminalStack));
            }
        }
        return entries;
    }

    private static Optional<UniversalTerminalAdapter> getDefinition(String terminalName) {
        return TERMINAL_DEFINITIONS.stream()
                .filter(definition -> definition.name().equals(terminalName))
                .findFirst();
    }

    public static Optional<UniversalTerminalAdapter> getAdapter(ItemStack stack) {
        return getDefinitions().stream()
                .filter(definition -> definition.matches(stack))
                .findFirst();
    }

    public static <T> @Nullable T resolveMenuHost(UniversalTerminalPart part, Player player,
                                                  @Nullable String terminalName, Class<T> hostInterface) {
        if (terminalName == null) {
            return hostInterface.isInstance(part) ? hostInterface.cast(part) : null;
        }

        return getDefinition(terminalName)
                .map(adapter -> adapter.resolveMenuHost(part, player, hostInterface))
                .orElseGet(() -> hostInterface.isInstance(part) ? hostInterface.cast(part) : null);
    }

    private static TerminalEntry terminalEntryFromStack(ItemStack stack) {
        Optional<UniversalTerminalAdapter> adapter = getAdapter(stack)
                .filter(definition -> definition.canInstall(stack));
        if (adapter.isEmpty()) {
            throw new IllegalArgumentException("Unsupported terminal stack: " + stack);
        }
        return new TerminalEntry(adapter.get().name(), adapter.get().createStoredTerminal(stack));
    }

    private static List<TerminalEntry> mergeEntries(List<TerminalEntry> existing, List<TerminalEntry> additions) {
        Map<String, TerminalEntry> merged = new LinkedHashMap<>();
        for (TerminalEntry entry : existing) {
            merged.put(entry.name(), entry);
        }
        for (TerminalEntry entry : additions) {
            if (entry != null) {
                merged.putIfAbsent(entry.name(), entry);
            }
        }
        return List.copyOf(merged.values());
    }

    public record TerminalEntry(String name, ItemStack stack) {}
}
