package net.xuwu.enchantmentlibrary.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class EnchantmentBatchTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AtomicInteger sharpnessMax = new AtomicInteger(5);
        Function<String, EnchantmentBatch.LevelRange> limits = id -> switch (id) {
            case "sharpness" -> new EnchantmentBatch.LevelRange(1, sharpnessMax.get());
            case "unbreaking" -> new EnchantmentBatch.LevelRange(1, 3);
            case "smite" -> new EnchantmentBatch.LevelRange(1, 5);
            case "modded" -> new EnchantmentBatch.LevelRange(1, 100);
            default -> null;
        };
        BiPredicate<String, String> compatible = (a, b) -> !(a.equals("sharpness") && b.equals("smite") || a.equals("smite") && b.equals("sharpness"));
        var requested = Map.of("sharpness", 5, "unbreaking", 3);
        var plan = EnchantmentBatch.plan(requested, Map.of(), limits, id -> true, compatible).orElseThrow();
        check(plan.equals(Map.of("sharpness", BigInteger.valueOf(16), "unbreaking", BigInteger.valueOf(4))), "Independent levels produce independent costs");
        var upgraded = EnchantmentBatch.plan(requested, Map.of("sharpness", 3), limits, id -> true, compatible).orElseThrow();
        check(upgraded.get("sharpness").equals(BigInteger.valueOf(12)), "Upgrade charges only the difference");
        var partiallyPresent = EnchantmentBatch.plan(requested, Map.of("sharpness", 5), limits, id -> true, compatible).orElseThrow();
        check(partiallyPresent.equals(Map.of("unbreaking", BigInteger.valueOf(4))), "Already matching selections are free when adding another enchantment");
        check(EnchantmentBatch.plan(requested, requested, limits, id -> true, compatible).isEmpty(), "An entirely unchanged selection is not charged");
        check(EnchantmentBatch.plan(Map.of("sharpness", 3), Map.of("sharpness", 5), limits, id -> true, compatible).isEmpty(), "Downgrades do not silently destroy points");
        check(EnchantmentBatch.plan(Map.of("sharpness", 5, "smite", 5), Map.of(), limits, id -> true, compatible).isEmpty(), "Mutually exclusive selections rejected");
        check(EnchantmentBatch.plan(Map.of("smite", 5, "sharpness", 5), Map.of(), limits, id -> true, compatible).isEmpty(), "Conflicts independent of selection order");
        check(EnchantmentBatch.plan(Map.of("sharpness", 5), Map.of("smite", 5), limits, id -> true, compatible).isEmpty(), "Conflict against existing equipment enchantment rejected");
        check(EnchantmentBatch.plan(requested, Map.of(), limits, id -> !id.equals("unbreaking"), compatible).isEmpty(), "Any unsupported enchantment cancels the entire batch");
        check(EnchantmentBatch.plan(Map.of("sharpness", 6), Map.of(), limits, id -> true, compatible).isEmpty(), "Current maximum is enforced");
        sharpnessMax.set(10);
        check(EnchantmentBatch.plan(Map.of("sharpness", 8), Map.of(), limits, id -> true, compatible).isPresent(), "Higher maximum from another mod is read dynamically");
        sharpnessMax.set(5);
        check(EnchantmentBatch.plan(Map.of("sharpness", 8), Map.of(), limits, id -> true, compatible).isEmpty(), "Lowered maximum also takes effect");
        check(EnchantmentBatch.plan(Map.of("missing", 1), Map.of(), limits, id -> true, compatible).isEmpty(), "Unknown registry identifier rejected");
        check(EnchantmentBatch.plan(Map.of("sharpness", 0), Map.of(), limits, id -> true, compatible).isEmpty(), "Invalid level rejected");
        check(EnchantmentBatch.plan(Map.<String, Integer>of(), Map.of(), limits, id -> true, compatible).isEmpty(), "Empty selection rejected");
        Map<String, Integer> oversized = new HashMap<>();
        for (int i = 0; i <= EnchantmentBatch.MAX_SELECTIONS; i++) oversized.put("entry" + i, 1);
        check(EnchantmentBatch.plan(oversized, Map.of(), id -> new EnchantmentBatch.LevelRange(1, 1), id -> true, (a, b) -> true).isEmpty(), "Oversized selection rejected before processing");
        var high = EnchantmentBatch.plan(Map.of("modded", 100), Map.of(), limits, id -> true, compatible).orElseThrow();
        check(high.get("modded").equals(BigInteger.ONE.shiftLeft(99)), "Modified high levels have no integer overflow");
        check(EnchantmentBatch.plan(Map.of("sharpness", 5, "smite", 5), Map.of(), limits, id -> true, (a, b) -> true).isPresent(), "Books can store mutually exclusive enchantments");

        EnchantmentLedger<String> ledger = new EnchantmentLedger<>();
        ledger.deposit(Map.of("sharpness", BigInteger.valueOf(16), "unbreaking", BigInteger.valueOf(3)));
        Map<String, BigInteger> before = ledger.snapshot();
        long revision = ledger.revision();
        check(!ledger.canWithdrawAll(plan), "One insufficient balance makes the entire selection unavailable");
        check(!ledger.withdrawAll(plan), "Partial batch withdrawal rejected");
        check(ledger.snapshot().equals(before) && ledger.revision() == revision, "Failed batch leaves every balance unchanged");
        ledger.deposit(Map.of("unbreaking", BigInteger.ONE));
        revision = ledger.revision();
        check(ledger.withdrawAll(plan), "Whole selection succeeds when all balances are sufficient");
        check(ledger.snapshot().isEmpty(), "Successful batch consumes exactly all selected points");
        check(ledger.revision() == revision + 1, "A batch is published as one ledger update");
        check(!ledger.withdrawAll(Map.of()), "Empty debit rejected");
        ledger.deposit(Map.of("sharpness", BigInteger.valueOf(16), "unbreaking", BigInteger.valueOf(4)));
        before = ledger.snapshot();
        check(!ledger.withdrawAll(Map.of("sharpness", BigInteger.ONE, "unbreaking", BigInteger.ONE.negate())), "Invalid amount cancels all debits");
        check(ledger.snapshot().equals(before), "Invalid amount cannot charge another selected enchantment");
        System.out.println("Passed " + checks + " batch checks: independent levels, compatibility, dynamic limits, upgrades, atomic failure and success.");
    }
}
