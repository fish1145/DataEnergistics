package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.worldgen.meteorite.MeteoriteStructure;
import com.fish_dan_.data_energistics.worldgen.meteorite.MeteoriteStructurePiece;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DEStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Data_Energistics.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Data_Energistics.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<?>> METEORITE = STRUCTURE_TYPES.register("meteorite", () -> MeteoriteStructure.TYPE);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> METEORITE_PIECE = STRUCTURE_PIECES.register("meteorite_piece", () -> MeteoriteStructurePiece.TYPE);

    private DEStructures() {}

    public static void register(IEventBus modEventBus) {
        STRUCTURE_PIECES.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
    }
}
