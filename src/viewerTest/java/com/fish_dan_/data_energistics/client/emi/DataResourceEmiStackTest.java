package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.ae2.DataKeyType;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.ids.AEComponents;
import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.tooltip.RemainderTooltipComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataResourceEmiStackTest {

    private static final AEKeyRenderHandler<DataKey> TEST_DATA_KEY_RENDERER = new TestDataKeyRendererImpl();
    private static final AEKeyRenderHandler<TestCustomKey> TEST_CUSTOM_KEY_RENDERER = new TestCustomKeyRendererImpl();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        if (ModList.get() == null) {
            ModList.of(List.of(), List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        AEKeyRendering.register(DataKeyType.TYPE, DataKey.class, TEST_DATA_KEY_RENDERER);
        AEKeyRendering.register(TestCustomKeyType.INSTANCE, TestCustomKey.class, TEST_CUSTOM_KEY_RENDERER);
        assertTrue(EmiStackConverters.register(DataResourceEmiStackConverter.INSTANCE));
        assertTrue(EmiStackConverters.register(GenericAeKeyEmiStackConverter.INSTANCE));
        assertTrue(EmiStackConverters.register(new TestSpecializedConverterImpl()));
    }

    private static final class TestDataKeyRendererImpl implements AEKeyRenderHandler<DataKey> {

        @Override
        public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, DataKey key) {
            throw new UnsupportedOperationException("The tooltip test must not render Data keys");
        }

        @Override
        public void drawOnBlockFace(
                                    PoseStack poseStack,
                                    MultiBufferSource buffers,
                                    DataKey key,
                                    float scale,
                                    int combinedLight,
                                    Level level) {
            throw new UnsupportedOperationException("The tooltip test must not render Data keys");
        }

        @Override
        public Component getDisplayName(DataKey key) {
            return key.getDisplayName();
        }

        @Override
        public List<Component> getTooltip(DataKey key) {
            return List.of(getDisplayName(key));
        }
    }

    private static final class TestCustomKeyRendererImpl implements AEKeyRenderHandler<TestCustomKey> {

        @Override
        public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, TestCustomKey key) {
            throw new UnsupportedOperationException("The tooltip test must not render custom keys");
        }

        @Override
        public void drawOnBlockFace(
                                    PoseStack poseStack,
                                    MultiBufferSource buffers,
                                    TestCustomKey key,
                                    float scale,
                                    int combinedLight,
                                    Level level) {
            throw new UnsupportedOperationException("The tooltip test must not render custom keys");
        }

        @Override
        public Component getDisplayName(TestCustomKey key) {
            return key.getDisplayName();
        }

        @Override
        public List<Component> getTooltip(TestCustomKey key) {
            return List.of(getDisplayName(key));
        }
    }

    @Test
    void identifiesOnlyTheTwoDataResourceKinds() {
        DataResourceEmiStack data = new DataResourceEmiStack(DataResourceEmiKey.DATA, 17L);
        DataResourceEmiStack dataFlow = new DataResourceEmiStack(DataResourceEmiKey.DATA_FLOW, 17L);

        assertEquals(DataResourceEmiKey.DATA, data.getKey());
        assertEquals(DataKey.ID, data.getId());
        assertEquals(DataKey.of().getDisplayName(), data.getName());
        assertEquals(DataComponentPatch.EMPTY, data.getComponentChanges());
        assertFalse(data.isEmpty());
        assertTrue(data.isEqual(new DataResourceEmiStack(DataResourceEmiKey.DATA, 1L)));
        assertFalse(data.isEqual(dataFlow));
        assertThrows(IllegalArgumentException.class, () -> new DataResourceEmiStack(DataResourceEmiKey.DATA, 0L));
        assertThrows(IllegalArgumentException.class, () -> new DataResourceEmiStack(DataResourceEmiKey.DATA, -1L));
    }

    @Test
    void copyPreservesAnExplicitlyEmptiedStack() {
        DataResourceEmiStack stack = new DataResourceEmiStack(DataResourceEmiKey.DATA, 1L);
        stack.setAmount(0L);

        DataResourceEmiStack copy = stack.copy();

        assertTrue(stack.isEmpty());
        assertTrue(copy.isEmpty());
        assertEquals(0L, copy.getAmount());
    }

    @Test
    void copyPreservesIngredientStateWithoutSharingTheRemainder() {
        Comparison neverEqual = Comparison.of((first, second) -> false);
        DataResourceEmiStack remainder = new DataResourceEmiStack(DataResourceEmiKey.DATA_FLOW, 5L);
        DataResourceEmiStack original = new DataResourceEmiStack(DataResourceEmiKey.DATA, Long.MAX_VALUE);
        original.setChance(0.25F);
        original.setRemainder(remainder);
        original.comparison(neverEqual);

        DataResourceEmiStack copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(DataResourceEmiKey.DATA, copy.getKey());
        assertEquals(Long.MAX_VALUE, copy.getAmount());
        assertEquals(0.25F, copy.getChance());
        assertNotSame(remainder, copy.getRemainder());
        assertEquals(remainder.getKey(), copy.getRemainder().getKey());
        assertEquals(5L, copy.getRemainder().getAmount());
        assertFalse(copy.isEqual(new DataResourceEmiStack(DataResourceEmiKey.DATA, 1L)));
    }

    @Test
    void emiTooltipExposesTextAndRemainderComponents() {
        DataResourceEmiStack stack = new DataResourceEmiStack(DataResourceEmiKey.DATA, Long.MAX_VALUE);
        stack.setRemainder(new DataResourceEmiStack(DataResourceEmiKey.DATA_FLOW, 1L));

        List<Component> tooltipText = stack.getTooltipText();
        List<ClientTooltipComponent> tooltip = stack.getTooltip();

        assertEquals(DataKey.of().getDisplayName(), tooltipText.getFirst());
        assertEquals(
                GenericStackDisplayHelper.createAmountTooltip(new GenericStack(DataKey.of(), Long.MAX_VALUE)),
                tooltipText.getLast());
        assertEquals(tooltipText.size() + 1, tooltip.size());
        assertTrue(tooltip.subList(0, tooltipText.size()).stream().allMatch(ClientTextTooltip.class::isInstance));
        assertInstanceOf(RemainderTooltipComponent.class, tooltip.getLast());
    }

    @Test
    void genericStackUsesTheAeKeyAsIdentityAndPersistsItInOneComponent() {
        GenericAeKeyEmiStack first = new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, 17L);
        GenericAeKeyEmiStack second = new GenericAeKeyEmiStack(TestCustomKey.ALTERNATE, 17L);

        assertInstanceOf(GenericAeKeyEmiKey.class, first.getKey());
        assertSame(TestCustomKey.FALLBACK, first.aeKey());
        assertEquals(TestCustomKey.FALLBACK.getId(), first.getId());
        assertEquals(1, first.getComponentChanges().size());
        assertEquals(
                new GenericStack(TestCustomKey.FALLBACK, 1L),
                first.getComponentChanges().get(AEComponents.WRAPPED_STACK).orElseThrow());
        assertFalse(first.isEmpty());
        assertTrue(first.isEqual(new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, 1L)));
        assertFalse(first.isEqual(second));
        assertThrows(IllegalArgumentException.class, () -> new GenericAeKeyEmiStack(AEItemKey.of(Items.STONE), 1L));
        assertThrows(IllegalArgumentException.class, () -> new GenericAeKeyEmiStack(AEFluidKey.of(Fluids.WATER), 1L));
        assertThrows(IllegalArgumentException.class, () -> new GenericAeKeyEmiStack(DataKey.of(), 1L));
        assertThrows(IllegalArgumentException.class, () -> new GenericAeKeyEmiStack(DataFlowKey.of(), 1L));
        assertThrows(IllegalArgumentException.class, () -> new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, 0L));
        assertThrows(IllegalArgumentException.class, () -> new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, -1L));
    }

    @Test
    void genericCopyAndTooltipPreserveTheFullIngredientContract() {
        Comparison neverEqual = Comparison.of((first, second) -> false);
        GenericAeKeyEmiStack remainder = new GenericAeKeyEmiStack(TestCustomKey.ALTERNATE, 5L);
        GenericAeKeyEmiStack original = new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, Long.MAX_VALUE);
        original.setChance(0.25F);
        original.setRemainder(remainder);
        original.comparison(neverEqual);

        GenericAeKeyEmiStack copy = original.copy();
        List<Component> tooltipText = original.getTooltipText();

        assertNotSame(original, copy);
        assertSame(TestCustomKey.FALLBACK, copy.aeKey());
        assertEquals(Long.MAX_VALUE, copy.getAmount());
        assertEquals(0.25F, copy.getChance());
        assertNotSame(remainder, copy.getRemainder());
        assertEquals(5L, copy.getRemainder().getAmount());
        assertFalse(copy.isEqual(new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, 1L)));
        assertEquals(TestCustomKey.FALLBACK.getDisplayName(), original.getName());
        assertEquals(TestCustomKey.FALLBACK.getDisplayName(), tooltipText.getFirst());
        assertEquals(
                GenericStackDisplayHelper.createAmountTooltip(new GenericStack(TestCustomKey.FALLBACK, Long.MAX_VALUE)),
                tooltipText.getLast());
    }

    @Test
    void genericSerializerRebuildsIdentityAndRejectsMalformedStacks() {
        GenericAeKeyEmiStackSerializer serializer = GenericAeKeyEmiStackSerializer.INSTANCE;
        GenericAeKeyEmiStack original = new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, Long.MAX_VALUE);
        GenericAeKeyEmiStack restored = assertInstanceOf(
                GenericAeKeyEmiStack.class,
                serializer.create(original.getId(), original.getComponentChanges(), original.getAmount()));

        assertEquals("data_energistics_ae_key", serializer.getType());
        assertSame(TestCustomKey.FALLBACK, restored.aeKey());
        assertEquals(Long.MAX_VALUE, restored.getAmount());
        assertEquals(original.getComponentChanges(), restored.getComponentChanges());

        DataComponentPatch valid = original.getComponentChanges();
        DataComponentPatch wrongIdentityAmount = DataComponentPatch.builder()
                .set(AEComponents.WRAPPED_STACK, new GenericStack(TestCustomKey.FALLBACK, 2L))
                .build();
        DataComponentPatch itemIdentity = DataComponentPatch.builder()
                .set(AEComponents.WRAPPED_STACK, new GenericStack(AEItemKey.of(Items.STONE), 1L))
                .build();
        DataComponentPatch removedIdentity = DataComponentPatch.builder()
                .remove(AEComponents.WRAPPED_STACK)
                .build();
        DataComponentType<String> extraType = DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .build();
        DataComponentPatch extraComponent = DataComponentPatch.builder()
                .set(AEComponents.WRAPPED_STACK, new GenericStack(TestCustomKey.FALLBACK, 1L))
                .set(extraType, "unexpected")
                .build();
        DataComponentPatch dataIdentity = DataComponentPatch.builder()
                .set(AEComponents.WRAPPED_STACK, new GenericStack(DataKey.of(), 1L))
                .build();
        DataComponentPatch dataFlowIdentity = DataComponentPatch.builder()
                .set(AEComponents.WRAPPED_STACK, new GenericStack(DataFlowKey.of(), 1L))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.create(TestCustomKey.ALTERNATE.getId(), valid, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.create(TestCustomKey.FALLBACK.getId(), DataComponentPatch.EMPTY, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.create(TestCustomKey.FALLBACK.getId(), wrongIdentityAmount, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.create(ResourceLocation.withDefaultNamespace("stone"), itemIdentity, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.create(TestCustomKey.FALLBACK.getId(), removedIdentity, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> serializer.create(TestCustomKey.FALLBACK.getId(), extraComponent, 1L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(DataKey.ID, dataIdentity, 1L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(DataFlowKey.ID, dataFlowIdentity, 1L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(TestCustomKey.FALLBACK.getId(), valid, 0L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(TestCustomKey.FALLBACK.getId(), valid, -1L));
    }

    @Test
    void genericConverterIsReverseOnlyAndPreservesCustomKeysAndLongAmounts() {
        GenericAeKeyEmiStackConverter converter = GenericAeKeyEmiStackConverter.INSTANCE;
        GenericAeKeyEmiStack emiStack = new GenericAeKeyEmiStack(TestCustomKey.FALLBACK, Long.MAX_VALUE);
        GenericStack restored = converter.toGenericStack(emiStack);

        assertEquals(GenericAeKeyEmiKey.class, converter.getKeyType());
        assertNull(converter.toEmiStack(new GenericStack(TestCustomKey.FALLBACK, Long.MAX_VALUE)));
        assertSame(TestCustomKey.FALLBACK, restored.what());
        assertEquals(Long.MAX_VALUE, restored.amount());
        assertNull(converter.toGenericStack(EmiStack.EMPTY));
        assertNull(converter.toEmiStack(new GenericStack(AEItemKey.of(Items.STONE), 1L)));
        assertNull(converter.toEmiStack(new GenericStack(AEFluidKey.of(Fluids.WATER), 1L)));
        assertNull(converter.toGenericStack(EmiStack.of(Items.STONE)));
        assertNull(converter.toGenericStack(EmiStack.of(Fluids.WATER)));
    }

    @Test
    void recipeAdapterAcceptsDataResourcesAndRejectsUnsupportedCustomKeys() {
        GenericStack stack = new GenericStack(DataKey.of(), 64L);

        assertInstanceOf(DataResourceEmiStack.class, EmiStackHelper.toEmiStack(stack));
        assertInstanceOf(DataResourceEmiStack.class, DataReassemblerRecipeIngredientAdapterImpl.toEmiStack(stack));
        assertThrows(
                IllegalArgumentException.class,
                () -> DataReassemblerRecipeIngredientAdapterImpl.toEmiStack(
                        new GenericStack(TestCustomKey.FALLBACK, 65L)));
    }

    @Test
    void localFallbackPrefersLateSpecializedConvertersAndHandlesOtherwiseUnknownKeys() {
        EmiStack converted = GenericAeKeyEmiStacks.toEmiStack(
                new GenericStack(TestCustomKey.SPECIALIZED, 73L));
        GenericAeKeyEmiStack fallback = assertInstanceOf(
                GenericAeKeyEmiStack.class,
                GenericAeKeyEmiStacks.toEmiStack(new GenericStack(TestCustomKey.FALLBACK, 74L)));
        GenericAeKeyEmiStack zeroAmount = assertInstanceOf(
                GenericAeKeyEmiStack.class,
                GenericAeKeyEmiStacks.toEmiStack(new GenericStack(TestCustomKey.FALLBACK, 0L)));

        TestSpecializedEmiStack specialized = assertInstanceOf(TestSpecializedEmiStack.class, converted);
        assertSame(TestCustomKey.SPECIALIZED, specialized.aeKey());
        assertEquals(73L, specialized.getAmount());
        assertSame(TestCustomKey.FALLBACK, fallback.aeKey());
        assertEquals(74L, fallback.getAmount());
        assertEquals(1L, zeroAmount.getAmount());
        assertThrows(
                IllegalArgumentException.class,
                () -> GenericAeKeyEmiStacks.toEmiStack(new GenericStack(TestCustomKey.FALLBACK, -1L)));
    }

    @Test
    void serializerCreatesOnlyWhitelistedComponentFreePositiveStacks() {
        DataResourceEmiStackSerializer serializer = DataResourceEmiStackSerializer.INSTANCE;

        assertEquals("data_energistics_key", serializer.getType());
        assertEquals(
                DataResourceEmiKey.DATA,
                serializer.create(DataKey.ID, DataComponentPatch.EMPTY, 3L).getKey());
        assertEquals(
                DataResourceEmiKey.DATA_FLOW,
                serializer.create(DataFlowKey.ID, DataComponentPatch.EMPTY, 4L).getKey());
        assertEquals(
                "data_energistics_key:data_energistics:data",
                serializer.serialize(new DataResourceEmiStack(DataResourceEmiKey.DATA, 1L)).getAsString());

        DataComponentType<String> testComponent = DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .build();
        DataComponentPatch components = DataComponentPatch.builder()
                .set(testComponent, "not supported")
                .build();
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("data_energistics", "unknown");
        assertThrows(IllegalArgumentException.class, () -> serializer.create(unknown, DataComponentPatch.EMPTY, 1L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(DataKey.ID, components, 1L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(DataKey.ID, DataComponentPatch.EMPTY, 0L));
        assertThrows(IllegalArgumentException.class, () -> serializer.create(DataKey.ID, DataComponentPatch.EMPTY, -1L));
    }

    @Test
    void serializerRoundTripPreservesLongAmountAndChance() {
        DataResourceEmiStackSerializer serializer = DataResourceEmiStackSerializer.INSTANCE;
        DataResourceEmiStack original = new DataResourceEmiStack(DataResourceEmiKey.DATA_FLOW, Long.MAX_VALUE);
        original.setChance(0.5F);

        JsonElement serialized = serializer.serialize(original);
        DataResourceEmiStack restored = assertInstanceOf(
                DataResourceEmiStack.class,
                serializer.deserialize(serialized));

        assertEquals(DataResourceEmiKey.DATA_FLOW, restored.getKey());
        assertEquals(Long.MAX_VALUE, restored.getAmount());
        assertEquals(0.5F, restored.getChance());
    }

    @Test
    void converterRoundTripsBothKeysAndPreservesLongAmounts() {
        DataResourceEmiStackConverter converter = DataResourceEmiStackConverter.INSTANCE;

        assertEquals(DataResourceEmiKey.class, converter.getKeyType());
        assertConverted(converter, DataKey.of());
        assertConverted(converter, DataFlowKey.of());

        DataResourceEmiStack zeroAmount = assertInstanceOf(
                DataResourceEmiStack.class,
                converter.toEmiStack(new GenericStack(DataKey.of(), 0L)));
        assertEquals(1L, zeroAmount.getAmount());

        assertNull(converter.toGenericStack(EmiStack.EMPTY));
        assertNull(converter.toGenericStack(new DataResourceEmiStack(DataResourceEmiKey.DATA, 1L).setAmount(0L)));
        assertNull(converter.toEmiStack(new GenericStack(AEItemKey.of(Items.STONE), 1L)));
        assertNull(converter.toGenericStack(EmiStack.of(Items.STONE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> converter.toEmiStack(new GenericStack(DataKey.of(), -1L)));
    }

    @Test
    void converterRegistrationIsOrderedAndEachRegistrarRunsOnlyOnce() {
        DataEnergisticsEmiPlugin.ConverterRegistration registration = new DataEnergisticsEmiPlugin.ConverterRegistration();
        AtomicInteger dataInvocationCount = new AtomicInteger();
        AtomicInteger genericInvocationCount = new AtomicInteger();

        assertThrows(IllegalStateException.class, registration::requireRegistered);
        assertThrows(IllegalStateException.class, () -> registration.registerGenericOnce(() -> true));
        registration.registerDataOnce(() -> {
            dataInvocationCount.incrementAndGet();
            return true;
        });
        assertThrows(IllegalStateException.class, registration::requireRegistered);
        registration.registerGenericOnce(() -> {
            genericInvocationCount.incrementAndGet();
            return true;
        });
        registration.requireRegistered();
        registration.registerDataOnce(() -> {
            dataInvocationCount.incrementAndGet();
            return false;
        });
        registration.registerGenericOnce(() -> {
            genericInvocationCount.incrementAndGet();
            return false;
        });

        assertEquals(1, dataInvocationCount.get());
        assertEquals(1, genericInvocationCount.get());
        assertThrows(
                IllegalStateException.class,
                () -> new DataEnergisticsEmiPlugin.ConverterRegistration().registerDataOnce(() -> false));
        DataEnergisticsEmiPlugin.ConverterRegistration genericConflict = new DataEnergisticsEmiPlugin.ConverterRegistration();
        genericConflict.registerDataOnce(() -> true);
        assertThrows(IllegalStateException.class, () -> genericConflict.registerGenericOnce(() -> false));
    }

    private static void assertConverted(DataResourceEmiStackConverter converter, AEKey key) {
        GenericStack genericStack = new GenericStack(key, Long.MAX_VALUE);

        DataResourceEmiStack emiStack = assertInstanceOf(
                DataResourceEmiStack.class,
                converter.toEmiStack(genericStack));
        GenericStack restored = converter.toGenericStack(emiStack);

        assertEquals(key, restored.what());
        assertEquals(Long.MAX_VALUE, restored.amount());
    }

    private record TestSpecializedIdentity(AEKey aeKey) {}

    private static final class TestSpecializedEmiStack extends EmiStack {

        private final TestSpecializedIdentity key;

        private TestSpecializedEmiStack(AEKey key, long amount) {
            this.key = new TestSpecializedIdentity(key);
            this.amount = amount;
        }

        @Override
        public EmiStack copy() {
            TestSpecializedEmiStack copy = new TestSpecializedEmiStack(this.key.aeKey(), 1L);
            copy.amount = this.amount;
            copy.chance = this.chance;
            copy.setRemainder(getRemainder().copy());
            copy.comparison = this.comparison;
            return copy;
        }

        @Override
        public boolean isEmpty() {
            return this.amount <= 0L;
        }

        @Override
        public DataComponentPatch getComponentChanges() {
            return DataComponentPatch.EMPTY;
        }

        @Override
        public TestSpecializedIdentity getKey() {
            return this.key;
        }

        @Override
        public ResourceLocation getId() {
            return this.key.aeKey().getId();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int x, int y, float delta, int flags) {
            EmiStack.of(Items.DIAMOND).render(guiGraphics, x, y, delta, flags);
        }

        @Override
        public List<Component> getTooltipText() {
            return List.of(getName());
        }

        @Override
        public Component getName() {
            return this.key.aeKey().getDisplayName();
        }

        private AEKey aeKey() {
            return this.key.aeKey();
        }
    }

    private static final class TestSpecializedConverterImpl implements EmiStackConverter {

        @Override
        public Class<?> getKeyType() {
            return TestSpecializedIdentity.class;
        }

        @Override
        public EmiStack toEmiStack(GenericStack stack) {
            if (stack.what().equals(TestCustomKey.SPECIALIZED)) {
                return new TestSpecializedEmiStack(stack.what(), stack.amount());
            }
            return null;
        }

        @Override
        public GenericStack toGenericStack(EmiStack stack) {
            TestSpecializedIdentity identity = stack.getKeyOfType(TestSpecializedIdentity.class);
            return identity == null ? null : new GenericStack(identity.aeKey(), stack.getAmount());
        }
    }

    private static final class TestCustomKeyType extends AEKeyType {

        private static final TestCustomKeyType INSTANCE = new TestCustomKeyType();

        private TestCustomKeyType() {
            super(
                    ResourceLocation.fromNamespaceAndPath("data_energistics", "viewer_test_custom_key_type"),
                    TestCustomKey.class,
                    Component.literal("Viewer test custom key"));
        }

        @Override
        public MapCodec<? extends AEKey> codec() {
            return TestCustomKey.MAP_CODEC;
        }

        @Override
        public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
            return new TestCustomKey(input.readResourceLocation());
        }
    }

    private static final class TestCustomKey extends AEKey {

        private static final MapCodec<TestCustomKey> MAP_CODEC = ResourceLocation.CODEC
                .fieldOf("id")
                .xmap(TestCustomKey::new, TestCustomKey::getId);
        private static final TestCustomKey FALLBACK = new TestCustomKey(
                ResourceLocation.fromNamespaceAndPath("data_energistics", "viewer_test_fallback"));
        private static final TestCustomKey SPECIALIZED = new TestCustomKey(
                ResourceLocation.fromNamespaceAndPath("data_energistics", "viewer_test_specialized"));
        private static final TestCustomKey ALTERNATE = new TestCustomKey(
                ResourceLocation.fromNamespaceAndPath("data_energistics", "viewer_test_alternate"));

        private final ResourceLocation id;

        private TestCustomKey(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return TestCustomKeyType.INSTANCE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id.toString());
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            data.writeResourceLocation(id);
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal("Viewer test " + id.getPath());
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            if (amount > 0L) {
                drops.add(GenericStack.wrapInItemStack(this, amount));
            }
        }

        @Override
        public boolean hasComponents() {
            return false;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestCustomKey other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
