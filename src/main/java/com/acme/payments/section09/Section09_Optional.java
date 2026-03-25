package com.acme.payments.section09;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 9 – OPTIONAL FOR SAFE DATA ACCESS
 * =======================================================================
 * Optional contract (Principle 6):
 *   ✅ Return type only — forces callers to handle absence explicitly
 *   ❌ Never a field    — breaks Jackson/JPA; 16-byte overhead per field
 *   ❌ Never a param    — caller can still pass null Optional
 *   ❌ Never .get()     — use orElse / orElseThrow / ifPresent / ifPresentOrElse
 *   ❌ Never List<Optional<T>> — use Optional.stream() + flatMap instead
 *
 * Sub-sections:
 *   9A – findById returning Optional (findFirst pattern)
 *   9B – ifPresentOrElse (Java 9+)
 *   9C – flatMap — safe nested access without NPE chains
 *   9D – orElseThrow — mandatory value with meaningful error
 *   9E – filter on Optional — threshold check without unwrapping
 *   9F – Optional.stream() + flatMap — flatten ids to found transactions
 *   9G – Anti-patterns (with explanations)
 *
 * Run this section alone:
 *   java com.acme.payments.section09.Section09_Optional
 */
public class Section09_Optional {

    private static final Currency INR = Currency.getInstance("INR");

    // -----------------------------------------------------------------------
    // 9A – findById (canonical Optional return type)
    // -----------------------------------------------------------------------

    /** findFirst() returns Optional<T> naturally — no null handling needed. */
    static Optional<PaymentTransaction> findById(List<PaymentTransaction> txns, String id) {
        return txns.stream().filter(t -> t.transactionId().equals(id)).findFirst();
    }

    // -----------------------------------------------------------------------
    // 9B – ifPresentOrElse (Java 9+)
    // -----------------------------------------------------------------------

    static void processOrWarn(List<PaymentTransaction> txns, String id) {
        findById(txns, id).ifPresentOrElse(
            tx -> System.out.println("  Processing: " + tx.transactionId()),
            ()  -> System.out.println("  ⚠️  Not found: " + id + " — verify ID and expiry")
        );
    }

    // -----------------------------------------------------------------------
    // 9C – flatMap: safe nested access without NPE chains
    // -----------------------------------------------------------------------

    /**
     * Navigates nested maps without NPE chains.
     * Each flatMap step returns Optional.empty() if the value is absent —
     * no null checks required at any level.
     */
    static Optional<String> getMerchantChannel(PaymentTransaction tx,
                                                Map<String, Map<String, String>> meta) {
        return Optional.ofNullable(tx)
            .flatMap(t  -> Optional.ofNullable(meta.get(t.merchantId())))
            .flatMap(m  -> Optional.ofNullable(m.get("channel")));
    }

    // -----------------------------------------------------------------------
    // 9D – orElseThrow — mandatory value with meaningful error
    // -----------------------------------------------------------------------

    static PaymentTransaction getRequired(List<PaymentTransaction> txns, String id) {
        return findById(txns, id)
            .orElseThrow(() -> new IllegalArgumentException(
                "Transaction not found: " + id + " — verify ID and expiry"));
    }

    // -----------------------------------------------------------------------
    // 9E – filter on Optional — threshold check without unwrapping
    // -----------------------------------------------------------------------

    static Optional<PaymentTransaction> findHighValue(List<PaymentTransaction> txns,
                                                       String id, BigDecimal threshold) {
        return findById(txns, id)
            .filter(tx -> tx.amount().compareTo(threshold) > 0);
    }

    // -----------------------------------------------------------------------
    // 9F – Optional.stream() + flatMap
    // -----------------------------------------------------------------------

    /**
     * Maps a list of IDs to found transactions, silently skipping missing ones.
     *
     * ❌ Anti-pattern: ids.stream().map(id -> findById(txns, id))
     *    Produces Stream<Optional<PaymentTransaction>> — ugly unwrapping needed.
     *
     * ✅ Correct: flatMap with Optional.stream() (Java 9+) produces
     *    Stream<PaymentTransaction> — only present values, no empty Optionals.
     */
    static List<PaymentTransaction> findAllById(List<PaymentTransaction> txns, List<String> ids) {
        return ids.stream()
            .flatMap(id -> findById(txns, id).stream())
            .collect(Collectors.toList());
    }

    /** Chains findById → getRefundAmount using nested flatMap. */
    static Optional<BigDecimal> getRefundAmount(List<PaymentTransaction> txns, String id) {
        return findById(txns, id).flatMap(PaymentTransaction::getRefundAmount);
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 9 – OPTIONAL FOR SAFE DATA ACCESS");

        Instant NOW = Instant.parse("2024-11-15T10:30:00Z");
        List<PaymentTransaction> txns = List.of(
            new PaymentTransaction("tok_upi_789",  new BigDecimal("4500.00"),   INR, NOW, PaymentStatus.SETTLED,    PaymentMethod.UPI,          "merch_swiggy_01", "cust_a"),
            new PaymentTransaction("tok_card_101", new BigDecimal("12750.50"),  INR, NOW, PaymentStatus.SETTLED,    PaymentMethod.CREDIT_CARD,  "merch_swiggy_01", "cust_b"),
            new PaymentTransaction("tok_ref_001",  new BigDecimal("3500.00"),   INR, NOW, PaymentStatus.REFUNDED,   PaymentMethod.WALLET,       "merch_zomato_01", "cust_c"),
            new PaymentTransaction("tok_high_001", new BigDecimal("150000.00"), INR, NOW, PaymentStatus.SETTLED,    PaymentMethod.BANK_TRANSFER,"merch_amazon_01", "cust_d"),
            new PaymentTransaction("tok_auth_001", new BigDecimal("500.00"),    INR, NOW, PaymentStatus.AUTHORIZED, PaymentMethod.DEBIT_CARD,   "merch_blinkit",   "cust_e")
        );

        sep("9A) findById — Optional as return type (findFirst)");
        print("  findById('tok_upi_789'): " + findById(txns,"tok_upi_789").map(PaymentTransaction::transactionId) + "  ✅");
        print("  findById('tok_MISSING'): " + findById(txns,"tok_MISSING")                                         + "  ✅");

        sep("9B) ifPresentOrElse — handles both presence and absence");
        processOrWarn(txns, "tok_card_101");
        processOrWarn(txns, "tok_MISSING_XYZ");

        sep("9C) flatMap — safe nested metadata access (no NPE chains)");
        Map<String, Map<String, String>> meta = new HashMap<>();
        meta.put("merch_swiggy_01", Map.of("channel", "MOBILE_APP", "region", "IN-MH"));
        // merch_zomato_01 deliberately has NO metadata entry
        print("  swiggy channel: " + getMerchantChannel(txns.get(0), meta) + "  ✅");
        print("  zomato channel: " + getMerchantChannel(txns.get(2), meta) + "  (no NPE — empty)  ✅");

        sep("9D) orElseThrow — mandatory value, meaningful error");
        print("  getRequired found:   " + getRequired(txns,"tok_upi_789").transactionId() + "  ✅");
        try { getRequired(txns,"tok_DOES_NOT_EXIST"); }
        catch (IllegalArgumentException e) { print("  getRequired missing: " + e.getMessage() + "  ✅"); }

        sep("9E) filter on Optional — threshold without unwrapping");
        print("  tok_high_001 (₹150K > ₹100K): " + findHighValue(txns,"tok_high_001", new BigDecimal("100000")).map(PaymentTransaction::transactionId) + "  ✅");
        print("  tok_upi_789  (₹4.5K < ₹100K): " + findHighValue(txns,"tok_upi_789",  new BigDecimal("100000")) + "  ✅");

        sep("9F) Optional.stream() + flatMap — skip missing IDs cleanly");
        List<String> ids = List.of("tok_card_101", "tok_MISSING_A", "tok_ref_001", "tok_MISSING_B");
        List<PaymentTransaction> found = findAllById(txns, ids);
        print("  Lookup: " + ids);
        print("  Found " + found.size() + " (2 missing skipped cleanly):");
        found.forEach(tx -> print("    → " + tx.transactionId()));
        getRefundAmount(txns,"tok_ref_001").ifPresent(a -> print("  Refund via chained flatMap: ₹" + a + "  ✅"));

        sep("9G) Anti-patterns — what NOT to do");
        print("  ❌ Optional as field:  record Tx(Optional<String> notes) {}");
        print("     Jackson: {\"notes\":{\"present\":false}} instead of {\"notes\":null}");
        print("     Fix:     record Tx(@Nullable String notes) { Optional<String> getNotes() {...} }");
        print("");
        print("  ❌ Optional as param:  void process(Optional<PaymentTransaction> txOpt)");
        print("     Caller can STILL pass null Optional — defeats the purpose");
        print("     Fix:     void process(PaymentTransaction tx) { Objects.requireNonNull(tx); }");
        print("");
        print("  ❌ .get() without .isPresent()  → NoSuchElementException in production");
        print("  ✅ Use .orElse() / .orElseThrow() / .ifPresent() / .ifPresentOrElse()");
        print("");
        print("  ❌ List<Optional<T>>  → use findAllById() flatMap pattern (Section 9F above)");

        done("Section 9");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
