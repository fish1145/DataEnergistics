package com.fish_dan_.data_energistics.common.pattern;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternTerminalPartition;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves identities from Minecraft and AE2 registries plus live provider hosts.
 */
final class RegistryBackedProviderIdentityResolver implements ProviderIdentityResolver {

    /**
     * Extracts a dimension ID from a physical host; injectable for direct resolver tests.
     */
    private final Function<BlockEntity, ResourceLocation> dimensionIdResolver;
    /**
     * Extracts a registered item ID from a mounted part; injectable for direct resolver tests.
     */
    private final Function<IPart, ResourceLocation> partItemIdResolver;

    /**
     * Creates the live resolver used by production callers.
     */
    RegistryBackedProviderIdentityResolver() {
        this(RegistryBackedProviderIdentityResolver::resolveDimensionId, RegistryBackedProviderIdentityResolver::resolvePartItemId);
    }

    /**
     * Creates a resolver with explicit physical metadata sources for logic tests.
     *
     * @param dimensionIdResolver physical-host dimension lookup
     * @param partItemIdResolver  mounted-part item lookup
     */
    RegistryBackedProviderIdentityResolver(Function<BlockEntity, ResourceLocation> dimensionIdResolver,
                                           Function<IPart, ResourceLocation> partItemIdResolver) {
        this.dimensionIdResolver = dimensionIdResolver;
        this.partItemIdResolver = partItemIdResolver;
    }

    /**
     * Applies dedicated Trinity, physical part, physical block and virtual fallback precedence.
     */
    @Override
    public ProviderIdentity resolve(PatternContainer provider) {
        if (provider instanceof TrinityPatternTerminalPartition partition) {
            TrinityPatternTerminalPartition.PartitionKey key = partition.key();
            return new ProviderIdentity.Trinity(key.hostId(), key.coreId(), key.partitionIndex());
        }
        if (provider instanceof PatternProviderLogicHost logicHost) {
            BlockEntity blockEntity = requireBlockEntity(logicHost);
            if (provider instanceof IPart part) {
                return resolvePart(part, blockEntity);
            }
            return resolveBlock(blockEntity);
        }
        if (provider instanceof BlockEntity blockEntity) {
            return resolveBlock(blockEntity);
        }
        return resolveVirtual(provider);
    }

    /**
     * Builds a physical block identity from immutable world and registry metadata.
     */
    private ProviderIdentity.Block resolveBlock(BlockEntity blockEntity) {
        ResourceLocation blockEntityTypeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        if (blockEntityTypeId == null) {
            throw new IllegalStateException("Pattern provider block entity type is not registered: " +
                    blockEntity.getType());
        }
        return new ProviderIdentity.Block(
                this.dimensionIdResolver.apply(blockEntity),
                blockEntity.getBlockPos(),
                blockEntityTypeId);
    }

    /**
     * Builds a multipart identity after proving the part occupies exactly one host slot.
     */
    private ProviderIdentity.Part resolvePart(IPart part, BlockEntity blockEntity) {
        if (!(blockEntity instanceof IPartHost partHost)) {
            throw new IllegalStateException("Pattern provider part is not hosted by an AE2 part host at " +
                    blockEntity.getBlockPos());
        }
        return new ProviderIdentity.Part(
                this.dimensionIdResolver.apply(blockEntity),
                blockEntity.getBlockPos(),
                resolveMount(partHost, part),
                this.partItemIdResolver.apply(part));
    }

    /**
     * Encodes the terminal group structurally when no physical or dedicated persistent key is available.
     */
    private static ProviderIdentity.Virtual resolveVirtual(PatternContainer provider) {
        var group = Objects.requireNonNull(
                provider.getTerminalGroup(),
                "Pattern provider terminal group");
        return ProviderIdentityResolver.virtualIdentity(
                group.icon() == null ? null : group.icon().getId(),
                Objects.requireNonNull(group.name(), "Pattern provider terminal group name"));
    }

    /**
     * Resolves AE2's nullable center slot and six side slots without reading private part fields.
     */
    private static ProviderIdentity.Mount resolveMount(IPartHost partHost, IPart part) {
        ProviderIdentity.Mount resolved = null;
        if (partHost.getPart(null) == part) {
            resolved = ProviderIdentity.Mount.CENTER;
        }
        for (Direction direction : Direction.values()) {
            if (partHost.getPart(direction) != part) {
                continue;
            }
            if (resolved != null) {
                throw new IllegalStateException("Pattern provider part occupies multiple slots on the same host");
            }
            resolved = ProviderIdentity.Mount.fromDirection(direction);
        }
        if (resolved == null) {
            throw new IllegalStateException("Pattern provider part is not mounted on its reported host");
        }
        return resolved;
    }

    /**
     * Rejects incomplete host implementations before location resolution.
     */
    private static BlockEntity requireBlockEntity(PatternProviderLogicHost logicHost) {
        return Objects.requireNonNull(logicHost.getBlockEntity(), "Pattern provider logic host block entity");
    }

    /**
     * Reads the dimension of a live physical provider and rejects detached block entities.
     */
    private static ResourceLocation resolveDimensionId(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            throw new IllegalStateException("Pattern provider block entity is not attached to a level at " +
                    blockEntity.getBlockPos());
        }
        return level.dimension().location();
    }

    /**
     * Reads the registered item that AE2 uses to persist and reconstruct one part.
     */
    private static ResourceLocation resolvePartItemId(IPart part) {
        return IPartItem.getId(part.getPartItem());
    }
}
