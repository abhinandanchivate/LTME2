package com.acme.payments.section03;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * =======================================================================
 * SECTION 3 – CORE DESIGN PRINCIPLES FOR FINANCIAL SYSTEMS
 * =======================================================================
 *
 * Seven production-grade principles that underpin every code decision.
 * Each principle shows a BROKEN (❌) vs CORRECT (✅) code pair with a
 * runnable demonstration.
 *
 * NOTE: These are NOT SOLID principles.
 *   SOLID  → governs class architecture (SRP, OCP, LSP, ISP, DIP)
 *   These  → govern RUNTIME DATA BEHAVIOUR in payment systems:
 *            data correctness, thread safety, regulatory compliance.
 *
 * Principles:
 *   P1 – Deep Immutability
 *   P2 – Structural Equality Contract
 *   P3 – Stateless Stream Pipelines
 *   P4 – Business-Driven Natural Ordering
 *   P5 – Audit Trail Integrity
 *   P6 – Optional Best Practices
 *   P7 – Parallel Stream Management
 *
 * Run this section alone:
 *   java com.acme.payments.section03.Section03_DesignPrinciples
 */
public class Section03_DesignPrinciples {

    // -----------------------------------------------------------------------
    // P1 – DEEP IMMUTABILITY
    // final prevents field REASSIGNMENT, not mutation of the referenced object.
    // Deep immutability requires defensive copies for every mutable component.
    // -----------------------------------------------------------------------

    /** ❌ Shallow: caller's list and record share the same reference. */
    record BrokenBatch(List<String> txIds) {}

    /** ✅ Deep: List.copyOf() creates an independent, unmodifiable snapshot. */
    record SafeBatch(List<String> txIds) {
        public SafeBatch { txIds = List.copyOf(txIds); }
    }

    static void p1_deepImmutability() {
        sep("P1 – Deep Immutability");

        // ❌ Broken
        List<String> src = new ArrayList<>(List.of("tok_001", "tok_002"));
        BrokenBatch broken = new BrokenBatch(src);
        src.add("INJECTED");
        print("  ❌ BrokenBatch after caller adds: " + broken.txIds());

        // ✅ Safe
        List<String> src2 = new ArrayList<>(List.of("tok_001", "tok_002"));
        SafeBatch safe = new SafeBatch(src2);
        src2.clear();
        print("  ✅ SafeBatch after caller clears:  " + safe.txIds());

        try { safe.txIds().add("hack"); }
        catch (UnsupportedOperationException e) {
            print("  ✅ safe.txIds().add() → UnsupportedOperationException (immutable view)");
        }

        note("Common mistakes:",
             "(1) Assuming final = immutable  — final blocks reassignment, not mutation",
             "(2) Collections.unmodifiableList() without copying — still shares backing list",
             "(3) Using java.util.Date        — always use java.time.Instant",
             "(4) Defensive copy on input but NOT on getter output");
    }

    // -----------------------------------------------------------------------
    // P2 – STRUCTURAL EQUALITY CONTRACT
    // equals() and hashCode() must use EXACTLY the same set of fields.
    // A mismatch silently breaks HashSet deduplication.
    // -----------------------------------------------------------------------

    static class BrokenTx {
        final String id; final BigDecimal amount;
        BrokenTx(String id, BigDecimal amount) { this.id = id; this.amount = amount; }
        @Override public boolean equals(Object o) {
            return this.id.equals(((BrokenTx) o).id);          // ❌ ID only
        }
        @Override public int hashCode() {
            return Objects.hash(id, amount);                    // ❌ both fields — MISMATCH
        }
    }

    static void p2_equalityContract() {
        sep("P2 – Structural Equality Contract");

        BrokenTx t1 = new BrokenTx("tok_123", new BigDecimal("100.00"));
        BrokenTx t2 = new BrokenTx("tok_123", new BigDecimal("200.00")); // different amount

        print("  ❌ t1.equals(t2)         = " + t1.equals(t2)                       + "  (true — ID only)");
        print("  ❌ hashCodes match       = " + (t1.hashCode() == t2.hashCode())     + "  (false — different amounts)");

        Set<BrokenTx> set = new HashSet<>();
        set.add(t1); set.add(t2);
        print("  ❌ HashSet size          = " + set.size() + "  (2 — DUPLICATE SETTLEMENT! should be 1)");
        print("  ✅ Fix: Java 17 Records auto-generate equals/hashCode from ALL fields — bug impossible.");
        note("Production impact: This exact mismatch caused the $2.3M reconciliation discrepancy.");
    }

    // -----------------------------------------------------------------------
    // P3 – STATELESS STREAM PIPELINES
    // Stream lambdas must NOT read or write shared mutable variables.
    // -----------------------------------------------------------------------

    static void p3_statelessPipelines() {
        sep("P3 – Stateless Stream Pipelines");

        List<BigDecimal> amounts = List.of(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("3000"));

        // ❌ Stateful external accumulator — race condition under parallel
        BigDecimal[] bad = {BigDecimal.ZERO};
        amounts.stream().forEach(a -> bad[0] = bad[0].add(a));
        print("  ❌ Stateful forEach accumulator:       " + bad[0] + "  (non-deterministic with parallelStream)");

        // ✅ Stateless reduce — associative: (a+b)+c == a+(b+c)
        BigDecimal good = amounts.parallelStream().reduce(BigDecimal.ZERO, BigDecimal::add);
        print("  ✅ Stateless reduce (deterministic):   " + good);
    }

    // -----------------------------------------------------------------------
    // P4 – BUSINESS-DRIVEN NATURAL ORDERING
    // Comparable must reflect business priority.
    // Payments: timestamp → amount → id  (FIFO → value priority → deterministic tiebreaker)
    // -----------------------------------------------------------------------

    record SimpleTx(Instant timestamp, BigDecimal amount, String id)
            implements Comparable<SimpleTx> {
        @Override public int compareTo(SimpleTx o) {
            int t = this.timestamp.compareTo(o.timestamp); if (t != 0) return t;
            int a = this.amount.compareTo(o.amount);       if (a != 0) return a;
            return this.id.compareTo(o.id);
        }
    }

    static void p4_naturalOrdering() {
        sep("P4 – Business-Driven Natural Ordering");

        List<SimpleTx> txs = new ArrayList<>(List.of(
            new SimpleTx(Instant.parse("2024-11-15T10:30:00Z"), new BigDecimal("5000"), "tok_c"),
            new SimpleTx(Instant.parse("2024-11-15T10:28:00Z"), new BigDecimal("9000"), "tok_a"),
            new SimpleTx(Instant.parse("2024-11-15T10:28:00Z"), new BigDecimal("3000"), "tok_b")
        ));
        Collections.sort(txs);
        print("  ✅ Sorted (timestamp → amount → id):");
        for (SimpleTx tx : txs)
            print("     " + tx.timestamp() + "  ₹" + tx.amount() + "  " + tx.id());
        note("riskFlags deliberately excluded from ordering — same transaction must not",
             "sort differently after fraud scoring updates.");
    }

    // -----------------------------------------------------------------------
    // P5 – AUDIT TRAIL INTEGRITY
    // Collectors.toList() preserves encounter order; toSet() does NOT.
    // For SOX-compliant audit trails always collect to ordered structures.
    // -----------------------------------------------------------------------

    static void p5_auditTrailIntegrity() {
        sep("P5 – Audit Trail Integrity");

        List<String> events = List.of("evt_003", "evt_001", "evt_002");

        Set<String>  unordered = events.stream().collect(Collectors.toSet());
        List<String> ordered   = events.stream().collect(Collectors.toList());

        print("  ❌ toSet()  — order lost: " + unordered + "  (fails SOX audit trail)");
        print("  ✅ toList() — order kept: " + ordered   + "  (satisfies SOX Section 404)");
        note("Indian regulatory context: RBI and SEBI require temporally ordered audit logs.",
             "Unordered collections can INVALIDATE a compliance submission.");
    }

    // -----------------------------------------------------------------------
    // P6 – OPTIONAL BEST PRACTICES
    // Optional is a return type ONLY.
    // Never a field (breaks serialization); never a parameter (caller can pass null).
    // -----------------------------------------------------------------------

    static void p6_optionalBestPractices() {
        sep("P6 – Optional Best Practices");

        List<String> tokens = List.of("tok_001", "tok_002");

        // ✅ Return type
        Optional<String> found   = tokens.stream().filter(t -> t.equals("tok_001")).findFirst();
        Optional<String> missing = tokens.stream().filter(t -> t.equals("tok_XYZ")).findFirst();

        found.ifPresent(t   -> print("  ✅ ifPresent:    " + t));
        print("  ✅ orElse:       " + missing.orElse("NOT_FOUND"));
        try {
            missing.orElseThrow(() -> new IllegalArgumentException("Token not found"));
        } catch (IllegalArgumentException e) {
            print("  ✅ orElseThrow:  " + e.getMessage());
        }
        Optional<BigDecimal> highVal = Optional.of(new BigDecimal("75000"))
            .filter(a -> a.compareTo(new BigDecimal("50000")) > 0);
        print("  ✅ filter:       " + highVal);

        note("Rules:",
             "✅ Return type only — forces callers to handle absence",
             "❌ Never a field   — breaks Jackson/JPA; 16-byte overhead per field",
             "❌ Never a param   — caller can still pass null Optional",
             "❌ Never .get()    — use orElse / orElseThrow / ifPresent");
    }

    // -----------------------------------------------------------------------
    // P7 – PARALLEL STREAM MANAGEMENT
    // Parallel streams default to the JVM's shared ForkJoin pool.
    // Use a dedicated pool for CPU-intensive payment batch processing.
    // -----------------------------------------------------------------------

    static void p7_parallelStreamManagement() {
        sep("P7 – Parallel Stream Management");

        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 1; i <= 500; i++) amounts.add(new BigDecimal(i));

        // ✅ Dedicated ForkJoinPool — does NOT starve Spring async / scheduled tasks
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        try {
            BigDecimal total = pool.submit(() ->
                amounts.parallelStream().reduce(BigDecimal.ZERO, BigDecimal::add)
            ).get();
            print("  ✅ Dedicated pool result (1..500): ₹" + total);
        } catch (Exception e) {
            print("  ERROR: " + e.getMessage());
        } finally {
            pool.shutdown();
        }

        note("Performance reality (Indian enterprise batches 10K–50K txns):",
             "CPU-intensive 100K: seq 500ms vs parallel 150ms  → ✅ use parallel",
             "DB save 10K:        seq 2000ms vs parallel 5000ms → ❌ sequential",
             "Simple filter 100:  seq 1ms    vs parallel 5ms    → ❌ sequential",
             "PRODUCTION NOTE: Never instantiate ForkJoinPool per-request.",
             "Inject as a Spring @Bean with proper lifecycle management.");
    }

    // -----------------------------------------------------------------------
    // MAIN — runs all 7 principles
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        banner("SECTION 3 – CORE DESIGN PRINCIPLES FOR FINANCIAL SYSTEMS");
        info("These 7 principles govern RUNTIME BEHAVIOUR (data correctness,");
        info("thread safety, regulatory compliance) — NOT class structure.");
        info("SOLID governs architecture; these seven govern domain object behaviour.");

        p1_deepImmutability();
        p2_equalityContract();
        p3_statelessPipelines();
        p4_naturalOrdering();
        p5_auditTrailIntegrity();
        p6_optionalBestPractices();
        p7_parallelStreamManagement();

        done("Section 3");
    }

    // ---- shared helpers ----
    static void banner(String t) { System.out.println("\n" + "=".repeat(68) + "\n  " + t + "\n" + "=".repeat(68)); }
    static void sep(String t)    { System.out.println("\n── " + t + " " + "─".repeat(Math.max(0, 64 - t.length()))); }
    static void info(String t)   { System.out.println("  " + t); }
    static void print(String t)  { System.out.println(t); }
    static void note(String... lines) {
        System.out.println("  NOTE:");
        for (String l : lines) System.out.println("    " + l);
    }
    static void done(String s)   { System.out.println("\n✅  " + s + " complete.\n"); }
}
