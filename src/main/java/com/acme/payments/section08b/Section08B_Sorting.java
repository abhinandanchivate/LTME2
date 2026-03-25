package com.acme.payments.section08b;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 8B – SORTING BY KEY, VALUE, AND COMPOUND COMPARATORS
 * =======================================================================
 * Comparable vs Comparator:
 *   Comparable<T>  │ Inside the class │ One ordering   │ compareTo()
 *   Comparator<T>  │ Outside the class│ Unlimited      │ compare(o1, o2)
 *
 * Map ordering:
 *   HashMap          │ No order guarantee │ ❌ reports  │ ✅ fast internal aggregation
 *   LinkedHashMap    │ Insertion order    │ ❌ thread   │ ✅ preserve sorted() result
 *   TreeMap          │ Natural key order  │ ❌ thread   │ ✅ alphabetical / range reports
 *   ConcurrentHashMap│ None, thread-safe  │ ✅ thread   │ ✅ parallelStream collectors
 *
 * Sub-sections:
 *   8B-A – Single-field Comparators
 *   8B-B – Compound comparators (settlement queue, fraud review)
 *   8B-C – Null-safe comparators
 *   8B-D – TreeMap — alphabetical key order
 *   8B-E – Sort by value descending (LinkedHashMap)
 *   8B-F – Compound entry sort (value desc, then key asc)
 *   8B-G – getHighVolumeMerchants pipeline (filter entrySet stream)
 *   8B-H – Formatted report lines (map each entry to String)
 *
 * Run this section alone:
 *   java com.acme.payments.section08b.Section08B_Sorting
 */
public class Section08B_Sorting {

    private static final Currency INR = Currency.getInstance("INR");

    // -----------------------------------------------------------------------
    // COMPARATOR DEFINITIONS — reusable across the application
    // -----------------------------------------------------------------------

    /**
     * Settlement queue: merchant → FIFO within merchant → high amount on tie.
     * thenComparing() is evaluated ONLY when the primary returns 0.
     *
     * Pause and think: with 10K transactions across 20 merchants, roughly
     * how many thenComparing(timestamp) calls occur vs comparing(merchantId)?
     */
    public static final Comparator<PaymentTransaction> SETTLEMENT_ORDER =
        Comparator.comparing(PaymentTransaction::merchantId)
                  .thenComparing(PaymentTransaction::timestamp)
                  .thenComparing(Comparator.comparing(PaymentTransaction::amount).reversed());

    /**
     * Fraud review queue: largest amount first → oldest timestamp → id tiebreaker.
     */
    public static final Comparator<PaymentTransaction> FRAUD_REVIEW_ORDER =
        Comparator.comparing(PaymentTransaction::amount).reversed()
                  .thenComparing(PaymentTransaction::timestamp)
                  .thenComparing(PaymentTransaction::transactionId);

    /**
     * Null-safe comparator — prevents NPE mid-sort for legacy API data.
     * nullsLast → null merchantId values appear at the end of sorted output.
     */
    public static final Comparator<PaymentTransaction> NULL_SAFE_MERCHANT =
        Comparator.comparing(PaymentTransaction::merchantId,
                             Comparator.nullsLast(String::compareTo));

    // -----------------------------------------------------------------------
    // 8B-D – TreeMap (alphabetical keys)
    // -----------------------------------------------------------------------

    /** TreeMap::new as the map supplier → keys always in alphabetical order at O(log n). */
    static Map<String, BigDecimal> alphabeticalMerchantReport(List<PaymentTransaction> txns) {
        return txns.stream()
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .collect(Collectors.groupingBy(
                PaymentTransaction::merchantId,
                TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, PaymentTransaction::amount, BigDecimal::add)
            ));
    }

    // -----------------------------------------------------------------------
    // 8B-E – Sort by value descending (LinkedHashMap)
    // -----------------------------------------------------------------------

    /**
     * Sorts an existing map by value (highest first).
     * LinkedHashMap preserves the insertion order from .sorted() —
     * HashMap would immediately discard it, breaking compliance reproducibility.
     */
    static Map<String, BigDecimal> sortByVolumeDescending(Map<String, BigDecimal> totals) {
        return totals.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,          // merge: keep first on key collision
                LinkedHashMap::new    // ← CRITICAL: preserves sorted order
            ));
    }

    // -----------------------------------------------------------------------
    // 8B-F – Compound entry sort
    // -----------------------------------------------------------------------

    /** Rank merchants by count desc; alphabetical on tie. Returns ordered list of entries. */
    static List<Map.Entry<String, Long>> rankMerchantsByCount(List<PaymentTransaction> txns) {
        Map<String, Long> counts = txns.stream()
            .collect(Collectors.groupingBy(PaymentTransaction::merchantId, Collectors.counting()));
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                             .thenComparing(Map.Entry.comparingByKey()))
            .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // 8B-G – getHighVolumeMerchants pipeline
    // -----------------------------------------------------------------------

    /**
     * Aggregates, filters, and sorts in a single chained pipeline:
     *   collect(groupingBy→reducing)  → Map<merchant, total>
     *   .entrySet().stream()          → Stream<Map.Entry>
     *   .filter(above threshold)      → high-volume only
     *   .sorted(byValue desc)         → highest first
     *   .collect(toMap → LinkedHashMap) → preserves sorted order
     */
    static Map<String, BigDecimal> getHighVolumeMerchants(List<PaymentTransaction> txns,
                                                           BigDecimal threshold) {
        return txns.stream()
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .collect(Collectors.groupingBy(
                PaymentTransaction::merchantId,
                Collectors.reducing(BigDecimal.ZERO, PaymentTransaction::amount, BigDecimal::add)
            ))
            .entrySet().stream()
            .filter(e -> e.getValue().compareTo(threshold) > 0)
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new
            ));
    }

    // -----------------------------------------------------------------------
    // 8B-H – Formatted report lines
    // -----------------------------------------------------------------------

    /** map() works on Map.Entry exactly like any other stream element. */
    static List<String> buildReportLines(Map<String, BigDecimal> totals) {
        return totals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> String.format("MERCHANT: %-20s | SETTLED: ₹%,12.2f", e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 8B – COMPARATORS AND MAP SORTING");

        Instant base = Instant.parse("2024-11-15T10:00:00Z");
        List<PaymentTransaction> txns = new ArrayList<>(List.of(
            new PaymentTransaction("tok_001", new BigDecimal("4500"),  INR, base.plusSeconds(1800), PaymentStatus.SETTLED,    PaymentMethod.UPI,          "merch_swiggy_01", "cust_a"),
            new PaymentTransaction("tok_002", new BigDecimal("12750"), INR, base.plusSeconds(1770), PaymentStatus.SETTLED,    PaymentMethod.CREDIT_CARD,  "merch_swiggy_01", "cust_b"),
            new PaymentTransaction("tok_003", new BigDecimal("8200"),  INR, base.plusSeconds(1680), PaymentStatus.SETTLED,    PaymentMethod.CREDIT_CARD,  "merch_zomato_01", "cust_c"),
            new PaymentTransaction("tok_004", new BigDecimal("63100"), INR, base.plusSeconds(1560), PaymentStatus.SETTLED,    PaymentMethod.BANK_TRANSFER,"merch_flipkart",  "cust_d"),
            new PaymentTransaction("tok_005", new BigDecimal("45200"), INR, base.plusSeconds(1440), PaymentStatus.SETTLED,    PaymentMethod.CREDIT_CARD,  "merch_amazon_in", "cust_e"),
            new PaymentTransaction("tok_006", new BigDecimal("8750"),  INR, base.plusSeconds(1320), PaymentStatus.SETTLED,    PaymentMethod.UPI,          "merch_blinkit",   "cust_f"),
            new PaymentTransaction("tok_007", new BigDecimal("75000"), INR, base.plusSeconds(1200), PaymentStatus.AUTHORIZED, PaymentMethod.UPI,          "merch_swiggy_01", "cust_g"),
            new PaymentTransaction("tok_008", new BigDecimal("3000"),  INR, base.plusSeconds(1080), PaymentStatus.FAILED,     PaymentMethod.DEBIT_CARD,   "merch_zomato_01", "cust_h")
        ));

        sep("8B-A) Single-field comparator — amount descending");
        txns.stream().sorted(Comparator.comparing(PaymentTransaction::amount).reversed()).limit(3)
            .forEach(tx -> print("  ₹" + tx.amount() + " | " + tx.transactionId()));

        sep("8B-B) Settlement queue: merchant → FIFO → high amount on tie");
        new ArrayList<>(txns).stream().sorted(SETTLEMENT_ORDER)
            .forEach(tx -> print("  " + tx.merchantId() + " | " + tx.timestamp() + " | ₹" + tx.amount()));

        sep("8B-B) Fraud review queue: amount desc → timestamp → id");
        new ArrayList<>(txns).stream().sorted(FRAUD_REVIEW_ORDER)
            .forEach(tx -> print("  ₹" + tx.amount() + " | " + tx.transactionId()));

        sep("8B-C) Null-safe: Comparator.nullsLast(String::compareTo)");
        print("  Defined. Use case: legacy APIs returning null merchantId — sort to end, no NPE.");

        sep("8B-D) TreeMap — keys always alphabetical");
        Map<String, BigDecimal> alpha = alphabeticalMerchantReport(txns);
        print("  Alphabetical keys: " + alpha.keySet());
        alpha.forEach((m, amt) -> print("  " + m + " → ₹" + amt));

        sep("8B-E) Sort by value descending — LinkedHashMap preserves sorted order");
        Map<String, BigDecimal> byVol = sortByVolumeDescending(alpha);
        print("  By volume desc: " + new ArrayList<>(byVol.keySet()));
        byVol.forEach((m, amt) -> print("  " + m + " → ₹" + amt));

        sep("8B-F) Rank merchants by count, then name on tie");
        rankMerchantsByCount(txns).forEach(e -> print("  " + e.getKey() + " = " + e.getValue() + " txns"));

        sep("8B-G) High-volume merchants (threshold ₹40,000)");
        getHighVolumeMerchants(txns, new BigDecimal("40000"))
            .forEach((m, amt) -> print("  " + m + " → ₹" + amt));

        sep("8B-H) Formatted report lines (alphabetical merchant)");
        buildReportLines(alpha).forEach(l -> print("  " + l));

        sep("Map implementation reference");
        print("  HashMap           │ No order   │ ❌ reports  │ ✅ fast internal aggregation");
        print("  LinkedHashMap     │ Insert ord │ ❌ thread   │ ✅ preserve sorted() result");
        print("  TreeMap           │ Key order  │ ❌ thread   │ ✅ alphabetical reports");
        print("  ConcurrentHashMap │ None       │ ✅ thread   │ ✅ parallelStream collectors");

        done("Section 8B");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
