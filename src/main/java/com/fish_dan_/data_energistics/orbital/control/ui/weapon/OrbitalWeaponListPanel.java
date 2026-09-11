package com.fish_dan_.data_energistics.orbital.control.ui.weapon;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPresentation;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiTheme.Tone;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Stable weapon rows: status updates do not replace the tree, input focus or scroll position. */
public final class OrbitalWeaponListPanel {

    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.workspace.";
    public final UIElement root = new UIElement();
    public final TextField search = OrbitalControlUiTheme.textInput("orbital_weapon_search", "", 4, 22, 120);
    private final Label count = OrbitalControlUiTheme.label("orbital_weapon_count", Component.empty(),
            4, 2, 120, 18, OrbitalControlUiTheme.ACCENT_TEXT, 9, TextWrap.HOVER_ROLL);
    private final ScrollerView list = OrbitalControlUiTheme.scrollPanel("orbital_weapon_list");
    private final Object2ObjectLinkedOpenHashMap<UUID, Button> rows = new Object2ObjectLinkedOpenHashMap<>();
    private List<UUID> order = List.of();
    private List<WeaponEntry> entries = List.of();
    private final TextField name = OrbitalControlUiTheme.textInput("orbital_weapon_name", "", 4, 0, 120);
    private final Button rename = OrbitalControlUiTheme.button("orbital_weapon_rename",
            Component.translatable(PREFIX + "rename"), 4, 0, 120, 20, Tone.ACCENT);
    private @Nullable BiConsumer<UUID, String> renaming;
    private @Nullable UUID selectedId;
    private String persistedName = "";
    private boolean owner;
    private @Nullable Consumer<UUID> selection;

    public OrbitalWeaponListPanel() {
        root.setId("orbital_weapon_panel");
        OrbitalControlUiTheme.stylePanel(root, Tone.PANEL_ALT);
        search.style(style -> style.tooltips(Component.translatable(PREFIX + "search_hint")));
        search.textFieldStyle(style -> style.placeholder(Component.translatable(PREFIX + "search_placeholder")));
        search.registerValueListener(ignored -> filter());
        list.viewContainer.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
        name.textFieldStyle(style -> style.placeholder(Component.translatable(PREFIX + "name_placeholder")));
        name.style(style -> style.tooltips(Component.translatable(PREFIX + "rename_hint", OrbitalWeaponRecord.MAX_NAME_LENGTH)));
        name.setTextValidator(value -> {
            try {
                OrbitalWeaponRecord.normalizeName(value);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        });
        name.registerValueListener(ignored -> updateRename());
        rename.setOnClick(ignored -> {
            if (owner && selectedId != null && renaming != null) {
                renaming.accept(selectedId, name.getRawText());
            }
        });
        name.setActive(false);
        rename.setActive(false);
        root.addChildren(count, search, list, name, rename);
    }

    public void setSelectionListener(Consumer<UUID> selection) {
        this.selection = selection;
    }

    public void setRenameListener(BiConsumer<UUID, String> renaming) {
        this.renaming = renaming;
    }

    public void resize(int width, int height) {
        OrbitalControlUiTheme.place(count, 4, 2, width - 8, 18);
        OrbitalControlUiTheme.place(search, 4, 22, width - 8, 20);
        OrbitalControlUiTheme.place(list, 2, 46, width - 4, Math.max(1, height - 100));
        OrbitalControlUiTheme.place(name, 4, height - 50, width - 8, 20);
        OrbitalControlUiTheme.place(rename, 4, height - 26, width - 8, 20);
    }

    public void apply(OrbitalControlTerminalSnapshot snapshot) {
        count.setValue(OrbitalControlPresentation.selectorPosition(snapshot));
        entries = snapshot.weapons();
        WeaponEntry selectedWeapon = snapshot.selectedWeapon().orElse(null);
        String nextName = selectedWeapon == null ? "" : selectedWeapon.customName();
        if (!Objects.equals(selectedId, snapshot.selectedWeaponId()) || !persistedName.equals(nextName)) {
            selectedId = snapshot.selectedWeaponId();
            persistedName = nextName;
            name.setText(nextName, false);
        }
        owner = selectedWeapon != null && selectedWeapon.owner();
        name.setActive(owner);
        updateRename();
        List<UUID> nextOrder = snapshot.weapons().stream().map(WeaponEntry::weaponId).toList();
        if (!order.equals(nextOrder)) {
            list.viewContainer.clearAllChildren();
            rows.clear();
            order = nextOrder;
            for (UUID id : order) {
                Button row = OrbitalControlUiTheme.button("orbital_weapon_" + id, Component.empty(), 0, 0, 116, 36, Tone.PANEL);
                row.layout(layout -> layout.positionType(TaffyPosition.RELATIVE).widthPercent(100).height(36).flexShrink(0));
                row.setOnClick(ignored -> {
                    if (selection != null) {
                        selection.accept(id);
                    }
                });
                rows.put(id, row);
                list.addScrollViewChild(row);
            }
            filter();
        }
        for (WeaponEntry weapon : snapshot.weapons()) {
            Button row = rows.get(weapon.weaponId());
            boolean selected = weapon.weaponId().equals(snapshot.selectedWeaponId());
            Component text = Component.literal((selected ? "> " : "") + OrbitalControlPresentation.weaponName(weapon))
                    .append("\n").append(OrbitalControlPresentation.weaponState(weapon));
            row.setText(text);
            row.text.textStyle(style -> style.textWrap(TextWrap.WRAP).fontSize(9));
            row.style(style -> style.tooltips(Component.literal(OrbitalControlPresentation.weaponName(weapon)),
                    Component.literal(weapon.weaponId().toString()),
                    OrbitalControlPresentation.identity(weapon), OrbitalControlPresentation.celestialEnergy(weapon),
                    OrbitalControlPresentation.aeEnergy(weapon)));
            OrbitalControlUiTheme.styleButton(row, selected ? Tone.ACCENT : Tone.PANEL);
        }
        filter();
    }

    private void filter() {
        String query = search.getRawText().strip().toLowerCase(Locale.ROOT);
        for (WeaponEntry entry : entries) {
            Button row = rows.get(entry.weaponId());
            if (row != null) {
                row.setDisplay(entry.weaponId().toString().contains(query) || entry.customName().toLowerCase(Locale.ROOT).contains(query) || entry.ownerName().toLowerCase(Locale.ROOT).contains(query));
            }
        }
    }

    private void updateRename() {
        rename.setActive(owner && !name.getRawText().strip().equals(persistedName));
    }
}
