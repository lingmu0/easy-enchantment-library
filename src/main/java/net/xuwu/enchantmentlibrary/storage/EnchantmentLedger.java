package net.xuwu.enchantmentlibrary.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/** Point accounting is independent of Minecraft so conservation can be tested without launching it. */
public final class EnchantmentLedger<K> {
    public static final int MAX_DIGITS = 1234;
    public static final int MAX_BITS = 4096;
    private final Map<K, BigInteger> points = new HashMap<>();
    private long revision;

    public static BigInteger cost(int level) {
        if (level < 1 || level > MAX_BITS) throw new IllegalArgumentException("Level cannot be represented by the point balance: " + level);
        return BigInteger.ONE.shiftLeft(level - 1);
    }

    public static BigInteger parse(String value) {
        if (value.isEmpty() || value.length() > MAX_DIGITS || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("Invalid enchantment points");
        }
        BigInteger result = new BigInteger(value);
        if (result.bitLength() > MAX_BITS) throw new IllegalArgumentException("Enchantment storage is full");
        return result;
    }

    public BigInteger get(K id) { return points.getOrDefault(id, BigInteger.ZERO); }
    public Map<K, BigInteger> snapshot() { return Map.copyOf(points); }
    public long revision() { return revision; }

    public boolean deposit(Map<K, BigInteger> additions) {
        if (additions.isEmpty() || additions.entrySet().stream().anyMatch(e ->
                e.getValue().signum() <= 0 || get(e.getKey()).add(e.getValue()).bitLength() > MAX_BITS)) return false;
        additions.forEach((id, amount) -> points.merge(id, amount, BigInteger::add));
        revision++;
        return true;
    }

    public boolean withdraw(K id, BigInteger amount) {
        return withdrawAll(Map.of(id, amount));
    }

    public boolean canWithdrawAll(Map<K, BigInteger> amounts) {
        return !amounts.isEmpty() && amounts.entrySet().stream().allMatch(entry ->
                entry.getValue().signum() > 0 && get(entry.getKey()).compareTo(entry.getValue()) >= 0);
    }

    public boolean withdrawAll(Map<K, BigInteger> amounts) {
        if (!canWithdrawAll(amounts)) return false;
        amounts.forEach((id, amount) -> {
            BigInteger remainder = get(id).subtract(amount);
            if (remainder.signum() == 0) points.remove(id);
            else points.put(id, remainder);
        });
        revision++;
        return true;
    }

    public void replace(Map<K, BigInteger> values) {
        // Validate first: malformed data must not partially overwrite a live ledger.
        for (BigInteger value : values.values()) {
            if (value.signum() <= 0 || value.bitLength() > MAX_BITS) throw new IllegalArgumentException("Invalid stored points");
        }
        points.clear();
        points.putAll(values);
        revision++;
    }
}
