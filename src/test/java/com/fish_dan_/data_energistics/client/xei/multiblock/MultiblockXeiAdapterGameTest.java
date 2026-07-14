package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.emi.TrinityMultiblockEmiRecipe;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.ResolvedJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewTierOption;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.StructurePreviewSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructureSelection;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinding;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneElement;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.PatternUnit;
import com.modularmc.mdl.api.multiblock.Predicates;
import com.modularmc.mdl.api.multiblock.RepeatRange;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class MultiblockXeiAdapterGameTest {

    private static final ResourceLocation CONTROLLER_ID = Data_Energistics.id("xei_adapter_test");
    private static final String TIER_ID = "frame_tier";

    private MultiblockXeiAdapterGameTest() {}

    @TestHolder(value = "multiblock_xei_adapters_share_live_view_and_isolate_scene_lifetimes", side = Dist.CLIENT)
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void adaptersShareLiveViewAndIsolateSceneLifetimes(GameTestHelper helper) {
        MultiblockPreviewSpec spec = spec(7L);
        MutableCatalog catalog = new MutableCatalog(spec);
        TrackingSceneBinder sceneBinder = new TrackingSceneBinder();
        MultiblockXeiUiFactory uiFactory = MultiblockXeiUiFactory.create(
                catalog,
                StructurePreviewUiFactory.create(sceneBinder),
                true);
        MultiblockXeiRecipe jeiRecipe = MultiblockXeiRecipe.create(CONTROLLER_ID, uiFactory);
        TrinityMultiblockEmiRecipe emiRecipe = new TrinityMultiblockEmiRecipe(
                MultiblockXeiRecipe.create(CONTROLLER_ID, uiFactory));

        MultiblockXeiComposition jei = jeiRecipe.createComposition("xei_adapter_jei");
        MultiblockXeiComposition emi = emiRecipe.createComposition("xei_adapter_emi");
        MultiblockRecipeView initial = jeiRecipe.currentRecipeView();

        assertEquals(initial, emiRecipe.currentRecipeView());
        assertEquals(initial.registeredRecipeId(), jeiRecipe.registeredRecipeId());
        assertEquals(initial.registeredRecipeId(), emiRecipe.getId());
        assertNotSame(jei.previewUi().session(), emi.previewUi().session());
        assertNotSame(jei.previewUi().scene(), emi.previewUi().scene());
        assertEquals(2, sceneBinder.bindCount.get());

        MultiblockRecipeView beforeStructure = jei.currentRecipeView();
        jei.selectStructure("child");
        assertRecipeChanged(beforeStructure, jei.currentRecipeView());
        assertEquals(beforeStructure.registeredRecipeId(), jei.registeredRecipeId());
        jei.selectStructure("main");

        MultiblockRecipeView beforeVariant = jei.currentRecipeView();
        jei.selectVariant(1);
        assertRecipeChanged(beforeVariant, jei.currentRecipeView());

        MultiblockRecipeView beforeTier = jei.currentRecipeView();
        jei.previewUi().panel().nextTier();
        assertRecipeChanged(beforeTier, jei.currentRecipeView());

        int repeatUnit = jei.previewUi().session().variableRepeatUnits().getFirst();
        MultiblockRecipeView beforeRepeat = jei.currentRecipeView();
        jei.previewUi().panel().nextRepeat(repeatUnit);
        assertRecipeChanged(beforeRepeat, jei.currentRecipeView());

        PreviewPredicateSnapshot selectable = firstSelectableCandidate(jei);
        int nextCandidate = (selectable.selectedCandidateIndex() + 1) % selectable.candidates().size();
        MultiblockRecipeView beforeCandidate = jei.currentRecipeView();
        jei.selectCandidate(selectable.key(), nextCandidate);
        assertRecipeChanged(beforeCandidate, jei.currentRecipeView());

        MultiblockRecipeView beforeLayer = jei.currentRecipeView();
        jei.previewUi().panel().nextLayer();
        assertSame(beforeLayer, jei.currentRecipeView());
        assertEquals(beforeLayer.inputs(), jei.currentRecipeView().inputs());
        assertEquals(beforeLayer.projectionFingerprint(), jei.currentRecipeView().projectionFingerprint());

        List<MultiblockXeiIngredient> roles = MultiblockXeiIngredient.from(jei.currentRecipeView());
        assertEquals(jei.currentRecipeView().inputs().size() + 1, roles.size());
        assertEquals(1L, roles.stream().filter(role -> role.io() == IngredientIO.OUTPUT).count());
        assertTrue(roles.stream().allMatch(role -> role.io() == IngredientIO.INPUT || role.io() == IngredientIO.OUTPUT));
        assertEquals(jei.currentRecipeView().output(), roles.getLast().material());

        int releasesBeforeJeiClose = sceneBinder.releaseCount.get();
        jei.modularUI().onRemoved();
        assertEquals(releasesBeforeJeiClose + 1, sceneBinder.releaseCount.get());
        assertIllegalState(jeiRecipe::currentRecipeView);

        int releasesBeforeEmiClose = sceneBinder.releaseCount.get();
        emi.modularUI().onRemoved();
        assertEquals(releasesBeforeEmiClose + 1, sceneBinder.releaseCount.get());
        assertIllegalState(emiRecipe::currentRecipeView);
        assertEquals(sceneBinder.bindCount.get(), sceneBinder.releaseCount.get());
        helper.succeed();
    }

    @TestHolder(value = "multiblock_xei_live_source_rejects_stale_catalog_revision", side = Dist.CLIENT)
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void liveSourceRejectsStaleCatalogRevision(GameTestHelper helper) {
        MultiblockPreviewSpec initialSpec = spec(11L);
        MutableCatalog catalog = new MutableCatalog(initialSpec);
        StructurePreviewUiFactory previewFactory = StructurePreviewUiFactory.create((scene, selected) -> {
            throw new GameTestAssertException("Server-side stale-revision fixture must not bind a Scene");
        });
        MultiblockXeiRecipe recipe = MultiblockXeiRecipe.create(
                CONTROLLER_ID,
                MultiblockXeiUiFactory.create(catalog, previewFactory, false));
        MultiblockXeiComposition composition = recipe.createComposition("xei_stale_revision");
        ResourceLocation registeredId = recipe.registeredRecipeId();

        catalog.publish(spec(12L));

        assertIllegalState(recipe::currentRecipeView);
        assertEquals(registeredId, recipe.registeredRecipeId());
        composition.modularUI().onRemoved();
        helper.succeed();
    }

    @TestHolder(value = "multiblock_xei_replacement_failure_is_terminal", side = Dist.CLIENT)
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replacementFailureIsTerminal(GameTestHelper helper) {
        MultiblockPreviewSpec spec = spec(13L);
        MutableCatalog catalog = new MutableCatalog(spec);
        FailingFirstReleaseSceneBinder sceneBinder = new FailingFirstReleaseSceneBinder();
        MultiblockXeiRecipe recipe = MultiblockXeiRecipe.create(
                CONTROLLER_ID,
                MultiblockXeiUiFactory.create(
                        catalog,
                        StructurePreviewUiFactory.create(sceneBinder),
                        true));
        MultiblockXeiComposition composition = recipe.createComposition("xei_replacement_failure");
        PreviewPredicateSnapshot selectable = firstSelectableCandidate(composition);
        int nextCandidate = (selectable.selectedCandidateIndex() + 1) % selectable.candidates().size();

        RuntimeException failure = captureRuntimeFailure(() -> composition.selectCandidate(selectable.key(), nextCandidate));

        assertSame(sceneBinder.firstReleaseFailure, failure);
        assertIllegalState(composition::currentRecipeView);
        assertEquals(2, sceneBinder.bindCount.get());
        assertEquals(2, sceneBinder.releaseCount.get());
        composition.modularUI().onRemoved();
        assertEquals(2, sceneBinder.releaseCount.get());
        helper.succeed();
    }

    private static PreviewPredicateSnapshot firstSelectableCandidate(MultiblockXeiComposition composition) {
        return composition.previewUi().session().snapshot().cells().stream()
                .map(cell -> cell.predicate())
                .filter(predicate -> predicate.candidates().size() > 1)
                .filter(predicate -> predicate.candidates().stream()
                        .map(candidate -> candidate.placementKey().orElse(null))
                        .distinct()
                        .count() > 1)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("Fixture requires a material-changing candidate"));
    }

    private static void assertRecipeChanged(MultiblockRecipeView before, MultiblockRecipeView after) {
        assertEquals(before.registeredRecipeId(), after.registeredRecipeId());
        assertNotEquals(before.projectionFingerprint(), after.projectionFingerprint());
        assertNotEquals(before.inputs(), after.inputs());
    }

    private static MultiblockPreviewSpec spec(long revision) {
        return new MultiblockPreviewSpec(
                CONTROLLER_ID,
                Component.literal("XEI adapter fixture"),
                AEItemKey.of(Blocks.CRAFTING_TABLE),
                revision,
                List.of(
                        substructure("main", Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.OBSIDIAN),
                        substructure("child", Blocks.DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.NETHER_BRICKS)));
    }

    private static SubstructurePreviewSpec substructure(String id,
                                                        Block firstFrame,
                                                        Block secondFrame,
                                                        Block endFrame) {
        JsonMultiBlockStructureKey key = new JsonMultiBlockStructureKey(CONTROLLER_ID, id);
        List<JsonMultiBlockDefinition> variants = List.of(
                new ResolvedJsonMultiBlockDefinition(key, pattern(firstFrame, endFrame)),
                new ResolvedJsonMultiBlockDefinition(key, pattern(secondFrame, endFrame)));
        PreviewTierDomain tier = new PreviewTierDomain(
                TIER_ID,
                Component.literal("Frame tier"),
                List.of(
                        new PreviewTierOption(1, Component.literal("Iron"), ResourceLocation.withDefaultNamespace("iron_block")),
                        new PreviewTierOption(2, Component.literal("Diamond"), ResourceLocation.withDefaultNamespace("diamond_block"))),
                1);
        List<Integer> repeats = variants.getFirst().pattern().getLayout().units().stream()
                .map(PatternUnit::repeats)
                .map(RepeatRange::min)
                .toList();
        return new SubstructurePreviewSpec(
                variants,
                Component.literal(id),
                List.of(tier),
                new SubstructureSelection(0, repeats, Map.of(TIER_ID, 1), Map.of()));
    }

    private static BlockPattern pattern(Block frame, Block endFrame) {
        return FactoryBlockPattern.start()
                .aisle("~TC")
                .beginRepeatable()
                .aisle("ATC")
                .endRepeatable(1, 2)
                .aisle("DTC")
                .where('A', Predicates.blocks(frame))
                .where('D', Predicates.blocks(endFrame))
                .where('T', Predicates.blocks(Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK))
                .where('C', Predicates.blocks(Blocks.STONE, Blocks.GOLD_BLOCK))
                .build();
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new GameTestAssertException("Expected true");
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected identical objects");
        }
    }

    private static void assertNotSame(Object first, Object second) {
        if (first == second) {
            throw new GameTestAssertException("Expected distinct object identities");
        }
    }

    private static void assertNotEquals(Object first, Object second) {
        if (first.equals(second)) {
            throw new GameTestAssertException("Expected distinct values");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() == null || expected.getMessage().isBlank()) {
                throw new GameTestAssertException("IllegalStateException must explain the stale or removed state");
            }
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    private static RuntimeException captureRuntimeFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new GameTestAssertException("Expected RuntimeException");
    }

    private static final class MutableCatalog implements MultiblockPreviewCatalog {

        private MultiblockPreviewCatalogSnapshot snapshot;

        private MutableCatalog(MultiblockPreviewSpec spec) {
            publish(spec);
        }

        private void publish(MultiblockPreviewSpec spec) {
            this.snapshot = new MultiblockPreviewCatalogSnapshot(
                    spec.definitionRevision(),
                    Map.of(spec.controllerId(), spec));
        }

        @Override
        public MultiblockPreviewCatalogSnapshot snapshot() {
            return this.snapshot;
        }
    }

    private static final class TrackingSceneBinder implements StructurePreviewSceneBinder {

        private final AtomicInteger bindCount = new AtomicInteger();
        private final AtomicInteger refreshCount = new AtomicInteger();
        private final AtomicInteger releaseCount = new AtomicInteger();

        @Override
        public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                                 BiConsumer<BlockPos, Direction> selectionConsumer) {
            this.bindCount.incrementAndGet();
            return new StructurePreviewSceneBinding() {

                private boolean released;

                @Override
                public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
                    if (this.released) {
                        throw new GameTestAssertException("Released test Scene binding cannot be refreshed");
                    }
                    refreshCount.incrementAndGet();
                }

                @Override
                public void release() {
                    if (!this.released) {
                        this.released = true;
                        releaseCount.incrementAndGet();
                    }
                }
            };
        }
    }

    private static final class FailingFirstReleaseSceneBinder implements StructurePreviewSceneBinder {

        private final RuntimeException firstReleaseFailure = new RuntimeException("First Scene release failed");
        private final AtomicInteger bindCount = new AtomicInteger();
        private final AtomicInteger releaseCount = new AtomicInteger();

        @Override
        public StructurePreviewSceneBinding bind(StructurePreviewSceneElement scene,
                                                 BiConsumer<BlockPos, Direction> selectionConsumer) {
            int bindingIndex = this.bindCount.incrementAndGet();
            return new StructurePreviewSceneBinding() {

                private boolean released;

                @Override
                public void refresh(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
                    if (this.released) {
                        throw new GameTestAssertException("Released replacement fixture cannot be refreshed");
                    }
                }

                @Override
                public void release() {
                    if (this.released) {
                        return;
                    }
                    this.released = true;
                    releaseCount.incrementAndGet();
                    if (bindingIndex == 1) {
                        throw firstReleaseFailure;
                    }
                }
            };
        }
    }
}
