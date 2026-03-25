package com.acme.payments.section05;

import com.acme.payments.domain.PaymentMethod;
import com.acme.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

/**
 * =======================================================================
 * SECTION 5 – JAVA 8 COMPATIBLE IMMUTABLE PaymentTransaction (Builder)
 * =======================================================================
 *
 * For systems on Java 8–11 (legacy core banking on WebLogic, JBoss,
 * IBM WebSphere). Records require Java 16+.
 *
 * Immutability strategy:
 *   • All fields are private final
 *   • No setters provided
 *   • Class is final — no subclass can add mutable state
 *   • Private constructor — only Builder can create instances
 *
 * The Builder pattern:
 *   • Separates object configuration from construction
 *   • Names every argument at the call site — impossible to swap silently
 *   • Validation fires at build() — single, inescapable construction point
 *
 * Memoized hashCode:
 *   volatile ensures cross-thread visibility of the computed result.
 *   This is a benign data race — worst case is recomputing the same value.
 *   Reference: Bloch, Effective Java 3rd Edition, Item 83.
 */
public final class PaymentTransaction implements Comparable<PaymentTransaction> {

    private final String        transactionId;
    private final BigDecimal    amount;
    private final Currency      currency;
    private final Instant       timestamp;
    private final PaymentStatus status;
    private final PaymentMethod method;
    private final String        merchantId;
    private final String        customerId;

    private transient volatile int hashCode; // memoized — benign data race

    // -------------------------------------------------------------------
    // PRIVATE CONSTRUCTOR — only Builder can instantiate
    // -------------------------------------------------------------------
    private PaymentTransaction(Builder b) {
        this.transactionId = Objects.requireNonNull(b.transactionId, "transactionId");
        this.amount        = Objects.requireNonNull(b.amount,         "amount");
        this.currency      = Objects.requireNonNull(b.currency,       "currency");
        this.timestamp     = Objects.requireNonNull(b.timestamp,      "timestamp");
        this.status        = Objects.requireNonNull(b.status,         "status");
        this.method        = Objects.requireNonNull(b.method,         "method");
        this.merchantId    = Objects.requireNonNull(b.merchantId,     "merchantId");
        this.customerId    = Objects.requireNonNull(b.customerId,     "customerId");

        if (this.amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive: " + this.amount);

        if (this.transactionId.matches("\\d{13,19}"))
            throw new IllegalArgumentException(
                "Raw PAN detected — use tokenized ID (e.g., tok_abc123)");
    }

    // -------------------------------------------------------------------
    // GETTERS — no setters (immutability by convention for Java 8)
    // -------------------------------------------------------------------
    public String        getTransactionId() { return transactionId; }
    public BigDecimal    getAmount()        { return amount;         }
    public Currency      getCurrency()      { return currency;       }
    public Instant       getTimestamp()     { return timestamp;      }
    public PaymentStatus getStatus()        { return status;         }
    public PaymentMethod getMethod()        { return method;         }
    public String        getMerchantId()    { return merchantId;     }
    public String        getCustomerId()    { return customerId;     }

    public ZonedDateTime businessTime(ZoneId zone) {
        Objects.requireNonNull(zone);
        return ZonedDateTime.ofInstant(timestamp, zone);
    }
    public Optional<BigDecimal> getRefundAmount() {
        return status == PaymentStatus.REFUNDED ? Optional.of(amount) : Optional.empty();
    }

    // -------------------------------------------------------------------
    // equals / hashCode — SAME field set in BOTH (Principle 2)
    // The $2.3M incident came from mismatched fields between these two.
    // -------------------------------------------------------------------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentTransaction)) return false;
        PaymentTransaction that = (PaymentTransaction) o;
        return Objects.equals(transactionId, that.transactionId)
            && Objects.equals(amount,        that.amount)
            && Objects.equals(currency,      that.currency)
            && Objects.equals(timestamp,     that.timestamp)
            && status == that.status
            && method == that.method
            && Objects.equals(merchantId, that.merchantId)
            && Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        int result = hashCode;         // read volatile once
        if (result == 0) {
            result = Objects.hash(transactionId, amount, currency, timestamp,
                                  status, method, merchantId, customerId);
            hashCode = result;         // write volatile once
        }
        return result;
    }

    @Override
    public int compareTo(PaymentTransaction o) {
        int t = this.timestamp.compareTo(o.timestamp); if (t != 0) return t;
        int a = this.amount.compareTo(o.amount);       if (a != 0) return a;
        return this.transactionId.compareTo(o.transactionId);
    }

    @Override
    public String toString() {
        return "PaymentTransaction{id='" + transactionId + "', amount=" + amount
             + ", currency=" + currency.getCurrencyCode() + ", status=" + status + "}";
    }

    // -------------------------------------------------------------------
    // FLUENT BUILDER
    // Each setter returns 'this' for method chaining.
    // Validation fires at build() — the single construction gate.
    // -------------------------------------------------------------------
    public static class Builder {
        private String transactionId;
        private BigDecimal    amount;
        private Currency      currency;
        private Instant       timestamp;
        private PaymentStatus status;
        private PaymentMethod method;
        private String        merchantId;
        private String        customerId;

        public Builder transactionId(String v) { this.transactionId = v; return this; }
        public Builder amount(BigDecimal v)     { this.amount = v;        return this; }
        public Builder currency(Currency v)     { this.currency = v;      return this; }
        public Builder timestamp(Instant v)     { this.timestamp = v;     return this; }
        public Builder status(PaymentStatus v)  { this.status = v;        return this; }
        public Builder method(PaymentMethod v)  { this.method = v;        return this; }
        public Builder merchantId(String v)     { this.merchantId = v;    return this; }
        public Builder customerId(String v)     { this.customerId = v;    return this; }

        /** Validates all fields and returns an immutable PaymentTransaction. */
        public PaymentTransaction build() { return new PaymentTransaction(this); }
    }
}
