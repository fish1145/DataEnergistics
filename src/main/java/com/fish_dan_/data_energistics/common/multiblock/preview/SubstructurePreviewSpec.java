package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;

import net.minecraft.network.chat.Component;

import com.modularmc.mdl.api.multiblock.PatternLayout;
import com.modularmc.mdl.api.multiblock.PatternUnit;
import com.modularmc.mdl.api.multiblock.RepeatRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Immutable preview definition for one named MDLib-backed substructure.
 */
public final class SubstructurePreviewSpec {

    private final JsonMultiBlockDefinition definition;
    private final List<JsonMultiBlockDefinition> variants;
    private final Component title;
    private final List<PreviewTierDomain> tierDomains;
    private final List<List<RepeatRange>> repeatRangesByVariant;
    private final SubstructureSelection defaults;

    /**
     * Binds business tier metadata and validated defaults to one active JSON multiblock definition.
     *
     * @param definition  active structure definition
     * @param title       player-facing substructure title
     * @param tierDomains ordered independent tier categories
     * @param defaults    initial repeat, tier, and candidate choices
     */
    public SubstructurePreviewSpec(JsonMultiBlockDefinition definition,
                                   Component title,
                                   List<PreviewTierDomain> tierDomains,
                                   SubstructureSelection defaults) {
        this(singleVariant(definition), title, tierDomains, defaults);
    }

    /**
     * Binds an ordered shape-variant domain and business metadata to one stable named structure.
     *
     * @param variants    ordered non-empty definitions sharing one structure key
     * @param title       player-facing substructure title
     * @param tierDomains ordered independent tier categories
     * @param defaults    initial variant, repeat, tier, and candidate choices
     */
    public SubstructurePreviewSpec(List<JsonMultiBlockDefinition> variants,
                                   Component title,
                                   List<PreviewTierDomain> tierDomains,
                                   SubstructureSelection defaults) {
        if (variants == null || title == null || tierDomains == null || defaults == null) {
            throw new IllegalArgumentException("Substructure preview spec arguments cannot be null");
        }
        this.variants = copyVariants(variants);
        this.definition = this.variants.getFirst();
        this.title = title.copy();
        this.tierDomains = copyTierDomains(tierDomains);
        this.repeatRangesByVariant = this.variants.stream()
                .map(JsonMultiBlockDefinition::pattern)
                .map(pattern -> pattern.getLayout().units().stream().map(PatternUnit::repeats).toList())
                .toList();
        this.defaults = validateSelection(defaults);
    }

    /**
     * Returns the stable structure name declared by the JSON definition key.
     */
    public String id() {
        return this.definition.key().structureName();
    }

    /**
     * Returns the default variant definition for callers written against the former single-shape contract.
     */
    public JsonMultiBlockDefinition definition() {
        return definition(this.defaults.variantIndex());
    }

    /**
     * Returns ordered definitions forming the legal zero-based shape-variant domain.
     */
    public List<JsonMultiBlockDefinition> variants() {
        return this.variants;
    }

    /**
     * Returns the number of legal shape variants for this named structure.
     */
    public int variantCount() {
        return this.variants.size();
    }

    /**
     * Returns the explicit legal zero-based variant indexes in stable order.
     */
    public List<Integer> variantIndexes() {
        return IntStream.range(0, this.variants.size()).boxed().toList();
    }

    /**
     * Resolves one shape variant and fails fast for an out-of-range index.
     *
     * @param variantIndex zero-based shape variant
     * @return matching definition
     */
    public JsonMultiBlockDefinition definition(int variantIndex) {
        if (variantIndex < 0 || variantIndex >= this.variants.size()) {
            throw new IllegalArgumentException("Preview variant index " + variantIndex + " is outside 0.." +
                    (this.variants.size() - 1) + " for " + id());
        }
        return this.variants.get(variantIndex);
    }

    /**
     * Returns a detached title so callers cannot mutate definition-owned text.
     */
    public Component title() {
        return this.title.copy();
    }

    /**
     * Returns ordered independent tier categories.
     */
    public List<PreviewTierDomain> tierDomains() {
        return this.tierDomains;
    }

    /**
     * Returns repeat ranges for the default variant.
     */
    public List<RepeatRange> repeatRanges() {
        return repeatRanges(this.defaults.variantIndex());
    }

    /**
     * Returns repeat ranges derived from one variant's MDLib pattern layout.
     *
     * @param variantIndex zero-based shape variant
     * @return immutable repeat ranges for that variant
     */
    public List<RepeatRange> repeatRanges(int variantIndex) {
        definition(variantIndex);
        return this.repeatRangesByVariant.get(variantIndex);
    }

    /**
     * Returns the validated initial selection for a new preview session.
     */
    public SubstructureSelection defaults() {
        return this.defaults;
    }

    /**
     * Resolves one declared tier category by id.
     *
     * @param domainId stable tier-domain id
     * @return matching tier category
     */
    public PreviewTierDomain tierDomain(String domainId) {
        for (PreviewTierDomain domain : this.tierDomains) {
            if (domain.id().equals(domainId)) {
                return domain;
            }
        }
        throw new IllegalArgumentException("Unknown preview tier domain " + domainId + " for " + id());
    }

    /**
     * Validates a selection against this definition and normalizes tier map order to the domain declaration order.
     *
     * @param selection candidate selection
     * @return validated immutable selection
     */
    public SubstructureSelection validateSelection(SubstructureSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Substructure preview selection cannot be null");
        }
        List<RepeatRange> repeatRanges = repeatRanges(selection.variantIndex());
        if (selection.repeatCounts().size() != repeatRanges.size()) {
            throw new IllegalArgumentException("Substructure " + id() + " variant " + selection.variantIndex() +
                    " expects " + repeatRanges.size() +
                    " repeat counts, got " + selection.repeatCounts().size());
        }
        for (int index = 0; index < repeatRanges.size(); index++) {
            repeatRanges.get(index).requireValid(selection.repeatCounts().get(index));
        }

        Set<String> declaredDomains = new HashSet<>();
        for (PreviewTierDomain domain : this.tierDomains) {
            declaredDomains.add(domain.id());
        }
        if (!selection.tierSelections().keySet().equals(declaredDomains)) {
            throw new IllegalArgumentException("Substructure " + id() +
                    " tier selections must exactly match its declared domains");
        }
        Map<String, Integer> orderedTiers = new LinkedHashMap<>();
        for (PreviewTierDomain domain : this.tierDomains) {
            int value = selection.tierSelections().get(domain.id());
            domain.option(value);
            orderedTiers.put(domain.id(), value);
        }

        PatternLayout layout = definition(selection.variantIndex()).pattern().getLayout();
        for (Map.Entry<PreviewPredicateKey, Integer> entry : selection.candidateSelections().entrySet()) {
            PreviewPredicateKey key = entry.getKey();
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Preview candidate index cannot be negative: " + entry.getValue());
            }
            if (key.sourceLayer() >= layout.sourceDepth() || key.y() >= layout.height() ||
                    key.x() >= layout.width()) {
                throw new IllegalArgumentException("Preview predicate key is outside substructure " + id() +
                        ": " + key);
            }
        }
        return new SubstructureSelection(
                selection.variantIndex(),
                selection.repeatCounts(),
                orderedTiers,
                selection.candidateSelections());
    }

    private static List<JsonMultiBlockDefinition> copyVariants(List<JsonMultiBlockDefinition> variants) {
        List<JsonMultiBlockDefinition> copy = new ArrayList<>(variants);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Substructure preview spec requires at least one variant");
        }
        JsonMultiBlockDefinition first = copy.getFirst();
        if (first == null) {
            throw new IllegalArgumentException("Substructure preview variants cannot contain null");
        }
        for (JsonMultiBlockDefinition variant : copy) {
            if (variant == null) {
                throw new IllegalArgumentException("Substructure preview variants cannot contain null");
            }
            if (!first.key().equals(variant.key())) {
                throw new IllegalArgumentException("Substructure preview variants must share one structure key: " +
                        first.key() + " and " + variant.key());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<JsonMultiBlockDefinition> singleVariant(JsonMultiBlockDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Substructure preview definition cannot be null");
        }
        return List.of(definition);
    }

    private static List<PreviewTierDomain> copyTierDomains(List<PreviewTierDomain> tierDomains) {
        List<PreviewTierDomain> copy = new ArrayList<>(tierDomains);
        Set<String> ids = new HashSet<>();
        for (PreviewTierDomain domain : copy) {
            if (domain == null) {
                throw new IllegalArgumentException("Substructure tier domains cannot contain null");
            }
            if (!ids.add(domain.id())) {
                throw new IllegalArgumentException("Duplicate preview tier domain: " + domain.id());
            }
        }
        return Collections.unmodifiableList(copy);
    }
}
