package com.acme.payments.section08;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 8 – ADVANCED COLLECTORS FOR MULTI-DIMENSIONAL ANALYTICS
 * =======================================================================
 * All nine collectors from the PDF (A – I):
 *
 *   A – Top N high-value transactions (sorted + limit)
 *   B – BigDecimal total with reduce (precision mandatory)
 *   C – groupingBy merchant → list
 *   D – partitioningBy (exactly two groups: true / false)
 *   E – summarizingDouble (count/sum/avg/min/max in ONE pass)
 *   F – Group by IST business day (UTC→IST conversion)
 *   G – groupingByConcurrent with parallelStream (thread-safe)
 *   H – Merchant totals with exact BigDecimal reducing
 *   I – Multi-level grouping: merchant → status → count (cross-tab)
 *
 * Run this section alone:
 *   java com.acme.payments.section08.Section08_AdvancedCollectors
 */
public class Section08_AdvancedCollectors {

    private static final Currency INR = Currency.getInstance("INR");
    private static final ZoneId   IST = ZoneId.of("Asia/Kolkata");

    // -----------------------------------------------------------------------
    // COMPLETE analyzeTransactions() — all nine collectors in one method
    // -----------------------------------------------------------------------
    static void analyzeTransactions(List<PaymentTransaction> txns) {

        // A: Top 10 high-value settled transactions — fraud review input
        List<String> top10Ids = txns.stream()
            .filter(t -> t.status() == PaymentStatus.SETTLED)
            .filter(t -> t.amount().compareTo(new BigDecimal("50000")) >= 0)
            .sorted(Comparator.comparing(PaymentTransaction::amount).reversed())
            .limit(10)
            .map(PaymentTransaction::transactionId)
            .collect(Collectors.toList());
        System.out.println("\n  [A] High-value settled IDs (≥₹50K): " + top10Ids);

        // B: Total settled — BigDecimal precision MANDATORY for financial totals
        //    Never use double for financial aggregation — rounding errors compound
        BigDecimal totalSettled = txns.stream()
            .filter(t -> t.status() == PaymentStatus.SETTLED)
            .map(PaymentTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("  [B] Total settled (BigDecimal): ₹" + totalSettled);

        // C: Group by merchant → list (equivalent of SQL GROUP BY)
        Map<String, List<PaymentTransaction>> byMerchant = txns.stream()
            .collect(Collectors.groupingBy(PaymentTransaction::merchantId));
        System.out.println("  [C] Merchant groups: " + byMerchant.keySet());

        // D: Partition — exactly two groups (true = settled, false = everything else)
        //    partitioningBy is more expressive than groupingBy(Boolean) for binary splits
        Map<Boolean, List<PaymentTransaction>> partition = txns.stream()
            .collect(Collectors.partitioningBy(t -> t.status() == PaymentStatus.SETTLED));
        System.out.println("  [D] Settled: " + partition.get(true).size()
                         + "  | Non-settled: " + partition.get(false).size());

        // E: Statistical summary in ONE pass — count, sum, avg, min, max simultaneously
        //    Much faster than 5 separate pipeline passes over the same data
        DoubleSummaryStatistics stats = txns.stream()
            .filter(t -> t.currency().getCurrencyCode().equals("INR"))
            .collect(Collectors.summarizingDouble(t -> t.amount().doubleValue()));
        System.out.printf("  [E] INR stats: count=%d | avg=₹%.2f | max=₹%.2f | min=₹%.2f%n",
            stats.getCount(), stats.getAverage(), stats.getMax(), stats.getMin());

        // F: Group by IST business day — UTC midnight ≠ IST midnight (+5:30 offset)
        //    businessTime() converts stored UTC Instant to IST before extracting the date
        Map<String, Long> txnsPerDay = txns.stream()
            .collect(Collectors.groupingBy(
                t -> t.businessTime(IST).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                Collectors.counting()
            ));
        System.out.println("  [F] Transactions per IST day: " + txnsPerDay);

        // G: parallelStream + groupingByConcurrent
        //    ⚠️ groupingBy with parallelStream compiles but MAY give wrong results
        //       because groupingBy uses a non-thread-safe HashMap internally.
        //    ✅ groupingByConcurrent uses ConcurrentHashMap — safe for parallel
        Map<String, DoubleSummaryStatistics> merchantStats = txns.parallelStream()
            .filter(t -> t.status() == PaymentStatus.SETTLED)
            .collect(Collectors.groupingByConcurrent(
                PaymentTransaction::merchantId,
                Collectors.summarizingDouble(t -> t.amount().doubleValue())
            ));
        System.out.println("  [G] Merchant stats (parallel + concurrent): " + merchantStats.keySet());

        // H: Merchant totals with exact BigDecimal — NOT double
        //    reducing(identity, mapper, combiner) — three-arg form for stream aggregation
        Map<String, BigDecimal> merchantTotals = txns.stream()
            .filter(t -> t.status() == PaymentStatus.SETTLED)
            .collect(Collectors.groupingBy(
                PaymentTransaction::merchantId,
                Collectors.reducing(BigDecimal.ZERO, PaymentTransaction::amount, BigDecimal::add)
            ));
        System.out.println("  [H] Merchant totals (BigDecimal precision): " + merchantTotals);

        // I: Multi-level grouping: merchant → status → count
        //    Produces a cross-tab / pivot table — useful for dashboards and regulatory reports
        Map<String, Map<PaymentStatus, Long>> matrix = txns.stream()
            .collect(Collectors.groupingBy(
                PaymentTransaction::merchantId,
                Collectors.groupingBy(PaymentTransaction::status, Collectors.counting())
            ));
        System.out.println("  [I] Merchant×Status cross-tab:");
        matrix.forEach((m, statusMap) -> System.out.println("      " + m + " → " + statusMap));
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 8 – ADVANCED COLLECTORS (A–I)");

        Instant base = Instant.parse("2024-11-15T10:00:00Z");
        List<PaymentTransaction> txns = List.of(
            new PaymentTransaction("tok_upi_789",   new BigDecimal("4500.00"),   INR, base.plusSeconds(1800), PaymentStatus.SETTLED,     PaymentMethod.UPI,          "merch_swiggy_01",  "cust_a"),
            new PaymentTransaction("tok_card_101",  new BigDecimal("12750.50"),  INR, base.plusSeconds(1770), PaymentStatus.SETTLED,     PaymentMethod.CREDIT_CARD,  "merch_swiggy_01",  "cust_b"),
            new PaymentTransaction("tok_card_303",  new BigDecimal("8200.00"),   INR, base.plusSeconds(1680), PaymentStatus.SETTLED,     PaymentMethod.CREDIT_CARD,  "merch_zomato_01",  "cust_c"),
            new PaymentTransaction("tok_neft_202",  new BigDecimal("250000.00"), INR, base.plusSeconds(1560), PaymentStatus.SETTLED,     PaymentMethod.BANK_TRANSFER,"merch_amazon_01",  "cust_d"),
            new PaymentTransaction("tok_high_001",  new BigDecimal("75000.00"),  INR, base.plusSeconds(1440), PaymentStatus.SETTLED,     PaymentMethod.CREDIT_CARD,  "merch_amazon_01",  "cust_e"),
            new PaymentTransaction("tok_fail_007",  new BigDecimal("3000.00"),   INR, base.plusSeconds(1320), PaymentStatus.FAILED,      PaymentMethod.DEBIT_CARD,   "merch_swiggy_01",  "cust_f"),
            new PaymentTransaction("tok_ref_990",   new BigDecimal("1500.00"),   INR, base.plusSeconds(1200), PaymentStatus.REFUNDED,    PaymentMethod.WALLET,       "merch_zomato_01",  "cust_g"),
            new PaymentTransaction("tok_auth_555",  new BigDecimal("9999.00"),   INR, base.plusSeconds(1080), PaymentStatus.AUTHORIZED,  PaymentMethod.UPI,          "merch_amazon_01",  "cust_h")
        );

        print("  Running collectors A–I on " + txns.size() + " transactions:");
        analyzeTransactions(txns);

        sep("Collector reference — when to use each");
        print("  Collector                │ Use case");
        print("  ─────────────────────────┼────────────────────────────────────────");
        print("  groupingBy               │ GROUP BY — list of txns per merchant");
        print("  groupingByConcurrent     │ GROUP BY with parallelStream (ConcurrentHashMap)");
        print("  partitioningBy           │ Split into exactly 2 groups (true/false)");
        print("  counting                 │ COUNT(*) as a downstream collector");
        print("  reducing                 │ Custom aggregation (BigDecimal sum)");
        print("  summarizingDouble        │ count+sum+avg+min+max in ONE pass");
        print("  collectingAndThen        │ Collector + finisher transformation");
        print("  toList                   │ Ordered List (preserves encounter order — SOX)");
        print("  toSet                    │ Unordered Set (⚠️  breaks SOX audit trail order)");
        print("  toConcurrentMap          │ Thread-safe map for parallelStream collectors");

        done("Section 8");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
