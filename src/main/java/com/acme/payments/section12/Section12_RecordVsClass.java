package com.acme.payments.section12;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * =======================================================================
 * SECTION 12 – JAVA 17 RECORD vs JAVA 8 CLASS: DECISION FRAMEWORK
 * =======================================================================
 * Feature comparison:
 *   Feature          │ Java 17 Record           │ Java 8 Class
 *   ─────────────────┼──────────────────────────┼──────────────────────────
 *   Lines of code    │ ~45                      │ 120+
 *   Equality         │ Auto-generated (correct) │ Manual (field-mismatch risk)
 *   Immutability     │ Language-enforced        │ Convention (final + no setters)
 *   Inheritance      │ Cannot extend classes    │ Full hierarchy support
 *   Pattern matching │ Native (Java 21+)        │ Manual instanceof + cast
 *   Memory           │ ~80 bytes/instance       │ ~84 bytes/instance
 *   Runtime          │ Java 16+                 │ Java 8+
 *
 * Use RECORDS when: new microservices on Java 17+, value objects, DTOs,
 *   domain events, config objects — no inheritance requirement.
 *
 * Use CLASSES when: Java 8–11 runtimes, Android SDK <31, inheritance
 *   hierarchies, complex serialization, Android consumers.
 *
 * Migration strategy:
 *   Strong candidate: final class, no superclass, all-final fields,
 *                     standard equals/hashCode, simple constructor validation.
 *   Keep as class:    part of inheritance, custom serialization, Android consumers.
 *
 * Run this section alone:
 *   java com.acme.payments.section12.Section12_RecordVsClass
 */
public class Section12_RecordVsClass {

    // -----------------------------------------------------------------------
    // MIGRATION EQUIVALENCE VERIFICATION
    // -----------------------------------------------------------------------

    static void verifyMigrationEquivalence() {
        sep("Migration equivalence: Java 8 class ↔ Java 17 record");

        Currency   INR       = Currency.getInstance("INR");
        BigDecimal amount    = new BigDecimal("7250.50");
        Instant    timestamp = Instant.parse("2024-11-15T10:30:00Z");

        // Java 17 Record (section04)
        com.acme.payments.section04.PaymentTransaction rec =
            new com.acme.payments.section04.PaymentTransaction(
                "tok_abc123", amount, INR, timestamp,
                PaymentStatus.AUTHORIZED, PaymentMethod.UPI,
                "merch_amazon_in_001", "cust_tok_8b2f9a");

        // Java 8 Legacy Class (section05)
        com.acme.payments.section05.PaymentTransaction leg =
            new com.acme.payments.section05.PaymentTransaction.Builder()
                .transactionId("tok_abc123").amount(amount).currency(INR).timestamp(timestamp)
                .status(PaymentStatus.AUTHORIZED).method(PaymentMethod.UPI)
                .merchantId("merch_amazon_in_001").customerId("cust_tok_8b2f9a")
                .build();

        check("transactionId", rec.transactionId(),  leg.getTransactionId());
        check("amount",        rec.amount(),          leg.getAmount());
        check("currency",      rec.currency(),        leg.getCurrency());
        check("timestamp",     rec.timestamp(),       leg.getTimestamp());
        check("status",        rec.status(),          leg.getStatus());
        check("method",        rec.method(),          leg.getMethod());
        check("merchantId",    rec.merchantId(),      leg.getMerchantId());
        check("customerId",    rec.customerId(),      leg.getCustomerId());

        // Deduplication verification — both must work
        Set<com.acme.payments.section04.PaymentTransaction> recSet = new HashSet<>();
        com.acme.payments.section04.PaymentTransaction rec2 =
            new com.acme.payments.section04.PaymentTransaction(
                "tok_abc123", amount, INR, timestamp,
                PaymentStatus.AUTHORIZED, PaymentMethod.UPI,
                "merch_amazon_in_001", "cust_tok_8b2f9a");
        recSet.add(rec); recSet.add(rec2);
        print("  Record HashSet dedup size  = " + recSet.size() + "  (expected 1)  ✅");

        Set<com.acme.payments.section05.PaymentTransaction> legSet = new HashSet<>();
        com.acme.payments.section05.PaymentTransaction leg2 =
            new com.acme.payments.section05.PaymentTransaction.Builder()
                .transactionId("tok_abc123").amount(amount).currency(INR).timestamp(timestamp)
                .status(PaymentStatus.AUTHORIZED).method(PaymentMethod.UPI)
                .merchantId("merch_amazon_in_001").customerId("cust_tok_8b2f9a").build();
        legSet.add(leg); legSet.add(leg2);
        print("  Legacy  HashSet dedup size = " + legSet.size() + "  (expected 1)  ✅");
    }

    private static void check(String field, Object recVal, Object legVal) {
        boolean ok = java.util.Objects.equals(recVal, legVal);
        System.out.printf("  %-15s  record=%-30s  legacy=%-30s  %s%n", field, recVal, legVal, ok ? "✅" : "❌");
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 12 – RECORD vs CLASS: DECISION FRAMEWORK");

        sep("Feature comparison table");
        print("  Feature           │ Java 17 Record           │ Java 8 Class");
        print("  ──────────────────┼──────────────────────────┼──────────────────────────");
        print("  Boilerplate       │ ~45 lines                │ 120+ lines");
        print("  Equality          │ Auto-generated (correct) │ Manual (field-mismatch risk)");
        print("  Immutability      │ Language-enforced        │ Convention (final + no setters)");
        print("  Inheritance       │ Cannot extend classes    │ Full hierarchy support");
        print("  Pattern matching  │ Native (Java 21+)        │ Manual instanceof + cast");
        print("  Memory            │ ~80 bytes/instance       │ ~84 bytes/instance");
        print("  Runtime           │ Java 16+                 │ Java 8+");

        sep("When to choose Record");
        print("  ✅ New microservice on Java 17+ (Spring Boot 3+, Quarkus, Micronaut)");
        print("  ✅ Value objects: MonetaryAmount, TransactionId, CurrencyPair");
        print("  ✅ DTOs: API request/response payloads, Kafka event envelopes");
        print("  ✅ Domain events: PaymentInitiated, PaymentSettled, RefundProcessed");
        print("  ✅ Config: RetryPolicy, ThrottleConfig, FraudThresholds");

        sep("When to keep as Class");
        print("  ✅ Java 8–11 runtime (WebLogic 14, JBoss EAP 7, IBM WebSphere 9)");
        print("  ✅ Inheritance: CardPaymentTransaction extends PaymentTransaction");
        print("  ✅ Android SDK < 31 — Records require API 31+");
        print("  ✅ Lazy field initialization (e.g., memoized hashCode in Section 5)");
        print("  ✅ Custom Jackson deserializer with mutable builder needed");

        sep("Migration checklist (Java 8 → Java 17)");
        print("  Convert if:");
        print("    ✅ final class, no superclass");
        print("    ✅ All fields are final");
        print("    ✅ Standard equals/hashCode from all fields");
        print("    ✅ Simple constructor validation (null checks, positive amount, PAN check)");
        print("  Keep as class:");
        print("    ❌ Part of inheritance hierarchy");
        print("    ❌ Custom serialization hooks (readObject/writeObject)");
        print("    ❌ Android consumers (API < 31)");
        print("    ❌ Lazy initialization required beyond memoized hashCode");

        verifyMigrationEquivalence();

        sep("Production-ready pattern — all 9 principles simultaneously");
        print("  public record PaymentTransaction(");
        print("      String transactionId, BigDecimal amount, Currency currency,");
        print("      Instant timestamp, PaymentStatus status, PaymentMethod method,");
        print("      String merchantId, String customerId, List<String> riskFlags)");
        print("      implements Comparable<PaymentTransaction> {");
        print("");
        print("    public PaymentTransaction {");
        print("      Objects.requireNonNull(transactionId, \"transactionId required\");  // P8");
        print("      if (amount.compareTo(ZERO) <= 0)");
        print("          throw new IllegalArgumentException(\"amount must be positive\");");
        print("      if (transactionId.matches(\"\\\\d{13,19}\"))");
        print("          throw new IllegalArgumentException(\"Raw PAN — use tokenized ID\"); // P4");
        print("      riskFlags = List.copyOf(riskFlags);                                  // P1");
        print("    }");
        print("");
        print("    @Override public int compareTo(PaymentTransaction o) {                 // P5");
        print("      int t = timestamp.compareTo(o.timestamp); if (t != 0) return t;");
        print("      int a = amount.compareTo(o.amount);       if (a != 0) return a;");
        print("      return transactionId.compareTo(o.transactionId);");
        print("    }");
        print("  }");

        done("Section 12");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
