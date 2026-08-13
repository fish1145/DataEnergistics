package com.fish_dan_.data_energistics.gui.ldlib2.trinity.exchange;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityInformationExchangeDepotBlockEntity.StorageMode;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;

import java.util.List;

/** Binds the authored information-exchange-depot NBT to its server-authoritative storage mode. */
public final class TrinityInformationExchangeDepotUi {

    private static final String ROOT_ID = "trinity_information_exchange_depot_root";
    private static final String CONTENT_ID = "trinity_information_exchange_depot_content";
    private static final String MODE_GROUP_ID = "trinity_information_exchange_depot_mode_group";
    private static final String TITLE_ID = "trinity_information_exchange_depot_title";
    private static final String CLOSE_ID = "trinity_information_exchange_depot_close";

    private TrinityInformationExchangeDepotUi() {}

    public static ModularUI mount(TrinityInformationExchangeDepotMenu menu, Component title) {
        try {
            UI ui = TrinityUiNbtLayouts.load("information_exchange_depot");
            Layout layout = Layout.bind(ui.rootElement);
            bindText(layout, title);
            bindModes(menu, layout);
            layout.close().setOnClick(event -> menu.getPlayer().closeContainer());
            WindowDragHelper.setDragMove(
                    layout.root(),
                    layout.root(),
                    event -> event.button == 0 && event.target == layout.root(),
                    ignored -> {});

            ModularUI modularUI = ModularUI.of(ui, menu.getPlayer());
            AeMenuBridge.create(menu).mount(modularUI);
            return modularUI;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the Trinity information exchange depot LDLib2 UI", failure);
            throw failure;
        }
    }

    private static void bindText(Layout layout, Component title) {
        layout.title().setText(title);
        layout.title().setAllowHitTest(false);
        layout.title().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        layout.close().text.style(style -> style.tooltips(Component.translatable("gui.close")));
        layout.close().style(style -> style.tooltips(Component.translatable("gui.close")));
    }

    private static void bindModes(TrinityInformationExchangeDepotMenu menu, Layout layout) {
        bindMode(menu, layout.input(), StorageMode.INPUT);
        bindMode(menu, layout.storage(), StorageMode.STORAGE);
        bindMode(menu, layout.output(), StorageMode.OUTPUT);
        layout.root().addEventListener(UIEvents.TICK, ignored -> {
            StorageMode mode = menu.mode();
            layout.input().setOn(mode == StorageMode.INPUT, false);
            layout.storage().setOn(mode == StorageMode.STORAGE, false);
            layout.output().setOn(mode == StorageMode.OUTPUT, false);
        });
    }

    private static void bindMode(
                                 TrinityInformationExchangeDepotMenu menu,
                                 Toggle toggle,
                                 StorageMode mode) {
        toggle.noText();
        toggle.setOnToggleChanged(on -> {
            if (on && menu.mode() != mode) {
                menu.sendSetMode(mode);
            }
        });
        Component tooltip = Component.translatable(
                "gui.data_energistics.trinity_information_exchange_depot.mode." + mode.serializedName());
        toggle.style(style -> style.tooltips(tooltip));
        toggle.toggleButton(button -> button.text.style(style -> style.tooltips(tooltip)));
    }

    private record Layout(
                          UIElement root,
                          UIElement content,
                          ToggleGroupElement group,
                          Toggle input,
                          Toggle storage,
                          Toggle output,
                          Label title,
                          Button close) {

        private static Layout bind(UIElement root) {
            List<UIElement> rootChildren = authoredChildren(root);
            if (rootChildren.size() != 2) {
                throw new IllegalStateException("Information exchange depot layout expected two authored root children");
            }
            UIElement content = require(rootChildren, 0, UIElement.class, "content");
            Button close = require(rootChildren, 1, Button.class, "close");
            List<UIElement> contentChildren = authoredChildren(content);
            if (contentChildren.size() != 2) {
                throw new IllegalStateException("Information exchange depot content expected two authored children");
            }
            ToggleGroupElement group = require(contentChildren, 0, ToggleGroupElement.class, "mode group");
            Label title = require(contentChildren, 1, Label.class, "title");
            List<UIElement> toggles = authoredChildren(group);
            if (toggles.size() != 3) {
                throw new IllegalStateException("Information exchange depot mode group expected three authored toggles");
            }
            Toggle storage = require(toggles, 0, Toggle.class, "storage mode");
            Toggle input = require(toggles, 1, Toggle.class, "input mode");
            Toggle output = require(toggles, 2, Toggle.class, "output mode");

            root.setId(ROOT_ID);
            content.setId(CONTENT_ID);
            group.setId(MODE_GROUP_ID);
            title.setId(TITLE_ID);
            close.setId(CLOSE_ID);
            return new Layout(root, content, group, input, storage, output, title, close);
        }

        private static List<UIElement> authoredChildren(UIElement element) {
            return element.getChildren().stream().filter(child -> !child.isInternalUI()).toList();
        }

        private static <T extends UIElement> T require(
                                                       List<UIElement> children,
                                                       int index,
                                                       Class<T> expected,
                                                       String role) {
            UIElement child = children.get(index);
            if (!expected.isInstance(child)) {
                throw new IllegalStateException("Information exchange depot " + role + " must be " +
                        expected.getSimpleName() + ", found " + child.getClass().getSimpleName());
            }
            return expected.cast(child);
        }
    }
}
