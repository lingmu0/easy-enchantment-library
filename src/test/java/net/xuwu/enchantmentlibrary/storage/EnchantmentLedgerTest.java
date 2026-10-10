package net.xuwu.enchantmentlibrary.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** No game process, test runner dependency, or assertions flag is required. */
public final class EnchantmentLedgerTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static void rejects(Runnable operation, String label) {
        try { operation.run(); } catch (IllegalArgumentException expected) { check(true, label); return; }
        check(false, label);
    }

    public static void main(String[] args) {
        EnchantmentLedger<String> ledger = new EnchantmentLedger<>();
        check(ledger.deposit(Map.of("sharpness", EnchantmentLedger.cost(5))), "Store V = 16 points");
        for (int i = 0; i < 16; i++) check(ledger.withdraw("sharpness", EnchantmentLedger.cost(1)), "Split V into sixteen I");
        check(ledger.get("sharpness").signum() == 0, "Splitting conserves every point");
        check(ledger.snapshot().isEmpty(), "Empty entries are removed");
        for (int i = 0; i < 16; i++) check(ledger.deposit(Map.of("sharpness", EnchantmentLedger.cost(1))), "Deposit sixteen I");
        check(ledger.withdraw("sharpness", EnchantmentLedger.cost(5)), "Combine sixteen I into V");
        check(ledger.get("sharpness").signum() == 0, "Combining conserves points");

        // Level 100 exercises interoperability beyond both vanilla levels and 64-bit arithmetic.
        BigInteger high = EnchantmentLedger.cost(100);
        check(ledger.deposit(Map.of("modded", high)), "Store modified level 100");
        check(ledger.withdraw("modded", EnchantmentLedger.cost(99)), "Split high level first half");
        check(ledger.withdraw("modded", EnchantmentLedger.cost(99)), "Split high level second half");
        check(ledger.deposit(Map.of("max", EnchantmentLedger.cost(255))), "Full item-component level range");
        check(ledger.get("max").equals(EnchantmentLedger.cost(255)), "Level 255 has no integer overflow");
        rejects(() -> EnchantmentLedger.cost(0), "Reject level zero");
        check(ledger.deposit(Map.of("extended", EnchantmentLedger.cost(1000))), "No hard-coded vanilla or 255-level accounting cap");
        rejects(() -> EnchantmentLedger.cost(EnchantmentLedger.MAX_BITS + 1), "Balance representation limit is checked");

        ledger.deposit(Map.of("upgrade", EnchantmentLedger.cost(4)));
        BigInteger difference = EnchantmentLedger.cost(4).subtract(EnchantmentLedger.cost(3));
        check(ledger.withdraw("upgrade", difference), "Upgrade III to IV charges the difference");
        check(ledger.get("upgrade").equals(EnchantmentLedger.cost(3)), "Remaining balance after upgrade");
        Map<String, BigInteger> before = ledger.snapshot();
        long revision = ledger.revision();
        check(!ledger.withdraw("upgrade", BigInteger.valueOf(100)), "Insufficient balance rejected");
        check(!ledger.withdraw("upgrade", BigInteger.ZERO), "Zero withdrawal rejected");
        check(!ledger.withdraw("upgrade", BigInteger.ONE.negate()), "Negative withdrawal rejected");
        check(!ledger.deposit(Map.of("bad", BigInteger.ONE.negate(), "good", BigInteger.ONE)), "Multi-entry deposit validates atomically");
        check(before.equals(ledger.snapshot()) && ledger.revision() == revision, "Rejected operations do not mutate state");
        rejects(() -> ledger.replace(Map.of("invalid", BigInteger.ZERO)), "Invalid restore rejected");
        check(before.equals(ledger.snapshot()), "Rejected restore leaves existing state intact");

        EnchantmentLedger<String> loaded = new EnchantmentLedger<>();
        Map<String, BigInteger> serialized = new HashMap<>();
        before.forEach((key, value) -> serialized.put(key, EnchantmentLedger.parse(value.toString())));
        loaded.replace(serialized);
        check(loaded.snapshot().equals(before), "Decimal storage round trip");
        check(EnchantmentLedger.parse("0").equals(BigInteger.ZERO), "Zero parses for sanitization");
        rejects(() -> EnchantmentLedger.parse("-1"), "Negative stored data rejected");
        rejects(() -> EnchantmentLedger.parse("hello"), "Non-numeric stored data rejected");
        rejects(() -> EnchantmentLedger.parse("1".repeat(1300)), "Oversized stored data rejected");
        BigInteger full = BigInteger.ONE.shiftLeft(4096).subtract(BigInteger.ONE);
        check(EnchantmentLedger.parse(full.toString()).equals(full), "Largest supported balance round trip");
        loaded.replace(Map.of("full", full));
        check(!loaded.deposit(Map.of("full", BigInteger.ONE, "other", BigInteger.ONE)), "Full storage cannot partially accept an item");
        check(loaded.get("other").signum() == 0 && loaded.get("full").equals(full), "Capacity rejection conserves inventory");

        Random random = new Random(1211);
        BigInteger expected = BigInteger.ZERO;
        EnchantmentLedger<String> randomized = new EnchantmentLedger<>();
        for (int i = 0; i < 10000; i++) {
            BigInteger amount = EnchantmentLedger.cost(1 + random.nextInt(100));
            if (random.nextBoolean()) {
                check(randomized.deposit(Map.of("test", amount)), "Random deposit");
                expected = expected.add(amount);
            } else {
                boolean sufficient = expected.compareTo(amount) >= 0;
                check(randomized.withdraw("test", amount) == sufficient, "Random withdrawal outcome");
                if (sufficient) expected = expected.subtract(amount);
            }
            check(randomized.get("test").equals(expected), "Randomized point conservation");
        }
        System.out.println("Passed " + checks + " ledger checks: split, combine, upgrade, high levels, persistence, rejection, conservation.");
    }
}
