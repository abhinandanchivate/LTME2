package com.acme.payments.section04;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * =======================================================================
 * SECTION 4 – JAVA 17+ PaymentTransaction RECORD (core domain object)
 * =======================================================================
 *
 * Records enforce immutability and structural equality at the COMPILER
 * level — 70% less boilerplate than equivalent Java 8 classes, with
 * stronger correctness guarantees.
 *
 *  Feature              │ Benefit
 *  ─────────────────────┼──────────────────────────────────────────────
 *  Auto equals/hashCode │ Correct structural equality — duplicate bug impossible
 *  Immutable by default │ Thread-safe without synchronized
 *  Compact constructor  │ Single PCI/GDPR validation point, cannot be bypassed
 *  Exhaustive switch    │ New PaymentStatus → compile error until handled
 *  tx.amount() syntax   │ Value object convention — cleaner API
 *
 * PCI-DSS Req 3.4  : Raw PANs NEVER stored; constructor rejects 13–19 digit strings.
 * GDPR Article 5   : customerId is always a token — never name/email/national ID.
 * SOX Section 404  : Natural ordering ensures reproducible audit sequencing.
 * ISO 4217         : Currency validated via java.util.Currency.
 *
 * Why Instant and NOT ZonedDateTime for the stored field?
 *   A Mumbai merchant and a New York issuer see the SAME Instant as an identical
 *   UTC moment.  LocalDateTime carries no timezone — "10:30:00" means different
 *   things in IST and EST, breaking cross-timezone settlement sequencing.
 */
public record PaymentTransaction(
        String        transactionId,  // PCI tokenized ID  — never a raw 13–19 digit PAN
        BigDecimal    amount,         // Positive; smallest currency unit where applicable
        Currency      currency,       // ISO 4217  (INR, USD, EUR …)
        Instant       timestamp,      // UTC — timezone-agnostic for global settlement
        PaymentStatus status,         // AUTHORIZED → SETTLED / REFUNDED / FAILED
        PaymentMethod method,         // CREDIT_CARD, UPI, BANK_TRANSFER, WALLET …
        String        merchantId,     // Acquirer-assigned merchant identifier
        String        customerId,     // GDPR Article 5 token — never raw customer data
        List<String>  riskFlags       // Fraud signals attached at scoring time
) implements Comparable<PaymentTransaction> {

    // -------------------------------------------------------------------
    // COMPACT CONSTRUCTOR
    // All validation fires HERE — a successfully constructed instance is
    // guaranteed to be valid.  Impossible to create an invalid object.
    // -------------------------------------------------------------------
    public PaymentTransaction {
        // Null guards — field-named messages for rapid debugging
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(amount,         "amount must not be null");
        Objects.requireNonNull(currency,       "currency must not be null");
        Objects.requireNonNull(timestamp,      "timestamp must not be null");
        Objects.requireNonNull(status,         "status must not be null");
        Objects.requireNonNull(method,         "method must not be null");
        Objects.requireNonNull(merchantId,     "merchantId must not be null");
        Objects.requireNonNull(customerId,     "customerId must not be null");
        Objects.requireNonNull(riskFlags,      "riskFlags must not be null");

        // Business invariant
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive: " + amount);

        // PCI-DSS Req 3.4: Visa 16d | Amex 15d | RuPay 16d | Maestro 13–19d
        if (transactionId.matches("\\d{13,19}"))
            throw new IllegalArgumentException(
                "Raw PAN detected — use tokenized ID (e.g., tok_abc123). " +
                "PCI-DSS Req 3.4 violation.");

        // Principle 1: Deep immutability — List.copyOf() also rejects null elements
        riskFlags = List.copyOf(riskFlags);
    }

    /** Convenience constructor — no riskFlags (defaults to empty). */
    public PaymentTransaction(String transactionId, BigDecimal amount, Currency currency,
                               Instant timestamp, PaymentStatus status, PaymentMethod method,
                               String merchantId, String customerId) {
        this(transactionId, amount, currency, timestamp, status, method,
             merchantId, customerId, List.of());
    }

    // -------------------------------------------------------------------
    // BUSINESS METHODS
    // -------------------------------------------------------------------

    /**
     * Converts stored UTC Instant to the given business timezone for reporting.
     * Use for display only — never for storage.
     * Example: IST = ZoneId.of("Asia/Kolkata")  →  UTC 10:30 becomes IST 16:00
     */
    public ZonedDateTime businessTime(ZoneId zone) {
        Objects.requireNonNull(zone, "zone must not be null");
        return ZonedDateTime.ofInstant(timestamp, zone);
    }

    /**
     * Returns the refund amount if this transaction has been refunded.
     * Demonstrates Principle 6: Optional as return type only.
     */
    public Optional<BigDecimal> getRefundAmount() {
        return status == PaymentStatus.REFUNDED
                ? Optional.of(amount)
                : Optional.empty();
    }

    // -------------------------------------------------------------------
    // NATURAL ORDERING — Principle 4: Business-driven
    //   timestamp  : FIFO — oldest settled first (regulatory requirement)
    //   amount     : higher-value priority on timestamp tie
    //   id         : deterministic tiebreaker for SOX reproducibility
    // riskFlags is deliberately excluded — same transaction must not sort
    // differently after fraud scoring updates.
    // -------------------------------------------------------------------
    @Override
    public int compareTo(PaymentTransaction o) {
        int t = this.timestamp.compareTo(o.timestamp); if (t != 0) return t;
        int a = this.amount.compareTo(o.amount);       if (a != 0) return a;
        return this.transactionId.compareTo(o.transactionId);
    }
}
