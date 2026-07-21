package com.opensocket.aievent.core.routing.selection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.opensocket.aievent.core.routing.AgentCandidateScore;

/** Registry and stable normalization boundary for supported Agent Pool selection strategies. */
public final class SelectionStrategyRegistry {
    public static final String LOWEST_LOAD = "LOWEST_LOAD";
    public static final String WEIGHTED_SCORE = "WEIGHTED_SCORE";
    public static final String MANUAL_ONLY = "MANUAL_ONLY";
    public static final Set<String> SUPPORTED_STRATEGIES = Set.of(LOWEST_LOAD, WEIGHTED_SCORE, MANUAL_ONLY);

    private final Map<String, AgentSelectionStrategy> strategies;
    private final AgentSelectionStrategy fallback;

    public SelectionStrategyRegistry(List<AgentSelectionStrategy> strategies) {
        Map<String, AgentSelectionStrategy> registered = new LinkedHashMap<>();
        if (strategies != null) {
            for (AgentSelectionStrategy strategy : strategies) {
                if (strategy != null && strategy.strategyCode() != null && !strategy.strategyCode().isBlank()) {
                    registered.put(normalize(strategy.strategyCode()), strategy);
                }
            }
        }
        this.fallback = registered.getOrDefault(LOWEST_LOAD, new LowestLoadSelectionStrategy());
        registered.putIfAbsent(LOWEST_LOAD, fallback);
        registered.putIfAbsent(WEIGHTED_SCORE, new WeightedScoreSelectionStrategy());
        registered.putIfAbsent(MANUAL_ONLY, new ManualOnlySelectionStrategy());
        this.strategies = Map.copyOf(registered);
    }

    public static SelectionStrategyRegistry createDefault() {
        return new SelectionStrategyRegistry(List.of(
                new LowestLoadSelectionStrategy(),
                new WeightedScoreSelectionStrategy(),
                new ManualOnlySelectionStrategy()
        ));
    }

    public String supportedStrategy(String strategy) {
        String normalized = normalize(strategy);
        return strategies.containsKey(normalized) ? normalized : LOWEST_LOAD;
    }

    public boolean isManualOnly(SelectionStrategyContext context) {
        return strategy(context).manualOnly();
    }

    public AgentCandidateScore annotate(AgentCandidateScore score, SelectionStrategyContext context) {
        return strategy(context).annotate(score, normalizedContext(context));
    }

    public SelectionResult select(List<AgentCandidateScore> candidates, SelectionStrategyContext context) {
        SelectionStrategyContext normalizedContext = normalizedContext(context);
        AgentSelectionStrategy strategy = strategy(normalizedContext);
        if (strategy.manualOnly()) {
            return new SelectionResult(strategy.strategyCode(), true, List.of());
        }
        List<AgentCandidateScore> sorted = (candidates == null ? List.<AgentCandidateScore>of() : candidates).stream()
                .sorted(strategy.comparator(normalizedContext))
                .toList();
        return new SelectionResult(strategy.strategyCode(), false, sorted);
    }

    private AgentSelectionStrategy strategy(SelectionStrategyContext context) {
        String strategy = supportedStrategy(context == null ? null : context.selectionStrategy());
        return strategies.getOrDefault(strategy, fallback);
    }

    private SelectionStrategyContext normalizedContext(SelectionStrategyContext context) {
        if (context == null) {
            return new SelectionStrategyContext(null, null, LOWEST_LOAD, Map.of());
        }
        return new SelectionStrategyContext(
                context.targetPoolId(),
                context.targetPoolCode(),
                supportedStrategy(context.selectionStrategy()),
                context.normalizedMembers());
    }

    private static String normalize(String strategy) {
        return strategy == null || strategy.isBlank()
                ? LOWEST_LOAD
                : strategy.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
