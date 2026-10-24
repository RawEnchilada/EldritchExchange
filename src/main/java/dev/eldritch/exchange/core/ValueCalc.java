package dev.eldritch.exchange.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ValueCalc {

    public record Recipe(String output, int count, List<List<String>> slots) {
    }

    private static final int MAX_PASSES = 500;
    private static final int USAGE_ROUNDS = 8;
    private static final double EPSILON = 1e-9;

    private ValueCalc() {
    }

    /**
     * Relaxes item values from seeds through recipes. Each output gets the cheapest
     * known cost per unit. Items that cannot be reached from a seed get no value.
     *
     * <p>After the forward pass, items that are consumed by a recipe but never
     * produced by one (uncraftable drops, modded materials, ...) are estimated from
     * the recipes that use them. Known and seeded values are never overwritten.
     */
    public static Map<String, Long> compute(Map<String, Long> seeds, List<Recipe> recipes) {
        Map<String, Double> values = new HashMap<>();
        for (Map.Entry<String, Long> seed : seeds.entrySet()) {
            values.put(seed.getKey(), (double) seed.getValue());
        }

        // Values filled in by usage inference are guesses. They may unlock recipes
        // that produce previously unknown items, but they must not be allowed to
        // undercut values that were already derived from real recipes: chained
        // guesses (ore -> guess -> cheaper crushed ore -> cheaper ingot) can
        // otherwise multiply an ore into seven ingots' worth of value.
        Set<String> inferred = new HashSet<>();
        relax(values, seeds, recipes, inferred);
        for (int round = 0; round < USAGE_ROUNDS; round++) {
            if (!inferFromUsage(values, seeds, recipes, inferred)) {
                break;
            }
            relax(values, seeds, recipes, inferred);
        }

        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            long rounded = (long) Math.ceil(entry.getValue() - EPSILON);
            result.put(entry.getKey(), Math.max(1L, rounded));
        }
        return result;
    }

    private static boolean relax(Map<String, Double> values, Map<String, Long> seeds,
            List<Recipe> recipes, Set<String> inferred) {
        boolean changed = false;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean passChanged = false;
            for (Recipe recipe : recipes) {
                if (seeds.containsKey(recipe.output()) || recipe.count() <= 0) {
                    continue;
                }
                double total = 0;
                boolean reachable = true;
                boolean usesInferred = false;
                for (List<String> options : recipe.slots()) {
                    double best = Double.POSITIVE_INFINITY;
                    boolean bestIsInferred = false;
                    for (String option : options) {
                        Double v = values.get(option);
                        if (v != null && v < best) {
                            best = v;
                            bestIsInferred = inferred.contains(option);
                        }
                    }
                    if (best == Double.POSITIVE_INFINITY) {
                        reachable = false;
                        break;
                    }
                    total += best;
                    usesInferred |= bestIsInferred;
                }
                if (!reachable || total <= 0) {
                    continue;
                }
                double candidate = total / recipe.count();
                Double current = values.get(recipe.output());
                boolean unknown = current == null;
                if (!unknown && (usesInferred || candidate >= current - EPSILON)) {
                    continue;
                }
                values.put(recipe.output(), candidate);
                if (usesInferred) {
                    inferred.add(recipe.output());
                } else {
                    inferred.remove(recipe.output());
                }
                passChanged = true;
            }
            if (!passChanged) {
                break;
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Estimates values for ingredients that no recipe produces. For each recipe
     * that consumes an unknown item either:
     * <ul>
     *   <li>the known output value minus the other ingredients gives the exact
     *       leftover value (precise), or</li>
     *   <li>if the output itself is unknown, the most valuable known ingredient
     *       is used as a rough stand-in (prediction).</li>
     * </ul>
     * Several recipes may use the same item, so the median estimate wins, and the
     * cheapest option in an ingredient slot is honoured. Only unknown items are
     * filled in; seeded and derived values are left alone.
     */
    private static boolean inferFromUsage(Map<String, Double> values, Map<String, Long> seeds,
            List<Recipe> recipes, Set<String> inferred) {
        Map<String, List<Double>> estimates = new HashMap<>();

        for (Recipe recipe : recipes) {
            if (recipe.count() <= 0) {
                continue;
            }
            Double output = values.get(recipe.output());
            double knownTotal = 0;
            double maxKnown = 0;
            int unknownSlots = 0;
            for (List<String> options : recipe.slots()) {
                double best = Double.POSITIVE_INFINITY;
                for (String option : options) {
                    Double v = values.get(option);
                    if (v != null && v < best) {
                        best = v;
                    }
                }
                if (best == Double.POSITIVE_INFINITY) {
                    unknownSlots++;
                } else {
                    knownTotal += best;
                    maxKnown = Math.max(maxKnown, best);
                }
            }
            if (unknownSlots == 0) {
                continue;
            }

            double estimate;
            if (output != null && output * recipe.count() - knownTotal > EPSILON) {
                estimate = (output * recipe.count() - knownTotal) / unknownSlots;
            } else if (maxKnown > 0) {
                estimate = maxKnown;
            } else {
                continue;
            }

            for (List<String> options : recipe.slots()) {
                if (hasKnownOption(options, values)) {
                    continue;
                }
                for (String option : options) {
                    if (values.containsKey(option) || seeds.containsKey(option)) {
                        continue;
                    }
                    estimates.computeIfAbsent(option, key -> new ArrayList<>()).add(estimate);
                }
            }
        }

        boolean changed = false;
        for (Map.Entry<String, List<Double>> entry : estimates.entrySet()) {
            double median = median(entry.getValue());
            if (median > 0) {
                values.put(entry.getKey(), median);
                inferred.add(entry.getKey());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean hasKnownOption(List<String> options, Map<String, Double> values) {
        for (String option : options) {
            if (values.containsKey(option)) {
                return true;
            }
        }
        return false;
    }

    private static double median(List<Double> numbers) {
        numbers.sort(Double::compareTo);
        int size = numbers.size();
        if (size == 0) {
            return 0;
        }
        if (size % 2 == 1) {
            return numbers.get(size / 2);
        }
        return (numbers.get(size / 2 - 1) + numbers.get(size / 2)) / 2.0;
    }

    public static List<Recipe> recipes(Recipe... entries) {
        List<Recipe> list = new ArrayList<>();
        for (Recipe r : entries) {
            list.add(r);
        }
        return list;
    }
}
