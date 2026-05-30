package com.fish_dan_.data_energistics.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;
import appeng.menu.AEBaseMenu;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class UniversalTerminalScreenHook {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Method ADD_TO_LEFT_TOOLBAR = resolveMethod(AEBaseScreen.class, "addToLeftToolbar", Button.class);
    private static final Method ADD_RENDERABLE_WIDGET = resolveMethod(Screen.class, "addRenderableWidget", GuiEventListener.class);
    private static final Method REMOVE_WIDGET = resolveMethod(Screen.class, "removeWidget", GuiEventListener.class);
    private static final Field VERTICAL_TOOLBAR_FIELD = resolveField(AEBaseScreen.class, "verticalToolbar");
    private static final Field TOOLBAR_BUTTONS_FIELD = resolveField(VerticalButtonBar.class, "buttons");
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

        try {
            ADD_TO_LEFT_TOOLBAR.invoke(screen, button);
            ADD_RENDERABLE_WIDGET.invoke(screen, button);
            ADD_RENDERABLE_WIDGET.invoke(screen, selectorPanel);
            CYCLE_BUTTONS.put(screen, button);
            SELECTOR_PANELS.put(screen, selectorPanel);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.warn("Failed to inject universal terminal cycle button into {}", screen.getClass().getName(), e);
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
        try {
            REMOVE_WIDGET.invoke(screen, widget);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.warn("Failed to detach universal terminal widget {} from {}",
                    widget.getClass().getName(), screen.getClass().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromLeftToolbar(AEBaseScreen<?> screen) {
        try {
            VerticalButtonBar toolbar = (VerticalButtonBar) VERTICAL_TOOLBAR_FIELD.get(screen);
            List<Button> buttons = (List<Button>) TOOLBAR_BUTTONS_FIELD.get(toolbar);
            buttons.removeIf(existing -> existing instanceof UniversalTerminalCycleButton);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to remove universal terminal button from AE2 toolbar in {}",
                    screen.getClass().getName(), e);
        }
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

        try {
            VerticalButtonBar toolbar = (VerticalButtonBar) VERTICAL_TOOLBAR_FIELD.get(screen);
            List<Button> buttons = (List<Button>) TOOLBAR_BUTTONS_FIELD.get(toolbar);
            return buttons.contains(button);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Failed to inspect AE2 toolbar buttons in {}", screen.getClass().getName(), e);
            return false;
        }
    }

    private static Method resolveMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve method " + owner.getName() + "#" + name, e);
        }
    }

    private static Field resolveField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve field " + owner.getName() + "#" + name, e);
        }
    }
}
