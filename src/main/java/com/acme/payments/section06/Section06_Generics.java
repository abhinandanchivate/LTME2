package com.acme.payments.section06;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;
import com.acme.payments.section04.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 6 – GENERICS IN THE PAYMENT DOMAIN
 * =======================================================================
 * Generics provide compile-time type safety for reusable payment processing
 * components.  The compiler verifies that only the correct transaction types
 * flow through each processing stage.
 *
 * Sub-sections:
 *   6A – Bounded type parameters (T extends PaymentTransaction)
 *   6B – ProcessingResult<T> generic record
 *   6C – PECS: Producer Extends, Consumer Super
 *   6D – Generic utility methods (transform, topN, safeCast)
 *   6E – Type erasure and Class tokens
 *
 * Run this section alone:
 *   java com.acme.payments.section06.Section06_Generics
 */
public class Section06_Generics {

    // -----------------------------------------------------------------------
    // 6A – BOUNDED TYPE PARAMETERS
    // T extends PaymentTransaction ensures every T has all PT fields/methods.
    // -----------------------------------------------------------------------

    /** Generic processor interface — T must be a PaymentTransaction or subtype. */
    interface PaymentProcessor<T extends PaymentTransaction> {
        ProcessingResult<T> process(T transaction);
        ValidationResult    validate(T transaction);
        Optional<T>         findById(String transactionId);
    }

    /** Immutable validation result — nested record keeps contract co-located. */
    record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult { violations = List.copyOf(violations); }
        static ValidationResult ok()                      { return new ValidationResult(true,  List.of()); }
        static ValidationResult failed(List<String> v)   { return new ValidationResult(false, v); }
    }

    // -----------------------------------------------------------------------
    // 6B – ProcessingResult<T>
    // -----------------------------------------------------------------------

    /** Immutable result — generic over the concrete transaction subtype. */
    record ProcessingResult<T extends PaymentTransaction>(
            T                transaction,
            ProcessingStatus status,
            Optional<String> errorMessage,
            Optional<String> confirmationCode
    ) {
        enum ProcessingStatus { SUCCESS, FAILED, PENDING, REJECTED }

        public ProcessingResult {
            Objects.requireNonNull(transaction,      "transaction");
            Objects.requireNonNull(status,           "status");
            Objects.requireNonNull(errorMessage,     "errorMessage");
            Objects.requireNonNull(confirmationCode, "confirmationCode");
        }

        static <T extends PaymentTransaction> ProcessingResult<T> success(T tx, String code) {
            return new ProcessingResult<>(tx, ProcessingStatus.SUCCESS, Optional.empty(), Optional.of(code));
        }
        static <T extends PaymentTransaction> ProcessingResult<T> failure(T tx, String err) {
            return new ProcessingResult<>(tx, ProcessingStatus.FAILED, Optional.of(err), Optional.empty());
        }
        boolean isSuccessful() { return status == ProcessingStatus.SUCCESS; }
    }

    // -----------------------------------------------------------------------
    // 6C – PECS: Producer Extends, Consumer Super
    // "Producer Extends" — reading from a collection → ? extends T
    // "Consumer Super"   — writing to a collection  → ? super T
    // -----------------------------------------------------------------------

    /** Reads amounts (produces) → ? extends PaymentTransaction */
    static BigDecimal calculateTotal(List<? extends PaymentTransaction> transactions) {
        return transactions.stream()
            .map(PaymentTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Reads source (extends) and writes to destination (super). */
    static void addHighValueTransactions(
            List<? extends PaymentTransaction> source,
            List<? super PaymentTransaction>   destination,
            BigDecimal threshold) {
        source.stream()
            .filter(tx -> tx.amount().compareTo(threshold) > 0)
            .forEach(destination::add);
    }

    // -----------------------------------------------------------------------
    // 6D – GENERIC UTILITY METHODS
    // -----------------------------------------------------------------------

    /** <T,R> — two independent type params; maps List<T> → List<R>. */
    static <T, R> List<R> transform(List<T> items, Function<T, R> mapper) {
        return items.stream().map(mapper).collect(Collectors.toUnmodifiableList());
    }

    /** Bound T extends Comparable<T> ensures natural ordering exists. */
    static <T extends Comparable<T>> List<T> topN(List<T> items, int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive: " + n);
        return items.stream().sorted().limit(n).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Type-safe downcast using a Class token.
     * Used in Kafka message handlers that receive Object messages.
     * Cannot use instanceof with a generic T — type erasure makes it impossible.
     */
    static <T> Optional<T> safeCast(Object obj, Class<T> clazz) {
        return clazz.isInstance(obj) ? Optional.of(clazz.cast(obj)) : Optional.empty();
    }

    // -----------------------------------------------------------------------
    // 6E – TYPE ERASURE
    // Generic type parameters are erased at compile time.
    // The JVM sees only raw types at runtime.
    // List<PaymentTransaction> and List<String> are IDENTICAL at runtime.
    // -----------------------------------------------------------------------

    /**
     * ❌ Cannot check generic type parameter at runtime — T is erased:
     *    boolean isListOf(List<T> list, Class<T> clazz) — impossible
     *
     * ✅ Pass Class<T> as a "type token" to preserve runtime type info.
     */
    static <T> boolean containsType(List<?> list, Class<T> clazz) {
        return list.stream().anyMatch(clazz::isInstance);
    }

    // -----------------------------------------------------------------------
    // MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 6 – GENERICS IN THE PAYMENT DOMAIN");

        Currency INR = Currency.getInstance("INR");
        Instant  NOW = Instant.parse("2024-11-15T10:30:00Z");

        List<PaymentTransaction> batch = List.of(
            new PaymentTransaction("tok_001", new BigDecimal("4500"),   INR, NOW, PaymentStatus.SETTLED,     PaymentMethod.UPI,         "merch_swiggy",  "cust_a"),
            new PaymentTransaction("tok_002", new BigDecimal("12750"),  INR, NOW, PaymentStatus.SETTLED,     PaymentMethod.CREDIT_CARD, "merch_swiggy",  "cust_b"),
            new PaymentTransaction("tok_003", new BigDecimal("75000"),  INR, NOW, PaymentStatus.AUTHORIZED,  PaymentMethod.BANK_TRANSFER,"merch_amazon",  "cust_c"),
            new PaymentTransaction("tok_004", new BigDecimal("1200"),   INR, NOW, PaymentStatus.FAILED,      PaymentMethod.DEBIT_CARD,  "merch_zomato",  "cust_d")
        );

        sep("6A+6B) ProcessingResult<T> — generic over transaction subtype");
        ProcessingResult<PaymentTransaction> ok  = ProcessingResult.success(batch.get(0), "CONF_001");
        ProcessingResult<PaymentTransaction> err = ProcessingResult.failure(batch.get(3), "INSUFFICIENT_FUNDS");
        print("  success.isSuccessful()     = " + ok.isSuccessful()  + "  ✅");
        print("  success.confirmationCode() = " + ok.confirmationCode());
        print("  failure.isSuccessful()     = " + err.isSuccessful());
        print("  failure.errorMessage()     = " + err.errorMessage());

        sep("6A) ValidationResult — nested record");
        ValidationResult v1 = ValidationResult.ok();
        ValidationResult v2 = ValidationResult.failed(List.of("Amount exceeds daily limit", "Merchant blocked"));
        print("  ok.valid()           = " + v1.valid());
        print("  failed.valid()       = " + v2.valid());
        print("  failed.violations()  = " + v2.violations());

        sep("6C) PECS — Producer Extends, Consumer Super");
        BigDecimal total = calculateTotal(batch);
        print("  calculateTotal (? extends) = ₹" + total);

        List<PaymentTransaction> dest = new ArrayList<>();
        addHighValueTransactions(batch, dest, new BigDecimal("10000"));
        print("  addHighValue (? super, threshold ₹10K): " + dest.size() + " transaction(s)");
        dest.forEach(tx -> print("    → " + tx.transactionId() + " ₹" + tx.amount()));

        sep("6D) Generic utilities — transform, topN, safeCast");
        List<String> ids = transform(batch, PaymentTransaction::transactionId);
        print("  transform → ids: " + ids);

        List<PaymentTransaction> top2 = topN(new ArrayList<>(batch), 2);
        print("  topN(2) — earliest first:");
        top2.forEach(tx -> print("    " + tx.timestamp() + " | " + tx.transactionId()));

        sep("6E) Type erasure — safeCast with Class token");
        Object msg1 = batch.get(0);  // simulate Kafka Object message
        Object msg2 = "raw_string";

        safeCast(msg1, PaymentTransaction.class)
            .ifPresent(tx -> print("  safeCast OK:    " + tx.transactionId() + "  ✅"));
        safeCast(msg2, PaymentTransaction.class)
            .ifPresentOrElse(
                tx -> print("  safeCast found: " + tx),
                ()  -> print("  safeCast empty: String ≠ PaymentTransaction  ✅"));

        print("  containsType(batch, PaymentTransaction.class) = "
            + containsType(batch, PaymentTransaction.class) + "  ✅");
        print("  containsType(batch, String.class)             = "
            + containsType(batch, String.class));

        done("Section 6");
    }

    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void print(String t)  { System.out.println(t); }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
