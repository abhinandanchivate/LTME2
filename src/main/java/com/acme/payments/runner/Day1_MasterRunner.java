package com.acme.payments.runner;

import com.acme.payments.section03.Section03_DesignPrinciples;
import com.acme.payments.section04.Section04_Runner;
import com.acme.payments.section05.Section05_Runner;
import com.acme.payments.section06.Section06_Generics;
import com.acme.payments.section07.Section07_StreamPipelines;
import com.acme.payments.section08.Section08_AdvancedCollectors;
import com.acme.payments.section08b.Section08B_Sorting;
import com.acme.payments.section09.Section09_Optional;
import com.acme.payments.section10.Section10_Performance;
import com.acme.payments.section11.Section11_AntiPatterns;
import com.acme.payments.section12.Section12_RecordVsClass;
import com.acme.payments.section13.Section13_Exercises;

/**
 * =======================================================================
 * DAY 1 MASTER RUNNER
 * Advanced Java – Payments Use Case
 * Immutable Domain Modeling and Stream Mastery for Financial Systems
 * =======================================================================
 *
 * Runs all twelve sections in sequence, each in its own block.
 *
 * Each section:
 *   → lives in its own package (section03 … section13)
 *   → has its own main() method — run independently if needed
 *   → shows BROKEN (❌) vs CORRECT (✅) code
 *   → is self-contained — no shared mutable state between sections
 *
 * USAGE:
 *   Run ALL sections:
 *     java com.acme.payments.runner.Day1_MasterRunner
 *
 *   Run ONE section (pass section number as argument):
 *     java com.acme.payments.runner.Day1_MasterRunner 3
 *     java com.acme.payments.runner.Day1_MasterRunner 8B
 *     java com.acme.payments.runner.Day1_MasterRunner 11
 *
 *   Run each section's own main directly:
 *     java com.acme.payments.section04.Section04_Runner
 *     java com.acme.payments.section08b.Section08B_Sorting
 *     java com.acme.payments.section13.Section13_Exercises
 *
 * SECTIONS:
 *   §3  – Core Design Principles (7 Principles: P1–P7)
 *   §4  – Java 17 Record: PaymentTransaction (9 subsections)
 *   §5  – Java 8 Builder Pattern: PaymentTransaction (6 subsections)
 *   §6  – Generics: PECS, bounded types, type erasure
 *   §7  – Stream Pipelines: filter→sort→map→reduce, regulatory reporting
 *   §8  – Advanced Collectors: A–I (groupingBy … multi-level cross-tab)
 *   §8B – Sorting: Comparator chains, TreeMap, LinkedHashMap, Map.Entry
 *   §9  – Optional: findById, flatMap, orElseThrow, filter, anti-patterns
 *   §10 – Performance: primitive streams, short-circuit, lazy, parallel
 *   §11 – Anti-Patterns: AP1–AP5 (broken vs fixed, with live demos)
 *   §12 – Record vs Class: decision framework and migration equivalence
 *   §13 – Exercises: Exercise 1 (riskFlags) + Exercise 2 (bug hunt)
 */
public class Day1_MasterRunner {

    public static void main(String[] args) throws Exception {

        header();

        String filter = (args.length > 0) ? args[0].toUpperCase() : "ALL";

        run("3",   filter, () -> Section03_DesignPrinciples.main(new String[]{}));
        run("4",   filter, () -> Section04_Runner.main(new String[]{}));
        run("5",   filter, () -> Section05_Runner.main(new String[]{}));
        run("6",   filter, () -> Section06_Generics.main(new String[]{}));
        run("7",   filter, () -> Section07_StreamPipelines.main(new String[]{}));
        run("8",   filter, () -> Section08_AdvancedCollectors.main(new String[]{}));
        run("8B",  filter, () -> Section08B_Sorting.main(new String[]{}));
        run("9",   filter, () -> Section09_Optional.main(new String[]{}));
        run("10",  filter, () -> Section10_Performance.main(new String[]{}));
        run("11",  filter, () -> Section11_AntiPatterns.main(new String[]{}));
        run("12",  filter, () -> Section12_RecordVsClass.main(new String[]{}));
        run("13",  filter, () -> Section13_Exercises.main(new String[]{}));

        footer(filter);
    }

    private static void run(String section, String filter, ThrowingRunnable r) throws Exception {
        if (!filter.equals("ALL") && !filter.equals(section)) return;
        r.run();
        System.out.println("\n" + "─".repeat(68) + "\n");
    }

    @FunctionalInterface
    interface ThrowingRunnable { void run() throws Exception; }

    // -----------------------------------------------------------------------

    static void header() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  Advanced Java – Payments Use Case                              ║");
        System.out.println("║  Day 1: Immutable Domain Modeling & Stream Mastery              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Scenario:  Global processor, 50,000+ TPS, UPI / NEFT / RTGS   ║");
        System.out.println("║  Problem 1: $2.3M reconciliation discrepancy (broken equals)   ║");
        System.out.println("║  Problem 2: PCI-DSS audit failure (raw PANs in logs)           ║");
        System.out.println("║  Problem 3: 400ms latency spikes (stream pipeline bugs)        ║");
        System.out.println("║  Solution:  Sections 3 – 13 below ↓                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }

    static void footer(String filter) {
        if (!filter.equals("ALL")) return;
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  DAY 1 COMPLETE – KEY TAKEAWAYS                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  #1  Deep immutability  – List.copyOf() in compact constructor ║");
        System.out.println("║  #2  equals/hashCode    – SAME fields in BOTH (P2 contract)   ║");
        System.out.println("║  #3  Stateless streams  – reduce() not external accumulator   ║");
        System.out.println("║  #4  PCI at ctor time   – regex in compact constructor        ║");
        System.out.println("║  #5  Business ordering  – timestamp → amount → id             ║");
        System.out.println("║  #6  Optional return    – never field / parameter             ║");
        System.out.println("║  #7  Dedicated pool     – ForkJoinPool injection              ║");
        System.out.println("║  #8  Sorted map output  – TreeMap / LinkedHashMap for reports ║");
        System.out.println("║  #9  Comparator chains  – comparing().thenComparing()         ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Next steps:                                                   ║");
        System.out.println("║  1. Apply List.copyOf() to all existing collection fields     ║");
        System.out.println("║  2. Audit equals()/hashCode() — verify with HashSet test      ║");
        System.out.println("║  3. Profile stream pipelines with JMH before optimizing       ║");
        System.out.println("║  4. Document PCI validation in constructors (visible in code) ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Run individual sections:                                      ║");
        System.out.println("║    java ...Day1_MasterRunner 4     → Section 4 only           ║");
        System.out.println("║    java ...Day1_MasterRunner 8B    → Section 8B only          ║");
        System.out.println("║    java ...Day1_MasterRunner 11    → Section 11 only          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
