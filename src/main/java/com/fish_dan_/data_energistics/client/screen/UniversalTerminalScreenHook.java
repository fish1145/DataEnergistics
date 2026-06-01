package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;
import appeng.menu.AEBaseMenu;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class UniversalTerminalScreenHook {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle ADD_TO_LEFT_TOOLBAR = resolveMethod(AEBaseScreen.class, "addToLeftToolbar", Button.class, Button.class);
    private static final MethodHandle ADD_RENDERABLE_WIDGET = resolveMethod(Screen.class, "addRenderableWidget", GuiEventListener.class, GuiEventListener.class);
    private static final MethodHandle REMOVE_WIDGET = resolveMethod(Screen.class, "removeWidget", void.class, GuiEventListener.class);
    private static final Optional<VarHandle> VERTICAL_TOOLBAR_FIELD = resolveField(AEBaseScreen.class, "verticalToolbar");
    private static final Optional<VarHandle> TOOLBAR_BUTTONS_FIELD = resolveField(VerticalButtonBar.class, "buttons");
    private static final Map<Screen, UniversalTerminalCycleButton> CYCLE_BUTTONS = new WeakHashMap<>();
    private static final Map<Screen, UniversalTerminalSelectorPanel> SELECTOR_PANELS = new WeakHashMap<>();
    private static boolean rememberedSelectorOpen;
    private static int rememberedSelectorPage;

    private UniversalTerminalScreenHook() {}

    public static UniversalTerminalSelectorPanel getSelectorPanel(Screen screen) {
        return SELECTOR_PANELS.get(screen);
    }

    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AEBaseScreen<?> screen)) {
            return;
        }

        AEBaseMenu menu = screen.getMenu();
        boolean supportsUniversalTerminal = UniversalTerminalClientHelper.supportsUniversalTerminal(menu);
        if (supportsUniversalTerminal) {
            UniversalTerminalClientHelper.restoreMousePositionIfNeeded();
        } else {
            detachExistingControls(screen);
            rememberedSelectorOpen = false;
            rememberedSelectorPage = 0;
            return;
        }

        injectFreshControls(screen);
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AEBaseScreen<?> screen)) {
            return;
        }

        AEBaseMenu menu = screen.getMenu();
        if (!UniversalTerminalClientHelper.supportsUniversalTerminal(menu)) {
            return;
        }

        ensureControlsPresent(screen);
    }

    static void rememberSelectorState(boolean open, int page) {
        rememberedSelectorOpen = open;
        rememberedSelectorPage = Math.max(0, page);
    }

    private static void ensureControlsPresent(AEBaseScreen<?> screen) {
        UniversalTerminalCycleButton mappedButton = CYCLE_BUTTONS.get(screen);
        UniversalTerminalSelectorPanel mappedPanel = SELECTOR_PANELS.get(screen);
        boolean missingButton = mappedButton == null || !isRenderableAttached(screen, mappedButton) || !isToolbarButtonAttached(screen, mappedButton);
        boolean missingPanel = mappedPanel == null || !isRenderableAttached(screen, mappedPanel);

        if (!missingButton && !missingPanel) {
            return;
        }

        injectFreshControls(screen);
    }

    private static void injectFreshControls(AEBaseScreen<?> screen) {
        detachExistingControls(screen);

        Supplier<AEBaseMenu> menuSupplier = screen::getMenu;
        UniversalTerminalSelectorPanel selectorPanel = new UniversalTerminalSelectorPanel(screen, menuSupplier, null);
        UniversalTerminalCycleButton button = new UniversalTerminalCycleButton(
                btn -> selectorPanel.toggleOpen(),
                () -> {
                    AEBaseMenu currentMenu = menuSupplier.get();
                    return currentMenu != null ? UniversalTerminalClientHelper.getActiveTerminalIcon(currentMenu) : net.minecraft.world.item.ItemStack.EMPTY;
                },
                () -> {
                    AEBaseMenu currentMenu = menuSupplier.get();
                    return currentMenu != null ? UniversalTerminalClientHelper.getSelectorTooltip(currentMenu) : java.util.List.of();
                },
                selectorPanel::isOpen,
                () -> new int[] { screen.getGuiLeft() - 18, screen.getGuiTop() + 2 });
        selectorPanel.setAnchorButton(button);
        selectorPanel.restoreState(rememberedSelectorOpen, rememberedSelectorPage);

        if (invoke(ADD_TO_LEFT_TOOLBAR, screen, button) && invoke(ADD_RENDERABLE_WIDGET, screen, button) && invoke(ADD_RENDERABLE_WIDGET, screen, selectorPanel)) {
            CYCLE_BUTTONS.put(screen, button);
            SELECTOR_PANELS.put(screen, selectorPanel);
        } else {
            LOGGER.warn("Failed to inject universal terminal cycle button into {}", screen.getClass().getName());
        }
    }

    private static void detachExistingControls(AEBaseScreen<?> screen) {
        UniversalTerminalSelectorPanel mappedPanel = SELECTOR_PANELS.remove(screen);
        UniversalTerminalCycleButton mappedButton = CYCLE_BUTTONS.remove(screen);

        detachWidget(screen, mappedPanel);
        detachWidget(screen, mappedButton);
        removeFromLeftToolbar(screen);

        UniversalTerminalSelectorPanel strayPanel = findSelectorPanel(screen);
        if (strayPanel != null) {
            detachWidget(screen, strayPanel);
        }

        UniversalTerminalCycleButton strayButton = findCycleButton(screen);
        if (strayButton != null) {
            detachWidget(screen, strayButton);
        }
    }

    private static void detachWidget(Screen screen, GuiEventListener widget) {
        if (widget == null) {
            return;
        }
        if (!invoke(REMOVE_WIDGET, screen, widget)) {
            LOGGER.warn("Failed to detach universal terminal widget {} from {}",
                    widget.getClass().getName(), screen.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromLeftToolbar(AEBaseScreen<?> screen) {
        VerticalButtonBar toolbar = (VerticalButtonBar) ReflectionAccess.getField(VERTICAL_TOOLBAR_FIELD, screen);
        List<Button> buttons = toolbar == null ? null : (List<Button>) ReflectionAccess.getField(TOOLBAR_BUTTONS_FIELD, toolbar);
        if (buttons == null) {
            LOGGER.warn("Failed to remove universal terminal button from AE2 toolbar in {}",
                    screen.getClass().getName());
            return;
        }
        buttons.removeIf(existing -> existing instanceof UniversalTerminalCycleButton);
    }

    private static UniversalTerminalCycleButton findCycleButton(AEBaseScreen<?> screen) {
        for (var renderable : screen.renderables) {
            if (renderable instanceof UniversalTerminalCycleButton button) {
                return button;
            }
        }
        return null;
    }

    private static UniversalTerminalSelectorPanel findSelectorPanel(AEBaseScreen<?> screen) {
        for (var renderable : screen.renderables) {
            if (renderable instanceof UniversalTerminalSelectorPanel panel) {
                return panel;
            }
        }
        return null;
    }

    private static boolean isRenderableAttached(AEBaseScreen<?> screen, GuiEventListener widget) {
        if (widget == null) {
            return false;
        }

        for (var renderable : screen.renderables) {
            if (renderable == widget) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean isToolbarButtonAttached(AEBaseScreen<?> screen, Button button) {
        if (button == null) {
            return false;
        }

        VerticalButtonBar toolbar = (VerticalButtonBar) ReflectionAccess.getField(VERTICAL_TOOLBAR_FIELD, screen);
        List<Button> buttons = toolbar == null ? null : (List<Button>) ReflectionAccess.getField(TOOLBAR_BUTTONS_FIELD, toolbar);
        if (buttons == null) {
            LOGGER.warn("Failed to inspect AE2 toolbar buttons in {}", screen.getClass().getName());
            return false;
        }
        return buttons.contains(button);
    }

    private static MethodHandle resolveMethod(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) {
        try {
            return MethodHandles.privateLookupIn(owner, LOOKUP)
                    .findVirtual(owner, name, MethodType.methodType(returnType, parameterTypes));
        } catch (NoSuchMethodException | IllegalAccessException | SecurityException e) {
            throw new IllegalStateException("Could not resolve method " + owner.getName() + "#" + name, e);
        }
    }

    private static Optional<VarHandle> resolveField(Class<?> owner, String name) {
        Optional<VarHandle> field = ReflectionAccess.findField(owner, name);
        if (field.isEmpty()) {
            throw new IllegalStateException("Could not resolve field " + owner.getName() + "#" + name);
        }
        return field;
    }

    private static boolean invoke(MethodHandle method, Object target, Object argument) {
        try {
            method.invoke(target, argument);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
