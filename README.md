# Advanced Java – Payments Use Case | Day 1

**Immutable Domain Modeling & Stream Mastery for Financial Systems**

Complete section-by-section implementation of the Day 1 PDF.  
Every section has its own package, its own `main()` method, and runs independently.

---

## Project structure

```
src/main/java/com/acme/payments/
├── domain/
│   ├── PaymentStatus.java          (enum: AUTHORIZED, SETTLED, REFUNDED, FAILED)
│   └── PaymentMethod.java          (enum: CREDIT_CARD, DEBIT_CARD, UPI, BANK_TRANSFER, WALLET)
│
├── section03/
│   └── Section03_DesignPrinciples.java   §3  All 7 Principles (P1–P7): broken vs correct
├── section04/
│   ├── PaymentTransaction.java           §4  Java 17 Record (PCI-DSS, GDPR, SOX)
│   └── Section04_Runner.java             §4  9 subsections: construction, validation, equality …
├── section05/
│   ├── PaymentTransaction.java           §5  Java 8 Builder (memoized hashCode, fluent API)
│   └── Section05_Runner.java             §5  6 subsections incl. migration equivalence check
├── section06/
│   └── Section06_Generics.java           §6  PECS, ProcessingResult<T>, safeCast, type erasure
├── section07/
│   └── Section07_StreamPipelines.java    §7  filter→sort→map→reduce, regulatory reporting, peek
├── section08/
│   └── Section08_AdvancedCollectors.java §8  Collectors A–I (groupingBy…multi-level cross-tab)
├── section08b/
│   └── Section08B_Sorting.java           §8B Comparator chains, TreeMap, LinkedHashMap, Map.Entry
├── section09/
│   └── Section09_Optional.java           §9  findById, flatMap, orElseThrow, filter, anti-patterns
├── section10/
│   └── Section10_Performance.java        §10 Primitive streams, short-circuit, lazy, parallel pool
├── section11/
│   └── Section11_AntiPatterns.java       §11 AP1–AP5 live demos: broken vs fixed
├── section12/
│   └── Section12_RecordVsClass.java      §12 Decision framework + migration equivalence table
├── section13/
│   └── Section13_Exercises.java          §13 Exercise 1 (riskFlags) + Exercise 2 (bug hunt)
└── runner/
    └── Day1_MasterRunner.java            Master: runs all or a single section by number
```

---

## Requirements

- Java 17+
- No external dependencies (stdlib only)

---

## Build

```bash
# Create output directory
mkdir -p target/classes

# Compile all sources in one pass
javac --release 17 -sourcepath src/main/java -d target/classes \
  $(find src/main/java -name "*.java")
```

---

## Run

### Run ALL sections (master runner)
```bash
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner
```

### Run ONE section by number
```bash
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 3
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 4
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 5
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 6
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 7
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 8
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 8B
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 9
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 10
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 11
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 12
java -cp target/classes com.acme.payments.runner.Day1_MasterRunner 13
```

### Run each section's own main() directly
```bash
java -cp target/classes com.acme.payments.section04.Section04_Runner
java -cp target/classes com.acme.payments.section08b.Section08B_Sorting
java -cp target/classes com.acme.payments.section11.Section11_AntiPatterns
```

---

## What each section covers

| Section | Topic | Key demonstrations |
|---------|-------|-------------------|
| §3 | Core Design Principles | P1 Deep immutability, P2 Equality contract, P3 Stateless streams, P4 Business ordering, P5 Audit trail, P6 Optional, P7 Parallel pool |
| §4 | Java 17 Record | Compact constructor, PCI-DSS validation, riskFlags deep copy, auto equals/hashCode, exhaustive switch, businessTime() |
| §5 | Java 8 Builder | Fluent Builder, memoized volatile hashCode, equals/hashCode same fields, migration equivalence |
| §6 | Generics | `T extends PaymentTransaction`, `ProcessingResult<T>`, PECS `? extends` / `? super`, `safeCast`, type erasure |
| §7 | Stream Pipelines | `filter→sort→map→reduce`, `groupingBy`, regulatory reporting (GDPR+SOX+PCI), exhaustive switch, `peek()`, lazy eval |
| §8 | Advanced Collectors | A–I: top-N, `reduce`, `groupingBy`, `partitioningBy`, `summarizingDouble`, IST day grouping, `groupingByConcurrent`, BigDecimal totals, multi-level cross-tab |
| §8B | Sorting | `comparing().thenComparing()`, settlement queue, fraud queue, null-safe, `TreeMap`, `LinkedHashMap`, `Map.Entry` sort |
| §9 | Optional | `findById`, `ifPresentOrElse`, `flatMap` nested, `orElseThrow`, `filter`, `Optional.stream()` flatten, all anti-patterns |
| §10 | Performance | `mapToLong()`, `summaryStatistics()`, `anyMatch/allMatch/noneMatch/findFirst/limit`, lazy element count, `toConcurrentMap`, dedicated `ForkJoinPool` |
| §11 | Anti-Patterns | AP1 mutable Date, AP2 broken symmetry, AP3 race condition, AP4 Optional misuse, AP5 nested collection deep copy |
| §12 | Record vs Class | Feature table, when to use each, migration checklist, field-by-field equivalence test, dedup verification |
| §13 | Exercises | Exercise 1: 5 assertions on riskFlags; Exercise 2: 4 bugs identified+fixed with side-by-side output |

---

## Key takeaways (Day 1)

1. **Deep immutability** — `List.copyOf()` in the compact constructor
2. **Equality contract** — `equals()` and `hashCode()` must use the **same** fields (the broken version caused the $2.3M incident)
3. **Stateless pipelines** — `reduce()` not external accumulators
4. **PCI at construction time** — regex in compact constructor, impossible to bypass
5. **Business ordering** — timestamp → amount → id (SOX reproducibility)
6. **Optional as return type** — never field, never parameter
7. **Dedicated ForkJoinPool** — never use the shared common pool for batch processing
8. **Sorted map output** — `TreeMap` / `LinkedHashMap` for compliance reports
9. **Comparator chains** — `comparing().thenComparing()` in one expression
