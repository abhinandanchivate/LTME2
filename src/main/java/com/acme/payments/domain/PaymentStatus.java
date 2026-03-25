package com.acme.payments.domain;

/**
 * Lifecycle states for a payment transaction.
 * Using an exhaustive switch on this enum causes a compile error
 * when a new status is added but not handled — prevents silent routing bugs.
 */
public enum PaymentStatus {
    AUTHORIZED,
    SETTLED,
    REFUNDED,
    FAILED
}
