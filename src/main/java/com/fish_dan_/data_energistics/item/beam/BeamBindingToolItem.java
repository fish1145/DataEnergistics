package com.fish_dan_.data_energistics.item.beam;

import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Server-authoritative, dimension-aware selection for symmetric omni bindings. */
public final class BeamBindingToolItem extends Item {

    public BeamBindingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        var pos = context.getClickedPos();
        if (player == null || !(level.getBlockEntity(pos) instanceof BeamEndpoint target) ||
                target.beamState().kind() != BeamDeviceKind.OMNI) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            stack.set(DEDataComponents.BEAM_BINDING_SOURCE, GlobalPos.of(level.dimension(), pos));
            message(player, "selected");
            return InteractionResult.CONSUME;
        }
        GlobalPos selected = stack.get(DEDataComponents.BEAM_BINDING_SOURCE);
        if (selected == null) {
            return fail(player, "no_source");
        }
        if (!selected.dimension().equals(level.dimension())) {
            return fail(player, "wrong_dimension");
        }
        if (!level.hasChunkAt(selected.pos())) {
            return fail(player, "unloaded");
        }
        if (!(level.getBlockEntity(selected.pos()) instanceof BeamEndpoint source) ||
                source.beamState().kind() != BeamDeviceKind.OMNI) {
            stack.remove(DEDataComponents.BEAM_BINDING_SOURCE);
            return fail(player, "missing_source");
        }
        if (!level.mayInteract(player, selected.pos()) || source == target) {
            return fail(player, "invalid_target");
        }
        if (!source.beamState().boundTo(target) && !source.beamState().withinRange(target)) {
            return fail(player, "out_of_range");
        }
        boolean added = source.beamState().toggleBinding(target);
        message(player, added ? "linked" : "unlinked");
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            stack.remove(DEDataComponents.BEAM_BINDING_SOURCE);
            message(player, "cleared");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.data_energistics.beam_binding_tool"));
        GlobalPos source = stack.get(DEDataComponents.BEAM_BINDING_SOURCE);
        if (source != null) {
            lines.add(Component.translatable("tooltip.data_energistics.beam_binding_source", source.dimension().location().toString(),
                    source.pos().getX(), source.pos().getY(), source.pos().getZ()));
        }
    }

    private static InteractionResult fail(Player player, String key) {
        message(player, key);
        return InteractionResult.FAIL;
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.data_energistics.beam." + key), true);
    }
}
