package com.fish_dan_.data_energistics.integration.ftbultimine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.PoweredPickaxeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import dev.ftb.mods.ftbultimine.api.blockbreaking.BlockBreakHandler;
import dev.ftb.mods.ftbultimine.api.blockbreaking.RegisterBlockBreakHandlerEvent;
import dev.ftb.mods.ftbultimine.api.shape.Shape;

public final class DataCrystalPickaxeFtbUltimineCompat {

    private DataCrystalPickaxeFtbUltimineCompat() {}

    public static void init() {
        RegisterBlockBreakHandlerEvent.REGISTER.register(registry -> registry.registerHandler(new DuplicateOreBlockBreakHandler()));
    }

    private static final class DuplicateOreBlockBreakHandler implements BlockBreakHandler {

        @Override
        public Result breakBlock(Player player, BlockPos pos, BlockState state, Shape shape, BlockHitResult hitResult) {
            boolean duplicated = PoweredPickaxeItem.tryDropDuplicateOreLootFromFtbUltimine(player, pos, state);
            Data_Energistics.LOGGER.debug(
                    "Data crystal pickaxe FTB Ultimine block handler player={} pos={} block={} duplicated={}",
                    player.getName().getString(),
                    pos,
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    duplicated);
            return Result.PASS;
        }

        @Override
        public void postBreak(Player player) {
            Data_Energistics.LOGGER.debug("Data crystal pickaxe FTB Ultimine postBreak player={}", player.getName().getString());
            PoweredPickaxeItem.clearFtbUltimineDuplicateMarkers();
        }
    }
}
