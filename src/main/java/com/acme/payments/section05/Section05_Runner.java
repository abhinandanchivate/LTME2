package com.acme.payments.section05;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * =======================================================================
 * SECTION 5 RUNNER – Java 8 Builder Pattern: PaymentTransaction
 * =======================================================================
 * Sub-sections:
 *   5A – Fluent Builder construction
 *   5B – Same validation rules as the Java 17 Record
 *   5C – Memoized hashCode (volatile, benign data race)
 *   5D – equals/hashCode contract — identical field sets
 *   5E – Natural ordering matches Record version
 *   5F – Migration equivalence: Java 8 class ↔ Java 17 record
 *
 * Run this section alone:
 *   java com.acme.payments.section05.Section05_Runner
 */
public class Section05_Runner {

    private static final Currency INR = Currency.getInstance("INR");

    static void demo5A() {
        sep("5A) Fluent Builder — each field named at the call site");
        PaymentTransaction tx = new PaymentTransaction.Builder()
            .transactionId("tok_abc123")
            .amount(new BigDecimal("7250.50"))
            .currency(INR)
            .timestamp(Instant.parse("2024-11-15T10:30:00Z"))
            .status(PaymentStatus.AUTHORIZED)
            .method(PaymentMethod.UPI)
            .merchantId("merch_amazon_in_001")
            .customerId("cust_tok_8b2f9a")
            .build();                // ← validation fires here

        print("  getTransactionId : " + tx.getTransactionId());
        print("  getAmount        : ₹" + tx.getAmount());
        print("  getStatus        : " + tx.getStatus());
        print("  getMethod        : " + tx.getMethod());
        print("  getMerchantId    : " + tx.getMerchantId());
    }

    static void demo5B() {
        sep("5B) Identical validation rules as the Java 17 Record");
        tryBuild("Null transactionId",
            () -> base().transactionId(null).build());
        tryBuild("Raw PAN (16 digits)",
            () -> base().transactionId("4111111111111111").build());
        tryBuild("Negative amount",
            () -> base().amount(new BigDecimal("-1")).build());
        tryBuild("Zero amount",
            () -> base().amount(BigDecimal.ZERO).build());
    }

    static void demo5C() {
        sep("5C) Memoized hashCode — volatile benign data race (Bloch EJ3 §83)");
        PaymentTransaction tx = base().build();
        int h1 = tx.hashCode(), h2 = tx.hashCode(), h3 = tx.hashCode();
        print("  Call 1 : " + h1);
        print("  Call 2 : " + h2 + "  (same — memoized on first call)");
        print("  Call 3 : " + h3 + "  (same)");
        print("  All equal: " + (h1 == h2 && h2 == h3) + "  ✅");
    }

    static void demo5D() {
        sep("5D) equals/hashCode — SAME field set in both (Principle 2)");
        PaymentTransaction tx1 = base().build();
        PaymentTransaction tx2 = base().build();

        print("  tx1.equals(tx2)       = " + tx1.equals(tx2)                    + "  (expected true)");
        print("  hashCodes match       = " + (tx1.hashCode() == tx2.hashCode()) + "  (expected true)");

        Set<PaymentTransaction> set = new HashSet<>();
        set.add(tx1); set.add(tx2);
        print("  HashSet size          = " + set.size() + "  (expected 1 — dedup works)  ✅");

        PaymentTransaction tx3 = base().amount(new BigDecimal("9999")).build();
        print("  tx1.equals(tx3)[diff] = " + tx1.equals(tx3) + "  (expected false)");
    }

    static void demo5E() {
        sep("5E) Natural ordering matches Java 17 Record (timestamp → amount → id)");
        PaymentTransaction earlier = base()
            .transactionId("tok_early")
            .timestamp(Instant.parse("2024-11-15T09:00:00Z")).build();
        PaymentTransaction later = base().build();
        int cmp = earlier.compareTo(later);
        print("  earlier.compareTo(later) = " + cmp + "  (negative = earlier sorts first)  ✅");
    }

    static void demo5F() {
        sep("5F) Migration equivalence: Java 8 class ↔ Java 17 Record");
        PaymentTransaction leg = base().build();
        com.acme.payments.section04.PaymentTransaction rec =
            new com.acme.payments.section04.PaymentTransaction(
                "tok_abc123", new BigDecimal("7250.50"), INR,
                Instant.parse("2024-11-15T10:30:00Z"),
                PaymentStatus.AUTHORIZED, PaymentMethod.UPI,
                "merch_amazon_in_001", "cust_tok_8b2f9a");

        check("transactionId", leg.getTransactionId(), rec.transactionId());
        check("amount",        leg.getAmount(),         rec.amount());
        check("currency",      leg.getCurrency(),       rec.currency());
        check("status",        leg.getStatus(),         rec.status());
        check("merchantId",    leg.getMerchantId(),     rec.merchantId());
    }

    // ---- MAIN ----
    public static void main(String[] args) {
        banner("SECTION 5 – JAVA 8 BUILDER PATTERN: PaymentTransaction");
        demo5A();
        demo5B();
        demo5C();
        demo5D();
        demo5E();
        demo5F();
        done("Section 5");
    }

    // ---- helpers ----
    static PaymentTransaction.Builder base() {
        return new PaymentTransaction.Builder()
            .transactionId("tok_abc123").amount(new BigDecimal("7250.50")).currency(INR)
            .timestamp(Instant.parse("2024-11-15T10:30:00Z"))
            .status(PaymentStatus.AUTHORIZED).method(PaymentMethod.UPI)
            .merchantId("merch_amazon_in_001").customerId("cust_tok_8b2f9a");
    }
    static void tryBuild(String label, Runnable r) {
        try { r.run(); print("  ❌ " + label + " — exception NOT thrown");
        } catch (Exception e) { print("  ✅ " + label + " rejected: " + e.getMessage()); }
    }
    static void check(String f, Object a, Object b) {
        boolean ok = java.util.Objects.equals(a, b);
        System.out.printf("  %-15s legacy=%-30s record=%-30s %s%n", f, a, b, ok ? "✅" : "❌");
    }
    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
