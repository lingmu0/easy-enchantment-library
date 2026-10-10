package net.xuwu.enchantmentlibrary.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Validates the entire selection before any item or balance is changed. */
public final class EnchantmentBatch {
    public static final int MAX_SELECTIONS = 128;
    public record LevelRange(int min, int max) {}

    public static <K> Optional<Map<K, BigInteger>> plan(Map<K, Integer> requested, Map<K, Integer> existing,
            Function<K, LevelRange> limits, Predicate<K> supported, BiPredicate<K, K> compatible) {
        if (requested.isEmpty() || requested.size() > MAX_SELECTIONS) return Optional.empty();
        Map<K, BigInteger> costs = new HashMap<>();
        for (var entry : requested.entrySet()) {
            K id = entry.getKey();
            int level = entry.getValue();
            LevelRange range = limits.apply(id);
            int previous = existing.getOrDefault(id, 0);
            if (range == null || level < Math.max(1, range.min()) || level > range.max()
                    || level > EnchantmentLedger.MAX_BITS || level < previous || !supported.test(id)) return Optional.empty();
            if (level == previous) continue;
            for (K other : existing.keySet()) {
                if (!id.equals(other) && !compatible.test(id, other)) return Optional.empty();
            }
            for (K other : requested.keySet()) {
                if (!id.equals(other) && !compatible.test(id, other)) return Optional.empty();
            }
            costs.put(id, EnchantmentLedger.cost(level).subtract(previous == 0 ? BigInteger.ZERO : EnchantmentLedger.cost(previous)));
        }
        return costs.isEmpty() ? Optional.empty() : Optional.of(Map.copyOf(costs));
    }
}
