package com.fish_dan_.data_energistics.item.terminal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record UniversalTerminalItemData(String activeTerminal, List<TerminalEntryData> terminals,
                                        CompoundTag terminalData) {

    private static final String TAG_TERMINALS = "installed_terminals";
    private static final String TAG_NAME = "name";
    private static final String TAG_STACK = "stack";
    private static final String TAG_ACTIVE = "active_terminal";

    public static final UniversalTerminalItemData EMPTY = new UniversalTerminalItemData("", List.of(), new CompoundTag());

    public static final Codec<UniversalTerminalItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("active_terminal", "").forGetter(UniversalTerminalItemData::activeTerminal),
            TerminalEntryData.CODEC.listOf().optionalFieldOf("installed_terminals", List.of()).forGetter(UniversalTerminalItemData::terminals),
            CompoundTag.CODEC.optionalFieldOf("terminal_data", new CompoundTag()).forGetter(UniversalTerminalItemData::terminalData))
            .apply(instance, UniversalTerminalItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, UniversalTerminalItemData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public UniversalTerminalItemData {
        activeTerminal = activeTerminal == null ? "" : activeTerminal;
        terminals = List.copyOf(terminals);
        terminalData = terminalData.copy();
    }

    public UniversalTerminalItemData withActiveTerminal(String activeTerminal) {
        return new UniversalTerminalItemData(activeTerminal, this.terminals, this.terminalData);
    }

    public UniversalTerminalItemData withTerminals(List<TerminalEntryData> terminals) {
        return new UniversalTerminalItemData(this.activeTerminal, terminals, this.terminalData);
    }

    public UniversalTerminalItemData withTerminalData(CompoundTag terminalData) {
        return new UniversalTerminalItemData(this.activeTerminal, this.terminals, terminalData);
    }

    public CompoundTag toLegacyTag(HolderLookup.Provider registries) {
        CompoundTag tag = this.terminalData.copy();
        if (!this.activeTerminal.isEmpty()) {
            tag.putString(TAG_ACTIVE, this.activeTerminal);
        }
        if (!this.terminals.isEmpty()) {
            ListTag terminalList = new ListTag();
            for (TerminalEntryData entry : this.terminals) {
                if (entry.name().isEmpty() || entry.stack().isEmpty()) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString(TAG_NAME, entry.name());
                entryTag.put(TAG_STACK, entry.stack().saveOptional(registries));
                terminalList.add(entryTag);
            }
            tag.put(TAG_TERMINALS, terminalList);
        }
        return tag;
    }

    public static UniversalTerminalItemData fromLegacyTag(CompoundTag tag, HolderLookup.Provider registries) {
        List<TerminalEntryData> entries = new ArrayList<>();
        ListTag terminalList = tag.getList(TAG_TERMINALS, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < terminalList.size(); i++) {
            CompoundTag entryTag = terminalList.getCompound(i);
            String name = entryTag.getString(TAG_NAME);
            ItemStack stack = ItemStack.parseOptional(registries, entryTag.getCompound(TAG_STACK));
            if (!name.isEmpty() && !stack.isEmpty()) {
                entries.add(new TerminalEntryData(name, stack));
            }
        }
        return new UniversalTerminalItemData(tag.getString(TAG_ACTIVE), entries, tag.copy());
    }

    public record TerminalEntryData(String name, ItemStack stack) {

        public static final Codec<TerminalEntryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(TerminalEntryData::name),
                ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(TerminalEntryData::stack))
                .apply(instance, TerminalEntryData::new));

        public TerminalEntryData {
            name = name == null ? "" : name;
            stack = stack.copyWithCount(1);
        }
    }
}
