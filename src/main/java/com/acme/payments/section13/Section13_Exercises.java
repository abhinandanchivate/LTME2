package com.acme.payments.section13;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 13 – HANDS-ON EXERCISES
 * =======================================================================
 * Exercise 1: Deep Immutability with riskFlags
 *   Task: Verify that PaymentTransaction enforces deep immutability for
 *   List<String> riskFlags — 5 test assertions inline.
 *
 * Exercise 2: Bug Hunt — Settlement Aggregation
 *   4 deliberate bugs in BrokenSettlementService.
 *   FixedSettlementService shows every correction side-by-side.
 *
 * Run this section alone:
 *   java com.acme.payments.section13.Section13_Exercises
 */
public class Section13_Exercises {

    private static final Currency INR = Currency.getInstance("INR");

    // =======================================================================
    // EXERCISE 1 – DEEP IMMUTABILITY WITH riskFlags
    // =======================================================================

    static void exercise1() {
        sep("EXERCISE 1: Deep Immutability with riskFlags");

        List<String> original = new ArrayList<>(List.of("high_velocity", "unusual_location"));
        PaymentTransaction tx = new PaymentTransaction(
            "tok_001", new BigDecimal("50000.00"), INR,
            Instant.now(), PaymentStatus.AUTHORIZED, PaymentMethod.CREDIT_CARD,
            "merch_test", "cust_test", original
        );

        // Mutate the original list AFTER construction
        original.add("card_testing");
        original.remove("high_velocity");

        // --- Test 1: size unchanged ---
        boolean t1 = tx.riskFlags().size() == 2;
        print("  Test 1 — riskFlags.size() == 2:         " + t1 + (t1 ? " ✅" : " ❌"));

        // --- Test 2: original flag still present ---
        boolean t2 = tx.riskFlags().contains("high_velocity");
        print("  Test 2 — 'high_velocity' still present: " + t2 + (t2 ? " ✅" : " ❌"));

        // --- Test 3: injected flag NOT present ---
        boolean t3 = !tx.riskFlags().contains("card_testing");
        print("  Test 3 — 'card_testing' not injected:   " + t3 + (t3 ? " ✅" : " ❌"));

        // --- Test 4: accessor returns unmodifiable view ---
        boolean t4;
        try { tx.riskFlags().add("hack"); t4 = false; }
        catch (UnsupportedOperationException e) { t4 = true; }
        print("  Test 4 — accessor is unmodifiable:      " + t4 + (t4 ? " ✅" : " ❌"));

        // --- Test 5: null riskFlags rejected ---
        boolean t5;
        try {
            new PaymentTransaction("tok_002", new BigDecimal("100"), INR, Instant.now(),
                PaymentStatus.AUTHORIZED, PaymentMethod.UPI, "m", "c", null);
            t5 = false;
        } catch (NullPointerException e) { t5 = true; }
        print("  Test 5 — null riskFlags rejected:       " + t5 + (t5 ? " ✅" : " ❌"));

        boolean allPassed = t1 && t2 && t3 && t4 && t5;
        print("\n  All tests passed: " + allPassed + (allPassed ? " ✅" : " ❌"));

        print("\n  Discussion: List.copyOf() automatically rejects null elements,");
        print("  making a separate null-element check redundant.");
        print("  Keeping the explicit requireNonNull(riskFlags) is still good practice:");
        print("  → communicates intent clearly to future developers");
        print("  → provides a better error message than the one from List.copyOf()");
    }

    // =======================================================================
    // EXERCISE 2 – BUG HUNT: SETTLEMENT AGGREGATION
    // =======================================================================

    /**
     * ❌ BROKEN service — 4 deliberate bugs from the PDF.
     *
     * Bug 1: tx.status  — records expose state via ACCESSOR METHODS (tx.status())
     *                     Direct field access is a COMPILE ERROR on records.
     * Bug 2: tx.currency — same: must be tx.currency()
     * Bug 3: tx.amount   — same: must be tx.amount() or PaymentTransaction::amount
     * Bug 4: Logging "PAN" label — audit red flag; must validate token format first
     *        and use structured logging, not System.out with "PAN" as a label.
     *
     * NOTE: Bugs 1–3 are compile errors in Java, shown as comments to preserve
     * the educational value while still allowing compilation.
     */
    static class BrokenSettlementService {

        public Map<Currency, BigDecimal> aggregateByCurrency(List<PaymentTransaction> txs) {
            /*
             * ORIGINAL BROKEN CODE (compile errors — educational only):
             *
             * return txs.stream()
             *     .filter(tx -> tx.status == PaymentStatus.SETTLED)      // ❌ Bug 1: field access on record
             *     .collect(Collectors.groupingBy(
             *         tx -> tx.currency,                                  // ❌ Bug 2: field access on record
             *         Collectors.reducing(
             *             BigDecimal.ZERO,
             *             tx -> tx.amount,                                // ❌ Bug 3: field access on record
             *             BigDecimal::add
             *         )
             *     ));
             */

            // Same logic with Bugs 1–3 present conceptually, corrected for compilation:
            return txs.stream()
                .filter(tx -> tx.status() == PaymentStatus.SETTLED)  // should be tx.status == ...
                .collect(Collectors.groupingBy(
                    tx -> tx.currency(),                              // should be tx.currency
                    Collectors.reducing(BigDecimal.ZERO, tx -> tx.amount(), BigDecimal::add)
                ));
        }

        public void logTransaction(PaymentTransaction tx) {
            // ❌ Bug 4: "PAN" label is an audit red flag; no token validation
            System.out.println("  ❌ [BROKEN] Processing PAN: " + tx.transactionId());
        }
    }

    /**
     * ✅ FIXED service — all 4 bugs corrected.
     *
     * Bug 1 fix: tx.status()              → accessor method on record
     * Bug 2 fix: PaymentTransaction::currency → method reference (preferred over lambda)
     * Bug 3 fix: PaymentTransaction::amount   → method reference (preferred over lambda)
     * Bug 4 fix: validate token format first; use structured AUDIT label, not "PAN"
     */
    static class FixedSettlementService {

        public Map<Currency, BigDecimal> aggregateByCurrency(List<PaymentTransaction> txs) {
            return txs.stream()
                .filter(tx -> tx.status() == PaymentStatus.SETTLED)          // ✅ Bug 1 fixed
                .collect(Collectors.groupingBy(
                    PaymentTransaction::currency,                              // ✅ Bug 2 fixed
                    Collectors.reducing(
                        BigDecimal.ZERO,
                        PaymentTransaction::amount,                            // ✅ Bug 3 fixed
                        BigDecimal::add
                    )
                ));
        }

        public void logTransaction(PaymentTransaction tx) {
            // ✅ Bug 4 fixed:
            // Step 1 — PCI belt-and-suspenders: reject raw PAN before it reaches the log
            if (tx.transactionId().matches("\\d{13,19}"))
                throw new SecurityException(
                    "PCI VIOLATION: Raw PAN in logTransaction — use tokenized ID");

            // Step 2 — Structured AUDIT log (no "PAN" label)
            System.out.println(
                "  ✅ AUDIT: token="    + tx.transactionId()
                + " merchant="          + tx.merchantId()
                + " amount="            + tx.amount()
                + " status="            + tx.status());
        }
    }

    static void exercise2() {
        sep("EXERCISE 2: Bug Hunt — Settlement Aggregation");

        print("  4 bugs from the PDF:");
        print("  ──────────────────────────────────────────────────────────────────");
        print("  Bug │ Wrong code             │ Fixed code                    │ Reason");
        print("  ────┼───────────────────────┼───────────────────────────────┼─────────────────");
        print("   1  │ tx.status             │ tx.status()                   │ Record uses accessor");
        print("   2  │ tx -> tx.currency     │ PaymentTransaction::currency  │ Method reference");
        print("   3  │ tx -> tx.amount       │ PaymentTransaction::amount    │ Method reference");
        print("   4  │ 'Processing PAN:'     │ validate + AUDIT: token=      │ PCI + structured log");

        Instant NOW = Instant.parse("2024-11-15T10:30:00Z");
        List<PaymentTransaction> txs = List.of(
            new PaymentTransaction("tok_upi_789",  new BigDecimal("4500.00"),  INR, NOW,
                PaymentStatus.SETTLED,  PaymentMethod.UPI,         "merch_swiggy_01", "cust_a"),
            new PaymentTransaction("tok_card_101", new BigDecimal("12750.50"), INR, NOW,
                PaymentStatus.SETTLED,  PaymentMethod.CREDIT_CARD, "merch_swiggy_01", "cust_b"),
            new PaymentTransaction("tok_card_303", new BigDecimal("8200.00"),  INR, NOW,
                PaymentStatus.SETTLED,  PaymentMethod.CREDIT_CARD, "merch_zomato_01", "cust_c"),
            new PaymentTransaction("tok_fail_007", new BigDecimal("1000.00"),  INR, NOW,
                PaymentStatus.FAILED,   PaymentMethod.UPI,         "merch_swiggy_01", "cust_d")
        );

        print("\n  Broken service output (Bug 4 visible):");
        BrokenSettlementService broken = new BrokenSettlementService();
        broken.logTransaction(txs.get(0));
        Map<Currency, BigDecimal> brokenResult = broken.aggregateByCurrency(txs);
        print("  aggregateByCurrency: " + brokenResult);

        print("\n  Fixed service output:");
        FixedSettlementService fixed = new FixedSettlementService();
        fixed.logTransaction(txs.get(0));
        Map<Currency, BigDecimal> fixedResult = fixed.aggregateByCurrency(txs);
        print("  aggregateByCurrency: " + fixedResult);

        // Verify Bug 4 PCI check
        print("\n  Bug 4 — PAN validation in logTransaction:");
        print("  (Raw PAN construction is blocked at Section 4 level — belt-and-suspenders)");
        print("  ✅ logTransaction validates token format before writing to log");

        boolean resultsMatch = brokenResult.equals(fixedResult);
        print("\n  Aggregation results match: " + resultsMatch + (resultsMatch ? "  ✅" : "  ❌"));
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 13 – HANDS-ON EXERCISES");
        exercise1();
        exercise2();
        done("Section 13");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
