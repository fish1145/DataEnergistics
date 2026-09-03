package com.fish_dan_.data_energistics.client.crafting.tree.tooltip;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmCyclePalette;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRenderer;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Cycle;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import appeng.api.client.AEKeyRendering;
import appeng.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Native confirmation-style hover details, scoped to the hovered node and its selected related cycle. */
public final class CraftingPlanNodeTooltip {

    private static final String CYCLE_TEXT = "gui.data_energistics.trinity_planning.cycle.";
    private final CraftingPlanGraph graph;
    private @Nullable PlacedNode hovered;
    private List<Cycle> related = List.of();
    private List<Component> details = List.of();
    private int selectedCycle;

    public CraftingPlanNodeTooltip(CraftingPlanGraph graph) {
        this.graph = graph;
    }

    public void render(GuiGraphics graphics, Font font, PlacedNode node, int mouseX, int mouseY) {
        if (this.hovered == null || this.hovered.id() != node.id()) {
            this.hovered = node;
            this.related = this.graph.cycles().stream().filter(cycle -> cycle.nodeIds().contains(node.id()))
                    .sorted(Comparator.comparingInt(Cycle::ordinal)).toList();
            this.selectedCycle = 0;
            this.details = details(node);
        }
        List<Component> lines = new ObjectArrayList<>(AEKeyRendering.getTooltip(CraftingPlanGraphRenderer.key(node)));
        lines.addAll(this.details);
        int maxWidth = Math.max(40, graphics.guiWidth() / 2 - 40);
        List<FormattedCharSequence> wrapped = new ObjectArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            MutableComponent line = lines.get(index).copy();
            if (index == 0) line.withStyle(ChatFormatting.WHITE);
            else if (line.getStyle().getColor() == null) line.withStyle(ChatFormatting.GRAY);
            wrapped.addAll(ComponentRenderUtils.wrapComponents(line, maxWidth, font));
        }
        graphics.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    public void hide() {
        this.hovered = null;
        this.related = List.of();
        this.details = List.of();
    }

    public boolean keyPressed(int keyCode, int scanCode) {
        if (this.hovered == null || this.related.size() <= 1) return false;
        if (DEKeyMappings.PREVIOUS_TRINITY_CYCLE.matches(keyCode, scanCode)) {
            this.selectedCycle = Math.floorMod(this.selectedCycle - 1, this.related.size());
        } else if (DEKeyMappings.NEXT_TRINITY_CYCLE.matches(keyCode, scanCode)) {
            this.selectedCycle = (this.selectedCycle + 1) % this.related.size();
        } else {
            return false;
        }
        this.details = details(this.hovered);
        return true;
    }

    private List<Component> details(PlacedNode node) {
        List<Component> lines = new ObjectArrayList<>();
        if (node.viewNode().sourceNode() instanceof Material material) {
            if (material.stored().signum() > 0) lines.add(GuiText.FromStorage.text(TrinityAmountFormatter.format(material.stored())));
            if (material.missing().signum() > 0) lines.add(GuiText.Missing.text(TrinityAmountFormatter.format(material.missing())));
            if (material.crafting().signum() > 0) lines.add(GuiText.ToCraft.text(TrinityAmountFormatter.format(material.crafting())));
            if (material.stored().signum() > 0) {
                String percentage = material.inventoryUsageBasisPoints() == 0 ? "<0.01%" : BigDecimal.valueOf(material.inventoryUsageBasisPoints(), 2).stripTrailingZeros().toPlainString() + "%";
                lines.add(cycleText("inventory_usage", percentage).withStyle(ChatFormatting.GRAY));
            }
            if (material.missing().signum() > 0) {
                lines.add(cycleText("shortage_required", TrinityAmountFormatter.format(material.required())).withStyle(ChatFormatting.RED));
                lines.add(cycleText("shortage_available", TrinityAmountFormatter.format(material.stored())).withStyle(ChatFormatting.RED));
                lines.add(cycleText("shortage_missing", TrinityAmountFormatter.format(material.missing())).withStyle(ChatFormatting.RED));
            }
            if (material.unresolved().signum() > 0) {
                lines.add(cycleText("unresolved_demand", TrinityAmountFormatter.format(material.unresolved())).withStyle(ChatFormatting.YELLOW));
            }
        }
        @Nullable
        Process process = node.viewNode().sourceNode() instanceof Process value ? value : node.embeddedProcessId() == null ? null : (Process) this.graph.node(node.embeddedProcessId());
        if (process != null) {
            lines.add(text("process_details", process.stageIndex(), process.variantOrdinal()).withStyle(ChatFormatting.GRAY));
            lines.add(text("executions", TrinityAmountFormatter.format(process.executions())).withStyle(ChatFormatting.GRAY));
            for (var edge : this.graph.edges()) {
                if (edge.source() != process.id() && edge.target() != process.id()) continue;
                int materialId = edge.source() == process.id() ? edge.target() : edge.source();
                Material material = (Material) this.graph.node(materialId);
                lines.add(text("edge_details", text("role." + edge.role().name().toLowerCase(Locale.ROOT)),
                        material.key().getDisplayName(), TrinityAmountFormatter.format(edge.amount())).withStyle(ChatFormatting.GRAY));
            }
        }
        if (!this.related.isEmpty()) appendCycle(lines, node, this.related.get(this.selectedCycle));
        return List.copyOf(lines);
    }

    private void appendCycle(List<Component> lines, PlacedNode node, Cycle cycle) {
        lines.add(cycleText("current_related", this.selectedCycle + 1, this.related.size(), cycle.ordinal())
                .withStyle(style -> style.withColor(TrinityCraftConfirmCyclePalette.rgb(cycle.ordinal()))));
        if (this.related.size() > 1) {
            lines.add(cycleText("switch_hint", DEKeyMappings.PREVIOUS_TRINITY_CYCLE.getTranslatedKeyMessage(),
                    DEKeyMappings.NEXT_TRINITY_CYCLE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
        }
        var key = CraftingPlanGraphRenderer.key(node);
        boolean input = false;
        boolean output = false;
        for (var edge : this.graph.edges()) {
            if (edge.role() == Role.DIAGNOSTIC) continue;
            int materialId = edge.role() == Role.INPUT ? edge.target() : edge.source();
            int processId = edge.role() == Role.INPUT ? edge.source() : edge.target();
            if (((Material) this.graph.node(materialId)).key().equals(key) && ((Process) this.graph.node(processId)).cycleIds().contains(cycle.id())) {
                if (edge.role() == Role.INPUT) input = true;
                else output = true;
            }
        }
        if (input || output) lines.add(cycleText(input && output ? "role_input_output" : input ? "role_input" : "role_output")
                .withStyle(ChatFormatting.GRAY));
        BigInteger seed = cycle.minimumSeed().getOrDefault(key, BigInteger.ZERO);
        BigInteger net = cycle.netChange().getOrDefault(key, BigInteger.ZERO);
        if (seed.signum() > 0) lines.add(cycleText("minimum_seed", TrinityAmountFormatter.format(seed)).withStyle(ChatFormatting.GRAY));
        if (net.signum() != 0) {
            lines.add(cycleText(net.signum() < 0 ? "net_consumed" : "net_produced", TrinityAmountFormatter.format(net.abs()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (input && output) {
            lines.add(cycleText("reused").withStyle(ChatFormatting.GRAY));
        }
        lines.add(cycleText("repetitions", TrinityAmountFormatter.format(cycle.repetitions())).withStyle(ChatFormatting.GRAY));
        lines.add(cycleText("stage_count", TrinityAmountFormatter.format(cycle.stageOrder().size())).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent cycleText(String suffix, Object... arguments) {
        return Component.translatable(CYCLE_TEXT + suffix, arguments);
    }

    private static MutableComponent text(String suffix, Object... arguments) {
        return Component.translatable("gui.data_energistics.plan_tree." + suffix, arguments);
    }
}
