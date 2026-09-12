package com.fish_dan_.data_energistics.orbital.control.ui.map;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Main tactical viewport with stable target highlighting and distinct external-map and refresh actions. */
public final class OrbitalTacticalMapPanel {

    public static final int RADIUS = 3;
    public static final ResourceLocation BUILTIN = Data_Energistics.id("builtin_tactical_map");
    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.";
    public final UIElement root = new UIElement();
    public final Selector<MapProviderOption> provider;
    public final Button externalMap;
    public final Button refresh;
    public final Button recenter;
    public final ObjectArrayList<Button> panButtons = new ObjectArrayList<>();
    public final ObjectArrayList<Button> cells = new ObjectArrayList<>(49);
    public final Label status;
    private final Label legend;
    private final @Nullable OrbitalMapTile[] displayedTiles = new OrbitalMapTile[49];
    private final boolean[] displayedTargets = new boolean[49];
    private final boolean[] displayedAreas = new boolean[49];
    private final boolean[] initializedCells = new boolean[49];
    private boolean operable;

    public OrbitalTacticalMapPanel() {
        root.setId("orbital_tactical_workspace");
        OrbitalControlUiTheme.stylePanel(root, Tone.PANEL_ALT);
        MapProviderOption builtin = new MapProviderOption(BUILTIN,
                Component.translatable(PREFIX + "fire_control.map.provider.builtin"));
        provider = OrbitalControlUiTheme.choices("orbital_fire_control_map_provider", 6, 4, 128,
                List.of(builtin), builtin, MapProviderOption::label);
        externalMap = OrbitalControlUiTheme.button("orbital_external_map",
                Component.translatable(PREFIX + "workspace.external_map"), 138, 4, 88, 20, Tone.PANEL);
        refresh = OrbitalControlUiTheme.button("orbital_map_refresh",
                Component.translatable(PREFIX + "fire_control.map.refresh"), 0, 0, 60, 20, Tone.ACCENT);
        recenter = OrbitalControlUiTheme.button("orbital_map_recenter",
                Component.translatable(PREFIX + "workspace.recenter"), 0, 0, 64, 20, Tone.PANEL);
        status = OrbitalControlUiTheme.label("orbital_map_status", Component.translatable(PREFIX + "fire_control.map.status"),
                6, 28, 220, 18, OrbitalControlUiTheme.MUTED_TEXT, 9, TextWrap.HOVER_ROLL);
        legend = OrbitalControlUiTheme.label("orbital_map_legend", Component.translatable(PREFIX + "workspace.map_hint"),
                6, 0, 220, 14, OrbitalControlUiTheme.MUTED_TEXT, 8, TextWrap.HOVER_ROLL);
        legend.style(style -> style.tooltips(Component.translatable(PREFIX + "fire_control.map.legend")));
        root.addChildren(provider, externalMap, status, refresh, recenter, legend);
        for (String direction : List.of("←", "↑", "↓", "→")) {
            Button button = OrbitalControlUiTheme.button("orbital_map_pan_" + panButtons.size(),
                    Component.literal(direction), 0, 0, 22, 20, Tone.PANEL);
            panButtons.add(button);
            root.addChild(button);
        }
        for (int index = 0; index < 49; index++) {
            Button cell = OrbitalControlUiTheme.button("orbital_map_cell_" + index,
                    Component.literal("?"), 0, 0, 20, 20, Tone.PANEL);
            cells.add(cell);
            root.addChild(cell);
        }
        provider.registerValueListener(ignored -> updateProvider());
        updateProvider();
    }

    public void resize(int width, int height) {
        OrbitalControlUiTheme.place(provider, 6, 4, Math.max(80, width - 104), 20);
        OrbitalControlUiTheme.place(externalMap, width - 94, 4, 88, 20);
        OrbitalControlUiTheme.place(status, 6, 4, width - 12, 20);
        int available = Math.max(1, Math.min(width - 12, height - 68));
        int visibleRadius = Math.clamp((available / 14 - 1) / 2, 1, RADIUS);
        int diameter = visibleRadius * 2 + 1;
        int cellSize = Math.max(1, available / diameter);
        int gridLeft = (width - cellSize * diameter) / 2;
        for (int index = 0; index < cells.size(); index++) {
            int offsetX = index % 7 - RADIUS;
            int offsetZ = index / 7 - RADIUS;
            cells.get(index).setDisplay(Math.abs(offsetX) <= visibleRadius && Math.abs(offsetZ) <= visibleRadius);
            OrbitalControlUiTheme.place(cells.get(index), gridLeft + (offsetX + visibleRadius) * cellSize,
                    28 + (offsetZ + visibleRadius) * cellSize, Math.max(1, cellSize - 1), Math.max(1, cellSize - 1));
        }
        int navigationLeft = Math.max(4, (width - 224) / 2);
        for (int index = 0; index < panButtons.size(); index++) {
            OrbitalControlUiTheme.place(panButtons.get(index), navigationLeft + index * 24, height - 40, 22, 20);
        }
        OrbitalControlUiTheme.place(recenter, navigationLeft + 96, height - 40, 64, 20);
        OrbitalControlUiTheme.place(refresh, navigationLeft + 164, height - 40, 60, 20);
        OrbitalControlUiTheme.place(legend, 6, height - 17, width - 12, 14);
    }

    public void setOperable(boolean operable) {
        this.operable = operable;
        refresh.setActive(operable);
        recenter.setActive(operable);
        provider.setActive(operable);
        panButtons.forEach(button -> button.setActive(operable));
        cells.forEach(button -> button.setActive(operable));
        externalMap.setActive(operable && provider.getValue() != null && !BUILTIN.equals(provider.getValue().id()));
    }

    public void updateProvider() {
        boolean external = provider.getValue() != null && !BUILTIN.equals(provider.getValue().id());
        externalMap.setDisplay(external);
        // Provider candidates arrive asynchronously; keep the button's active state in sync with the
        // latest provider instead of leaving the value from the previous operability update behind.
        externalMap.setActive(this.operable && external);
        provider.setDisplay(provider.getCandidates().size() > 1);
        status.setDisplay(provider.getCandidates().size() <= 1);
    }

    public void showCell(int index, @Nullable OrbitalMapTile tile, Component marker, boolean target, boolean affected) {
        if (initializedCells[index] && Objects.equals(displayedTiles[index], tile) &&
                displayedTargets[index] == target && displayedAreas[index] == affected) {
            return;
        }
        initializedCells[index] = true;
        displayedTiles[index] = tile;
        displayedTargets[index] = target;
        displayedAreas[index] = affected;
        Button cell = cells.get(index);
        int background = tile != null && tile.known() ? 0xFF000000 | tile.biomeColor() : 0xFFCAD9DD;
        int border = target ? 0xFFFFBC36 : affected ? 0xFFDC6277 : 0xFF6C929B;
        var texture = GuiTextureGroup.of(new ColorRectTexture(background), new ColorBorderTexture(target ? -2 : -1, border));
        cell.buttonStyle(style -> style.baseTexture(texture)
                .hoverTexture(GuiTextureGroup.of(new ColorRectTexture(background), new ColorBorderTexture(-2, 0xFFFFFFFF)))
                .pressedTexture(texture));
        cell.style(style -> style.backgroundTexture(texture));
        cell.setText(target ? Component.literal("+") : marker);
        cell.text.textStyle(style -> style.textColor(0xFFFFFFFF).textShadow(true));
        if (tile != null) {
            Component hint = Component.translatable(PREFIX + "workspace.cell_hint", tile.chunkX() * 16L + 8,
                    tile.chunkZ() * 16L + 8, tile.known() ? Integer.toString(tile.surfaceY()) : "?");
            cell.style(style -> style.tooltips(hint, Component.translatable(PREFIX + "workspace.map_hint")));
        } else {
            cell.style(style -> style.tooltips(Component.translatable(PREFIX + "fire_control.map.status")));
        }
    }

    public record MapProviderOption(ResourceLocation id, Component label) {}
}
