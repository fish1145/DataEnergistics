package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;
import com.fish_dan_.data_energistics.orbital.control.ui.layout.OrbitalTerminalLayout;
import com.fish_dan_.data_energistics.orbital.control.ui.map.OrbitalTacticalMapPanel;
import com.fish_dan_.data_energistics.orbital.control.ui.map.OrbitalTacticalMapPanel.MapProviderOption;
import com.fish_dan_.data_energistics.orbital.control.ui.target.OrbitalModePicker;
import com.fish_dan_.data_energistics.orbital.control.ui.target.OrbitalTargetPanel;
import com.fish_dan_.data_energistics.orbital.control.ui.weapon.OrbitalWeaponListPanel;
import com.fish_dan_.data_energistics.orbital.control.ui.weapon.OrbitalWeaponStatusPanel;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.math.Size;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Locale;

/** Responsive tactical workspace; stable component identities preserve input focus during synchronization. */
public final class OrbitalControlDashboard {

    public static final int MAP_RADIUS = OrbitalTacticalMapPanel.RADIUS;
    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.";
    public final UIElement root = new UIElement();
    public final OrbitalWeaponListPanel weapons = new OrbitalWeaponListPanel();
    public final OrbitalTacticalMapPanel map = new OrbitalTacticalMapPanel();
    public final OrbitalWeaponStatusPanel status = new OrbitalWeaponStatusPanel();
    public final OrbitalTargetPanel target;
    public final Label feedback;
    public final Button hudLayout;
    public final Button refreshPreview;
    public final Button confirm;
    public final TextField dimension;
    public final OrbitalModePicker mode;
    public final TextField targetX;
    public final TextField targetZ;
    public final Selector<OrbitalTargetYMode> targetYMode;
    public final TextField targetYValue;
    public final Selector<Integer> radius;
    public final Selector<OrbitalDirectedEnergyDepth> depth;
    public final Selector<MapProviderOption> mapProvider;
    public final Button selectOnMap;
    public final Button mapRefresh;
    public final Label mapStatus;
    public final ObjectArrayList<Button> mapCells;

    private final Label heading;
    private final ObjectArrayList<Button> pages = new ObjectArrayList<>();
    private final UIElement holdProgress = new UIElement();
    private OrbitalTerminalLayout layout = OrbitalTerminalLayout.forViewport(800, 480);
    private Page page = Page.MAP;
    private boolean draftChanged;

    private OrbitalControlDashboard(Player player) {
        root.setId("orbital_control_root");
        OrbitalControlUiTheme.stylePanel(root, Tone.SHELL);
        root.layout(style -> style.width(layout.width()).height(layout.height()));
        target = new OrbitalTargetPanel(player);
        dimension = target.dimension;
        mode = target.mode;
        targetX = target.targetX;
        targetZ = target.targetZ;
        targetYMode = target.targetYMode;
        targetYValue = target.targetYValue;
        radius = target.radius;
        depth = target.depth;
        mapProvider = map.provider;
        selectOnMap = map.externalMap;
        mapRefresh = map.refresh;
        mapStatus = map.status;
        mapCells = map.cells;
        heading = OrbitalControlUiTheme.label("orbital_workspace_heading", Component.translatable(PREFIX + "title"),
                8, 4, 240, 20, OrbitalControlUiTheme.ACCENT_TEXT, 10, TextWrap.HOVER_ROLL);
        feedback = OrbitalControlUiTheme.label("orbital_workspace_feedback", Component.translatable(PREFIX + "subtitle"),
                6, 0, 320, 16, OrbitalControlUiTheme.MUTED_TEXT, 9, TextWrap.HOVER_ROLL);
        hudLayout = OrbitalControlUiTheme.button("orbital_hud_layout", Component.translatable(PREFIX + "workspace.hud_layout"),
                0, 3, 84, 22, Tone.PANEL);
        refreshPreview = OrbitalControlUiTheme.button("orbital_preview", Component.translatable(PREFIX + "fire_control.preview"),
                0, 0, 112, 24, Tone.ACCENT);
        confirm = OrbitalControlUiTheme.button("orbital_confirm", Component.translatable(PREFIX + "fire_control.confirm"),
                0, 0, 112, 24, Tone.DANGER);
        holdProgress.setAllowHitTest(false);
        holdProgress.style(style -> style.backgroundTexture(new ColorRectTexture(0xFFC23D58)));
        OrbitalControlUiTheme.place(holdProgress, 1, 20, 0, 3);
        confirm.addChild(holdProgress);
        for (Page entry : Page.values()) {
            Button tab = OrbitalControlUiTheme.button("orbital_page_" + entry.name(), pageLabel(entry),
                    6 + pages.size() * 80, 28, 76, 22, Tone.PANEL);
            tab.setOnClick(ignored -> showPage(entry));
            pages.add(tab);
            root.addChild(tab);
        }
        root.addChildren(heading, hudLayout, weapons.root, map.root, target.root, status.root, feedback, refreshPreview, confirm);
        resize(Size.of(800, 480));
        confirm.setActive(false);
    }

    public static OrbitalControlDashboard create(Player player) {
        return new OrbitalControlDashboard(player);
    }

    /** LDLib2 invokes this during initialization and every window or GUI-scale resize. */
    public Size resize(Size viewport) {
        layout = OrbitalTerminalLayout.forViewport(viewport.getWidth(), viewport.getHeight());
        int width = layout.width();
        int height = layout.height();
        OrbitalControlUiTheme.place(heading, 8, 4, width - 104, 20);
        OrbitalControlUiTheme.place(hudLayout, width - 90, 3, 84, 22);
        OrbitalControlUiTheme.place(feedback, 6, height - 48, width - 12, 16);
        OrbitalControlUiTheme.place(refreshPreview, width - 234, height - 29, 112, 24);
        OrbitalControlUiTheme.place(confirm, width - 118, height - 29, 112, 24);
        OrbitalControlUiTheme.place(weapons.root, 6, layout.bodyTop(), layout.listWidth(), layout.bodyHeight());
        weapons.resize(layout.listWidth(), layout.bodyHeight());
        OrbitalControlUiTheme.place(map.root, layout.mapLeft(), layout.bodyTop(), layout.mapWidth(), layout.bodyHeight());
        map.resize(layout.mapWidth(), layout.bodyHeight());
        OrbitalControlUiTheme.place(target.root, layout.formLeft(), layout.bodyTop(), layout.formWidth(), layout.bodyHeight());
        OrbitalControlUiTheme.place(status.root, layout.formLeft(), layout.bodyTop(), layout.formWidth(), layout.bodyHeight());
        updatePages();
        return Size.of(width, height);
    }

    public Page page() {
        return page;
    }

    public void showPage(Page page) {
        this.page = page;
        updatePages();
    }

    private void updatePages() {
        boolean wide = layout.wide();
        weapons.root.setDisplay(wide || page == Page.WEAPONS);
        map.root.setDisplay(wide || page == Page.MAP);
        target.root.setDisplay(wide ? page != Page.STATUS : page == Page.FIRE_CONTROL);
        status.root.setDisplay(page == Page.STATUS);
        int x = 6;
        for (Page entry : Page.values()) {
            Button button = pages.get(entry.ordinal());
            boolean visible = !wide || entry == Page.MAP || entry == Page.STATUS;
            button.setDisplay(visible);
            if (visible) {
                int tabWidth = Math.min(76, (layout.width() - 18) / 4);
                OrbitalControlUiTheme.place(button, x, 28, tabWidth, 22);
                x += tabWidth + 4;
            }
            boolean selected = page == entry || wide && entry == Page.MAP && page != Page.STATUS;
            OrbitalControlUiTheme.styleButton(button, selected ? Tone.ACCENT : Tone.PANEL);
        }
    }

    public void apply(OrbitalControlMenuSnapshot snapshot) {
        weapons.apply(snapshot.terminal());
        status.apply(snapshot.terminal());
        heading.setValue(OrbitalControlPresentation.weaponTitle(snapshot.terminal()));
        boolean operable = snapshot.terminal().selectedWeapon().map(weapon -> weapon.canOperate()).orElse(false);
        target.setOperable(operable);
        map.setOperable(operable);
        var fire = snapshot.fireControl();
        refreshPreview.setActive(operable && fire.phase() != OrbitalFireControlSessionSnapshot.Phase.CALCULATING);
        confirm.setActive(false); // The binding enables only the current draft and server nonce.
        target.preview.setValue(OrbitalControlPresentation.fireControl(fire, snapshot.feedback()));
        if (!draftChanged) {
            feedback.setValue(OrbitalControlPresentation.feedback(snapshot.feedback()));
        }
        long percent = fire.requiredHoldTicks() == 0 ? 0 : Math.min(100, fire.heldTicks() * 100 / fire.requiredHoldTicks());
        holdProgress.layout(style -> style.width(percent * 110 / 100));
        confirm.setText(fire.phase() == OrbitalFireControlSessionSnapshot.Phase.HOLDING ?
                Component.translatable(PREFIX + (percent >= 100 ? "workspace.release" : "workspace.holding"), percent) :
                Component.translatable(PREFIX + "fire_control.confirm"));
    }

    public void updateDirectedFields(boolean operable) {
        target.updateMode(operable);
    }

    public void markDraftChanged() {
        draftChanged = true;
        confirm.setActive(false);
        holdProgress.layout(style -> style.width(0));
        Component message = Component.translatable(PREFIX + "workspace.draft_changed");
        target.setMessage(message);
        feedback.setValue(message);
    }

    public void markPreviewRequested() {
        draftChanged = false;
        confirm.setActive(false);
        target.setMessage(Component.empty());
    }

    private static Component pageLabel(Page page) {
        return Component.translatable(PREFIX + "workspace.page." + page.name().toLowerCase(Locale.ROOT));
    }

    public enum Page {
        MAP,
        FIRE_CONTROL,
        STATUS,
        WEAPONS
    }
}
