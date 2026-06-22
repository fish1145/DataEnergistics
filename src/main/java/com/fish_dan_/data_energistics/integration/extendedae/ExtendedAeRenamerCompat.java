package com.fish_dan_.data_energistics.integration.extendedae;

import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;

public final class ExtendedAeRenamerCompat {

    private static final ResourceLocation RENAMER_MENU_ID = ResourceLocation.fromNamespaceAndPath("extendedae", "renamer");

    private ExtendedAeRenamerCompat() {}

    public static InteractionResult tryOpenRenamer(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!stack.is(ModItems.DATA_CRYSTAL_CUTTING_KNIFE.get())) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        MenuType<?> menuType = BuiltInRegistries.MENU.getOptional(RENAMER_MENU_ID).orElse(null);
        if (menuType == null) {
            return InteractionResult.PASS;
        }

        Object renameTarget = findRenameTarget(context);
        if (renameTarget == null) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean opened = false;
        if (renameTarget instanceof AEBaseBlockEntity blockEntity) {
            opened = MenuOpener.open(menuType, player, MenuLocators.forBlockEntity(blockEntity));
        } else if (renameTarget instanceof AEBasePart part && part.getHost() != null) {
            opened = MenuOpener.open(menuType, player, MenuLocators.forPart(part));
        }
        return opened ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Nullable
    private static Object findRenameTarget(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
        if (blockEntity instanceof AEBaseBlockEntity aeBaseBlockEntity) {
            return aeBaseBlockEntity;
        }

        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            Vec3 clicked = context.getClickLocation();
            Vec3 localHit = new Vec3(clicked.x - pos.getX(), clicked.y - pos.getY(), clicked.z - pos.getZ());
            var selectedPart = cableBus.getCableBus().selectPartLocal(localHit);
            if (selectedPart != null && selectedPart.part instanceof AEBasePart aeBasePart) {
                return aeBasePart;
            }
        }

        return null;
    }
}
