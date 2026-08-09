package com.fish_dan_.data_energistics.client.screen.terminal;

import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuBridge;
import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuLocator;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalSelectPayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;
import com.fish_dan_.data_energistics.util.UniversalTerminalHostAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.AEBaseMenu;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class UniversalTerminalClientHelper {

    private static @Nullable MousePosition pendingMousePosition;
    private static @Nullable TerminalState cachedTerminalState;
    private static @Nullable TerminalState syncedTerminalState;
    private static @Nullable AEBaseMenu syncedTerminalMenu;

    private UniversalTerminalClientHelper() {}

    public static boolean supportsUniversalTerminal(AEBaseMenu menu) {
        return getTerminalState(menu) != null;
    }

    public static @Nullable String getNextTerminalName(AEBaseMenu menu, boolean reverse) {
        TerminalState state = getTerminalState(menu);
        if (state == null) {
            return null;
        }

        String terminalName = getNextTerminalName(state.installedTerminals(), state.activeTerminalName(), reverse);
        return terminalName != null ? terminalName : getNextTerminalName(state.availableTerminalMask(), state.activeTerminalIndex(), reverse);
    }

    public static ItemStack getNextTerminalIcon(AEBaseMenu menu) {
        String terminalName = getNextTerminalName(menu, false);
        return terminalName == null ? ItemStack.EMPTY : UniversalTerminalData.getMenuIcon(terminalName);
    }

    public static ItemStack getActiveTerminalIcon(AEBaseMenu menu) {
        TerminalState state = getTerminalState(menu);
        if (state == null) {
            return ItemStack.EMPTY;
        }

        if (state.activeTerminalName() != null) {
            for (var entry : state.installedEntries()) {
                if (state.activeTerminalName().equals(entry.name()) && !entry.stack().isEmpty()) {
                    return entry.stack().copy();
                }
            }
        }

        String terminalName = state.activeTerminalName();
        return terminalName == null ? ItemStack.EMPTY : UniversalTerminalData.getMenuIcon(terminalName);
    }

    public static @Nullable String getActiveTerminalName(AEBaseMenu menu) {
        TerminalState state = getTerminalState(menu);
        return state != null ? state.activeTerminalName() : null;
    }

    public static List<String> getInstalledTerminalNames(AEBaseMenu menu) {
        TerminalState state = getTerminalState(menu);
        return state != null ? state.installedTerminals() : List.of();
    }

    public static List<UniversalTerminalData.TerminalEntry> getInstalledTerminalEntries(AEBaseMenu menu) {
        TerminalState state = getTerminalState(menu);
        return state != null ? state.installedEntries() : List.of();
    }

    public static List<Component> getCycleTooltip(AEBaseMenu menu) {
        return buildCycleTooltip(getNextTerminalName(menu, false), getNextTerminalName(menu, true));
    }

    public static List<Component> getSelectorTooltip(AEBaseMenu menu) {
        return List.of(
                Component.translatable("gui.data_energistics.universal_terminal.switch_terminal"),
                Component.translatable("gui.data_energistics.universal_terminal.current",
                        getActiveTerminalName(menu) == null ? Component.empty() : UniversalTerminalData.getTerminalDisplayName(getActiveTerminalName(menu))));
    }

    public static void sendCycleTerminal(boolean reverse) {
        PacketDistributor.sendToServer(new UniversalTerminalCyclePayload(reverse));
    }

    public static void sendSelectTerminal(String terminalName) {
        PacketDistributor.sendToServer(new UniversalTerminalSelectPayload(terminalName));
    }

    public static void rememberMousePosition() {
        Minecraft minecraft = Minecraft.getInstance();
        pendingMousePosition = new MousePosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
    }

    public static void restoreMousePositionIfNeeded() {
        MousePosition mousePosition = pendingMousePosition;
        if (mousePosition == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || !minecraft.isWindowActive()) {
            return;
        }

        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, mousePosition.x(), mousePosition.y());
        minecraft.mouseHandler.setIgnoreFirstMove();
        pendingMousePosition = null;
    }

    public static void cacheSyncedTerminalState(List<String> installedTerminalNames, @Nullable String activeTerminalName) {
        if (installedTerminalNames.isEmpty()) {
            syncedTerminalState = null;
            syncedTerminalMenu = null;
            return;
        }

        List<UniversalTerminalData.TerminalEntry> installedEntries = installedTerminalNames.stream()
                .map(name -> new UniversalTerminalData.TerminalEntry(name, UniversalTerminalData.getMenuIcon(name)))
                .toList();
        int availableMask = buildTerminalMask(installedTerminalNames);
        int activeTerminalIndex = UniversalTerminalData.getTerminalIndex(activeTerminalName);
        syncedTerminalState = new TerminalState(
                installedEntries,
                List.copyOf(installedTerminalNames),
                activeTerminalName,
                availableMask,
                activeTerminalIndex);
        syncedTerminalMenu = null;
    }

    private static @Nullable TerminalState getTerminalState(AEBaseMenu menu) {
        TerminalState directState = getDirectTerminalState(menu);
        if (directState != null) {
            cachedTerminalState = directState;
            return normalizeTerminalState(directState, menu);
        }

        TerminalState fallbackState = getCachedTerminalState(menu);
        if (fallbackState != null) {
            return fallbackState;
        }

        TerminalState syncedState = getSyncedTerminalState(menu);
        if (syncedState != null) {
            cachedTerminalState = syncedState;
            return syncedState;
        }

        return null;
    }

    private static @Nullable TerminalState getDirectTerminalState(AEBaseMenu menu) {
        if (menu instanceof UniversalTerminalMenuBridge bridge) {
            UniversalTerminalPart host = bridge.getUniversalTerminalHost();
            return new TerminalState(
                    host != null ? host.getInstalledTerminalEntries() : List.of(),
                    host != null ? host.getInstalledTerminalNames() : List.of(),
                    host != null ? host.getActiveTerminalName() : null,
                    bridge.getAvailableTerminalMask(),
                    bridge.getActiveTerminalIndex());
        }

        Object target = menu.getTarget();
        if (target instanceof UniversalTerminalHostAccessor accessor) {
            UniversalTerminalPart part = accessor.getUniversalTerminalPart();
            return new TerminalState(
                    part.getInstalledTerminalEntries(),
                    part.getInstalledTerminalNames(),
                    part.getActiveTerminalName(),
                    part.getInstalledTerminalMask(),
                    part.getActiveTerminalIndex());
        }

        if (!(menu.getLocator() instanceof UniversalTerminalMenuLocator locator)) {
            return null;
        }

        UniversalTerminalPart part = locator.locate(menu.getPlayer(), UniversalTerminalPart.class);
        if (part == null) {
            return null;
        }

        return new TerminalState(
                part.getInstalledTerminalEntries(),
                part.getInstalledTerminalNames(),
                part.getActiveTerminalName(),
                part.getInstalledTerminalMask(),
                part.getActiveTerminalIndex());
    }

    private static @Nullable TerminalState getCachedTerminalState(AEBaseMenu menu) {
        TerminalState cachedState = cachedTerminalState;
        if (cachedState == null || cachedState.installedTerminals().isEmpty()) {
            return null;
        }

        String activeTerminalName = resolveTerminalNameByMenuType(menu, cachedState.installedTerminals());
        if (activeTerminalName == null) {
            return null;
        }

        return normalizeTerminalState(new TerminalState(
                cachedState.installedEntries(),
                cachedState.installedTerminals(),
                activeTerminalName,
                cachedState.availableTerminalMask(),
                cachedState.activeTerminalIndex()), menu);
    }

    private static @Nullable TerminalState getSyncedTerminalState(AEBaseMenu menu) {
        TerminalState pendingState = syncedTerminalState;
        if (pendingState == null || pendingState.installedTerminals().isEmpty()) {
            return null;
        }

        if (syncedTerminalMenu == menu) {
            return normalizeTerminalState(pendingState, menu);
        }

        if (syncedTerminalMenu != null) {
            return null;
        }

        String activeTerminalName = resolveTerminalNameByMenuType(menu, pendingState.installedTerminals());
        if (activeTerminalName == null) {
            return null;
        }

        syncedTerminalMenu = menu;
        return normalizeTerminalState(new TerminalState(
                pendingState.installedEntries(),
                pendingState.installedTerminals(),
                activeTerminalName,
                pendingState.availableTerminalMask(),
                pendingState.activeTerminalIndex()), menu);
    }

    private static TerminalState normalizeTerminalState(TerminalState state, AEBaseMenu menu) {
        List<String> installedTerminals = state.installedTerminals();
        if (installedTerminals.isEmpty()) {
            return state;
        }

        String activeTerminalName = state.activeTerminalName();
        if (activeTerminalName == null || !installedTerminals.contains(activeTerminalName)) {
            String resolvedByMenuType = resolveTerminalNameByMenuType(menu, installedTerminals);
            activeTerminalName = resolvedByMenuType != null ? resolvedByMenuType : installedTerminals.getFirst();
        }

        int availableMask = buildTerminalMask(installedTerminals);
        int activeTerminalIndex = UniversalTerminalData.getTerminalIndex(activeTerminalName);
        return new TerminalState(
                state.installedEntries(),
                installedTerminals,
                activeTerminalName,
                availableMask,
                activeTerminalIndex);
    }

    private static int buildTerminalMask(List<String> installedTerminals) {
        int mask = 0;
        for (String terminalName : installedTerminals) {
            int index = UniversalTerminalData.getTerminalIndex(terminalName);
            if (index >= 0) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    private static @Nullable String resolveTerminalNameByMenuType(AEBaseMenu menu, List<String> installedTerminals) {
        var menuType = menu.getType();
        for (String terminalName : installedTerminals) {
            if (UniversalTerminalData.getMenuType(terminalName) == menuType) {
                return terminalName;
            }
        }

        return null;
    }

    private static @Nullable String getNextTerminalName(int mask, int activeIndex, boolean reverse) {
        if (mask == 0 || activeIndex < 0) {
            return null;
        }

        int definitionCount = UniversalTerminalData.getDefinitionCount();
        int index = activeIndex;
        for (int i = 0; i < definitionCount; i++) {
            index = reverse ? (index - 1 + definitionCount) % definitionCount : (index + 1) % definitionCount;
            if ((mask & (1 << index)) != 0) {
                return UniversalTerminalData.getTerminalNameByIndex(index);
            }
        }

        return UniversalTerminalData.getTerminalNameByIndex(activeIndex);
    }

    private static @Nullable String getNextTerminalName(List<String> installedTerminals, @Nullable String activeTerminalName,
                                                        boolean reverse) {
        if (installedTerminals.isEmpty()) {
            return null;
        }

        int currentIndex = activeTerminalName == null ? -1 : installedTerminals.indexOf(activeTerminalName);
        if (currentIndex < 0) {
            return installedTerminals.getFirst();
        }

        int offset = reverse ? -1 : 1;
        return installedTerminals.get((currentIndex + offset + installedTerminals.size()) % installedTerminals.size());
    }

    private static List<Component> buildCycleTooltip(@Nullable String next, @Nullable String previous) {
        return List.of(
                Component.translatable("gui.data_energistics.universal_terminal.switch_terminal"),
                Component.translatable("gui.data_energistics.universal_terminal.next",
                        next == null ? Component.empty() : UniversalTerminalData.getTerminalDisplayName(next)),
                Component.translatable("gui.data_energistics.universal_terminal.previous",
                        previous == null ? Component.empty() : UniversalTerminalData.getTerminalDisplayName(previous)));
    }

    private record TerminalState(List<UniversalTerminalData.TerminalEntry> installedEntries,
                                 List<String> installedTerminals, @Nullable String activeTerminalName,
                                 int availableTerminalMask, int activeTerminalIndex) {}

    private record MousePosition(double x, double y) {}
}
