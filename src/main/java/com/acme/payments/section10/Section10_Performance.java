package com.acme.payments.section10;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 10 – STREAM PERFORMANCE OPTIMIZATION
 * =======================================================================
 * Sub-sections:
 *   10A – Primitive streams (avoid boxing overhead)
 *   10B – Short-circuiting (stop processing early)
 *   10C – Lazy evaluation — only processes elements when terminal fires
 *   10D – Parallel streams — when to use and when NOT to
 *   10E – Dedicated ForkJoinPool (don't starve the shared common pool)
 *
 * Performance reality (Indian enterprise batches 10K–50K txns):
 *   Scenario              │ Sequential │ Parallel │ Verdict
 *   ──────────────────────┼────────────┼──────────┼─────────────────
 *   CPU-intensive, 100K   │   500ms    │  150ms   │ ✅ parallel
 *   DB save, 10K          │  2000ms    │ 5000ms   │ ❌ sequential
 *   Simple filter, 100    │     1ms    │    5ms   │ ❌ sequential
 *   BigDecimal reduce, 1M │   800ms    │  280ms   │ ✅ dedicated pool
 *
 * Run this section alone:
 *   java com.acme.payments.section10.Section10_Performance
 */
public class Section10_Performance {

    // -----------------------------------------------------------------------
    // 10A – PRIMITIVE STREAMS
    // -----------------------------------------------------------------------

    /**
     * ❌ Slow: Stream<Integer> boxes every value — 1M Integer allocations per op.
     * ✅ Fast: LongStream works with primitive long — 0 allocations, ~3× faster.
     *
     * Quick exercise: When does mapToInt() overflow for Indian enterprise payments?
     *   Integer.MAX_VALUE = 2,147,483,647 paise = ₹21,474,836.47
     *   A single large RTGS transfer can exceed this → always use mapToLong().
     */
    static long totalAmountPrimitive(List<PaymentTransaction> txns) {
        return txns.stream()
            .mapToLong(tx -> tx.amount().longValue())  // no boxing
            .sum();
    }

    /** count + sum + avg + min + max in a SINGLE pass — no five separate streams. */
    static LongSummaryStatistics amountSummary(List<PaymentTransaction> txns) {
        return txns.stream()
            .mapToLong(tx -> tx.amount().longValue())
            .summaryStatistics();
    }

    // -----------------------------------------------------------------------
    // 10B – SHORT-CIRCUITING
    // -----------------------------------------------------------------------

    /**
     * ❌ Never:  .filter(...).collect(...).size() > 0 — processes ALL elements
     * ✅ Always: .anyMatch(...)                       — stops at FIRST match
     */
    static boolean hasHighValueTransaction(List<PaymentTransaction> txns, BigDecimal threshold) {
        return txns.stream().anyMatch(tx -> tx.amount().compareTo(threshold) > 0);
    }

    /** allMatch — stops at first false. */
    static boolean allSettled(List<PaymentTransaction> txns) {
        return txns.stream().allMatch(tx -> tx.status() == PaymentStatus.SETTLED);
    }

    /** noneMatch — stops at first true. */
    static boolean noPanViolations(List<PaymentTransaction> txns) {
        return txns.stream().noneMatch(tx -> tx.transactionId().matches("\\d{13,19}"));
    }

    /** findFirst — stops after first element that passes the filter. */
    static Optional<PaymentTransaction> oldestAuthorized(List<PaymentTransaction> txns) {
        return txns.stream()
            .filter(tx -> tx.status() == PaymentStatus.AUTHORIZED)
            .sorted()
            .findFirst();
    }

    /** limit(n) — stops after n elements. */
    static List<PaymentTransaction> preview(List<PaymentTransaction> txns, int n) {
        return txns.stream().limit(n).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // 10C – LAZY EVALUATION WITH ELEMENT COUNT
    // -----------------------------------------------------------------------

    static void demonstrateLazy(List<PaymentTransaction> txns, BigDecimal threshold) {
        long[] evaluated = {0};
        boolean found = txns.stream()
            .peek(tx -> evaluated[0]++)
            .anyMatch(tx -> tx.amount().compareTo(threshold) > 0);
        System.out.printf("  anyMatch(>₹%s): found=%-5s  elements evaluated=%d  (of %d total)%n",
            threshold, found, evaluated[0], txns.size());
    }

    // -----------------------------------------------------------------------
    // 10D – PARALLEL STREAMS: GOOD vs BAD CANDIDATES
    // -----------------------------------------------------------------------

    /**
     * ✅ Good: CPU-intensive, large dataset, STATELESS operation.
     * Cryptographic signature validation — CPU-bound, no I/O, no shared state.
     */
    static Map<String, Boolean> validateSignatures(List<PaymentTransaction> txns) {
        return txns.parallelStream()
            .collect(Collectors.toConcurrentMap(
                PaymentTransaction::transactionId,
                tx -> tx.transactionId().startsWith("tok_")  // simulates CPU-bound check
            ));
    }

    // ❌ Bad: I/O-bound — saturates DB connection pool:
    //   txns.parallelStream().forEach(tx -> database.save(tx));   // DON'T DO THIS

    // -----------------------------------------------------------------------
    // 10E – DEDICATED ForkJoinPool
    // -----------------------------------------------------------------------

    /**
     * Uses a dedicated ForkJoinPool so the JVM's shared common pool is NOT
     * starved (it is shared by Spring @Async, @Scheduled, other parallelStreams).
     *
     * PRODUCTION NOTE: Never instantiate ForkJoinPool per-request.
     * Inject as a Spring @Bean with proper lifecycle management.
     */
    static BigDecimal parallelTotalSettled(List<PaymentTransaction> txns) {
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        try {
            return pool.submit(() ->
                txns.parallelStream()
                    .filter(tx -> tx.status() == PaymentStatus.SETTLED)
                    .map(PaymentTransaction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel aggregation failed", e);
        } finally {
            pool.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 10 – STREAM PERFORMANCE OPTIMIZATION");

        Currency INR  = Currency.getInstance("INR");
        Instant  base = Instant.parse("2024-11-15T10:00:00Z");

        List<PaymentTransaction> txns = new ArrayList<>();
        PaymentStatus[] statuses = {PaymentStatus.SETTLED, PaymentStatus.SETTLED,
                                     PaymentStatus.AUTHORIZED, PaymentStatus.FAILED};
        String[] merchants = {"merch_swiggy","merch_amazon","merch_flipkart","merch_zomato"};
        for (int i = 0; i < 200; i++)
            txns.add(new PaymentTransaction("tok_" + String.format("%05d", i),
                new BigDecimal((i + 1) * 100), INR, base.plusSeconds(i * 10L),
                statuses[i % 4], PaymentMethod.UPI, merchants[i % 4], "cust_" + i));
        print("  Batch: " + txns.size() + " transactions");

        sep("10A) Primitive streams — no boxing overhead");
        print("  mapToLong().sum()      = " + totalAmountPrimitive(txns));
        LongSummaryStatistics s = amountSummary(txns);
        System.out.printf("  summaryStatistics:  count=%d | avg=%.0f | min=%d | max=%d%n",
            s.getCount(), s.getAverage(), s.getMin(), s.getMax());
        print("  ✅ ~3× faster than Stream<Long> — zero boxing allocations");
        print("  ⚠️  mapToInt() overflows at ₹21,474,836 — always use mapToLong() in payments");

        sep("10B) Short-circuiting — stops at first match");
        print("  anyMatch(>₹19,000):    " + hasHighValueTransaction(txns, new BigDecimal("19000")));
        print("  allMatch(SETTLED):     " + allSettled(txns) + "  (false — mixed statuses)");
        print("  noneMatch(raw PAN):    " + noPanViolations(txns) + "  ✅");
        print("  findFirst(AUTHORIZED): " + oldestAuthorized(txns).map(PaymentTransaction::transactionId));
        print("  limit(3) preview:      " + preview(txns,3).stream()
            .map(PaymentTransaction::transactionId).collect(Collectors.joining(", ")));
        print("");
        print("  Operation  │ Stops when      │ Payment use case");
        print("  ───────────┼─────────────────┼─────────────────────────────────");
        print("  anyMatch   │ First true      │ 'Does any txn exceed daily limit?'");
        print("  allMatch   │ First false     │ 'Are all txns validated?'");
        print("  noneMatch  │ First true      │ 'Are there no PAN violations?'");
        print("  findFirst  │ First element   │ 'Get oldest unprocessed txn'");
        print("  limit(n)   │ After n elements│ 'Preview first 100 txns'");

        sep("10C) Lazy evaluation — pipeline halts at first anyMatch hit");
        demonstrateLazy(txns, new BigDecimal("200"));     // matches early
        demonstrateLazy(txns, new BigDecimal("99999999")); // no match — scans all

        sep("10D) Parallel streams — CPU-intensive, stateless");
        Map<String, Boolean> sigs = validateSignatures(txns);
        long valid = sigs.values().stream().filter(v -> v).count();
        print("  Parallel signature check: " + valid + "/" + txns.size() + " valid  ✅");
        print("  ❌ Never parallelStream() for: DB I/O, stateful ops, small datasets (<10K)");

        sep("10E) Dedicated ForkJoinPool — shared pool not starved");
        BigDecimal pTotal = parallelTotalSettled(txns);
        BigDecimal sTotal = txns.stream().filter(tx -> tx.status() == PaymentStatus.SETTLED)
            .map(PaymentTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        print("  Dedicated pool total : ₹" + pTotal);
        print("  Sequential total     : ₹" + sTotal);
        print("  Results match        : " + pTotal.equals(sTotal) + "  ✅");

        sep("Pipeline optimization summary");
        print("  Technique               │ Speedup      │ When to apply");
        print("  ────────────────────────┼──────────────┼─────────────────────────────");
        print("  Primitive streams       │ 2–3×         │ Numeric ops on large sets");
        print("  Short-circuiting        │ 10–10,000×   │ Existence checks / early exit");
        print("  Filter early (cheap)    │ 1.5–2×       │ Multiple filters, one cheap");
        print("  Single-pass collect     │ 2–3×         │ count+sum+avg simultaneously");
        print("  Parallel + ded. pool    │ 2–4×         │ CPU-intensive, >50K elements");

        done("Section 10");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
