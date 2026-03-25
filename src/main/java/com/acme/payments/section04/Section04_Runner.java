package com.acme.payments.section04;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * =======================================================================
 * SECTION 4 RUNNER – Java 17 Record: PaymentTransaction
 * =======================================================================
 * Demonstrates every feature of the PaymentTransaction record.
 *
 * Sub-sections:
 *   4A – Valid construction and field access
 *   4B – Validation guards (null, negative, raw PAN)
 *   4C – Deep immutability of riskFlags
 *   4D – Auto-generated structural equality (equals / hashCode)
 *   4E – HashSet deduplication (reconciliation safety)
 *   4F – Optional.getRefundAmount()
 *   4G – Natural ordering (timestamp → amount → id)
 *   4H – Exhaustive switch routing (Java 17 pattern-matching)
 *   4I – businessTime() UTC → IST conversion
 *
 * Run this section alone:
 *   java com.acme.payments.section04.Section04_Runner
 */
public class Section04_Runner {

    private static final Currency INR = Currency.getInstance("INR");
    private static final ZoneId   IST = ZoneId.of("Asia/Kolkata");

    // -----------------------------------------------------------------------
    // 4A – VALID CONSTRUCTION
    // -----------------------------------------------------------------------
    static void demo4A() {
        sep("4A) Valid construction and field access");

        PaymentTransaction tx = new PaymentTransaction(
            "tok_abc123",
            new BigDecimal("7250.50"),
            INR,
            Instant.parse("2024-11-15T10:30:00Z"),
            PaymentStatus.AUTHORIZED,
            PaymentMethod.UPI,
            "merch_amazon_in_001",
            "cust_tok_8b2f9a",
            List.of("high_velocity")
        );
        print("  transactionId : " + tx.transactionId());
        print("  amount        : ₹" + tx.amount());
        print("  currency      : " + tx.currency().getCurrencyCode());
        print("  timestamp     : " + tx.timestamp() + "  (stored as UTC Instant)");
        print("  status        : " + tx.status());
        print("  method        : " + tx.method());
        print("  merchantId    : " + tx.merchantId());
        print("  customerId    : " + tx.customerId());
        print("  riskFlags     : " + tx.riskFlags());
    }

    // -----------------------------------------------------------------------
    // 4B – VALIDATION GUARDS
    // -----------------------------------------------------------------------
    static void demo4B() {
        sep("4B) Constructor validation — every guard fires independently");

        tryBuild("Null transactionId",
            () -> new PaymentTransaction(null, BigDecimal.ONE, INR, Instant.now(),
                    PaymentStatus.AUTHORIZED, PaymentMethod.UPI, "m", "c"));

        tryBuild("Raw Visa PAN (16 digits)",
            () -> new PaymentTransaction("4111111111111111", BigDecimal.ONE, INR, Instant.now(),
                    PaymentStatus.AUTHORIZED, PaymentMethod.UPI, "m", "c"));

        tryBuild("Raw Amex PAN (15 digits)",
            () -> new PaymentTransaction("371449635398431", BigDecimal.ONE, INR, Instant.now(),
                    PaymentStatus.AUTHORIZED, PaymentMethod.UPI, "m", "c"));

        tryBuild("Negative amount",
            () -> new PaymentTransaction("tok_x", new BigDecimal("-500"), INR, Instant.now(),
                    PaymentStatus.AUTHORIZED, PaymentMethod.UPI, "m", "c"));

        tryBuild("Zero amount",
            () -> new PaymentTransaction("tok_x", BigDecimal.ZERO, INR, Instant.now(),
                    PaymentStatus.AUTHORIZED, PaymentMethod.UPI, "m", "c"));
    }

    // -----------------------------------------------------------------------
    // 4C – DEEP IMMUTABILITY OF riskFlags
    // -----------------------------------------------------------------------
    static void demo4C() {
        sep("4C) Deep immutability — riskFlags isolated from caller mutations");

        List<String> original = new ArrayList<>(List.of("high_velocity", "unusual_location"));
        PaymentTransaction tx = new PaymentTransaction(
            "tok_risk_01", new BigDecimal("50000"), INR, Instant.now(),
            PaymentStatus.AUTHORIZED, PaymentMethod.CREDIT_CARD, "merch_01", "cust_01",
            original);

        // Mutate the original list in every possible way
        original.add("card_testing");
        original.remove("high_velocity");

        print("  Caller list after mutation : " + original);
        print("  tx.riskFlags() unchanged   : " + tx.riskFlags() + "  ✅");

        try { tx.riskFlags().add("hack"); }
        catch (UnsupportedOperationException e) {
            print("  tx.riskFlags().add() blocked: UnsupportedOperationException  ✅");
        }
    }

    // -----------------------------------------------------------------------
    // 4D + 4E – STRUCTURAL EQUALITY AND HashSet DEDUPLICATION
    // -----------------------------------------------------------------------
    static void demo4D() {
        sep("4D+4E) Auto-generated equals/hashCode — duplicate settlement prevented");

        Instant ts = Instant.parse("2024-11-15T10:00:00Z");
        PaymentTransaction t1 = new PaymentTransaction(
            "tok_dup", new BigDecimal("1000"), INR, ts,
            PaymentStatus.SETTLED, PaymentMethod.UPI, "merch_01", "cust_01");
        PaymentTransaction t2 = new PaymentTransaction(
            "tok_dup", new BigDecimal("1000"), INR, ts,
            PaymentStatus.SETTLED, PaymentMethod.UPI, "merch_01", "cust_01");

        print("  t1.equals(t2)            = " + t1.equals(t2)                    + "  (expected true)");
        print("  hashCodes match          = " + (t1.hashCode() == t2.hashCode()) + "  (expected true)");

        Set<PaymentTransaction> set = new HashSet<>();
        set.add(t1); set.add(t2);
        print("  HashSet size after 2 adds = " + set.size() + "  (expected 1 — no duplicate settlement) ✅");

        // Different amounts → not equal
        PaymentTransaction t3 = new PaymentTransaction(
            "tok_dup", new BigDecimal("2000"), INR, ts,
            PaymentStatus.SETTLED, PaymentMethod.UPI, "merch_01", "cust_01");
        print("  t1.equals(t3) [diff amt]  = " + t1.equals(t3) + "  (expected false)");
    }

    // -----------------------------------------------------------------------
    // 4F – OPTIONAL getRefundAmount()
    // -----------------------------------------------------------------------
    static void demo4F() {
        sep("4F) Optional.getRefundAmount() — return type, never field");

        PaymentTransaction settled = new PaymentTransaction(
            "tok_settled", new BigDecimal("5000"), INR, Instant.now(),
            PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c");
        PaymentTransaction refunded = new PaymentTransaction(
            "tok_refunded", new BigDecimal("3500"), INR, Instant.now(),
            PaymentStatus.REFUNDED, PaymentMethod.WALLET, "m", "c");

        settled.getRefundAmount()
            .ifPresentOrElse(
                a -> print("  SETTLED  refund: ₹" + a),
                ()  -> print("  SETTLED  refund: Optional.empty()  ✅"));
        refunded.getRefundAmount()
            .ifPresent(a -> print("  REFUNDED refund: ₹" + a + "  ✅"));
    }

    // -----------------------------------------------------------------------
    // 4G – NATURAL ORDERING
    // -----------------------------------------------------------------------
    static void demo4G() {
        sep("4G) Natural ordering — timestamp → amount → transactionId");

        List<PaymentTransaction> unsorted = new ArrayList<>(List.of(
            new PaymentTransaction("tok_c", new BigDecimal("5000"), INR,
                Instant.parse("2024-11-15T10:30:00Z"), PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c"),
            new PaymentTransaction("tok_a", new BigDecimal("9000"), INR,
                Instant.parse("2024-11-15T10:28:00Z"), PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c"),
            new PaymentTransaction("tok_b", new BigDecimal("3000"), INR,
                Instant.parse("2024-11-15T10:28:00Z"), PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c")
        ));
        Collections.sort(unsorted);
        print("  Sorted output:");
        for (PaymentTransaction tx : unsorted)
            print("    " + tx.timestamp() + " | ₹" + tx.amount() + " | " + tx.transactionId());
    }

    // -----------------------------------------------------------------------
    // 4H – EXHAUSTIVE SWITCH ROUTING (Java 17)
    // -----------------------------------------------------------------------
    static void demo4H() {
        sep("4H) Exhaustive switch — adding a PaymentStatus causes compile error");

        for (PaymentStatus s : PaymentStatus.values()) {
            PaymentTransaction tx = new PaymentTransaction(
                "tok_sw_" + s, new BigDecimal("100"), INR, Instant.now(),
                s, PaymentMethod.UPI, "m", "c");
            String route = switch (tx.status()) {
                case AUTHORIZED -> "initiateSettlement()";
                case SETTLED    -> "updateLedger()";
                case REFUNDED   -> "processRefund()";
                case FAILED     -> "triggerFraudReview()";
            };
            print("  " + s + " → " + route);
        }
    }

    // -----------------------------------------------------------------------
    // 4I – businessTime() UTC → IST
    // -----------------------------------------------------------------------
    static void demo4I() {
        sep("4I) businessTime() — UTC Instant → IST ZonedDateTime for reporting");

        PaymentTransaction tx = new PaymentTransaction(
            "tok_ist", new BigDecimal("1000"), INR,
            Instant.parse("2024-11-15T10:30:00Z"),
            PaymentStatus.SETTLED, PaymentMethod.UPI, "m", "c");

        var biz = tx.businessTime(IST);
        print("  Stored UTC  : " + tx.timestamp());
        print("  IST display : " + biz + "  (UTC 10:30 → IST 16:00 = +5:30)");
        print("  IST date    : " + biz.toLocalDate() + "  (prevents off-by-one at midnight)  ✅");
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 4 – JAVA 17 RECORD: PaymentTransaction");

        demo4A();
        demo4B();
        demo4C();
        demo4D();
        demo4F();
        demo4G();
        demo4H();
        demo4I();

        done("Section 4");
    }

    // ---- helpers ----
    static void tryBuild(String label, Runnable r) {
        try { r.run(); print("  ❌ " + label + " — exception NOT thrown");
        } catch (Exception e) { print("  ✅ " + label + " rejected: " + e.getMessage()); }
    }
    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
