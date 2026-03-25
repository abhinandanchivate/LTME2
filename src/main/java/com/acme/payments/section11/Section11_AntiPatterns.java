package com.acme.payments.section11;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 11 – CRITICAL ANTI-PATTERNS
 * =======================================================================
 * Five production anti-patterns that cause real financial incidents.
 * Each shows BROKEN code, explains the failure mode, and gives the FIX.
 *
 *   AP1 – Shallow immutability with mutable components (java.util.Date)
 *   AP2 – Broken equals() symmetry with inheritance
 *   AP3 – Stateful lambda in parallel streams (race condition)
 *   AP4 – Optional as parameter or field
 *   AP5 – Missing deep immutability in nested collections (SettlementReport)
 *
 * Run this section alone:
 *   java com.acme.payments.section11.Section11_AntiPatterns
 */
public class Section11_AntiPatterns {

    // -----------------------------------------------------------------------
    // AP1 – SHALLOW IMMUTABILITY WITH MUTABLE COMPONENTS
    // -----------------------------------------------------------------------

    /** ❌ BROKEN: final blocks reassignment, NOT mutation of the Date object. */
    static final class BrokenLegacyTx {
        private final Date timestamp;
        BrokenLegacyTx(Date ts) { this.timestamp = ts; }          // no copy
        public Date getTimestamp() { return timestamp; }           // returns internal ref
    }

    /** ✅ FIX: Defensive copy on input AND on output. */
    static final class SafeLegacyTx {
        private final Date timestamp;
        SafeLegacyTx(Date ts) { this.timestamp = new Date(ts.getTime()); }   // copy in
        public Date getTimestamp() { return new Date(timestamp.getTime()); }  // copy out
    }

    static void ap1() {
        sep("AP1) Shallow immutability — java.util.Date is mutable");
        Date d = new Date(1_700_000_000_000L);
        BrokenLegacyTx broken = new BrokenLegacyTx(d);
        SafeLegacyTx   safe   = new SafeLegacyTx(d);

        d.setTime(0); // caller mutates the original Date after construction

        print("  ❌ BrokenLegacyTx.getTimestamp() = " + broken.getTimestamp().getTime()
            + "  (changed to 0 — internal state corrupted!)");
        print("  ✅ SafeLegacyTx.getTimestamp()   = " + safe.getTimestamp().getTime()
            + "  (original value preserved)");
        print("  ✅ Best fix: use Instant (immutable by design — java.time.Instant.now())");
    }

    // -----------------------------------------------------------------------
    // AP2 – BROKEN equals() SYMMETRY WITH INHERITANCE
    // -----------------------------------------------------------------------

    static class ParentTx {
        final String id;
        ParentTx(String id) { this.id = id; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ParentTx)) return false;
            return this.id.equals(((ParentTx) o).id);  // passes for ALL ParentTx subtypes
        }
        @Override public int hashCode() { return Objects.hash(id); }
    }

    static class ChildRefundTx extends ParentTx {
        final String origId;
        ChildRefundTx(String id, String origId) { super(id); this.origId = origId; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ChildRefundTx)) return false; // stricter than parent
            return super.equals(o) && this.origId.equals(((ChildRefundTx) o).origId);
        }
        @Override public int hashCode() { return Objects.hash(id, origId); }
    }

    /** ✅ FIX: Composition over inheritance. Records are final — symmetry impossible to break. */
    record RefundTransaction(PaymentTransaction original, BigDecimal refundAmount, Instant refundTime) {}

    static void ap2() {
        sep("AP2) Broken equals() symmetry — inheritance pitfall");
        ParentTx pt = new ParentTx("tx_123");
        ChildRefundTx rt = new ChildRefundTx("tx_123", "orig_456");

        print("  pt.equals(rt) = " + pt.equals(rt) + "  (true — parent checks ID only)");
        print("  rt.equals(pt) = " + rt.equals(pt) + "  (false — child checks type too — ASYMMETRIC!)");

        Set<Object> set = new HashSet<>();
        set.add(rt);
        print("  set.contains(pt) = " + set.contains(pt) + "  (false — wrong bucket! hashCodes differ)");
        set.add(pt);
        print("  set.size()       = " + set.size() + "  (2 — DUPLICATE! merchant pays twice)");
        print("  ✅ Fix: Records are final — cannot be subclassed → symmetry violation impossible");
    }

    // -----------------------------------------------------------------------
    // AP3 – STATEFUL LAMBDA IN PARALLEL STREAMS
    // -----------------------------------------------------------------------

    static BigDecimal brokenParallelSum(List<PaymentTransaction> batch) {
        BigDecimal[] total = {BigDecimal.ZERO};
        batch.parallelStream().forEach(tx -> total[0] = total[0].add(tx.amount())); // RACE CONDITION
        return total[0];
    }

    static BigDecimal correctParallelSum(List<PaymentTransaction> batch) {
        return batch.parallelStream()
            .map(PaymentTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add); // stateless — associative — deterministic
    }

    static void ap3() {
        sep("AP3) Stateful lambda in parallel stream — race condition");

        Currency INR = Currency.getInstance("INR");
        List<PaymentTransaction> batch = new ArrayList<>();
        for (int i = 1; i <= 100; i++)
            batch.add(new PaymentTransaction("tok_" + i, new BigDecimal(i * 100),
                INR, Instant.now(), PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c"));

        BigDecimal expected = batch.stream()
            .map(PaymentTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<BigDecimal> brokenResults = new HashSet<>();
        for (int i = 0; i < 5; i++) brokenResults.add(brokenParallelSum(batch));
        BigDecimal correct = correctParallelSum(batch);

        print("  Expected (sequential):    ₹" + expected);
        print("  ❌ Broken parallel runs → " + brokenResults.size() + " distinct result(s): " + brokenResults);
        print("     (race condition may not always fire in single JVM, but IS non-deterministic)");
        print("  ✅ Correct reduce:        ₹" + correct);
        print("  Correct == expected:      " + correct.equals(expected) + "  ✅");
    }

    // -----------------------------------------------------------------------
    // AP4 – Optional AS PARAMETER OR FIELD
    // -----------------------------------------------------------------------

    /** ❌ Optional as parameter — forces wrapping; caller can still pass null Optional. */
    static void brokenProcess(Optional<PaymentTransaction> txOpt) {
        txOpt.ifPresent(tx -> System.out.println("  Processing: " + tx.transactionId()));
    }

    /** ✅ Fix: direct parameter with requireNonNull. */
    static void correctProcess(PaymentTransaction tx) {
        Objects.requireNonNull(tx, "tx must not be null");
        System.out.println("  Processing: " + tx.transactionId());
    }

    /** ❌ Optional as record field — Jackson serializes as {"present":false}. */
    record BrokenTxRecord(String id, Optional<BigDecimal> refundAmount) {}

    /** ✅ Fix: nullable field + accessor returns Optional on demand. */
    record CorrectTxRecord(String id, BigDecimal refundAmount) {
        Optional<BigDecimal> getRefundAmount() { return Optional.ofNullable(refundAmount); }
    }

    static void ap4() {
        sep("AP4) Optional as parameter or field");

        Currency INR = Currency.getInstance("INR");
        PaymentTransaction tx = new PaymentTransaction("tok_ap4", new BigDecimal("5000"),
            INR, Instant.now(), PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c");

        print("  ❌ Optional as parameter:");
        brokenProcess(Optional.of(tx));
        // Passing null Optional — compiler allows it, but causes NPE at runtime
        try { brokenProcess(null); }
        catch (NullPointerException e) {
            print("  ❌ brokenProcess(null) → NullPointerException! Caller bypassed 'Optional' safety.");
        }

        print("  ✅ Direct parameter:");
        correctProcess(tx);
        try { correctProcess(null); }
        catch (NullPointerException e) { print("  ✅ null rejected: " + e.getMessage()); }

        BrokenTxRecord  bad  = new BrokenTxRecord("tok_x", Optional.empty());
        CorrectTxRecord good = new CorrectTxRecord("tok_x", null);
        print("  ❌ Optional field:        " + bad.refundAmount()       + "  (ugly Optional object as field)");
        print("  ✅ Nullable + accessor:   " + good.getRefundAmount()   + "  (Optional.empty — correct)");
    }

    // -----------------------------------------------------------------------
    // AP5 – MISSING DEEP IMMUTABILITY IN NESTED COLLECTIONS
    // -----------------------------------------------------------------------

    /** ❌ Caller's list and record share the same reference. */
    record BrokenBatch(String id, List<PaymentTransaction> transactions) {}

    /** ✅ List.copyOf() severs the caller reference completely. */
    record SafeBatch(String id, List<PaymentTransaction> transactions) {
        public SafeBatch { transactions = List.copyOf(transactions); }
    }

    /**
     * Refactor challenge from PDF:
     * SettlementReport holds Map<String, List<PaymentTransaction>> transactionsByMerchant.
     * BOTH Map.copyOf() AND List.copyOf() per value are required for true immutability.
     */
    record SafeSettlementReport(Map<String, List<PaymentTransaction>> transactionsByMerchant) {
        public SafeSettlementReport {
            Map<String, List<PaymentTransaction>> copy = new LinkedHashMap<>();
            transactionsByMerchant.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            transactionsByMerchant = Map.copyOf(copy);
        }
    }

    static void ap5() {
        sep("AP5) Missing deep immutability in collections");

        Currency INR = Currency.getInstance("INR");
        PaymentTransaction tx = new PaymentTransaction("tok_ap5", new BigDecimal("5000"),
            INR, Instant.now(), PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c");

        List<PaymentTransaction> mutable = new ArrayList<>(List.of(tx));
        BrokenBatch bb = new BrokenBatch("batch_01", mutable);
        SafeBatch   sb = new SafeBatch("batch_01", mutable);

        mutable.clear(); // caller clears after passing

        print("  ❌ BrokenBatch.transactions().size() = " + bb.transactions().size()
            + "  (expected 1 — list was shared!)");
        print("  ✅ SafeBatch.transactions().size()   = " + sb.transactions().size()
            + "  (unchanged — List.copyOf protected it)");

        // SettlementReport refactor challenge
        Map<String, List<PaymentTransaction>> map = new HashMap<>();
        map.put("merch_swiggy", new ArrayList<>(List.of(tx)));
        SafeSettlementReport report = new SafeSettlementReport(map);

        map.get("merch_swiggy").clear(); // mutate inner list
        map.clear();                     // mutate outer map

        print("  ✅ SafeSettlementReport after double mutation:");
        print("     merchants remaining : " + report.transactionsByMerchant().keySet());
        print("     swiggy txns         : " + report.transactionsByMerchant().get("merch_swiggy").size());
        try { report.transactionsByMerchant().put("hack", List.of()); }
        catch (UnsupportedOperationException e) { print("     outer Map is unmodifiable  ✅"); }
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 11 – CRITICAL ANTI-PATTERNS (AP1–AP5)");
        ap1();
        ap2();
        ap3();
        ap4();
        ap5();
        done("Section 11");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
