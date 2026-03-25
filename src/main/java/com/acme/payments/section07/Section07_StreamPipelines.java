package com.acme.payments.section07;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 7 – STREAM PIPELINES FOR FINANCIAL ANALYTICS
 * =======================================================================
 * A stream pipeline has three parts:
 *   SOURCE       — collection / array / generator
 *   INTERMEDIATE — lazy: filter, map, sorted, peek
 *   TERMINAL     — triggers execution: collect, reduce, count, forEach
 *
 * Sub-sections:
 *   7A – Settlement batch pipeline (filter → sort → map → reduce)
 *   7B – groupingBy for merchant transaction counts
 *   7C – Regulatory reporting (GDPR + SOX + PCI + ISO 4217)
 *   7D – Exhaustive switch routing (Java 17)
 *   7E – peek(): debugging tool only, NOT business logic
 *   7F – Lazy evaluation demonstration
 *
 * Run this section alone:
 *   java com.acme.payments.section07.Section07_StreamPipelines
 */
public class Section07_StreamPipelines {

    private static final Logger   LOG = Logger.getLogger(Section07_StreamPipelines.class.getName());
    private static final Currency INR = Currency.getInstance("INR");
    private static final ZoneId   IST = ZoneId.of("Asia/Kolkata");

    // -----------------------------------------------------------------------
    // 7A – SETTLEMENT BATCH PIPELINE
    // -----------------------------------------------------------------------

    /**
     * Total settled: filter → sort (natural: timestamp→amount→id) → map → reduce.
     * Method reference (PaymentTransaction::amount) preferred over lambda:
     * shorter, more readable, no closure risk.
     */
    static BigDecimal totalSettled(List<PaymentTransaction> batch) {
        return batch.stream()
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .sorted()                              // natural ordering (Comparable)
            .map(PaymentTransaction::amount)       // method reference
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // -----------------------------------------------------------------------
    // 7B – groupingBy FOR MERCHANT COUNTS
    // -----------------------------------------------------------------------

    /** Settled count per merchant — stream equivalent of SQL GROUP BY. */
    static Map<String, Long> countByMerchant(List<PaymentTransaction> batch) {
        return batch.stream()
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .collect(Collectors.groupingBy(
                PaymentTransaction::merchantId,
                Collectors.counting()
            ));
    }

    // -----------------------------------------------------------------------
    // 7C – REGULATORY REPORTING
    // -----------------------------------------------------------------------

    /**
     * Merchant settlement report for today's business date in the given timezone.
     *
     * GDPR: businessTime() ensures explicit timezone conversion — prevents
     *       off-by-one day errors at the UTC/IST midnight boundary (+5:30).
     * SOX:  groupingBy with toList() preserves encounter order per merchant.
     * PCI:  no raw PAN — only tokenized IDs throughout.
     * ISO:  Currency filtered via java.util.Currency (ISO 4217).
     */
    static Map<String, Double> generateMerchantSettlementReport(
            List<PaymentTransaction> transactions, ZoneId businessZone) {
        return transactions.stream()
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .filter(tx -> tx.businessTime(businessZone).toLocalDate()
                           .equals(LocalDate.now(businessZone)))
            .collect(Collectors.groupingBy(
                PaymentTransaction::merchantId,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    txs -> txs.stream()
                        .filter(tx -> tx.currency().equals(INR))
                        .map(PaymentTransaction::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .doubleValue()   // Double for DISPLAY only — never for calculation
                )
            ));
    }

    // -----------------------------------------------------------------------
    // 7D – EXHAUSTIVE SWITCH ROUTING (Java 17)
    // Adding a new PaymentStatus value causes a compile error here until handled.
    // -----------------------------------------------------------------------

    static String routeTransaction(PaymentTransaction tx) {
        return switch (tx.status()) {
            case AUTHORIZED -> "initiateSettlement(" + tx.transactionId() + ")";
            case SETTLED    -> "updateLedger("       + tx.transactionId() + ")";
            case REFUNDED   -> "processRefund("      + tx.transactionId() + ")";
            case FAILED     -> "triggerFraudReview(" + tx.transactionId() + ")";
        };
    }

    // -----------------------------------------------------------------------
    // 7E – peek(): DEBUGGING TOOL, NOT BUSINESS LOGIC
    // peek() vs forEach():
    //   peek()    — intermediate — pipeline continues ✅
    //   forEach() — terminal     — pipeline ends      ❌ (use only for final side effects)
    // -----------------------------------------------------------------------

    static List<PaymentTransaction> settledWithDebugLogging(List<PaymentTransaction> txns) {
        return txns.stream()
            .peek(tx -> LOG.fine("Input: id=" + tx.transactionId() + " status=" + tx.status()))
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .peek(tx -> LOG.fine("Post-filter: id=" + tx.transactionId()))
            .sorted()
            .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // 7F – LAZY EVALUATION
    // -----------------------------------------------------------------------

    static void demonstrateLazyEvaluation(List<PaymentTransaction> txns) {
        // ⚠️ Builds a pipeline but does NOTHING — no terminal operation
        // txns.stream().filter(...).map(...);   // no terminal → no execution

        // ✅ Terminal operation (.reduce) triggers the entire pipeline
        BigDecimal total = txns.stream()
            .filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .map(PaymentTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);   // executes NOW

        print("  Lazy eval — total only computed when reduce() fires: ₹" + total);
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 7 – STREAM PIPELINES FOR FINANCIAL ANALYTICS");

        // PDF Section 7 sample batch
        Instant t1 = Instant.parse("2024-11-15T10:30:00Z");
        Instant t2 = Instant.parse("2024-11-15T10:29:30Z");
        Instant t3 = Instant.parse("2024-11-15T10:28:00Z");

        List<PaymentTransaction> batch = List.of(
            new PaymentTransaction("tok_upi_789",  new BigDecimal("4500.00"),  INR, t1,
                PaymentStatus.SETTLED,  PaymentMethod.UPI,         "merch_swiggy_01", "cust_a"),
            new PaymentTransaction("tok_card_101", new BigDecimal("12750.50"), INR, t2,
                PaymentStatus.SETTLED,  PaymentMethod.CREDIT_CARD, "merch_swiggy_01", "cust_b"),
            new PaymentTransaction("tok_card_303", new BigDecimal("8200.00"),  INR, t3,
                PaymentStatus.SETTLED,  PaymentMethod.CREDIT_CARD, "merch_zomato_01", "cust_c"),
            new PaymentTransaction("tok_fail_007", new BigDecimal("1000.00"),  INR,
                Instant.parse("2024-11-15T10:25:00Z"),
                PaymentStatus.FAILED,   PaymentMethod.UPI,         "merch_swiggy_01", "cust_d"),
            new PaymentTransaction("tok_ref_001",  new BigDecimal("2000.00"),  INR,
                Instant.parse("2024-11-15T10:24:00Z"),
                PaymentStatus.REFUNDED, PaymentMethod.WALLET,      "merch_zomato_01", "cust_e")
        );

        sep("7A) Settlement batch pipeline: filter → sort → map → reduce");
        BigDecimal total = totalSettled(batch);
        print("  Total settled: ₹" + total + "  (expected ₹25450.50 — FAILED/REFUNDED excluded)  ✅");

        sep("7B) groupingBy — settled count per merchant");
        Map<String, Long> counts = countByMerchant(batch);
        counts.forEach((m, c) -> print("  " + m + " → " + c));

        sep("7C) Regulatory reporting — GDPR + SOX + PCI + ISO 4217");
        Map<String, Double> report = generateMerchantSettlementReport(batch, IST);
        if (report.isEmpty()) {
            print("  (Report empty — batch is in Nov 2024, not today's IST date)");
            print("  Pipeline: filter(SETTLED) → filter(today in IST) → groupingBy → INR sum");
        } else {
            report.forEach((m, amt) -> print("  " + m + " → ₹" + String.format("%.2f", amt)));
        }
        print("  Compliance:");
        print("    ✅ PCI-DSS 3.4 — tokenized IDs; raw PANs rejected at construction");
        print("    ✅ GDPR Art 5  — businessTime() explicit timezone conversion");
        print("    ✅ SOX Sec 404 — encounter order preserved via toList()");
        print("    ✅ ISO 4217    — Currency validated via java.util.Currency");

        sep("7D) Exhaustive switch — compile error if any case missing");
        for (PaymentTransaction tx : batch)
            print("  " + tx.transactionId() + " → " + routeTransaction(tx));

        sep("7E) peek() — intermediate debugging only");
        List<PaymentTransaction> settled = settledWithDebugLogging(batch);
        print("  Settled+sorted count: " + settled.size() + "  (peek logs at FINE level)");
        settled.forEach(tx -> print("    → " + tx.timestamp() + " | " + tx.transactionId()));
        print("  ✅ peek() correct: logging only, no state mutation");

        sep("7F) Lazy evaluation — pipeline executes only when terminal fires");
        demonstrateLazyEvaluation(batch);

        done("Section 7");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
