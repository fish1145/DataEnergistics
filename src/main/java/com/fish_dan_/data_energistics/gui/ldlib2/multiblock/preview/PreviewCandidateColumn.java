package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierOption;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ObjIntConsumer;

/**
 * Displays the non-selected candidates for the block currently picked in a structure preview.
 */
final class PreviewCandidateColumn extends UIElement {

    private static final int VISIBLE_ENTRY_COUNT = 6;
    private static final int ENTRY_WIDTH = 16;
    private static final int ENTRY_HEIGHT = 18;
    private static final SpriteTexture ENTRY_TEXTURE = SpriteTexture.of(
            "data_energistics:textures/guis/autobuild/replaceable_block_column.png")
            .setSprite(0, 0, ENTRY_WIDTH, ENTRY_HEIGHT);

    private final List<CandidateEntry> entries = new ArrayList<>(VISIBLE_ENTRY_COUNT);
    private final ObjIntConsumer<PreviewPredicateKey> candidateSelectionHandler;
    private final ObjIntConsumer<String> tierSelectionHandler;
    @Nullable
    private PreviewPredicateKey predicateKey;
    private List<CandidateChoice> choices = List.of();
    private int firstVisibleCandidate;

    PreviewCandidateColumn(@NotNull String id,
                           @NotNull ObjIntConsumer<PreviewPredicateKey> candidateSelectionHandler,
                           @NotNull ObjIntConsumer<String> tierSelectionHandler) {
        this.candidateSelectionHandler = candidateSelectionHandler;
        this.tierSelectionHandler = tierSelectionHandler;
        setId(id);
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(3)
                .top(24)
                .width(ENTRY_WIDTH)
                .height(VISIBLE_ENTRY_COUNT * ENTRY_HEIGHT));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        for (int index = 0; index < VISIBLE_ENTRY_COUNT; index++) {
            CandidateEntry entry = createEntry(id, index);
            this.entries.add(entry);
            addChild(entry.root());
        }
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        refreshEntries();
    }

    void refresh(@Nullable PreviewCellSnapshot selectedCell,
                 @NotNull List<PreviewTierDomain> tierDomains,
                 @NotNull Map<String, Integer> tierSelections) {
        PreviewPredicateKey nextKey = selectedCell == null ? null : selectedCell.predicate().key();
        if (!Objects.equals(this.predicateKey, nextKey)) {
            this.firstVisibleCandidate = 0;
        }
        this.predicateKey = nextKey;
        this.choices = choices(selectedCell, tierDomains, tierSelections);
        this.firstVisibleCandidate = Math.min(this.firstVisibleCandidate, maxFirstVisibleCandidate());
        refreshEntries();
    }

    private CandidateEntry createEntry(String idPrefix, int visibleIndex) {
        UIElement root = new UIElement();
        root.setId(idPrefix + "_entry_" + visibleIndex);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(visibleIndex * ENTRY_HEIGHT)
                .width(ENTRY_WIDTH)
                .height(ENTRY_HEIGHT));
        root.style(style -> style.backgroundTexture(ENTRY_TEXTURE));

        ItemSlot slot = new ItemSlot();
        slot.setId(root.getId() + "_slot");
        slot.setItem(ItemStack.EMPTY);
        slot.setAllowHitTest(false);
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(1)
                .width(ENTRY_WIDTH)
                .height(ENTRY_WIDTH));
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        root.addChild(slot);

        CandidateEntry entry = new CandidateEntry(root, slot);
        root.addEventListener(UIEvents.CLICK, event -> select(entry, event));
        root.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> addTooltip(entry, event));
        return entry;
    }

    private void select(CandidateEntry entry, UIEvent event) {
        CandidateChoice choice = entry.choice();
        if (event.button != 0 || choice == null) {
            return;
        }
        choice.selection().run();
        event.stopPropagation();
    }

    private void addTooltip(CandidateEntry entry, UIEvent event) {
        CandidateChoice choice = entry.choice();
        if (choice == null) {
            return;
        }
        event.hoverTooltips = new HoverTooltips(List.of(choice.name().copy()), null, null, null);
    }

    private void onMouseWheel(UIEvent event) {
        int maximum = maxFirstVisibleCandidate();
        if (maximum == 0 || event.deltaY == 0.0F) {
            return;
        }
        int direction = event.deltaY > 0.0F ? -1 : 1;
        int updated = Math.max(0, Math.min(maximum, this.firstVisibleCandidate + direction));
        if (updated != this.firstVisibleCandidate) {
            this.firstVisibleCandidate = updated;
            refreshEntries();
        }
        event.stopPropagation();
    }

    private void refreshEntries() {
        for (int visibleIndex = 0; visibleIndex < this.entries.size(); visibleIndex++) {
            int candidateOffset = this.firstVisibleCandidate + visibleIndex;
            CandidateEntry entry = this.entries.get(visibleIndex);
            if (candidateOffset >= this.choices.size()) {
                entry.clear();
                continue;
            }
            entry.show(this.choices.get(candidateOffset));
        }
    }

    private int maxFirstVisibleCandidate() {
        return Math.max(0, this.choices.size() - VISIBLE_ENTRY_COUNT);
    }

    private List<CandidateChoice> choices(@Nullable PreviewCellSnapshot selectedCell,
                                          List<PreviewTierDomain> tierDomains,
                                          Map<String, Integer> tierSelections) {
        if (selectedCell == null) {
            return List.of();
        }

        PreviewCandidate selectedCandidate = selectedCell.predicate().selectedCandidate().orElse(null);
        ResourceLocation selectedBlockId = selectedCandidate == null || selectedCandidate.state().isEmpty() ? null : BuiltInRegistries.BLOCK.getKey(selectedCandidate.state().orElseThrow().getBlock());
        PreviewTierDomain matchingDomain = null;
        if (selectedBlockId != null) {
            for (PreviewTierDomain domain : tierDomains) {
                if (!domain.containsBlock(selectedBlockId)) {
                    continue;
                }
                if (matchingDomain != null) {
                    throw new IllegalStateException("Selected preview block belongs to multiple tier domains");
                }
                matchingDomain = domain;
            }
        }
        if (matchingDomain != null) {
            return tierChoices(matchingDomain, tierSelections, selectedBlockId);
        }

        List<CandidateChoice> choices = new ArrayList<>();
        int selectedIndex = selectedCell.predicate().selectedCandidateIndex();
        for (int candidateIndex = 0; candidateIndex < selectedCell.predicate().candidates().size(); candidateIndex++) {
            if (candidateIndex != selectedIndex) {
                int choiceIndex = candidateIndex;
                choices.add(new CandidateChoice(
                        selectedCell.predicate().candidates().get(candidateIndex),
                        () -> this.candidateSelectionHandler.accept(selectedCell.predicate().key(), choiceIndex)));
            }
        }
        return List.copyOf(choices);
    }

    private List<CandidateChoice> tierChoices(PreviewTierDomain domain,
                                              Map<String, Integer> tierSelections,
                                              ResourceLocation selectedBlockId) {
        Integer selectedValue = tierSelections.get(domain.id());
        if (selectedValue == null) {
            throw new IllegalStateException("Selected preview tier domain is absent from the active selection: " +
                    domain.id());
        }
        if (!domain.option(selectedValue).blockId().equals(selectedBlockId)) {
            throw new IllegalStateException("Selected preview block does not match tier domain " + domain.id());
        }

        List<CandidateChoice> choices = new ArrayList<>();
        for (PreviewTierOption option : domain.options()) {
            if (option.value() == selectedValue) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.getOptional(option.blockId())
                    .orElseThrow(() -> new IllegalStateException("Unknown preview tier block: " + option.blockId()));
            ItemStack icon = block.asItem().getDefaultInstance();
            if (icon.isEmpty()) {
                throw new IllegalStateException("Preview tier block has no item form: " + option.blockId());
            }
            choices.add(new CandidateChoice(
                    icon,
                    option.label(),
                    () -> this.tierSelectionHandler.accept(domain.id(), option.value())));
        }
        return List.copyOf(choices);
    }

    private static final class CandidateEntry {

        private final UIElement root;
        private final ItemSlot slot;
        @Nullable
        private CandidateChoice choice;

        private CandidateEntry(UIElement root, ItemSlot slot) {
            this.root = root;
            this.slot = slot;
        }

        private UIElement root() {
            return this.root;
        }

        @Nullable
        private CandidateChoice choice() {
            return this.choice;
        }

        private void show(CandidateChoice choice) {
            this.choice = choice;
            this.slot.setItem(choice.icon().copy());
            this.root.setVisible(true);
        }

        private void clear() {
            this.choice = null;
            this.slot.setItem(ItemStack.EMPTY);
            this.root.setVisible(false);
        }
    }

    private record CandidateChoice(ItemStack icon, Component name, Runnable selection) {

        private CandidateChoice {
            icon = icon.copy();
            name = name.copy();
        }

        private CandidateChoice(PreviewCandidate candidate, Runnable selection) {
            this(
                    candidate.placementKey().map(key -> key.toStack(1)).orElse(ItemStack.EMPTY),
                    candidate.placementKey()
                            .<Component>map(key -> key.getDisplayName().copy())
                            .orElseGet(() -> Component.translatable("block.minecraft.air")),
                    selection);
        }
    }
}
