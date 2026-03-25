package com.acme.payments.domain;

/**
 * Payment instrument. Covers Indian rails (UPI, NEFT/RTGS via BANK_TRANSFER)
 * and global card / wallet methods.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    BANK_TRANSFER,   // NEFT, RTGS, IMPS
    UPI,             // NPCI Unified Payments Interface
    WALLET           // Paytm, PhonePe, Google Pay
}
