package com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import org.jspecify.annotations.Nullable;

/** Server-owned life state: deliberately neither serialized, synchronized nor copied on player death. */
public final class OrbitalErasureAttachments {

    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Data_Energistics.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<OrbitalErasureState>> LIFE = TYPES.register(
            "orbital_erasure_life", () -> AttachmentType.builder(OrbitalErasureState::new).build());

    private OrbitalErasureAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static @Nullable OrbitalErasureState find(Entity entity) {
        return entity.hasData(LIFE) ? entity.getData(LIFE) : null;
    }

    public static boolean blocksRecovery(Entity entity) {
        OrbitalErasureState state = find(entity);
        return state != null && state.blocksRecovery();
    }

    static OrbitalErasureState begin(Entity entity) {
        return entity.getData(LIFE);
    }

    /** Explicit respawn/failure boundary; this does not alter any equipment or third-party data. */
    public static void clear(Entity entity) {
        entity.removeData(LIFE);
    }
}
