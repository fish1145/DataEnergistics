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

/**
 * Immutable preview definition for one named MDLib-backed substructure.
 */
public final class SubstructurePreviewSpec {

    private final JsonMultiBlockDefinition definition;
    private final Component title;
    private final List<PreviewTierDomain> tierDomains;
    private final List<RepeatRange> repeatRanges;
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
        if (definition == null || title == null || tierDomains == null || defaults == null) {
            throw new IllegalArgumentException("Substructure preview spec arguments cannot be null");
        }
        this.definition = definition;
        this.title = title.copy();
        this.tierDomains = copyTierDomains(tierDomains);
        PatternLayout layout = definition.pattern().getLayout();
        this.repeatRanges = layout.units().stream().map(PatternUnit::repeats).toList();
        this.defaults = validateSelection(defaults);
    }

    /**
     * Returns the stable structure name declared by the JSON definition key.
     */
    public String id() {
        return this.definition.key().structureName();
    }

    /**
     * Returns the active JSON definition used for future projection.
     */
    public JsonMultiBlockDefinition definition() {
        return this.definition;
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
     * Returns repeat ranges derived from the current MDLib pattern layout.
     */
    public List<RepeatRange> repeatRanges() {
        return this.repeatRanges;
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
        if (selection.repeatCounts().size() != this.repeatRanges.size()) {
            throw new IllegalArgumentException("Substructure " + id() + " expects " + this.repeatRanges.size() +
                    " repeat counts, got " + selection.repeatCounts().size());
        }
        for (int index = 0; index < this.repeatRanges.size(); index++) {
            this.repeatRanges.get(index).requireValid(selection.repeatCounts().get(index));
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

        PatternLayout layout = this.definition.pattern().getLayout();
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
                selection.repeatCounts(),
                orderedTiers,
                selection.candidateSelections());
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
